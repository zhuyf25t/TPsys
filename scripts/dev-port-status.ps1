[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Keep diagnostics usable in older hosts that do not allow changing encoding.
}

$Ports = @(5173, 8080)
$InterestingProcessNames = @("java", "javaw", "sbt", "node", "npm", "codex")

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

function Get-ListeningConnections {
  param([int]$Port)

  try {
    return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop)
  } catch {
    return @()
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
  if ($haystack -match "vite\s+build" -or $haystack -match "tsc\s+-p\s+frontend/tsconfig\.json") {
    return "Frontend build/check process"
  }
  if ($haystack -match "vite\.js" -or $haystack -match "\bvite\b" -or $haystack -match "vite --config frontend/vite\.config\.ts") {
    return "Vite dev server"
  }
  if ($haystack -match "codex") {
    return "Codex node/tooling"
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

  return "related process"
}

function Test-CommandContains {
  param(
    [AllowNull()][string]$Value,
    [string]$Pattern
  )

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return $false
  }

  return $Value -match $Pattern
}

Write-Host "Dev port status (read-only)"
Write-Host "No processes will be stopped by this script."
Write-Host ""

$portRows = @()
$listeningByPort = @{}

foreach ($port in $Ports) {
  $connections = Get-ListeningConnections -Port $port
  $listeningByPort[$port] = $connections

  if ($connections.Count -eq 0) {
    $portRows += [pscustomobject]@{
      Port = $port
      State = "Not listening"
      OwningProcess = ""
      ProcessName = ""
      Role = ""
      CommandLine = ""
    }
    continue
  }

  foreach ($connection in $connections) {
    $info = Get-ProcessInfoById -ProcessId ([int]$connection.OwningProcess)
    $role = Get-ProcessRole -Name $info.Name -CommandLine $info.CommandLine
    $portRows += [pscustomobject]@{
      Port = $port
      State = "Listen"
      OwningProcess = $connection.OwningProcess
      ProcessName = $info.Name
      Role = $role
      CommandLine = Shorten-Text -Value $info.CommandLine
    }
  }
}

Write-Host "Listening ports"
$portRows | Format-Table -AutoSize

$processRows = @()
try {
  $allProcesses = @(Get-CimInstance Win32_Process -ErrorAction Stop)
} catch {
  $allProcesses = @(Get-WmiObject Win32_Process -ErrorAction Stop)
}

foreach ($process in $allProcesses) {
  $name = [string]$process.Name
  $commandLine = [string]$process.CommandLine
  $role = Get-ProcessRole -Name $name -CommandLine $commandLine
  $isInterestingName = $false

  foreach ($interestingName in $InterestingProcessNames) {
    if ($name -match "^$([regex]::Escape($interestingName))(\.exe)?$") {
      $isInterestingName = $true
      break
    }
  }

  $isInterestingCommand = Test-CommandContains -Value $commandLine -Pattern "BackendHttp4sApp|backend:dev|vite|codex|sbt"

  if ($isInterestingName -or $isInterestingCommand) {
    $processRows += [pscustomobject]@{
      ProcessId = [int]$process.ProcessId
      Name = $name
      Role = $role
      CommandLine = Shorten-Text -Value $commandLine
    }
  }
}

Write-Host ""
Write-Host "java/sbt/node related process summary"
if ($processRows.Count -eq 0) {
  Write-Host "No related java/sbt/node processes found."
} else {
  $processRows | Sort-Object Name, ProcessId | Format-Table -AutoSize
}

$backendConnections = @($listeningByPort[8080])
$frontendConnections = @($listeningByPort[5173])
$backendRows = @($portRows | Where-Object { $_.Port -eq 8080 -and $_.State -eq "Listen" })
$frontendRows = @($portRows | Where-Object { $_.Port -eq 5173 -and $_.State -eq "Listen" })
$backendLooksRunning = $false
$frontendLooksRunning = $false

foreach ($row in $backendRows) {
  if ($row.CommandLine -match "BackendHttp4sApp" -or $row.Role -match "BackendHttp4sApp") {
    $backendLooksRunning = $true
  }
}

foreach ($row in $frontendRows) {
  if ($row.CommandLine -match "vite" -or $row.Role -match "Vite") {
    $frontendLooksRunning = $true
  }
}

Write-Host ""
Write-Host "Recommendations"
if ($backendConnections.Count -eq 0) {
  Write-Host "- 8080 is not listening: backend is not running; start it with npm run backend:dev."
} elseif ($backendLooksRunning) {
  Write-Host "- 8080 is listening and the command contains BackendHttp4sApp: backend is already running. Do not repeat sbt run. If you need compile, stop the old backend first or use a clean shell/environment."
} else {
  Write-Host "- 8080 is listening, but it was not identified as BackendHttp4sApp. Treat it as a port conflict until the owning process is confirmed."
}

if ($frontendConnections.Count -eq 0) {
  Write-Host "- 5173 is not listening: frontend is not running; start it with npm run dev."
} elseif ($frontendLooksRunning) {
  Write-Host "- 5173 is listening and looks like Vite: frontend is already running."
} else {
  Write-Host "- 5173 is listening, but it was not identified as Vite. Treat it as a port conflict until the owning process is confirmed."
}

Write-Host "- This script is diagnostic only. It never calls Stop-Process."
