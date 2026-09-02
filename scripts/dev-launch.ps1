<#
.SYNOPSIS
  Launch / manage / test CrazyPhone's Stonecutter version nodes without hand-tracking PIDs, ports,
  JAVA_HOME or working directories.

.DESCRIPTION
  This project builds 9 separate Stonecutter version nodes (5 NeoForge, 4 Fabric). Testing a change
  across several of them means repeatedly setting JAVA_HOME, cd-ing into the repo root, launching
  `gradlew.bat :<node>:runClient` / `:runServer` in the background, remembering which dedicated-server
  port belongs to which node (they all default to 25565 and collide if run together), and hunting
  `tasklist`/PIDs by memory size to tell one java.exe from another. This script replaces all of that
  with one small state directory (.dev-launch/) tracking one PID + one log file per (version, kind).

.PARAMETER Command
  list    - show every known version node, its loader, and its assigned dedicated-server port.
  client  - launch a client for -Version (background, logged, PID-tracked).
  server  - launch a dedicated server for -Version (background, logged, PID-tracked).
  status  - show every tracked process and whether it's still alive.
  stop    - stop one tracked process (-Version + -Kind), or every tracked process (-Version all).
  tail    - live-follow one tracked process's log (Ctrl+C to stop following, doesn't kill it).
  port    - best-effort patch that version's own server.properties to its assigned port + eula=true.
            Only takes effect on the NEXT server launch (the run-*/server directory must already
            exist - run `server <version>` once first if it doesn't).

.PARAMETER Version
  A node name from `list` (e.g. "1.20.4", "26.1", "1.21.1-fabric").

.PARAMETER Kind
  "client" or "server" - which of the two tracked processes for -Version to target. Defaults to
  "client". Only used by stop/tail; list/status show both.

.EXAMPLE
  .\scripts\dev-launch.ps1 list
.EXAMPLE
  .\scripts\dev-launch.ps1 client 26.1
.EXAMPLE
  .\scripts\dev-launch.ps1 port 1.21.1-fabric
  .\scripts\dev-launch.ps1 server 1.21.1-fabric
.EXAMPLE
  .\scripts\dev-launch.ps1 status
.EXAMPLE
  .\scripts\dev-launch.ps1 tail 26.1 client
.EXAMPLE
  .\scripts\dev-launch.ps1 stop 26.1 client
  .\scripts\dev-launch.ps1 stop 26.1 all
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('list', 'client', 'server', 'status', 'stop', 'tail', 'port')]
    [string]$Command = 'list',

    [Parameter(Position = 1)]
    [string]$Version,

    [Parameter(Position = 2)]
    [ValidateSet('client', 'server', 'all')]
    [string]$Kind = 'client'
)

$ErrorActionPreference = 'Stop'

# --- setup -------------------------------------------------------------------------------------

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

# Override with $env:CRAZYPHONE_JAVA_HOME if your JDK lives elsewhere - this default only matches
# this machine's own setup. gradlew.bat needs a real JAVA_HOME set; it won't reliably fall back to
# whatever's already on PATH for this project's toolchain requirements.
$JavaHomeDefault = 'C:\Users\yanni\.jdks\ms-21.0.12'
$JavaHome = if ($env:CRAZYPHONE_JAVA_HOME) { $env:CRAZYPHONE_JAVA_HOME } else { $JavaHomeDefault }
if (-not (Test-Path $JavaHome)) {
    Write-Warning "JAVA_HOME candidate '$JavaHome' does not exist on this machine."
    Write-Warning "Set `$env:CRAZYPHONE_JAVA_HOME to your own JDK 21 path, or edit the default at the top of this script."
}
$env:JAVA_HOME = $JavaHome

$StateDir = Join-Path $RepoRoot '.dev-launch'
$LogsDir = Join-Path $StateDir 'logs'
New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null

$Gradlew = Join-Path $RepoRoot 'gradlew.bat'

# --- version table -------------------------------------------------------------------------------
# runDir: where THIS version's own server.properties/eula.txt live, relative to the repo root - every
# node (NeoForge and Fabric alike) now gets its own versions/<node>/run[-server], since NeoForge's
# previous root-level run-<minecraftVersion>[-server] layout (build.gradle.kts's own gameDirectory
# config) was migrated to match Fabric's own long-standing convention (Loom's default), keeping the
# repo root free of one run-* pair per node.
$Versions = [ordered]@{
    '1.20.4'        = @{ Loader = 'neoforge'; Port = 25566; RunDir = 'versions/1.20.4/run-server';        ClientRunDir = 'versions/1.20.4/run' }
    '1.21.1'        = @{ Loader = 'neoforge'; Port = 25568; RunDir = 'versions/1.21.1/run-server';        ClientRunDir = 'versions/1.21.1/run' }
    '1.21.10'       = @{ Loader = 'neoforge'; Port = 25569; RunDir = 'versions/1.21.10/run-server';       ClientRunDir = 'versions/1.21.10/run' }
    '26.1'          = @{ Loader = 'neoforge'; Port = 25565; RunDir = 'versions/26.1/run-server';          ClientRunDir = 'versions/26.1/run' }
    '26.2'          = @{ Loader = 'neoforge'; Port = 25570; RunDir = 'versions/26.2/run-server';          ClientRunDir = 'versions/26.2/run' }
    '1.21.1-fabric' = @{ Loader = 'fabric';   Port = 25567; RunDir = 'versions/1.21.1-fabric/run-server'; ClientRunDir = 'versions/1.21.1-fabric/run' }
    '1.20.1-fabric' = @{ Loader = 'fabric';   Port = 25571; RunDir = 'versions/1.20.1-fabric/run-server'; ClientRunDir = 'versions/1.20.1-fabric/run' }
    '26.1-fabric'   = @{ Loader = 'fabric';   Port = 25572; RunDir = 'versions/26.1-fabric/run-server';   ClientRunDir = 'versions/26.1-fabric/run' }
    '26.2-fabric'   = @{ Loader = 'fabric';   Port = 25573; RunDir = 'versions/26.2-fabric/run-server';   ClientRunDir = 'versions/26.2-fabric/run' }
}

function Assert-Version {
    param([string]$V)
    if ([string]::IsNullOrWhiteSpace($V)) {
        Write-Error "Missing -Version. Run '.\scripts\dev-launch.ps1 list' to see available versions."
    }
    if (-not $Versions.Contains($V)) {
        Write-Error "Unknown version '$V'. Run '.\scripts\dev-launch.ps1 list' to see available versions."
    }
}

function Get-PidFile { param([string]$V, [string]$K) Join-Path $StateDir "$V-$K.pid" }
function Get-LogFile { param([string]$V, [string]$K) Join-Path $LogsDir "$V-$K.log" }

function Get-TrackedProcess {
    param([string]$V, [string]$K)
    $pidFile = Get-PidFile $V $K
    if (-not (Test-Path $pidFile)) { return $null }
    $procId = Get-Content $pidFile -ErrorAction SilentlyContinue
    if (-not $procId) { return $null }
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) { return $proc }
    return $null
}

# The tracked PID (Start-Process's own return value) is gradlew.bat's wrapper process - the actual
# game JVM is launched by Gradle's own (often reused, long-lived, DETACHED) daemon, so it's not a
# child of the tracked PID in the Windows process tree at all - `taskkill /T` on the wrapper alone
# does not reach it, confirmed live (killing the wrapper left the JVM running, still holding its
# own log file open).
#
# The run directory itself doesn't appear in the visible command line at all - both NeoForge
# (ModDevGradle) and Fabric (Loom) launch via an @argfile (clientRunProgramArgs.txt etc.), confirmed
# by inspecting a live process's own CommandLine. What IS always present and unique per Stonecutter
# node, on both loaders, is its own build output path on the classpath: every jar/class entry for
# THIS node's own compiled mod code lives under versions/<node>/build/... - no other node's JVM
# will ever have that exact substring, regardless of which run dir or daemon happened to launch it.
function Find-GameJvmProcessIds {
    param([string]$V, [string]$K)
    $needle = [regex]::Escape((Join-Path $RepoRoot "versions\$V\build"))
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match $needle) } |
        Select-Object -ExpandProperty ProcessId
}

# --- commands ------------------------------------------------------------------------------------

function Invoke-List {
    Write-Host ""
    "{0,-16} {1,-10} {2,-6}" -f 'VERSION', 'LOADER', 'PORT' | Write-Host
    foreach ($k in $Versions.Keys) {
        $v = $Versions[$k]
        "{0,-16} {1,-10} {2,-6}" -f $k, $v.Loader, $v.Port | Write-Host
    }
    Write-Host ""
    Write-Host "Usage: .\scripts\dev-launch.ps1 <client|server> <version>"
}

function Invoke-Start {
    param([string]$K)
    Assert-Version $Version
    $gradleTask = if ($K -eq 'server') { 'runServer' } else { 'runClient' }
    $existingJvmIds = @(Find-GameJvmProcessIds $Version $K)
    if ($existingJvmIds.Count -gt 0) {
        Write-Warning "$Version $K already has a running JVM (PID $($existingJvmIds -join ', ')). Use 'stop $Version $K' first if you want to relaunch."
        return
    }
    $log = Get-LogFile $Version $K
    if (Test-Path $log) { Remove-Item $log -Force }
    Write-Host "Launching :$Version`:$gradleTask -> $log"
    $errLog = "$log.err"
    if (Test-Path $errLog) { Remove-Item $errLog -Force }
    $proc = Start-Process -FilePath $Gradlew `
        -ArgumentList ":$Version`:$gradleTask", '--console=plain' `
        -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $log `
        -RedirectStandardError $errLog `
        -PassThru -WindowStyle Hidden
    $proc.Id | Set-Content (Get-PidFile $Version $K)
    if ($K -eq 'server') {
        $info = $Versions[$Version]
        Write-Host "PID $($proc.Id) - assigned port $($info.Port) (run '.\scripts\dev-launch.ps1 port $Version' once its run dir exists to apply it)"
    } else {
        Write-Host "PID $($proc.Id)"
    }
    Write-Host "Tail with:  .\scripts\dev-launch.ps1 tail $Version $K"
    Write-Host "Stop with:  .\scripts\dev-launch.ps1 stop $Version $K"
}

function Invoke-Status {
    Write-Host ""
    "{0,-16} {1,-8} {2,-10} {3,-10} {4}" -f 'VERSION', 'KIND', 'JVM PID', 'STATE', 'LOG' | Write-Host
    foreach ($k in $Versions.Keys) {
        foreach ($kind in @('client', 'server')) {
            $pidFile = Get-PidFile $k $kind
            if (-not (Test-Path $pidFile)) { continue }
            $jvmIds = @(Find-GameJvmProcessIds $k $kind)
            $state = if ($jvmIds.Count -gt 0) { 'running' } else { 'stopped' }
            $idDisplay = if ($jvmIds.Count -gt 0) { $jvmIds -join ',' } else { '-' }
            "{0,-16} {1,-8} {2,-10} {3,-10} {4}" -f $k, $kind, $idDisplay, $state, (Get-LogFile $k $kind) | Write-Host
        }
    }
    Write-Host ""
}

function Invoke-Stop {
    Assert-Version $Version
    $kinds = if ($Kind -eq 'all') { @('client', 'server') } else { @($Kind) }
    foreach ($k in $kinds) {
        $pidFile = Get-PidFile $Version $k
        $jvmIds = @(Find-GameJvmProcessIds $Version $k)
        if ($jvmIds.Count -eq 0) {
            Write-Host "$Version $k is not running."
            if (Test-Path $pidFile) { Remove-Item $pidFile -Force }
            continue
        }
        Write-Host "Stopping $Version $k (JVM PID $($jvmIds -join ', '))..."
        # Killing the actual game JVM(s) directly, found by command-line match - see
        # Find-GameJvmProcessIds's own doc comment on why the tracked wrapper PID's own process tree
        # doesn't reach it. Also best-effort tree-kills the tracked wrapper PID itself (cheap, and
        # cleans up the cmd.exe/gradlew.bat launcher shell if it's somehow still alive) - its own
        # "process not found" once already dead is expected, not an error.
        $wrapperProc = Get-TrackedProcess $Version $k
        if ($wrapperProc) {
            try { & taskkill /T /F /PID $wrapperProc.Id *>$null } catch {}
        }
        foreach ($jvmId in $jvmIds) {
            Stop-Process -Id $jvmId -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Tail {
    Assert-Version $Version
    $log = Get-LogFile $Version $Kind
    if (-not (Test-Path $log)) {
        Write-Error "No log yet for $Version $Kind - launch it first with '.\scripts\dev-launch.ps1 $Kind $Version'."
    }
    Get-Content -Path $log -Wait -Tail 40
}

function Invoke-Port {
    Assert-Version $Version
    $info = $Versions[$Version]
    $runDir = Join-Path $RepoRoot $info.RunDir
    if (-not (Test-Path $runDir)) {
        Write-Error "Run directory '$($info.RunDir)' doesn't exist yet - launch '.\scripts\dev-launch.ps1 server $Version' once first (it will fail to bind cleanly the first time if another node is already on this port, that's fine, it still creates the directory), then re-run this."
    }
    $propsPath = Join-Path $runDir 'server.properties'
    $eulaPath = Join-Path $runDir 'eula.txt'
    Set-Content -Path $eulaPath -Value 'eula=true' -Encoding utf8
    $port = $info.Port
    if (Test-Path $propsPath) {
        $lines = Get-Content $propsPath
        if ($lines -match '^server-port=') {
            $lines = $lines -replace '^server-port=.*$', "server-port=$port"
        } else {
            $lines += "server-port=$port"
        }
        Set-Content -Path $propsPath -Value $lines -Encoding utf8
    } else {
        Set-Content -Path $propsPath -Value "server-port=$port" -Encoding utf8
    }
    Write-Host "$Version server.properties -> server-port=$port, eula.txt -> true ($runDir)"
}

switch ($Command) {
    'list' { Invoke-List }
    'client' { Invoke-Start -K 'client' }
    'server' { Invoke-Start -K 'server' }
    'status' { Invoke-Status }
    'stop' { Invoke-Stop }
    'tail' { Invoke-Tail }
    'port' { Invoke-Port }
}
