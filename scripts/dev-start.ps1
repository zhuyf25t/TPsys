[CmdletBinding()]
param(
  [switch]$FrontendOnly,
  [switch]$BackendOnly,
  [switch]$OpenStatus
)

$ErrorActionPreference = "Stop"

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Keep output readable where the host does not allow changing encoding.
}

if ($FrontendOnly -and $BackendOnly) {
  Write-Error "Choose only one of -FrontendOnly or -BackendOnly."
  exit 1
}

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

function Shorten-Text {
  param(
    [AllowNull()][string]$Value,
    [int]$MaxLength = 140
  )

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return ""
  }

  $normalized = ($Value -replace "\s+", " ").Trim()
  if ($normalized.Length -le $MaxLength) {
    return $normalized
  }

  return $normalized.Substring(0, $MaxLength - 3) + "..."
}

function Get-ListeningConnections {
  param([int]$Port)

  try {
    return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop)
  } catch {
    return @()
  }
}

function Get-ProcessInfoById {
  param([int]$ProcessId)

  $process = $null
  try {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
  } catch {
    try {
      $process = Get-WmiObject Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
    } catch {
      $process = $null
    }
  }

  if ($null -ne $process) {
    return [pscustomobject]@{
      ProcessId = [int]$process.ProcessId
      Name = [string]$process.Name
      CommandLine = [string]$process.CommandLine
    }
  }

  try {
    $fallback = Get-Process -Id $ProcessId -ErrorAction Stop
    return [pscustomobject]@{
      ProcessId = [int]$fallback.Id
      Name = [string]$fallback.ProcessName
      CommandLine = ""
    }
  } catch {
    return [pscustomobject]@{
      ProcessId = $ProcessId
      Name = "<unknown>"
      CommandLine = ""
    }
  }
}

function Get-ProcessRole {
  param(
    [string]$Name,
    [string]$CommandLine
  )

  $haystack = "$Name $CommandLine"

  if ($haystack -match "runMain\s+(slaydemo\.backend\.http4s|services\.http4s|route)\.BackendHttp4sApp") {
    return "BackendHttp4sApp via sbt runMain"
  }
  if ($haystack -match "BackendHttp4sApp") {
    return "BackendHttp4sApp"
  }
  if ($haystack -match "npm(\.cmd)?\s+run\s+backend:dev") {
    return "npm backend:dev wrapper"
  }
  if ($haystack -match "vite\.js" -or $haystack -match "\bvite\b" -or $haystack -match "vite --config frontend/vite\.config\.ts") {
    return "Vite dev server"
  }
  if ($Name -match "java|javaw") {
    return "Java process"
  }
  if ($Name -match "sbt") {
    return "sbt process"
  }
  if ($Name -match "node") {
    return "Node process"
  }

  return "unidentified listener"
}

function Get-PortRows {
  param([int]$Port)

  $connections = Get-ListeningConnections -Port $Port
  $rows = @()

  foreach ($connection in $connections) {
    $info = Get-ProcessInfoById -ProcessId ([int]$connection.OwningProcess)
    $rows += [pscustomobject]@{
      Port = $Port
      ProcessId = $info.ProcessId
      Name = $info.Name
      Role = Get-ProcessRole -Name $info.Name -CommandLine $info.CommandLine
      CommandLine = Shorten-Text -Value $info.CommandLine
    }
  }

  return $rows
}

function Write-PortOccupied {
  param(
    [string]$ServiceName,
    [int]$Port,
    [object[]]$Rows
  )

  Write-Host "$ServiceName port $Port is already listening. No new process was started."
  $Rows | Format-Table -AutoSize | Out-Host
  Write-Host "Suggestion: run npm run dev:status to confirm the owner. Stop the old process manually only if you intend to replace it."
}

function Start-MissingService {
  param(
    [string]$ServiceName,
    [int]$Port,
    [string[]]$Arguments
  )

  $rows = @(Get-PortRows -Port $Port)
  if ($rows.Count -gt 0) {
    Write-PortOccupied -ServiceName $ServiceName -Port $Port -Rows $rows
    return $false
  }

  Write-Host "$ServiceName port $Port is not listening. Starting: npm $($Arguments -join ' ')"
  Start-Process -FilePath "npm.cmd" -ArgumentList $Arguments -WorkingDirectory $RepoRoot -WindowStyle Hidden
  return $true
}

Write-Host "Dev start (safe opt-in)"
Write-Host "This script starts missing services only. It never calls Stop-Process."
Write-Host ""

$startedAny = $false

if (-not $BackendOnly) {
  $startedAny = (Start-MissingService -ServiceName "Frontend" -Port 5173 -Arguments @("run", "dev", "--", "--host", "127.0.0.1")) -or $startedAny
}

if (-not $FrontendOnly) {
  $startedAny = (Start-MissingService -ServiceName "Backend" -Port 8080 -Arguments @("run", "backend:dev")) -or $startedAny
}

if ($startedAny) {
  Write-Host ""
  Write-Host "Waiting 2 seconds before status output..."
  Start-Sleep -Seconds 2
}

if ($OpenStatus) {
  Write-Host ""
  npm run dev:status
} else {
  Write-Host ""
  Write-Host "Use npm run dev:status for a detailed read-only port/process report."
}
