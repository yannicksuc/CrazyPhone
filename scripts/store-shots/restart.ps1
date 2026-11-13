param([switch]$Server)
$repo = 'C:\Users\yanni\code\CrazyPhone'
Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" | ? { $_.CommandLine -match 'CrazyPhone.*runClient' } | % { taskkill /PID $_.ProcessId /T /F | Out-Null }
if ($Server) {
  python "$repo\.dev-launch\shots\tools\rcon.py" 35575 "save-all flush" "stop" | Out-Null
  $deadline = (Get-Date).AddSeconds(60)
  while ((Get-Date) -lt $deadline -and (Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" | ? { $_.CommandLine -match 'CrazyPhone.*runServer' })) { Start-Sleep 2 }
}
Start-Sleep 3
foreach ($d in 'run', 'run-client2') { $o = "$repo\versions\1.21.1\$d\options.txt"; (Get-Content $o) -replace '^guiScale:.*', 'guiScale:4' | Set-Content $o }
Set-Location $repo; Remove-Item .dev-launch\1.21.1-*.pid -ErrorAction SilentlyContinue; $env:CRAZYPHONE_DEV_COMMANDS = '1'
if ($Server) {
  .\scripts\dev-launch.ps1 server 1.21.1 | Out-Null
  $log = "$repo\.dev-launch\logs\1.21.1-server.log"
  $deadline = (Get-Date).AddMinutes(6)
  while ((Get-Date) -lt $deadline -and -not (Select-String -Path $log -Pattern 'Done \(' -Quiet -ErrorAction SilentlyContinue)) { Start-Sleep 3 }
  if (Select-String -Path $log -Pattern 'BUILD FAILED|error:' -Quiet) { Write-Host 'SERVER BUILD FAILED'; Select-String -Path $log -Pattern 'error:' | Select -First 5; exit 1 }
}
.\scripts\ui-bench-client.ps1 -Repo $repo -Version 1.21.1 -Kind client | Select-Object -Last 1
.\scripts\ui-bench-client.ps1 -Repo $repo -Version 1.21.1 -Kind client2 | Select-Object -Last 1
Add-Type -Namespace W -Name U -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetWindowPos(System.IntPtr h, System.IntPtr a, int x, int y, int cx, int cy, uint f);'
foreach ($x in (Get-Process java | ? MainWindowTitle -like 'Minecraft NeoForge* 1.21.1*')) { [W.U]::SetWindowPos($x.MainWindowHandle, [IntPtr]1, 0, 0, 1936, 1119, 0x10) | Out-Null }
$log2 = "$repo\.dev-launch\logs\1.21.1-client2.log"
$deadline = (Get-Date).AddMinutes(5)
while ((Get-Date) -lt $deadline -and -not (Select-String -Path $log2 -Pattern 'Connecting to voice chat' -Quiet -ErrorAction SilentlyContinue)) { Start-Sleep 3 }
Write-Host 'ready'
