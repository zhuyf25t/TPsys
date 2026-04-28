[CmdletBinding()]
param(
  [string]$FrontendUrl = "http://127.0.0.1:5173",
  [string]$BackendUrl = "http://127.0.0.1:5173/api",
  [string]$Password = "bp40-pass",
  [string]$BrowserPath,
  [int]$PlayingTimeoutSeconds = 35,
  [int]$FreshElapsedMaxMs = 15000,
  [switch]$KeepProfile
)

$ErrorActionPreference = "Stop"

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

function Reset-RuntimeDir {
  param(
    [Parameter(Mandatory = $true)][string]$RuntimeDir,
    [Parameter(Mandatory = $true)][string]$WorkspaceRoot
  )

  if ($KeepProfile) {
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

function Normalize-BaseUrl {
  param([string]$Value)

  $normalized = $Value.Trim().TrimEnd("/")
  if ([string]::IsNullOrWhiteSpace($normalized)) {
    throw "Base URL is empty."
  }

  return $normalized
}

function Join-TestUrl {
  param(
    [Parameter(Mandatory = $true)][string]$Base,
    [Parameter(Mandatory = $true)][string]$Path
  )

  $normalizedBase = Normalize-BaseUrl $Base
  if ($Path.StartsWith("/")) {
    return "$normalizedBase$Path"
  }

  return "$normalizedBase/$Path"
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

  foreach ($commandName in @("msedge", "msedge.exe", "chrome", "chrome.exe")) {
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

  throw "Could not find Microsoft Edge or Google Chrome. Pass -BrowserPath explicitly."
}

function Get-FreeTcpPort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  try {
    $listener.Start()
    return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
  } finally {
    $listener.Stop()
  }
}

function New-ClientUrl {
  param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Handle,
    [Parameter(Mandatory = $true)][string]$PasswordValue,
    [Parameter(Mandatory = $true)][string]$Target
  )

  $encodedHandle = [System.Uri]::EscapeDataString($Handle)
  $encodedPassword = [System.Uri]::EscapeDataString($PasswordValue)
  $encodedTarget = [System.Uri]::EscapeDataString($Target)
  return "$(Normalize-BaseUrl $BaseUrl)/bp14-client.html?handle=$encodedHandle&password=$encodedPassword&skin=blue&target=$encodedTarget"
}

function Start-CdpBrowser {
  param(
    [Parameter(Mandatory = $true)][string]$BrowserExe,
    [Parameter(Mandatory = $true)][string]$ProfileDir,
    [Parameter(Mandatory = $true)][int]$DebugPort,
    [Parameter(Mandatory = $true)][string]$Url
  )

  New-Item -ItemType Directory -Force -Path $ProfileDir | Out-Null

  $arguments = @(
    "--remote-debugging-port=$DebugPort",
    "--user-data-dir=`"$ProfileDir`"",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-background-networking",
    "--disable-background-timer-throttling",
    "--disable-renderer-backgrounding",
    "--disable-features=CalculateNativeWinOcclusion",
    "--window-size=1280,800",
    "--headless=new",
    "--disable-gpu",
    "`"$Url`""
  )

  return Start-Process -FilePath $BrowserExe -ArgumentList $arguments -PassThru -WindowStyle Hidden
}

function Wait-CdpTarget {
  param(
    [Parameter(Mandatory = $true)][int]$DebugPort,
    [Parameter(Mandatory = $true)][string]$Label,
    [string]$TargetId = "",
    [int]$TimeoutSeconds = 15
  )

  $endpoint = "http://127.0.0.1:$DebugPort/json/list"
  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastError = $null

  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    try {
      $targets = Invoke-RestMethod -Uri $endpoint -TimeoutSec 2 -ErrorAction Stop
      foreach ($target in @($targets)) {
        $idMatches = [string]::IsNullOrWhiteSpace($TargetId) -or $target.id -eq $TargetId
        if (
          $idMatches -and
          $target.type -eq "page" -and
          -not [string]::IsNullOrWhiteSpace($target.webSocketDebuggerUrl)
        ) {
          return $target
        }
      }
    } catch {
      $lastError = $_.Exception.Message
    }

    Start-Sleep -Milliseconds 200
  }

  if ([string]::IsNullOrWhiteSpace($lastError)) {
    throw "CDP target for $Label was not available on port $DebugPort."
  }

  throw "CDP target for $Label was not available on port $DebugPort. Last error: $lastError"
}

function Connect-Cdp {
  param(
    [Parameter(Mandatory = $true)][string]$WebSocketUrl,
    [Parameter(Mandatory = $true)][string]$Label
  )

  $socket = [System.Net.WebSockets.ClientWebSocket]::new()
  $socket.Options.KeepAliveInterval = [TimeSpan]::FromSeconds(15)
  $null = $socket.ConnectAsync([System.Uri]$WebSocketUrl, [System.Threading.CancellationToken]::None).GetAwaiter().GetResult()

  return [pscustomobject]@{
    Label = $Label
    Socket = $socket
    NextId = 0
  }
}

function Close-Cdp {
  param($Client)

  if ($null -eq $Client -or $null -eq $Client.Socket) {
    return
  }

  try {
    if ($Client.Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
      $null = $Client.Socket.CloseAsync(
        [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
        "bp40 browser smoke complete",
        [System.Threading.CancellationToken]::None
      ).GetAwaiter().GetResult()
    }
  } catch {
  } finally {
    $Client.Socket.Dispose()
  }
}

function Receive-CdpMessage {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [int]$TimeoutSeconds = 15
  )

  $buffer = New-Object byte[] 65536
  $stream = [System.IO.MemoryStream]::new()
  try {
    do {
      $segment = [ArraySegment[byte]]::new($buffer)
      $cts = [System.Threading.CancellationTokenSource]::new()
      $cts.CancelAfter([TimeSpan]::FromSeconds($TimeoutSeconds))
      try {
        $result = $Client.Socket.ReceiveAsync($segment, $cts.Token).GetAwaiter().GetResult()
      } catch {
        throw "Timed out waiting for CDP response from $($Client.Label). $($_.Exception.Message)"
      } finally {
        $cts.Dispose()
      }

      if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
        throw "CDP socket closed for $($Client.Label)."
      }

      $stream.Write($buffer, 0, $result.Count)
    } while (-not $result.EndOfMessage)

    $json = [System.Text.Encoding]::UTF8.GetString($stream.ToArray())
    return $json | ConvertFrom-Json
  } finally {
    $stream.Dispose()
  }
}

function Invoke-CdpCommand {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Method,
    [object]$Params = $null,
    [int]$TimeoutSeconds = 15
  )

  $Client.NextId = [int]$Client.NextId + 1
  $id = [int]$Client.NextId
  $message = [ordered]@{
    id = $id
    method = $Method
  }
  if ($null -ne $Params) {
    $message.params = $Params
  }

  $json = $message | ConvertTo-Json -Depth 64 -Compress
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
  $segment = [ArraySegment[byte]]::new($bytes)
  $null = $Client.Socket.SendAsync(
    $segment,
    [System.Net.WebSockets.WebSocketMessageType]::Text,
    $true,
    [System.Threading.CancellationToken]::None
  ).GetAwaiter().GetResult()

  for ($attempt = 0; $attempt -lt 500; $attempt++) {
    $response = Receive-CdpMessage -Client $Client -TimeoutSeconds $TimeoutSeconds
    if ($response.id -ne $id) {
      continue
    }

    if ($null -ne $response.error) {
      throw "CDP $Method failed for $($Client.Label): $($response.error.message)"
    }

    return $response
  }

  throw "CDP $Method did not receive a matching response for $($Client.Label)."
}

function Invoke-CdpEvaluate {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Expression,
    [switch]$AwaitPromise,
    [int]$TimeoutSeconds = 15
  )

  $params = @{
    expression = $Expression
    returnByValue = $true
    userGesture = $true
  }
  if ($AwaitPromise) {
    $params.awaitPromise = $true
  }

  $response = Invoke-CdpCommand -Client $Client -Method "Runtime.evaluate" -Params $params -TimeoutSeconds $TimeoutSeconds
  if ($null -ne $response.result.exceptionDetails) {
    $details = $response.result.exceptionDetails
    $description = $details.text
    if ($null -ne $details.exception -and -not [string]::IsNullOrWhiteSpace($details.exception.description)) {
      $description = $details.exception.description
    }
    throw "Runtime.evaluate failed for $($Client.Label): $description"
  }

  $remote = $response.result.result
  if ($null -eq $remote) {
    return $null
  }

  $valueProperty = $remote.PSObject.Properties["value"]
  if ($null -ne $valueProperty) {
    return $valueProperty.Value
  }

  return $remote
}

function Initialize-CdpPage {
  param($Client)

  Invoke-CdpCommand -Client $Client -Method "Page.enable" -Params @{} | Out-Null
  Invoke-CdpCommand -Client $Client -Method "Runtime.enable" -Params @{} | Out-Null
}

function Get-PageBattleContext {
  param($Client)

  $expression = @'
(() => {
  const readSessions = (prefix) => {
    const sessions = [];
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index);
      if (!key || !key.startsWith(prefix)) {
        continue;
      }
      const raw = window.localStorage.getItem(key);
      try {
        const parsed = JSON.parse(raw || "null");
        sessions.push({
          key,
          battleId: String(parsed?.battleId || ""),
          elapsedMs: Number(parsed?.snapshot?.elapsedMs ?? NaN),
          savedAt: Number(parsed?.savedAt ?? NaN),
          sharedAuthoritativeRuntime: parsed?.sharedAuthoritativeRuntime === true,
          localAuthoritativePlayerId: String(parsed?.localAuthoritativePlayerId || ""),
          localAuthoritativeTicketId: String(parsed?.localAuthoritativeTicketId || "")
        });
      } catch {
        sessions.push({ key, battleId: "", elapsedMs: NaN, savedAt: NaN, parseError: true });
      }
    }
    sessions.sort((left, right) => left.key.localeCompare(right.key));
    return sessions;
  };
  const phase = document.querySelector(".arena-shell--playing")
    ? "playing"
    : document.querySelector(".arena-shell--matching")
      ? "matching"
      : document.querySelector(".arena-shell--settled")
        ? "settled"
        : "unknown";
  return {
    href: window.location.href,
    phase,
    timer: document.querySelector(".hud-timer")?.textContent?.trim() || "",
    activeSessions: readSessions("slay-demo.active-battle-session.v2."),
    completedSessions: readSessions("slay-demo.completed-battle-session.v2.")
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression
}

function Wait-PagePlaying {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$TimeoutSeconds = 35
  )

  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastContext = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $lastContext = Get-PageBattleContext -Client $Client
    if ($lastContext.phase -eq "playing" -and $lastContext.timer -match "^\d{2}:\d{2}$") {
      return $lastContext
    }

    Start-Sleep -Milliseconds 300
  }

  $serialized = if ($null -eq $lastContext) { "<none>" } else { $lastContext | ConvertTo-Json -Depth 8 -Compress }
  throw "$Label did not reach playing with HUD timer. Last context: $serialized"
}

function Wait-ActiveSessionForBattle {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$BattleId,
    [int]$TimeoutSeconds = 8
  )

  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastContext = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $lastContext = Get-PageBattleContext -Client $Client
    $matching = @($lastContext.activeSessions | Where-Object { $_.battleId -eq $BattleId })
    if ($matching.Count -gt 0) {
      return $lastContext
    }

    Start-Sleep -Milliseconds 250
  }

  $serialized = if ($null -eq $lastContext) { "<none>" } else { $lastContext | ConvertTo-Json -Depth 8 -Compress }
  throw "$Label did not persist active session for battleId=$BattleId. Last context: $serialized"
}

function Wait-LatestActiveSession {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$TimeoutSeconds = 8
  )

  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastContext = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $lastContext = Get-PageBattleContext -Client $Client
    $latest = Get-LatestActiveSession $lastContext
    if ($null -ne $latest -and -not [string]::IsNullOrWhiteSpace($latest.battleId)) {
      return $lastContext
    }

    Start-Sleep -Milliseconds 250
  }

  $serialized = if ($null -eq $lastContext) { "<none>" } else { $lastContext | ConvertTo-Json -Depth 8 -Compress }
  throw "$Label did not persist any active session. Last context: $serialized"
}

function Get-LatestActiveSession {
  param([Parameter(Mandatory = $true)]$Context)

  $sessions = @($Context.activeSessions)
  if ($sessions.Count -eq 0) {
    return $null
  }

  return $sessions | Sort-Object -Property savedAt -Descending | Select-Object -First 1
}

function Get-BattleState {
  param(
    [Parameter(Mandatory = $true)][string]$BattleId,
    [Parameter(Mandatory = $true)][string]$BackendBase
  )

  $encoded = [System.Uri]::EscapeDataString($BattleId)
  return Invoke-RestMethod -Method Get -Uri "$(Normalize-BaseUrl $BackendBase)/battle/state/$encoded" -TimeoutSec 8
}

function Convert-TimerToElapsedMs {
  param(
    [string]$Timer,
    [int64]$DurationMs
  )

  if ($Timer -notmatch "^(\d{2}):(\d{2})$") {
    return $null
  }

  $remainingMs = (([int64]$Matches[1] * 60L) + [int64]$Matches[2]) * 1000L
  return [Math]::Max(0L, [int64]$DurationMs - $remainingMs)
}

function Assert-TimerFreshness {
  param(
    [Parameter(Mandatory = $true)]$Context,
    [Parameter(Mandatory = $true)]$State,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$ToleranceMs = 2500
  )

  $timerElapsedMs = Convert-TimerToElapsedMs -Timer ([string]$Context.timer) -DurationMs ([int64]$State.durationMs)
  Assert-Condition ($null -ne $timerElapsedMs) "$Label HUD timer is not parseable: '$($Context.timer)'."
  $deltaMs = [Math]::Abs([int64]$timerElapsedMs - [int64]$State.elapsedMs)
  Assert-Condition (
    $deltaMs -le $ToleranceMs
  ) "$Label HUD timer does not match backend elapsed: timer=$($Context.timer), timerElapsedMs=$timerElapsedMs, backendElapsedMs=$($State.elapsedMs), battleId=$($State.battleId)."
}

function Assert-Condition {
  param(
    [bool]$Condition,
    [string]$Message
  )

  if (-not $Condition) {
    throw $Message
  }
}

function Stop-SmokeProcess {
  param($Process)

  if ($null -eq $Process) {
    return
  }

  try {
    if (-not $Process.HasExited) {
      Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
  } catch {
  }
}

$frontendBase = Normalize-BaseUrl $FrontendUrl
$backendBase = Normalize-BaseUrl $BackendUrl
$workspaceRoot = Get-WorkspaceRoot
$runtimeDir = Join-Path $workspaceRoot ".runtime\bp40-browser-session-freshness"
$client = $null
$secondClient = $null
$process = $null

try {
  Write-Host "BP-40C browser session freshness smoke"
  Write-Host "Frontend: $frontendBase"
  Write-Host "Backend: $backendBase"

  $health = Invoke-RestMethod -Uri (Join-TestUrl -Base $backendBase -Path "/health") -TimeoutSec 8
  Assert-Condition ($health.status -eq "ok") "Backend /health did not return status=ok."
  Invoke-WebRequest -Uri $frontendBase -UseBasicParsing -TimeoutSec 8 | Out-Null

  Reset-RuntimeDir -RuntimeDir $runtimeDir -WorkspaceRoot $workspaceRoot
  $browserExe = Resolve-BrowserPath -RequestedPath $BrowserPath
  $debugPort = Get-FreeTcpPort
  $runSuffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
  $handle = "bp40c$runSuffix"
  $firstUrl = New-ClientUrl -BaseUrl $frontendBase -Handle $handle -PasswordValue $Password -Target "/battle?new=1"

  Write-Host "Launching first tab for $handle..."
  $process = Start-CdpBrowser -BrowserExe $browserExe -ProfileDir $runtimeDir -DebugPort $debugPort -Url $firstUrl
  $target = Wait-CdpTarget -DebugPort $debugPort -Label "tabA"
  $client = Connect-Cdp -WebSocketUrl $target.webSocketDebuggerUrl -Label "tabA"
  Initialize-CdpPage -Client $client

  Wait-PagePlaying -Client $client -Label "tabA" -TimeoutSeconds $PlayingTimeoutSeconds | Out-Null
  $firstPersisted = Wait-LatestActiveSession -Client $client -Label "tabA"
  $firstSession = Get-LatestActiveSession $firstPersisted
  Assert-Condition ($null -ne $firstSession) "First tab did not expose an active session."
  $firstState = Get-BattleState -BackendBase $backendBase -BattleId $firstSession.battleId
  Assert-TimerFreshness -Context $firstPersisted -State $firstState -Label "First /battle?new=1"

  Write-Host "First battle: id=$($firstSession.battleId) timer=$($firstPersisted.timer) localElapsedMs=$($firstSession.elapsedMs) backendElapsedMs=$($firstState.elapsedMs)"
  Start-Sleep -Milliseconds 2500

  Write-Host "Opening same-profile second tab via /battle?new=1..."
  $secondUrl = Join-TestUrl -Base $frontendBase -Path "/battle?new=1"
  $created = Invoke-CdpCommand -Client $client -Method "Target.createTarget" -Params @{ url = $secondUrl } -TimeoutSeconds 8
  $secondTargetId = [string]$created.result.targetId
  $secondTarget = Wait-CdpTarget -DebugPort $debugPort -Label "tabB" -TargetId $secondTargetId
  $secondClient = Connect-Cdp -WebSocketUrl $secondTarget.webSocketDebuggerUrl -Label "tabB"
  Initialize-CdpPage -Client $secondClient

  $secondPlaying = Wait-PagePlaying -Client $secondClient -Label "tabB" -TimeoutSeconds $PlayingTimeoutSeconds
  $secondSession = Get-LatestActiveSession $secondPlaying
  Assert-Condition ($null -ne $secondSession) "Second tab did not expose an active session."
  $secondState = Get-BattleState -BackendBase $backendBase -BattleId $secondSession.battleId

  Assert-Condition ($secondSession.battleId -ne $firstSession.battleId) "Second /battle?new=1 reused first battleId=$($firstSession.battleId)."
  Assert-Condition ([int64]$secondState.elapsedMs -le $FreshElapsedMaxMs) "Second backend elapsed is not fresh: battleId=$($secondSession.battleId) elapsedMs=$($secondState.elapsedMs)."
  Assert-TimerFreshness -Context $secondPlaying -State $secondState -Label "Second /battle?new=1"

  Write-Host "Second battle: id=$($secondSession.battleId) timer=$($secondPlaying.timer) localElapsedMs=$($secondSession.elapsedMs) backendElapsedMs=$($secondState.elapsedMs)"

  Write-Host "Waiting for old tab to attempt periodic/page-lifecycle active-session persistence..."
  Start-Sleep -Milliseconds 6500
  $afterOldTabCanWrite = Get-PageBattleContext -Client $secondClient
  $latestAfterOldTabCanWrite = Get-LatestActiveSession $afterOldTabCanWrite
  Assert-Condition ($null -ne $latestAfterOldTabCanWrite) "No active session after old-tab persistence window."
  Assert-Condition (
    $latestAfterOldTabCanWrite.battleId -eq $secondSession.battleId
  ) "Old tab overwrote active session after fresh second tab: expected=$($secondSession.battleId), actual=$($latestAfterOldTabCanWrite.battleId), first=$($firstSession.battleId), timer=$($afterOldTabCanWrite.timer), localElapsedMs=$($latestAfterOldTabCanWrite.elapsedMs)."

  Write-Host "Navigating second tab to lobby, then ordinary /battle..."
  Invoke-CdpCommand -Client $secondClient -Method "Page.navigate" -Params @{ url = (Join-TestUrl -Base $frontendBase -Path "/") } | Out-Null
  Start-Sleep -Milliseconds 800
  Invoke-CdpCommand -Client $secondClient -Method "Page.navigate" -Params @{ url = (Join-TestUrl -Base $frontendBase -Path "/battle") } | Out-Null
  $plainPlaying = Wait-PagePlaying -Client $secondClient -Label "tabB ordinary /battle" -TimeoutSeconds $PlayingTimeoutSeconds
  $plainSession = Get-LatestActiveSession $plainPlaying
  Assert-Condition ($null -ne $plainSession) "Ordinary /battle did not expose an active session."
  $plainState = Get-BattleState -BackendBase $backendBase -BattleId $plainSession.battleId
  Assert-Condition ($plainSession.battleId -ne $secondSession.battleId) "Ordinary /battle restored prior active battleId=$($secondSession.battleId) without resume=1."
  Assert-Condition ([int64]$plainState.elapsedMs -le $FreshElapsedMaxMs) "Ordinary /battle backend elapsed is not fresh: battleId=$($plainSession.battleId) elapsedMs=$($plainState.elapsedMs)."
  Assert-TimerFreshness -Context $plainPlaying -State $plainState -Label "Ordinary /battle"

  Write-Host "[PASS] BP-40C browser session freshness"
  Write-Host "firstBattleId=$($firstSession.battleId)"
  Write-Host "secondBattleId=$($secondSession.battleId)"
  Write-Host "plainBattleId=$($plainSession.battleId)"
  Write-Host "plainTimer=$($plainPlaying.timer)"
  Write-Host "plainBackendElapsedMs=$($plainState.elapsedMs)"
} finally {
  Close-Cdp -Client $secondClient
  Close-Cdp -Client $client
  Stop-SmokeProcess -Process $process
}
