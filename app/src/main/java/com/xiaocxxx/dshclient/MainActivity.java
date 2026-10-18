package com.xiaocxxx.dshclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * DSH 移动端客户端。
 *
 * 界面本身由电脑上的 DSH 服务提供（`dsh --profile web --host <局域网地址>`），
 * 本应用负责承载 WebView、保存服务器地址与 token、处理返回键与文件选择。
 */
public class MainActivity extends Activity {

    private static final String PREFS = "dsh_client";
    private static final String KEY_BASE = "server_base";
    private static final String KEY_TOKEN = "server_token";
    private static final int REQUEST_FILE = 1001;

    private WebView webView;
    private ProgressBar progress;
    private ScrollView setupPanel;
    private LinearLayout errorPanel;
    private TextView errorText;
    private EditText urlInput;
    private EditText tokenInput;
    private ValueCallback<Uri[]> fileCallback;

    private String base;
    private String token = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);
        setupPanel = findViewById(R.id.setupPanel);
        errorPanel = findViewById(R.id.errorPanel);
        errorText = findViewById(R.id.errorText);
        urlInput = findViewById(R.id.urlInput);
        tokenInput = findViewById(R.id.tokenInput);

        configureWebView();

        findViewById(R.id.connectButton).setOnClickListener(v -> connectFromForm());
        findViewById(R.id.pasteButton).setOnClickListener(v -> pasteFromClipboard());
        findViewById(R.id.retryButton).setOnClickListener(v -> loadCurrent());
        findViewById(R.id.settingsButton).setOnClickListener(v -> showSetup());
        findViewById(R.id.menuButton).setOnClickListener(this::showMenu);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        base = prefs.getString(KEY_BASE, null);
        token = prefs.getString(KEY_TOKEN, "");
        if (base == null) {
            showSetup();
        } else {
            loadCurrent();
        }
    }

    // ---------------------------------------------------------------- WebView

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportMultipleWindows(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                if (isSameServer(uri)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // 没有可处理的应用时保持原样，不崩溃
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null || !request.isForMainFrame()) return;
                showError(getString(R.string.error_network, String.valueOf(error.getDescription())));
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request == null || !request.isForMainFrame()) return;
                if (response != null && response.getStatusCode() == 401) {
                    showError(getString(R.string.error_unauthorized));
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progress.setVisibility(View.GONE);
                } else {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(newProgress);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, REQUEST_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                Uri uri = Uri.parse(url);
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String name = uri.getLastPathSegment();
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name == null ? "dsh-download" : name);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) manager.enqueue(request);
                toast(getString(R.string.download_started));
            } catch (Exception e) {
                toast(e.toString());
            }
        });
    }

    private boolean isSameServer(Uri uri) {
        if (base == null) return true;
        Uri server = Uri.parse(base);
        String a = uri.getHost();
        String b = server.getHost();
        if (a == null || b == null) return true;
        return a.equalsIgnoreCase(b) && uri.getPort() == server.getPort();
    }

    // ------------------------------------------------------------------ 连接

    private void connectFromForm() {
        String raw = urlInput.getText().toString().trim();
        if (raw.isEmpty()) {
            toast(getString(R.string.setup_required));
            return;
        }
        ServerAddress parsed = parseAddress(raw);
        if (parsed == null) {
            toast(getString(R.string.setup_bad_url));
            return;
        }
        String manualToken = tokenInput.getText().toString().trim();
        if (!manualToken.isEmpty()) parsed.token = manualToken;

        base = parsed.base;
        token = parsed.token;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE, base)
                .putString(KEY_TOKEN, token)
                .apply();
        loadCurrent();
    }

    private void loadCurrent() {
        if (base == null) {
            showSetup();
            return;
        }
        setupPanel.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(buildUrl());
    }

    private String buildUrl() {
        String url = base.endsWith("/") ? base : base + "/";
        if (!token.isEmpty()) url = url + "?token=" + Uri.encode(token);
        return url;
    }

    private void showSetup() {
        urlInput.setText(base == null ? "" : base);
        tokenInput.setText(token);
        setupPanel.setVisibility(View.VISIBLE);
        errorPanel.setVisibility(View.GONE);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        setupPanel.setVisibility(View.GONE);
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null) return;
        urlInput.setText(text.toString().trim());
    }

    /** 把用户粘贴的完整地址拆成 base 与 token。 */
    private static ServerAddress parseAddress(String raw) {
        String text = raw.trim();
        if (!text.contains("://")) text = "http://" + text;
        Uri uri;
        try {
            uri = Uri.parse(text);
        } catch (Exception e) {
            return null;
        }
        if (uri.getScheme() == null || uri.getAuthority() == null) return null;

        ServerAddress parsed = new ServerAddress();
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://").append(uri.getAuthority());
        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            builder.append(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
        }
        parsed.base = builder.toString();
        String tokenParam = uri.getQueryParameter("token");
        if (tokenParam != null) parsed.token = tokenParam;
        return parsed;
    }

    private static final class ServerAddress {
        String base;
        String token = "";
    }

    // -------------------------------------------------------------------- 菜单

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_reload);
        menu.getMenu().add(0, 2, 1, R.string.menu_settings);
        menu.getMenu().add(0, 3, 2, R.string.menu_browser);
        menu.getMenu().add(0, 4, 3, R.string.menu_about);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    loadCurrent();
                    return true;
                case 2:
                    showSetup();
                    return true;
                case 3:
                    openInBrowser();
                    return true;
                case 4:
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.menu_about)
                            .setMessage(R.string.about_text)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void openInBrowser() {
        if (base == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(buildUrl())));
        } catch (Exception e) {
            toast(e.toString());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (setupPanel.getVisibility() == View.VISIBLE && base != null) {
                setupPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_FILE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    result[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
