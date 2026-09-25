# UTF-8 with BOM
# 在桌面创建「用药雷达」快捷方式（指向项目内干净的启动入口，任意机器可用）
$root = Split-Path -Parent $PSScriptRoot
$shortcutPath = [Environment]::GetFolderPath("Desktop") + "\用药雷达.lnk"
$targetPath = Join-Path $root "用药雷达.bat"

$wshShell = New-Object -ComObject WScript.Shell
$shortcut = $wshShell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $targetPath
$shortcut.WorkingDirectory = $root
$shortcut.WindowStyle = 1
$ico = Join-Path $root "assets\medication-radar.ico"
if (Test-Path $ico) { $shortcut.IconLocation = $ico }
$shortcut.Description = "用药雷达一键启动"
$shortcut.Save()

Write-Host "Shortcut created: $shortcutPath"
