# Build the release APK. ASCII-only: Windows PowerShell reads .ps1 as ANSI.
$ErrorActionPreference = 'Stop'
# Resolve the project from this script's location so the repo is portable.
$proj = Split-Path -Parent $MyInvocation.MyCommand.Path

# signing material stays outside the repo and out of the command line
$props = @{}
Get-Content "$proj\keystore.properties" | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z]+)\s*=\s*(.*)$') { $props[$matches[1]] = $matches[2].Trim() }
}

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = 'C:\Android\sdk'
$env:ANDROID_SDK_ROOT = 'C:\Android\sdk'
$env:DSH_KEYSTORE = $props['storeFile']
$env:DSH_KEYSTORE_PASSWORD = $props['storePassword']
$env:DSH_KEY_ALIAS = $props['keyAlias']
$env:DSH_KEY_PASSWORD = $props['keyPassword']

Write-Host "building release APK ..."
& 'C:\Android\gradle-8.7\bin\gradle.bat' -p $proj assembleRelease --no-daemon --console=plain 2>&1 | Select-Object -Last 60

$apk = "$proj\app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apk) {
  $dist = "$proj\dist"
  New-Item -ItemType Directory $dist -Force | Out-Null
  $target = "$dist\DSH-Client-1.0.0.apk"
  Copy-Item $apk $target -Force
  $item = Get-Item $target
  Write-Host "APK: $target"
  Write-Host ("size: {0:N0} bytes ({1:N1} MB)" -f $item.Length, ($item.Length / 1MB))
  $hash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
  Write-Host "sha256: $hash"
  $hash | Set-Content "$dist\DSH-Client-1.0.0.apk.sha256" -NoNewline
} else {
  Write-Host "APK NOT FOUND"
  exit 1
}
