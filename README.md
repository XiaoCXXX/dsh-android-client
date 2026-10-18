# DSH Client for Android

DeepSeek Harness 的**移动端客户端**。

界面由你电脑上的 DSH 服务提供，这个 App 负责承载 WebView、保存服务器地址与 token、处理返回键、
文件选择与下载。所以你手机上看到的，就是电脑上同一套 DSH 界面 —— 包括模型菜单、思考强度滑条和订阅用量。

> **为什么不是独立运行？**
> DSH 的服务端是一个 Node.js 进程，依赖 `node-pty`（原生串口/伪终端模块）、随平台的 `ripgrep`
> 二进制等。这些在 Android 上没有可用的构建（nodejs-mobile 最高停在 Node 18，且不带这些原生模块）。
> 因此手机端只做客户端，服务端继续跑在电脑上 —— 和「IDE 的远程开发」是一个思路。

## 使用

### 1. 在电脑上开放局域网访问

DSH 默认只绑定 `127.0.0.1`，手机连不上。用你的局域网地址启动：

```powershell
dsh --profile web --host 192.168.1.5 --no-open
```

（把 `192.168.1.5` 换成电脑在局域网里的地址；Windows 防火墙首次会询问，选「允许专用网络」。）

终端会打印完整地址，形如：

```
dsh web: http://192.168.1.5:3080/?token=AbCdEf123...
```

### 2. 在手机上填入这个地址

打开 App → 把上面整条地址粘贴进输入框 → 连接。地址里的 `token` 会自动解析；也可以分开填。

地址会保存在本地，下次打开直接进入界面。

### 3. 常用操作

- **返回键**：先回退网页历史，退到底再退出 App
- **右上角 ⋮**：重新加载 / 服务器设置 / 在浏览器中打开 / 关于
- **上传文件**：DSH 界面里的附件按钮会调起系统的文件选择器
- **下载文件**：走系统下载管理器，落在「下载」目录

## 构建

需要 JDK 17+、Android SDK（platform 34、build-tools 34）与 Gradle 8.7。

```powershell
# 签名材料放仓库外，用环境变量指过去
$env:DSH_KEYSTORE = 'C:\path\to\keystore.jks'
$env:DSH_KEYSTORE_PASSWORD = '...'
$env:DSH_KEY_ALIAS = 'dsh'
$env:DSH_KEY_PASSWORD = '...'

gradle assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。不提供签名材料时 `release` 不签名，
可以直接构建 `assembleDebug` 自用。

`local.properties` 里需要 `sdk.dir`，它与签名材料都在 `.gitignore` 里，不会入库。

## 兼容性

| 项 | 值 |
| --- | --- |
| minSdk | 26（Android 8.0） |
| targetSdk | 34 |
| 依赖 | 无第三方库，纯 Android Framework（约 100 KB） |
| 明文 HTTP | 允许（局域网自建服务通常没有证书） |

## 已知限制

- 只能连**已启动**的 DSH 服务，不能在本机运行服务端（原因见开头）
- 目前是手动填地址；扫码配对还没做
- 横竖屏切换不重建界面，但 WebView 状态由系统管理

## 许可

MIT
