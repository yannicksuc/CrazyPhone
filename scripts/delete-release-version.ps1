<#
.SYNOPSIS
  Delete a specific published version from Modrinth and/or point you at CurseForge's own deletion UI.

.DESCRIPTION
  Modrinth exposes a real delete endpoint (DELETE /v2/version/{id}, needs a token with the
  VERSION_DELETE scope) - this script calls it for you. CurseForge's own public Upload API has NO
  delete endpoint at all (confirmed: their API only supports uploading files - removing one is a
  website-only action, done from the file's own page on curseforge.com/console or
  authors.curseforge.com). For -Platform curseforge or both, this script only opens/prints the right
  page for you to finish manually - it never claims to delete anything there.

  This script never runs with your real token supplied by anyone but you - it reads
  $env:MODRINTH_TOKEN from YOUR OWN shell session. Set it yourself before running
  (`$env:MODRINTH_TOKEN = "mrp_..."`), the same way you'd set any other credential-bearing env var -
  it is never hardcoded here or passed in as a plain argument.

.PARAMETER VersionNumber
  The version string as shown on Modrinth (e.g. "1.2.3+neoforge-1.21.1"), OR a raw Modrinth version
  ID (e.g. "cXxUQYdj", the short id shown in "as version ID cXxUQYdj" after a successful upload).
  Either form works - if it doesn't look like a bare Modrinth id, this script looks it up by name
  first.

.PARAMETER Platform
  modrinth (default), curseforge, or both.

.PARAMETER ModrinthProjectId
  Defaults to "crazyphone". Only needed if resolving VersionNumber by name (skipped if you already
  passed a raw version id).

.EXAMPLE
  $env:MODRINTH_TOKEN = "mrp_..."
  .\scripts\delete-release-version.ps1 -VersionNumber "1.2.3+neoforge-1.21.1"
.EXAMPLE
  .\scripts\delete-release-version.ps1 -VersionNumber "1.2.4+neoforge-1.21.1" -Platform both
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$VersionNumber,

    [Parameter(Position = 1)]
    [ValidateSet('modrinth', 'curseforge', 'both')]
    [string]$Platform = 'modrinth',

    [string]$ModrinthProjectId = 'crazyphone'
)

$ErrorActionPreference = 'Stop'

function Resolve-ModrinthVersionId {
    param([string]$VersionOrId, [string]$ProjectId)
    # A raw Modrinth version id is always exactly 8 lowercase-alphanumeric characters (their own
    # base62 id shape, confirmed against every "as version ID XXXXXXXX" seen in this project's own
    # release logs) - anything else (a "+"-separated version_number label like this project uses) is
    # looked up by name instead of assumed to already be an id.
    if ($VersionOrId -match '^[A-Za-z0-9]{8}$') {
        return $VersionOrId
    }
    Write-Host "Looking up Modrinth version id for '$VersionOrId' on project '$ProjectId'..."
    # Authenticated, not anonymous - confirmed live that a project still "Under review" 404s on even
    # GET /project/{id} without a token, despite being visible on the website to its own logged-in
    # owner. The same token used for the delete call below already has read access as a side effect.
    $headers = if ($env:MODRINTH_TOKEN) { @{ Authorization = $env:MODRINTH_TOKEN } } else { @{} }
    $versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId/version" -Method Get -Headers $headers
    $match = $versions | Where-Object { $_.version_number -eq $VersionOrId -or $_.name -eq $VersionOrId }
    if (-not $match) {
        Write-Error "No Modrinth version found on project '$ProjectId' matching '$VersionOrId'. Check the exact version_number shown on the project's Versions tab."
    }
    if (@($match).Count -gt 1) {
        Write-Error "Multiple Modrinth versions matched '$VersionOrId' - pass the exact version id instead (shown as 'as version ID XXXXXXXX' when it was first uploaded)."
    }
    return $match.id
}

function Remove-ModrinthVersion {
    param([string]$VersionOrId, [string]$ProjectId)
    if (-not $env:MODRINTH_TOKEN) {
        Write-Error "`$env:MODRINTH_TOKEN is not set. Set it yourself first: `$env:MODRINTH_TOKEN = 'mrp_...' (your own Modrinth PAT with the 'Delete versions' scope)."
    }
    $id = Resolve-ModrinthVersionId -VersionOrId $VersionOrId -ProjectId $ProjectId
    Write-Host "Deleting Modrinth version $id ($VersionOrId)..."
    Invoke-RestMethod -Uri "https://api.modrinth.com/v2/version/$id" -Method Delete -Headers @{ Authorization = $env:MODRINTH_TOKEN }
    Write-Host "Deleted."
}

function Show-CurseForgeManualStep {
    param([string]$VersionOrId)
    Write-Host ""
    Write-Host "CurseForge has no delete API at all (confirmed - their Upload API only supports uploading)." -ForegroundColor Yellow
    Write-Host "Delete '$VersionOrId' yourself: https://authors.curseforge.com/#/projects/1449269/files"
    Write-Host "Find the matching file, open its own page, and use the delete option there."
    Write-Host ""
}

switch ($Platform) {
    'modrinth' { Remove-ModrinthVersion -VersionOrId $VersionNumber -ProjectId $ModrinthProjectId }
    'curseforge' { Show-CurseForgeManualStep -VersionOrId $VersionNumber }
    'both' {
        Remove-ModrinthVersion -VersionOrId $VersionNumber -ProjectId $ModrinthProjectId
        Show-CurseForgeManualStep -VersionOrId $VersionNumber
    }
}
