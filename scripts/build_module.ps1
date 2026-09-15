$ErrorActionPreference = 'Stop'
# 打包 KernelSU 模块 zip，并把交付物同步到 D:\Deepseek Harness\RefreshSwitch
# APK 由 Gradle(Compose) 构建：D:\android-build\capp\app\build\outputs\apk\release\app-release.apk
$root   = "D:\android-build"
$mod    = "$root\ksu_module"
$apk    = "$root\capp\app\build\outputs\apk\release\app-release.apk"
$deliv  = "D:\Deepseek Harness\RefreshSwitch"
$zip    = "$mod\RefreshSwitch-KSU-module.zip"

if (-not (Test-Path $apk)) { throw "未找到 $apk，请先运行 build_compose.ps1" }

Write-Host "== 1) 同步 APK 到模块目录 =="
Copy-Item $apk "$mod\RefreshSwitch.apk" -Force
Get-Item "$mod\RefreshSwitch.apk" | ForEach-Object { "   {0:N2} MB  {1}" -f ($_.Length/1MB), $_.LastWriteTime }

Write-Host "== 2) 重建模块 zip（文件位于根目录，KSU 要求）=="
Add-Type -AssemblyName System.IO.Compression.FileSystem
Remove-Item $zip -Force -ErrorAction SilentlyContinue
$z = [System.IO.Compression.ZipFile]::Open($zip, 'Create')
foreach ($n in @('module.prop','service.sh','action.sh','uninstall.sh','RefreshSwitch.apk')) {
    $p = Join-Path $mod $n
    if (-not (Test-Path $p)) { throw "缺少模块文件: $n" }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z, $p, $n, 'Optimal') | Out-Null
}
$z.Dispose()
Get-Item $zip | ForEach-Object { "   {0}  {1:N2} MB" -f $_.Name, ($_.Length/1MB) }

Write-Host "== 3) 同步交付目录 $deliv =="
New-Item -ItemType Directory -Force -Path $deliv | Out-Null
Copy-Item $apk "$deliv\RefreshSwitch.apk" -Force
Copy-Item $zip "$deliv\RefreshSwitch-KSU-module.zip" -Force
Copy-Item "$mod\module.prop","$mod\service.sh","$mod\action.sh","$mod\uninstall.sh" $deliv -Force

Write-Host "== 4) 校验 zip 内容 =="
$z2 = [System.IO.Compression.ZipFile]::OpenRead($zip)
$z2.Entries | ForEach-Object { "   {0,-24} {1,10}" -f $_.FullName, $_.Length }
$z2.Dispose()
Write-Host "== 5) md5 =="
Get-FileHash "$deliv\RefreshSwitch.apk","$deliv\RefreshSwitch-KSU-module.zip" -Algorithm MD5 |
    ForEach-Object { "   {0}  {1}" -f $_.Hash.Substring(0,16), $_.Path }
