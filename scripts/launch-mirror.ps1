$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "User") + ";" + [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
$adb = "C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
$phoneIP = "192.168.1.110"
$port = "5555"

# 连接手机
$conn = & $adb connect "${phoneIP}:${port}" 2>&1
Start-Sleep 2

$devs = & $adb devices 2>&1
if ($devs -notmatch "${phoneIP}:${port}\s+device") {
    Write-Host "[!] 无法连接 ${phoneIP}:${port}，使用 USB 模式重试" -ForegroundColor Yellow
}

# 查找 scrcpy
$scrcpyPaths = @(
    (Get-Command scrcpy -ErrorAction SilentlyContinue).Source,
    "C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy.exe",
    "C:\Program Files\scrcpy\scrcpy.exe"
) | Where-Object { $_ -and (Test-Path $_) }

$scrcpy = $scrcpyPaths | Select-Object -First 1

if (-not $scrcpy) {
    Write-Host "[!] scrcpy 未在预设路径找到，扫描 WinGet 目录..." -ForegroundColor Yellow
    Get-ChildItem "C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages" -Recurse -Filter "scrcpy.exe" -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "  尝试: $($_.FullName)"
        $scrcpy = $_.FullName
    }
}

if ($scrcpy) {
    Write-Host "使用 scrcpy: $scrcpy" -ForegroundColor Green
    Write-Host "启动投屏... [Pixel3a-Display]" -ForegroundColor Cyan
    Write-Host "快捷键: Ctrl+H 隐藏 | Ctrl+F 全屏 | Ctrl+Shift+/- 缩放"
    & $scrcpy --max-size 1080 --window-title="Pixel3a-Display" --show-touches --no-audio
} else {
    Write-Host "[!] scrcpy 未安装!" -ForegroundColor Red
    Write-Host "运行: winget install Genymobile.Scrcpy --source winget --accept-package-agreements --accept-source-agreements"
}
