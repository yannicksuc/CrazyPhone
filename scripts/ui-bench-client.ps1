param([string]$Repo, [string]$Version, [string]$Kind = "client")
Add-Type -Namespace DevBg -Name Win32 -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool SetWindowPos(System.IntPtr h, System.IntPtr after, int x, int y, int cx, int cy, uint flags);
[DllImport("user32.dll")] public static extern System.IntPtr GetForegroundWindow();
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(System.IntPtr h);
'@
$before = @(Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | ForEach-Object Id)
$previous = [DevBg.Win32]::GetForegroundWindow()
Set-Location $Repo
& .\scripts\dev-launch.ps1 $Kind $Version | Out-Host
$deadline = (Get-Date).AddMinutes(8)
while ((Get-Date) -lt $deadline) {
    $p = Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' -and $before -notcontains $_.Id } | Select-Object -First 1
    if ($p) {
        # Keep it at the bottom for a while: the window can come back up when the game finishes loading.
        $until = (Get-Date).AddSeconds(90)
        while ((Get-Date) -lt $until) {
            $h = (Get-Process -Id $p.Id -ErrorAction SilentlyContinue).MainWindowHandle
            if ($h -and $h -ne [System.IntPtr]::Zero) {
                [DevBg.Win32]::SetWindowPos($h, [System.IntPtr]1, 0, 0, 0, 0, 0x13) | Out-Null
                if ($previous -ne [System.IntPtr]::Zero -and [DevBg.Win32]::GetForegroundWindow() -eq $h) {
                    [DevBg.Win32]::SetForegroundWindow($previous) | Out-Null
                }
            }
            Start-Sleep -Milliseconds 300
        }
        Write-Host "client window kept at the back (pid $($p.Id))"
        exit 0
    }
    Start-Sleep -Milliseconds 300
}
Write-Host "client window not found"
