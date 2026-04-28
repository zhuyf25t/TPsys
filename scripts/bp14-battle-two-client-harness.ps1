[CmdletBinding()]
param(
  [string]$FrontendUrl = "http://127.0.0.1:5173",
  [string]$Password = "bp14-pass",
  [string]$BrowserPath,
  [switch]$KeepProfiles
)

$ErrorActionPreference = "Stop"

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Older PowerShell hosts may not allow changing output encoding.
}

function Get-WorkspaceRoot {
  return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).ProviderPath
}

function Test-PathIsUnderRoot {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Root
  )

  $fullPath = [System.IO.Path]::GetFullPath($Path)
  $fullRoot = [System.IO.Path]::GetFullPath($Root)
  if (-not $fullRoot.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
    $fullRoot = "$fullRoot$([System.IO.Path]::DirectorySeparatorChar)"
  }

  return $fullPath.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)
}

function Reset-Bp14RuntimeDir {
  param(
    [Parameter(Mandatory = $true)][string]$RuntimeDir,
    [Parameter(Mandatory = $true)][string]$WorkspaceRoot
  )

  if ($KeepProfiles) {
    New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
    return
  }

  if (Test-Path -LiteralPath $RuntimeDir) {
    if (-not (Test-PathIsUnderRoot -Path $RuntimeDir -Root $WorkspaceRoot)) {
      throw "Refusing to clean runtime dir outside workspace: $RuntimeDir"
    }

    Remove-Item -LiteralPath $RuntimeDir -Recurse -Force
  }

  New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
}

function Resolve-BrowserPath {
  param([string]$RequestedPath)

  if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
    $command = Get-Command $RequestedPath -ErrorAction SilentlyContinue
    if ($null -ne $command) {
      return $command.Source
    }

    if (Test-Path -LiteralPath $RequestedPath -PathType Leaf) {
      return (Resolve-Path -LiteralPath $RequestedPath).ProviderPath
    }

    throw "BrowserPath not found: $RequestedPath"
  }

  foreach ($commandName in @("msedge", "chrome", "chrome.exe", "msedge.exe")) {
    $command = Get-Command $commandName -ErrorAction SilentlyContinue
    if ($null -ne $command) {
      return $command.Source
    }
  }

  $candidates = @(
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
  )

  foreach ($candidate in $candidates) {
    if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
      return (Resolve-Path -LiteralPath $candidate).ProviderPath
    }
  }

  throw "Could not find msedge or chrome. Pass -BrowserPath explicitly."
}

function New-ClientUrl {
  param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Handle,
    [Parameter(Mandatory = $true)][string]$PasswordValue,
    [Parameter(Mandatory = $true)][string]$Skin
  )

  $encodedHandle = [System.Uri]::EscapeDataString($Handle)
  $encodedPassword = [System.Uri]::EscapeDataString($PasswordValue)
  $encodedSkin = [System.Uri]::EscapeDataString($Skin)
  $encodedTarget = [System.Uri]::EscapeDataString("/battle?new=1")
  $normalizedBaseUrl = $BaseUrl.Trim().TrimEnd("/")
  if ([string]::IsNullOrWhiteSpace($normalizedBaseUrl)) {
    throw "FrontendUrl is empty. Example: -FrontendUrl http://127.0.0.1:5173"
  }

  return "$normalizedBaseUrl/bp14-client.html?handle=$encodedHandle&password=$encodedPassword&skin=$encodedSkin&target=$encodedTarget"
}

function Start-Bp14Browser {
  param(
    [Parameter(Mandatory = $true)][string]$BrowserExe,
    [Parameter(Mandatory = $true)][string]$ProfileDir,
    [Parameter(Mandatory = $true)][string]$Url
  )

  New-Item -ItemType Directory -Force -Path $ProfileDir | Out-Null

  $arguments = @(
    "--user-data-dir=`"$ProfileDir`"",
    "--no-first-run",
    "--new-window",
    "`"$Url`""
  )

  Start-Process -FilePath $BrowserExe -ArgumentList $arguments | Out-Null
}

$workspaceRoot = Get-WorkspaceRoot
$runtimeDir = Join-Path $workspaceRoot ".runtime\bp14"
$clientADir = Join-Path $runtimeDir "client-a"
$clientBDir = Join-Path $runtimeDir "client-b"
$stamp = Get-Date -Format "HHmmssfff"
$clientAHandle = "bp14_a_$stamp"
$clientBHandle = "bp14_b_$stamp"

Reset-Bp14RuntimeDir -RuntimeDir $runtimeDir -WorkspaceRoot $workspaceRoot

$browserExe = Resolve-BrowserPath -RequestedPath $BrowserPath
$clientAUrl = New-ClientUrl -BaseUrl $FrontendUrl -Handle $clientAHandle -PasswordValue $Password -Skin "blue"
$clientBUrl = New-ClientUrl -BaseUrl $FrontendUrl -Handle $clientBHandle -PasswordValue $Password -Skin "soldier"

Start-Bp14Browser -BrowserExe $browserExe -ProfileDir $clientADir -Url $clientAUrl
Start-Bp14Browser -BrowserExe $browserExe -ProfileDir $clientBDir -Url $clientBUrl

Write-Host ""
Write-Host "BP-14 two-client manual harness launched." -ForegroundColor Cyan
Write-Host "Browser: $browserExe"
Write-Host "Profiles: $runtimeDir"
Write-Host ""
Write-Host "Checklist:"
Write-Host "1. Ensure backend and Vite dev server are running before using this harness."
Write-Host "2. Each window should bootstrap auth, then redirect to /battle?new=1."
Write-Host "3. Verify both windows join the same room/battle."
Write-Host "4. Verify movement and fire from one client are visible in the other."
Write-Host ""
Write-Host "Client A: $clientAHandle (blue)"
Write-Host "Client B: $clientBHandle (soldier)"
