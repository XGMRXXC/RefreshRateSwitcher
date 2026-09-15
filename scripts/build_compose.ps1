# 一键构建 MIUIX Compose 版 APK（Gradle + Kotlin 2.3.20 + Compose MP 1.10.3 + MIUIX 0.8.8）
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "D:\android-build\jdk\jdk-21.0.12.1+1"
$gradle = "D:\android-build\gradle\gradle-8.14.3\bin\gradle.bat"
$proj   = "D:\android-build\capp"

if (-not (Test-Path $gradle)) { throw "未找到 Gradle: $gradle" }
if (-not (Test-Path "$proj\settings.gradle.kts")) { throw "未找到工程: $proj" }

Set-Location $proj
& $gradle :app:assembleRelease --console=plain

$apk = "$proj\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "构建失败：未生成 APK" }
Get-Item $apk | ForEach-Object { "APK: {0}  {1:N2} MB" -f $_.FullName, ($_.Length/1MB) }
