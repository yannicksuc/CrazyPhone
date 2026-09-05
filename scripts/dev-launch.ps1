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
  client  - launch a client for -Version (background, logged, PID-tracked), auto-joining -Version's
            dedicated server (see build.gradle.kts/build.fabric26.gradle.kts's own --quickPlayMultiplayer).
  client2 - launch a SECOND, differently-named client for -Version (same auto-join) - for testing
            calls/contacts/multiplayer features that need two players. NeoForge only for now (every
            NeoForge node's own "client2" run config; 1.21.1-fabric/1.20.1-fabric have no client2 run
            defined, only 26.1-fabric/26.2-fabric do).
  server  - launch a dedicated server for -Version (background, logged, PID-tracked).
  status  - show every tracked process and whether it's still alive.
  stop    - stop one tracked process (-Version + -Kind), or every tracked process (-Version all).
  tail    - live-follow one tracked process's log (Ctrl+C to stop following, doesn't kill it).
  port    - (re)apply the dev-server defaults to that version's run-server directory without
            launching it: eula=true, its assigned port, creative + peaceful, and the "dev-defaults"
            world datapack (eternal day, clear weather, op LordFinn) - see Initialize-ServerRunDir.
            `server <version>` already does this automatically before every launch, so this is only
            needed to fix a run dir by hand; changes take effect on the NEXT server launch.

.PARAMETER Version
  A node name from `list` (e.g. "1.20.4", "26.1", "1.21.1-fabric").

.PARAMETER Kind
  "client", "client2" or "server" - which tracked process for -Version to target. Defaults to
  "client". Only used by stop/tail; list/status show all three.

.EXAMPLE
  .\scripts\dev-launch.ps1 list
.EXAMPLE
  .\scripts\dev-launch.ps1 client 26.1
  .\scripts\dev-launch.ps1 client2 26.1
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
    [ValidateSet('list', 'client', 'client2', 'server', 'status', 'stop', 'tail', 'port')]
    [string]$Command = 'list',

    [Parameter(Position = 1)]
    [string]$Version,

    [Parameter(Position = 2)]
    [ValidateSet('client', 'client2', 'server', 'all')]
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
# Every node (NeoForge and Fabric alike) now uses the identical versions/<node>/run[-server] layout
# (see build.gradle.kts/build.fabric*.gradle.kts's own gameDirectory/runDir config), so RunDir/
# ClientRunDir below are derived from the node name itself rather than repeated per entry - only
# Loader and the assigned dedicated-server Port actually vary node to node.
$VersionMeta = [ordered]@{
    '1.20.4'        = @{ Loader = 'neoforge'; Port = 25566 }
    '1.21.1'        = @{ Loader = 'neoforge'; Port = 25568 }
    '1.21.10'       = @{ Loader = 'neoforge'; Port = 25569 }
    '26.1'          = @{ Loader = 'neoforge'; Port = 25565 }
    '26.2'          = @{ Loader = 'neoforge'; Port = 25570 }
    '1.21.1-fabric' = @{ Loader = 'fabric';   Port = 25567 }
    '1.20.1-fabric' = @{ Loader = 'fabric';   Port = 25571 }
    '26.1-fabric'   = @{ Loader = 'fabric';   Port = 25572 }
    '26.2-fabric'   = @{ Loader = 'fabric';   Port = 25573 }
}
$Versions = [ordered]@{}
foreach ($k in $VersionMeta.Keys) {
    $Versions[$k] = @{
        Loader       = $VersionMeta[$k].Loader
        Port         = $VersionMeta[$k].Port
        # +10000 keeps it out of the 25565-2557x range every node's own dedicated-server Port already
        # occupies above, same "one fixed slot per node, no collisions" reasoning as Port itself.
        RconPort     = $VersionMeta[$k].Port + 10000
        RunDir       = "versions/$k/run-server"
        ClientRunDir = "versions/$k/run"
    }
}
# Loopback-only dev servers (see server.properties' own online-mode=false) - this is exactly as sensitive
# as every other already-hardcoded dev credential in this script (nothing), just enough to let
# Invoke-Stop authenticate for a graceful "save-all" + "stop" before ever falling back to a hard kill.
$RconPassword = 'crazyphone-dev'

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
# game JVM is launched by Gradle's own (reused, long-lived) daemon, so it's generally not in the
# tracked PID's own process tree: the daemon is a child of whichever wrapper FIRST spawned it and
# an orphan for every wrapper after that. Killing a wrapper therefore usually leaves the JVM
# running (confirmed live, still holding its log open) - and tree-killing the one wrapper that
# happens to be the daemon's parent kills every game the daemon launched (also confirmed live).
# Hence: find the JVM by its command line, never by tree.
#
# The run directory itself doesn't appear in the visible command line at all - both NeoForge
# (ModDevGradle) and Fabric (Loom) launch via an @argfile (clientRunProgramArgs.txt etc.), confirmed
# by inspecting a live process's own CommandLine. What IS always present and unique per Stonecutter
# node, on both loaders, is its own build output path on the classpath: every jar/class entry for
# THIS node's own compiled mod code lives under versions/<node>/build/... - no other node's JVM
# will ever have that exact substring, regardless of which run dir or daemon happened to launch it.
#
# That path alone is shared by a node's client/client2/server JVMs, so the kind has to be told
# apart too, or `client X` refuses to start while `server X` runs (and `stop X client` kills the
# server). ModDevGradle (NeoForge) leaves a per-run-config marker on the visible command line -
# `@...\build\moddev\<runName>RunProgramArgs.txt` - and that's what distinguishes "client" from
# "client2" too (confirmed: the substring "clientRunProgramArgs" does NOT appear inside
# "client2RunProgramArgs", so the plain "client" pattern below can't accidentally match client2's
# JVM). Loom (Fabric) only sets `-Dfabric.dli.env=<kind>`/`Knot<Kind>`, identical for client and
# client2 - no client2 run exists on the older Fabric nodes anyway (1.21.1-fabric, 1.20.1-fabric),
# and on 26.1-fabric/26.2-fabric its fixed offline username (LordFinnAlt, see
# build.fabric26.gradle.kts) is used as the distinguishing needle instead.
function Find-GameJvmProcessIds {
    param([string]$V, [string]$K)
    $needle = [regex]::Escape((Join-Path $RepoRoot "versions\$V\build"))
    $kindNeedle = switch ($K) {
        'server' { 'serverRunProgramArgs|fabric\.dli\.env=server|KnotServer' }
        'client2' { 'client2RunProgramArgs|LordFinnAlt' }
        default { 'clientRunProgramArgs|(fabric\.dli\.env=client|KnotClient)(?!.*LordFinnAlt)' }
    }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match $needle) -and ($_.CommandLine -match $kindNeedle) } |
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
    $gradleTask = switch ($K) {
        'server' { 'runServer' }
        'client2' { 'runClient2' }
        default { 'runClient' }
    }
    $existingJvmIds = @(Find-GameJvmProcessIds $Version $K)
    if ($existingJvmIds.Count -gt 0) {
        Write-Warning "$Version $K already has a running JVM (PID $($existingJvmIds -join ', ')). Use 'stop $Version $K' first if you want to relaunch."
        return
    }
    if ($K -eq 'server') { Initialize-ServerRunDir $Version }
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
        Write-Host "PID $($proc.Id) - port $($info.Port), creative/peaceful, eternal day, clear weather, op LordFinn (see 'port' for details)"
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
        foreach ($kind in @('client', 'client2', 'server')) {
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
    $kinds = if ($Kind -eq 'all') { @('client', 'client2', 'server') } else { @($Kind) }
    foreach ($k in $kinds) {
        $pidFile = Get-PidFile $Version $k
        $jvmIds = @(Find-GameJvmProcessIds $Version $k)
        if ($jvmIds.Count -eq 0) {
            Write-Host "$Version $k is not running."
            if (Test-Path $pidFile) { Remove-Item $pidFile -Force }
            continue
        }
        Write-Host "Stopping $Version $k (JVM PID $($jvmIds -join ', '))..."
        if ($k -eq 'server') {
            $info = $Versions[$Version]
            Write-Host "  Requesting a graceful stop via RCON (save-all flush, then stop)..."
            $sent = Send-RconCommands -Port $info.RconPort -Password $RconPassword -Commands @('save-all flush', 'stop')
            if ($sent) {
                # Poll rather than sleep-then-check-once: a world with a lot to flush can take a few
                # seconds, and there's no reason to wait the full budget once it's already gone.
                $deadline = (Get-Date).AddSeconds(20)
                while ((Get-Date) -lt $deadline -and @(Find-GameJvmProcessIds $Version $k).Count -gt 0) {
                    Start-Sleep -Milliseconds 500
                }
                if (@(Find-GameJvmProcessIds $Version $k).Count -eq 0) {
                    Write-Host "  Stopped gracefully."
                    Remove-Item (Get-PidFile $Version $k) -Force -ErrorAction SilentlyContinue
                    continue
                }
                Write-Warning "  RCON stop didn't finish within 20s - falling back to a hard kill (SavedData written since the last autosave may still be lost)."
            } else {
                Write-Warning "  Couldn't reach RCON (server still starting up, or enable-rcon wasn't on for this run) - falling back to a hard kill (SavedData written since the last autosave may be lost). Run this same stop command again once the server's fully up to get a graceful one."
            }
        }
        # Kill the actual game JVM(s) directly, found by command-line match (see
        # Find-GameJvmProcessIds), then the tracked cmd.exe/gradlew.bat wrapper and ITS OWN
        # GradleWrapperMain java child - and nothing further down. Deliberately not `taskkill /T`:
        # the first wrapper to need a Gradle daemon becomes that daemon's parent process, and the
        # daemon then launches every later run of any node too, so tree-killing that wrapper takes
        # the daemon and every game it started down with it (confirmed live: `stop 26.1 server`
        # also killed the 26.1 client). Left alone, the daemon just cancels this one build once its
        # JVM is gone.
        foreach ($jvmId in $jvmIds) {
            Stop-Process -Id $jvmId -Force -ErrorAction SilentlyContinue
        }
        $wrapperProc = Get-TrackedProcess $Version $k
        if ($wrapperProc) {
            Get-CimInstance Win32_Process -Filter "ParentProcessId=$($wrapperProc.Id) AND Name='java.exe'" |
                Where-Object { $_.CommandLine -match 'GradleWrapperMain' } |
                ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            Stop-Process -Id $wrapperProc.Id -Force -ErrorAction SilentlyContinue
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

# --- RCON (graceful server stop) ------------------------------------------------------------------
# A dedicated server killed with Stop-Process never runs its own shutdown hooks, so any SavedData
# (crazyphone's phone/contact/conversation registries included - see PhoneRegistrySavedData/
# ConversationSavedData) that hasn't hit a periodic autosave yet is simply gone, not just delayed -
# confirmed live, repeatedly, the hard way. Minecraft's own RCON protocol (a small binary TCP protocol,
# not HTTP) is the only remote-command channel a Start-Process'd, non-interactive dedicated server
# actually exposes - its stdin isn't capturable for later writes the way Start-Process launches it (see
# Invoke-Start's own -RedirectStandardOutput/-RedirectStandardError, no -RedirectStandardInput target to
# write into afterward) - so Invoke-Stop below uses this to ask nicely (`save-all flush` then `stop`)
# before ever falling back to a hard kill.
function Send-RconPacket {
    param([System.Net.Sockets.NetworkStream]$Stream, [int]$RequestId, [int]$PacketType, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    # Payload layout: requestId(4) + type(4) + body + 2 null terminators (body's own + the packet's).
    $payloadLen = 4 + 4 + $bodyBytes.Length + 2
    $buf = New-Object byte[] (4 + $payloadLen)
    [Array]::Copy([BitConverter]::GetBytes($payloadLen), 0, $buf, 0, 4)
    [Array]::Copy([BitConverter]::GetBytes($RequestId), 0, $buf, 4, 4)
    [Array]::Copy([BitConverter]::GetBytes($PacketType), 0, $buf, 8, 4)
    [Array]::Copy($bodyBytes, 0, $buf, 12, $bodyBytes.Length)
    # Trailing 2 bytes stay 0 (both null terminators) - .NET already zero-fills a new byte[].
    $Stream.Write($buf, 0, $buf.Length)
    $Stream.Flush()
}

function Read-RconPacket {
    param([System.Net.Sockets.NetworkStream]$Stream)
    $lenBuf = New-Object byte[] 4
    $got = 0
    while ($got -lt 4) {
        $n = $Stream.Read($lenBuf, $got, 4 - $got)
        if ($n -le 0) { throw 'RCON connection closed while reading length' }
        $got += $n
    }
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    $buf = New-Object byte[] $len
    $got = 0
    while ($got -lt $len) {
        $n = $Stream.Read($buf, $got, $len - $got)
        if ($n -le 0) { throw 'RCON connection closed while reading payload' }
        $got += $n
    }
    $requestId = [BitConverter]::ToInt32($buf, 0)
    $bodyLen = $len - 4 - 4 - 2
    $body = if ($bodyLen -gt 0) { [System.Text.Encoding]::ASCII.GetString($buf, 8, $bodyLen) } else { '' }
    return @{ RequestId = $requestId; Body = $body }
}

# Best-effort: authenticates, then sends every command in order, ignoring each one's own response body
# (dev-launch only ever fires "save-all"/"stop", neither of which callers need the text of). Returns
# $true only if authentication actually succeeded - a truthy return here is what lets Invoke-Stop trust
# that "stop" was genuinely delivered instead of falling straight through to a hard kill.
function Send-RconCommands {
    param([int]$Port, [string]$Password, [string[]]$Commands, [int]$TimeoutMs = 3000)
    $client = $null
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $connectTask = $client.ConnectAsync('127.0.0.1', $Port)
        if (-not $connectTask.Wait($TimeoutMs)) { return $false }
        $stream = $client.GetStream()
        $stream.ReadTimeout = $TimeoutMs
        $stream.WriteTimeout = $TimeoutMs

        Send-RconPacket $stream 1 3 $Password   # SERVERDATA_AUTH
        $authResponse = Read-RconPacket $stream
        if ($authResponse.RequestId -eq -1) { return $false }   # bad rcon.password

        foreach ($cmd in $Commands) {
            Send-RconPacket $stream 2 2 $cmd     # SERVERDATA_EXECCOMMAND
            Read-RconPacket $stream | Out-Null
        }
        return $true
    } catch {
        return $false
    } finally {
        if ($client) { $client.Dispose() }
    }
}

# --- dev-server defaults -------------------------------------------------------------------------
# Every test server should come up the same way, ready to test in rather than to survive in:
# creative, peaceful, permanent daytime, permanent clear weather, LordFinn already op. The first
# three of those that server.properties can express are patched there (read on every start -
# `difficulty` is force-applied to the level by the dedicated server, `force-gamemode` re-applies
# creative on every join even to a player who was survival last time). Time, weather and the op
# are per-world state (level.dat / ops.json), so they're applied by a tiny world datapack whose
# #minecraft:load function reruns on every world load: `time set day` / `weather clear` reset
# whatever the previous session left, and `op LordFinn` resolves the UUID by NAME on the server
# itself, which is what makes it work on both the offline-UUID NeoForge clients (see
# build.gradle.kts' --uuid) and the real-Microsoft-account DevAuth fabric26 clients without
# hardcoding either UUID here. `op` needs permission level 3, so function-permission-level is
# raised to 4 (dev-only server, nothing else runs functions here).
#
# The datapack ships BOTH directory layouts because the singular rename happened in 1.21:
# data/<ns>/functions + tags/functions (<= 1.20.4) and data/<ns>/function + tags/function (>= 1.21);
# each version simply ignores the layout it doesn't know.

$DevOpPlayer = 'LordFinn'

# Windows PowerShell 5.1's `-Encoding utf8` writes a BOM, which Java's Properties loader keeps as
# part of the first key (so `eula=true` silently stops being `eula`) and Gson rejects outright in
# JSON - hence explicit BOM-less UTF-8 for everything the server itself parses.
function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

# Set key=value in a .properties line array, replacing the existing line or appending a new one.
function Set-PropertyLine {
    param([string[]]$Lines, [string]$Key, [string]$Value)
    $pattern = '^' + [regex]::Escape($Key) + '='
    if ($Lines -match $pattern) {
        return $Lines -replace ($pattern + '.*$'), "$Key=$Value"
    }
    return $Lines + "$Key=$Value"
}

function Initialize-ServerRunDir {
    param([string]$V)
    $info = $Versions[$V]
    $runDir = Join-Path $RepoRoot $info.RunDir
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    Write-Utf8NoBom (Join-Path $runDir 'eula.txt') "eula=true`n"

    $propsPath = Join-Path $runDir 'server.properties'
    $lines = if (Test-Path $propsPath) { @(Get-Content $propsPath) } else { @() }
    $lines = Set-PropertyLine $lines 'server-port' $info.Port
    $lines = Set-PropertyLine $lines 'gamemode' 'creative'
    $lines = Set-PropertyLine $lines 'force-gamemode' 'true'
    $lines = Set-PropertyLine $lines 'difficulty' 'peaceful'
    $lines = Set-PropertyLine $lines 'function-permission-level' '4'
    # Lets Invoke-Stop ask for a real "save-all flush" + "stop" instead of a hard kill - see Send-RconCommands'
    # own doc comment for why that distinction actually matters (SavedData surviving a restart at all).
    $lines = Set-PropertyLine $lines 'enable-rcon' 'true'
    $lines = Set-PropertyLine $lines 'rcon.port' $info.RconPort
    $lines = Set-PropertyLine $lines 'rcon.password' $RconPassword
    $lines = Set-PropertyLine $lines 'broadcast-rcon-to-ops' 'false'
    Write-Utf8NoBom $propsPath (($lines -join "`n") + "`n")

    # The datapack goes in the world the server will actually load - honour a custom level-name.
    $levelName = 'world'
    $levelLine = $lines | Where-Object { $_ -match '^level-name=(.+)$' } | Select-Object -First 1
    if ($levelLine -and $levelLine -match '^level-name=(.+)$') { $levelName = $Matches[1].Trim() }
    $packDir = Join-Path $runDir "$levelName\datapacks\dev-defaults"

    # Two functions rather than one: a single .mcfunction is all-or-nothing at parse time, and one
    # unknown command makes the WHOLE #minecraft:load tag fail to resolve (nothing runs at all). The
    # gamerule ids were renamed in 26.x (doDaylightCycle -> advance_time, doWeatherCycle ->
    # advance_weather, per 26.1's own GameRuleRegistryFix), so the two freeze rules are split out
    # into their own function, written with the naming this node's Minecraft version actually knows.
    # The tag also marks both entries "required": false, so a mismatch degrades to "the freeze rules
    # didn't apply" instead of "nothing in the pack ran".
    $isModernGameRules = ($V -match '^(\d+)\.') -and ([int]$Matches[1] -ge 26)
    $gameRuleLines = if ($isModernGameRules) {
        @('gamerule advance_time false', 'gamerule advance_weather false')
    } else {
        @('gamerule doDaylightCycle false', 'gamerule doWeatherCycle false')
    }
    $functions = @{
        'defaults'  = @(
            'defaultgamemode creative',
            'difficulty peaceful',
            'time set day',
            'weather clear',
            "op $DevOpPlayer",
            "say [dev-defaults] creative, peaceful, eternal day, clear weather, op $DevOpPlayer"
        )
        'gamerules' = $gameRuleLines
    }
    # pack_format 15 = 1.20.1, the oldest node; supported_formats (1.20.2+) and min/max_format
    # (1.21.9+) both declare "up to anything newer" so no version flags it as incompatible.
    $mcmeta = '{"pack":{"pack_format":15,"supported_formats":[15,9999],"min_format":15,"max_format":9999,' +
        '"description":"CrazyPhone dev-server defaults - written by scripts/dev-launch.ps1, do not edit"}}'
    $loadTag = '{"values":[' + (($functions.Keys | Sort-Object | ForEach-Object { "{`"id`":`"crazyphone_dev:$_`",`"required`":false}" }) -join ',') + ']}'

    Write-Utf8NoBom (Join-Path $packDir 'pack.mcmeta') $mcmeta
    foreach ($name in $functions.Keys) {
        $body = ($functions[$name] -join "`n") + "`n"
        Write-Utf8NoBom (Join-Path $packDir "data\crazyphone_dev\function\$name.mcfunction") $body
        Write-Utf8NoBom (Join-Path $packDir "data\crazyphone_dev\functions\$name.mcfunction") $body
    }
    Write-Utf8NoBom (Join-Path $packDir 'data\minecraft\tags\function\load.json') $loadTag
    Write-Utf8NoBom (Join-Path $packDir 'data\minecraft\tags\functions\load.json') $loadTag

    Write-Host "$V dev-server defaults applied to $($info.RunDir): port $($info.Port), eula, creative (forced), peaceful, dev-defaults datapack (eternal day, clear weather, op $DevOpPlayer)"
}

function Invoke-Port {
    Assert-Version $Version
    Initialize-ServerRunDir $Version
    Write-Host "Takes effect on the next '.\scripts\dev-launch.ps1 server $Version'."
}

switch ($Command) {
    'list' { Invoke-List }
    'client' { Invoke-Start -K 'client' }
    'client2' { Invoke-Start -K 'client2' }
    'server' { Invoke-Start -K 'server' }
    'status' { Invoke-Status }
    'stop' { Invoke-Stop }
    'tail' { Invoke-Tail }
    'port' { Invoke-Port }
}
