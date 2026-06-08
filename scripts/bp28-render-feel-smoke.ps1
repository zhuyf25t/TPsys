[CmdletBinding()]
param(
  [string]$FrontendUrl = "http://127.0.0.1:5173",
  [string]$BackendUrl = "http://127.0.0.1:8080",
  [string]$Password = "bp28-pass",
  [string]$BrowserPath,
  [int]$PlayingTimeoutSeconds = 45,
  [int]$FrameSampleSeconds = 4,
  [ValidateSet("StraightFire", "StraightLeft", "MixedMovement", "DualClientPressure", "SkillPressure", "TargetedSkillPressure", "TargetedSkillNoopPressure", "WeaponSwitchPressure")]
  [string]$Scenario = "StraightFire",
  [int]$InputDurationMs = 1800,
  [int]$WindowWidth = 1280,
  [int]$WindowHeight = 720,
  [int]$ClientAWindowX = 40,
  [int]$ClientAWindowY = 40,
  [int]$ClientBWindowX = 1040,
  [int]$ClientBWindowY = 40,
  [ValidateSet("winter", "default", "autumn", "normal")]
  [string]$ModeId = "winter",
  [int]$PreInputSettleMs = 1700,
  [string]$SummaryPath,
  [switch]$Headful,
  [switch]$DisableGpu,
  [switch]$KeepProfiles,
  [switch]$KeepBrowsersOpen
)

$ErrorActionPreference = "Stop"

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Older Windows PowerShell hosts can still run the smoke without changing output encoding.
}

function Normalize-BaseUrl {
  param([string]$Value)

  $trimmed = ""
  if ($null -ne $Value) {
    $trimmed = $Value.Trim()
  }

  if ([string]::IsNullOrWhiteSpace($trimmed)) {
    throw "Base URL is empty."
  }

  return $trimmed.TrimEnd("/")
}

function Join-TestUrl {
  param(
    [Parameter(Mandatory = $true)][string]$Base,
    [Parameter(Mandatory = $true)][string]$Path
  )

  $normalizedPath = $Path
  if (-not $normalizedPath.StartsWith("/")) {
    $normalizedPath = "/$normalizedPath"
  }

  return "$Base$normalizedPath"
}

function Resolve-Bp28EffectiveInputDurationMs {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("StraightFire", "StraightLeft", "MixedMovement", "DualClientPressure", "SkillPressure", "TargetedSkillPressure", "TargetedSkillNoopPressure", "WeaponSwitchPressure")][string]$Scenario,
    [Parameter(Mandatory = $true)][int]$RequestedInputDurationMs
  )

  if ($Scenario -eq "TargetedSkillPressure" -or $Scenario -eq "TargetedSkillNoopPressure") {
    return [Math]::Max($RequestedInputDurationMs, 4500)
  }

  return $RequestedInputDurationMs
}

function Read-ErrorBody {
  param($Response)

  if ($null -eq $Response) {
    return ""
  }

  try {
    $stream = $Response.GetResponseStream()
    if ($null -eq $stream) {
      return ""
    }

    $reader = [System.IO.StreamReader]::new($stream)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  } catch {
    return ""
  }
}

function Invoke-SmokeJson {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Uri,
    [object]$Body = $null,
    [int]$TimeoutSec = 8
  )

  $parameters = @{
    Method = $Method
    Uri = $Uri
    Headers = @{ "Accept" = "application/json" }
    TimeoutSec = $TimeoutSec
    ErrorAction = "Stop"
  }

  if ($null -ne $Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = ($Body | ConvertTo-Json -Depth 16 -Compress)
  }

  try {
    return Invoke-RestMethod @parameters
  } catch {
    $response = $_.Exception.Response
    $status = ""
    if ($null -ne $response) {
      try {
        $status = " HTTP $([int]$response.StatusCode)"
      } catch {
        $status = ""
      }
    }

    $bodyText = Read-ErrorBody -Response $response
    if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
      $bodyText = " Response: $bodyText"
    }

    throw "$Method $Uri failed.$status$bodyText"
  }
}

function Test-HttpReachable {
  param(
    [Parameter(Mandatory = $true)][string]$Uri,
    [Parameter(Mandatory = $true)][string]$Name
  )

  try {
    Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 8 -ErrorAction Stop | Out-Null
  } catch {
    throw "$Name is not reachable at $Uri. Start the service before running this smoke. $($_.Exception.Message)"
  }
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
    [Parameter(Mandatory = $true)][string]$Skin,
    [Parameter(Mandatory = $true)][ValidateSet("winter", "default", "autumn", "normal")][string]$ModeId
  )

  $encodedHandle = [System.Uri]::EscapeDataString($Handle)
  $encodedPassword = [System.Uri]::EscapeDataString($PasswordValue)
  $encodedSkin = [System.Uri]::EscapeDataString($Skin)
  $encodedTarget = [System.Uri]::EscapeDataString("/battle?new=1&diagnostics=1&mode=$ModeId")
  return "$BaseUrl/bp14-client.html?handle=$encodedHandle&password=$encodedPassword&skin=$encodedSkin&target=$encodedTarget"
}

function Resolve-ExpectedMapIdForMode {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("winter", "default", "autumn", "normal")][string]$ModeId
  )

  switch ($ModeId) {
    "winter" { return "winter-hunt-v1" }
    "default" { return "default-industrial-arena" }
    "autumn" { return "fall-hunt-v1" }
    "normal" { return "normal-hunt-v1" }
  }
}

function Start-CdpBrowser {
  param(
    [Parameter(Mandatory = $true)][string]$BrowserExe,
    [Parameter(Mandatory = $true)][string]$ProfileDir,
    [Parameter(Mandatory = $true)][int]$DebugPort,
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][int]$WindowWidth,
    [Parameter(Mandatory = $true)][int]$WindowHeight,
    [Parameter(Mandatory = $true)][int]$WindowX,
    [Parameter(Mandatory = $true)][int]$WindowY
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
    "--window-size=$WindowWidth,$WindowHeight"
  )

  if (-not $Headful) {
    $arguments += "--headless=new"
    if ($DisableGpu) {
      $arguments += "--disable-gpu"
    }
  } else {
    $arguments += "--new-window"
    $arguments += "--window-position=$WindowX,$WindowY"
  }

  $arguments += "`"$Url`""

  $startParameters = @{
    FilePath = $BrowserExe
    ArgumentList = $arguments
    PassThru = $true
  }
  if (-not $Headful) {
    $startParameters.WindowStyle = "Hidden"
  }

  return Start-Process @startParameters
}

function Wait-CdpTarget {
  param(
    [Parameter(Mandatory = $true)][int]$DebugPort,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$TimeoutSeconds = 15
  )

  $endpoint = "http://127.0.0.1:$DebugPort/json/list"
  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastError = $null

  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    try {
      $targets = Invoke-RestMethod -Uri $endpoint -TimeoutSec 2 -ErrorAction Stop
      foreach ($target in @($targets)) {
        if (
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
  $null = $socket.ConnectAsync(
    [System.Uri]$WebSocketUrl,
    [System.Threading.CancellationToken]::None
  ).GetAwaiter().GetResult()

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
        "bp28 smoke complete",
        [System.Threading.CancellationToken]::None
      ).GetAwaiter().GetResult()
    }
  } catch {
    # Browser cleanup below is authoritative.
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

function Enable-CdpPerformanceMetrics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings
  )

  try {
    Invoke-CdpCommand -Client $Client -Method "Performance.enable" -Params @{} | Out-Null
    return [pscustomobject]@{
      available = $true
    }
  } catch {
    $message = "CDP Performance.enable unavailable for $($Client.Label): $($_.Exception.Message)"
    $Warnings.Add($message) | Out-Null
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      warning = $message
    }
  }
}

function Read-CdpPerformanceMetrics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings
  )

  try {
    $response = Invoke-CdpCommand -Client $Client -Method "Performance.getMetrics" -Params @{} -TimeoutSeconds 8
    $metrics = [ordered]@{}
    foreach ($metric in @($response.result.metrics)) {
      if (
        $null -ne $metric -and
        -not [string]::IsNullOrWhiteSpace($metric.name) -and
        $null -ne $metric.PSObject.Properties["value"]
      ) {
        $metrics[$metric.name] = $metric.value
      }
    }

    return [pscustomobject]@{
      available = $true
      phase = $Phase
      metrics = $metrics
    }
  } catch {
    $message = "CDP Performance.getMetrics unavailable for $($Client.Label) phase=$Phase`: $($_.Exception.Message)"
    $Warnings.Add($message) | Out-Null
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      warning = $message
    }
  }
}

function Get-CdpPerformanceMetricValue {
  param(
    $Snapshot,
    [Parameter(Mandatory = $true)][string]$MetricName
  )

  $metrics = Get-ObjectPropertyValue -InputObject $Snapshot -Name "metrics"
  if ($null -eq $metrics) {
    return $null
  }

  if ($metrics -is [System.Collections.IDictionary]) {
    if ($metrics.Contains($MetricName)) {
      return $metrics[$MetricName]
    }
    return $null
  }

  $property = $metrics.PSObject.Properties[$MetricName]
  if ($null -eq $property) {
    return $null
  }

  return $property.Value
}

function New-CdpPerformanceDelta {
  param(
    $BeforeInput,
    $AfterInput
  )

  $metricNames = @(
    "Resources",
    "Nodes",
    "JSHeapUsedSize",
    "LayoutCount",
    "RecalcStyleCount",
    "ScriptDuration",
    "TaskDuration"
  )
  $metrics = [ordered]@{}
  foreach ($metricName in $metricNames) {
    $beforeValue = Get-CdpPerformanceMetricValue -Snapshot $BeforeInput -MetricName $metricName
    $afterValue = Get-CdpPerformanceMetricValue -Snapshot $AfterInput -MetricName $metricName
    $delta = $null
    if ($null -ne $beforeValue -and $null -ne $afterValue) {
      $delta = [double]$afterValue - [double]$beforeValue
    }

    $metrics[$metricName] = [ordered]@{
      beforeInput = $beforeValue
      afterInput = $afterValue
      delta = $delta
    }
  }

  return [ordered]@{
    available = ((Get-ObjectPropertyValue -InputObject $BeforeInput -Name "available") -eq $true -and (Get-ObjectPropertyValue -InputObject $AfterInput -Name "available") -eq $true)
    from = "beforeInput"
    to = "afterInput"
    metrics = $metrics
  }
}

function Get-PageStatus {
  param($Client)

  $expression = @'
(() => {
  const shell = document.querySelector(".arena-shell");
  const matching = document.querySelector(".arena-shell__overlay--matching");
  const settled = document.querySelector(".arena-shell__overlay--settled");
  const runtimeRoot = document.querySelector("[aria-label='battle runtime']") || document.querySelector(".arena-shell__runtime");
  const runtimeCanvas = runtimeRoot ? runtimeRoot.querySelector("canvas") : document.querySelector("canvas");
  const hudRoot = document.querySelector("#hud-root");
  const hudText = hudRoot ? hudRoot.textContent.trim() : "";
  const legacyPlaying = Boolean(shell && shell.classList.contains("arena-shell--playing"));
  const modernPlaying =
    Boolean(runtimeCanvas && hudRoot) &&
    !matching &&
    !settled &&
    (
      /生命值|武器栏|小地图|战斗日志|权威同步/.test(hudText) ||
      runtimeCanvas.getBoundingClientRect().width > 0
    );
  return {
    href: window.location.href,
    readyState: document.readyState,
    playing: legacyPlaying || modernPlaying,
    shellClass: shell ? shell.className : null,
    runtimeCanvas: Boolean(runtimeCanvas),
    hudRoot: Boolean(hudRoot),
    hudText: hudText.slice(0, 200),
    matchingText: matching ? matching.textContent.trim().slice(0, 200) : null,
    settledText: settled ? settled.textContent.trim().slice(0, 200) : null,
    bodyText: document.body ? document.body.textContent.trim().slice(0, 300) : ""
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression
}

function Wait-PagePlaying {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$TimeoutSeconds = 45
  )

  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastStatus = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $lastStatus = Get-PageStatus -Client $Client
    if ($lastStatus.playing -eq $true) {
      return $lastStatus
    }

    Start-Sleep -Milliseconds 300
  }

  $statusJson = $lastStatus | ConvertTo-Json -Depth 8 -Compress
  throw "$Label did not enter playing state within ${TimeoutSeconds}s. Last page status: $statusJson"
}

function Get-PageBattleContext {
  param($Client)

  $expression = @'
(() => {
  function safeParseJson(raw) {
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function readWeapon(hero) {
    if (!hero || !Array.isArray(hero.weapons)) {
      return null;
    }
    const index = Number.isFinite(hero.currentWeaponIndex) ? Math.max(0, Math.trunc(hero.currentWeaponIndex)) : 0;
    return hero.weapons[index] || hero.weapons[0] || null;
  }

  function compactHero(hero) {
    const weapon = readWeapon(hero);
    return {
      heroId: typeof hero?.heroId === "string" ? hero.heroId : "",
      displayName: typeof hero?.displayName === "string" ? hero.displayName : "",
      position: Number.isFinite(hero?.position?.x) && Number.isFinite(hero?.position?.y)
        ? { x: hero.position.x, y: hero.position.y }
        : null,
      ammoInMagazine: Number.isFinite(weapon?.ammoInMagazine) ? weapon.ammoInMagazine : null,
      reserveAmmo: Number.isFinite(weapon?.reserveAmmo) ? weapon.reserveAmmo : null
    };
  }

  function compactSession(key, parsed) {
    const snapshot = parsed && parsed.snapshot ? parsed.snapshot : null;
    const heroes = Array.isArray(snapshot?.heroes) ? snapshot.heroes.map(compactHero) : [];
    const projectiles = Array.isArray(snapshot?.projectiles)
      ? snapshot.projectiles.map((projectile) => ({
          projectileId: typeof projectile?.projectileId === "string" ? projectile.projectileId : "",
          ownerHeroId: typeof projectile?.ownerHeroId === "string" ? projectile.ownerHeroId : ""
        }))
      : [];
    return {
      key,
      battleId: typeof parsed?.battleId === "string" && parsed.battleId.trim() ? parsed.battleId.trim() : null,
      sharedAuthoritativeRuntime: parsed?.sharedAuthoritativeRuntime === true,
      localAuthoritativePlayerId:
        typeof parsed?.localAuthoritativePlayerId === "string" && parsed.localAuthoritativePlayerId.trim()
          ? parsed.localAuthoritativePlayerId.trim()
          : null,
      localAuthoritativeTicketId:
        typeof parsed?.localAuthoritativeTicketId === "string" && parsed.localAuthoritativeTicketId.trim()
          ? parsed.localAuthoritativeTicketId.trim()
          : null,
      owner: {
        handle: typeof parsed?.owner?.handle === "string" ? parsed.owner.handle : null,
        sessionToken: typeof parsed?.owner?.sessionToken === "string" ? parsed.owner.sessionToken : null
      },
      snapshot: {
        playerHeroId: typeof snapshot?.playerHeroId === "string" ? snapshot.playerHeroId : null,
        elapsedMs: Number.isFinite(snapshot?.elapsedMs) ? snapshot.elapsedMs : null,
        heroes,
        projectiles,
        projectileCount: projectiles.length
      }
    };
  }

  function collectBattleIds(value, output, depth) {
    if (!value || depth > 5) {
      return;
    }
    if (Array.isArray(value)) {
      for (const entry of value.slice(0, 40)) {
        collectBattleIds(entry, output, depth + 1);
      }
      return;
    }
    if (typeof value !== "object") {
      return;
    }
    for (const [key, entry] of Object.entries(value)) {
      if (key === "battleId" && typeof entry === "string" && entry.trim()) {
        output.add(entry.trim());
      } else {
        collectBattleIds(entry, output, depth + 1);
      }
    }
  }

  const sessionPrefixes = [
    "slay-demo.active-battle-session.v2.",
    "slay-demo.completed-battle-session.v2.",
    "slay-demo.active-battle-session.v1",
    "slay-demo.completed-battle-session.v1"
  ];
  const storageSessions = [];
  const discoveredBattleIds = new Set();
  const storageKeys = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (key) {
      storageKeys.push(key);
    }
  }

  for (const key of storageKeys) {
    const raw = window.localStorage.getItem(key);
    const parsed = safeParseJson(raw);
    if (parsed) {
      collectBattleIds(parsed, discoveredBattleIds, 0);
    }
    if (sessionPrefixes.some((prefix) => key === prefix || key.startsWith(prefix)) && parsed) {
      storageSessions.push(compactSession(key, parsed));
    }
  }

  const primarySession = storageSessions.find((session) => session.battleId) || null;
  const shell = document.querySelector(".arena-shell");
  const matching = document.querySelector(".arena-shell__overlay--matching");
  const settled = document.querySelector(".arena-shell__overlay--settled");
  const runtimeRoot = document.querySelector("[aria-label='battle runtime']") || document.querySelector(".arena-shell__runtime");
  const runtimeCanvas = runtimeRoot ? runtimeRoot.querySelector("canvas") : document.querySelector("canvas");
  const hudRoot = document.querySelector("#hud-root");
  const hudText = hudRoot ? hudRoot.textContent.trim() : "";
  const legacyPlaying = Boolean(shell && shell.classList.contains("arena-shell--playing"));
  const modernPlaying =
    Boolean(runtimeCanvas && hudRoot) &&
    !matching &&
    !settled &&
    (
      /生命值|武器栏|小地图|战斗日志|权威同步/.test(hudText) ||
      runtimeCanvas.getBoundingClientRect().width > 0
    );
  return {
    href: window.location.href,
    readyState: document.readyState,
    playing: legacyPlaying || modernPlaying,
    shellClass: shell ? shell.className : null,
    runtimeCanvas: Boolean(runtimeCanvas),
    hudRoot: Boolean(hudRoot),
    authHandle: window.localStorage.getItem("slay-demo.auth.session.v1"),
    sessionToken: window.localStorage.getItem("slay-demo.auth.session-token.v1"),
    battleId: primarySession?.battleId || Array.from(discoveredBattleIds)[0] || null,
    localAuthoritativePlayerId: primarySession?.localAuthoritativePlayerId || null,
    localAuthoritativeTicketId: primarySession?.localAuthoritativeTicketId || null,
    primarySession,
    storageSessions,
    discoveredBattleIds: Array.from(discoveredBattleIds)
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression
}

function Wait-PageBattleContext {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$TimeoutMs = 4000,
    [int]$PollIntervalMs = 150
  )

  $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($TimeoutMs)
  $lastContext = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $lastContext = Get-PageBattleContext -Client $Client
    if (
      $null -ne $lastContext -and
      -not [string]::IsNullOrWhiteSpace($lastContext.battleId) -and
      -not [string]::IsNullOrWhiteSpace($lastContext.localAuthoritativePlayerId)
    ) {
      return $lastContext
    }

    Start-Sleep -Milliseconds $PollIntervalMs
  }

  return $lastContext
}

function Start-RafSample {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][int]$DurationMs
  )

  $expression = @"
(() => {
  const durationMs = Math.max(1000, $DurationMs);
  const sample = {
    startedAt: performance.now(),
    lastFrameAt: null,
    frames: [],
    intervals: [],
    phases: ["idle", "input", "postInput"],
    phaseTransitions: [],
    inputWindow: null,
    longTasks: {
      available: false,
      status: "unavailable",
      reason: null,
      entries: []
    },
    done: false
  };
  window.__bp28RenderFeelSmokePhase = "idle";
  window.__bp28RenderFeelSmokeRaf = sample;
  sample.phaseTransitions.push({ phase: "idle", at: sample.startedAt });
  try {
    const PerformanceObserverCtor = typeof PerformanceObserver === "function" ? PerformanceObserver : null;
    const supportedTypes = Array.isArray(PerformanceObserverCtor?.supportedEntryTypes)
      ? PerformanceObserver.supportedEntryTypes
      : [];
    if (!PerformanceObserverCtor || !supportedTypes.includes("longtask")) {
      sample.longTasks.reason = "PerformanceObserver longtask is not supported";
    } else {
      const observer = new PerformanceObserverCtor((list) => {
        for (const entry of list.getEntries()) {
          sample.longTasks.entries.push({
            name: typeof entry.name === "string" ? entry.name : "",
            startTime: Number.isFinite(entry.startTime) ? entry.startTime : null,
            duration: Number.isFinite(entry.duration) ? entry.duration : null,
            attributionCount: Array.isArray(entry.attribution) ? entry.attribution.length : null
          });
        }
        if (sample.longTasks.entries.length > 200) {
          sample.longTasks.entries.splice(0, sample.longTasks.entries.length - 200);
        }
      });
      observer.observe({ type: "longtask", buffered: true });
      sample.longTasks.available = true;
      sample.longTasks.status = "available";
      sample.longTasks.reason = null;
      sample.longTaskObserver = observer;
    }
  } catch (error) {
    sample.longTasks.available = false;
    sample.longTasks.status = "unavailable";
    sample.longTasks.reason = error instanceof Error ? error.message : String(error);
  }
  function tick(now) {
    if (sample.lastFrameAt !== null) {
      const intervalMs = now - sample.lastFrameAt;
      const phase = typeof window.__bp28RenderFeelSmokePhase === "string"
        ? window.__bp28RenderFeelSmokePhase
        : "idle";
      sample.intervals.push(intervalMs);
      sample.frames.push({ intervalMs, phase });
    }
    sample.lastFrameAt = now;
    if (now - sample.startedAt < durationMs) {
      window.requestAnimationFrame(tick);
      return;
    }
    sample.done = true;
    sample.finishedAt = now;
  }
  window.requestAnimationFrame(tick);
  return true;
})()
"@

  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
}

function Set-RafPhase {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][ValidateSet("idle", "input", "postInput")][string]$Phase
  )

  $expression = @"
(() => {
  window.__bp28RenderFeelSmokePhase = "$Phase";
  const sample = window.__bp28RenderFeelSmokeRaf;
  if (sample && Array.isArray(sample.phaseTransitions)) {
    const now = typeof performance !== "undefined" && typeof performance.now === "function"
      ? performance.now()
      : null;
    sample.phaseTransitions.push({ phase: "$Phase", at: Number.isFinite(now) ? now : null });
  }
  return window.__bp28RenderFeelSmokePhase;
})()
"@

  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
}

function Set-RafInputWindow {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [AllowNull()][Nullable[double]]$InputStartPageMs,
    [AllowNull()][Nullable[double]]$InputEndPageMs,
    [string]$Source = "inputEventWindow"
  )

  $startJson = if ($null -ne $InputStartPageMs) { [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:R}", [double]$InputStartPageMs) } else { "null" }
  $endJson = if ($null -ne $InputEndPageMs) { [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:R}", [double]$InputEndPageMs) } else { "null" }
  $sourceJson = $Source | ConvertTo-Json -Compress

  $expression = @"
(() => {
  const sample = window.__bp28RenderFeelSmokeRaf;
  if (!sample) {
    return false;
  }
  sample.inputWindow = {
    inputStartPageMs: $startJson,
    inputEndPageMs: $endJson,
    source: $sourceJson
  };
  return true;
})()
"@

  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
}

function Read-RafSample {
  param($Client)

  $expression = @'
(() => {
  const sample = window.__bp28RenderFeelSmokeRaf;
  const rawFrames = Array.isArray(sample?.frames)
    ? sample.frames
    : Array.isArray(sample?.intervals)
      ? sample.intervals.map((intervalMs) => ({ intervalMs, phase: "idle" }))
      : [];
  const frames = rawFrames
    .map((frame) => ({
      intervalMs: Number.isFinite(frame?.intervalMs) ? frame.intervalMs : null,
      phase: typeof frame?.phase === "string" && frame.phase ? frame.phase : "idle"
    }))
    .filter((frame) => Number.isFinite(frame.intervalMs));

  function percentile(sorted, percentileValue) {
    if (sorted.length === 0) {
      return null;
    }
    const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * percentileValue) - 1));
    return sorted[index];
  }

  function summarize(inputFrames) {
    const intervals = inputFrames.map((frame) => frame.intervalMs);
    const sorted = [...intervals].sort((left, right) => left - right);
    const sum = intervals.reduce((total, value) => total + value, 0);
    const median = sorted.length === 0
      ? null
      : sorted.length % 2 === 1
        ? sorted[(sorted.length - 1) / 2]
        : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
    return {
      frameCount: intervals.length,
      avgMs: intervals.length === 0 ? null : sum / intervals.length,
      medianMs: median,
      p95Ms: percentile(sorted, 0.95),
      p99Ms: percentile(sorted, 0.99),
      maxMs: intervals.length === 0 ? null : Math.max(...intervals),
      over25ms: intervals.filter((value) => value > 25).length,
      over40ms: intervals.filter((value) => value > 40).length
    };
  }

  function readLongTasks() {
    const source = sample?.longTasks;
    if (!source || source.available !== true) {
      return {
        available: false,
        status: "unavailable",
        reason: source?.reason || "longtask observer was not initialized"
      };
    }
    const entries = Array.isArray(source.entries)
      ? source.entries.filter((entry) => Number.isFinite(entry?.duration))
      : [];
    const inputWindow = sample?.inputWindow || null;
    const transitions = Array.isArray(sample?.phaseTransitions)
      ? sample.phaseTransitions
          .filter((transition) => typeof transition?.phase === "string" && Number.isFinite(transition?.at))
          .sort((left, right) => left.at - right.at)
      : [];

    function diagnosticPhaseName(phase) {
      if (phase === "input") {
        return "input";
      }
      if (phase === "postInput" || phase === "post-input") {
        return "post-input";
      }
      return "startup/pre-input";
    }

    function phaseForEntry(entry) {
      const startTime = entry.startTime;
      if (
        Number.isFinite(inputWindow?.inputStartPageMs) &&
        Number.isFinite(inputWindow?.inputEndPageMs)
      ) {
        if (startTime < inputWindow.inputStartPageMs) {
          return "startup/pre-input";
        }
        if (startTime <= inputWindow.inputEndPageMs) {
          return "input";
        }
        return "post-input";
      }

      let currentPhase = "idle";
      for (const transition of transitions) {
        if (transition.at > startTime) {
          break;
        }
        currentPhase = transition.phase;
      }
      return diagnosticPhaseName(currentPhase);
    }

    const classifiedEntries = entries.map((entry) => ({
      name: typeof entry.name === "string" ? entry.name : "",
      startTime: Number.isFinite(entry.startTime) ? entry.startTime : null,
      duration: entry.duration,
      attributionCount: Number.isFinite(entry.attributionCount) ? entry.attributionCount : null,
      phase: phaseForEntry(entry)
    }));

    function summarizeLongTaskEntries(inputEntries) {
      const durations = inputEntries.map((entry) => entry.duration);
      const sorted = [...durations].sort((left, right) => left - right);
      return {
        taskCount: inputEntries.length,
        totalDurationMs: durations.reduce((total, value) => total + value, 0),
        maxDurationMs: durations.length === 0 ? null : Math.max(...durations),
        p95DurationMs: percentile(sorted, 0.95)
      };
    }

    const byPhase = {};
    for (const phase of ["startup/pre-input", "input", "post-input"]) {
      byPhase[phase] = summarizeLongTaskEntries(classifiedEntries.filter((entry) => entry.phase === phase));
    }
    const overallLongTasks = summarizeLongTaskEntries(classifiedEntries);
    return {
      available: true,
      status: "available",
      taskCount: overallLongTasks.taskCount,
      totalDurationMs: overallLongTasks.totalDurationMs,
      maxDurationMs: overallLongTasks.maxDurationMs,
      p95DurationMs: overallLongTasks.p95DurationMs,
      byPhase,
      phaseBasis: Number.isFinite(inputWindow?.inputStartPageMs) && Number.isFinite(inputWindow?.inputEndPageMs)
        ? inputWindow
        : { source: "rafPhaseTransitions", transitions },
      entries: classifiedEntries.slice(-20)
    };
  }

  function readHeap() {
    const memory = performance?.memory;
    if (
      !memory ||
      !Number.isFinite(memory.usedJSHeapSize) ||
      !Number.isFinite(memory.totalJSHeapSize) ||
      !Number.isFinite(memory.jsHeapSizeLimit)
    ) {
      return {
        available: false,
        status: "unavailable",
        reason: "performance.memory is not exposed"
      };
    }
    return {
      available: true,
      status: "available",
      usedJSHeapSize: memory.usedJSHeapSize,
      totalJSHeapSize: memory.totalJSHeapSize,
      jsHeapSizeLimit: memory.jsHeapSizeLimit
    };
  }

  const overall = summarize(frames);
  const phaseNames = new Set(["idle", "input", "postInput"]);
  for (const frame of frames) {
    phaseNames.add(frame.phase);
  }
  const byPhase = {};
  for (const phase of phaseNames) {
    byPhase[phase] = summarize(frames.filter((frame) => frame.phase === phase));
  }
  const byDiagnosticPhase = {
    "startup/pre-input": summarize(frames.filter((frame) => frame.phase !== "input" && frame.phase !== "postInput")),
    input: summarize(frames.filter((frame) => frame.phase === "input")),
    "post-input": summarize(frames.filter((frame) => frame.phase === "postInput"))
  };

  return {
    done: sample?.done === true,
    startedAt: Number.isFinite(sample?.startedAt) ? sample.startedAt : null,
    finishedAt: Number.isFinite(sample?.finishedAt) ? sample.finishedAt : null,
    currentPhase: typeof window.__bp28RenderFeelSmokePhase === "string" ? window.__bp28RenderFeelSmokePhase : null,
    frameCount: overall.frameCount,
    avgMs: overall.avgMs,
    medianMs: overall.medianMs,
    p95Ms: overall.p95Ms,
    p99Ms: overall.p99Ms,
    maxMs: overall.maxMs,
    over25ms: overall.over25ms,
    over40ms: overall.over40ms,
    overall,
    byPhase,
    byDiagnosticPhase,
    longTasks: readLongTasks(),
    jsHeap: readHeap()
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression
}

function Get-InputPoint {
  param($Client)

  $expression = @'
(() => {
  const root = document.querySelector("[aria-label='battle runtime']") || document.querySelector(".arena-shell__runtime") || document.querySelector(".arena-shell__viewport") || document.body;
  const rect = root.getBoundingClientRect();
  const width = Math.max(1, rect.width || window.innerWidth || 1280);
  const height = Math.max(1, rect.height || window.innerHeight || 720);
  return {
    x: Math.round(rect.left + width * 0.68),
    y: Math.round(rect.top + height * 0.48)
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression
}

function Get-PageFocusStatus {
  param($Client)

  $expression = @'
(() => {
  const active = document.activeElement;
  return {
    hasFocus: typeof document.hasFocus === "function" ? document.hasFocus() : null,
    visibilityState: typeof document.visibilityState === "string" ? document.visibilityState : null,
    activeElementTag: active && typeof active.tagName === "string" ? active.tagName.toLowerCase() : null,
    activeElementClass: active && typeof active.className === "string" ? active.className : null,
    windowFocusedAtMs: typeof performance !== "undefined" && typeof performance.now === "function"
      ? performance.now()
      : null
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression
}

function Send-CdpKeyEvent {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][ValidateSet("keyDown", "keyUp")][string]$Type,
    [Parameter(Mandatory = $true)][ValidateSet("w", "a", "s", "d", "q", "e", "r")][string]$Key
  )

  $upperKey = $Key.ToUpperInvariant()
  $virtualKeyCode = [int][char]$upperKey
  Invoke-CdpCommand -Client $Client -Method "Input.dispatchKeyEvent" -Params @{
    type = $Type
    key = $Key
    code = "Key$upperKey"
    windowsVirtualKeyCode = $virtualKeyCode
    nativeVirtualKeyCode = $virtualKeyCode
  } | Out-Null

  $domType = $(if ($Type -eq "keyDown") { "keydown" } else { "keyup" })
  $expression = @"
(() => {
  const event = new KeyboardEvent("$domType", {
    bubbles: true,
    cancelable: true,
    view: window,
    key: "$Key",
    code: "Key$upperKey",
    keyCode: $virtualKeyCode,
    which: $virtualKeyCode
  });
  window.dispatchEvent(event);
  document.dispatchEvent(event);
  return true;
})()
"@
  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
}

function Set-CdpMovementKeys {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][AllowNull()][AllowEmptyCollection()][string[]]$DesiredKeys,
    [Parameter(Mandatory = $true)][hashtable]$PressedKeys
  )

  $desired = @{}
  foreach ($key in @($DesiredKeys)) {
    if (-not [string]::IsNullOrWhiteSpace($key)) {
      $desired[$key.ToLowerInvariant()] = $true
    }
  }

  foreach ($key in @($PressedKeys.Keys)) {
    if (-not $desired.ContainsKey($key)) {
      Send-CdpKeyEvent -Client $Client -Type "keyUp" -Key $key
      $PressedKeys.Remove($key)
    }
  }

  foreach ($key in @($desired.Keys)) {
    if (-not $PressedKeys.ContainsKey($key)) {
      Send-CdpKeyEvent -Client $Client -Type "keyDown" -Key $key
      $PressedKeys[$key] = $true
    }
  }
}

function Send-CdpKeyTap {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][ValidateSet("q", "e", "r")][string]$Key
  )

  Send-CdpKeyEvent -Client $Client -Type "keyDown" -Key $Key
  Send-CdpKeyEvent -Client $Client -Type "keyUp" -Key $Key
}

function Send-DomMouseEvent {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][ValidateSet("mousemove", "mousedown", "mouseup")][string]$Type,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [Parameter(Mandatory = $true)][int]$Buttons
  )

  $button = $(if ($Type -eq "mousemove") { -1 } else { 0 })
  $expression = @"
(() => {
  const target =
    document.querySelector("[aria-label='battle runtime'] canvas") ||
    document.querySelector("[aria-label='battle runtime']") ||
    document.querySelector(".arena-shell__runtime canvas") ||
    document.querySelector(".arena-shell__runtime") ||
    document.querySelector(".arena-shell__viewport") ||
    document.body;
  target.dispatchEvent(new MouseEvent("$Type", {
    bubbles: true,
    cancelable: true,
    view: window,
    clientX: $X,
    clientY: $Y,
    screenX: $X,
    screenY: $Y,
    button: $button,
    buttons: $Buttons
  }));
  return true;
})()
"@

  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
}

function Send-BattleMouseEvent {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][ValidateSet("mouseMoved", "mousePressed", "mouseReleased")][string]$Type,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [Parameter(Mandatory = $true)][int]$Buttons,
    [string]$Button = "none",
    [int]$ClickCount = 0,
    [switch]$SkipDomFallback
  )

  $params = @{
    type = $Type
    x = $X
    y = $Y
    button = $Button
    buttons = $Buttons
  }
  if ($ClickCount -gt 0) {
    $params.clickCount = $ClickCount
  }

  Invoke-CdpCommand -Client $Client -Method "Input.dispatchMouseEvent" -Params $params | Out-Null

  $domType = switch ($Type) {
    "mousePressed" { "mousedown" }
    "mouseReleased" { "mouseup" }
    default { "mousemove" }
  }
  if (-not $SkipDomFallback) {
    Send-DomMouseEvent -Client $Client -Type $domType -X $X -Y $Y -Buttons $Buttons
  }
}

function Send-DomWheelEvent {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [Parameter(Mandatory = $true)][int]$DeltaY
  )

  $expression = @"
(() => {
  const target =
    document.querySelector("[aria-label='battle runtime'] canvas") ||
    document.querySelector("[aria-label='battle runtime']") ||
    document.querySelector(".arena-shell__runtime canvas") ||
    document.querySelector(".arena-shell__runtime") ||
    document.querySelector(".arena-shell__viewport") ||
    document.body;
  target.dispatchEvent(new WheelEvent("wheel", {
    bubbles: true,
    cancelable: true,
    view: window,
    clientX: $X,
    clientY: $Y,
    screenX: $X,
    screenY: $Y,
    deltaX: 0,
    deltaY: $DeltaY
  }));
  return true;
})()
"@

  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
}

function Send-BattleWheelEvent {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [Parameter(Mandatory = $true)][int]$DeltaY,
    [switch]$SkipDomFallback
  )

  Invoke-CdpCommand -Client $Client -Method "Input.dispatchMouseEvent" -Params @{
    type = "mouseWheel"
    x = $X
    y = $Y
    deltaX = 0
    deltaY = $DeltaY
  } | Out-Null

  if (-not $SkipDomFallback) {
    Send-DomWheelEvent -Client $Client -X $X -Y $Y -DeltaY $DeltaY
  }
}

function Get-MixedMovementKeys {
  param(
    [Parameter(Mandatory = $true)][int]$ElapsedMs,
    [Parameter(Mandatory = $true)][int]$DurationMs
  )

  $duration = [Math]::Max(1, $DurationMs)
  $ratio = [double]$ElapsedMs / [double]$duration

  if ($ratio -lt 0.22) {
    return @("d")
  }
  if ($ratio -lt 0.34) {
    return @()
  }
  if ($ratio -lt 0.52) {
    return @("s", "d")
  }
  if ($ratio -lt 0.66) {
    return @("a")
  }
  if ($ratio -lt 0.76) {
    return @()
  }
  if ($ratio -lt 0.9) {
    return @("w", "a")
  }

  return @("d")
}

function Get-DualPressureMovementKeys {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("clientA", "clientB")][string]$Label,
    [Parameter(Mandatory = $true)][int]$ElapsedMs
  )

  $patternsA = @(
    @("d"),
    @("s", "d"),
    @("s"),
    @("a", "s"),
    @("a"),
    @("w", "a"),
    @("w"),
    @("d", "w")
  )
  $patternsB = @(
    @("a"),
    @("w", "a"),
    @("w"),
    @("d", "w"),
    @("d"),
    @("s", "d"),
    @("s"),
    @("a", "s")
  )

  $index = [int]([Math]::Floor([Math]::Max(0, $ElapsedMs) / 450) % $patternsA.Count)
  if ($Label -eq "clientB") {
    return $patternsB[$index]
  }

  return $patternsA[$index]
}

function Get-DualPressureFireHeld {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("clientA", "clientB")][string]$Label,
    [Parameter(Mandatory = $true)][int]$ElapsedMs
  )

  $elapsed = [Math]::Max(0, $ElapsedMs)
  if ($Label -eq "clientA") {
    return (($elapsed % 1300) -lt 1080)
  }

  $shifted = ($elapsed + 260) % 900
  return ($shifted -lt 430)
}

function Get-DualPressureAimPoint {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("clientA", "clientB")][string]$Label,
    [Parameter(Mandatory = $true)][int]$BaseX,
    [Parameter(Mandatory = $true)][int]$BaseY,
    [Parameter(Mandatory = $true)][int]$ElapsedMs,
    [Parameter(Mandatory = $true)][int]$WindowWidth,
    [Parameter(Mandatory = $true)][int]$WindowHeight
  )

  $direction = 1.0
  if ($Label -eq "clientB") {
    $direction = -1.0
  }

  $phase = ([double][Math]::Max(0, $ElapsedMs)) / 1000.0
  $x = [int][Math]::Round($BaseX + ($direction * [Math]::Sin($phase * 5.1) * 190.0))
  $y = [int][Math]::Round($BaseY + ([Math]::Cos($phase * 4.3) * 115.0))
  $minX = 8
  $minY = 8
  $maxX = [Math]::Max($minX, $WindowWidth - 8)
  $maxY = [Math]::Max($minY, $WindowHeight - 8)

  return [pscustomobject]@{
    x = [Math]::Min($maxX, [Math]::Max($minX, $x))
    y = [Math]::Min($maxY, [Math]::Max($minY, $y))
  }
}

function Get-SkillPressureAimPoint {
  param(
    [Parameter(Mandatory = $true)][int]$BaseX,
    [Parameter(Mandatory = $true)][int]$BaseY,
    [Parameter(Mandatory = $true)][int]$ElapsedMs,
    [Parameter(Mandatory = $true)][int]$WindowWidth,
    [Parameter(Mandatory = $true)][int]$WindowHeight
  )

  $phase = ([double][Math]::Max(0, $ElapsedMs)) / 1000.0
  $x = [int][Math]::Round($BaseX + ([Math]::Sin($phase * 4.7) * 170.0))
  $y = [int][Math]::Round($BaseY + ([Math]::Cos($phase * 3.9) * 105.0))
  $minX = 8
  $minY = 8
  $maxX = [Math]::Max($minX, $WindowWidth - 8)
  $maxY = [Math]::Max($minY, $WindowHeight - 8)

  return [pscustomobject]@{
    x = [Math]::Min($maxX, [Math]::Max($minX, $x))
    y = [Math]::Min($maxY, [Math]::Max($minY, $y))
  }
}

function Get-TargetedSkillNoopAimPoint {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("Blink", "Freeze")][string]$Skill,
    [Parameter(Mandatory = $true)][int]$WindowWidth,
    [Parameter(Mandatory = $true)][int]$WindowHeight
  )

  $minX = 8
  $minY = 8
  $maxX = [Math]::Max($minX, $WindowWidth - 8)
  $maxY = [Math]::Max($minY, $WindowHeight - 8)

  return [pscustomobject]@{
    x = $WindowWidth + 2048
    y = $WindowHeight + 2048
  }
}

function Set-CdpMousePressed {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][bool]$ShouldPress,
    [Parameter(Mandatory = $true)][ref]$MousePressed,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y
  )

  if ($ShouldPress -and -not $MousePressed.Value) {
    Send-BattleMouseEvent -Client $Client -Type "mousePressed" -X $X -Y $Y -Button "left" -Buttons 1 -ClickCount 1
    $MousePressed.Value = $true
  } elseif (-not $ShouldPress -and $MousePressed.Value) {
    Send-BattleMouseEvent -Client $Client -Type "mouseReleased" -X $X -Y $Y -Button "left" -Buttons 0 -ClickCount 1
    $MousePressed.Value = $false
  }
}

function New-DualPressureClientInputSummary {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("clientA", "clientB")][string]$Label,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [Parameter(Mandatory = $true)][int]$DurationMs,
    $InputStartPageMarker,
    $InputDispatchStartPageMarker,
    $InputEndPageMarker,
    $InputEventProbe,
    $CommandFetchProbe,
    $CommandFetchProbeInstall,
    $FocusBefore,
    $FocusAfterBringToFront,
    [Parameter(Mandatory = $true)][int]$AimSampleCount,
    [Parameter(Mandatory = $true)][int]$FirePressCount,
    [Parameter(Mandatory = $true)][int]$FireReleaseCount
  )

  $inputStartPageMs = Get-ObjectPropertyValue -InputObject $InputStartPageMarker -Name "pageNowMs"
  $inputDispatchStartPageMs = Get-ObjectPropertyValue -InputObject $InputDispatchStartPageMarker -Name "pageNowMs"
  if ($null -eq $inputDispatchStartPageMs) {
    $inputDispatchStartPageMs = $inputStartPageMs
  }
  $inputStartPageWallMs = Get-ObjectPropertyValue -InputObject $InputStartPageMarker -Name "wallMs"
  $inputDispatchStartWallMs = Get-ObjectPropertyValue -InputObject $InputDispatchStartPageMarker -Name "wallMs"
  if ($null -eq $inputDispatchStartWallMs) {
    $inputDispatchStartWallMs = $inputStartPageWallMs
  }
  $firstInputEventPageMs = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstInputEventPageMs"
  $firstInputEventWallMs = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstInputEventWallMs"
  $firstMovementInputEventPageMs = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstMovementInputEventPageMs"
  $firstMovementInputEventWallMs = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstMovementInputEventWallMs"
  $firstFireInputEventPageMs = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstFireInputEventPageMs"
  $firstFireInputEventWallMs = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstFireInputEventWallMs"
  $preDispatchOverheadMs = $null
  if ($null -ne $inputStartPageWallMs -and $null -ne $inputDispatchStartWallMs) {
    $preDispatchOverheadMs = [long]([double]$inputDispatchStartWallMs - [double]$inputStartPageWallMs)
  } elseif ($null -ne $inputStartPageMs -and $null -ne $inputDispatchStartPageMs) {
    $preDispatchOverheadMs = [Math]::Round(([double]$inputDispatchStartPageMs - [double]$inputStartPageMs), 3)
  }
  $dispatchToEventOverheadMs = $null
  if ($null -ne $inputDispatchStartPageMs -and $null -ne $firstInputEventPageMs) {
    $dispatchToEventOverheadMs = [Math]::Round(([double]$firstInputEventPageMs - [double]$inputDispatchStartPageMs), 3)
  }
  $motionLatencyBasis = "inputDispatchStartPageMs"
  $motionInputStartPageMs = $inputDispatchStartPageMs
  $motionInputStartWallMs = $inputDispatchStartWallMs
  if ($null -ne $firstMovementInputEventPageMs) {
    $motionLatencyBasis = "firstMovementInputEventPageMs"
    $motionInputStartPageMs = $firstMovementInputEventPageMs
    $motionInputStartWallMs = $firstMovementInputEventWallMs
  }
  $muzzleLatencyBasis = "inputDispatchStartPageMs"
  $muzzleInputStartPageMs = $inputDispatchStartPageMs
  $muzzleInputStartWallMs = $inputDispatchStartWallMs
  if ($null -ne $firstFireInputEventPageMs) {
    $muzzleLatencyBasis = "firstFireInputEventPageMs"
    $muzzleInputStartPageMs = $firstFireInputEventPageMs
    $muzzleInputStartWallMs = $firstFireInputEventWallMs
  }

  return [pscustomobject]@{
    label = $Label
    scenario = "DualClientPressure"
    x = $X
    y = $Y
    durationMs = $DurationMs
    fireStartOffsetMs = 0
    fireEndOffsetMs = $DurationMs
    firePressCount = $FirePressCount
    fireReleaseCount = $FireReleaseCount
    aimSampleCount = $AimSampleCount
    inputStartPageMs = $inputStartPageMs
    inputDispatchStartPageMs = $inputDispatchStartPageMs
    firstInputEventPageMs = $firstInputEventPageMs
    firstInputEventType = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstInputEventType"
    firstInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstInputEventKeyOrButton"
    firstMovementInputEventPageMs = $firstMovementInputEventPageMs
    firstMovementInputEventType = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstMovementInputEventType"
    firstMovementInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstMovementInputEventKeyOrButton"
    firstFireInputEventPageMs = $firstFireInputEventPageMs
    firstFireInputEventType = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstFireInputEventType"
    firstFireInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $InputEventProbe -Name "firstFireInputEventKeyOrButton"
    inputEndPageMs = Get-ObjectPropertyValue -InputObject $InputEndPageMarker -Name "pageNowMs"
    inputStartPageWallMs = $inputStartPageWallMs
    inputDispatchStartWallMs = $inputDispatchStartWallMs
    firstInputEventWallMs = $firstInputEventWallMs
    firstMovementInputEventWallMs = $firstMovementInputEventWallMs
    firstFireInputEventWallMs = $firstFireInputEventWallMs
    inputEndPageWallMs = Get-ObjectPropertyValue -InputObject $InputEndPageMarker -Name "wallMs"
    preDispatchOverheadMs = $preDispatchOverheadMs
    dispatchToEventOverheadMs = $dispatchToEventOverheadMs
    latencyBasis = $(if ($null -ne $firstInputEventPageMs) { "firstInputEventPageMs" } else { "inputDispatchStartPageMs" })
    motionLatencyBasis = $motionLatencyBasis
    muzzleLatencyBasis = $muzzleLatencyBasis
    motionInputStartPageMs = $motionInputStartPageMs
    muzzleInputStartPageMs = $muzzleInputStartPageMs
    motionInputStartWallMs = $motionInputStartWallMs
    muzzleInputStartWallMs = $muzzleInputStartWallMs
    inputEventProbe = $InputEventProbe
    commandFetchProbe = $CommandFetchProbe
    commandFetchProbeInstall = $CommandFetchProbeInstall
    focus = [ordered]@{
      before = $FocusBefore
      afterBringToFront = $FocusAfterBringToFront
    }
    inputProbe = [ordered]@{
      probeWindow = "dualClientPressure"
      commandFetchProbe = $CommandFetchProbe
    }
  }
}

function Invoke-DualClientPressureInputBurst {
  param(
    [Parameter(Mandatory = $true)]$ClientA,
    [Parameter(Mandatory = $true)]$ClientB,
    [Parameter(Mandatory = $true)][int]$DurationMs,
    [Parameter(Mandatory = $true)][int]$WindowWidth,
    [Parameter(Mandatory = $true)][int]$WindowHeight,
    [int]$ProbePollIntervalMs = 80
  )

  $pointA = Get-InputPoint -Client $ClientA
  $pointB = Get-InputPoint -Client $ClientB
  $baseXA = [int]$pointA.x
  $baseYA = [int]$pointA.y
  $baseXB = [int]$pointB.x
  $baseYB = [int]$pointB.y
  $focusBeforeA = Get-PageFocusStatus -Client $ClientA
  $focusBeforeB = Get-PageFocusStatus -Client $ClientB
  Invoke-CdpCommand -Client $ClientA -Method "Page.bringToFront" -Params @{} | Out-Null
  Invoke-CdpEvaluate -Client $ClientA -Expression "(() => { window.focus(); document.body && document.body.focus && document.body.focus(); return true; })()" | Out-Null
  Invoke-CdpCommand -Client $ClientB -Method "Page.bringToFront" -Params @{} | Out-Null
  Invoke-CdpEvaluate -Client $ClientB -Expression "(() => { window.focus(); document.body && document.body.focus && document.body.focus(); return true; })()" | Out-Null
  $focusAfterBringToFrontA = Get-PageFocusStatus -Client $ClientA
  $focusAfterBringToFrontB = Get-PageFocusStatus -Client $ClientB
  $inputEventProbeInstallA = Install-InputEventProbe -Client $ClientA
  $inputEventProbeInstallB = Install-InputEventProbe -Client $ClientB
  $commandFetchProbeInstallA = Install-CommandFetchProbe -Client $ClientA
  $commandFetchProbeInstallB = Install-CommandFetchProbe -Client $ClientB
  $inputStartPageMarkerA = Read-PageTimingMarker -Client $ClientA
  $inputStartPageMarkerB = Read-PageTimingMarker -Client $ClientB
  $pressedMovementKeysA = @{}
  $pressedMovementKeysB = @{}
  $mousePressedA = $false
  $mousePressedB = $false
  $firePressCountA = 0
  $firePressCountB = 0
  $fireReleaseCountA = 0
  $fireReleaseCountB = 0
  $aimSampleCountA = 0
  $aimSampleCountB = 0

  $inputDispatchStartPageMarkerA = Read-PageTimingMarker -Client $ClientA
  $inputDispatchStartPageMarkerB = Read-PageTimingMarker -Client $ClientB
  $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($DurationMs)
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $elapsedMs = [int]([Math]::Max(0, $DurationMs - ($deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds))
    $aimA = Get-DualPressureAimPoint -Label "clientA" -BaseX $baseXA -BaseY $baseYA -ElapsedMs $elapsedMs -WindowWidth $WindowWidth -WindowHeight $WindowHeight
    $aimB = Get-DualPressureAimPoint -Label "clientB" -BaseX $baseXB -BaseY $baseYB -ElapsedMs $elapsedMs -WindowWidth $WindowWidth -WindowHeight $WindowHeight
    Set-CdpMovementKeys -Client $ClientA -DesiredKeys (Get-DualPressureMovementKeys -Label "clientA" -ElapsedMs $elapsedMs) -PressedKeys $pressedMovementKeysA
    Set-CdpMovementKeys -Client $ClientB -DesiredKeys (Get-DualPressureMovementKeys -Label "clientB" -ElapsedMs $elapsedMs) -PressedKeys $pressedMovementKeysB

    $wasPressedA = $mousePressedA
    $wasPressedB = $mousePressedB
    Set-CdpMousePressed -Client $ClientA -ShouldPress (Get-DualPressureFireHeld -Label "clientA" -ElapsedMs $elapsedMs) -MousePressed ([ref]$mousePressedA) -X ([int]$aimA.x) -Y ([int]$aimA.y)
    Set-CdpMousePressed -Client $ClientB -ShouldPress (Get-DualPressureFireHeld -Label "clientB" -ElapsedMs $elapsedMs) -MousePressed ([ref]$mousePressedB) -X ([int]$aimB.x) -Y ([int]$aimB.y)
    if (-not $wasPressedA -and $mousePressedA) { $firePressCountA += 1 }
    if ($wasPressedA -and -not $mousePressedA) { $fireReleaseCountA += 1 }
    if (-not $wasPressedB -and $mousePressedB) { $firePressCountB += 1 }
    if ($wasPressedB -and -not $mousePressedB) { $fireReleaseCountB += 1 }

    Send-BattleMouseEvent -Client $ClientA -Type "mouseMoved" -X ([int]$aimA.x) -Y ([int]$aimA.y) -Buttons $(if ($mousePressedA) { 1 } else { 0 })
    Send-BattleMouseEvent -Client $ClientB -Type "mouseMoved" -X ([int]$aimB.x) -Y ([int]$aimB.y) -Buttons $(if ($mousePressedB) { 1 } else { 0 })
    $aimSampleCountA += 1
    $aimSampleCountB += 1

    Start-Sleep -Milliseconds $ProbePollIntervalMs
  }

  if ($mousePressedA) {
    Set-CdpMousePressed -Client $ClientA -ShouldPress $false -MousePressed ([ref]$mousePressedA) -X $baseXA -Y $baseYA
    $fireReleaseCountA += 1
  }
  if ($mousePressedB) {
    Set-CdpMousePressed -Client $ClientB -ShouldPress $false -MousePressed ([ref]$mousePressedB) -X $baseXB -Y $baseYB
    $fireReleaseCountB += 1
  }
  Set-CdpMovementKeys -Client $ClientA -DesiredKeys @() -PressedKeys $pressedMovementKeysA
  Set-CdpMovementKeys -Client $ClientB -DesiredKeys @() -PressedKeys $pressedMovementKeysB
  $inputEndPageMarkerA = Read-PageTimingMarker -Client $ClientA
  $inputEndPageMarkerB = Read-PageTimingMarker -Client $ClientB
  $inputEventProbeA = Read-InputEventProbe -Client $ClientA
  $inputEventProbeB = Read-InputEventProbe -Client $ClientB
  $commandFetchProbeA = Read-CommandFetchProbe -Client $ClientA
  $commandFetchProbeB = Read-CommandFetchProbe -Client $ClientB

  $clientAResult = New-DualPressureClientInputSummary `
    -Label "clientA" `
    -X $baseXA `
    -Y $baseYA `
    -DurationMs $DurationMs `
    -InputStartPageMarker $inputStartPageMarkerA `
    -InputDispatchStartPageMarker $inputDispatchStartPageMarkerA `
    -InputEndPageMarker $inputEndPageMarkerA `
    -InputEventProbe $inputEventProbeA `
    -CommandFetchProbe $commandFetchProbeA `
    -CommandFetchProbeInstall $commandFetchProbeInstallA `
    -FocusBefore $focusBeforeA `
    -FocusAfterBringToFront $focusAfterBringToFrontA `
    -AimSampleCount $aimSampleCountA `
    -FirePressCount $firePressCountA `
    -FireReleaseCount $fireReleaseCountA
  $clientBResult = New-DualPressureClientInputSummary `
    -Label "clientB" `
    -X $baseXB `
    -Y $baseYB `
    -DurationMs $DurationMs `
    -InputStartPageMarker $inputStartPageMarkerB `
    -InputDispatchStartPageMarker $inputDispatchStartPageMarkerB `
    -InputEndPageMarker $inputEndPageMarkerB `
    -InputEventProbe $inputEventProbeB `
    -CommandFetchProbe $commandFetchProbeB `
    -CommandFetchProbeInstall $commandFetchProbeInstallB `
    -FocusBefore $focusBeforeB `
    -FocusAfterBringToFront $focusAfterBringToFrontB `
    -AimSampleCount $aimSampleCountB `
    -FirePressCount $firePressCountB `
    -FireReleaseCount $fireReleaseCountB

  return [pscustomobject]@{
    scenario = "DualClientPressure"
    mode = "dualClient"
    x = $clientAResult.x
    y = $clientAResult.y
    durationMs = $clientAResult.durationMs
    fireStartOffsetMs = $clientAResult.fireStartOffsetMs
    fireEndOffsetMs = $clientAResult.fireEndOffsetMs
    inputStartPageMs = $clientAResult.inputStartPageMs
    inputDispatchStartPageMs = $clientAResult.inputDispatchStartPageMs
    firstInputEventPageMs = $clientAResult.firstInputEventPageMs
    firstInputEventType = $clientAResult.firstInputEventType
    firstInputEventKeyOrButton = $clientAResult.firstInputEventKeyOrButton
    firstMovementInputEventPageMs = $clientAResult.firstMovementInputEventPageMs
    firstMovementInputEventType = $clientAResult.firstMovementInputEventType
    firstMovementInputEventKeyOrButton = $clientAResult.firstMovementInputEventKeyOrButton
    firstFireInputEventPageMs = $clientAResult.firstFireInputEventPageMs
    firstFireInputEventType = $clientAResult.firstFireInputEventType
    firstFireInputEventKeyOrButton = $clientAResult.firstFireInputEventKeyOrButton
    inputEndPageMs = $clientAResult.inputEndPageMs
    inputStartPageWallMs = $clientAResult.inputStartPageWallMs
    inputDispatchStartWallMs = $clientAResult.inputDispatchStartWallMs
    firstInputEventWallMs = $clientAResult.firstInputEventWallMs
    firstMovementInputEventWallMs = $clientAResult.firstMovementInputEventWallMs
    firstFireInputEventWallMs = $clientAResult.firstFireInputEventWallMs
    inputEndPageWallMs = $clientAResult.inputEndPageWallMs
    preDispatchOverheadMs = $clientAResult.preDispatchOverheadMs
    dispatchToEventOverheadMs = $clientAResult.dispatchToEventOverheadMs
    latencyBasis = $clientAResult.latencyBasis
    motionLatencyBasis = $clientAResult.motionLatencyBasis
    muzzleLatencyBasis = $clientAResult.muzzleLatencyBasis
    motionInputStartPageMs = $clientAResult.motionInputStartPageMs
    muzzleInputStartPageMs = $clientAResult.muzzleInputStartPageMs
    motionInputStartWallMs = $clientAResult.motionInputStartWallMs
    muzzleInputStartWallMs = $clientAResult.muzzleInputStartWallMs
    inputEventProbe = $clientAResult.inputEventProbe
    inputEventProbeInstall = $inputEventProbeInstallA
    commandFetchProbe = $clientAResult.commandFetchProbe
    commandFetchProbeInstall = $clientAResult.commandFetchProbeInstall
    focus = $clientAResult.focus
    inputProbe = $clientAResult.inputProbe
    clientA = $clientAResult
    clientB = $clientBResult
    clients = [ordered]@{
      clientA = $clientAResult
      clientB = $clientBResult
    }
  }
}

function Invoke-InputBurst {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][int]$DurationMs,
    [ValidateSet("StraightFire", "StraightLeft", "MixedMovement", "DualClientPressure", "SkillPressure", "TargetedSkillPressure", "TargetedSkillNoopPressure", "WeaponSwitchPressure")]
    [string]$Scenario = "StraightFire",
    [int]$WindowWidth = 1280,
    [int]$WindowHeight = 720,
    $BeforeState = $null,
    [string]$BackendBase = "",
    [string]$BattleId = "",
    [string]$Handle = "",
    [string]$PlayerId = "",
    $BeforeContext = $null,
    $LocalFeedbackBefore = $null,
    $RemoteViewClient = $null,
    $RemoteViewBefore = $null,
    [string]$RemoteHeroId = "",
    [string]$RemoteHeroDisplayName = "",
    [Nullable[long]]$InputStartWallMs = $null,
    [int]$ProbePollIntervalMs = 100
  )

  $point = Get-InputPoint -Client $Client
  $x = [int]$point.x
  $y = [int]$point.y

  $focusBefore = Get-PageFocusStatus -Client $Client
  Invoke-CdpCommand -Client $Client -Method "Page.bringToFront" -Params @{} | Out-Null
  Invoke-CdpEvaluate -Client $Client -Expression "(() => { window.focus(); document.body && document.body.focus && document.body.focus(); return true; })()" | Out-Null
  $focusAfterBringToFront = Get-PageFocusStatus -Client $Client
  Send-BattleMouseEvent -Client $Client -Type "mouseMoved" -X $x -Y $y -Buttons 0
  $inputStartPageMarker = Read-PageTimingMarker -Client $Client
  $inputDispatchStartPageMarker = $null
  $inputEventProbeInstall = Install-InputEventProbe -Client $Client
  $inputEventProbe = $null
  $commandFetchProbeInstall = Install-CommandFetchProbe -Client $Client
  $commandFetchProbe = $null
  $transientNoticeProbeInstall = Install-TransientNoticeProbe -Client $Client
  $transientNoticeProbe = $null
  $pressedMovementKeys = @{}
  $mousePressed = $false
  $mixedFireDispatched = $false
  $firePressCount = 0
  $fireReleaseCount = 0
  $aimSampleCount = 0
  $skillTapCount = 0
  $skillKeys = @()
  $skillTapSchedule = @(
    [pscustomobject]@{ OffsetMs = 350; Key = "q" },
    [pscustomobject]@{ OffsetMs = 850; Key = "e" },
    [pscustomobject]@{ OffsetMs = 1350; Key = "r" },
    [pscustomobject]@{ OffsetMs = 1850; Key = "q" },
    [pscustomobject]@{ OffsetMs = 2350; Key = "e" },
    [pscustomobject]@{ OffsetMs = 2850; Key = "r" }
  )
  $dispatchedSkillTapIndexes = @{}
  $targetedSkillTapCount = 0
  $targetedSkillKeys = @()
  $targetedConfirmCount = 0
  $targetedSkillTapSchedule = @(
    [pscustomobject]@{ OffsetMs = 250; Key = "q"; Skill = "Blink" },
    [pscustomobject]@{ OffsetMs = 1500; Key = "r"; Skill = "Freeze" }
  )
  $targetedConfirmSchedule = @(
    [pscustomobject]@{ PressOffsetMs = 850; ReleaseOffsetMs = 980; Skill = "Blink" },
    [pscustomobject]@{ PressOffsetMs = 2100; ReleaseOffsetMs = 2230; Skill = "Freeze" }
  )
  if ($Scenario -eq "TargetedSkillNoopPressure") {
    $targetedSkillTapSchedule = @(
      [pscustomobject]@{ OffsetMs = 250; Key = "q"; Skill = "Blink" },
      [pscustomobject]@{ OffsetMs = 1250; Key = "q"; Skill = "Blink" },
      [pscustomobject]@{ OffsetMs = 2200; Key = "r"; Skill = "Freeze" },
      [pscustomobject]@{ OffsetMs = 3200; Key = "r"; Skill = "Freeze" }
    )
    $targetedConfirmSchedule = @(
      [pscustomobject]@{ PressOffsetMs = 850; ReleaseOffsetMs = 980; Skill = "Blink" },
      [pscustomobject]@{ PressOffsetMs = 1650; ReleaseOffsetMs = 1780; Skill = "Blink" },
      [pscustomobject]@{ PressOffsetMs = 2700; ReleaseOffsetMs = 2830; Skill = "Freeze" },
      [pscustomobject]@{ PressOffsetMs = 3700; ReleaseOffsetMs = 3830; Skill = "Freeze" }
    )
  }
  $dispatchedTargetedSkillTapIndexes = @{}
  $pressedTargetedConfirmIndexes = @{}
  $releasedTargetedConfirmIndexes = @{}
  $weaponSwitchWheelCount = 0
  $weaponSwitchWheelSchedule = @(
    [pscustomobject]@{ OffsetMs = 350; DeltaY = 120 },
    [pscustomobject]@{ OffsetMs = 1150; DeltaY = 120 }
  )
  $dispatchedWeaponSwitchWheelIndexes = @{}
  $mixedFireStartMs = 0
  $mixedFireEndMs = [Math]::Min(
    [Math]::Max(160, [Math]::Floor($DurationMs * 0.08)),
    [Math]::Max(160, $DurationMs)
  )

  if ($Scenario -eq "StraightFire") {
    $inputDispatchStartPageMarker = Read-PageTimingMarker -Client $Client
    Set-CdpMovementKeys -Client $Client -DesiredKeys @("d") -PressedKeys $pressedMovementKeys
    Send-BattleMouseEvent -Client $Client -Type "mousePressed" -X $x -Y $y -Button "left" -Buttons 1 -ClickCount 1
    $mousePressed = $true
    $firePressCount += 1
  } elseif ($Scenario -eq "StraightLeft") {
    $inputDispatchStartPageMarker = Read-PageTimingMarker -Client $Client
    Set-CdpMovementKeys -Client $Client -DesiredKeys @("a") -PressedKeys $pressedMovementKeys
  } elseif ($Scenario -eq "MixedMovement") {
    $inputDispatchStartPageMarker = Read-PageTimingMarker -Client $Client
    Set-CdpMovementKeys -Client $Client -DesiredKeys (Get-MixedMovementKeys -ElapsedMs 0 -DurationMs $DurationMs) -PressedKeys $pressedMovementKeys
    Send-BattleMouseEvent -Client $Client -Type "mousePressed" -X $x -Y $y -Button "left" -Buttons 1 -ClickCount 1
    $mousePressed = $true
    $firePressCount += 1
    $mixedFireDispatched = $true
  } elseif ($Scenario -eq "WeaponSwitchPressure") {
    $inputDispatchStartPageMarker = Read-PageTimingMarker -Client $Client
    Set-CdpMovementKeys -Client $Client -DesiredKeys @() -PressedKeys $pressedMovementKeys
  } else {
    $inputDispatchStartPageMarker = Read-PageTimingMarker -Client $Client
    Set-CdpMovementKeys -Client $Client -DesiredKeys (Get-MixedMovementKeys -ElapsedMs 0 -DurationMs $DurationMs) -PressedKeys $pressedMovementKeys
  }

  $inputEventProbe = Read-InputEventProbe -Client $Client -KeepInstalled

  $probeStartWallMs = $InputStartWallMs
  if ($null -eq $probeStartWallMs) {
    $probeStartWallMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  }
  $authoritativeProbeMetric = $null
  $pageSnapshotProbeMetric = $null
  $localFeedbackProbeMetric = $null
  $remoteViewProbeMetric = $null
  $lastAuthoritativeProbeMetric = $null
  $lastPageSnapshotProbeMetric = $null
  $lastLocalFeedbackProbeMetric = $null
  $lastRemoteViewProbeMetric = $null
  $probeAttempt = 0
  $inputStartPageMs = Get-ObjectPropertyValue -InputObject $inputStartPageMarker -Name "pageNowMs"
  $inputDispatchStartPageMs = Get-ObjectPropertyValue -InputObject $inputDispatchStartPageMarker -Name "pageNowMs"
  if ($null -eq $inputDispatchStartPageMs) {
    $inputDispatchStartPageMs = $inputStartPageMs
  }
  $inputStartPageWallMs = Get-ObjectPropertyValue -InputObject $inputStartPageMarker -Name "wallMs"
  $inputDispatchStartWallMs = Get-ObjectPropertyValue -InputObject $inputDispatchStartPageMarker -Name "wallMs"
  if ($null -eq $inputDispatchStartWallMs) {
    $inputDispatchStartWallMs = $inputStartPageWallMs
  }
  $preDispatchOverheadMs = $null
  if ($null -ne $inputStartPageWallMs -and $null -ne $inputDispatchStartWallMs) {
    $preDispatchOverheadMs = [long]([double]$inputDispatchStartWallMs - [double]$inputStartPageWallMs)
  } elseif ($null -ne $inputStartPageMs -and $null -ne $inputDispatchStartPageMs) {
    $preDispatchOverheadMs = [Math]::Round(([double]$inputDispatchStartPageMs - [double]$inputStartPageMs), 3)
  }
  $firstInputEventPageMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventPageMs"
  $firstInputEventType = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventType"
  $firstInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventKeyOrButton"
  $firstInputEventWallMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventWallMs"
  $firstMovementInputEventPageMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventPageMs"
  $firstMovementInputEventType = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventType"
  $firstMovementInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventKeyOrButton"
  $firstMovementInputEventWallMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventWallMs"
  $firstFireInputEventPageMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventPageMs"
  $firstFireInputEventType = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventType"
  $firstFireInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventKeyOrButton"
  $firstFireInputEventWallMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventWallMs"
  $dispatchToEventOverheadMs = $null
  if ($null -ne $inputDispatchStartPageMs -and $null -ne $firstInputEventPageMs) {
    $dispatchToEventOverheadMs = [Math]::Round(([double]$firstInputEventPageMs - [double]$inputDispatchStartPageMs), 3)
  }
  $localFeedbackInputStartPageMs = $inputDispatchStartPageMs
  $localFeedbackProbeStartWallMs = $inputDispatchStartWallMs
  $localFeedbackLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstInputEventPageMs) {
    $localFeedbackInputStartPageMs = $firstInputEventPageMs
    $localFeedbackLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $localFeedbackProbeStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $motionInputStartPageMs = $inputDispatchStartPageMs
  $motionInputStartWallMs = $inputDispatchStartWallMs
  $motionLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstMovementInputEventPageMs) {
    $motionInputStartPageMs = $firstMovementInputEventPageMs
    $motionLatencyBasis = "firstMovementInputEventPageMs"
    if ($null -ne $firstMovementInputEventWallMs) {
      $motionInputStartWallMs = [long]([double]$firstMovementInputEventWallMs)
    }
  } elseif ($null -ne $firstInputEventPageMs) {
    $motionInputStartPageMs = $firstInputEventPageMs
    $motionLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $motionInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $muzzleInputStartPageMs = $inputDispatchStartPageMs
  $muzzleInputStartWallMs = $inputDispatchStartWallMs
  $muzzleLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstFireInputEventPageMs) {
    $muzzleInputStartPageMs = $firstFireInputEventPageMs
    $muzzleLatencyBasis = "firstFireInputEventPageMs"
    if ($null -ne $firstFireInputEventWallMs) {
      $muzzleInputStartWallMs = [long]([double]$firstFireInputEventWallMs)
    }
  } elseif ($null -ne $firstInputEventPageMs) {
    $muzzleInputStartPageMs = $firstInputEventPageMs
    $muzzleLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $muzzleInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  if ($null -eq $localFeedbackProbeStartWallMs) {
    $localFeedbackProbeStartWallMs = $probeStartWallMs
  }
  if ($null -eq $motionInputStartWallMs) {
    $motionInputStartWallMs = $probeStartWallMs
  }
  if ($null -eq $muzzleInputStartWallMs) {
    $muzzleInputStartWallMs = $probeStartWallMs
  }
  $canProbeAuthoritative = (
    $null -ne $BeforeState -and
    -not [string]::IsNullOrWhiteSpace($BackendBase) -and
    -not [string]::IsNullOrWhiteSpace($BattleId) -and
    -not [string]::IsNullOrWhiteSpace($Handle)
  )
  $canProbePageSnapshot = (
    $null -ne $BeforeContext -and
    -not [string]::IsNullOrWhiteSpace($Handle)
  )
  $canProbeLocalFeedback = (
    $null -ne $LocalFeedbackBefore -and
    $LocalFeedbackBefore.available -eq $true -and
    $null -ne $localFeedbackInputStartPageMs
  )
  $canProbeRemoteView = (
    $null -ne $RemoteViewClient -and
    $null -ne $RemoteViewBefore -and
    $RemoteViewBefore.available -eq $true
  )

  $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($DurationMs)
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $elapsedMs = [int]([Math]::Max(0, $DurationMs - ($deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds))
    if ($Scenario -eq "MixedMovement" -or $Scenario -eq "SkillPressure" -or $Scenario -eq "TargetedSkillPressure" -or $Scenario -eq "TargetedSkillNoopPressure") {
      Set-CdpMovementKeys -Client $Client -DesiredKeys (Get-MixedMovementKeys -ElapsedMs $elapsedMs -DurationMs $DurationMs) -PressedKeys $pressedMovementKeys

      if ($Scenario -eq "SkillPressure") {
        for ($tapIndex = 0; $tapIndex -lt $skillTapSchedule.Count; $tapIndex++) {
          $tap = $skillTapSchedule[$tapIndex]
          if ($elapsedMs -ge [int]$tap.OffsetMs -and $DurationMs -ge [int]$tap.OffsetMs -and -not $dispatchedSkillTapIndexes.ContainsKey($tapIndex)) {
            Send-CdpKeyTap -Client $Client -Key $tap.Key
            $dispatchedSkillTapIndexes[$tapIndex] = $true
            $skillTapCount += 1
            $skillKeys += $tap.Key
          }
        }
      }

      if ($Scenario -eq "TargetedSkillPressure" -or $Scenario -eq "TargetedSkillNoopPressure") {
        for ($tapIndex = 0; $tapIndex -lt $targetedSkillTapSchedule.Count; $tapIndex++) {
          $tap = $targetedSkillTapSchedule[$tapIndex]
          if ($elapsedMs -ge [int]$tap.OffsetMs -and $DurationMs -ge [int]$tap.OffsetMs -and -not $dispatchedTargetedSkillTapIndexes.ContainsKey($tapIndex)) {
            Send-CdpKeyTap -Client $Client -Key $tap.Key
            $dispatchedTargetedSkillTapIndexes[$tapIndex] = $true
            $targetedSkillTapCount += 1
            $targetedSkillKeys += $tap.Key
          }
        }

        $targetedConfirmPressedThisLoop = @{}
        for ($confirmIndex = 0; $confirmIndex -lt $targetedConfirmSchedule.Count; $confirmIndex++) {
          $confirm = $targetedConfirmSchedule[$confirmIndex]
          if (
            $elapsedMs -ge [int]$confirm.PressOffsetMs -and
            $DurationMs -ge [int]$confirm.PressOffsetMs -and
            -not $pressedTargetedConfirmIndexes.ContainsKey($confirmIndex) -and
            -not $mousePressed
          ) {
            if ($Scenario -eq "TargetedSkillNoopPressure") {
              $confirmAim = Get-TargetedSkillNoopAimPoint -Skill $confirm.Skill -WindowWidth $WindowWidth -WindowHeight $WindowHeight
            } else {
              $confirmAim = Get-SkillPressureAimPoint -BaseX $x -BaseY $y -ElapsedMs $elapsedMs -WindowWidth $WindowWidth -WindowHeight $WindowHeight
            }
            $confirmX = [int]$confirmAim.x
            $confirmY = [int]$confirmAim.y
            Send-BattleMouseEvent -Client $Client -Type "mouseMoved" -X $confirmX -Y $confirmY -Buttons 0 -SkipDomFallback:($Scenario -eq "TargetedSkillNoopPressure")
            Send-BattleMouseEvent -Client $Client -Type "mousePressed" -X $confirmX -Y $confirmY -Button "left" -Buttons 1 -ClickCount 1 -SkipDomFallback:($Scenario -eq "TargetedSkillNoopPressure")
            $pressedTargetedConfirmIndexes[$confirmIndex] = $true
            $targetedConfirmPressedThisLoop[$confirmIndex] = $true
            $mousePressed = $true
            $firePressCount += 1
            $targetedConfirmCount += 1
          }
        }

        for ($confirmIndex = 0; $confirmIndex -lt $targetedConfirmSchedule.Count; $confirmIndex++) {
          $confirm = $targetedConfirmSchedule[$confirmIndex]
          if (
            $elapsedMs -ge [int]$confirm.ReleaseOffsetMs -and
            $pressedTargetedConfirmIndexes.ContainsKey($confirmIndex) -and
            -not $releasedTargetedConfirmIndexes.ContainsKey($confirmIndex) -and
            -not $targetedConfirmPressedThisLoop.ContainsKey($confirmIndex) -and
            $mousePressed
          ) {
            if ($Scenario -eq "TargetedSkillNoopPressure") {
              $confirmAim = Get-TargetedSkillNoopAimPoint -Skill $confirm.Skill -WindowWidth $WindowWidth -WindowHeight $WindowHeight
            } else {
              $confirmAim = Get-SkillPressureAimPoint -BaseX $x -BaseY $y -ElapsedMs $elapsedMs -WindowWidth $WindowWidth -WindowHeight $WindowHeight
            }
            $confirmX = [int]$confirmAim.x
            $confirmY = [int]$confirmAim.y
            Send-BattleMouseEvent -Client $Client -Type "mouseReleased" -X $confirmX -Y $confirmY -Button "left" -Buttons 0 -ClickCount 1 -SkipDomFallback:($Scenario -eq "TargetedSkillNoopPressure")
            $releasedTargetedConfirmIndexes[$confirmIndex] = $true
            $mousePressed = $false
            $fireReleaseCount += 1
          }
        }
      }

      if (
        -not $mousePressed -and
        $elapsedMs -ge $mixedFireStartMs -and
        $elapsedMs -lt $mixedFireEndMs -and
        ($Scenario -eq "MixedMovement" -or $Scenario -eq "SkillPressure")
      ) {
        Send-BattleMouseEvent -Client $Client -Type "mousePressed" -X $x -Y $y -Button "left" -Buttons 1 -ClickCount 1
        $mousePressed = $true
        $firePressCount += 1
        $mixedFireDispatched = $true
      } elseif ($mousePressed -and $elapsedMs -ge $mixedFireEndMs) {
        Send-BattleMouseEvent -Client $Client -Type "mouseReleased" -X $x -Y $y -Button "left" -Buttons 0 -ClickCount 1
        $mousePressed = $false
        $fireReleaseCount += 1
      }
    }

    if ($Scenario -eq "WeaponSwitchPressure") {
      for ($wheelIndex = 0; $wheelIndex -lt $weaponSwitchWheelSchedule.Count; $wheelIndex++) {
        $wheel = $weaponSwitchWheelSchedule[$wheelIndex]
        if (
          $elapsedMs -ge [int]$wheel.OffsetMs -and
          $DurationMs -ge [int]$wheel.OffsetMs -and
          -not $dispatchedWeaponSwitchWheelIndexes.ContainsKey($wheelIndex)
        ) {
          Send-BattleWheelEvent -Client $Client -X $x -Y $y -DeltaY ([int]$wheel.DeltaY)
          $dispatchedWeaponSwitchWheelIndexes[$wheelIndex] = $true
          $weaponSwitchWheelCount += 1
        }
      }
    }

    if ($Scenario -eq "SkillPressure" -or $Scenario -eq "TargetedSkillPressure" -or $Scenario -eq "TargetedSkillNoopPressure") {
      if ($Scenario -eq "TargetedSkillNoopPressure") {
        $noopAimSkill = $(if ($elapsedMs -lt 2200) { "Blink" } else { "Freeze" })
        $aim = Get-TargetedSkillNoopAimPoint -Skill $noopAimSkill -WindowWidth $WindowWidth -WindowHeight $WindowHeight
      } else {
        $aim = Get-SkillPressureAimPoint -BaseX $x -BaseY $y -ElapsedMs $elapsedMs -WindowWidth $WindowWidth -WindowHeight $WindowHeight
      }
      $dispatchX = [int]$aim.x
      $dispatchY = [int]$aim.y
      $aimSampleCount += 1
    } else {
      $dispatchX = $x
      $dispatchY = $y
    }

    Send-BattleMouseEvent -Client $Client -Type "mouseMoved" -X $dispatchX -Y $dispatchY -Buttons $(if ($mousePressed) { 1 } else { 0 }) -SkipDomFallback:($Scenario -eq "TargetedSkillNoopPressure")

    $probeAttempt += 1
    if ($canProbeLocalFeedback) {
      $currentLocalFeedback = Read-LocalFeedbackDiagnostics -Client $Client -Phase "inputBurst"
      $currentPageNowMs = Get-ObjectPropertyValue -InputObject $currentLocalFeedback -Name "pageNowMs"
      $lastLocalFeedbackProbeMetric = New-LocalFeedbackLatencyMetric `
        -Before $LocalFeedbackBefore `
        -After $currentLocalFeedback `
        -InputStartPageMs $localFeedbackInputStartPageMs `
        -InputEndPageMs $currentPageNowMs `
        -InputStartWallMs ([long]$localFeedbackProbeStartWallMs) `
        -InputEndWallMs ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) `
        -LatencyBasis $localFeedbackLatencyBasis `
        -InputDispatchStartPageMs $inputDispatchStartPageMs `
        -InputDispatchStartWallMs $inputDispatchStartWallMs `
        -FirstInputEventPageMs $firstInputEventPageMs `
        -FirstInputEventType $firstInputEventType `
        -FirstInputEventKeyOrButton $firstInputEventKeyOrButton `
        -FirstInputEventWallMs $(if ($null -ne $firstInputEventWallMs) { [long]([double]$firstInputEventWallMs) } else { $null }) `
        -FirstMovementInputEventPageMs $firstMovementInputEventPageMs `
        -FirstMovementInputEventType $firstMovementInputEventType `
        -FirstMovementInputEventKeyOrButton $firstMovementInputEventKeyOrButton `
        -FirstMovementInputEventWallMs $(if ($null -ne $firstMovementInputEventWallMs) { [long]([double]$firstMovementInputEventWallMs) } else { $null }) `
        -FirstFireInputEventPageMs $firstFireInputEventPageMs `
        -FirstFireInputEventType $firstFireInputEventType `
        -FirstFireInputEventKeyOrButton $firstFireInputEventKeyOrButton `
        -FirstFireInputEventWallMs $(if ($null -ne $firstFireInputEventWallMs) { [long]([double]$firstFireInputEventWallMs) } else { $null }) `
        -MotionInputStartPageMs $motionInputStartPageMs `
        -MotionInputStartWallMs ([long]$motionInputStartWallMs) `
        -MotionLatencyBasis $motionLatencyBasis `
        -MuzzleInputStartPageMs $muzzleInputStartPageMs `
        -MuzzleInputStartWallMs ([long]$muzzleInputStartWallMs) `
        -MuzzleLatencyBasis $muzzleLatencyBasis `
        -DispatchToEventOverheadMs $dispatchToEventOverheadMs
      if ($lastLocalFeedbackProbeMetric -is [System.Collections.IDictionary]) {
        $lastLocalFeedbackProbeMetric["legacyInputStartPageMs"] = $inputStartPageMs
        $lastLocalFeedbackProbeMetric["legacyInputStartWallMs"] = $inputStartPageWallMs
        $lastLocalFeedbackProbeMetric["preDispatchOverheadMs"] = $preDispatchOverheadMs
      }
      if (Test-LocalFeedbackMetricHasAnySample -Metric $lastLocalFeedbackProbeMetric) {
        $localFeedbackProbeMetric = $lastLocalFeedbackProbeMetric
      }
    }

    if ($null -eq $remoteViewProbeMetric -and $canProbeRemoteView) {
      $currentRemoteView = Read-RemoteViewDiagnostics -Client $RemoteViewClient -Phase "inputBurst"
      $lastRemoteViewProbeMetric = New-RemoteViewMetric `
        -Before $RemoteViewBefore `
        -After $currentRemoteView `
        -RemoteHeroId $RemoteHeroId `
        -RemoteHeroDisplayName $RemoteHeroDisplayName `
        -InputStartWallMs ([long]$probeStartWallMs) `
        -InputEndWallMs ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
      if (Test-RemoteViewMetricHasAnyInputSample -Metric $lastRemoteViewProbeMetric) {
        $remoteViewProbeMetric = $lastRemoteViewProbeMetric
      }
    }

    if ($null -eq $authoritativeProbeMetric -and $canProbeAuthoritative) {
      $currentState = Get-BattleState -BackendBase $BackendBase -BattleId $BattleId
      $lastAuthoritativeProbeMetric = Measure-StateActionEffect -BeforeState $BeforeState -AfterState $currentState -Handle $Handle -PlayerId $PlayerId
      $authoritativeProbePassed = $lastAuthoritativeProbeMetric.available -eq $true -and $lastAuthoritativeProbeMetric.passed -eq $true
      if ($Scenario -eq "MixedMovement") {
        $authoritativeProbePassed = $authoritativeProbePassed -and $lastAuthoritativeProbeMetric.fired -eq $true
      }
      if ($authoritativeProbePassed) {
        $authoritativeProbeMetric = Add-ActionConfirmationMetadata `
          -Metric $lastAuthoritativeProbeMetric `
          -InputStartWallMs ([long]$probeStartWallMs) `
          -ConfirmWallMs ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) `
          -Attempt $probeAttempt `
          -ProbeWindow "inputBurst" `
          -CapturedDuringInput $true
      }
    }

    if ($null -eq $pageSnapshotProbeMetric -and $canProbePageSnapshot) {
      $currentContext = Get-PageBattleContext -Client $Client
      $lastPageSnapshotProbeMetric = Measure-LocalStorageActionEffect -BeforeContext $BeforeContext -AfterContext $currentContext -Handle $Handle
      $pageSnapshotProbePassed = $lastPageSnapshotProbeMetric.available -eq $true -and $lastPageSnapshotProbeMetric.passed -eq $true
      if ($Scenario -eq "MixedMovement") {
        $pageSnapshotProbePassed = $pageSnapshotProbePassed -and $lastPageSnapshotProbeMetric.fired -eq $true
      }
      if ($pageSnapshotProbePassed) {
        $pageSnapshotProbeMetric = Add-ActionConfirmationMetadata `
          -Metric $lastPageSnapshotProbeMetric `
          -InputStartWallMs ([long]$probeStartWallMs) `
          -ConfirmWallMs ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) `
          -Attempt $probeAttempt `
          -ProbeWindow "inputBurst" `
          -CapturedDuringInput $true
      }
    }

    Start-Sleep -Milliseconds $ProbePollIntervalMs
  }

  if ($Scenario -eq "MixedMovement" -and -not $mixedFireDispatched) {
    Send-BattleMouseEvent -Client $Client -Type "mousePressed" -X $x -Y $y -Button "left" -Buttons 1 -ClickCount 1
    $mousePressed = $true
    $firePressCount += 1
  }

  if ($mousePressed) {
    Send-BattleMouseEvent -Client $Client -Type "mouseReleased" -X $x -Y $y -Button "left" -Buttons 0 -ClickCount 1
    $fireReleaseCount += 1
  }
  Set-CdpMovementKeys -Client $Client -DesiredKeys @() -PressedKeys $pressedMovementKeys
  $inputEndPageMarker = Read-PageTimingMarker -Client $Client
  $inputEventProbe = Read-InputEventProbe -Client $Client
  $inputEndPageMs = Get-ObjectPropertyValue -InputObject $inputEndPageMarker -Name "pageNowMs"
  $inputEndPageWallMs = Get-ObjectPropertyValue -InputObject $inputEndPageMarker -Name "wallMs"
  $firstInputEventPageMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventPageMs"
  $firstInputEventType = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventType"
  $firstInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventKeyOrButton"
  $firstInputEventWallMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstInputEventWallMs"
  $firstMovementInputEventPageMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventPageMs"
  $firstMovementInputEventType = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventType"
  $firstMovementInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventKeyOrButton"
  $firstMovementInputEventWallMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstMovementInputEventWallMs"
  $firstFireInputEventPageMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventPageMs"
  $firstFireInputEventType = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventType"
  $firstFireInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventKeyOrButton"
  $firstFireInputEventWallMs = Get-ObjectPropertyValue -InputObject $inputEventProbe -Name "firstFireInputEventWallMs"
  $dispatchToEventOverheadMs = $null
  if ($null -ne $inputDispatchStartPageMs -and $null -ne $firstInputEventPageMs) {
    $dispatchToEventOverheadMs = [Math]::Round(([double]$firstInputEventPageMs - [double]$inputDispatchStartPageMs), 3)
  }
  $localFeedbackInputStartPageMs = $inputDispatchStartPageMs
  $localFeedbackProbeStartWallMs = $inputDispatchStartWallMs
  $localFeedbackLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstInputEventPageMs) {
    $localFeedbackInputStartPageMs = $firstInputEventPageMs
    $localFeedbackLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $localFeedbackProbeStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $motionInputStartPageMs = $inputDispatchStartPageMs
  $motionInputStartWallMs = $inputDispatchStartWallMs
  $motionLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstMovementInputEventPageMs) {
    $motionInputStartPageMs = $firstMovementInputEventPageMs
    $motionLatencyBasis = "firstMovementInputEventPageMs"
    if ($null -ne $firstMovementInputEventWallMs) {
      $motionInputStartWallMs = [long]([double]$firstMovementInputEventWallMs)
    }
  } elseif ($null -ne $firstInputEventPageMs) {
    $motionInputStartPageMs = $firstInputEventPageMs
    $motionLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $motionInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $muzzleInputStartPageMs = $inputDispatchStartPageMs
  $muzzleInputStartWallMs = $inputDispatchStartWallMs
  $muzzleLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstFireInputEventPageMs) {
    $muzzleInputStartPageMs = $firstFireInputEventPageMs
    $muzzleLatencyBasis = "firstFireInputEventPageMs"
    if ($null -ne $firstFireInputEventWallMs) {
      $muzzleInputStartWallMs = [long]([double]$firstFireInputEventWallMs)
    }
  } elseif ($null -ne $firstInputEventPageMs) {
    $muzzleInputStartPageMs = $firstInputEventPageMs
    $muzzleLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $muzzleInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  if ($null -eq $localFeedbackProbeStartWallMs) {
    $localFeedbackProbeStartWallMs = $probeStartWallMs
  }
  if ($null -eq $motionInputStartWallMs) {
    $motionInputStartWallMs = $probeStartWallMs
  }
  if ($null -eq $muzzleInputStartWallMs) {
    $muzzleInputStartWallMs = $probeStartWallMs
  }
  if ($canProbeLocalFeedback) {
    $finalLocalFeedback = Read-LocalFeedbackDiagnostics -Client $Client -Phase "inputBurstFinal"
    $finalLocalFeedbackProbeMetric = New-LocalFeedbackLatencyMetric `
      -Before $LocalFeedbackBefore `
      -After $finalLocalFeedback `
      -InputStartPageMs $localFeedbackInputStartPageMs `
      -InputEndPageMs $inputEndPageMs `
      -InputStartWallMs ([long]$localFeedbackProbeStartWallMs) `
      -InputEndWallMs ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) `
      -LatencyBasis $localFeedbackLatencyBasis `
      -InputDispatchStartPageMs $inputDispatchStartPageMs `
      -InputDispatchStartWallMs $inputDispatchStartWallMs `
      -FirstInputEventPageMs $firstInputEventPageMs `
      -FirstInputEventType $firstInputEventType `
      -FirstInputEventKeyOrButton $firstInputEventKeyOrButton `
      -FirstInputEventWallMs $(if ($null -ne $firstInputEventWallMs) { [long]([double]$firstInputEventWallMs) } else { $null }) `
      -FirstMovementInputEventPageMs $firstMovementInputEventPageMs `
      -FirstMovementInputEventType $firstMovementInputEventType `
      -FirstMovementInputEventKeyOrButton $firstMovementInputEventKeyOrButton `
      -FirstMovementInputEventWallMs $(if ($null -ne $firstMovementInputEventWallMs) { [long]([double]$firstMovementInputEventWallMs) } else { $null }) `
      -FirstFireInputEventPageMs $firstFireInputEventPageMs `
      -FirstFireInputEventType $firstFireInputEventType `
      -FirstFireInputEventKeyOrButton $firstFireInputEventKeyOrButton `
      -FirstFireInputEventWallMs $(if ($null -ne $firstFireInputEventWallMs) { [long]([double]$firstFireInputEventWallMs) } else { $null }) `
      -MotionInputStartPageMs $motionInputStartPageMs `
      -MotionInputStartWallMs ([long]$motionInputStartWallMs) `
      -MotionLatencyBasis $motionLatencyBasis `
      -MuzzleInputStartPageMs $muzzleInputStartPageMs `
      -MuzzleInputStartWallMs ([long]$muzzleInputStartWallMs) `
      -MuzzleLatencyBasis $muzzleLatencyBasis `
      -DispatchToEventOverheadMs $dispatchToEventOverheadMs
    if ($finalLocalFeedbackProbeMetric -is [System.Collections.IDictionary]) {
      $finalLocalFeedbackProbeMetric["legacyInputStartPageMs"] = $inputStartPageMs
      $finalLocalFeedbackProbeMetric["legacyInputStartWallMs"] = $inputStartPageWallMs
      $finalLocalFeedbackProbeMetric["preDispatchOverheadMs"] = $preDispatchOverheadMs
    }
    $lastLocalFeedbackProbeMetric = $finalLocalFeedbackProbeMetric
    if ($null -ne $finalLocalFeedbackProbeMetric -and $finalLocalFeedbackProbeMetric.available -eq $true) {
      $localFeedbackProbeMetric = $finalLocalFeedbackProbeMetric
    }
  }
  $commandFetchProbe = Read-CommandFetchProbe -Client $Client
  $transientNoticeProbe = Read-TransientNoticeProbe -Client $Client

  return [pscustomobject]@{
    scenario = $Scenario
    x = $x
    y = $y
    durationMs = $DurationMs
    fireStartOffsetMs = $(if ($Scenario -eq "MixedMovement" -or $Scenario -eq "SkillPressure") { $mixedFireStartMs } else { 0 })
    fireEndOffsetMs = $(if ($Scenario -eq "MixedMovement" -or $Scenario -eq "SkillPressure") { $mixedFireEndMs } else { $DurationMs })
    firePressCount = $firePressCount
    fireReleaseCount = $fireReleaseCount
    aimSampleCount = $aimSampleCount
    skillTapCount = $skillTapCount
    skillKeys = @($skillKeys)
    skillTapScheduleMs = @(
      $skillTapSchedule |
        Where-Object { $DurationMs -ge [int]$_.OffsetMs } |
        ForEach-Object { [ordered]@{ offsetMs = [int]$_.OffsetMs; key = $_.Key } }
    )
    targetedSkillTapCount = $targetedSkillTapCount
    targetedSkillKeys = @($targetedSkillKeys)
    targetedConfirmCount = $targetedConfirmCount
    targetedSkillTapScheduleMs = @(
      $targetedSkillTapSchedule |
        Where-Object { $DurationMs -ge [int]$_.OffsetMs } |
        ForEach-Object { [ordered]@{ offsetMs = [int]$_.OffsetMs; key = $_.Key; skill = $_.Skill } }
    )
    targetedConfirmScheduleMs = @(
      $targetedConfirmSchedule |
        Where-Object { $DurationMs -ge [int]$_.PressOffsetMs } |
        ForEach-Object {
          [ordered]@{
            pressOffsetMs = [int]$_.PressOffsetMs
            releaseOffsetMs = [int]$_.ReleaseOffsetMs
            skill = $_.Skill
          }
        }
    )
    weaponSwitchWheelCount = $weaponSwitchWheelCount
    weaponSwitchWheelScheduleMs = @(
      $weaponSwitchWheelSchedule |
        Where-Object { $DurationMs -ge [int]$_.OffsetMs } |
        ForEach-Object { [ordered]@{ offsetMs = [int]$_.OffsetMs; deltaY = [int]$_.DeltaY } }
    )
    inputStartPageMs = $inputStartPageMs
    inputDispatchStartPageMs = $inputDispatchStartPageMs
    firstInputEventPageMs = $firstInputEventPageMs
    firstInputEventType = $firstInputEventType
    firstInputEventKeyOrButton = $firstInputEventKeyOrButton
    firstMovementInputEventPageMs = $firstMovementInputEventPageMs
    firstMovementInputEventType = $firstMovementInputEventType
    firstMovementInputEventKeyOrButton = $firstMovementInputEventKeyOrButton
    firstFireInputEventPageMs = $firstFireInputEventPageMs
    firstFireInputEventType = $firstFireInputEventType
    firstFireInputEventKeyOrButton = $firstFireInputEventKeyOrButton
    inputEndPageMs = $inputEndPageMs
    inputStartPageWallMs = $inputStartPageWallMs
    inputDispatchStartWallMs = $inputDispatchStartWallMs
    firstInputEventWallMs = $firstInputEventWallMs
    firstMovementInputEventWallMs = $firstMovementInputEventWallMs
    firstFireInputEventWallMs = $firstFireInputEventWallMs
    inputEndPageWallMs = $inputEndPageWallMs
    preDispatchOverheadMs = $preDispatchOverheadMs
    dispatchToEventOverheadMs = $dispatchToEventOverheadMs
    latencyBasis = $localFeedbackLatencyBasis
    motionLatencyBasis = $motionLatencyBasis
    muzzleLatencyBasis = $muzzleLatencyBasis
    motionInputStartPageMs = $motionInputStartPageMs
    muzzleInputStartPageMs = $muzzleInputStartPageMs
    motionInputStartWallMs = $motionInputStartWallMs
    muzzleInputStartWallMs = $muzzleInputStartWallMs
    inputEventProbe = $inputEventProbe
    inputEventProbeInstall = $inputEventProbeInstall
    commandFetchProbe = $commandFetchProbe
    commandFetchProbeInstall = $commandFetchProbeInstall
    transientNoticeProbe = $transientNoticeProbe
    transientNoticeProbeInstall = $transientNoticeProbeInstall
    focus = [ordered]@{
      before = $focusBefore
      afterBringToFront = $focusAfterBringToFront
    }
    inputProbe = [ordered]@{
      pollIntervalMs = $ProbePollIntervalMs
      probeWindow = "inputBurst"
      authoritative = $authoritativeProbeMetric
      pageSnapshot = $pageSnapshotProbeMetric
      localFeedback = $localFeedbackProbeMetric
      remoteView = $remoteViewProbeMetric
      lastAuthoritative = $lastAuthoritativeProbeMetric
      lastPageSnapshot = $lastPageSnapshotProbeMetric
      lastLocalFeedback = $lastLocalFeedbackProbeMetric
      lastRemoteView = $lastRemoteViewProbeMetric
      commandFetchProbe = $commandFetchProbe
      transientNoticeProbe = $transientNoticeProbe
    }
  }
}

function Get-BattleState {
  param(
    [Parameter(Mandatory = $true)][string]$BackendBase,
    [Parameter(Mandatory = $true)][string]$BattleId
  )

  $encodedBattleId = [System.Uri]::EscapeDataString($BattleId)
  return Invoke-SmokeJson -Method "GET" -Uri (Join-TestUrl -Base $BackendBase -Path "/battle/state/$encodedBattleId") -TimeoutSec 8
}

function Find-StatePlayer {
  param(
    [Parameter(Mandatory = $true)]$State,
    [Parameter(Mandatory = $true)][string]$Handle,
    [string]$PlayerId
  )

  $players = @($State.players)
  if (-not [string]::IsNullOrWhiteSpace($PlayerId)) {
    $byPlayerId = @($players | Where-Object { $_.playerId -ceq $PlayerId } | Select-Object -First 1)
    if ($byPlayerId.Count -gt 0) {
      return $byPlayerId[0]
    }
  }

  $normalizedHandle = $Handle.Trim().ToLowerInvariant()
  $byHandle = @(
    $players |
      Where-Object {
        ("" + $_.handle).Trim().ToLowerInvariant() -eq $normalizedHandle -or
        ("" + $_.displayName).Trim().ToLowerInvariant() -eq $normalizedHandle
      } |
      Select-Object -First 1
  )
  if ($byHandle.Count -gt 0) {
    return $byHandle[0]
  }

  return $null
}

function Get-OwnerProjectileCount {
  param(
    [Parameter(Mandatory = $true)]$State,
    [string]$HeroId
  )

  if ([string]::IsNullOrWhiteSpace($HeroId)) {
    return 0
  }

  return @($State.projectiles | Where-Object { $_.ownerHeroId -ceq $HeroId }).Count
}

function Measure-StateActionEffect {
  param(
    [Parameter(Mandatory = $true)]$BeforeState,
    [Parameter(Mandatory = $true)]$AfterState,
    [Parameter(Mandatory = $true)][string]$Handle,
    [string]$PlayerId
  )

  $beforePlayer = Find-StatePlayer -State $BeforeState -Handle $Handle -PlayerId $PlayerId
  $afterPlayer = Find-StatePlayer -State $AfterState -Handle $Handle -PlayerId $PlayerId
  if ($null -eq $beforePlayer -or $null -eq $afterPlayer) {
    return [pscustomobject]@{
      available = $false
      reason = "player not found in authoritative state"
    }
  }

  $dx = [double]$afterPlayer.position.x - [double]$beforePlayer.position.x
  $dy = [double]$afterPlayer.position.y - [double]$beforePlayer.position.y
  $distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
  $beforeProjectileCount = Get-OwnerProjectileCount -State $BeforeState -HeroId $beforePlayer.heroId
  $afterProjectileCount = Get-OwnerProjectileCount -State $AfterState -HeroId $afterPlayer.heroId
  $ammoDelta = [int]$beforePlayer.ammoInMagazine - [int]$afterPlayer.ammoInMagazine
  $projectileDelta = [int]$afterProjectileCount - [int]$beforeProjectileCount

  return [pscustomobject]@{
    available = $true
    playerId = $afterPlayer.playerId
    heroId = $afterPlayer.heroId
    beforePosition = $beforePlayer.position
    afterPosition = $afterPlayer.position
    movementDistance = $distance
    beforeAmmo = $beforePlayer.ammoInMagazine
    afterAmmo = $afterPlayer.ammoInMagazine
    ammoDelta = $ammoDelta
    beforeOwnerProjectileCount = $beforeProjectileCount
    afterOwnerProjectileCount = $afterProjectileCount
    projectileDelta = $projectileDelta
    moved = $distance -gt 1.0
    fired = ($ammoDelta -gt 0 -or $projectileDelta -gt 0)
    passed = ($distance -gt 1.0 -or $ammoDelta -gt 0 -or $projectileDelta -gt 0)
  }
}

function Get-ActionTriggeredConditions {
  param($Metric)

  $conditions = @()
  if ($null -eq $Metric) {
    return $conditions
  }

  if ($Metric.moved -eq $true) {
    $conditions += "moved"
  }
  if ($Metric.fired -eq $true) {
    $conditions += "fired"
  }

  return $conditions
}

function Add-ActionConfirmationMetadata {
  param(
    [Parameter(Mandatory = $true)]$Metric,
    [Parameter(Mandatory = $true)][long]$InputStartWallMs,
    [Parameter(Mandatory = $true)][long]$ConfirmWallMs,
    [Parameter(Mandatory = $true)][int]$Attempt,
    [Parameter(Mandatory = $true)][string]$ProbeWindow,
    [Parameter(Mandatory = $true)][bool]$CapturedDuringInput
  )

  $Metric | Add-Member -NotePropertyName confirmWallMs -NotePropertyValue $ConfirmWallMs -Force
  $Metric | Add-Member -NotePropertyName confirmLatencyMs -NotePropertyValue ([long]($ConfirmWallMs - $InputStartWallMs)) -Force
  $Metric | Add-Member -NotePropertyName confirmedOnAttempt -NotePropertyValue $Attempt -Force
  $Metric | Add-Member -NotePropertyName triggeredBy -NotePropertyValue (Get-ActionTriggeredConditions -Metric $Metric) -Force
  $Metric | Add-Member -NotePropertyName probeWindow -NotePropertyValue $ProbeWindow -Force
  $Metric | Add-Member -NotePropertyName capturedDuringInput -NotePropertyValue $CapturedDuringInput -Force
  return $Metric
}

function Wait-AuthoritativeActionEffect {
  param(
    [Parameter(Mandatory = $true)][string]$BackendBase,
    [Parameter(Mandatory = $true)][string]$BattleId,
    [Parameter(Mandatory = $true)]$BeforeState,
    [Parameter(Mandatory = $true)][string]$Handle,
    [string]$PlayerId,
    [Nullable[long]]$InputStartWallMs = $null,
    [string]$ProbeWindow = "postInputFallback",
    [bool]$CapturedDuringInput = $false
  )

  $lastMetric = $null
  for ($attempt = 0; $attempt -lt 12; $attempt++) {
    $afterState = Get-BattleState -BackendBase $BackendBase -BattleId $BattleId
    $lastMetric = Measure-StateActionEffect -BeforeState $BeforeState -AfterState $afterState -Handle $Handle -PlayerId $PlayerId
    if ($lastMetric.available -eq $true -and $lastMetric.passed -eq $true) {
      $confirmWallMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
      if ($null -eq $InputStartWallMs) {
        $InputStartWallMs = $confirmWallMs
      }
      return Add-ActionConfirmationMetadata `
        -Metric $lastMetric `
        -InputStartWallMs ([long]$InputStartWallMs) `
        -ConfirmWallMs $confirmWallMs `
        -Attempt ($attempt + 1) `
        -ProbeWindow $ProbeWindow `
        -CapturedDuringInput $CapturedDuringInput
    }

    Start-Sleep -Milliseconds 250
  }

  return $lastMetric
}

function Find-ContextHero {
  param(
    $Context,
    [Parameter(Mandatory = $true)][string]$Handle
  )

  $session = $Context.primarySession
  if ($null -eq $session -or $null -eq $session.snapshot) {
    return $null
  }

  $heroes = @($session.snapshot.heroes)
  $playerHeroId = "" + $session.snapshot.playerHeroId
  if (-not [string]::IsNullOrWhiteSpace($playerHeroId)) {
    $byHeroId = @($heroes | Where-Object { $_.heroId -ceq $playerHeroId } | Select-Object -First 1)
    if ($byHeroId.Count -gt 0) {
      return $byHeroId[0]
    }
  }

  $normalizedHandle = $Handle.Trim().ToLowerInvariant()
  $byHandle = @(
    $heroes |
      Where-Object { ("" + $_.displayName).Trim().ToLowerInvariant() -eq $normalizedHandle } |
      Select-Object -First 1
  )
  if ($byHandle.Count -gt 0) {
    return $byHandle[0]
  }

  return $null
}

function Measure-LocalStorageActionEffect {
  param(
    $BeforeContext,
    $AfterContext,
    [Parameter(Mandatory = $true)][string]$Handle
  )

  $beforeHero = Find-ContextHero -Context $BeforeContext -Handle $Handle
  $afterHero = Find-ContextHero -Context $AfterContext -Handle $Handle
  if ($null -eq $beforeHero -or $null -eq $afterHero) {
    return [pscustomobject]@{
      available = $false
      reason = "player not found in active battle session localStorage"
    }
  }

  $dx = [double]$afterHero.position.x - [double]$beforeHero.position.x
  $dy = [double]$afterHero.position.y - [double]$beforeHero.position.y
  $distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
  $beforeProjectileCount = [int]$BeforeContext.primarySession.snapshot.projectileCount
  $afterProjectileCount = [int]$AfterContext.primarySession.snapshot.projectileCount
  $ammoDelta = [int]$beforeHero.ammoInMagazine - [int]$afterHero.ammoInMagazine
  $projectileDelta = $afterProjectileCount - $beforeProjectileCount

  return [pscustomobject]@{
    available = $true
    source = "localStorage.activeBattleSession"
    heroId = $afterHero.heroId
    beforePosition = $beforeHero.position
    afterPosition = $afterHero.position
    movementDistance = $distance
    beforeAmmo = $beforeHero.ammoInMagazine
    afterAmmo = $afterHero.ammoInMagazine
    ammoDelta = $ammoDelta
    beforeProjectileCount = $beforeProjectileCount
    afterProjectileCount = $afterProjectileCount
    projectileDelta = $projectileDelta
    moved = $distance -gt 1.0
    fired = ($ammoDelta -gt 0 -or $projectileDelta -gt 0)
    passed = ($distance -gt 1.0 -or $ammoDelta -gt 0 -or $projectileDelta -gt 0)
  }
}

function Wait-PageSnapshotActionEffect {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)]$BeforeContext,
    [Parameter(Mandatory = $true)][string]$Handle,
    [Parameter(Mandatory = $true)][long]$InputStartWallMs,
    [int]$TimeoutMs = 2500,
    [int]$PollIntervalMs = 75,
    [string]$ProbeWindow = "postInputFallback",
    [bool]$CapturedDuringInput = $false
  )

  $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($TimeoutMs)
  $lastMetric = $null
  $attempt = 0
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $attempt += 1
    $currentContext = Get-PageBattleContext -Client $Client
    $lastMetric = Measure-LocalStorageActionEffect -BeforeContext $BeforeContext -AfterContext $currentContext -Handle $Handle
    if ($lastMetric.available -eq $true -and $lastMetric.passed -eq $true) {
      $confirmWallMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
      return Add-ActionConfirmationMetadata `
        -Metric $lastMetric `
        -InputStartWallMs $InputStartWallMs `
        -ConfirmWallMs $confirmWallMs `
        -Attempt $attempt `
        -ProbeWindow $ProbeWindow `
        -CapturedDuringInput $CapturedDuringInput
    }

    Start-Sleep -Milliseconds $PollIntervalMs
  }

  $reason = "active battle session localStorage did not show movement or firing within ${TimeoutMs}ms"
  if ($null -ne $lastMetric -and -not [string]::IsNullOrWhiteSpace($lastMetric.reason)) {
    $reason = $lastMetric.reason
  }

  return [pscustomobject]@{
    available = $false
    source = "localStorage.activeBattleSession"
    status = "unavailable"
    reason = $reason
    timeoutMs = $TimeoutMs
    pollIntervalMs = $PollIntervalMs
    probeWindow = $ProbeWindow
    capturedDuringInput = $CapturedDuringInput
    lastMetric = $lastMetric
  }
}

function New-InputFeedbackLatencyMetric {
  param(
    [Parameter(Mandatory = $true)][long]$InputStartWallMs,
    [Parameter(Mandatory = $true)][long]$InputEndWallMs,
    $AuthoritativeMetric,
    $PageSnapshotMetric
  )

  $authoritativeLatencyMs = $null
  if ($null -ne $AuthoritativeMetric -and $AuthoritativeMetric.available -eq $true -and $AuthoritativeMetric.passed -eq $true) {
    if ($null -ne $AuthoritativeMetric.PSObject.Properties["confirmLatencyMs"]) {
      $authoritativeLatencyMs = $AuthoritativeMetric.confirmLatencyMs
    }
  }

  $pageSnapshotLatencyMs = $null
  if ($null -ne $PageSnapshotMetric -and $PageSnapshotMetric.available -eq $true -and $PageSnapshotMetric.passed -eq $true) {
    if ($null -ne $PageSnapshotMetric.PSObject.Properties["confirmLatencyMs"]) {
      $pageSnapshotLatencyMs = $PageSnapshotMetric.confirmLatencyMs
    }
  }

  $triggeredBy = [ordered]@{
    authoritative = @()
    pageSnapshot = @()
  }
  if ($null -ne $AuthoritativeMetric -and $AuthoritativeMetric.PSObject.Properties["triggeredBy"]) {
    $triggeredBy.authoritative = @($AuthoritativeMetric.triggeredBy)
  }
  if ($null -ne $PageSnapshotMetric -and $PageSnapshotMetric.PSObject.Properties["triggeredBy"]) {
    $triggeredBy.pageSnapshot = @($PageSnapshotMetric.triggeredBy)
  }

  $probeWindow = [ordered]@{
    authoritative = $null
    pageSnapshot = $null
  }
  if ($null -ne $AuthoritativeMetric -and $AuthoritativeMetric.PSObject.Properties["probeWindow"]) {
    $probeWindow.authoritative = $AuthoritativeMetric.probeWindow
  }
  if ($null -ne $PageSnapshotMetric -and $PageSnapshotMetric.PSObject.Properties["probeWindow"]) {
    $probeWindow.pageSnapshot = $PageSnapshotMetric.probeWindow
  }

  $capturedDuringInput = [ordered]@{
    authoritative = $false
    pageSnapshot = $false
  }
  if ($null -ne $AuthoritativeMetric -and $AuthoritativeMetric.PSObject.Properties["capturedDuringInput"]) {
    $capturedDuringInput.authoritative = [bool]$AuthoritativeMetric.capturedDuringInput
  }
  if ($null -ne $PageSnapshotMetric -and $PageSnapshotMetric.PSObject.Properties["capturedDuringInput"]) {
    $capturedDuringInput.pageSnapshot = [bool]$PageSnapshotMetric.capturedDuringInput
  }

  $source = "unavailable"
  $available = $false
  if ($null -ne $authoritativeLatencyMs) {
    $source = "api.authoritativeBattleState"
    $available = $true
  } elseif ($null -ne $pageSnapshotLatencyMs) {
    $source = "localStorage.activeBattleSession"
    $available = $true
  }

  return [ordered]@{
    available = $available
    source = $source
    inputStartWallMs = $InputStartWallMs
    inputEndWallMs = $InputEndWallMs
    inputDurationMs = [long]($InputEndWallMs - $InputStartWallMs)
    authoritativeConfirmLatencyMs = $authoritativeLatencyMs
    pageSnapshotConfirmLatencyMs = $pageSnapshotLatencyMs
    probeWindow = $probeWindow
    capturedDuringInput = $capturedDuringInput
    triggeredBy = $triggeredBy
    authoritative = $AuthoritativeMetric
    pageSnapshot = $PageSnapshotMetric
  }
}

function Get-ObjectPropertyValue {
  param(
    $InputObject,
    [Parameter(Mandatory = $true)][string]$Name,
    $DefaultValue = $null
  )

  if ($null -eq $InputObject) {
    return $DefaultValue
  }

  if ($InputObject -is [System.Collections.IDictionary]) {
    if ($InputObject.Contains($Name)) {
      return $InputObject[$Name]
    }

    return $DefaultValue
  }

  $property = $InputObject.PSObject.Properties[$Name]
  if ($null -eq $property) {
    return $DefaultValue
  }

  return $property.Value
}

function Read-VfxDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings = $null
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.vfx : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.vfx is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      if ($null -ne $Warnings) {
        $Warnings.Add("vfxMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      }
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    $reason = $_.Exception.Message
    if ($null -ne $Warnings) {
      $Warnings.Add("vfxMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    }
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function New-VfxClientMetric {
  param(
    $Before,
    $After
  )

  $beforeDiagnostics = Get-ObjectPropertyValue -InputObject $Before -Name "diagnostics"
  $afterDiagnostics = Get-ObjectPropertyValue -InputObject $After -Name "diagnostics"
  if (
    $null -eq $Before -or
    $null -eq $After -or
    $Before.available -ne $true -or
    $After.available -ne $true -or
    $null -eq $beforeDiagnostics -or
    $null -eq $afterDiagnostics
  ) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      mode = "window.__slayDemoBattleDiagnostics.vfx"
      before = $Before
      after = $After
    }
  }

  $beforeCreatedCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "createdCount" -DefaultValue 0)
  $afterCreatedCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "createdCount" -DefaultValue 0)
  $beforeDestroyedCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "destroyedCount" -DefaultValue 0)
  $afterDestroyedCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "destroyedCount" -DefaultValue 0)

  return [ordered]@{
    available = $true
    status = "available"
    mode = "window.__slayDemoBattleDiagnostics.vfx"
    createdDelta = $afterCreatedCount - $beforeCreatedCount
    destroyedDelta = $afterDestroyedCount - $beforeDestroyedCount
    activeTransientCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "activeTransientCount" -DefaultValue 0)
    trackedTransientSlotCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "trackedTransientSlotCount" -DefaultValue 0)
    activeRingCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "activeRingCount" -DefaultValue 0)
    peakActiveTransientCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "peakActiveTransientCount" -DefaultValue 0)
    before = $Before
    after = $After
  }
}

function Read-HudDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings = $null
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.hud : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.hud is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      if ($null -ne $Warnings) {
        $Warnings.Add("hudMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      }
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    $reason = $_.Exception.Message
    if ($null -ne $Warnings) {
      $Warnings.Add("hudMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    }
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function New-HudClientMetric {
  param(
    $Before,
    $After
  )

  $beforeDiagnostics = Get-ObjectPropertyValue -InputObject $Before -Name "diagnostics"
  $afterDiagnostics = Get-ObjectPropertyValue -InputObject $After -Name "diagnostics"
  if (
    $null -eq $Before -or
    $null -eq $After -or
    $Before.available -ne $true -or
    $After.available -ne $true -or
    $null -eq $beforeDiagnostics -or
    $null -eq $afterDiagnostics
  ) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      mode = "window.__slayDemoBattleDiagnostics.hud"
      before = $Before
      after = $After
    }
  }

  $beforeRenderCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "minimapRenderCount" -DefaultValue 0)
  $afterRenderCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "minimapRenderCount" -DefaultValue 0)
  $beforeStaticRedrawCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "minimapStaticLayerRedrawCount" -DefaultValue 0)
  $afterStaticRedrawCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "minimapStaticLayerRedrawCount" -DefaultValue 0)

  return [ordered]@{
    available = $true
    status = "available"
    mode = "window.__slayDemoBattleDiagnostics.hud"
    minimapRenderDelta = $afterRenderCount - $beforeRenderCount
    minimapStaticLayerRedrawDelta = $afterStaticRedrawCount - $beforeStaticRedrawCount
    minimapRenderCount = $afterRenderCount
    minimapStaticLayerRedrawCount = $afterStaticRedrawCount
    lastObstacleCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "lastObstacleCount" -DefaultValue 0)
    lastHeroCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "lastHeroCount" -DefaultValue 0)
    lastPickupCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "lastPickupCount" -DefaultValue 0)
    before = $Before
    after = $After
  }
}

function Read-PageTimingMarker {
  param($Client)

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  return {
    pageNowMs,
    timeOriginMs,
    wallMs: Date.now()
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
}

function Install-InputEventProbe {
  param($Client)

  $expression = @'
(() => {
  const previous = window.__bp28InputEventProbe;
  if (previous && typeof previous.cleanup === "function") {
    previous.cleanup();
  }
  const probe = {
    installedAtPageMs: typeof performance !== "undefined" && typeof performance.now === "function" ? performance.now() : null,
    installedAtWallMs: Date.now(),
    timeOriginMs: typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin) ? performance.timeOrigin : null,
    first: null,
    firstMovement: null,
    firstFire: null,
    events: [],
    keyCounts: {}
  };
  const cleanup = () => {
    window.removeEventListener("keydown", onInput, true);
    window.removeEventListener("mousedown", onInput, true);
    if (probe.timeoutId) {
      clearTimeout(probe.timeoutId);
      probe.timeoutId = null;
    }
  };
  const onInput = (event) => {
    const pageMs = typeof performance !== "undefined" && typeof performance.now === "function" ? performance.now() : null;
    const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin) ? performance.timeOrigin : probe.timeOriginMs;
    const input = {
      pageMs,
      type: event.type,
      keyOrButton: event.type === "keydown" ? event.key : event.button,
      code: event.type === "keydown" ? event.code : null,
      wallMs: Number.isFinite(timeOriginMs) && Number.isFinite(pageMs) ? timeOriginMs + pageMs : Date.now(),
      dateNowMs: Date.now(),
      timeOriginMs
    };
    probe.events.push(input);
    if (probe.events.length > 80) {
      probe.events.shift();
    }
    if (event.type === "keydown") {
      const normalizedKey = typeof event.key === "string" ? event.key.toLowerCase() : "";
      probe.keyCounts[normalizedKey] = (probe.keyCounts[normalizedKey] || 0) + 1;
    }
    if (!probe.first) {
      probe.first = input;
    }
    if (!probe.firstMovement && event.type === "keydown") {
      const key = typeof event.key === "string" ? event.key.toLowerCase() : "";
      const code = typeof event.code === "string" ? event.code.toLowerCase() : "";
      const movementKeys = new Set(["w", "a", "s", "d", "arrowup", "arrowleft", "arrowdown", "arrowright"]);
      const movementCodes = new Set(["keyw", "keya", "keys", "keyd", "arrowup", "arrowleft", "arrowdown", "arrowright"]);
      if (movementKeys.has(key) || movementCodes.has(code)) {
        probe.firstMovement = input;
      }
    }
    if (!probe.firstFire && event.type === "mousedown") {
      probe.firstFire = input;
    }
  };
  cleanup();
  probe.cleanup = cleanup;
  probe.timeoutId = setTimeout(cleanup, 5000);
  window.__bp28InputEventProbe = probe;
  window.addEventListener("keydown", onInput, true);
  window.addEventListener("mousedown", onInput, true);
  return {
    installedAtPageMs: probe.installedAtPageMs,
    installedAtWallMs: probe.installedAtWallMs,
    timeOriginMs: probe.timeOriginMs
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
}

function Read-InputEventProbe {
  param(
    $Client,
    [switch]$KeepInstalled
  )

  $shouldCleanup = if ($KeepInstalled) { "false" } else { "true" }
  $expression = @"
(() => {
  const shouldCleanup = $shouldCleanup;
  const probe = window.__bp28InputEventProbe;
  if (!probe) {
    return {
      available: false,
      reason: "input event probe is not installed"
    };
  }
  if (shouldCleanup && typeof probe.cleanup === "function") {
    probe.cleanup();
  }
  const first = probe.first || null;
  const firstMovement = probe.firstMovement || null;
  const firstFire = probe.firstFire || null;
  if (shouldCleanup) {
    delete window.__bp28InputEventProbe;
  }
  return {
    available: !!first,
    reason: first ? null : "input event probe did not capture keydown or mousedown",
    installedAtPageMs: probe.installedAtPageMs,
    installedAtWallMs: probe.installedAtWallMs,
    timeOriginMs: probe.timeOriginMs,
    firstInputEventPageMs: first ? first.pageMs : null,
    firstInputEventType: first ? first.type : null,
    firstInputEventKeyOrButton: first ? first.keyOrButton : null,
    firstInputEventWallMs: first ? first.wallMs : null,
    firstInputEventDateNowMs: first ? first.dateNowMs : null,
    firstInputEventTimeOriginMs: first ? first.timeOriginMs : null,
    firstMovementInputEventPageMs: firstMovement ? firstMovement.pageMs : null,
    firstMovementInputEventType: firstMovement ? firstMovement.type : null,
    firstMovementInputEventKeyOrButton: firstMovement ? firstMovement.keyOrButton : null,
    firstMovementInputEventWallMs: firstMovement ? firstMovement.wallMs : null,
    firstMovementInputEventDateNowMs: firstMovement ? firstMovement.dateNowMs : null,
    firstMovementInputEventTimeOriginMs: firstMovement ? firstMovement.timeOriginMs : null,
    firstFireInputEventPageMs: firstFire ? firstFire.pageMs : null,
    firstFireInputEventType: firstFire ? firstFire.type : null,
    firstFireInputEventKeyOrButton: firstFire ? firstFire.keyOrButton : null,
    firstFireInputEventWallMs: firstFire ? firstFire.wallMs : null,
    firstFireInputEventDateNowMs: firstFire ? firstFire.dateNowMs : null,
    firstFireInputEventTimeOriginMs: firstFire ? firstFire.timeOriginMs : null,
    keyCounts: probe.keyCounts || {},
    recentEvents: Array.isArray(probe.events) ? probe.events.slice(-40) : []
  };
})()
"@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
}

function Install-CommandFetchProbe {
  param($Client)

  $expression = @'
(() => {
  const previous = window.__bp28CommandFetchProbe;
  if (previous && typeof previous.cleanup === "function") {
    previous.cleanup();
  }

  const originalFetch = window.fetch;
  const probe = {
    available: typeof originalFetch === "function",
    installedAtPageMs: typeof performance !== "undefined" && typeof performance.now === "function" ? performance.now() : null,
    installedAtWallMs: Date.now(),
    timeOriginMs: typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin) ? performance.timeOrigin : null,
    requests: [],
    responseParsePromises: [],
    originalFetch
  };

  const pageNow = () => typeof performance !== "undefined" && typeof performance.now === "function" ? performance.now() : null;
  const requestUrl = (resource) => {
    try {
      if (typeof resource === "string") {
        return resource;
      }
      if (resource instanceof URL) {
        return resource.toString();
      }
      if (typeof Request !== "undefined" && resource instanceof Request) {
        return resource.url;
      }
      if (resource && typeof resource.url === "string") {
        return resource.url;
      }
      return String(resource || "");
    } catch {
      return "";
    }
  };
  const sanitizeString = (value) => typeof value === "string" && value.length > 0 ? value : null;
  const sanitizeOutcome = (outcome) => {
    if (!outcome || typeof outcome !== "object") {
      return null;
    }
    const action = sanitizeString(outcome.action);
    const status = sanitizeString(outcome.status);
    if (!action || !status) {
      return null;
    }
    const sanitized = { action, status };
    const reason = sanitizeString(outcome.reason);
    if (reason) {
      sanitized.reason = reason;
    }
    return sanitized;
  };
  const recordResponsePayload = (record, payload) => {
    if (!payload || typeof payload !== "object") {
      return;
    }
    const acceptedPayload = payload.kind === "accepted" && payload.accepted && typeof payload.accepted === "object"
      ? payload.accepted
      : payload;
    const commandStatus = sanitizeString(acceptedPayload.commandStatus);
    if (commandStatus) {
      record.responseCommandStatus = commandStatus;
    }
    const commandReason = sanitizeString(acceptedPayload.commandReason);
    if (commandReason) {
      record.responseCommandReason = commandReason;
    }
    if (Array.isArray(acceptedPayload.outcomes)) {
      record.responseOutcomes = acceptedPayload.outcomes
        .map(sanitizeOutcome)
        .filter((outcome) => outcome !== null);
    }
  };
  const parseBodyFields = (init) => {
    const fields = {};
    if (!init || typeof init.body !== "string") {
      return fields;
    }
    try {
      const body = JSON.parse(init.body);
      if (Number.isFinite(body?.clientCommandSeq)) {
        fields.bodyClientCommandSeq = body.clientCommandSeq;
      }
      if (typeof body?.primaryHeld === "boolean") {
        fields.bodyPrimaryHeld = body.primaryHeld;
      }
      if (typeof body?.castDash === "boolean") {
        fields.bodyCastDash = body.castDash;
      }
      if (typeof body?.castBlink === "boolean") {
        fields.bodyCastBlink = body.castBlink;
      }
      if (typeof body?.castFreeze === "boolean") {
        fields.bodyCastFreeze = body.castFreeze;
      }
      if (body?.movement && typeof body.movement === "object") {
        fields.bodyMovement = {
          x: Number.isFinite(body.movement.x) ? body.movement.x : null,
          y: Number.isFinite(body.movement.y) ? body.movement.y : null
        };
      }
      if (body?.pointerWorld && typeof body.pointerWorld === "object") {
        fields.bodyPointerWorld = {
          x: Number.isFinite(body.pointerWorld.x) ? body.pointerWorld.x : null,
          y: Number.isFinite(body.pointerWorld.y) ? body.pointerWorld.y : null
        };
      }
    } catch {
      fields.bodyParseFailed = true;
    }
    return fields;
  };
  const cleanup = () => {
    if (window.fetch === patchedFetch) {
      window.fetch = originalFetch;
    }
  };
  async function patchedFetch(...args) {
    const url = requestUrl(args[0]);
    const shouldRecord =
      url.includes("/battle/commands") ||
      url.includes("/battlecommand") ||
      url.includes("/battle/command");
    if (!shouldRecord) {
      return originalFetch.apply(this, args);
    }

    const record = {
      url,
      startedAtPageMs: pageNow(),
      startedAtWallMs: Date.now(),
      ...parseBodyFields(args[1])
    };
    probe.requests.push(record);

    try {
      const response = await originalFetch.apply(this, args);
      record.endedAtPageMs = pageNow();
      record.endedAtWallMs = Date.now();
      if (Number.isFinite(record.startedAtPageMs) && Number.isFinite(record.endedAtPageMs)) {
        record.durationMs = Math.round((record.endedAtPageMs - record.startedAtPageMs) * 1000) / 1000;
      }
      record.ok = !!response.ok;
      record.status = response.status;
      const responseParsePromise = (async () => {
        try {
          recordResponsePayload(record, await response.clone().json());
        } catch {
          record.responseParseFailed = true;
        }
      })();
      probe.responseParsePromises.push(responseParsePromise);
      return response;
    } catch (error) {
      record.endedAtPageMs = pageNow();
      record.endedAtWallMs = Date.now();
      if (Number.isFinite(record.startedAtPageMs) && Number.isFinite(record.endedAtPageMs)) {
        record.durationMs = Math.round((record.endedAtPageMs - record.startedAtPageMs) * 1000) / 1000;
      }
      record.failed = true;
      record.errorName = error && typeof error.name === "string" ? error.name : null;
      throw error;
    }
  }

  probe.cleanup = cleanup;
  if (probe.available) {
    window.fetch = patchedFetch;
  }
  window.__bp28CommandFetchProbe = probe;

  return {
    available: probe.available,
    installedAtPageMs: probe.installedAtPageMs,
    installedAtWallMs: probe.installedAtWallMs,
    timeOriginMs: probe.timeOriginMs
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
}

function Install-TransientNoticeProbe {
  param($Client)

  $expression = @'
(() => {
  const selector = "[data-battle-transient-notice='true'], .arena-shell__transient-notice";
  const readText = () => {
    const node = document.querySelector(selector);
    const text = typeof node?.textContent === "string" ? node.textContent.trim() : "";
    return text.length > 0 ? text : null;
  };
  const probe = {
    available: typeof MutationObserver === "function",
    installedAtPageMs: typeof performance !== "undefined" && typeof performance.now === "function" ? performance.now() : null,
    installedAtWallMs: Date.now(),
    texts: []
  };
  const record = () => {
    const text = readText();
    if (text && probe.texts[probe.texts.length - 1] !== text) {
      probe.texts.push(text);
    }
  };
  record();
  if (probe.available) {
    const observer = new MutationObserver(record);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true
    });
    probe.cleanup = () => observer.disconnect();
  }
  window.__bp28TransientNoticeProbe = probe;
  return {
    available: probe.available,
    installedAtPageMs: probe.installedAtPageMs,
    installedAtWallMs: probe.installedAtWallMs
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
}

function Read-TransientNoticeProbe {
  param($Client)

  $expression = @'
(() => {
  const probe = window.__bp28TransientNoticeProbe;
  if (!probe) {
    return {
      available: false,
      reason: "transient notice probe is not installed",
      texts: []
    };
  }
  if (typeof probe.cleanup === "function") {
    probe.cleanup();
  }
  delete window.__bp28TransientNoticeProbe;
  const texts = Array.isArray(probe.texts)
    ? probe.texts.filter((text) => typeof text === "string" && text.length > 0)
    : [];
  return {
    available: probe.available === true,
    reason: probe.available === true ? null : "MutationObserver is not available",
    installedAtPageMs: probe.installedAtPageMs,
    installedAtWallMs: probe.installedAtWallMs,
    textCount: texts.length,
    texts
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
}

function Read-CommandFetchProbe {
  param($Client)

  $expression = @'
(async () => {
  const probe = window.__bp28CommandFetchProbe;
  if (!probe) {
    return {
      available: false,
      reason: "command fetch probe is not installed",
      requests: []
    };
  }
  if (typeof probe.cleanup === "function") {
    probe.cleanup();
  }
  const responseParsePromises = Array.isArray(probe.responseParsePromises) ? probe.responseParsePromises : [];
  if (responseParsePromises.length) {
    await Promise.allSettled(responseParsePromises);
  }
  delete window.__bp28CommandFetchProbe;
  const requests = Array.isArray(probe.requests) ? probe.requests : [];
  const durations = requests
    .map((request) => request && Number.isFinite(request.durationMs) ? request.durationMs : null)
    .filter((duration) => duration !== null)
    .sort((a, b) => a - b);
  const percentile = (values, p) => {
    if (!values.length) {
      return null;
    }
    const index = Math.min(values.length - 1, Math.max(0, Math.ceil(values.length * p) - 1));
    return values[index];
  };
  const countTrue = (fieldName) =>
    requests.filter((request) => request && request[fieldName] === true).length;
  const outcomes = requests.flatMap((request) => Array.isArray(request?.responseOutcomes) ? request.responseOutcomes : []);
  const countOutcome = (action, status) =>
    outcomes.filter((outcome) => outcome && outcome.action === action && outcome.status === status).length;
  const countStringField = (items, fieldName) =>
    items.reduce((counts, item) => {
      const value = item && typeof item[fieldName] === "string" && item[fieldName].length > 0 ? item[fieldName] : null;
      if (value) {
        counts[value] = (counts[value] || 0) + 1;
      }
      return counts;
    }, {});
  const countOutcomeReasons = (action, status) =>
    countStringField(
      outcomes.filter((outcome) => outcome && outcome.action === action && outcome.status === status),
      "reason"
    );
  const skillNoopWithoutReasonCount = outcomes.filter((outcome) =>
    outcome &&
    outcome.status === "noop" &&
    !(typeof outcome.reason === "string" && outcome.reason.length > 0)
  ).length;
  return {
    available: probe.available === true,
    reason: probe.available === true ? null : "window.fetch is not available",
    installedAtPageMs: probe.installedAtPageMs,
    installedAtWallMs: probe.installedAtWallMs,
    timeOriginMs: probe.timeOriginMs,
    requestCount: requests.length,
    failedCount: requests.filter((request) => request && request.failed === true).length,
    responseParseFailedCount: requests.filter((request) => request && request.responseParseFailed === true).length,
    castDashTrueCount: countTrue("bodyCastDash"),
    castBlinkTrueCount: countTrue("bodyCastBlink"),
    castFreezeTrueCount: countTrue("bodyCastFreeze"),
    blinkAppliedCount: countOutcome("Blink", "applied"),
    blinkNoopCount: countOutcome("Blink", "noop"),
    freezeAppliedCount: countOutcome("Freeze", "applied"),
    freezeNoopCount: countOutcome("Freeze", "noop"),
    skillOutcomeCount: outcomes.length,
    skillOutcomeReasons: countStringField(outcomes, "reason"),
    blinkNoopReasons: countOutcomeReasons("Blink", "noop"),
    freezeNoopReasons: countOutcomeReasons("Freeze", "noop"),
    skillNoopWithoutReasonCount,
    responseCommandStatusCounts: countStringField(requests, "responseCommandStatus"),
    responseCommandReasonCounts: countStringField(requests, "responseCommandReason"),
    firstDurationMs: requests.length ? requests[0].durationMs ?? null : null,
    p95DurationMs: percentile(durations, 0.95),
    maxDurationMs: durations.length ? durations[durations.length - 1] : null,
    requests
  };
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -AwaitPromise -TimeoutSeconds 8
}

function Read-AuthoritativeNetworkDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings = $null
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.authoritativeNetwork : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.authoritativeNetwork is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      if ($null -ne $Warnings) {
        $Warnings.Add("authoritativeNetworkMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      }
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    $reason = $_.Exception.Message
    if ($null -ne $Warnings) {
      $Warnings.Add("authoritativeNetworkMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    }
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function Get-Bp28PercentileValue {
  param(
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][double[]]$SortedValues,
    [Parameter(Mandatory = $true)][double]$Percentile
  )

  if ($SortedValues.Count -eq 0) {
    return $null
  }

  $clamped = [Math]::Min(1.0, [Math]::Max(0.0, $Percentile))
  $index = [int]([Math]::Ceiling($SortedValues.Count * $clamped) - 1)
  $index = [Math]::Min($SortedValues.Count - 1, [Math]::Max(0, $index))
  return $SortedValues[$index]
}

function New-Bp28SampleNumberSummary {
  param(
    [AllowEmptyCollection()]$Samples,
    [Parameter(Mandatory = $true)][string]$FieldName
  )

  $sampleList = @()
  if ($null -ne $Samples) {
    $sampleList = @($Samples)
  }
  $values = @(
    foreach ($sample in $sampleList) {
      $value = Get-ObjectPropertyValue -InputObject $sample -Name $FieldName
      if (Test-FiniteNumber -Value $value) {
        [double]$value
      }
    }
  )

  if ($values.Count -eq 0) {
    return [ordered]@{
      sampleCount = $sampleList.Count
      valueCount = 0
      avg = $null
      max = $null
      p95 = $null
    }
  }

  $sortedValues = @($values | Sort-Object)
  $sum = 0.0
  foreach ($value in $values) {
    $sum += [double]$value
  }

  return [ordered]@{
    sampleCount = $sampleList.Count
    valueCount = $values.Count
    avg = $sum / [double]$values.Count
    max = $sortedValues[$sortedValues.Count - 1]
    p95 = Get-Bp28PercentileValue -SortedValues $sortedValues -Percentile 0.95
  }
}

function New-Bp28SampleFieldCountSummary {
  param(
    [AllowEmptyCollection()]$Samples,
    [Parameter(Mandatory = $true)][string]$FieldName
  )

  $counts = [ordered]@{}
  $sampleList = @()
  if ($null -ne $Samples) {
    $sampleList = @($Samples)
  }
  foreach ($sample in $sampleList) {
    $value = Get-ObjectPropertyValue -InputObject $sample -Name $FieldName
    if ($null -eq $value) {
      continue
    }

    $key = ("" + $value).Trim()
    if ([string]::IsNullOrWhiteSpace($key)) {
      continue
    }

    if ($counts.Contains($key)) {
      $counts[$key] = [int]$counts[$key] + 1
    } else {
      $counts[$key] = 1
    }
  }

  return $counts
}

function New-AuthoritativeNetworkNumericSummary {
  param($Summary)

  if ($null -eq $Summary) {
    return [ordered]@{
      sampleCount = 0
      valueCount = 0
      avg = $null
      max = $null
      p95 = $null
    }
  }

  return [ordered]@{
    sampleCount = Get-ObjectPropertyValue -InputObject $Summary -Name "sampleCount" -DefaultValue 0
    valueCount = Get-ObjectPropertyValue -InputObject $Summary -Name "valueCount" -DefaultValue 0
    avg = Get-ObjectPropertyValue -InputObject $Summary -Name "avg"
    max = Get-ObjectPropertyValue -InputObject $Summary -Name "max"
    p95 = Get-ObjectPropertyValue -InputObject $Summary -Name "p95"
  }
}

function New-AuthoritativeNetworkStateIngressSummary {
  param($StateIngress)

  if ($null -eq $StateIngress) {
    return [ordered]@{
      available = $false
      reason = "authoritativeNetwork.stateIngress is not available"
      count = 0
      sampleCount = 0
    }
  }

  $rawSamples = Get-ObjectPropertyValue -InputObject $StateIngress -Name "recentSamples" -DefaultValue @()
  $samples = @()
  if ($null -ne $rawSamples) {
    $samples = @($rawSamples)
  }
  return [ordered]@{
    available = $true
    count = Get-ObjectPropertyValue -InputObject $StateIngress -Name "count" -DefaultValue 0
    channelCount = Get-ObjectPropertyValue -InputObject $StateIngress -Name "channelCount" -DefaultValue 0
    streamCount = Get-ObjectPropertyValue -InputObject $StateIngress -Name "streamCount" -DefaultValue 0
    pollCount = Get-ObjectPropertyValue -InputObject $StateIngress -Name "pollCount" -DefaultValue 0
    firstAtMs = Get-ObjectPropertyValue -InputObject $StateIngress -Name "firstAtMs"
    lastAtMs = Get-ObjectPropertyValue -InputObject $StateIngress -Name "lastAtMs"
    intervalSummary = New-AuthoritativeNetworkNumericSummary -Summary (Get-ObjectPropertyValue -InputObject $StateIngress -Name "intervalSummary")
    tickDeltaSummary = New-AuthoritativeNetworkNumericSummary -Summary (Get-ObjectPropertyValue -InputObject $StateIngress -Name "tickDeltaSummary")
    elapsedDeltaSummary = New-AuthoritativeNetworkNumericSummary -Summary (Get-ObjectPropertyValue -InputObject $StateIngress -Name "elapsedDeltaSummary")
    sampleCount = Get-ObjectPropertyValue -InputObject $StateIngress -Name "sampleCount" -DefaultValue $samples.Count
    sampleWindowSize = Get-ObjectPropertyValue -InputObject $StateIngress -Name "sampleWindowSize"
    sampleSourceCounts = New-Bp28SampleFieldCountSummary -Samples $samples -FieldName "source"
    sampleIntervalSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "intervalMs"
    sampleTickDeltaSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "tickDelta"
    sampleElapsedDeltaSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "elapsedDeltaMs"
    samplePlayerCountSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "playerCount"
    sampleProjectileCountSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "projectileCount"
    firstSample = if ($samples.Count -gt 0) { $samples[0] } else { $null }
    lastSample = Get-ObjectPropertyValue -InputObject $StateIngress -Name "lastSample" -DefaultValue $(if ($samples.Count -gt 0) { $samples[$samples.Count - 1] } else { $null })
  }
}

function New-AuthoritativeNetworkCommandSubmitSummary {
  param($CommandSubmit)

  if ($null -eq $CommandSubmit) {
    return [ordered]@{
      available = $false
      reason = "authoritativeNetwork.commandSubmit is not available"
      count = 0
      sampleCount = 0
    }
  }

  $rawSamples = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "recentSamples" -DefaultValue @()
  $samples = @()
  if ($null -ne $rawSamples) {
    $samples = @($rawSamples)
  }
  $primaryHeldSamples = @($samples | Where-Object {
    (Get-ObjectPropertyValue -InputObject $_ -Name "primaryHeld" -DefaultValue $false) -eq $true
  })
  $maxLatencySample = $null
  $latencySamples = @($samples | Where-Object {
    $value = Get-ObjectPropertyValue -InputObject $_ -Name "latencyMs"
    $null -ne $value
  } | Sort-Object -Property @{ Expression = { [double](Get-ObjectPropertyValue -InputObject $_ -Name "latencyMs" -DefaultValue 0) }; Descending = $true })
  if ($latencySamples.Count -gt 0) {
    $maxLatencySample = $latencySamples[0]
  }
  $maxQueueDelaySample = $null
  $queueDelaySamples = @($samples | Where-Object {
    $value = Get-ObjectPropertyValue -InputObject $_ -Name "queueDelayMs"
    $null -ne $value
  } | Sort-Object -Property @{ Expression = { [double](Get-ObjectPropertyValue -InputObject $_ -Name "queueDelayMs" -DefaultValue 0) }; Descending = $true })
  if ($queueDelaySamples.Count -gt 0) {
    $maxQueueDelaySample = $queueDelaySamples[0]
  }

  return [ordered]@{
    available = $true
    count = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "count" -DefaultValue 0
    acceptedCount = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "acceptedCount" -DefaultValue 0
    failedCount = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "failedCount" -DefaultValue 0
    firstSubmittedAtMs = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "firstSubmittedAtMs"
    lastCompletedAtMs = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "lastCompletedAtMs"
    latencySummary = New-AuthoritativeNetworkNumericSummary -Summary (Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "latencySummary")
    sampleCount = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "sampleCount" -DefaultValue $samples.Count
    sampleWindowSize = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "sampleWindowSize"
    sampleStatusCounts = New-Bp28SampleFieldCountSummary -Samples $samples -FieldName "status"
    sampleHttpStatusCounts = New-Bp28SampleFieldCountSummary -Samples $samples -FieldName "httpStatus"
    sampleErrorCodeCounts = New-Bp28SampleFieldCountSummary -Samples $samples -FieldName "errorCode"
    sampleLatencySummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "latencyMs"
    sampleQueueDelaySummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "queueDelayMs"
    sampleInFlightBeforeSendSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "inFlightCountBeforeSend"
    sampleInFlightLimitSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "inFlightLimit"
    priorityInputPendingCount = @($samples | Where-Object {
      (Get-ObjectPropertyValue -InputObject $_ -Name "priorityInputPending" -DefaultValue $false) -eq $true
    }).Count
    sampleClientCommandSeqSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "clientCommandSeq"
    sampleAcceptedCommandSeqSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "acceptedCommandSeq"
    sampleClientTickSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "clientTick"
    sampleAcceptedTickSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "acceptedTick"
    sampleAcceptedTickLagSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "acceptedTickLag"
    sampleAcceptedCommandSeqLagSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "acceptedCommandSeqLag"
    sampleServerPathCounts = New-Bp28SampleFieldCountSummary -Samples $samples -FieldName "serverPath"
    sampleServerDurationSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "serverDurationMs"
    sampleServerLockWaitSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "serverLockWaitMs"
    sampleServerLockHeldSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "serverLockHeldMs"
    sampleServerAdvanceSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "serverAdvanceMs"
    sampleServerCommitRetrySummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "serverCommitRetryCount"
    primaryHeldCount = $primaryHeldSamples.Count
    primaryHeldLatencySummary = New-Bp28SampleNumberSummary -Samples $primaryHeldSamples -FieldName "latencyMs"
    firstPrimaryHeldSample = if ($primaryHeldSamples.Count -gt 0) { $primaryHeldSamples[0] } else { $null }
    maxLatencySample = $maxLatencySample
    maxQueueDelaySample = $maxQueueDelaySample
    firstSample = if ($samples.Count -gt 0) { $samples[0] } else { $null }
    lastSample = Get-ObjectPropertyValue -InputObject $CommandSubmit -Name "lastSample" -DefaultValue $(if ($samples.Count -gt 0) { $samples[$samples.Count - 1] } else { $null })
  }
}

function New-AuthoritativeNetworkCommandDeferSummary {
  param($CommandDefer)

  if ($null -eq $CommandDefer) {
    return [ordered]@{
      available = $false
      reason = "authoritativeNetwork.commandDefer is not available"
      count = 0
      sampleCount = 0
    }
  }

  $rawSamples = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "recentSamples" -DefaultValue @()
  $samples = @()
  if ($null -ne $rawSamples) {
    $samples = @($rawSamples)
  }

  return [ordered]@{
    available = $true
    count = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "count" -DefaultValue 0
    priorityCount = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "priorityCount" -DefaultValue 0
    firstAtMs = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "firstAtMs"
    lastAtMs = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "lastAtMs"
    sampleCount = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "sampleCount" -DefaultValue $samples.Count
    sampleWindowSize = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "sampleWindowSize"
    sampleInFlightCountSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "inFlightCount"
    sampleInFlightLimitSummary = New-Bp28SampleNumberSummary -Samples $samples -FieldName "inFlightLimit"
    prioritySampleCount = @($samples | Where-Object {
      (Get-ObjectPropertyValue -InputObject $_ -Name "priorityInputPending" -DefaultValue $false) -eq $true
    }).Count
    firstSample = if ($samples.Count -gt 0) { $samples[0] } else { $null }
    lastSample = Get-ObjectPropertyValue -InputObject $CommandDefer -Name "lastSample" -DefaultValue $(if ($samples.Count -gt 0) { $samples[$samples.Count - 1] } else { $null })
  }
}

function New-AuthoritativeNetworkReadSummary {
  param($ReadResult)

  if ($null -eq $ReadResult) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = "authoritativeNetwork diagnostics were not read"
    }
  }

  $diagnostics = $null
  if ($ReadResult.available -eq $true) {
    $diagnostics = $ReadResult.diagnostics
  }

  return [ordered]@{
    available = $ReadResult.available
    status = $ReadResult.status
    phase = $ReadResult.phase
    reason = Get-ObjectPropertyValue -InputObject $ReadResult -Name "reason"
    pageNowMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "pageNowMs"
    timeOriginMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "timeOriginMs"
    wallMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "wallMs"
    stateIngress = New-AuthoritativeNetworkStateIngressSummary -StateIngress (Get-ObjectPropertyValue -InputObject $diagnostics -Name "stateIngress")
    commandSubmit = New-AuthoritativeNetworkCommandSubmitSummary -CommandSubmit (Get-ObjectPropertyValue -InputObject $diagnostics -Name "commandSubmit")
    commandDefer = New-AuthoritativeNetworkCommandDeferSummary -CommandDefer (Get-ObjectPropertyValue -InputObject $diagnostics -Name "commandDefer")
  }
}

function Read-LocalFeedbackDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings = $null
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.localFeedback : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.localFeedback is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      if ($null -ne $Warnings) {
        $Warnings.Add("localFeedbackLatencyMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      }
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    $reason = $_.Exception.Message
    if ($null -ne $Warnings) {
      $Warnings.Add("localFeedbackLatencyMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    }
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function Read-VisionDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.vision : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.vision is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $_.Exception.Message
    }
  }
}

function Get-LocalFeedbackChannel {
  param(
    $Diagnostics,
    [Parameter(Mandatory = $true)][ValidateSet("motion", "muzzle")][string]$ChannelName
  )

  if ($null -eq $Diagnostics) {
    return $null
  }

  return Get-ObjectPropertyValue -InputObject $Diagnostics -Name $ChannelName
}

function Get-LocalFeedbackCount {
  param(
    $ReadResult,
    [Parameter(Mandatory = $true)][ValidateSet("motion", "muzzle")][string]$ChannelName
  )

  if ($null -eq $ReadResult -or $ReadResult.available -ne $true) {
    return 0
  }

  $channel = Get-LocalFeedbackChannel -Diagnostics $ReadResult.diagnostics -ChannelName $ChannelName
  if ($null -eq $channel) {
    return 0
  }

  return [int](Get-ObjectPropertyValue -InputObject $channel -Name "count" -DefaultValue 0)
}

function New-LocalFeedbackChannelSummary {
  param($Channel)

  if ($null -eq $Channel) {
    return $null
  }

  return [ordered]@{
    count = Get-ObjectPropertyValue -InputObject $Channel -Name "count" -DefaultValue 0
    firstAtMs = Get-ObjectPropertyValue -InputObject $Channel -Name "firstAtMs"
    lastAtMs = Get-ObjectPropertyValue -InputObject $Channel -Name "lastAtMs"
    sampleCount = Get-ObjectPropertyValue -InputObject $Channel -Name "sampleCount" -DefaultValue 0
    sampleWindowSize = Get-ObjectPropertyValue -InputObject $Channel -Name "sampleWindowSize"
    lastSample = Get-ObjectPropertyValue -InputObject $Channel -Name "lastSample"
  }
}

function New-LocalFeedbackReadSummary {
  param($ReadResult)

  if ($null -eq $ReadResult) {
    return $null
  }

  $diagnostics = $null
  if ($ReadResult.available -eq $true) {
    $diagnostics = $ReadResult.diagnostics
  }
  $motionChannel = Get-LocalFeedbackChannel -Diagnostics $diagnostics -ChannelName "motion"
  $muzzleChannel = Get-LocalFeedbackChannel -Diagnostics $diagnostics -ChannelName "muzzle"

  return [ordered]@{
    available = $ReadResult.available
    status = $ReadResult.status
    phase = $ReadResult.phase
    reason = Get-ObjectPropertyValue -InputObject $ReadResult -Name "reason"
    pageNowMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "pageNowMs"
    timeOriginMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "timeOriginMs"
    wallMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "wallMs"
    motion = New-LocalFeedbackChannelSummary -Channel $motionChannel
    muzzle = New-LocalFeedbackChannelSummary -Channel $muzzleChannel
  }
}

function Find-FirstLocalFeedbackSampleAfterCount {
  param(
    $ReadResult,
    [Parameter(Mandatory = $true)][ValidateSet("motion", "muzzle")][string]$ChannelName,
    [Parameter(Mandatory = $true)][int]$BeforeCount,
    $InputStartPageMs = $null
  )

  if ($null -eq $ReadResult -or $ReadResult.available -ne $true) {
    return $null
  }

  $channel = Get-LocalFeedbackChannel -Diagnostics $ReadResult.diagnostics -ChannelName $ChannelName
  if ($null -eq $channel) {
    return $null
  }

  $samples = @($channel.recentSamples)
  $channelFirstAtMs = Get-ObjectPropertyValue -InputObject $channel -Name "firstAtMs"
  $inputStart = $null
  if ($null -ne $InputStartPageMs) {
    $inputStart = [double]$InputStartPageMs
  }

  $eligible = @(
    $samples |
      Where-Object {
        $sequence = [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0)
        $atMs = [double](Get-ObjectPropertyValue -InputObject $_ -Name "atMs" -DefaultValue -1)
        $include = $true
        if ($sequence -le $BeforeCount) {
          $include = $false
        }
        if ($null -ne $inputStart -and $atMs -lt ($inputStart - 5)) {
          $include = $false
        }
        $include
      } |
      Sort-Object -Property @{ Expression = { [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0) } }
  )

  if ($eligible.Count -eq 0) {
    if ($BeforeCount -eq 0 -and $null -ne $channelFirstAtMs) {
      $firstAtMs = [double]$channelFirstAtMs
      if ($firstAtMs -ge 0 -and ($null -eq $inputStart -or $firstAtMs -ge ($inputStart - 5))) {
        return [pscustomobject]@{
          sequence = 1
          atMs = $firstAtMs
          source = "channel.firstAtMs"
          sampleWindowTruncated = $true
          channelCount = Get-ObjectPropertyValue -InputObject $channel -Name "count" -DefaultValue 0
          channelSampleCount = Get-ObjectPropertyValue -InputObject $channel -Name "sampleCount" -DefaultValue 0
          sampleWindowSize = Get-ObjectPropertyValue -InputObject $channel -Name "sampleWindowSize"
        }
      }
    }

    return $null
  }

  if ($BeforeCount -eq 0 -and $null -ne $channelFirstAtMs) {
    $firstAtMs = [double]$channelFirstAtMs
    $windowFirstAtMs = [double](Get-ObjectPropertyValue -InputObject $eligible[0] -Name "atMs" -DefaultValue -1)
    if (
      $firstAtMs -ge 0 -and
      $windowFirstAtMs -ge 0 -and
      $firstAtMs -lt ($windowFirstAtMs - 0.5) -and
      ($null -eq $inputStart -or $firstAtMs -ge ($inputStart - 5))
    ) {
      return [pscustomobject]@{
        sequence = 1
        atMs = $firstAtMs
        source = "channel.firstAtMs"
        sampleWindowTruncated = $true
        windowedFirstSampleSequence = Get-ObjectPropertyValue -InputObject $eligible[0] -Name "sequence"
        windowedFirstSampleAtMs = $windowFirstAtMs
        channelCount = Get-ObjectPropertyValue -InputObject $channel -Name "count" -DefaultValue 0
        channelSampleCount = Get-ObjectPropertyValue -InputObject $channel -Name "sampleCount" -DefaultValue 0
        sampleWindowSize = Get-ObjectPropertyValue -InputObject $channel -Name "sampleWindowSize"
      }
    }
  }

  return $eligible[0]
}

function Get-LocalFeedbackLatencyMs {
  param(
    $Sample,
    $InputStartPageMs
  )

  if ($null -eq $Sample -or $null -eq $InputStartPageMs) {
    return $null
  }

  $atMs = [double](Get-ObjectPropertyValue -InputObject $Sample -Name "atMs" -DefaultValue -1)
  if ($atMs -lt 0) {
    return $null
  }

  return [Math]::Round([Math]::Max(0, $atMs - [double]$InputStartPageMs), 3)
}

function Test-LocalFeedbackSampleDuringInput {
  param(
    $Sample,
    $InputStartPageMs,
    $InputEndPageMs
  )

  if ($null -eq $Sample -or $null -eq $InputStartPageMs -or $null -eq $InputEndPageMs) {
    return $false
  }

  $atMs = [double](Get-ObjectPropertyValue -InputObject $Sample -Name "atMs" -DefaultValue -1)
  return ($atMs -ge ([double]$InputStartPageMs - 5) -and $atMs -le ([double]$InputEndPageMs + 5))
}

function New-LocalFeedbackLatencyMetric {
  param(
    $Before,
    $After,
    $InputStartPageMs = $null,
    $InputEndPageMs = $null,
    [Nullable[long]]$InputStartWallMs = $null,
    [Nullable[long]]$InputEndWallMs = $null,
    [string]$LatencyBasis = "inputDispatchStartPageMs",
    $InputDispatchStartPageMs = $null,
    [Nullable[long]]$InputDispatchStartWallMs = $null,
    $FirstInputEventPageMs = $null,
    $FirstInputEventType = $null,
    $FirstInputEventKeyOrButton = $null,
    [Nullable[long]]$FirstInputEventWallMs = $null,
    $FirstMovementInputEventPageMs = $null,
    $FirstMovementInputEventType = $null,
    $FirstMovementInputEventKeyOrButton = $null,
    [Nullable[long]]$FirstMovementInputEventWallMs = $null,
    $FirstFireInputEventPageMs = $null,
    $FirstFireInputEventType = $null,
    $FirstFireInputEventKeyOrButton = $null,
    [Nullable[long]]$FirstFireInputEventWallMs = $null,
    $MotionInputStartPageMs = $null,
    [Nullable[long]]$MotionInputStartWallMs = $null,
    [string]$MotionLatencyBasis = $null,
    $MuzzleInputStartPageMs = $null,
    [Nullable[long]]$MuzzleInputStartWallMs = $null,
    [string]$MuzzleLatencyBasis = $null,
    $DispatchToEventOverheadMs = $null
  )

  if ($null -eq $After -or $After.available -ne $true) {
    $unavailableReason = "local feedback diagnostics were not read"
    if ($null -ne $After) {
      $unavailableReason = $After.reason
    }
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = $unavailableReason
      mode = "window.__slayDemoBattleDiagnostics.localFeedback"
      before = New-LocalFeedbackReadSummary -ReadResult $Before
      after = New-LocalFeedbackReadSummary -ReadResult $After
    }
  }

  $beforeMotionCount = Get-LocalFeedbackCount -ReadResult $Before -ChannelName "motion"
  $beforeMuzzleCount = Get-LocalFeedbackCount -ReadResult $Before -ChannelName "muzzle"
  $afterMotionCount = Get-LocalFeedbackCount -ReadResult $After -ChannelName "motion"
  $afterMuzzleCount = Get-LocalFeedbackCount -ReadResult $After -ChannelName "muzzle"
  if ($null -eq $MotionInputStartPageMs) {
    $MotionInputStartPageMs = $InputStartPageMs
  }
  if ($null -eq $MotionInputStartWallMs) {
    $MotionInputStartWallMs = $InputStartWallMs
  }
  if ([string]::IsNullOrWhiteSpace($MotionLatencyBasis)) {
    $MotionLatencyBasis = $LatencyBasis
  }
  if ($null -eq $MuzzleInputStartPageMs) {
    $MuzzleInputStartPageMs = $InputStartPageMs
  }
  if ($null -eq $MuzzleInputStartWallMs) {
    $MuzzleInputStartWallMs = $InputStartWallMs
  }
  if ([string]::IsNullOrWhiteSpace($MuzzleLatencyBasis)) {
    $MuzzleLatencyBasis = $LatencyBasis
  }
  $motionSample = Find-FirstLocalFeedbackSampleAfterCount `
    -ReadResult $After `
    -ChannelName "motion" `
    -BeforeCount $beforeMotionCount `
    -InputStartPageMs $MotionInputStartPageMs
  $muzzleSample = Find-FirstLocalFeedbackSampleAfterCount `
    -ReadResult $After `
    -ChannelName "muzzle" `
    -BeforeCount $beforeMuzzleCount `
    -InputStartPageMs $MuzzleInputStartPageMs

  $motionLatencyMs = Get-LocalFeedbackLatencyMs -Sample $motionSample -InputStartPageMs $MotionInputStartPageMs
  $muzzleLatencyMs = Get-LocalFeedbackLatencyMs -Sample $muzzleSample -InputStartPageMs $MuzzleInputStartPageMs
  $motionCapturedDuringInput = Test-LocalFeedbackSampleDuringInput `
    -Sample $motionSample `
    -InputStartPageMs $MotionInputStartPageMs `
    -InputEndPageMs $InputEndPageMs
  $muzzleCapturedDuringInput = Test-LocalFeedbackSampleDuringInput `
    -Sample $muzzleSample `
    -InputStartPageMs $MuzzleInputStartPageMs `
    -InputEndPageMs $InputEndPageMs

  $motionProbeWindow = $null
  if ($null -ne $motionSample) {
    $motionProbeWindow = if ($motionCapturedDuringInput) { "inputBurst" } else { "postInputFallback" }
  }
  $muzzleProbeWindow = $null
  if ($null -ne $muzzleSample) {
    $muzzleProbeWindow = if ($muzzleCapturedDuringInput) { "inputBurst" } else { "postInputFallback" }
  }
  $inputDurationMs = $null
  if ($null -ne $InputStartWallMs -and $null -ne $InputEndWallMs) {
    $inputDurationMs = [long]($InputEndWallMs - $InputStartWallMs)
  }
  if ($null -eq $InputDispatchStartPageMs) {
    $InputDispatchStartPageMs = $InputStartPageMs
  }
  if ($null -eq $InputDispatchStartWallMs) {
    $InputDispatchStartWallMs = $InputStartWallMs
  }

  return [ordered]@{
    available = $true
    status = "available"
    complete = ($null -ne $motionLatencyMs -and $null -ne $muzzleLatencyMs)
    mode = "window.__slayDemoBattleDiagnostics.localFeedback"
    latencyBasis = $LatencyBasis
    motionLatencyBasis = $MotionLatencyBasis
    muzzleLatencyBasis = $MuzzleLatencyBasis
    inputStartPageMs = $InputStartPageMs
    motionInputStartPageMs = $MotionInputStartPageMs
    muzzleInputStartPageMs = $MuzzleInputStartPageMs
    inputDispatchStartPageMs = $InputDispatchStartPageMs
    firstInputEventPageMs = $FirstInputEventPageMs
    firstInputEventType = $FirstInputEventType
    firstInputEventKeyOrButton = $FirstInputEventKeyOrButton
    firstMovementInputEventPageMs = $FirstMovementInputEventPageMs
    firstMovementInputEventType = $FirstMovementInputEventType
    firstMovementInputEventKeyOrButton = $FirstMovementInputEventKeyOrButton
    firstFireInputEventPageMs = $FirstFireInputEventPageMs
    firstFireInputEventType = $FirstFireInputEventType
    firstFireInputEventKeyOrButton = $FirstFireInputEventKeyOrButton
    inputEndPageMs = $InputEndPageMs
    inputStartWallMs = $InputStartWallMs
    motionInputStartWallMs = $MotionInputStartWallMs
    muzzleInputStartWallMs = $MuzzleInputStartWallMs
    inputDispatchStartWallMs = $InputDispatchStartWallMs
    firstInputEventWallMs = $FirstInputEventWallMs
    firstMovementInputEventWallMs = $FirstMovementInputEventWallMs
    firstFireInputEventWallMs = $FirstFireInputEventWallMs
    inputEndWallMs = $InputEndWallMs
    inputDurationMs = $inputDurationMs
    dispatchToEventOverheadMs = $DispatchToEventOverheadMs
    motionLatencyMs = $motionLatencyMs
    muzzleLatencyMs = $muzzleLatencyMs
    motionCapturedDuringInput = $motionCapturedDuringInput
    muzzleCapturedDuringInput = $muzzleCapturedDuringInput
    motionCountDelta = $afterMotionCount - $beforeMotionCount
    muzzleCountDelta = $afterMuzzleCount - $beforeMuzzleCount
    probeWindow = [ordered]@{
      motion = $motionProbeWindow
      muzzle = $muzzleProbeWindow
    }
    motion = [ordered]@{
      latencyMs = $motionLatencyMs
      latencyBasis = $MotionLatencyBasis
      inputStartPageMs = $MotionInputStartPageMs
      inputStartWallMs = $MotionInputStartWallMs
      capturedDuringInput = $motionCapturedDuringInput
      countDelta = $afterMotionCount - $beforeMotionCount
      firstSample = $motionSample
    }
    muzzle = [ordered]@{
      latencyMs = $muzzleLatencyMs
      latencyBasis = $MuzzleLatencyBasis
      inputStartPageMs = $MuzzleInputStartPageMs
      inputStartWallMs = $MuzzleInputStartWallMs
      capturedDuringInput = $muzzleCapturedDuringInput
      countDelta = $afterMuzzleCount - $beforeMuzzleCount
      firstSample = $muzzleSample
    }
    before = New-LocalFeedbackReadSummary -ReadResult $Before
    after = New-LocalFeedbackReadSummary -ReadResult $After
  }
}

function Test-LocalFeedbackMetricComplete {
  param($Metric)

  return (
    $null -ne $Metric -and
    $Metric.available -eq $true -and
    $null -ne $Metric.motionLatencyMs -and
    $null -ne $Metric.muzzleLatencyMs
  )
}

function Test-LocalFeedbackMetricHasAnySample {
  param($Metric)

  return (
    $null -ne $Metric -and
    $Metric.available -eq $true -and
    ($null -ne $Metric.motionLatencyMs -or $null -ne $Metric.muzzleLatencyMs)
  )
}

function Round-MetricNumber {
  param(
    $Value,
    [int]$Digits = 3
  )

  if ($null -eq $Value) {
    return $null
  }

  return [Math]::Round([double]$Value, $Digits)
}

function Get-Vec2Distance {
  param(
    $Left,
    $Right
  )

  if ($null -eq $Left -or $null -eq $Right) {
    return $null
  }

  $leftX = Get-ObjectPropertyValue -InputObject $Left -Name "x"
  $leftY = Get-ObjectPropertyValue -InputObject $Left -Name "y"
  $rightX = Get-ObjectPropertyValue -InputObject $Right -Name "x"
  $rightY = Get-ObjectPropertyValue -InputObject $Right -Name "y"
  if ($null -eq $leftX -or $null -eq $leftY -or $null -eq $rightX -or $null -eq $rightY) {
    return $null
  }

  return [Math]::Sqrt([Math]::Pow([double]$rightX - [double]$leftX, 2) + [Math]::Pow([double]$rightY - [double]$leftY, 2))
}

function Get-NullableDouble {
  param($Value)

  if ($null -eq $Value) {
    return $null
  }

  try {
    return [double]$Value
  } catch {
    return $null
  }
}

function Assert-InitialCameraLookAheadStable {
  param(
    $VisionRead,
    [Parameter(Mandatory = $true)][string]$Label
  )

  Assert-Condition (
    $null -ne $VisionRead -and $VisionRead.available -eq $true
  ) "$Label vision diagnostics are unavailable before input."

  $diagnostics = Get-ObjectPropertyValue -InputObject $VisionRead -Name "diagnostics"
  $lookAhead = Get-ObjectPropertyValue -InputObject $diagnostics -Name "lookAhead"
  Assert-Condition ($null -ne $lookAhead) "$Label vision.lookAhead is unavailable before input."

  $pointerReady = Get-ObjectPropertyValue -InputObject $lookAhead -Name "pointerReady"
  Assert-Condition ($null -ne $pointerReady) "$Label vision.lookAhead.pointerReady is unavailable."

  if ($pointerReady -eq $true) {
    return
  }

  $pointer = Get-ObjectPropertyValue -InputObject $lookAhead -Name "pointer"
  $rawPointer = Get-ObjectPropertyValue -InputObject $lookAhead -Name "rawPointer"
  $screenCenter = Get-ObjectPropertyValue -InputObject $lookAhead -Name "screenCenter"
  Assert-Condition ($null -ne $pointer) "$Label vision.lookAhead.pointer is unavailable."
  Assert-Condition ($null -ne $rawPointer) "$Label vision.lookAhead.rawPointer is unavailable."
  Assert-Condition ($null -ne $screenCenter) "$Label vision.lookAhead.screenCenter is unavailable."

  $pointerToCenterDistance = Get-Vec2Distance -Left $pointer -Right $screenCenter
  $actualOffsetDistance = Get-NullableDouble (Get-ObjectPropertyValue -InputObject $lookAhead -Name "actualOffsetDistance")
  $targetAheadDistance = Get-NullableDouble (Get-ObjectPropertyValue -InputObject $lookAhead -Name "targetAheadDistance")

  Assert-Condition (
    $null -ne $pointerToCenterDistance -and $pointerToCenterDistance -le 0.5
  ) "$Label unresolved pointer should use screen center before input; distance=$pointerToCenterDistance."
  Assert-Condition (
    $null -ne $actualOffsetDistance -and $actualOffsetDistance -le 0.5
  ) "$Label unresolved pointer should not create camera offset; actualOffsetDistance=$actualOffsetDistance."
  Assert-Condition (
    $null -ne $targetAheadDistance -and $targetAheadDistance -le 0.5
  ) "$Label unresolved pointer should not move camera target ahead of player; targetAheadDistance=$targetAheadDistance."
}

function Get-VisionScreenPxPerWorldUnit {
  param($VisionRead)

  if ($null -eq $VisionRead -or $VisionRead.available -ne $true) {
    return $null
  }

  $diagnostics = Get-ObjectPropertyValue -InputObject $VisionRead -Name "diagnostics"
  $camera = Get-ObjectPropertyValue -InputObject $diagnostics -Name "camera"
  $screenPxPerWorldUnit = Get-ObjectPropertyValue -InputObject $camera -Name "screenPxPerWorldUnit"
  $average = Get-ObjectPropertyValue -InputObject $screenPxPerWorldUnit -Name "average"
  if ($null -ne $average) {
    return [double]$average
  }

  $zoom = Get-ObjectPropertyValue -InputObject $camera -Name "zoom"
  if ($null -ne $zoom) {
    return [double]$zoom
  }

  return $null
}

function Get-LocalFeedbackMotionSamplesForInput {
  param(
    $BeforeLocalFeedback,
    $AfterLocalFeedback,
    $InputStartPageMs = $null,
    $InputEndPageMs = $null
  )

  if ($null -eq $AfterLocalFeedback -or $AfterLocalFeedback.available -ne $true) {
    return @()
  }

  $beforeMotionCount = Get-LocalFeedbackCount -ReadResult $BeforeLocalFeedback -ChannelName "motion"
  $channel = Get-LocalFeedbackChannel -Diagnostics $AfterLocalFeedback.diagnostics -ChannelName "motion"
  if ($null -eq $channel) {
    return @()
  }

  $inputStart = $null
  if ($null -ne $InputStartPageMs) {
    $inputStart = [double]$InputStartPageMs
  }
  $inputEnd = $null
  if ($null -ne $InputEndPageMs) {
    $inputEnd = [double]$InputEndPageMs
  }

  return @(
    @($channel.recentSamples) |
      Where-Object {
        $sequence = [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0)
        $atMs = [double](Get-ObjectPropertyValue -InputObject $_ -Name "atMs" -DefaultValue -1)
        $include = $sequence -gt $beforeMotionCount -and $atMs -ge 0
        if ($include -and $null -ne $inputStart -and $atMs -lt ($inputStart - 5)) {
          $include = $false
        }
        if ($include -and $null -ne $inputEnd -and $atMs -gt ($inputEnd + 80)) {
          $include = $false
        }
        $include
      } |
      Sort-Object -Property @{ Expression = { [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0) } }
  )
}

function New-LocalHeroScreenMotionMetric {
  param(
    $BeforeLocalFeedback,
    $AfterLocalFeedback,
    $ScreenPxPerWorldUnit,
    $InputStartPageMs = $null,
    $InputEndPageMs = $null
  )

  if ($null -eq $AfterLocalFeedback -or $AfterLocalFeedback.available -ne $true) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = "localFeedback diagnostics are not available"
      mode = "window.__slayDemoBattleDiagnostics.localFeedback.motion"
    }
  }
  if ($null -eq $ScreenPxPerWorldUnit) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = "screenPxPerWorldUnit is not available"
      mode = "vision.camera.screenPxPerWorldUnit"
    }
  }

  $samples = @(Get-LocalFeedbackMotionSamplesForInput `
    -BeforeLocalFeedback $BeforeLocalFeedback `
    -AfterLocalFeedback $AfterLocalFeedback `
    -InputStartPageMs $InputStartPageMs `
    -InputEndPageMs $InputEndPageMs)

  if ($samples.Count -eq 0) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = "no localFeedback motion samples were captured in the input window"
      mode = "window.__slayDemoBattleDiagnostics.localFeedback.motion"
      sampleCount = 0
    }
  }

  $scale = [double]$ScreenPxPerWorldUnit
  $totalWorldDistance = 0.0
  $maxWorldPerSecond = $null
  $firstWorldPerSecond = $null
  $previousAtMs = $null

  foreach ($sample in $samples) {
    $distance = [double](Get-ObjectPropertyValue -InputObject $sample -Name "distance" -DefaultValue 0)
    $totalWorldDistance += $distance

    $atMs = [double](Get-ObjectPropertyValue -InputObject $sample -Name "atMs" -DefaultValue -1)
    if ($null -ne $previousAtMs -and $atMs -gt $previousAtMs) {
      $segmentWorldPerSecond = $distance * 1000 / ($atMs - [double]$previousAtMs)
      if ($null -eq $firstWorldPerSecond) {
        $firstWorldPerSecond = $segmentWorldPerSecond
      }
      if ($null -eq $maxWorldPerSecond -or $segmentWorldPerSecond -gt $maxWorldPerSecond) {
        $maxWorldPerSecond = $segmentWorldPerSecond
      }
    }
    $previousAtMs = $atMs
  }

  $firstSample = $samples[0]
  $lastSample = $samples[$samples.Count - 1]
  $firstAtMs = [double](Get-ObjectPropertyValue -InputObject $firstSample -Name "atMs" -DefaultValue 0)
  $lastAtMs = [double](Get-ObjectPropertyValue -InputObject $lastSample -Name "atMs" -DefaultValue $firstAtMs)
  $sampleWindowDurationMs = [Math]::Max(0, $lastAtMs - $firstAtMs)
  $inputWindowDurationMs = $null
  if ($null -ne $InputStartPageMs -and $null -ne $InputEndPageMs) {
    $inputWindowDurationMs = [Math]::Max(0, [double]$InputEndPageMs - [double]$InputStartPageMs)
  }
  $averageDurationMs = if ($null -ne $inputWindowDurationMs -and $inputWindowDurationMs -gt 0) { $inputWindowDurationMs } else { $sampleWindowDurationMs }
  $averageWorldPerSecond = $null
  if ($averageDurationMs -gt 0) {
    $averageWorldPerSecond = $totalWorldDistance * 1000 / $averageDurationMs
  }

  $firstFrom = Get-ObjectPropertyValue -InputObject $firstSample -Name "from"
  $lastTo = Get-ObjectPropertyValue -InputObject $lastSample -Name "to"
  $directWorldDisplacement = Get-Vec2Distance -Left $firstFrom -Right $lastTo

  return [ordered]@{
    available = $true
    status = "available"
    mode = "localFeedback.motion distance * vision.camera.screenPxPerWorldUnit"
    sampleCount = $samples.Count
    firstAtMs = Round-MetricNumber -Value $firstAtMs
    lastAtMs = Round-MetricNumber -Value $lastAtMs
    sampleWindowDurationMs = Round-MetricNumber -Value $sampleWindowDurationMs
    inputWindowDurationMs = Round-MetricNumber -Value $inputWindowDurationMs
    screenPxPerWorldUnit = Round-MetricNumber -Value $scale
    totalWorldDistance = Round-MetricNumber -Value $totalWorldDistance
    totalScreenDistancePx = Round-MetricNumber -Value ($totalWorldDistance * $scale)
    directWorldDisplacement = Round-MetricNumber -Value $directWorldDisplacement
    directScreenDisplacementPx = if ($null -ne $directWorldDisplacement) { Round-MetricNumber -Value ($directWorldDisplacement * $scale) } else { $null }
    firstMeasuredWorldUnitsPerSecond = Round-MetricNumber -Value $firstWorldPerSecond
    averageWorldUnitsPerSecond = Round-MetricNumber -Value $averageWorldPerSecond
    maxWorldUnitsPerSecond = Round-MetricNumber -Value $maxWorldPerSecond
    firstMeasuredScreenPxPerSecond = if ($null -ne $firstWorldPerSecond) { Round-MetricNumber -Value ($firstWorldPerSecond * $scale) } else { $null }
    averageScreenPxPerSecond = if ($null -ne $averageWorldPerSecond) { Round-MetricNumber -Value ($averageWorldPerSecond * $scale) } else { $null }
    maxScreenPxPerSecond = if ($null -ne $maxWorldPerSecond) { Round-MetricNumber -Value ($maxWorldPerSecond * $scale) } else { $null }
  }
}

function New-VisionClientMetric {
  param(
    $BeforeVision,
    $AfterVision,
    $BeforeLocalFeedback,
    $AfterLocalFeedback,
    $InputStartPageMs = $null,
    $InputEndPageMs = $null
  )

  $gaps = New-Object System.Collections.Generic.List[string]
  if ($null -eq $AfterVision -or $AfterVision.available -ne $true) {
    $reason = "vision diagnostics were not read"
    if ($null -ne $AfterVision -and -not [string]::IsNullOrWhiteSpace($AfterVision.reason)) {
      $reason = $AfterVision.reason
    }
    $gaps.Add($reason) | Out-Null
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = $reason
      mode = "window.__slayDemoBattleDiagnostics.vision"
      before = $BeforeVision
      after = $AfterVision
      gaps = @($gaps)
    }
  }

  $diagnostics = Get-ObjectPropertyValue -InputObject $AfterVision -Name "diagnostics"
  $camera = Get-ObjectPropertyValue -InputObject $diagnostics -Name "camera"
  $viewport = Get-ObjectPropertyValue -InputObject $diagnostics -Name "viewport"
  $lookAhead = Get-ObjectPropertyValue -InputObject $diagnostics -Name "lookAhead"
  $screenPxPerWorldUnit = Get-VisionScreenPxPerWorldUnit -VisionRead $AfterVision
  if ($null -eq $camera) {
    $gaps.Add("vision.camera is not available") | Out-Null
  }
  if ($null -eq $lookAhead) {
    $gaps.Add("vision.lookAhead is not available") | Out-Null
  }

  $localHeroScreenMotion = New-LocalHeroScreenMotionMetric `
    -BeforeLocalFeedback $BeforeLocalFeedback `
    -AfterLocalFeedback $AfterLocalFeedback `
    -ScreenPxPerWorldUnit $screenPxPerWorldUnit `
    -InputStartPageMs $InputStartPageMs `
    -InputEndPageMs $InputEndPageMs
  if ($localHeroScreenMotion.available -ne $true) {
    $gaps.Add("localHeroScreenMotion unavailable: $($localHeroScreenMotion.reason)") | Out-Null
  }

  return [ordered]@{
    available = $true
    status = "available"
    mode = "window.__slayDemoBattleDiagnostics.vision"
    camera = $camera
    viewport = $viewport
    lookAhead = $lookAhead
    localHeroScreenMotion = $localHeroScreenMotion
    before = [ordered]@{
      available = Get-ObjectPropertyValue -InputObject $BeforeVision -Name "available"
      phase = Get-ObjectPropertyValue -InputObject $BeforeVision -Name "phase"
      pageNowMs = Get-ObjectPropertyValue -InputObject $BeforeVision -Name "pageNowMs"
      wallMs = Get-ObjectPropertyValue -InputObject $BeforeVision -Name "wallMs"
    }
    after = [ordered]@{
      available = Get-ObjectPropertyValue -InputObject $AfterVision -Name "available"
      phase = Get-ObjectPropertyValue -InputObject $AfterVision -Name "phase"
      pageNowMs = Get-ObjectPropertyValue -InputObject $AfterVision -Name "pageNowMs"
      wallMs = Get-ObjectPropertyValue -InputObject $AfterVision -Name "wallMs"
    }
    gaps = @($gaps)
  }
}

function Wait-LocalFeedbackLatencyMetric {
  param(
    [Parameter(Mandatory = $true)]$Client,
    $Before,
    $InputStartPageMs = $null,
    $InputEndPageMs = $null,
    [Nullable[long]]$InputStartWallMs = $null,
    [Nullable[long]]$InputEndWallMs = $null,
    [string]$LatencyBasis = "inputDispatchStartPageMs",
    $InputDispatchStartPageMs = $null,
    [Nullable[long]]$InputDispatchStartWallMs = $null,
    $FirstInputEventPageMs = $null,
    $FirstInputEventType = $null,
    $FirstInputEventKeyOrButton = $null,
    [Nullable[long]]$FirstInputEventWallMs = $null,
    $FirstMovementInputEventPageMs = $null,
    $FirstMovementInputEventType = $null,
    $FirstMovementInputEventKeyOrButton = $null,
    [Nullable[long]]$FirstMovementInputEventWallMs = $null,
    $FirstFireInputEventPageMs = $null,
    $FirstFireInputEventType = $null,
    $FirstFireInputEventKeyOrButton = $null,
    [Nullable[long]]$FirstFireInputEventWallMs = $null,
    $MotionInputStartPageMs = $null,
    [Nullable[long]]$MotionInputStartWallMs = $null,
    [string]$MotionLatencyBasis = $null,
    $MuzzleInputStartPageMs = $null,
    [Nullable[long]]$MuzzleInputStartWallMs = $null,
    [string]$MuzzleLatencyBasis = $null,
    $DispatchToEventOverheadMs = $null,
    [int]$TimeoutMs = 1200,
    [int]$PollIntervalMs = 75
  )

  $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($TimeoutMs)
  $lastMetric = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $current = Read-LocalFeedbackDiagnostics -Client $Client -Phase "postInputFallback"
    $lastMetric = New-LocalFeedbackLatencyMetric `
      -Before $Before `
      -After $current `
      -InputStartPageMs $InputStartPageMs `
      -InputEndPageMs $InputEndPageMs `
      -InputStartWallMs $InputStartWallMs `
      -InputEndWallMs $InputEndWallMs `
      -LatencyBasis $LatencyBasis `
      -InputDispatchStartPageMs $InputDispatchStartPageMs `
      -InputDispatchStartWallMs $InputDispatchStartWallMs `
      -FirstInputEventPageMs $FirstInputEventPageMs `
      -FirstInputEventType $FirstInputEventType `
      -FirstInputEventKeyOrButton $FirstInputEventKeyOrButton `
      -FirstInputEventWallMs $FirstInputEventWallMs `
      -FirstMovementInputEventPageMs $FirstMovementInputEventPageMs `
      -FirstMovementInputEventType $FirstMovementInputEventType `
      -FirstMovementInputEventKeyOrButton $FirstMovementInputEventKeyOrButton `
      -FirstMovementInputEventWallMs $FirstMovementInputEventWallMs `
      -FirstFireInputEventPageMs $FirstFireInputEventPageMs `
      -FirstFireInputEventType $FirstFireInputEventType `
      -FirstFireInputEventKeyOrButton $FirstFireInputEventKeyOrButton `
      -FirstFireInputEventWallMs $FirstFireInputEventWallMs `
      -MotionInputStartPageMs $MotionInputStartPageMs `
      -MotionInputStartWallMs $MotionInputStartWallMs `
      -MotionLatencyBasis $MotionLatencyBasis `
      -MuzzleInputStartPageMs $MuzzleInputStartPageMs `
      -MuzzleInputStartWallMs $MuzzleInputStartWallMs `
      -MuzzleLatencyBasis $MuzzleLatencyBasis `
      -DispatchToEventOverheadMs $DispatchToEventOverheadMs
    if (Test-LocalFeedbackMetricComplete -Metric $lastMetric) {
      return $lastMetric
    }

    Start-Sleep -Milliseconds $PollIntervalMs
  }

  return $lastMetric
}

function Read-RemoteViewDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings = $null
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.remoteView : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.remoteView is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      if ($null -ne $Warnings) {
        $Warnings.Add("remoteViewMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      }
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    $reason = $_.Exception.Message
    if ($null -ne $Warnings) {
      $Warnings.Add("remoteViewMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    }
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function Find-RemoteHeroDiagnostics {
  param(
    $Diagnostics,
    [string]$HeroId = "",
    [string]$DisplayName = ""
  )

  if ($null -eq $Diagnostics) {
    return $null
  }

  $heroes = Get-ObjectPropertyValue -InputObject $Diagnostics -Name "heroes"
  if ($null -eq $heroes) {
    return $null
  }

  if (-not [string]::IsNullOrWhiteSpace($HeroId)) {
    $heroProperty = $heroes.PSObject.Properties[$HeroId]
    if ($null -ne $heroProperty) {
      return $heroProperty.Value
    }
  }

  $normalizedDisplayName = ""
  if (-not [string]::IsNullOrWhiteSpace($DisplayName)) {
    $normalizedDisplayName = $DisplayName.Trim().ToLowerInvariant()
  }

  foreach ($property in @($heroes.PSObject.Properties)) {
    $hero = $property.Value
    if ($null -eq $hero) {
      continue
    }

    if (-not [string]::IsNullOrWhiteSpace($HeroId) -and ("" + $hero.heroId) -ceq $HeroId) {
      return $hero
    }

    if (
      -not [string]::IsNullOrWhiteSpace($normalizedDisplayName) -and
      ("" + $hero.displayName).Trim().ToLowerInvariant() -eq $normalizedDisplayName
    ) {
      return $hero
    }
  }

  return $null
}

function Get-RemoteHeroSampleCount {
  param(
    $ReadResult,
    [string]$HeroId = "",
    [string]$DisplayName = ""
  )

  if ($null -eq $ReadResult -or $ReadResult.available -ne $true) {
    return 0
  }

  $hero = Find-RemoteHeroDiagnostics -Diagnostics $ReadResult.diagnostics -HeroId $HeroId -DisplayName $DisplayName
  if ($null -eq $hero) {
    return 0
  }

  return [int](Get-ObjectPropertyValue -InputObject $hero -Name "sampleCount" -DefaultValue 0)
}

function Get-RemoteProjectileBirthCount {
  param($ReadResult)

  if ($null -eq $ReadResult -or $ReadResult.available -ne $true) {
    return 0
  }

  $projectileBirths = Get-ObjectPropertyValue -InputObject $ReadResult.diagnostics -Name "projectileBirths"
  if ($null -eq $projectileBirths) {
    return 0
  }

  return [int](Get-ObjectPropertyValue -InputObject $projectileBirths -Name "count" -DefaultValue 0)
}

function New-RemoteHeroReadSummary {
  param($Hero)

  if ($null -eq $Hero) {
    return $null
  }

  return [ordered]@{
    heroId = Get-ObjectPropertyValue -InputObject $Hero -Name "heroId"
    displayName = Get-ObjectPropertyValue -InputObject $Hero -Name "displayName"
    firstSeenAtMs = Get-ObjectPropertyValue -InputObject $Hero -Name "firstSeenAtMs"
    lastSeenAtMs = Get-ObjectPropertyValue -InputObject $Hero -Name "lastSeenAtMs"
    sampleCount = Get-ObjectPropertyValue -InputObject $Hero -Name "sampleCount" -DefaultValue 0
    displayPosition = Get-ObjectPropertyValue -InputObject $Hero -Name "displayPosition"
    targetPosition = Get-ObjectPropertyValue -InputObject $Hero -Name "targetPosition"
    facing = Get-ObjectPropertyValue -InputObject $Hero -Name "facing"
    targetFacing = Get-ObjectPropertyValue -InputObject $Hero -Name "targetFacing"
    displayToTargetDistance = Get-ObjectPropertyValue -InputObject $Hero -Name "displayToTargetDistance"
    motionDistanceDelta = Get-ObjectPropertyValue -InputObject $Hero -Name "motionDistanceDelta"
    targetMotionDistanceDelta = Get-ObjectPropertyValue -InputObject $Hero -Name "targetMotionDistanceDelta"
    displayToTargetDistanceSummary = Get-ObjectPropertyValue -InputObject $Hero -Name "displayToTargetDistanceSummary"
    displayMotionDeltaSummary = Get-ObjectPropertyValue -InputObject $Hero -Name "displayMotionDeltaSummary"
    targetMotionDeltaSummary = Get-ObjectPropertyValue -InputObject $Hero -Name "targetMotionDeltaSummary"
    totalDisplayMotionDistance = Get-ObjectPropertyValue -InputObject $Hero -Name "totalDisplayMotionDistance"
    totalTargetMotionDistance = Get-ObjectPropertyValue -InputObject $Hero -Name "totalTargetMotionDistance"
    lastSample = Get-ObjectPropertyValue -InputObject $Hero -Name "lastSample"
  }
}

function New-RemoteProjectileBirthReadSummary {
  param($ProjectileBirths)

  if ($null -eq $ProjectileBirths) {
    return $null
  }

  return [ordered]@{
    count = Get-ObjectPropertyValue -InputObject $ProjectileBirths -Name "count" -DefaultValue 0
    firstAtMs = Get-ObjectPropertyValue -InputObject $ProjectileBirths -Name "firstAtMs"
    lastAtMs = Get-ObjectPropertyValue -InputObject $ProjectileBirths -Name "lastAtMs"
    sampleCount = Get-ObjectPropertyValue -InputObject $ProjectileBirths -Name "sampleCount" -DefaultValue 0
    sampleWindowSize = Get-ObjectPropertyValue -InputObject $ProjectileBirths -Name "sampleWindowSize"
    lastSample = Get-ObjectPropertyValue -InputObject $ProjectileBirths -Name "lastSample"
  }
}

function New-RemoteViewReadSummary {
  param(
    $ReadResult,
    [string]$RemoteHeroId = "",
    [string]$RemoteHeroDisplayName = ""
  )

  if ($null -eq $ReadResult) {
    return $null
  }

  $diagnostics = $null
  if ($ReadResult.available -eq $true) {
    $diagnostics = $ReadResult.diagnostics
  }
  $hero = Find-RemoteHeroDiagnostics -Diagnostics $diagnostics -HeroId $RemoteHeroId -DisplayName $RemoteHeroDisplayName
  $projectileBirths = Get-ObjectPropertyValue -InputObject $diagnostics -Name "projectileBirths"

  return [ordered]@{
    available = $ReadResult.available
    status = $ReadResult.status
    phase = $ReadResult.phase
    reason = Get-ObjectPropertyValue -InputObject $ReadResult -Name "reason"
    pageNowMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "pageNowMs"
    timeOriginMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "timeOriginMs"
    wallMs = Get-ObjectPropertyValue -InputObject $ReadResult -Name "wallMs"
    heroCount = Get-ObjectPropertyValue -InputObject $diagnostics -Name "heroCount" -DefaultValue 0
    heroIds = Get-ObjectPropertyValue -InputObject $diagnostics -Name "heroIds"
    remoteHero = New-RemoteHeroReadSummary -Hero $hero
    projectileBirths = New-RemoteProjectileBirthReadSummary -ProjectileBirths $projectileBirths
  }
}

function Find-StateHeroByHeroId {
  param(
    $State,
    [string]$HeroId
  )

  if ($null -eq $State -or [string]::IsNullOrWhiteSpace($HeroId)) {
    return $null
  }

  $players = @((Get-ObjectPropertyValue -InputObject $State -Name "players") | Where-Object { $null -ne $_ })
  $matches = @(
    $players |
      Where-Object { ("" + (Get-ObjectPropertyValue -InputObject $_ -Name "heroId")) -ceq $HeroId } |
      Select-Object -First 1
  )
  if ($matches.Count -gt 0) {
    return $matches[0]
  }

  return $null
}

function Get-StateHeroHp {
  param($Hero)

  if ($null -eq $Hero) {
    return $null
  }

  foreach ($name in @("hp", "health", "currentHp", "currentHealth")) {
    $value = Get-ObjectPropertyValue -InputObject $Hero -Name $name
    if (Test-FiniteNumber -Value $value) {
      return [double]$value
    }
  }

  return $null
}

function Get-StateHeroAlive {
  param($Hero)

  if ($null -eq $Hero) {
    return $null
  }

  foreach ($name in @("alive", "isAlive")) {
    $value = Get-ObjectPropertyValue -InputObject $Hero -Name $name
    if ($null -ne $value) {
      return [bool]$value
    }
  }

  $hp = Get-StateHeroHp -Hero $Hero
  if ($null -ne $hp) {
    return ($hp -gt 0)
  }

  return $null
}

function Test-StateHasProjectileTerminalsField {
  param($State)

  if ($null -eq $State) {
    return $false
  }

  return ($null -ne $State.PSObject.Properties["projectileTerminals"])
}

function Get-StateProjectileTerminals {
  param($State)

  if (-not (Test-StateHasProjectileTerminalsField -State $State)) {
    return @()
  }

  return @((Get-ObjectPropertyValue -InputObject $State -Name "projectileTerminals") | Where-Object { $null -ne $_ })
}

function New-ProjectileTerminalReasonSummary {
  param($Terminals)

  $summary = [ordered]@{
    hit = 0
    obstacle = 0
    world = 0
    ttl = 0
    other = 0
  }

  foreach ($terminal in @($Terminals | Where-Object { $null -ne $_ })) {
    $reason = ("" + (Get-ObjectPropertyValue -InputObject $terminal -Name "reason" -DefaultValue "")).Trim().ToLowerInvariant()
    switch ($reason) {
      "hit" { $summary["hit"] = [int]$summary["hit"] + 1 }
      "obstacle" { $summary["obstacle"] = [int]$summary["obstacle"] + 1 }
      "world" { $summary["world"] = [int]$summary["world"] + 1 }
      "ttl" { $summary["ttl"] = [int]$summary["ttl"] + 1 }
      default { $summary["other"] = [int]$summary["other"] + 1 }
    }
  }

  return $summary
}

function New-ProjectileTerminalSourceSummary {
  param($Terminals)

  $summary = [ordered]@{
    server = 0
    "snapshot-diff" = 0
    other = 0
  }

  foreach ($terminal in @($Terminals | Where-Object { $null -ne $_ })) {
    $source = ("" + (Get-ObjectPropertyValue -InputObject $terminal -Name "source" -DefaultValue "")).Trim().ToLowerInvariant()
    switch ($source) {
      "server" { $summary["server"] = [int]$summary["server"] + 1 }
      "snapshot-diff" { $summary["snapshot-diff"] = [int]$summary["snapshot-diff"] + 1 }
      default { $summary["other"] = [int]$summary["other"] + 1 }
    }
  }

  return $summary
}

function New-HitDisputeClientFields {
  param($ClientTerminal)

  if ($null -eq $ClientTerminal) {
    return [ordered]@{
      clientSource = $null
      diagnosticSource = $null
      clientReason = $null
      clientTerminalPosition = $null
      clientTargetPlayerId = $null
      clientTargetHeroId = $null
      clientHpBefore = $null
      clientHpAfter = $null
      clientDamage = $null
    }
  }

  return [ordered]@{
    clientSource = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "source"
    diagnosticSource = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "source"
    clientReason = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "reason"
    clientTerminalPosition = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "terminalPosition"
    clientTargetPlayerId = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "targetPlayerId"
    clientTargetHeroId = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "targetHeroId"
    clientHpBefore = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "hpBefore"
    clientHpAfter = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "hpAfter"
    clientDamage = Get-ObjectPropertyValue -InputObject $ClientTerminal -Name "damage"
  }
}

function New-HitDisputeServerFields {
  param($ServerTerminal)

  if ($null -eq $ServerTerminal) {
    return [ordered]@{
      serverReason = $null
      serverStart = $null
      serverEnd = $null
      serverTerminalPosition = $null
      serverTtlBefore = $null
      serverTtlAfter = $null
      serverElapsedMs = $null
      serverOwnerPlayerId = $null
      serverOwnerHeroId = $null
      serverTargetPlayerId = $null
      serverTargetHeroId = $null
      serverHpBefore = $null
      serverHpAfter = $null
      serverDamage = $null
    }
  }

  return [ordered]@{
    serverReason = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "reason"
    serverStart = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "start"
    serverEnd = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "end"
    serverTerminalPosition = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "terminalPosition"
    serverTtlBefore = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "ttlBefore"
    serverTtlAfter = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "ttlAfter"
    serverElapsedMs = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "elapsedMs"
    serverOwnerPlayerId = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "ownerPlayerId"
    serverOwnerHeroId = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "ownerHeroId"
    serverTargetPlayerId = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "targetPlayerId"
    serverTargetHeroId = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "targetHeroId"
    serverHpBefore = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "hpBefore"
    serverHpAfter = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "hpAfter"
    serverDamage = Get-ObjectPropertyValue -InputObject $ServerTerminal -Name "damage"
  }
}

function Get-NullableRoundedNumber {
  param(
    $Value,
    [int]$Digits = 3
  )

  if (-not (Test-FiniteNumber -Value $Value)) {
    return $null
  }

  return [Math]::Round([double]$Value, $Digits)
}

function New-HitDisputeSamples {
  param(
    $BeforeRemoteView,
    $AfterRemoteView,
    $BeforeState,
    $AfterState,
    [string]$BattleId,
    [string]$ObserverLabel,
    [string]$ObserverHandle,
    [string]$ObserverPlayerId,
    [Nullable[long]]$InputStartWallMs,
    [Nullable[long]]$InputEndWallMs,
    [AllowEmptyCollection()][object[]]$RelevantOwnerPlayerIds = @()
  )

  $nearEdgeDistanceThreshold = 24.0
  $serverSource = $null
  $serverFieldAvailable = $false
  $serverTerminals = @()
  foreach ($candidate in @(
      [ordered]@{ source = "api.afterState.projectileTerminals"; state = $AfterState },
      [ordered]@{ source = "api.beforeState.projectileTerminals"; state = $BeforeState }
    )) {
    if (-not (Test-StateHasProjectileTerminalsField -State $candidate.state)) {
      continue
    }

    $serverSource = $candidate.source
    $serverFieldAvailable = $true
    $serverTerminals = @(Get-StateProjectileTerminals -State $candidate.state)
    break
  }

  $serverTerminalById = @{}
  $relevantOwnerLookup = @{}
  $relevantOwnerList = @()
  foreach ($ownerPlayerId in @($RelevantOwnerPlayerIds | Where-Object { $null -ne $_ })) {
    $normalizedOwnerPlayerId = ("" + $ownerPlayerId).Trim()
    if ([string]::IsNullOrWhiteSpace($normalizedOwnerPlayerId) -or $relevantOwnerLookup.ContainsKey($normalizedOwnerPlayerId)) {
      continue
    }

    $relevantOwnerLookup[$normalizedOwnerPlayerId] = $true
    $relevantOwnerList += $normalizedOwnerPlayerId
  }

  $relevantServerProjectileIds = @{}
  $relevantServerTerminalCount = 0
  foreach ($serverTerminal in @($serverTerminals | Where-Object { $null -ne $_ })) {
    $serverProjectileId = "" + (Get-ObjectPropertyValue -InputObject $serverTerminal -Name "projectileId" -DefaultValue "")
    if (-not [string]::IsNullOrWhiteSpace($serverProjectileId)) {
      $serverTerminalById[$serverProjectileId] = $serverTerminal
    }

    $serverOwnerPlayerId = "" + (Get-ObjectPropertyValue -InputObject $serverTerminal -Name "ownerPlayerId" -DefaultValue "")
    if (
      -not [string]::IsNullOrWhiteSpace($serverProjectileId) -and
      -not [string]::IsNullOrWhiteSpace($serverOwnerPlayerId) -and
      $relevantOwnerLookup.ContainsKey($serverOwnerPlayerId)
    ) {
      $relevantServerProjectileIds[$serverProjectileId] = $true
    }
    if (
      -not [string]::IsNullOrWhiteSpace($serverOwnerPlayerId) -and
      $relevantOwnerLookup.ContainsKey($serverOwnerPlayerId)
    ) {
      $relevantServerTerminalCount += 1
    }
  }

  $clientSource = $null
  $clientRead = $null
  $clientTerminals = $null
  foreach ($candidate in @(
      [ordered]@{ source = "remoteView.after.diagnostics.projectileTerminals"; read = $AfterRemoteView },
      [ordered]@{ source = "remoteView.before.diagnostics.projectileTerminals"; read = $BeforeRemoteView }
    )) {
    $candidateRead = $candidate.read
    if ($null -eq $candidateRead -or $candidateRead.available -ne $true) {
      continue
    }

    $candidateTerminals = Get-ObjectPropertyValue -InputObject $candidateRead.diagnostics -Name "projectileTerminals"
    if ($null -eq $candidateTerminals) {
      continue
    }

    $clientSource = $candidate.source
    $clientRead = $candidateRead
    $clientTerminals = $candidateTerminals
    break
  }

  $timeOriginMs = Get-ObjectPropertyValue -InputObject $clientRead -Name "timeOriginMs"
  $sampleWindowMs = Get-ObjectPropertyValue -InputObject $clientTerminals -Name "sampleWindowMs"
  if ($null -eq $sampleWindowMs) {
    $sampleWindowMs = Get-ObjectPropertyValue -InputObject $clientTerminals -Name "windowMs"
  }
  $recentSamples = @((Get-ObjectPropertyValue -InputObject $clientTerminals -Name "recentSamples") | Where-Object { $null -ne $_ })
  $sortedSamples = @(
    $recentSamples |
      Sort-Object -Property @{ Expression = { [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0) } }
  )

  $samples = @()
  $remoteSampleProjectileIds = @{}
  foreach ($sample in $sortedSamples) {
    $projectileId = "" + (Get-ObjectPropertyValue -InputObject $sample -Name "projectileId" -DefaultValue "")
    if (-not [string]::IsNullOrWhiteSpace($projectileId)) {
      $remoteSampleProjectileIds[$projectileId] = $true
    }
    $serverTerminal = $null
    if (-not [string]::IsNullOrWhiteSpace($projectileId) -and $serverTerminalById.ContainsKey($projectileId)) {
      $serverTerminal = $serverTerminalById[$projectileId]
    }

    $nearestHeroId = "" + (Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroId" -DefaultValue "")
    $beforeHero = Find-StateHeroByHeroId -State $BeforeState -HeroId $nearestHeroId
    $afterHero = Find-StateHeroByHeroId -State $AfterState -HeroId $nearestHeroId
    $beforeHp = Get-StateHeroHp -Hero $beforeHero
    $afterHp = Get-StateHeroHp -Hero $afterHero
    $hpDelta = $null
    if ($null -ne $beforeHp -and $null -ne $afterHp) {
      $hpDelta = [Math]::Round($afterHp - $beforeHp, 3)
    }
    $damageObserved = $null
    if ($null -ne $hpDelta) {
      $damageObserved = ($hpDelta -lt 0)
    }

    $displayEdgeDistance = Get-NullableRoundedNumber -Value (Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroDisplayEdgeDistance")
    $authoritativeEdgeDistance = Get-NullableRoundedNumber -Value (Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroAuthoritativeEdgeDistance")
    $nearButNoDamage = (
      (
        ($null -ne $displayEdgeDistance -and $displayEdgeDistance -le $nearEdgeDistanceThreshold) -or
        ($null -ne $authoritativeEdgeDistance -and $authoritativeEdgeDistance -le $nearEdgeDistanceThreshold)
      ) -and
      $damageObserved -ne $true
    )

    $atMs = Get-ObjectPropertyValue -InputObject $sample -Name "atMs"
    $sampleEntry = [ordered]@{
      sampleSource = "remoteView"
      sequence = Get-ObjectPropertyValue -InputObject $sample -Name "sequence"
      atMs = $atMs
      sampleWallMs = Get-NullableRoundedNumber -Value (Convert-RemoteViewPageMsToWallMs -PageMs $atMs -TimeOriginMs $timeOriginMs)
      projectileId = if ([string]::IsNullOrWhiteSpace($projectileId)) { $null } else { $projectileId }
      kind = Get-ObjectPropertyValue -InputObject $sample -Name "kind"
      displayPosition = Get-ObjectPropertyValue -InputObject $sample -Name "displayPosition"
      authoritativePosition = Get-ObjectPropertyValue -InputObject $sample -Name "authoritativePosition"
      displayToAuthoritativeDistance = Get-NullableRoundedNumber -Value (Get-ObjectPropertyValue -InputObject $sample -Name "displayToAuthoritativeDistance")
      ttlMs = Get-NullableRoundedNumber -Value (Get-ObjectPropertyValue -InputObject $sample -Name "ttlMs")
      maxLifetimeMs = Get-NullableRoundedNumber -Value (Get-ObjectPropertyValue -InputObject $sample -Name "maxLifetimeMs")
      nearestHeroId = if ([string]::IsNullOrWhiteSpace($nearestHeroId)) { $null } else { $nearestHeroId }
      nearestHeroDisplayName = Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroDisplayName"
      nearestHeroAuthoritativeEdgeDistance = $authoritativeEdgeDistance
      nearestHeroDisplayEdgeDistance = $displayEdgeDistance
      beforeHp = $beforeHp
      afterHp = $afterHp
      hpDelta = $hpDelta
      damageObserved = $damageObserved
      targetAliveBefore = Get-StateHeroAlive -Hero $beforeHero
      targetAliveAfter = Get-StateHeroAlive -Hero $afterHero
      targetPositionBefore = Get-ObjectPropertyValue -InputObject $beforeHero -Name "position"
      targetPositionAfter = Get-ObjectPropertyValue -InputObject $afterHero -Name "position"
      terminalNearButNoDamage = $nearButNoDamage
    }

    $clientFields = New-HitDisputeClientFields -ClientTerminal $sample
    foreach ($entry in $clientFields.GetEnumerator()) {
      $sampleEntry[$entry.Key] = $entry.Value
    }

    $serverFields = New-HitDisputeServerFields -ServerTerminal $serverTerminal
    foreach ($entry in $serverFields.GetEnumerator()) {
      $sampleEntry[$entry.Key] = $entry.Value
    }
    if (
      (Test-HitDisputeHitDamageObserved -Sample $sampleEntry -Side "client") -or
      (Test-HitDisputeHitDamageObserved -Sample $sampleEntry -Side "server")
    ) {
      $sampleEntry["terminalNearButNoDamage"] = $false
    }
    $samples += $sampleEntry
  }

  foreach ($serverTerminal in @($serverTerminals | Where-Object { $null -ne $_ })) {
    $projectileId = "" + (Get-ObjectPropertyValue -InputObject $serverTerminal -Name "projectileId" -DefaultValue "")
    if (-not [string]::IsNullOrWhiteSpace($projectileId) -and $remoteSampleProjectileIds.ContainsKey($projectileId)) {
      continue
    }

    $targetHeroId = "" + (Get-ObjectPropertyValue -InputObject $serverTerminal -Name "targetHeroId" -DefaultValue "")
    $beforeHero = Find-StateHeroByHeroId -State $BeforeState -HeroId $targetHeroId
    $afterHero = Find-StateHeroByHeroId -State $AfterState -HeroId $targetHeroId
    $beforeHp = Get-ObjectPropertyValue -InputObject $serverTerminal -Name "hpBefore"
    $afterHp = Get-ObjectPropertyValue -InputObject $serverTerminal -Name "hpAfter"
    $hpDelta = $null
    if ((Test-FiniteNumber -Value $beforeHp) -and (Test-FiniteNumber -Value $afterHp)) {
      $hpDelta = [Math]::Round([double]$afterHp - [double]$beforeHp, 3)
    }
    $damageObserved = $null
    if ($null -ne $hpDelta) {
      $damageObserved = ($hpDelta -lt 0)
    }

    $sampleEntry = [ordered]@{
      sampleSource = "serverState"
      sequence = $null
      atMs = $null
      sampleWallMs = $null
      projectileId = if ([string]::IsNullOrWhiteSpace($projectileId)) { $null } else { $projectileId }
      kind = Get-ObjectPropertyValue -InputObject $serverTerminal -Name "kind"
      displayPosition = $null
      authoritativePosition = $null
      displayToAuthoritativeDistance = $null
      ttlMs = Get-ObjectPropertyValue -InputObject $serverTerminal -Name "ttlAfter"
      maxLifetimeMs = $null
      nearestHeroId = if ([string]::IsNullOrWhiteSpace($targetHeroId)) { $null } else { $targetHeroId }
      nearestHeroDisplayName = $null
      nearestHeroAuthoritativeEdgeDistance = $null
      nearestHeroDisplayEdgeDistance = $null
      beforeHp = $beforeHp
      afterHp = $afterHp
      hpDelta = $hpDelta
      damageObserved = $damageObserved
      targetAliveBefore = Get-StateHeroAlive -Hero $beforeHero
      targetAliveAfter = Get-StateHeroAlive -Hero $afterHero
      targetPositionBefore = Get-ObjectPropertyValue -InputObject $beforeHero -Name "position"
      targetPositionAfter = Get-ObjectPropertyValue -InputObject $afterHero -Name "position"
      terminalNearButNoDamage = $false
    }

    $clientFields = New-HitDisputeClientFields -ClientTerminal $null
    foreach ($entry in $clientFields.GetEnumerator()) {
      $sampleEntry[$entry.Key] = $entry.Value
    }

    $serverFields = New-HitDisputeServerFields -ServerTerminal $serverTerminal
    foreach ($entry in $serverFields.GetEnumerator()) {
      $sampleEntry[$entry.Key] = $entry.Value
    }
    $samples += $sampleEntry
  }

  $status = "available"
  if (-not $serverFieldAvailable -and $null -eq $clientTerminals) {
    $status = "unavailable"
  } elseif ($samples.Count -eq 0) {
    $status = "noSamples"
  }
  $clientTerminalCount = Get-ObjectPropertyValue -InputObject $clientTerminals -Name "count"
  if ($null -eq $clientTerminalCount -and $null -ne $clientTerminals) {
    $clientTerminalCount = $recentSamples.Count
  }
  $terminalCount = $clientTerminalCount
  if ($serverFieldAvailable) {
    $terminalCount = $serverTerminals.Count
  }
  $relevantClientTerminalCount = 0
  foreach ($sample in @($recentSamples | Where-Object { $null -ne $_ })) {
    $projectileId = "" + (Get-ObjectPropertyValue -InputObject $sample -Name "projectileId" -DefaultValue "")
    if (-not [string]::IsNullOrWhiteSpace($projectileId) -and $relevantServerProjectileIds.ContainsKey($projectileId)) {
      $relevantClientTerminalCount += 1
    }
  }

  return [ordered]@{
    available = ($serverFieldAvailable -or $null -ne $clientTerminals)
    status = $status
    source = if ($serverFieldAvailable) { $serverSource } else { $clientSource }
    serverSource = $serverSource
    clientSource = $clientSource
    battleId = if ([string]::IsNullOrWhiteSpace($BattleId)) { $null } else { $BattleId }
    observer = [ordered]@{
      clientLabel = $ObserverLabel
      handle = $ObserverHandle
      playerId = if ([string]::IsNullOrWhiteSpace($ObserverPlayerId)) { $null } else { $ObserverPlayerId }
    }
    inputStartWallMs = $InputStartWallMs
    inputEndWallMs = $InputEndWallMs
    sampleWindowMs = $sampleWindowMs
    thresholds = [ordered]@{
      terminalNearEdgeDistancePx = $nearEdgeDistanceThreshold
      terminalNearButNoDamage = "nearestHeroDisplayEdgeDistance <= 24 or nearestHeroAuthoritativeEdgeDistance <= 24, and damageObserved != true"
    }
    terminalCount = $terminalCount
    serverTerminalCount = $serverTerminals.Count
    clientTerminalCount = $clientTerminalCount
    relevantOwnerPlayerIds = @($relevantOwnerList)
    relevantServerTerminalCount = $relevantServerTerminalCount
    relevantClientTerminalCount = $relevantClientTerminalCount
    serverReasonSummary = New-ProjectileTerminalReasonSummary -Terminals $serverTerminals
    clientReasonSummary = New-ProjectileTerminalReasonSummary -Terminals $recentSamples
    clientSourceSummary = New-ProjectileTerminalSourceSummary -Terminals $recentSamples
    sampleCount = $samples.Count
    samples = @($samples)
  }
}

function Test-FiniteNumber {
  param($Value)

  if ($null -eq $Value) {
    return $false
  }

  try {
    $number = [double]$Value
    return (-not [double]::IsNaN($number) -and -not [double]::IsInfinity($number))
  } catch {
    return $false
  }
}

function Get-HitDisputeProjectileLabel {
  param($Sample)

  $projectileId = "" + (Get-ObjectPropertyValue -InputObject $Sample -Name "projectileId" -DefaultValue "")
  if ([string]::IsNullOrWhiteSpace($projectileId)) {
    return "projectileId=<missing>"
  }

  return "projectileId=$projectileId"
}

function Add-HitDisputeHitFieldAssertions {
  param(
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Failures,
    $Sample,
    [Parameter(Mandatory = $true)][string]$Side
  )

  $projectileLabel = Get-HitDisputeProjectileLabel -Sample $Sample
  $reasonName = "${Side}Reason"
  $targetPlayerName = "${Side}TargetPlayerId"
  $targetHeroName = "${Side}TargetHeroId"
  $hpBeforeName = "${Side}HpBefore"
  $hpAfterName = "${Side}HpAfter"
  $damageName = "${Side}Damage"

  $targetPlayerId = "" + (Get-ObjectPropertyValue -InputObject $Sample -Name $targetPlayerName -DefaultValue "")
  $targetHeroId = "" + (Get-ObjectPropertyValue -InputObject $Sample -Name $targetHeroName -DefaultValue "")
  $hpBefore = Get-ObjectPropertyValue -InputObject $Sample -Name $hpBeforeName
  $hpAfter = Get-ObjectPropertyValue -InputObject $Sample -Name $hpAfterName
  $damage = Get-ObjectPropertyValue -InputObject $Sample -Name $damageName

  if ([string]::IsNullOrWhiteSpace($targetPlayerId)) {
    $Failures.Add("Hit dispute $projectileLabel has $reasonName=hit but missing $targetPlayerName.") | Out-Null
  }
  if ([string]::IsNullOrWhiteSpace($targetHeroId)) {
    $Failures.Add("Hit dispute $projectileLabel has $reasonName=hit but missing $targetHeroName.") | Out-Null
  }
  if (-not (Test-FiniteNumber -Value $hpBefore)) {
    $Failures.Add("Hit dispute $projectileLabel has $reasonName=hit but $hpBeforeName is not finite.") | Out-Null
  }
  if (-not (Test-FiniteNumber -Value $hpAfter)) {
    $Failures.Add("Hit dispute $projectileLabel has $reasonName=hit but $hpAfterName is not finite.") | Out-Null
  }
  if (-not (Test-FiniteNumber -Value $damage)) {
    $Failures.Add("Hit dispute $projectileLabel has $reasonName=hit but $damageName is not finite.") | Out-Null
  }
  if ((Test-FiniteNumber -Value $hpBefore) -and (Test-FiniteNumber -Value $hpAfter)) {
    if ([double]$hpAfter -ge [double]$hpBefore) {
      $Failures.Add("Hit dispute $projectileLabel has $reasonName=hit but $hpAfterName ($hpAfter) is not below $hpBeforeName ($hpBefore).") | Out-Null
    }
  }
}

function Test-HitDisputeReasonExplainsNoDamage {
  param($Reason)

  $normalizedReason = ("" + $Reason).Trim().ToLowerInvariant()
  return @("blocked", "obstacle", "world", "ttl") -contains $normalizedReason
}

function Test-HitDisputeHitDamageObserved {
  param(
    $Sample,
    [Parameter(Mandatory = $true)][string]$Side
  )

  $reasonName = "${Side}Reason"
  $targetHeroName = "${Side}TargetHeroId"
  $hpBeforeName = "${Side}HpBefore"
  $hpAfterName = "${Side}HpAfter"
  $damageName = "${Side}Damage"

  $reason = ("" + (Get-ObjectPropertyValue -InputObject $Sample -Name $reasonName -DefaultValue "")).Trim().ToLowerInvariant()
  if ($reason -ne "hit") {
    return $false
  }

  $targetHeroId = "" + (Get-ObjectPropertyValue -InputObject $Sample -Name $targetHeroName -DefaultValue "")
  $hpBefore = Get-ObjectPropertyValue -InputObject $Sample -Name $hpBeforeName
  $hpAfter = Get-ObjectPropertyValue -InputObject $Sample -Name $hpAfterName
  $damage = Get-ObjectPropertyValue -InputObject $Sample -Name $damageName
  return (
    -not [string]::IsNullOrWhiteSpace($targetHeroId) -and
    (Test-FiniteNumber -Value $hpBefore) -and
    (Test-FiniteNumber -Value $hpAfter) -and
    [double]$hpAfter -lt [double]$hpBefore -and
    (Test-FiniteNumber -Value $damage) -and
    [double]$damage -gt 0
  )
}

function New-HitDisputeAssertionFailures {
  param($HitDisputeSamples)

  $failures = [System.Collections.Generic.List[string]]::new()
  if ($null -eq $HitDisputeSamples) {
    return ,$failures
  }

  $available = (Get-ObjectPropertyValue -InputObject $HitDisputeSamples -Name "available") -eq $true
  if (-not $available) {
    return ,$failures
  }

  $samples = @(Get-ObjectPropertyValue -InputObject $HitDisputeSamples -Name "samples" -DefaultValue @())
  foreach ($sample in @($samples | Where-Object { $null -ne $_ })) {
    $projectileLabel = Get-HitDisputeProjectileLabel -Sample $sample
    $clientReason = ("" + (Get-ObjectPropertyValue -InputObject $sample -Name "clientReason" -DefaultValue "")).Trim().ToLowerInvariant()
    $serverReason = ("" + (Get-ObjectPropertyValue -InputObject $sample -Name "serverReason" -DefaultValue "")).Trim().ToLowerInvariant()
    if (
      (Get-ObjectPropertyValue -InputObject $sample -Name "terminalNearButNoDamage") -eq $true -and
      -not (Test-HitDisputeReasonExplainsNoDamage -Reason $clientReason) -and
      -not (Test-HitDisputeReasonExplainsNoDamage -Reason $serverReason) -and
      -not (Test-HitDisputeHitDamageObserved -Sample $sample -Side "client") -and
      -not (Test-HitDisputeHitDamageObserved -Sample $sample -Side "server")
    ) {
      $nearestHeroId = Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroId"
      $displayEdge = Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroDisplayEdgeDistance"
      $authoritativeEdge = Get-ObjectPropertyValue -InputObject $sample -Name "nearestHeroAuthoritativeEdgeDistance"
      $beforeHp = Get-ObjectPropertyValue -InputObject $sample -Name "beforeHp"
      $afterHp = Get-ObjectPropertyValue -InputObject $sample -Name "afterHp"
      $failures.Add("Hit dispute $projectileLabel is terminalNearButNoDamage=true nearestHeroId=$nearestHeroId displayEdge=$displayEdge authoritativeEdge=$authoritativeEdge beforeHp=$beforeHp afterHp=$afterHp.") | Out-Null
    }

    if ($clientReason -eq "hit") {
      Add-HitDisputeHitFieldAssertions -Failures $failures -Sample $sample -Side "client"
    }

    if ($serverReason -eq "hit") {
      Add-HitDisputeHitFieldAssertions -Failures $failures -Sample $sample -Side "server"
    }
  }

  $relevantServerTerminalCount = Get-ObjectPropertyValue -InputObject $HitDisputeSamples -Name "relevantServerTerminalCount"
  $relevantClientTerminalCount = Get-ObjectPropertyValue -InputObject $HitDisputeSamples -Name "relevantClientTerminalCount"
  if ((Test-FiniteNumber -Value $relevantServerTerminalCount) -and [double]$relevantServerTerminalCount -gt 0 -and (Test-FiniteNumber -Value $relevantClientTerminalCount)) {
    if ([double]$relevantClientTerminalCount -lt [double]$relevantServerTerminalCount) {
      $relevantOwnerPlayerIds = @(
        Get-ObjectPropertyValue -InputObject $HitDisputeSamples -Name "relevantOwnerPlayerIds" -DefaultValue @()
      )
      $failures.Add("Hit dispute relevantClientTerminalCount ($relevantClientTerminalCount) is below relevantServerTerminalCount ($relevantServerTerminalCount) for relevantOwnerPlayerIds=$($relevantOwnerPlayerIds -join ','); human/input projectile terminals may not be surfaced in client diagnostics.") | Out-Null
    }
  }

  return ,$failures
}

function Convert-RemoteViewPageMsToWallMs {
  param(
    $PageMs,
    $TimeOriginMs
  )

  if ($null -eq $PageMs -or $null -eq $TimeOriginMs) {
    return $null
  }

  $page = [double]$PageMs
  $origin = [double]$TimeOriginMs
  if (-not (Test-FiniteNumber -Value $page) -or -not (Test-FiniteNumber -Value $origin)) {
    return $null
  }

  return $origin + $page
}

function Get-RemoteViewLatencyMs {
  param(
    $Sample,
    $TimeOriginMs,
    [Nullable[long]]$InputStartWallMs
  )

  if ($null -eq $Sample -or $null -eq $InputStartWallMs) {
    return $null
  }

  $atMs = Get-ObjectPropertyValue -InputObject $Sample -Name "atMs"
  $sampleWallMs = Convert-RemoteViewPageMsToWallMs -PageMs $atMs -TimeOriginMs $TimeOriginMs
  if ($null -eq $sampleWallMs) {
    return $null
  }

  return [Math]::Round([Math]::Max(0, $sampleWallMs - [double]$InputStartWallMs), 3)
}

function Test-RemoteViewSampleDuringInput {
  param(
    $Sample,
    $TimeOriginMs,
    [Nullable[long]]$InputStartWallMs,
    [Nullable[long]]$InputEndWallMs
  )

  if ($null -eq $Sample -or $null -eq $InputStartWallMs -or $null -eq $InputEndWallMs) {
    return $false
  }

  $atMs = Get-ObjectPropertyValue -InputObject $Sample -Name "atMs"
  $sampleWallMs = Convert-RemoteViewPageMsToWallMs -PageMs $atMs -TimeOriginMs $TimeOriginMs
  if ($null -eq $sampleWallMs) {
    return $false
  }

  return ($sampleWallMs -ge ([double]$InputStartWallMs - 5) -and $sampleWallMs -le ([double]$InputEndWallMs + 5))
}

function Test-RemoteProjectileBirthOwnerMatch {
  param(
    $Sample,
    [string]$RemoteHeroId = "",
    [string]$RemoteHeroDisplayName = ""
  )

  if ($null -eq $Sample) {
    return $false
  }

  if (-not [string]::IsNullOrWhiteSpace($RemoteHeroId)) {
    return ("" + (Get-ObjectPropertyValue -InputObject $Sample -Name "ownerHeroId")) -ceq $RemoteHeroId
  }

  if (-not [string]::IsNullOrWhiteSpace($RemoteHeroDisplayName)) {
    return ("" + (Get-ObjectPropertyValue -InputObject $Sample -Name "ownerDisplayName")).Trim().ToLowerInvariant() -eq $RemoteHeroDisplayName.Trim().ToLowerInvariant()
  }

  return $true
}

function Get-RemoteHeroPostInputSamples {
  param(
    $Hero,
    $BeforeHero,
    $TimeOriginMs,
    [Nullable[long]]$InputStartWallMs
  )

  if ($null -eq $Hero) {
    return @()
  }

  $samples = @((Get-ObjectPropertyValue -InputObject $Hero -Name "recentSamples") | Where-Object { $null -ne $_ })
  if ($samples.Count -eq 0) {
    return @()
  }

  $inputStartPageMs = $null
  if ($null -ne $InputStartWallMs -and $null -ne $TimeOriginMs) {
    $inputStartPageMs = [double]$InputStartWallMs - [double]$TimeOriginMs
  }

  $beforeLastSeenAtMs = $null
  if ($null -ne $BeforeHero) {
    $beforeLastSeenAtMs = Get-ObjectPropertyValue -InputObject $BeforeHero -Name "lastSeenAtMs"
  }

  $eligible = @(
    $samples |
      Where-Object {
        $atMs = [double](Get-ObjectPropertyValue -InputObject $_ -Name "atMs" -DefaultValue -1)
        $include = $true
        if ($atMs -lt 0) {
          $include = $false
        } elseif ($null -ne $inputStartPageMs) {
          $include = $atMs -ge ([double]$inputStartPageMs - 5)
        } elseif ($null -ne $beforeLastSeenAtMs) {
          $include = $atMs -gt ([double]$beforeLastSeenAtMs + 0.001)
        }
        $include
      } |
      Sort-Object -Property @{ Expression = { [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0) } }
  )

  return $eligible
}

function Get-RemoteProjectileBirthPostInputSamples {
  param(
    $After,
    [int]$BeforeProjectileBirthCount,
    [string]$RemoteHeroId = "",
    [string]$RemoteHeroDisplayName = ""
  )

  if ($null -eq $After -or $After.available -ne $true) {
    return @()
  }

  $projectileBirths = Get-ObjectPropertyValue -InputObject $After.diagnostics -Name "projectileBirths"
  if ($null -eq $projectileBirths) {
    return @()
  }

  $samples = @((Get-ObjectPropertyValue -InputObject $projectileBirths -Name "recentSamples") | Where-Object { $null -ne $_ })
  $eligible = @(
    $samples |
      Where-Object {
        $sequence = [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0)
        $sequence -gt $BeforeProjectileBirthCount -and
          (Test-RemoteProjectileBirthOwnerMatch -Sample $_ -RemoteHeroId $RemoteHeroId -RemoteHeroDisplayName $RemoteHeroDisplayName)
      } |
      Sort-Object -Property @{ Expression = { [int](Get-ObjectPropertyValue -InputObject $_ -Name "sequence" -DefaultValue 0) } }
  )

  return $eligible
}

function Sum-RemoteHeroDisplayMotion {
  param($Samples)

  $total = 0.0
  foreach ($sample in @($Samples)) {
    $delta = [double](Get-ObjectPropertyValue -InputObject $sample -Name "displayMotionDelta" -DefaultValue 0)
    if ((Test-FiniteNumber -Value $delta) -and $delta -gt 0) {
      $total += $delta
    }
  }

  return [Math]::Round($total, 3)
}

function New-RemoteViewMetric {
  param(
    $Before,
    $After,
    [string]$RemoteHeroId = "",
    [string]$RemoteHeroDisplayName = "",
    [Nullable[long]]$InputStartWallMs = $null,
    [Nullable[long]]$InputEndWallMs = $null
  )

  if ($null -eq $After -or $After.available -ne $true) {
    $unavailableReason = "remote view diagnostics were not read"
    if ($null -ne $After) {
      $unavailableReason = $After.reason
    }
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = $unavailableReason
      mode = "window.__slayDemoBattleDiagnostics.remoteView"
      before = New-RemoteViewReadSummary -ReadResult $Before -RemoteHeroId $RemoteHeroId -RemoteHeroDisplayName $RemoteHeroDisplayName
      after = New-RemoteViewReadSummary -ReadResult $After -RemoteHeroId $RemoteHeroId -RemoteHeroDisplayName $RemoteHeroDisplayName
    }
  }

  $beforeDiagnostics = $null
  if ($null -ne $Before -and $Before.available -eq $true) {
    $beforeDiagnostics = $Before.diagnostics
  }
  $beforeHero = Find-RemoteHeroDiagnostics -Diagnostics $beforeDiagnostics -HeroId $RemoteHeroId -DisplayName $RemoteHeroDisplayName
  $afterHero = Find-RemoteHeroDiagnostics -Diagnostics $After.diagnostics -HeroId $RemoteHeroId -DisplayName $RemoteHeroDisplayName
  $beforeHeroSampleCount = Get-RemoteHeroSampleCount -ReadResult $Before -HeroId $RemoteHeroId -DisplayName $RemoteHeroDisplayName
  $afterHeroSampleCount = Get-RemoteHeroSampleCount -ReadResult $After -HeroId $RemoteHeroId -DisplayName $RemoteHeroDisplayName
  $timeOriginMs = Get-ObjectPropertyValue -InputObject $After -Name "timeOriginMs"
  $postHeroSamples = Get-RemoteHeroPostInputSamples `
    -Hero $afterHero `
    -BeforeHero $beforeHero `
    -TimeOriginMs $timeOriginMs `
    -InputStartWallMs $InputStartWallMs
  $firstHeroPostInputSample = $null
  if ($postHeroSamples.Count -gt 0) {
    $firstHeroPostInputSample = $postHeroSamples[0]
  }

  $beforeTotalDisplayMotion = 0.0
  if ($null -ne $beforeHero) {
    $beforeTotalDisplayMotion = [double](Get-ObjectPropertyValue -InputObject $beforeHero -Name "totalDisplayMotionDistance" -DefaultValue 0)
  }
  $afterTotalDisplayMotion = 0.0
  if ($null -ne $afterHero) {
    $afterTotalDisplayMotion = [double](Get-ObjectPropertyValue -InputObject $afterHero -Name "totalDisplayMotionDistance" -DefaultValue 0)
  }
  $remoteHeroMotionDelta = [Math]::Max(0, $afterTotalDisplayMotion - $beforeTotalDisplayMotion)
  if ($remoteHeroMotionDelta -le 0 -and $postHeroSamples.Count -gt 0) {
    $remoteHeroMotionDelta = Sum-RemoteHeroDisplayMotion -Samples $postHeroSamples
  } else {
    $remoteHeroMotionDelta = [Math]::Round($remoteHeroMotionDelta, 3)
  }

  $remoteHeroFirstSeenLatencyMs = $null
  if ($null -eq $beforeHero -and $null -ne $afterHero) {
    $remoteHeroFirstSeenLatencyMs = Get-RemoteViewLatencyMs `
      -Sample ([pscustomobject]@{ atMs = Get-ObjectPropertyValue -InputObject $afterHero -Name "firstSeenAtMs" }) `
      -TimeOriginMs $timeOriginMs `
      -InputStartWallMs $InputStartWallMs
  }
  $remoteHeroFirstPostInputSampleLatencyMs = Get-RemoteViewLatencyMs `
    -Sample $firstHeroPostInputSample `
    -TimeOriginMs $timeOriginMs `
    -InputStartWallMs $InputStartWallMs
  $remoteHeroCapturedDuringInput = Test-RemoteViewSampleDuringInput `
    -Sample $firstHeroPostInputSample `
    -TimeOriginMs $timeOriginMs `
    -InputStartWallMs $InputStartWallMs `
    -InputEndWallMs $InputEndWallMs

  $beforeProjectileBirthCount = Get-RemoteProjectileBirthCount -ReadResult $Before
  $afterProjectileBirthCount = Get-RemoteProjectileBirthCount -ReadResult $After
  $projectileBirthSamples = Get-RemoteProjectileBirthPostInputSamples `
    -After $After `
    -BeforeProjectileBirthCount $beforeProjectileBirthCount `
    -RemoteHeroId $RemoteHeroId `
    -RemoteHeroDisplayName $RemoteHeroDisplayName
  $firstProjectileBirthSample = $null
  if ($projectileBirthSamples.Count -gt 0) {
    $firstProjectileBirthSample = $projectileBirthSamples[0]
  }
  $remoteProjectileBirthLatencyMs = Get-RemoteViewLatencyMs `
    -Sample $firstProjectileBirthSample `
    -TimeOriginMs $timeOriginMs `
    -InputStartWallMs $InputStartWallMs
  $remoteProjectileBirthCapturedDuringInput = Test-RemoteViewSampleDuringInput `
    -Sample $firstProjectileBirthSample `
    -TimeOriginMs $timeOriginMs `
    -InputStartWallMs $InputStartWallMs `
    -InputEndWallMs $InputEndWallMs

  $heroProbeWindow = $null
  if ($null -ne $firstHeroPostInputSample) {
    $heroProbeWindow = if ($remoteHeroCapturedDuringInput) { "inputBurst" } else { "postInputFallback" }
  }
  $projectileProbeWindow = $null
  if ($null -ne $firstProjectileBirthSample) {
    $projectileProbeWindow = if ($remoteProjectileBirthCapturedDuringInput) { "inputBurst" } else { "postInputFallback" }
  }

  $latencyUnavailableReason = [ordered]@{
    remoteHeroFirstSeen = $null
    remoteHeroFirstPostInputSample = $null
    remoteProjectileBirth = $null
  }
  if ($null -eq $timeOriginMs) {
    $latencyUnavailableReason.remoteHeroFirstSeen = "clientB performance.timeOrigin was unavailable"
    $latencyUnavailableReason.remoteHeroFirstPostInputSample = "clientB performance.timeOrigin was unavailable"
    $latencyUnavailableReason.remoteProjectileBirth = "clientB performance.timeOrigin was unavailable"
  }
  if ($null -eq $remoteHeroFirstSeenLatencyMs -and $null -eq $beforeHero) {
    $latencyUnavailableReason.remoteHeroFirstSeen = "clientB did not expose a first-seen sample for the target remote hero after input start"
  }
  if ($null -eq $remoteHeroFirstPostInputSampleLatencyMs) {
    $latencyUnavailableReason.remoteHeroFirstPostInputSample = "clientB did not expose a post-input remote hero sample for the target remote hero"
  }
  if ($null -eq $remoteProjectileBirthLatencyMs) {
    $latencyUnavailableReason.remoteProjectileBirth = "clientB did not expose a post-input remote projectile birth sample for the target owner"
  }

  $projectileBirthMatchMode = "allRemoteOwners"
  if (-not [string]::IsNullOrWhiteSpace($RemoteHeroId)) {
    $projectileBirthMatchMode = "ownerHeroId"
  } elseif (-not [string]::IsNullOrWhiteSpace($RemoteHeroDisplayName)) {
    $projectileBirthMatchMode = "ownerDisplayName"
  }
  $remoteHeroQuality = [ordered]@{
    displayToTargetDistanceSummary = Get-ObjectPropertyValue -InputObject $afterHero -Name "displayToTargetDistanceSummary"
    displayMotionDeltaSummary = Get-ObjectPropertyValue -InputObject $afterHero -Name "displayMotionDeltaSummary"
    targetMotionDeltaSummary = Get-ObjectPropertyValue -InputObject $afterHero -Name "targetMotionDeltaSummary"
  }

  return [ordered]@{
    available = $true
    status = "available"
    complete = ($null -ne $afterHero -and $postHeroSamples.Count -gt 0 -and $projectileBirthSamples.Count -gt 0)
    mode = "window.__slayDemoBattleDiagnostics.remoteView"
    target = [ordered]@{
      remoteHeroId = $RemoteHeroId
      remoteHeroDisplayName = $RemoteHeroDisplayName
    }
    inputStartWallMs = $InputStartWallMs
    inputEndWallMs = $InputEndWallMs
    remoteHeroObserved = ($null -ne $afterHero)
    remoteHeroSampleDelta = $afterHeroSampleCount - $beforeHeroSampleCount
    remoteHeroMotionDelta = $remoteHeroMotionDelta
    remoteHeroQuality = $remoteHeroQuality
    remoteHeroFirstSeenLatencyMs = $remoteHeroFirstSeenLatencyMs
    remoteHeroFirstPostInputSampleLatencyMs = $remoteHeroFirstPostInputSampleLatencyMs
    remoteHeroCapturedDuringInput = $remoteHeroCapturedDuringInput
    remoteProjectileBirthDelta = $projectileBirthSamples.Count
    remoteProjectileBirthTotalDelta = $afterProjectileBirthCount - $beforeProjectileBirthCount
    remoteProjectileBirthLatencyMs = $remoteProjectileBirthLatencyMs
    remoteProjectileBirthCapturedDuringInput = $remoteProjectileBirthCapturedDuringInput
    capturedDuringInput = ($remoteHeroCapturedDuringInput -or $remoteProjectileBirthCapturedDuringInput)
    probeWindow = [ordered]@{
      remoteHero = $heroProbeWindow
      remoteProjectileBirth = $projectileProbeWindow
    }
    projectileBirthMatchMode = $projectileBirthMatchMode
    latencyUnavailableReason = $latencyUnavailableReason
    remoteHero = [ordered]@{
      firstPostInputSample = $firstHeroPostInputSample
      postInputSampleCount = $postHeroSamples.Count
      after = New-RemoteHeroReadSummary -Hero $afterHero
    }
    remoteProjectileBirth = [ordered]@{
      firstSample = $firstProjectileBirthSample
      postInputSampleCount = $projectileBirthSamples.Count
    }
    before = New-RemoteViewReadSummary -ReadResult $Before -RemoteHeroId $RemoteHeroId -RemoteHeroDisplayName $RemoteHeroDisplayName
    after = New-RemoteViewReadSummary -ReadResult $After -RemoteHeroId $RemoteHeroId -RemoteHeroDisplayName $RemoteHeroDisplayName
  }
}

function Test-RemoteViewMetricHasAnyInputSample {
  param($Metric)

  if ($null -eq $Metric -or $Metric.available -ne $true) {
    return $false
  }

  return (
    [int](Get-ObjectPropertyValue -InputObject $Metric -Name "remoteHeroSampleDelta" -DefaultValue 0) -gt 0 -or
    [double](Get-ObjectPropertyValue -InputObject $Metric -Name "remoteHeroMotionDelta" -DefaultValue 0) -gt 0.001 -or
    [int](Get-ObjectPropertyValue -InputObject $Metric -Name "remoteProjectileBirthDelta" -DefaultValue 0) -gt 0
  )
}

function Test-RemoteViewMetricComplete {
  param($Metric)

  return (
    $null -ne $Metric -and
    $Metric.available -eq $true -and
    $Metric.remoteHeroObserved -eq $true -and
    $Metric.remoteHeroSampleDelta -gt 0 -and
    $Metric.remoteProjectileBirthDelta -gt 0
  )
}

function Wait-RemoteViewMetric {
  param(
    [Parameter(Mandatory = $true)]$Client,
    $Before,
    [string]$RemoteHeroId = "",
    [string]$RemoteHeroDisplayName = "",
    [Nullable[long]]$InputStartWallMs = $null,
    [Nullable[long]]$InputEndWallMs = $null,
    [int]$TimeoutMs = 1500,
    [int]$PollIntervalMs = 75
  )

  $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($TimeoutMs)
  $lastMetric = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $current = Read-RemoteViewDiagnostics -Client $Client -Phase "postInputFallback"
    $lastMetric = New-RemoteViewMetric `
      -Before $Before `
      -After $current `
      -RemoteHeroId $RemoteHeroId `
      -RemoteHeroDisplayName $RemoteHeroDisplayName `
      -InputStartWallMs $InputStartWallMs `
      -InputEndWallMs $InputEndWallMs
    if (Test-RemoteViewMetricComplete -Metric $lastMetric) {
      return $lastMetric
    }

    Start-Sleep -Milliseconds $PollIntervalMs
  }

  return $lastMetric
}

function Resolve-BattleIdentity {
  param(
    $ContextA,
    $ContextB,
    [System.Collections.Generic.List[string]]$Warnings
  )

  $battleIdA = ""
  if ($null -ne $ContextA -and -not [string]::IsNullOrWhiteSpace($ContextA.battleId)) {
    $battleIdA = $ContextA.battleId.Trim()
  }
  $battleIdB = ""
  if ($null -ne $ContextB -and -not [string]::IsNullOrWhiteSpace($ContextB.battleId)) {
    $battleIdB = $ContextB.battleId.Trim()
  }

  if ($battleIdA -and $battleIdB) {
    if ($battleIdA -ne $battleIdB) {
      throw "Clients exposed different battleIds: clientA=$battleIdA clientB=$battleIdB"
    }

    return [pscustomobject]@{
      battleId = $battleIdA
      source = "localStorage.activeBattleSession"
      sameBattleProven = $true
    }
  }

  if ($battleIdA -or $battleIdB) {
    $value = $battleIdA
    if (-not $value) {
      $value = $battleIdB
    }
    $Warnings.Add("Only one client exposed battleId from page storage; continuing with $value and validating roster via API if possible.") | Out-Null
    return [pscustomobject]@{
      battleId = $value
      source = "partialLocalStorage"
      sameBattleProven = $false
    }
  }

  $Warnings.Add("battleId was unavailable from both pages, but both clients reached playing; continuing with basic frame/input metrics.") | Out-Null
  return [pscustomobject]@{
    battleId = $null
    source = "unavailable"
    sameBattleProven = $false
  }
}

function New-DisplayTruthMetric {
  param(
    $Context,
    $State,
    [Parameter(Mandatory = $true)][string]$Handle,
    [string]$PlayerId
  )

  if ($null -eq $Context -or $null -eq $State) {
    return "unavailable"
  }

  $contextHero = Find-ContextHero -Context $Context -Handle $Handle
  $statePlayer = Find-StatePlayer -State $State -Handle $Handle -PlayerId $PlayerId
  if (
    $null -eq $contextHero -or
    $null -eq $statePlayer -or
    $null -eq $contextHero.position -or
    $null -eq $statePlayer.position
  ) {
    return "unavailable"
  }

  $dx = [double]$contextHero.position.x - [double]$statePlayer.position.x
  $dy = [double]$contextHero.position.y - [double]$statePlayer.position.y
  return [pscustomobject]@{
    mode = "localStorageSnapshotVsAuthoritative"
    distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
    localStoragePosition = $contextHero.position
    authoritativePosition = $statePlayer.position
    pageShellClass = $Context.shellClass
  }
}

function New-VisibleDisplayTruthMetric {
  param(
    $Vision,
    $State,
    [Parameter(Mandatory = $true)][string]$Handle,
    [string]$PlayerId
  )

  if ($null -eq $Vision -or $null -eq $State) {
    return "unavailable"
  }

  $visionDiagnostics = Get-ObjectPropertyValue -InputObject $Vision -Name "diagnostics"
  $camera = Get-ObjectPropertyValue -InputObject $visionDiagnostics -Name "camera"
  if ($null -eq $camera) {
    $camera = Get-ObjectPropertyValue -InputObject $Vision -Name "camera"
  }
  $displayPosition = Get-ObjectPropertyValue -InputObject $camera -Name "playerDisplayPosition"
  $statePlayer = Find-StatePlayer -State $State -Handle $Handle -PlayerId $PlayerId
  if (
    $null -eq $displayPosition -or
    $null -eq $statePlayer -or
    $null -eq $statePlayer.position
  ) {
    return "unavailable"
  }

  $dx = [double]$displayPosition.x - [double]$statePlayer.position.x
  $dy = [double]$displayPosition.y - [double]$statePlayer.position.y
  return [pscustomobject]@{
    mode = "visibleDisplayPoseVsAuthoritative"
    distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
    visibleDisplayPosition = $displayPosition
    authoritativePosition = $statePlayer.position
    phase = Get-ObjectPropertyValue -InputObject $Vision -Name "phase"
    pageNowMs = Get-ObjectPropertyValue -InputObject $Vision -Name "pageNowMs"
    wallMs = Get-ObjectPropertyValue -InputObject $Vision -Name "wallMs"
  }
}

function Read-LocalHeroCorrectionDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings
  )

  $expression = @'
(() => {
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.localHeroCorrection : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.localHeroCorrection is not available"
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics))
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error)
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      $Warnings.Add("localHeroCorrectionMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
    }
  } catch {
    $reason = $_.Exception.Message
    $Warnings.Add("localHeroCorrectionMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function New-LocalHeroCorrectionMetric {
  param(
    $Before,
    $After
  )

  if ($null -eq $After -or $After.available -ne $true) {
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      before = $Before
      after = $After
    }
  }

  $beforeDiagnostics = $null
  if ($null -ne $Before -and $Before.available -eq $true) {
    $beforeDiagnostics = $Before.diagnostics
  }

  $afterDiagnostics = $After.diagnostics
  $beforeCorrectionCount = 0
  $beforeAppliedCorrectionCount = 0
  $beforeIgnoredCount = 0
  $beforeDeadzoneIgnoredCount = 0
  $beforeHardSnapCount = 0
  $beforeSoftCorrectionCount = 0
  if ($null -ne $beforeDiagnostics) {
    $beforeCorrectionCount = [int]$beforeDiagnostics.correctionCount
    if ($null -ne $beforeDiagnostics.PSObject.Properties["appliedCorrectionCount"]) {
      $beforeAppliedCorrectionCount = [int]$beforeDiagnostics.appliedCorrectionCount
    } else {
      $beforeAppliedCorrectionCount = $beforeCorrectionCount
    }
    if ($null -ne $beforeDiagnostics.PSObject.Properties["ignoredCount"]) {
      $beforeIgnoredCount = [int]$beforeDiagnostics.ignoredCount
    }
    if ($null -ne $beforeDiagnostics.PSObject.Properties["deadzoneIgnoredCount"]) {
      $beforeDeadzoneIgnoredCount = [int]$beforeDiagnostics.deadzoneIgnoredCount
    }
    $beforeHardSnapCount = [int]$beforeDiagnostics.hardSnapCount
    $beforeSoftCorrectionCount = [int]$beforeDiagnostics.softCorrectionCount
  }

  $afterAppliedCorrectionCount = [int]$afterDiagnostics.correctionCount
  if ($null -ne $afterDiagnostics.PSObject.Properties["appliedCorrectionCount"]) {
    $afterAppliedCorrectionCount = [int]$afterDiagnostics.appliedCorrectionCount
  }
  $afterIgnoredCount = 0
  if ($null -ne $afterDiagnostics.PSObject.Properties["ignoredCount"]) {
    $afterIgnoredCount = [int]$afterDiagnostics.ignoredCount
  }
  $afterDeadzoneIgnoredCount = 0
  if ($null -ne $afterDiagnostics.PSObject.Properties["deadzoneIgnoredCount"]) {
    $afterDeadzoneIgnoredCount = [int]$afterDiagnostics.deadzoneIgnoredCount
  }

  return [pscustomobject]@{
    available = $true
    status = "available"
    mode = "window.__slayDemoBattleDiagnostics.localHeroCorrection"
    correctionDelta = [int]$afterDiagnostics.correctionCount - $beforeCorrectionCount
    appliedCorrectionDelta = $afterAppliedCorrectionCount - $beforeAppliedCorrectionCount
    ignoredDelta = $afterIgnoredCount - $beforeIgnoredCount
    deadzoneIgnoredDelta = $afterDeadzoneIgnoredCount - $beforeDeadzoneIgnoredCount
    hardSnapDelta = [int]$afterDiagnostics.hardSnapCount - $beforeHardSnapCount
    softCorrectionDelta = [int]$afterDiagnostics.softCorrectionCount - $beforeSoftCorrectionCount
    before = $Before
    after = $After
  }
}

function Read-AuthoritativeLocalHeroReplayDiagnostics {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Phase,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Warnings
  )

  $expression = @'
(() => {
  const pageNowMs = typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : null;
  const timeOriginMs = typeof performance !== "undefined" && Number.isFinite(performance.timeOrigin)
    ? performance.timeOrigin
    : null;
  const root = window.__slayDemoBattleDiagnostics;
  const diagnostics = root && typeof root === "object" ? root.authoritativeLocalHeroReplay : null;
  if (!diagnostics || typeof diagnostics !== "object") {
    return {
      available: false,
      status: "unavailable",
      reason: "window.__slayDemoBattleDiagnostics.authoritativeLocalHeroReplay is not available",
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }

  try {
    return {
      available: true,
      status: "available",
      diagnostics: JSON.parse(JSON.stringify(diagnostics)),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  } catch (error) {
    return {
      available: false,
      status: "unavailable",
      reason: error instanceof Error ? error.message : String(error),
      pageNowMs,
      timeOriginMs,
      wallMs: Date.now()
    };
  }
})()
'@

  try {
    $result = Invoke-CdpEvaluate -Client $Client -Expression $expression -TimeoutSeconds 8
    if ($null -eq $result -or $result.available -ne $true) {
      $reason = "unknown"
      if ($null -ne $result -and -not [string]::IsNullOrWhiteSpace($result.reason)) {
        $reason = $result.reason
      }
      $Warnings.Add("authoritativeLocalHeroReplayMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
      return [pscustomobject]@{
        available = $false
        status = "unavailable"
        phase = $Phase
        reason = $reason
        pageNowMs = Get-ObjectPropertyValue -InputObject $result -Name "pageNowMs"
        timeOriginMs = Get-ObjectPropertyValue -InputObject $result -Name "timeOriginMs"
        wallMs = Get-ObjectPropertyValue -InputObject $result -Name "wallMs"
      }
    }

    return [pscustomobject]@{
      available = $true
      status = "available"
      phase = $Phase
      diagnostics = $result.diagnostics
      pageNowMs = $result.pageNowMs
      timeOriginMs = $result.timeOriginMs
      wallMs = $result.wallMs
    }
  } catch {
    $reason = $_.Exception.Message
    $Warnings.Add("authoritativeLocalHeroReplayMetric $($Client.Label) phase=$Phase unavailable: $reason") | Out-Null
    return [pscustomobject]@{
      available = $false
      status = "unavailable"
      phase = $Phase
      reason = $reason
    }
  }
}

function New-AuthoritativeLocalHeroReplaySampleSummary {
  param(
    $Diagnostics,
    $WindowStartPageMs = $null,
    $WindowEndPageMs = $null
  )

  if ($null -eq $Diagnostics) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      reason = "authoritativeLocalHeroReplay diagnostics are not available"
      sampleCount = 0
    }
  }

  $samples = @(
    @(Get-ObjectPropertyValue -InputObject $Diagnostics -Name "recentSamples" -DefaultValue @()) |
      Where-Object {
        $atMs = Get-ObjectPropertyValue -InputObject $_ -Name "atMs"
        if (-not (Test-FiniteNumber -Value $atMs)) {
          return $false
        }

        $include = $true
        if ((Test-FiniteNumber -Value $WindowStartPageMs) -and [double]$atMs -lt [double]$WindowStartPageMs) {
          $include = $false
        }
        if ($include -and (Test-FiniteNumber -Value $WindowEndPageMs) -and [double]$atMs -gt [double]$WindowEndPageMs) {
          $include = $false
        }
        $include
      }
  )

  $skippedCount = 0
  $replayedCount = 0
  $clampedSampleCount = 0
  $maxUnacknowledgedCommandCount = 0
  $maxClampedCommandCount = 0
  $maxTotalReplayDeltaMs = $null
  $totalReplayDeltaSum = 0.0
  $totalReplayDeltaCount = 0
  $maxRawDistance = $null
  $rawDistanceSum = 0.0
  $rawDistanceCount = 0

  foreach ($sample in $samples) {
    if ((Get-ObjectPropertyValue -InputObject $sample -Name "skipped" -DefaultValue $false) -eq $true) {
      $skippedCount += 1
    } else {
      $replayedCount += 1
    }

    $unacknowledgedCommandCount = Get-ObjectPropertyValue -InputObject $sample -Name "unacknowledgedCommandCount" -DefaultValue 0
    if (Test-FiniteNumber -Value $unacknowledgedCommandCount) {
      $maxUnacknowledgedCommandCount = [Math]::Max($maxUnacknowledgedCommandCount, [int]$unacknowledgedCommandCount)
    }

    $clampedCommandCount = Get-ObjectPropertyValue -InputObject $sample -Name "clampedCommandCount" -DefaultValue 0
    if (Test-FiniteNumber -Value $clampedCommandCount) {
      $clampedCommandCount = [int]$clampedCommandCount
      if ($clampedCommandCount -gt 0) {
        $clampedSampleCount += 1
      }
      $maxClampedCommandCount = [Math]::Max($maxClampedCommandCount, $clampedCommandCount)
    }

    $totalReplayDeltaMs = Get-ObjectPropertyValue -InputObject $sample -Name "totalReplayDeltaMs"
    if (Test-FiniteNumber -Value $totalReplayDeltaMs) {
      $totalReplayDeltaMs = [double]$totalReplayDeltaMs
      $totalReplayDeltaSum += $totalReplayDeltaMs
      $totalReplayDeltaCount += 1
      if ($null -eq $maxTotalReplayDeltaMs -or $totalReplayDeltaMs -gt $maxTotalReplayDeltaMs) {
        $maxTotalReplayDeltaMs = $totalReplayDeltaMs
      }
    }

    $rawDistance = Get-ObjectPropertyValue -InputObject $sample -Name "rawAuthoritativePositionToReplayTargetDistance"
    if (Test-FiniteNumber -Value $rawDistance) {
      $rawDistance = [double]$rawDistance
      $rawDistanceSum += $rawDistance
      $rawDistanceCount += 1
      if ($null -eq $maxRawDistance -or $rawDistance -gt $maxRawDistance) {
        $maxRawDistance = $rawDistance
      }
    }
  }

  return [ordered]@{
    available = $true
    status = "available"
    sampleCount = $samples.Count
    skippedSampleCount = $skippedCount
    replayedSampleCount = $replayedCount
    clampedSampleCount = $clampedSampleCount
    maxUnacknowledgedCommandCount = $maxUnacknowledgedCommandCount
    maxClampedCommandCount = $maxClampedCommandCount
    maxTotalReplayDeltaMs = Round-MetricNumber -Value $maxTotalReplayDeltaMs
    avgTotalReplayDeltaMs = if ($totalReplayDeltaCount -gt 0) { Round-MetricNumber -Value ($totalReplayDeltaSum / $totalReplayDeltaCount) } else { $null }
    maxRawAuthoritativePositionToReplayTargetDistance = Round-MetricNumber -Value $maxRawDistance
    avgRawAuthoritativePositionToReplayTargetDistance = if ($rawDistanceCount -gt 0) { Round-MetricNumber -Value ($rawDistanceSum / $rawDistanceCount) } else { $null }
  }
}

function New-AuthoritativeLocalHeroReplayMetric {
  param(
    $Before,
    $After
  )

  if ($null -eq $After -or $After.available -ne $true) {
    return [ordered]@{
      available = $false
      status = "unavailable"
      before = $Before
      after = $After
    }
  }

  $beforeDiagnostics = $null
  if ($null -ne $Before -and $Before.available -eq $true) {
    $beforeDiagnostics = $Before.diagnostics
  }

  $afterDiagnostics = $After.diagnostics
  $beforeObservedCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "observedCount" -DefaultValue 0)
  $beforeReplayedCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "replayedCount" -DefaultValue 0)
  $beforeSkippedCount = [int](Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "skippedCount" -DefaultValue 0)
  $afterObservedCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "observedCount" -DefaultValue 0)
  $afterReplayedCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "replayedCount" -DefaultValue 0)
  $afterSkippedCount = [int](Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "skippedCount" -DefaultValue 0)

  $skipReasonDelta = [ordered]@{}
  $beforeSkipReasons = Get-ObjectPropertyValue -InputObject $beforeDiagnostics -Name "skipReasonCounts"
  $afterSkipReasons = Get-ObjectPropertyValue -InputObject $afterDiagnostics -Name "skipReasonCounts"
  foreach ($reason in @("invalid-input", "no-history", "no-unacked", "invalid-history")) {
    $beforeCount = [int](Get-ObjectPropertyValue -InputObject $beforeSkipReasons -Name $reason -DefaultValue 0)
    $afterCount = [int](Get-ObjectPropertyValue -InputObject $afterSkipReasons -Name $reason -DefaultValue 0)
    $skipReasonDelta[$reason] = $afterCount - $beforeCount
  }

  return [ordered]@{
    available = $true
    status = "available"
    mode = "window.__slayDemoBattleDiagnostics.authoritativeLocalHeroReplay"
    observedDelta = $afterObservedCount - $beforeObservedCount
    replayedDelta = $afterReplayedCount - $beforeReplayedCount
    skippedDelta = $afterSkippedCount - $beforeSkippedCount
    skipReasonDelta = $skipReasonDelta
    inputWindowSampleSummary = New-AuthoritativeLocalHeroReplaySampleSummary `
      -Diagnostics $afterDiagnostics `
      -WindowStartPageMs (Get-ObjectPropertyValue -InputObject $Before -Name "pageNowMs") `
      -WindowEndPageMs (Get-ObjectPropertyValue -InputObject $After -Name "pageNowMs")
    afterSampleSummary = New-AuthoritativeLocalHeroReplaySampleSummary -Diagnostics $afterDiagnostics
    before = $Before
    after = $After
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
    # Best effort cleanup only.
  }
}

function Convert-ToJavaStringLiteral {
  param([string]$Value)

  if ($null -eq $Value) {
    return '""'
  }

  $escaped = $Value.Replace("\", "\\").Replace('"', '\"').Replace("`r", "\r").Replace("`n", "\n")
  return '"' + $escaped + '"'
}

function Resolve-PostgresJdbcJar {
  param([Parameter(Mandatory = $true)][string]$WorkspaceRoot)

  $backendTarget = Join-Path $WorkspaceRoot "backend\target"
  if (-not (Test-Path -LiteralPath $backendTarget)) {
    return $null
  }

  return Get-ChildItem -LiteralPath $backendTarget -Recurse -Filter "postgresql-*.jar" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}

function Invoke-SmokeAccountCleanup {
  param(
    [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
    [Parameter(Mandatory = $true)][string]$RuntimeDir,
    [Parameter(Mandatory = $true)][string[]]$Handles,
    [string]$StorageMode,
    [System.Collections.Generic.List[string]]$Warnings
  )

  if ($StorageMode -ne "postgres") {
    return
  }

  $safeHandles = @(
    $Handles |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
      ForEach-Object { $_.Trim() } |
      Where-Object { $_ -match '^b28[ab][0-9a-f]{8}$' } |
      Select-Object -Unique
  )
  if ($safeHandles.Count -eq 0) {
    return
  }

  $jshellCommand = Get-Command "jshell" -ErrorAction SilentlyContinue
  if ($null -eq $jshellCommand) {
    if ($null -ne $Warnings) {
      $Warnings.Add("BP28 smoke account cleanup skipped: jshell is not available.") | Out-Null
    }
    return
  }

  $jdbcJar = Resolve-PostgresJdbcJar -WorkspaceRoot $WorkspaceRoot
  if ([string]::IsNullOrWhiteSpace($jdbcJar)) {
    if ($null -ne $Warnings) {
      $Warnings.Add("BP28 smoke account cleanup skipped: PostgreSQL JDBC jar was not found under backend target.") | Out-Null
    }
    return
  }

  if (-not (Test-Path -LiteralPath $RuntimeDir)) {
    New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
  }

  $databaseUrl = if ([string]::IsNullOrWhiteSpace($env:SLAY_DEMO_DATABASE_URL)) {
    "jdbc:postgresql://localhost:5432/slay_demo"
  } else {
    $env:SLAY_DEMO_DATABASE_URL.Trim()
  }
  $databaseUser = if ([string]::IsNullOrWhiteSpace($env:SLAY_DEMO_DATABASE_USER)) {
    "slay_user"
  } else {
    $env:SLAY_DEMO_DATABASE_USER.Trim()
  }
  $databasePassword = if ($null -eq $env:SLAY_DEMO_DATABASE_PASSWORD) {
    "secret"
  } else {
    $env:SLAY_DEMO_DATABASE_PASSWORD
  }
  $handleArray = ($safeHandles | ForEach-Object { Convert-ToJavaStringLiteral -Value $_ }) -join ", "
  $scriptPath = Join-Path $RuntimeDir "cleanup-bp28-smoke-accounts.jsh"
  $script = @"
import java.sql.*;
String url = $(Convert-ToJavaStringLiteral -Value $databaseUrl);
String user = $(Convert-ToJavaStringLiteral -Value $databaseUser);
String password = $(Convert-ToJavaStringLiteral -Value $databasePassword);
String[] handles = new String[] { $handleArray };
Connection conn = DriverManager.getConnection(url, user, password);
conn.setAutoCommit(false);
try {
  long deletedMails = 0L;
  long deletedAccounts = 0L;
  try (PreparedStatement statement = conn.prepareStatement("DELETE FROM mails WHERE lower(owner_handle) = lower(?)")) {
    for (String handle : handles) {
      statement.setString(1, handle);
      deletedMails += statement.executeUpdate();
    }
  }
  try (PreparedStatement statement = conn.prepareStatement("DELETE FROM identity_accounts WHERE lower(handle) = lower(?)")) {
    for (String handle : handles) {
      statement.setString(1, handle);
      deletedAccounts += statement.executeUpdate();
    }
  }
  conn.commit();
  System.out.println("bp28_cleanup_deleted_mails=" + deletedMails);
  System.out.println("bp28_cleanup_deleted_identity_accounts=" + deletedAccounts);
} catch (Throwable error) {
  conn.rollback();
  throw error;
} finally {
  conn.close();
}
/exit
"@
  [System.IO.File]::WriteAllText($scriptPath, $script, [System.Text.UTF8Encoding]::new($false))

  $previousErrorActionPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = "Continue"
    $cleanupOutput = & $jshellCommand.Source --class-path $jdbcJar $scriptPath 2>&1
    $cleanupExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }

  if ($cleanupExitCode -ne 0) {
    Write-Warning "BP28 smoke account cleanup failed."
    if ($null -ne $Warnings) {
      $Warnings.Add("BP28 smoke account cleanup failed: $($cleanupOutput -join ' ')") | Out-Null
    }
    return
  }

  Write-Host "BP-28E render-feel smoke: cleaned synthetic accounts $($safeHandles -join ', ')."
}

$frontendBase = Normalize-BaseUrl -Value $FrontendUrl
$backendBase = Normalize-BaseUrl -Value $BackendUrl
if ($InputDurationMs -lt 1000) {
  throw "InputDurationMs must be >= 1000."
}
if ($PreInputSettleMs -lt 0) {
  throw "PreInputSettleMs must be >= 0."
}
$InputDurationMs = Resolve-Bp28EffectiveInputDurationMs -Scenario $Scenario -RequestedInputDurationMs $InputDurationMs
$workspaceRoot = Get-WorkspaceRoot
$runtimeDir = Join-Path $workspaceRoot ".runtime\bp28-render-feel-smoke"
$clientADir = Join-Path $runtimeDir "client-a"
$clientBDir = Join-Path $runtimeDir "client-b"
$warnings = [System.Collections.Generic.List[string]]::new()
$clientA = $null
$clientB = $null
$processA = $null
$processB = $null
$clientAHandle = $null
$clientBHandle = $null
$backendStorageMode = $null

try {
  Write-Host "BP-28E render-feel smoke: checking services..."
  $health = Invoke-SmokeJson -Method "GET" -Uri (Join-TestUrl -Base $backendBase -Path "/health") -TimeoutSec 8
  Assert-Condition ($health.status -eq "ok") "Backend /health did not return status=ok."
  $backendStorageMode = "" + $health.storageMode
  Test-HttpReachable -Uri $frontendBase -Name "Frontend"

  Reset-RuntimeDir -RuntimeDir $runtimeDir -WorkspaceRoot $workspaceRoot
  $browserExe = Resolve-BrowserPath -RequestedPath $BrowserPath
  $portA = Get-FreeTcpPort
  $portB = Get-FreeTcpPort
  while ($portB -eq $portA) {
    $portB = Get-FreeTcpPort
  }

  $runSuffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
  $clientAHandle = "b28a$runSuffix"
  $clientBHandle = "b28b$runSuffix"
  $clientAUrl = New-ClientUrl -BaseUrl $frontendBase -Handle $clientAHandle -PasswordValue $Password -Skin "blue" -ModeId $ModeId
  $clientBUrl = New-ClientUrl -BaseUrl $frontendBase -Handle $clientBHandle -PasswordValue $Password -Skin "soldier" -ModeId $ModeId

  Write-Host "BP-28E render-feel smoke: launching browser clients..."
  $processA = Start-CdpBrowser `
    -BrowserExe $browserExe `
    -ProfileDir $clientADir `
    -DebugPort $portA `
    -Url $clientAUrl `
    -WindowWidth $WindowWidth `
    -WindowHeight $WindowHeight `
    -WindowX $ClientAWindowX `
    -WindowY $ClientAWindowY
  $processB = Start-CdpBrowser `
    -BrowserExe $browserExe `
    -ProfileDir $clientBDir `
    -DebugPort $portB `
    -Url $clientBUrl `
    -WindowWidth $WindowWidth `
    -WindowHeight $WindowHeight `
    -WindowX $ClientBWindowX `
    -WindowY $ClientBWindowY

  $targetA = Wait-CdpTarget -DebugPort $portA -Label "clientA"
  $targetB = Wait-CdpTarget -DebugPort $portB -Label "clientB"
  $clientA = Connect-Cdp -WebSocketUrl $targetA.webSocketDebuggerUrl -Label "clientA"
  $clientB = Connect-Cdp -WebSocketUrl $targetB.webSocketDebuggerUrl -Label "clientB"
  Initialize-CdpPage -Client $clientA
  Initialize-CdpPage -Client $clientB

  Write-Host "BP-28E render-feel smoke: waiting for both clients to enter playing..."
  $playingA = Wait-PagePlaying -Client $clientA -Label "clientA" -TimeoutSeconds $PlayingTimeoutSeconds
  $playingB = Wait-PagePlaying -Client $clientB -Label "clientB" -TimeoutSeconds $PlayingTimeoutSeconds
  Assert-Condition ($playingA.playing -eq $true) "clientA did not reach arena-shell--playing."
  Assert-Condition ($playingB.playing -eq $true) "clientB did not reach arena-shell--playing."
  $performanceEnableA = Enable-CdpPerformanceMetrics -Client $clientA -Warnings $warnings
  $performanceEnableB = Enable-CdpPerformanceMetrics -Client $clientB -Warnings $warnings

  if ($PreInputSettleMs -gt 0) {
    Start-Sleep -Milliseconds $PreInputSettleMs
  }
  $contextBeforeA = Wait-PageBattleContext -Client $clientA -Label "clientA beforeInput"
  $contextBeforeB = Wait-PageBattleContext -Client $clientB -Label "clientB beforeInput"
  $battleIdentity = Resolve-BattleIdentity -ContextA $contextBeforeA -ContextB $contextBeforeB -Warnings $warnings

  $beforeState = $null
  $statePlayerA = $null
  $statePlayerB = $null
  if (-not [string]::IsNullOrWhiteSpace($battleIdentity.battleId)) {
    $beforeState = Get-BattleState -BackendBase $backendBase -BattleId $battleIdentity.battleId
    $expectedMapId = Resolve-ExpectedMapIdForMode -ModeId $ModeId
    $actualMapId = "" + (Get-ObjectPropertyValue -InputObject $beforeState -Name "mapId" -DefaultValue "")
    Assert-Condition ($actualMapId -eq $expectedMapId) "Expected modeId=$ModeId to start mapId=$expectedMapId, but backend returned mapId=$actualMapId."
    $statePlayerA = Find-StatePlayer -State $beforeState -Handle $clientAHandle -PlayerId $contextBeforeA.localAuthoritativePlayerId
    $statePlayerB = Find-StatePlayer -State $beforeState -Handle $clientBHandle -PlayerId $contextBeforeB.localAuthoritativePlayerId
    if ($null -ne $statePlayerA -and $null -ne $statePlayerB) {
      $battleIdentity.sameBattleProven = $true
    } else {
      $warnings.Add("API battle state was readable, but roster did not expose both smoke handles.") | Out-Null
    }
  }
  $clientAHero = Find-ContextHero -Context $contextBeforeA -Handle $clientAHandle
  $clientAHeroId = ""
  $clientAHeroDisplayName = $clientAHandle
  if ($null -ne $clientAHero) {
    $clientAHeroId = "" + (Get-ObjectPropertyValue -InputObject $clientAHero -Name "heroId" -DefaultValue "")
    $clientAHeroDisplayName = "" + (Get-ObjectPropertyValue -InputObject $clientAHero -Name "displayName" -DefaultValue $clientAHandle)
  }
  if ($null -ne $statePlayerA) {
    $stateHeroId = "" + (Get-ObjectPropertyValue -InputObject $statePlayerA -Name "heroId" -DefaultValue "")
    if (-not [string]::IsNullOrWhiteSpace($stateHeroId)) {
      $clientAHeroId = $stateHeroId
    }
    $stateDisplayName = "" + (Get-ObjectPropertyValue -InputObject $statePlayerA -Name "displayName" -DefaultValue "")
    if (-not [string]::IsNullOrWhiteSpace($stateDisplayName)) {
      $clientAHeroDisplayName = $stateDisplayName
    }
  }

  $sampleDurationMs = [Math]::Max(3000, [Math]::Max($FrameSampleSeconds * 1000, $InputDurationMs + 1500))
  $preInputSampleMs = [Math]::Min(1000, [Math]::Max(500, [Math]::Floor($sampleDurationMs * 0.22)))
  Start-RafSample -Client $clientA -DurationMs $sampleDurationMs
  Start-RafSample -Client $clientB -DurationMs $sampleDurationMs
  $performanceBeforeInputA = Read-CdpPerformanceMetrics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  $performanceBeforeInputB = Read-CdpPerformanceMetrics -Client $clientB -Phase "beforeInput" -Warnings $warnings
  Start-Sleep -Milliseconds $preInputSampleMs
  $localHeroCorrectionBeforeA = Read-LocalHeroCorrectionDiagnostics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  $localHeroCorrectionBeforeB = Read-LocalHeroCorrectionDiagnostics -Client $clientB -Phase "beforeInput" -Warnings $warnings
  $authoritativeLocalHeroReplayBeforeA = Read-AuthoritativeLocalHeroReplayDiagnostics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  $authoritativeLocalHeroReplayBeforeB = Read-AuthoritativeLocalHeroReplayDiagnostics -Client $clientB -Phase "beforeInput" -Warnings $warnings
  $vfxBeforeA = Read-VfxDiagnostics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  $vfxBeforeB = Read-VfxDiagnostics -Client $clientB -Phase "beforeInput" -Warnings $warnings
  $hudBeforeA = Read-HudDiagnostics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  $hudBeforeB = Read-HudDiagnostics -Client $clientB -Phase "beforeInput" -Warnings $warnings
  $localFeedbackBeforeA = Read-LocalFeedbackDiagnostics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  $visionBeforeA = Read-VisionDiagnostics -Client $clientA -Phase "beforeInput"
  $visionBeforeB = Read-VisionDiagnostics -Client $clientB -Phase "beforeInput"
  Assert-InitialCameraLookAheadStable -VisionRead $visionBeforeA -Label "clientA beforeInput"
  Assert-InitialCameraLookAheadStable -VisionRead $visionBeforeB -Label "clientB beforeInput"
  $localFeedbackBeforeB = $null
  if ($Scenario -eq "DualClientPressure") {
    $localFeedbackBeforeB = Read-LocalFeedbackDiagnostics -Client $clientB -Phase "beforeInput" -Warnings $warnings
  }
  $remoteViewBeforeA = $null
  if ($Scenario -eq "DualClientPressure") {
    $remoteViewBeforeA = Read-RemoteViewDiagnostics -Client $clientA -Phase "beforeInput" -Warnings $warnings
  }
  $remoteViewBeforeB = Read-RemoteViewDiagnostics -Client $clientB -Phase "beforeInput" -Warnings $warnings

  if ($Scenario -eq "DualClientPressure") {
    Write-Host "BP-28E render-feel smoke: injecting dual-client pressure input..."
  } else {
    Write-Host "BP-28E render-feel smoke: injecting clientA movement/fire input..."
  }
  Set-RafPhase -Client $clientA -Phase "input"
  Set-RafPhase -Client $clientB -Phase "input"
  $inputStartWallMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if ($Scenario -eq "DualClientPressure") {
    $input = Invoke-DualClientPressureInputBurst `
      -ClientA $clientA `
      -ClientB $clientB `
      -DurationMs $InputDurationMs `
      -WindowWidth $WindowWidth `
      -WindowHeight $WindowHeight `
      -ProbePollIntervalMs 80
  } else {
    $input = Invoke-InputBurst `
      -Client $clientA `
      -DurationMs $InputDurationMs `
      -Scenario $Scenario `
      -WindowWidth $WindowWidth `
      -WindowHeight $WindowHeight `
      -BeforeState $beforeState `
      -BackendBase $backendBase `
      -BattleId $battleIdentity.battleId `
      -Handle $clientAHandle `
      -PlayerId $contextBeforeA.localAuthoritativePlayerId `
      -BeforeContext $contextBeforeA `
      -LocalFeedbackBefore $localFeedbackBeforeA `
      -RemoteViewClient $clientB `
      -RemoteViewBefore $remoteViewBeforeB `
      -RemoteHeroId $clientAHeroId `
      -RemoteHeroDisplayName $clientAHeroDisplayName `
      -InputStartWallMs $inputStartWallMs `
      -ProbePollIntervalMs 100
  }
  $inputEndWallMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $input | Add-Member -NotePropertyName inputStartWallMs -NotePropertyValue $inputStartWallMs -Force
  $input | Add-Member -NotePropertyName inputEndWallMs -NotePropertyValue $inputEndWallMs -Force
  $input | Add-Member -NotePropertyName wallDurationMs -NotePropertyValue ([long]($inputEndWallMs - $inputStartWallMs)) -Force
  $inputDispatchStartPageMs = Get-ObjectPropertyValue -InputObject $input -Name "inputDispatchStartPageMs"
  if ($null -eq $inputDispatchStartPageMs) {
    $inputDispatchStartPageMs = Get-ObjectPropertyValue -InputObject $input -Name "inputStartPageMs"
  }
  $inputDispatchStartWallMs = Get-ObjectPropertyValue -InputObject $input -Name "inputDispatchStartWallMs"
  if ($null -eq $inputDispatchStartWallMs) {
    $inputDispatchStartWallMs = $inputStartWallMs
  }
  $firstInputEventPageMs = Get-ObjectPropertyValue -InputObject $input -Name "firstInputEventPageMs"
  $firstInputEventWallMs = Get-ObjectPropertyValue -InputObject $input -Name "firstInputEventWallMs"
  $firstInputEventType = Get-ObjectPropertyValue -InputObject $input -Name "firstInputEventType"
  $firstInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $input -Name "firstInputEventKeyOrButton"
  $firstMovementInputEventPageMs = Get-ObjectPropertyValue -InputObject $input -Name "firstMovementInputEventPageMs"
  $firstMovementInputEventWallMs = Get-ObjectPropertyValue -InputObject $input -Name "firstMovementInputEventWallMs"
  $firstMovementInputEventType = Get-ObjectPropertyValue -InputObject $input -Name "firstMovementInputEventType"
  $firstMovementInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $input -Name "firstMovementInputEventKeyOrButton"
  $firstFireInputEventPageMs = Get-ObjectPropertyValue -InputObject $input -Name "firstFireInputEventPageMs"
  $firstFireInputEventWallMs = Get-ObjectPropertyValue -InputObject $input -Name "firstFireInputEventWallMs"
  $firstFireInputEventType = Get-ObjectPropertyValue -InputObject $input -Name "firstFireInputEventType"
  $firstFireInputEventKeyOrButton = Get-ObjectPropertyValue -InputObject $input -Name "firstFireInputEventKeyOrButton"
  $inputEndPageMs = Get-ObjectPropertyValue -InputObject $input -Name "inputEndPageMs"
  $dispatchToEventOverheadMs = Get-ObjectPropertyValue -InputObject $input -Name "dispatchToEventOverheadMs"
  $localFeedbackInputStartPageMs = $inputDispatchStartPageMs
  $localFeedbackInputStartWallMs = $inputDispatchStartWallMs
  $localFeedbackLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstInputEventPageMs) {
    $localFeedbackInputStartPageMs = $firstInputEventPageMs
    $localFeedbackLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $localFeedbackInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $motionInputStartPageMs = $inputDispatchStartPageMs
  $motionInputStartWallMs = $inputDispatchStartWallMs
  $motionLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstMovementInputEventPageMs) {
    $motionInputStartPageMs = $firstMovementInputEventPageMs
    $motionLatencyBasis = "firstMovementInputEventPageMs"
    if ($null -ne $firstMovementInputEventWallMs) {
      $motionInputStartWallMs = [long]([double]$firstMovementInputEventWallMs)
    }
  } elseif ($null -ne $firstInputEventPageMs) {
    $motionInputStartPageMs = $firstInputEventPageMs
    $motionLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $motionInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $muzzleInputStartPageMs = $inputDispatchStartPageMs
  $muzzleInputStartWallMs = $inputDispatchStartWallMs
  $muzzleLatencyBasis = "inputDispatchStartPageMs"
  if ($null -ne $firstFireInputEventPageMs) {
    $muzzleInputStartPageMs = $firstFireInputEventPageMs
    $muzzleLatencyBasis = "firstFireInputEventPageMs"
    if ($null -ne $firstFireInputEventWallMs) {
      $muzzleInputStartWallMs = [long]([double]$firstFireInputEventWallMs)
    }
  } elseif ($null -ne $firstInputEventPageMs) {
    $muzzleInputStartPageMs = $firstInputEventPageMs
    $muzzleLatencyBasis = "firstInputEventPageMs"
    if ($null -ne $firstInputEventWallMs) {
      $muzzleInputStartWallMs = [long]([double]$firstInputEventWallMs)
    }
  }
  $inputWindowStartPageMs = $firstInputEventPageMs
  $inputWindowSource = "firstInputEvent/inputEnd"
  if ($null -eq $inputWindowStartPageMs) {
    $inputWindowStartPageMs = $inputDispatchStartPageMs
    $inputWindowSource = "inputDispatch/inputEnd"
  }
  Set-RafInputWindow -Client $clientA -InputStartPageMs $inputWindowStartPageMs -InputEndPageMs $inputEndPageMs -Source $inputWindowSource
  if ($Scenario -eq "DualClientPressure") {
    $inputB = Get-ObjectPropertyValue -InputObject $input -Name "clientB"
    $inputBWindowStartPageMs = Get-ObjectPropertyValue -InputObject $inputB -Name "firstInputEventPageMs"
    $inputBWindowSource = "clientB.firstInputEvent/inputEnd"
    if ($null -eq $inputBWindowStartPageMs) {
      $inputBWindowStartPageMs = Get-ObjectPropertyValue -InputObject $inputB -Name "inputDispatchStartPageMs"
      $inputBWindowSource = "clientB.inputDispatch/inputEnd"
    }
    Set-RafInputWindow -Client $clientB -InputStartPageMs $inputBWindowStartPageMs -InputEndPageMs (Get-ObjectPropertyValue -InputObject $inputB -Name "inputEndPageMs") -Source $inputBWindowSource
  } else {
    Set-RafInputWindow -Client $clientB -InputStartPageMs $inputWindowStartPageMs -InputEndPageMs $inputEndPageMs -Source "clientA-observation/$inputWindowSource"
  }
  Set-RafPhase -Client $clientA -Phase "postInput"
  Set-RafPhase -Client $clientB -Phase "postInput"
  $performanceAfterInputA = Read-CdpPerformanceMetrics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $performanceAfterInputB = Read-CdpPerformanceMetrics -Client $clientB -Phase "afterInput" -Warnings $warnings
  $localFeedbackAfterA = Read-LocalFeedbackDiagnostics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $localFeedbackAfterB = $null
  if ($Scenario -eq "DualClientPressure") {
    $localFeedbackAfterB = Read-LocalFeedbackDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings
  }
  $visionAfterA = Read-VisionDiagnostics -Client $clientA -Phase "afterInput"
  $visionAfterB = Read-VisionDiagnostics -Client $clientB -Phase "afterInput"
  $remoteViewAfterB = Read-RemoteViewDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings
  $remoteViewMetric = New-RemoteViewMetric `
    -Before $remoteViewBeforeB `
    -After $remoteViewAfterB `
    -RemoteHeroId $clientAHeroId `
    -RemoteHeroDisplayName $clientAHeroDisplayName `
    -InputStartWallMs $inputStartWallMs `
    -InputEndWallMs $inputEndWallMs
  $remoteViewFallbackUsed = $false
  if (-not (Test-RemoteViewMetricComplete -Metric $remoteViewMetric)) {
    $remoteViewFallbackUsed = $true
    $fallbackRemoteViewMetric = Wait-RemoteViewMetric `
      -Client $clientB `
      -Before $remoteViewBeforeB `
      -RemoteHeroId $clientAHeroId `
      -RemoteHeroDisplayName $clientAHeroDisplayName `
      -InputStartWallMs $inputStartWallMs `
      -InputEndWallMs $inputEndWallMs
    if ($null -ne $fallbackRemoteViewMetric) {
      $remoteViewMetric = $fallbackRemoteViewMetric
    }
  }
  if ($remoteViewMetric -is [System.Collections.IDictionary]) {
    $remoteViewMetric["postInputFallbackUsed"] = $remoteViewFallbackUsed
    $remoteViewMetric["inputProbe"] = $input.inputProbe.remoteView
  }
  $localFeedbackLatencyMetric = New-LocalFeedbackLatencyMetric `
    -Before $localFeedbackBeforeA `
    -After $localFeedbackAfterA `
    -InputStartPageMs $localFeedbackInputStartPageMs `
    -InputEndPageMs (Get-ObjectPropertyValue -InputObject $input -Name "inputEndPageMs") `
    -InputStartWallMs $localFeedbackInputStartWallMs `
    -InputEndWallMs $inputEndWallMs `
    -LatencyBasis $localFeedbackLatencyBasis `
    -InputDispatchStartPageMs $inputDispatchStartPageMs `
    -InputDispatchStartWallMs $inputDispatchStartWallMs `
    -FirstInputEventPageMs $firstInputEventPageMs `
    -FirstInputEventType $firstInputEventType `
    -FirstInputEventKeyOrButton $firstInputEventKeyOrButton `
    -FirstInputEventWallMs $(if ($null -ne $firstInputEventWallMs) { [long]([double]$firstInputEventWallMs) } else { $null }) `
    -FirstMovementInputEventPageMs $firstMovementInputEventPageMs `
    -FirstMovementInputEventType $firstMovementInputEventType `
    -FirstMovementInputEventKeyOrButton $firstMovementInputEventKeyOrButton `
    -FirstMovementInputEventWallMs $(if ($null -ne $firstMovementInputEventWallMs) { [long]([double]$firstMovementInputEventWallMs) } else { $null }) `
    -FirstFireInputEventPageMs $firstFireInputEventPageMs `
    -FirstFireInputEventType $firstFireInputEventType `
    -FirstFireInputEventKeyOrButton $firstFireInputEventKeyOrButton `
    -FirstFireInputEventWallMs $(if ($null -ne $firstFireInputEventWallMs) { [long]([double]$firstFireInputEventWallMs) } else { $null }) `
    -MotionInputStartPageMs $motionInputStartPageMs `
    -MotionInputStartWallMs $(if ($null -ne $motionInputStartWallMs) { [long]([double]$motionInputStartWallMs) } else { $null }) `
    -MotionLatencyBasis $motionLatencyBasis `
    -MuzzleInputStartPageMs $muzzleInputStartPageMs `
    -MuzzleInputStartWallMs $(if ($null -ne $muzzleInputStartWallMs) { [long]([double]$muzzleInputStartWallMs) } else { $null }) `
    -MuzzleLatencyBasis $muzzleLatencyBasis `
    -DispatchToEventOverheadMs $dispatchToEventOverheadMs
  $localFeedbackFallbackUsed = $false
  if (-not (Test-LocalFeedbackMetricComplete -Metric $localFeedbackLatencyMetric)) {
    $localFeedbackFallbackUsed = $true
    $fallbackLocalFeedbackLatencyMetric = Wait-LocalFeedbackLatencyMetric `
      -Client $clientA `
      -Before $localFeedbackBeforeA `
      -InputStartPageMs $localFeedbackInputStartPageMs `
      -InputEndPageMs (Get-ObjectPropertyValue -InputObject $input -Name "inputEndPageMs") `
      -InputStartWallMs $localFeedbackInputStartWallMs `
      -InputEndWallMs $inputEndWallMs `
      -LatencyBasis $localFeedbackLatencyBasis `
      -InputDispatchStartPageMs $inputDispatchStartPageMs `
      -InputDispatchStartWallMs $inputDispatchStartWallMs `
      -FirstInputEventPageMs $firstInputEventPageMs `
      -FirstInputEventType $firstInputEventType `
      -FirstInputEventKeyOrButton $firstInputEventKeyOrButton `
      -FirstInputEventWallMs $(if ($null -ne $firstInputEventWallMs) { [long]([double]$firstInputEventWallMs) } else { $null }) `
      -FirstMovementInputEventPageMs $firstMovementInputEventPageMs `
      -FirstMovementInputEventType $firstMovementInputEventType `
      -FirstMovementInputEventKeyOrButton $firstMovementInputEventKeyOrButton `
      -FirstMovementInputEventWallMs $(if ($null -ne $firstMovementInputEventWallMs) { [long]([double]$firstMovementInputEventWallMs) } else { $null }) `
      -FirstFireInputEventPageMs $firstFireInputEventPageMs `
      -FirstFireInputEventType $firstFireInputEventType `
      -FirstFireInputEventKeyOrButton $firstFireInputEventKeyOrButton `
      -FirstFireInputEventWallMs $(if ($null -ne $firstFireInputEventWallMs) { [long]([double]$firstFireInputEventWallMs) } else { $null }) `
      -MotionInputStartPageMs $motionInputStartPageMs `
      -MotionInputStartWallMs $(if ($null -ne $motionInputStartWallMs) { [long]([double]$motionInputStartWallMs) } else { $null }) `
      -MotionLatencyBasis $motionLatencyBasis `
      -MuzzleInputStartPageMs $muzzleInputStartPageMs `
      -MuzzleInputStartWallMs $(if ($null -ne $muzzleInputStartWallMs) { [long]([double]$muzzleInputStartWallMs) } else { $null }) `
      -MuzzleLatencyBasis $muzzleLatencyBasis `
      -DispatchToEventOverheadMs $dispatchToEventOverheadMs
    if ($null -ne $fallbackLocalFeedbackLatencyMetric) {
      $localFeedbackLatencyMetric = $fallbackLocalFeedbackLatencyMetric
    }
  }
  if ($localFeedbackLatencyMetric -is [System.Collections.IDictionary]) {
    $localFeedbackLatencyMetric["postInputFallbackUsed"] = $localFeedbackFallbackUsed
    $localFeedbackLatencyMetric["inputProbe"] = $input.inputProbe.localFeedback
    $localFeedbackLatencyMetric["legacyInputStartPageMs"] = Get-ObjectPropertyValue -InputObject $input -Name "inputStartPageMs"
    $localFeedbackLatencyMetric["legacyInputStartWallMs"] = Get-ObjectPropertyValue -InputObject $input -Name "inputStartPageWallMs"
    $localFeedbackLatencyMetric["preDispatchOverheadMs"] = Get-ObjectPropertyValue -InputObject $input -Name "preDispatchOverheadMs"
    $localFeedbackLatencyMetric["inputEventProbe"] = Get-ObjectPropertyValue -InputObject $input -Name "inputEventProbe"
  }

  $authoritativeActionMetric = $null
  if ($null -ne $input.inputProbe -and $null -ne $input.inputProbe.authoritative) {
    $authoritativeActionMetric = $input.inputProbe.authoritative
  }
  if ($null -ne $beforeState -and -not [string]::IsNullOrWhiteSpace($battleIdentity.battleId)) {
    if ($null -eq $authoritativeActionMetric -or $authoritativeActionMetric.available -ne $true -or $authoritativeActionMetric.passed -ne $true) {
      $authoritativeActionMetric = Wait-AuthoritativeActionEffect `
        -BackendBase $backendBase `
        -BattleId $battleIdentity.battleId `
        -BeforeState $beforeState `
        -Handle $clientAHandle `
        -PlayerId $contextBeforeA.localAuthoritativePlayerId `
        -InputStartWallMs $inputStartWallMs `
        -ProbeWindow "postInputFallback" `
        -CapturedDuringInput $false
    }
  }

  $pageSnapshotActionMetric = $null
  if ($null -ne $input.inputProbe -and $null -ne $input.inputProbe.pageSnapshot) {
    $pageSnapshotActionMetric = $input.inputProbe.pageSnapshot
  }
  if ($null -eq $pageSnapshotActionMetric -or $pageSnapshotActionMetric.available -ne $true -or $pageSnapshotActionMetric.passed -ne $true) {
    $pageSnapshotActionMetric = Wait-PageSnapshotActionEffect `
      -Client $clientA `
      -BeforeContext $contextBeforeA `
      -Handle $clientAHandle `
      -InputStartWallMs $inputStartWallMs `
      -TimeoutMs 2500 `
      -PollIntervalMs 75 `
      -ProbeWindow "postInputFallback" `
      -CapturedDuringInput $false
  }
  if ($pageSnapshotActionMetric.available -ne $true) {
    $warnings.Add("inputFeedbackLatencyMetric pageSnapshot unavailable: $($pageSnapshotActionMetric.reason)") | Out-Null
  }

  $remainingSampleMs = [Math]::Max(0, $sampleDurationMs - [int]$preInputSampleMs - [int]$input.durationMs + 500)
  if ($remainingSampleMs -gt 0) {
    Start-Sleep -Milliseconds $remainingSampleMs
  }

  $rafA = Read-RafSample -Client $clientA
  $rafB = Read-RafSample -Client $clientB
  Assert-Condition ([int]$rafA.frameCount -gt 0) "clientA RAF sample did not collect frames."
  Assert-Condition ([int]$rafB.frameCount -gt 0) "clientB RAF sample did not collect frames."
  $vfxAfterA = Read-VfxDiagnostics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $vfxAfterB = Read-VfxDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings
  $hudAfterA = Read-HudDiagnostics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $hudAfterB = Read-HudDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings

  $contextAfterA = Get-PageBattleContext -Client $clientA
  $contextAfterB = Get-PageBattleContext -Client $clientB
  $afterState = $null
  if (-not [string]::IsNullOrWhiteSpace($battleIdentity.battleId)) {
    $afterState = Get-BattleState -BackendBase $backendBase -BattleId $battleIdentity.battleId
  }
  $visionTruthAfterA = Read-VisionDiagnostics -Client $clientA -Phase "displayTruthAfterState"
  $remoteViewHitDisputeAfterB = Read-RemoteViewDiagnostics -Client $clientB -Phase "hitDisputeAfterState" -Warnings $warnings

  $localStorageActionMetric = Measure-LocalStorageActionEffect `
    -BeforeContext $contextBeforeA `
    -AfterContext $contextAfterA `
    -Handle $clientAHandle

  $actionMetric = $authoritativeActionMetric
  $actionMetricSource = "api.authoritativeBattleState"
  if ($null -eq $actionMetric -or $actionMetric.available -ne $true) {
    $actionMetric = $localStorageActionMetric
    $actionMetricSource = "localStorage.activeBattleSession"
  }

  $actionMetricPassed = (
    $null -ne $actionMetric -and
    $actionMetric.available -eq $true -and
    $actionMetric.passed -eq $true
  )
  if ($Scenario -eq "WeaponSwitchPressure") {
    $actionMetricPassed = $true
  }
  if (-not $actionMetricPassed -and -not [string]::IsNullOrWhiteSpace($SummaryPath)) {
    $failureParent = Split-Path -Parent $SummaryPath
    if (-not [string]::IsNullOrWhiteSpace($failureParent)) {
      New-Item -ItemType Directory -Force -Path $failureParent | Out-Null
    }

    $failureEvidence = [ordered]@{
      ok = $false
      smoke = "BP-28E render-feel"
      failure = "clientA authoritative/local battle state did not show movement or firing after CDP input."
      actionMetricSource = $actionMetricSource
      actionMetric = $actionMetric
      authoritativeActionMetric = $authoritativeActionMetric
      pageSnapshotActionMetric = $pageSnapshotActionMetric
      localStorageActionMetric = $localStorageActionMetric
      input = $input
      localFeedbackLatencyMetric = $localFeedbackLatencyMetric
      remoteViewMetric = $remoteViewMetric
      contextBeforeA = $contextBeforeA
      contextAfterA = $contextAfterA
      battle = [ordered]@{
        battleId = $battleIdentity.battleId
        source = $battleIdentity.source
        sameBattleProven = $battleIdentity.sameBattleProven
      }
      backend = [ordered]@{
        beforeStateReadable = ($null -ne $beforeState)
        afterStateReadable = ($null -ne $afterState)
      }
      warnings = @($warnings)
    }
    Set-Content -LiteralPath $SummaryPath -Value ($failureEvidence | ConvertTo-Json -Depth 18) -Encoding UTF8
  }
  Assert-Condition $actionMetricPassed "clientA authoritative/local battle state did not show movement or firing after CDP input."

  $targetedSkillPressureMetric = $null
  $targetedSkillPressureAssertionFailures = [System.Collections.Generic.List[string]]::new()
  if ($Scenario -eq "TargetedSkillPressure") {
    $targetedInputSkillKeys = @(Get-ObjectPropertyValue -InputObject $input -Name "targetedSkillKeys" -DefaultValue @())
    $targetedInputSkillTapCount = [int](Get-ObjectPropertyValue -InputObject $input -Name "targetedSkillTapCount" -DefaultValue 0)
    $targetedInputConfirmCount = [int](Get-ObjectPropertyValue -InputObject $input -Name "targetedConfirmCount" -DefaultValue 0)
    $commandFetchProbe = Get-ObjectPropertyValue -InputObject $input -Name "commandFetchProbe"
    $castBlinkTrueCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "castBlinkTrueCount" -DefaultValue 0)
    $castFreezeTrueCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "castFreezeTrueCount" -DefaultValue 0)
    $blinkAppliedCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "blinkAppliedCount" -DefaultValue 0)
    $blinkNoopCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "blinkNoopCount" -DefaultValue 0)
    $freezeAppliedCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "freezeAppliedCount" -DefaultValue 0)
    $freezeNoopCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "freezeNoopCount" -DefaultValue 0)
    $skillOutcomeCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "skillOutcomeCount" -DefaultValue 0)
    $skillNoopWithoutReasonCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "skillNoopWithoutReasonCount" -DefaultValue 0)
    $channelSkillProbe = Invoke-CdpEvaluate -Client $clientA -Expression @'
(() => {
  const root = window.__slayDemoBattleDiagnostics || {};
  const preparedSamples = Array.isArray(root.authoritativePreparedInput?.samples)
    ? root.authoritativePreparedInput.samples
    : [];
  const commandSamples = Array.isArray(root.authoritativeNetwork?.commandSubmit?.recentSamples)
    ? root.authoritativeNetwork.commandSubmit.recentSamples
    : [];
  const commandSubmit = root.authoritativeNetwork?.commandSubmit || null;
  const countPreparedSkill = (skill) => preparedSamples.filter((sample) =>
    sample &&
    (
      sample.castSkill === skill ||
      (skill === "Blink" && sample.outputCastBlink === true) ||
      (skill === "Freeze" && sample.outputCastFreeze === true)
    )
  ).length;
  const countCommandSkill = (field) => commandSamples.filter((sample) => sample && sample[field] === true).length;
  return {
    preparedSampleCount: preparedSamples.length,
    preparedBlinkCount: countPreparedSkill("Blink"),
    preparedFreezeCount: countPreparedSkill("Freeze"),
    commandSampleCount: commandSamples.length,
    commandSubmitCount: Number(commandSubmit?.count || 0),
    commandAcceptedCount: Number(commandSubmit?.acceptedCount || 0),
    commandFailedCount: Number(commandSubmit?.failedCount || 0),
    commandCastBlinkCount: countCommandSkill("castBlink"),
    commandCastFreezeCount: countCommandSkill("castFreeze")
  };
})()
'@ -TimeoutSeconds 8
    $channelPreparedBlinkCount = [int](Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "preparedBlinkCount" -DefaultValue 0)
    $channelPreparedFreezeCount = [int](Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "preparedFreezeCount" -DefaultValue 0)
    $channelCommandBlinkCount = [int](Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "commandCastBlinkCount" -DefaultValue 0)
    $channelCommandFreezeCount = [int](Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "commandCastFreezeCount" -DefaultValue 0)
    $channelCommandAcceptedCount = [int](Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "commandAcceptedCount" -DefaultValue 0)
    $channelCommandFailedCount = [int](Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "commandFailedCount" -DefaultValue 0)
    $fetchRequestCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "requestCount" -DefaultValue 0)

    $deferTargetedAssert = {
      param(
        [bool]$Passed,
        [Parameter(Mandatory = $true)][string]$Message
      )

      if (-not $Passed) {
        $targetedSkillPressureAssertionFailures.Add($Message) | Out-Null
      }
    }

    & $deferTargetedAssert ($targetedInputSkillTapCount -ge 2) "TargetedSkillPressure did not dispatch both targeted skill prepare taps."
    & $deferTargetedAssert (($targetedInputSkillKeys -contains "q") -and ($targetedInputSkillKeys -contains "r")) "TargetedSkillPressure did not dispatch both Q and R targeted skill prepare taps."
    & $deferTargetedAssert ($targetedInputConfirmCount -ge 2) "TargetedSkillPressure did not dispatch both targeted skill confirm clicks."
    & $deferTargetedAssert (
      (
        $null -ne $commandFetchProbe -and
        $commandFetchProbe.available -eq $true -and
        $fetchRequestCount -gt 0
      ) -or
      ($channelCommandAcceptedCount -gt 0)
    ) "TargetedSkillPressure did not observe authoritative command submissions."
    & $deferTargetedAssert (
      $castBlinkTrueCount -gt 0 -or
      $channelPreparedBlinkCount -gt 0 -or
      $channelCommandBlinkCount -gt 0
    ) "TargetedSkillPressure did not observe castBlink=true."
    & $deferTargetedAssert (
      $castFreezeTrueCount -gt 0 -or
      $channelPreparedFreezeCount -gt 0 -or
      $channelCommandFreezeCount -gt 0
    ) "TargetedSkillPressure did not observe castFreeze=true."
    & $deferTargetedAssert (
      $skillOutcomeCount -gt 0 -or
      ($channelCommandBlinkCount -gt 0 -and $channelCommandFreezeCount -gt 0 -and $channelCommandAcceptedCount -gt 0)
    ) "TargetedSkillPressure did not observe accepted backend skill commands."
    & $deferTargetedAssert (
      ($blinkAppliedCount + $blinkNoopCount) -gt 0 -or
      $channelCommandBlinkCount -gt 0
    ) "TargetedSkillPressure did not observe a Blink command."
    & $deferTargetedAssert (
      ($freezeAppliedCount + $freezeNoopCount) -gt 0 -or
      $channelCommandFreezeCount -gt 0
    ) "TargetedSkillPressure did not observe a Freeze command."
    & $deferTargetedAssert ($skillNoopWithoutReasonCount -eq 0) "TargetedSkillPressure command fetch probe observed a noop skill outcome without a reason."
    & $deferTargetedAssert ($channelCommandFailedCount -eq 0) "TargetedSkillPressure channel command diagnostics observed failed command submissions."
    foreach ($failure in $targetedSkillPressureAssertionFailures) {
      $warnings.Add("TargetedSkillPressure deferred assertion failure: $failure") | Out-Null
    }

    $targetedSkillPressureMetric = [ordered]@{
      passed = ($targetedSkillPressureAssertionFailures.Count -eq 0)
      assertionFailures = @($targetedSkillPressureAssertionFailures)
      targetedSkillTapCount = $targetedInputSkillTapCount
      targetedSkillKeys = @($targetedInputSkillKeys)
      targetedConfirmCount = $targetedInputConfirmCount
      commandFetch = [ordered]@{
        available = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "available"
        requestCount = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "requestCount"
        castBlinkTrueCount = $castBlinkTrueCount
        castFreezeTrueCount = $castFreezeTrueCount
        blinkAppliedCount = $blinkAppliedCount
        blinkNoopCount = $blinkNoopCount
        freezeAppliedCount = $freezeAppliedCount
        freezeNoopCount = $freezeNoopCount
        skillOutcomeCount = $skillOutcomeCount
        skillOutcomeReasons = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "skillOutcomeReasons"
        skillNoopWithoutReasonCount = $skillNoopWithoutReasonCount
        responseParseFailedCount = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "responseParseFailedCount"
        responseCommandStatusCounts = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "responseCommandStatusCounts"
        responseCommandReasonCounts = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "responseCommandReasonCounts"
      }
      channel = [ordered]@{
        preparedSampleCount = Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "preparedSampleCount"
        preparedBlinkCount = $channelPreparedBlinkCount
        preparedFreezeCount = $channelPreparedFreezeCount
        commandSampleCount = Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "commandSampleCount"
        commandSubmitCount = Get-ObjectPropertyValue -InputObject $channelSkillProbe -Name "commandSubmitCount"
        commandAcceptedCount = $channelCommandAcceptedCount
        commandFailedCount = $channelCommandFailedCount
        commandCastBlinkCount = $channelCommandBlinkCount
        commandCastFreezeCount = $channelCommandFreezeCount
      }
    }
  }

  $targetedSkillNoopMetric = $null
  $targetedSkillNoopAssertionFailures = [System.Collections.Generic.List[string]]::new()
  if ($Scenario -eq "TargetedSkillNoopPressure") {
    $targetedInputSkillKeys = @(Get-ObjectPropertyValue -InputObject $input -Name "targetedSkillKeys" -DefaultValue @())
    $targetedInputSkillTapCount = [int](Get-ObjectPropertyValue -InputObject $input -Name "targetedSkillTapCount" -DefaultValue 0)
    $targetedInputConfirmCount = [int](Get-ObjectPropertyValue -InputObject $input -Name "targetedConfirmCount" -DefaultValue 0)
    $commandFetchProbe = Get-ObjectPropertyValue -InputObject $input -Name "commandFetchProbe"
    $transientNoticeProbe = Get-ObjectPropertyValue -InputObject $input -Name "transientNoticeProbe"
    $castBlinkTrueCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "castBlinkTrueCount" -DefaultValue 0)
    $castFreezeTrueCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "castFreezeTrueCount" -DefaultValue 0)
    $blinkNoopCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "blinkNoopCount" -DefaultValue 0)
    $freezeNoopCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "freezeNoopCount" -DefaultValue 0)
    $skillOutcomeCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "skillOutcomeCount" -DefaultValue 0)
    $skillNoopWithoutReasonCount = [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "skillNoopWithoutReasonCount" -DefaultValue 0)
    $blinkNoopReasons = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "blinkNoopReasons"
    $freezeNoopReasons = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "freezeNoopReasons"
    $blinkGeometryNoopCount =
      [int](Get-ObjectPropertyValue -InputObject $blinkNoopReasons -Name "out_of_range" -DefaultValue 0) +
      [int](Get-ObjectPropertyValue -InputObject $blinkNoopReasons -Name "invalid_target" -DefaultValue 0)
    $freezeGeometryNoopCount =
      [int](Get-ObjectPropertyValue -InputObject $freezeNoopReasons -Name "out_of_range" -DefaultValue 0) +
      [int](Get-ObjectPropertyValue -InputObject $freezeNoopReasons -Name "invalid_target" -DefaultValue 0)
    $noticeTexts = @(Get-ObjectPropertyValue -InputObject $transientNoticeProbe -Name "texts" -DefaultValue @())
    $noticeTextJoined = $noticeTexts -join "`n"
    $blinkNoticeLabel = [string]::Concat([char]0x95EA, [char]0x73B0)
    $freezeNoticeLabel = [string]::Concat([char]0x51BB, [char]0x7ED3)
    $targetNoticeLabel = [string]::Concat([char]0x76EE, [char]0x6807)
    $skillFailureNoticeObserved = (
      $transientNoticeProbe -and
      $transientNoticeProbe.available -eq $true -and
      ($noticeTextJoined.Contains($blinkNoticeLabel) -or $noticeTextJoined.Contains($freezeNoticeLabel)) -and
      $noticeTextJoined.Contains($targetNoticeLabel)
    )

    $deferTargetedNoopAssert = {
      param(
        [bool]$Passed,
        [Parameter(Mandatory = $true)][string]$Message
      )

      if (-not $Passed) {
        $targetedSkillNoopAssertionFailures.Add($Message) | Out-Null
      }
    }

    & $deferTargetedNoopAssert ($targetedInputSkillTapCount -ge 2) "TargetedSkillNoopPressure did not dispatch both targeted skill prepare taps."
    & $deferTargetedNoopAssert (($targetedInputSkillKeys -contains "q") -and ($targetedInputSkillKeys -contains "r")) "TargetedSkillNoopPressure did not dispatch both Q and R targeted skill prepare taps."
    & $deferTargetedNoopAssert ($targetedInputConfirmCount -ge 2) "TargetedSkillNoopPressure did not dispatch both targeted skill confirm clicks."
    & $deferTargetedNoopAssert (
      $null -ne $commandFetchProbe -and
      $commandFetchProbe.available -eq $true -and
      [int](Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "requestCount" -DefaultValue 0) -gt 0
    ) "TargetedSkillNoopPressure did not observe authoritative command fetch requests."
    & $deferTargetedNoopAssert ($castBlinkTrueCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe castBlink=true."
    & $deferTargetedNoopAssert ($castFreezeTrueCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe castFreeze=true."
    & $deferTargetedNoopAssert ($skillOutcomeCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe backend skill outcomes."
    & $deferTargetedNoopAssert ($blinkNoopCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe a Blink noop outcome."
    & $deferTargetedNoopAssert ($freezeNoopCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe a Freeze noop outcome."
    & $deferTargetedNoopAssert ($blinkGeometryNoopCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe a Blink out_of_range or invalid_target noop outcome."
    & $deferTargetedNoopAssert ($freezeGeometryNoopCount -gt 0) "TargetedSkillNoopPressure command fetch probe did not observe a Freeze out_of_range or invalid_target noop outcome."
    & $deferTargetedNoopAssert ($skillNoopWithoutReasonCount -eq 0) "TargetedSkillNoopPressure command fetch probe observed a noop skill outcome without a reason."
    & $deferTargetedNoopAssert (
      $transientNoticeProbe -and
      $transientNoticeProbe.available -eq $true
    ) "TargetedSkillNoopPressure transient notice probe was not available."
    & $deferTargetedNoopAssert ($skillFailureNoticeObserved) "TargetedSkillNoopPressure did not observe a Chinese Blink/Freeze failure transient notice."
    foreach ($failure in $targetedSkillNoopAssertionFailures) {
      $warnings.Add("TargetedSkillNoopPressure deferred assertion failure: $failure") | Out-Null
    }

    $targetedSkillNoopMetric = [ordered]@{
      passed = ($targetedSkillNoopAssertionFailures.Count -eq 0)
      assertionFailures = @($targetedSkillNoopAssertionFailures)
      targetedSkillTapCount = $targetedInputSkillTapCount
      targetedSkillKeys = @($targetedInputSkillKeys)
      targetedConfirmCount = $targetedInputConfirmCount
      commandFetch = [ordered]@{
        available = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "available"
        requestCount = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "requestCount"
        castBlinkTrueCount = $castBlinkTrueCount
        castFreezeTrueCount = $castFreezeTrueCount
        blinkNoopCount = $blinkNoopCount
        freezeNoopCount = $freezeNoopCount
        blinkGeometryNoopCount = $blinkGeometryNoopCount
        freezeGeometryNoopCount = $freezeGeometryNoopCount
        skillOutcomeCount = $skillOutcomeCount
        skillOutcomeReasons = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "skillOutcomeReasons"
        blinkNoopReasons = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "blinkNoopReasons"
        freezeNoopReasons = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "freezeNoopReasons"
        skillNoopWithoutReasonCount = $skillNoopWithoutReasonCount
        responseParseFailedCount = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "responseParseFailedCount"
        responseCommandStatusCounts = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "responseCommandStatusCounts"
        responseCommandReasonCounts = Get-ObjectPropertyValue -InputObject $commandFetchProbe -Name "responseCommandReasonCounts"
      }
      transientNotice = [ordered]@{
        available = Get-ObjectPropertyValue -InputObject $transientNoticeProbe -Name "available"
        textCount = Get-ObjectPropertyValue -InputObject $transientNoticeProbe -Name "textCount"
        skillFailureNoticeObserved = $skillFailureNoticeObserved
        texts = @($noticeTexts)
      }
    }
  }

  $weaponSwitchPressureMetric = $null
  $weaponSwitchPressureAssertionFailures = [System.Collections.Generic.List[string]]::new()
  if ($Scenario -eq "WeaponSwitchPressure") {
    $weaponSwitchWheelCount = [int](Get-ObjectPropertyValue -InputObject $input -Name "weaponSwitchWheelCount" -DefaultValue 0)
    $weaponSwitchProbe = Invoke-CdpEvaluate -Client $clientA -Expression @'
(() => {
  const root = window.__slayDemoBattleDiagnostics || {};
  const edgeEvents = Array.isArray(root.authoritativeInput?.edgeEvents)
    ? root.authoritativeInput.edgeEvents
    : [];
  const snapshots = Array.isArray(root.authoritativeInput?.snapshots)
    ? root.authoritativeInput.snapshots
    : [];
  const commandSamples = Array.isArray(root.authoritativeNetwork?.commandSubmit?.recentSamples)
    ? root.authoritativeNetwork.commandSubmit.recentSamples
    : [];
  const commandSubmit = root.authoritativeNetwork?.commandSubmit || null;
  const wheelSwitchEvents = edgeEvents.filter((event) => event && event.edge === "wheelSwitch");
  const switchSnapshots = snapshots.filter((sample) =>
    sample &&
    (
      sample.switchWeaponDirection === -1 ||
      sample.switchWeaponDirection === 1 ||
      sample.switchWeaponIndex !== null
    )
  );
  const switchCommands = commandSamples.filter((sample) =>
    sample &&
    (
      sample.switchWeaponDirection === -1 ||
      sample.switchWeaponDirection === 1 ||
      sample.switchWeaponIndex !== null
    )
  );
  return {
    edgeEventCount: edgeEvents.length,
    wheelSwitchEventCount: wheelSwitchEvents.length,
    snapshotCount: snapshots.length,
    switchSnapshotCount: switchSnapshots.length,
    commandSampleCount: commandSamples.length,
    switchCommandCount: switchCommands.length,
    commandSubmitCount: Number(commandSubmit?.count || 0),
    commandAcceptedCount: Number(commandSubmit?.acceptedCount || 0),
    commandFailedCount: Number(commandSubmit?.failedCount || 0)
  };
})()
'@ -TimeoutSeconds 8
    $wheelSwitchEventCount = [int](Get-ObjectPropertyValue -InputObject $weaponSwitchProbe -Name "wheelSwitchEventCount" -DefaultValue 0)
    $switchSnapshotCount = [int](Get-ObjectPropertyValue -InputObject $weaponSwitchProbe -Name "switchSnapshotCount" -DefaultValue 0)
    $switchCommandCount = [int](Get-ObjectPropertyValue -InputObject $weaponSwitchProbe -Name "switchCommandCount" -DefaultValue 0)
    $switchCommandAcceptedCount = [int](Get-ObjectPropertyValue -InputObject $weaponSwitchProbe -Name "commandAcceptedCount" -DefaultValue 0)
    $switchCommandFailedCount = [int](Get-ObjectPropertyValue -InputObject $weaponSwitchProbe -Name "commandFailedCount" -DefaultValue 0)
    $beforeSwitchPlayer = if ($null -ne $beforeState) {
      Find-StatePlayer -State $beforeState -Handle $clientAHandle -PlayerId $contextBeforeA.localAuthoritativePlayerId
    } else {
      $null
    }
    $afterSwitchPlayer = if ($null -ne $afterState) {
      Find-StatePlayer -State $afterState -Handle $clientAHandle -PlayerId $contextBeforeA.localAuthoritativePlayerId
    } else {
      $null
    }
    $beforeWeaponIndex = Get-ObjectPropertyValue -InputObject $beforeSwitchPlayer -Name "currentWeaponIndex"
    $afterWeaponIndex = Get-ObjectPropertyValue -InputObject $afterSwitchPlayer -Name "currentWeaponIndex"
    $beforeWeaponKind = Get-ObjectPropertyValue -InputObject $beforeSwitchPlayer -Name "currentWeaponKind"
    $afterWeaponKind = Get-ObjectPropertyValue -InputObject $afterSwitchPlayer -Name "currentWeaponKind"
    $weaponIndexChanged = (
      $null -ne $beforeWeaponIndex -and
      $null -ne $afterWeaponIndex -and
      [int]$beforeWeaponIndex -ne [int]$afterWeaponIndex
    )

    $deferWeaponSwitchAssert = {
      param(
        [bool]$Passed,
        [Parameter(Mandatory = $true)][string]$Message
      )

      if (-not $Passed) {
        $weaponSwitchPressureAssertionFailures.Add($Message) | Out-Null
      }
    }

    & $deferWeaponSwitchAssert ($weaponSwitchWheelCount -ge 2) "WeaponSwitchPressure did not dispatch both wheel events."
    & $deferWeaponSwitchAssert ($wheelSwitchEventCount -gt 0) "WeaponSwitchPressure did not observe frontend wheelSwitch edge events."
    & $deferWeaponSwitchAssert ($switchSnapshotCount -gt 0) "WeaponSwitchPressure did not observe switchWeaponDirection in fallback snapshots."
    & $deferWeaponSwitchAssert ($switchCommandCount -gt 0) "WeaponSwitchPressure did not observe switchWeaponDirection in channel command diagnostics."
    & $deferWeaponSwitchAssert ($switchCommandAcceptedCount -gt 0) "WeaponSwitchPressure did not observe accepted channel command submissions."
    & $deferWeaponSwitchAssert ($switchCommandFailedCount -eq 0) "WeaponSwitchPressure channel command diagnostics observed failed command submissions."
    & $deferWeaponSwitchAssert ($weaponIndexChanged) "WeaponSwitchPressure did not observe authoritative currentWeaponIndex change."
    foreach ($failure in $weaponSwitchPressureAssertionFailures) {
      $warnings.Add("WeaponSwitchPressure deferred assertion failure: $failure") | Out-Null
    }

    $weaponSwitchPressureMetric = [ordered]@{
      passed = ($weaponSwitchPressureAssertionFailures.Count -eq 0)
      assertionFailures = @($weaponSwitchPressureAssertionFailures)
      wheelDispatchCount = $weaponSwitchWheelCount
      wheelSwitchEventCount = $wheelSwitchEventCount
      switchSnapshotCount = $switchSnapshotCount
      switchCommandCount = $switchCommandCount
      commandAcceptedCount = $switchCommandAcceptedCount
      commandFailedCount = $switchCommandFailedCount
      beforeWeaponIndex = $beforeWeaponIndex
      afterWeaponIndex = $afterWeaponIndex
      beforeWeaponKind = $beforeWeaponKind
      afterWeaponKind = $afterWeaponKind
      weaponIndexChanged = $weaponIndexChanged
      probe = $weaponSwitchProbe
    }
  }

  $dualClientPressureMetric = $null
  if ($Scenario -eq "DualClientPressure") {
    Assert-Condition ($battleIdentity.sameBattleProven -eq $true) "DualClientPressure requires sameBattle=true, but same battle was not proven."

    $inputA = Get-ObjectPropertyValue -InputObject $input -Name "clientA"
    $inputB = Get-ObjectPropertyValue -InputObject $input -Name "clientB"
    $commandFetchProbeA = Get-ObjectPropertyValue -InputObject $inputA -Name "commandFetchProbe"
    $commandFetchProbeB = Get-ObjectPropertyValue -InputObject $inputB -Name "commandFetchProbe"
    $commandEvidenceA = (
      $null -ne $commandFetchProbeA -and
      $commandFetchProbeA.available -eq $true -and
      [int]$commandFetchProbeA.requestCount -gt 0
    )
    $commandEvidenceB = (
      $null -ne $commandFetchProbeB -and
      $commandFetchProbeB.available -eq $true -and
      [int]$commandFetchProbeB.requestCount -gt 0
    )

    $authoritativeActionMetricB = $null
    if ($null -ne $beforeState -and $null -ne $afterState) {
      $authoritativeActionMetricB = Measure-StateActionEffect `
        -BeforeState $beforeState `
        -AfterState $afterState `
        -Handle $clientBHandle `
        -PlayerId $contextBeforeB.localAuthoritativePlayerId
      if ($authoritativeActionMetricB.available -eq $true -and $authoritativeActionMetricB.passed -eq $true) {
        $authoritativeActionMetricB = Add-ActionConfirmationMetadata `
          -Metric $authoritativeActionMetricB `
          -InputStartWallMs $inputStartWallMs `
          -ConfirmWallMs ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) `
          -Attempt 1 `
          -ProbeWindow "postInputState" `
          -CapturedDuringInput $false
      }
    }
    if ($null -ne $beforeState -and -not [string]::IsNullOrWhiteSpace($battleIdentity.battleId)) {
      if ($null -eq $authoritativeActionMetricB -or $authoritativeActionMetricB.available -ne $true -or $authoritativeActionMetricB.passed -ne $true) {
        $authoritativeActionMetricB = Wait-AuthoritativeActionEffect `
          -BackendBase $backendBase `
          -BattleId $battleIdentity.battleId `
          -BeforeState $beforeState `
          -Handle $clientBHandle `
          -PlayerId $contextBeforeB.localAuthoritativePlayerId `
          -InputStartWallMs $inputStartWallMs `
          -ProbeWindow "postInputFallback" `
          -CapturedDuringInput $false
      }
    }

    $localStorageActionMetricB = Measure-LocalStorageActionEffect `
      -BeforeContext $contextBeforeB `
      -AfterContext $contextAfterB `
      -Handle $clientBHandle
    $actionMetricB = $authoritativeActionMetricB
    $actionMetricBSource = "api.authoritativeBattleState"
    if ($null -eq $actionMetricB -or $actionMetricB.available -ne $true) {
      $actionMetricB = $localStorageActionMetricB
      $actionMetricBSource = "localStorage.activeBattleSession"
    }
    $actionEvidenceA = ($null -ne $actionMetric -and $actionMetric.available -eq $true -and $actionMetric.passed -eq $true)
    $actionEvidenceB = ($null -ne $actionMetricB -and $actionMetricB.available -eq $true -and $actionMetricB.passed -eq $true)

    Assert-Condition ($commandEvidenceA -or $actionEvidenceA) "DualClientPressure clientA did not produce command fetch or input-effect evidence."
    Assert-Condition ($commandEvidenceB -or $actionEvidenceB) "DualClientPressure clientB did not produce command fetch or input-effect evidence."
    Assert-Condition ($rafA.longTasks.available -eq $true) "DualClientPressure clientA long task phase statistics are unavailable."
    Assert-Condition ($rafB.longTasks.available -eq $true) "DualClientPressure clientB long task phase statistics are unavailable."
    $rafAInputPhase = Get-ObjectPropertyValue -InputObject $rafA.byDiagnosticPhase -Name "input"
    $rafBInputPhase = Get-ObjectPropertyValue -InputObject $rafB.byDiagnosticPhase -Name "input"
    Assert-Condition ([int](Get-ObjectPropertyValue -InputObject $rafAInputPhase -Name "frameCount" -DefaultValue 0) -gt 0) "DualClientPressure clientA input RAF phase has no frames."
    Assert-Condition ([int](Get-ObjectPropertyValue -InputObject $rafBInputPhase -Name "frameCount" -DefaultValue 0) -gt 0) "DualClientPressure clientB input RAF phase has no frames."

    $dualClientPressureMetric = [ordered]@{
      sameBattle = [bool]$battleIdentity.sameBattleProven
      inputWindow = [ordered]@{
        clientA = [ordered]@{
          inputStartPageMs = Get-ObjectPropertyValue -InputObject $inputA -Name "firstInputEventPageMs"
          inputEndPageMs = Get-ObjectPropertyValue -InputObject $inputA -Name "inputEndPageMs"
        }
        clientB = [ordered]@{
          inputStartPageMs = Get-ObjectPropertyValue -InputObject $inputB -Name "firstInputEventPageMs"
          inputEndPageMs = Get-ObjectPropertyValue -InputObject $inputB -Name "inputEndPageMs"
        }
      }
      inputEvidence = [ordered]@{
        clientA = [ordered]@{
          commandFetch = $commandFetchProbeA
          commandEvidence = $commandEvidenceA
          actionEvidence = $actionEvidenceA
          inputEventProbe = Get-ObjectPropertyValue -InputObject $inputA -Name "inputEventProbe"
        }
        clientB = [ordered]@{
          commandFetch = $commandFetchProbeB
          commandEvidence = $commandEvidenceB
          actionEvidence = $actionEvidenceB
          inputEventProbe = Get-ObjectPropertyValue -InputObject $inputB -Name "inputEventProbe"
        }
      }
      action = [ordered]@{
        clientA = [ordered]@{
          source = $actionMetricSource
          metric = $actionMetric
        }
        clientB = [ordered]@{
          source = $actionMetricBSource
          metric = $actionMetricB
        }
      }
    }
  }

  $clientATruthPlayerId = $contextBeforeA.localAuthoritativePlayerId
  if ([string]::IsNullOrWhiteSpace($clientATruthPlayerId)) {
    $clientATruthPlayerId = Get-ObjectPropertyValue -InputObject $authoritativeActionMetric -Name "playerId" -DefaultValue ""
  }
  if ([string]::IsNullOrWhiteSpace($clientATruthPlayerId)) {
    $clientATruthPlayerId = Get-ObjectPropertyValue -InputObject $actionMetric -Name "playerId" -DefaultValue ""
  }

  $displayTruthMetric = New-DisplayTruthMetric `
    -Context $contextAfterA `
    -State $afterState `
    -Handle $clientAHandle `
    -PlayerId $clientATruthPlayerId
  if ($displayTruthMetric -eq "unavailable") {
    $warnings.Add("displayTruthMetric=unavailable; renderer display pose is not exposed to test tooling without business-code globals.") | Out-Null
  }
  $visibleDisplayTruthMetric = New-VisibleDisplayTruthMetric `
    -Vision $visionTruthAfterA `
    -State $afterState `
    -Handle $clientAHandle `
    -PlayerId $clientATruthPlayerId
  if ($visibleDisplayTruthMetric -eq "unavailable") {
    $warnings.Add("visibleDisplayTruthMetric=unavailable; renderer display pose or authoritative player state was unavailable.") | Out-Null
  }
  $inputFeedbackLatencyMetric = New-InputFeedbackLatencyMetric `
    -InputStartWallMs $inputStartWallMs `
    -InputEndWallMs $inputEndWallMs `
    -AuthoritativeMetric $authoritativeActionMetric `
    -PageSnapshotMetric $pageSnapshotActionMetric
  $localHeroCorrectionAfterA = Read-LocalHeroCorrectionDiagnostics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $localHeroCorrectionAfterB = Read-LocalHeroCorrectionDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings
  $authoritativeLocalHeroReplayAfterA = Read-AuthoritativeLocalHeroReplayDiagnostics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $authoritativeLocalHeroReplayAfterB = Read-AuthoritativeLocalHeroReplayDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings
  $localHeroCorrectionMetric = [ordered]@{
    clientA = New-LocalHeroCorrectionMetric -Before $localHeroCorrectionBeforeA -After $localHeroCorrectionAfterA
    clientB = New-LocalHeroCorrectionMetric -Before $localHeroCorrectionBeforeB -After $localHeroCorrectionAfterB
    localFeedbackLatencyMetric = $localFeedbackLatencyMetric
  }
  $authoritativeLocalHeroReplayMetric = [ordered]@{
    clientA = New-AuthoritativeLocalHeroReplayMetric `
      -Before $authoritativeLocalHeroReplayBeforeA `
      -After $authoritativeLocalHeroReplayAfterA
    clientB = New-AuthoritativeLocalHeroReplayMetric `
      -Before $authoritativeLocalHeroReplayBeforeB `
      -After $authoritativeLocalHeroReplayAfterB
  }
  $hitDisputeSamples = New-HitDisputeSamples `
    -BeforeRemoteView $remoteViewBeforeB `
    -AfterRemoteView $remoteViewHitDisputeAfterB `
    -BeforeState $beforeState `
    -AfterState $afterState `
    -BattleId $battleIdentity.battleId `
    -ObserverLabel "clientB" `
    -ObserverHandle $clientBHandle `
    -ObserverPlayerId $contextBeforeB.localAuthoritativePlayerId `
    -InputStartWallMs $inputStartWallMs `
    -InputEndWallMs $inputEndWallMs `
    -RelevantOwnerPlayerIds @(
      $contextBeforeA.localAuthoritativePlayerId,
      $contextBeforeB.localAuthoritativePlayerId,
      $contextAfterA.localAuthoritativePlayerId,
      $contextAfterB.localAuthoritativePlayerId
    )
  $hitDisputeAssertionFailures = New-HitDisputeAssertionFailures -HitDisputeSamples $hitDisputeSamples
  if (
    (Get-ObjectPropertyValue -InputObject $hitDisputeSamples -Name "available") -ne $true -and
    [string]::IsNullOrWhiteSpace("" + (Get-ObjectPropertyValue -InputObject $hitDisputeSamples -Name "serverSource" -DefaultValue "")) -and
    [string]::IsNullOrWhiteSpace("" + (Get-ObjectPropertyValue -InputObject $hitDisputeSamples -Name "clientSource" -DefaultValue ""))
  ) {
    $warnings.Add("hitDisputeSamples unavailable: both server projectileTerminals and client projectile terminal diagnostics were unavailable.") | Out-Null
  }
  foreach ($failure in $hitDisputeAssertionFailures) {
    $warnings.Add("HitDispute deferred assertion failure: $failure") | Out-Null
  }
  $vfxMetric = [ordered]@{
    clientA = New-VfxClientMetric -Before $vfxBeforeA -After $vfxAfterA
    clientB = New-VfxClientMetric -Before $vfxBeforeB -After $vfxAfterB
  }
  $hudMetric = [ordered]@{
    clientA = New-HudClientMetric -Before $hudBeforeA -After $hudAfterA
    clientB = New-HudClientMetric -Before $hudBeforeB -After $hudAfterB
  }
  $authoritativeInputDiagnosticsA = Invoke-CdpEvaluate -Client $clientA -Expression @'
(() => {
  const root = window.__slayDemoBattleDiagnostics || {};
  return {
    authoritativeInput: root.authoritativeInput || null,
    authoritativePreparedInput: root.authoritativePreparedInput || null
  };
})()
'@ -TimeoutSeconds 8
  $authoritativeNetworkAfterA = Read-AuthoritativeNetworkDiagnostics -Client $clientA -Phase "afterInput" -Warnings $warnings
  $authoritativeNetworkAfterB = Read-AuthoritativeNetworkDiagnostics -Client $clientB -Phase "afterInput" -Warnings $warnings
  $authoritativeNetworkMetric = [ordered]@{
    clientA = New-AuthoritativeNetworkReadSummary -ReadResult $authoritativeNetworkAfterA
    clientB = New-AuthoritativeNetworkReadSummary -ReadResult $authoritativeNetworkAfterB
  }
  $clientBVisionInputStartPageMs = $motionInputStartPageMs
  $clientBVisionInputEndPageMs = Get-ObjectPropertyValue -InputObject $input -Name "inputEndPageMs"
  if ($Scenario -eq "DualClientPressure") {
    $inputBForVision = Get-ObjectPropertyValue -InputObject $input -Name "clientB"
    $clientBVisionInputStartPageMs = Get-ObjectPropertyValue -InputObject $inputBForVision -Name "firstMovementInputEventPageMs"
    if ($null -eq $clientBVisionInputStartPageMs) {
      $clientBVisionInputStartPageMs = Get-ObjectPropertyValue -InputObject $inputBForVision -Name "firstInputEventPageMs"
    }
    if ($null -eq $clientBVisionInputStartPageMs) {
      $clientBVisionInputStartPageMs = Get-ObjectPropertyValue -InputObject $inputBForVision -Name "inputDispatchStartPageMs"
    }
    $clientBVisionInputEndPageMs = Get-ObjectPropertyValue -InputObject $inputBForVision -Name "inputEndPageMs"
  }
  $visionMetric = [ordered]@{
    clientA = New-VisionClientMetric `
      -BeforeVision $visionBeforeA `
      -AfterVision $visionAfterA `
      -BeforeLocalFeedback $localFeedbackBeforeA `
      -AfterLocalFeedback $localFeedbackAfterA `
      -InputStartPageMs $motionInputStartPageMs `
      -InputEndPageMs (Get-ObjectPropertyValue -InputObject $input -Name "inputEndPageMs")
    clientB = New-VisionClientMetric `
      -BeforeVision $visionBeforeB `
      -AfterVision $visionAfterB `
      -BeforeLocalFeedback $localFeedbackBeforeB `
      -AfterLocalFeedback $localFeedbackAfterB `
      -InputStartPageMs $clientBVisionInputStartPageMs `
      -InputEndPageMs $clientBVisionInputEndPageMs
  }

  $summary = [ordered]@{
    ok = ($targetedSkillPressureAssertionFailures.Count -eq 0 -and $targetedSkillNoopAssertionFailures.Count -eq 0 -and $weaponSwitchPressureAssertionFailures.Count -eq 0 -and $hitDisputeAssertionFailures.Count -eq 0)
    smoke = "BP-28E render-feel"
    frontendUrl = $frontendBase
    backendUrl = $backendBase
    browser = $browserExe
    headless = (-not [bool]$Headful)
    disableGpu = [bool]$DisableGpu
    scenario = $Scenario
    modeId = $ModeId
    preInputSettleMs = $PreInputSettleMs
    inputDurationMs = $InputDurationMs
    sameBattle = [bool]$battleIdentity.sameBattleProven
    window = [ordered]@{
      width = $WindowWidth
      height = $WindowHeight
      clientA = [ordered]@{
        x = $ClientAWindowX
        y = $ClientAWindowY
      }
      clientB = [ordered]@{
        x = $ClientBWindowX
        y = $ClientBWindowY
      }
    }
    keepBrowsersOpen = [bool]$KeepBrowsersOpen
    clients = @(
      [ordered]@{
        label = "clientA"
        handle = $clientAHandle
        playing = $playingA.playing
        battleId = $contextBeforeA.battleId
        playerId = $contextBeforeA.localAuthoritativePlayerId
      },
      [ordered]@{
        label = "clientB"
        handle = $clientBHandle
        playing = $playingB.playing
        battleId = $contextBeforeB.battleId
        playerId = $contextBeforeB.localAuthoritativePlayerId
      }
    )
    battle = [ordered]@{
      battleId = $battleIdentity.battleId
      source = $battleIdentity.source
      sameBattleProven = $battleIdentity.sameBattleProven
      expectedMapId = Resolve-ExpectedMapIdForMode -ModeId $ModeId
      mapId = Get-ObjectPropertyValue -InputObject $beforeState -Name "mapId"
    }
    input = $input
    preInputSampleMs = $preInputSampleMs
    raf = [ordered]@{
      clientA = $rafA
      clientB = $rafB
    }
    cdpPerformance = [ordered]@{
      clientA = [ordered]@{
        enable = $performanceEnableA
        beforeInput = $performanceBeforeInputA
        afterInput = $performanceAfterInputA
      }
      clientB = [ordered]@{
        enable = $performanceEnableB
        beforeInput = $performanceBeforeInputB
        afterInput = $performanceAfterInputB
      }
    }
    performanceDelta = [ordered]@{
      clientA = New-CdpPerformanceDelta -BeforeInput $performanceBeforeInputA -AfterInput $performanceAfterInputA
      clientB = New-CdpPerformanceDelta -BeforeInput $performanceBeforeInputB -AfterInput $performanceAfterInputB
    }
    actionMetricSource = $actionMetricSource
    actionMetric = $actionMetric
    inputFeedbackLatencyMetric = $inputFeedbackLatencyMetric
    localFeedbackLatencyMetric = $localFeedbackLatencyMetric
    remoteViewMetric = $remoteViewMetric
    hitDisputeSamples = $hitDisputeSamples
    hitDisputeAssertionFailures = @($hitDisputeAssertionFailures)
    displayTruthMetric = $displayTruthMetric
    visibleDisplayTruthMetric = $visibleDisplayTruthMetric
    targetedSkillPressureMetric = $targetedSkillPressureMetric
    targetedSkillPressureAssertionFailures = @($targetedSkillPressureAssertionFailures)
    targetedSkillNoopMetric = $targetedSkillNoopMetric
    targetedSkillNoopAssertionFailures = @($targetedSkillNoopAssertionFailures)
    weaponSwitchPressureMetric = $weaponSwitchPressureMetric
    weaponSwitchPressureAssertionFailures = @($weaponSwitchPressureAssertionFailures)
    dualClientPressureMetric = $dualClientPressureMetric
    localHeroCorrectionMetric = $localHeroCorrectionMetric
    authoritativeLocalHeroReplayMetric = $authoritativeLocalHeroReplayMetric
    authoritativeNetworkMetric = $authoritativeNetworkMetric
    visionMetric = $visionMetric
    vfxMetric = $vfxMetric
    hudMetric = $hudMetric
    diagnostics = [ordered]@{
      clientA = [ordered]@{
        authoritativeInput = Get-ObjectPropertyValue -InputObject $authoritativeInputDiagnosticsA -Name "authoritativeInput"
        authoritativePreparedInput = Get-ObjectPropertyValue -InputObject $authoritativeInputDiagnosticsA -Name "authoritativePreparedInput"
        authoritativeNetwork = $authoritativeNetworkMetric.clientA
      }
      clientB = [ordered]@{
        authoritativeNetwork = $authoritativeNetworkMetric.clientB
      }
    }
    warnings = @($warnings)
  }

  $summaryJson = $summary | ConvertTo-Json -Depth 24
  if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
    $summaryParent = Split-Path -Parent $SummaryPath
    if (-not [string]::IsNullOrWhiteSpace($summaryParent)) {
      New-Item -ItemType Directory -Force -Path $summaryParent | Out-Null
    }
    Set-Content -LiteralPath $SummaryPath -Value $summaryJson -Encoding UTF8
  }

  Write-Host ""
  Write-Host $summaryJson
  if ($targetedSkillPressureAssertionFailures.Count -gt 0) {
    throw "TargetedSkillPressure assertions failed after summary write: $($targetedSkillPressureAssertionFailures -join '; ')"
  }
  if ($targetedSkillNoopAssertionFailures.Count -gt 0) {
    throw "TargetedSkillNoopPressure assertions failed after summary write: $($targetedSkillNoopAssertionFailures -join '; ')"
  }
  if ($weaponSwitchPressureAssertionFailures.Count -gt 0) {
    throw "WeaponSwitchPressure assertions failed after summary write: $($weaponSwitchPressureAssertionFailures -join '; ')"
  }
  if ($hitDisputeAssertionFailures.Count -gt 0) {
    throw "HitDispute assertions failed after summary write: $($hitDisputeAssertionFailures -join '; ')"
  }
} finally {
  Close-Cdp -Client $clientA
  Close-Cdp -Client $clientB
  if (-not $KeepBrowsersOpen) {
    Stop-SmokeProcess -Process $processA
    Stop-SmokeProcess -Process $processB
  } else {
    Write-Host "BP-28E render-feel smoke: leaving browser windows open because -KeepBrowsersOpen was set."
  }
  if (-not $KeepBrowsersOpen) {
    Invoke-SmokeAccountCleanup `
      -WorkspaceRoot $workspaceRoot `
      -RuntimeDir $runtimeDir `
      -Handles @($clientAHandle, $clientBHandle) `
      -StorageMode $backendStorageMode `
      -Warnings $warnings
  }
}
