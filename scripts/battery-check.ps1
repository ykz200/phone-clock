$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "User") + ";" + [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
$adb = "C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
$phoneIP = "192.168.1.110"
$port = "5555"

# 连接手机
$conn = & $adb connect "${phoneIP}:${port}" 2>&1
Start-Sleep 2

$devs = & $adb devices 2>&1
if ($devs -notmatch "${phoneIP}:${port}\s+device") {
    Write-Host "[!] 无线连接失败，请确认:" -ForegroundColor Red
    Write-Host "    1. 手机和电脑在同一局域网"
    Write-Host "    2. 手机已重新开启 TCP 5555 端口 (adb tcpip 5555)"
    Write-Host "    3. 重新运行此脚本，或用 USB 线连接"
    pause
    return
}

# 存储
Write-Host "`n[存储]" -ForegroundColor Green
$df = & $adb shell df /data
$line = ($df | Select-String "/data").ToString().Trim()
Write-Host " $line"

# 电池
Write-Host "`n[电池]" -ForegroundColor Green
$batLines = & $adb shell dumpsys battery 2>&1
$batLines | ForEach-Object {
    $trimmed = $_.Trim()
    if ($trimmed -match "^(level|health|temperature|status|voltage):\s*(.+)$") {
        $k = $Matches[2].Trim()
        switch ($Matches[1]) {
            "level"      { $v = "$k%" }
            "health"     { $v = switch($k){2{"良好"}3{"过热"}4{"报废"}5{"过压"}default{"未知"}} }
            "temperature"{ $v = "$([math]::Round($k/10,1))°C" }
            "status"     { $v = switch($k){2{"充电中"}3{"放电中"}4{"未充电"}5{"充满"}default{"未知"}} }
            "voltage"    { $v = "${k}mV" }
            default      { $v = $k }
        }
        Write-Host "  ${Matches[1]}: $v"
    }
}

# 后台进程数
Write-Host "`n[运行中进程]" -ForegroundColor Green
$meminfo = & $adb shell cat /proc/meminfo | Select-String "MemAvailable"
if ($meminfo -match "MemAvailable:\s+(\d+)\s+kB") {
    $availMB = [math]::Round($Matches[1]/1024, 0)
    Write-Host "  可用内存: ${availMB}MB"
}

# 已启用应用数
$enabledCount = (& $adb shell pm list packages -e | Measure-Object).Count
Write-Host "  已启用应用数: $enabledCount"

Write-Host "`n=== 调整建议 ===" -ForegroundColor Yellow
Write-Host "若卡顿依然明显，可继续："
Write-Host "  - 卸载 Gmail:  adb shell pm uninstall -k --user 0 com.google.android.gm"
Write-Host "  - 卸载 Drive:  adb shell pm uninstall -k --user 0 com.google.android.apps.docs"
Write-Host "  - 启用充电保护: 需 Battery Charge Limit 应用 + 授权"
Write-Host "  - 固定路由器 DHCP 租约 (推荐)"
Write-Host "  - 长期插电 + 限充 80% 延长电池寿命"

Write-Host "`n按任意键继续..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown", [System.Threading.CancellationToken]::None)
