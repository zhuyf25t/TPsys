[CmdletBinding()]
param(
  [string]$FrontendUrl = "http://127.0.0.1:5173",
  [string]$BackendApiUrl = "http://127.0.0.1:8080/api",
  [string]$Password = "zombie-browser-pass",
  [string]$BrowserPath,
  [int]$PlayingTimeoutSeconds = 45,
  [int]$SoakSeconds = 0,
  [switch]$CaptureVisualEvidence,
  [switch]$KeepProfiles
)

$ErrorActionPreference = "Stop"

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

  $normalizedBase = Normalize-BaseUrl $Base
  if ($Path.StartsWith("/")) {
    return "$normalizedBase$Path"
  }

  return "$normalizedBase/$Path"
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

function Test-HasField {
  param(
    [object]$Value,
    [string]$Field
  )

  if ($null -eq $Value) {
    return $false
  }

  return $null -ne ($Value.PSObject.Properties | Where-Object { $_.Name -ceq $Field } | Select-Object -First 1)
}

function Read-Array {
  param(
    [object]$Value,
    [string]$Field
  )

  Assert-Condition (Test-HasField $Value $Field) "Missing array field '$Field'."
  $arrayValue = $Value.$Field
  if ($null -eq $arrayValue) {
    return @()
  }

  return @($arrayValue | ForEach-Object { $_ })
}

function Invoke-SmokeJson {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Uri,
    [object]$Body = $null,
    [int]$TimeoutSec = 10
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
    $detail = $_.Exception.Message
    if (-not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
      $detail = "$detail :: $($_.ErrorDetails.Message)"
    }
    throw "$Method $Uri failed. $detail"
  }
}

function Invoke-BattleMessage {
  param(
    [Parameter(Mandatory = $true)][string]$ApiBase,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$UserToken,
    [Parameter(Mandatory = $true)][hashtable]$Body
  )

  $payload = @{}
  foreach ($key in $Body.Keys) {
    $payload[$key] = $Body[$key]
  }
  $payload.userToken = $UserToken

  Invoke-SmokeJson -Method "POST" -Uri (Join-TestUrl -Base $ApiBase -Path "/$Name") -Body $payload
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
    [Parameter(Mandatory = $true)][string]$Skin
  )

  $encodedHandle = [System.Uri]::EscapeDataString($Handle)
  $encodedPassword = [System.Uri]::EscapeDataString($PasswordValue)
  $encodedSkin = [System.Uri]::EscapeDataString($Skin)
  $encodedTarget = [System.Uri]::EscapeDataString("/battle?new=1&diagnostics=1")
  return "$(Normalize-BaseUrl $BaseUrl)/bp14-client.html?handle=$encodedHandle&password=$encodedPassword&skin=$encodedSkin&target=$encodedTarget"
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
        "zombie browser smoke complete",
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

  function compactSession(key, parsed) {
    const snapshot = parsed && parsed.snapshot ? parsed.snapshot : null;
    return {
      key,
      battleId: typeof parsed?.battleId === "string" ? parsed.battleId.trim() : "",
      sharedAuthoritativeRuntime: parsed?.sharedAuthoritativeRuntime === true,
      localAuthoritativePlayerId:
        typeof parsed?.localAuthoritativePlayerId === "string" ? parsed.localAuthoritativePlayerId.trim() : "",
      localAuthoritativeTicketId:
        typeof parsed?.localAuthoritativeTicketId === "string" ? parsed.localAuthoritativeTicketId.trim() : "",
      owner: {
        handle: typeof parsed?.owner?.handle === "string" ? parsed.owner.handle : "",
        sessionToken: typeof parsed?.owner?.sessionToken === "string" ? parsed.owner.sessionToken : ""
      },
      elapsedMs: Number.isFinite(snapshot?.elapsedMs) ? snapshot.elapsedMs : null
    };
  }

  const storageSessions = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (!key || !key.startsWith("slay-demo.active-battle-session.v2.")) {
      continue;
    }
    const parsed = safeParseJson(window.localStorage.getItem(key));
    if (parsed) {
      storageSessions.push(compactSession(key, parsed));
    }
  }
  storageSessions.sort((left, right) => left.key.localeCompare(right.key));
  const primarySession = storageSessions.find((session) => session.battleId) || null;
  const shell = document.querySelector(".arena-shell");
  const canvas = document.querySelector("canvas");
  const timer = document.querySelector(".hud-timer")?.textContent?.trim() || "";
  const battleId = primarySession?.battleId || "";
  return {
    href: window.location.href,
    readyState: document.readyState,
    playing: Boolean(shell && shell.classList.contains("arena-shell--playing")),
    shellClass: shell ? shell.className : "",
    inBattle: Boolean(battleId && canvas && timer),
    timer,
    authHandle: window.localStorage.getItem("slay-demo.auth.session.v1") || "",
    sessionToken: window.localStorage.getItem("slay-demo.auth.session-token.v1") || "",
    battleId,
    localAuthoritativePlayerId: primarySession?.localAuthoritativePlayerId || "",
    localAuthoritativeTicketId: primarySession?.localAuthoritativeTicketId || "",
    sharedAuthoritativeRuntime: primarySession?.sharedAuthoritativeRuntime === true,
    activeSessionCount: storageSessions.length,
    storageSessions,
    canvas: canvas ? { width: canvas.width, height: canvas.height } : null,
    bodyText: document.body ? document.body.textContent.trim().slice(0, 260) : ""
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
  $lastContext = $null
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $lastContext = Get-PageBattleContext -Client $Client
    if ($lastContext.inBattle -eq $true -and -not [string]::IsNullOrWhiteSpace($lastContext.sessionToken)) {
      return $lastContext
    }
    Start-Sleep -Milliseconds 300
  }

  $serialized = if ($null -eq $lastContext) { "<none>" } else { $lastContext | ConvertTo-Json -Depth 8 -Compress }
  throw "$Label did not enter a persisted browser battle within ${TimeoutSeconds}s. Last context: $serialized"
}

function Read-BattleState {
  param(
    [Parameter(Mandatory = $true)][string]$ApiBase,
    [Parameter(Mandatory = $true)][string]$SessionToken,
    [Parameter(Mandatory = $true)][string]$BattleId
  )

  Invoke-BattleMessage -ApiBase $ApiBase -Name "battlestateread" -UserToken $SessionToken -Body @{
    battleId = $BattleId
  }
}

function Read-PlayerById {
  param(
    [Parameter(Mandatory = $true)]$State,
    [Parameter(Mandatory = $true)][string]$PlayerId
  )

  $players = Read-Array $State "players"
  $matches = @($players | Where-Object { $_.playerId -ceq $PlayerId } | Select-Object -First 1)
  Assert-Condition ($matches.Count -eq 1) "Battle state missing player $PlayerId."
  return $matches[0]
}

function Read-SkillByKind {
  param(
    [Parameter(Mandatory = $true)]$Player,
    [Parameter(Mandatory = $true)][string]$Kind
  )

  $skills = Read-Array $Player "skills"
  $matches = @($skills | Where-Object { $_.kind -ceq $Kind } | Select-Object -First 1)
  Assert-Condition ($matches.Count -eq 1) "Player $($Player.playerId) missing skill $Kind."
  return $matches[0]
}

function Read-ProjectileEvidenceCount {
  param([Parameter(Mandatory = $true)]$State)

  $activeProjectiles = Read-Array $State "projectiles"
  $terminalProjectiles = Read-Array $State "projectileTerminals"
  return ($activeProjectiles.Count + $terminalProjectiles.Count)
}

function Send-CdpKeyEvent {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][ValidateSet("keyDown", "keyUp")][string]$Type,
    [Parameter(Mandatory = $true)][ValidateSet("w", "a", "s", "d", "e", "r")][string]$Key
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
}

function Configure-SmokeSkillSlots {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Url
  )

  $expression = @'
(() => {
  window.localStorage.setItem("slay-demo.loadoutSkillSlots.v1", JSON.stringify({
    Q: "Blink",
    E: "Dash",
    R: "Critical"
  }));
  return true;
})()
'@

  Invoke-CdpEvaluate -Client $Client -Expression $expression | Out-Null
  Invoke-CdpCommand -Client $Client -Method "Page.navigate" -Params @{ url = $Url } | Out-Null
}

function Read-HudSkillEntries {
  param([Parameter(Mandatory = $true)]$Client)

  $expression = @'
(() => Array.from(document.querySelectorAll(".hud-skill-entry")).map((entry) => ({
  key: entry.children[0]?.textContent?.trim() || "",
  icon: entry.children[1]?.textContent?.trim() || "",
  name: entry.children[2]?.textContent?.trim() || "",
  state: entry.children[3]?.textContent?.trim() || "",
  className: entry.className || "",
  fill: entry.style.getPropertyValue("--hud-skill-fill") || "",
  progress: entry.style.getPropertyValue("--hud-skill-progress") || ""
})))()
'@

  return @(Invoke-CdpEvaluate -Client $Client -Expression $expression)
}

function Assert-HudSkillProgress {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$SlotKey
  )

  $entries = Read-HudSkillEntries -Client $Client
  $slot = @($entries | Where-Object { $_.key -ceq $SlotKey } | Select-Object -First 1)
  Assert-Condition ($slot.Count -eq 1) "HUD missing skill slot $SlotKey."
  $entry = $slot[0]
  $className = [string]$entry.className
  $fill = [string]$entry.fill
  $progress = [string]$entry.progress
  Assert-Condition (
    $className.Contains("cooldown") -or $className.Contains("active")
  ) "HUD skill slot $SlotKey did not enter cooldown/active state. class=$className state=$($entry.state)"
  Assert-Condition (
    -not [string]::IsNullOrWhiteSpace($fill) -and $fill -ne "0%"
  ) "HUD skill slot $SlotKey missing progress fill. fill=$fill progress=$progress"
}

function Test-HudSkillProgress {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$SlotKey
  )

  try {
    Assert-HudSkillProgress -Client $Client -SlotKey $SlotKey
    return $true
  } catch {
    return $false
  }
}

function Wait-HudSkillProgress {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$SlotKey,
    [int]$Attempts = 10,
    [int]$DelayMilliseconds = 200
  )

  for ($attempt = 0; $attempt -lt $Attempts; $attempt += 1) {
    if (Test-HudSkillProgress -Client $Client -SlotKey $SlotKey) {
      return $true
    }
    Start-Sleep -Milliseconds $DelayMilliseconds
  }

  return $false
}

function Read-PageVisualSnapshot {
  param([Parameter(Mandatory = $true)]$Client)

  $expression = @'
(async () => {
  const rectFor = (element) => {
    if (!element) {
      return null;
    }
    const rect = element.getBoundingClientRect();
    return {
      left: Math.round(rect.left),
      top: Math.round(rect.top),
      width: Math.round(rect.width),
      height: Math.round(rect.height)
    };
  };

  const canvas = document.querySelector("canvas");
  const hudRoot = document.querySelector("#hud-root");
  const timer = document.querySelector(".hud-timer");
  const minimap = document.querySelector(".hud-minimap");
  const skillEntries = Array.from(document.querySelectorAll(".hud-skill-entry"));
  const bars = Array.from(document.querySelectorAll(".hud-bar-fill"));
  const visual = {
    readyState: document.readyState,
    canvas: canvas ? {
      width: canvas.width,
      height: canvas.height,
      rect: rectFor(canvas)
    } : null,
    hudRoot: hudRoot ? { rect: rectFor(hudRoot) } : null,
    timer: timer ? { text: timer.textContent.trim(), rect: rectFor(timer) } : null,
    minimap: minimap ? { width: minimap.width, height: minimap.height, rect: rectFor(minimap) } : null,
    skillSlots: skillEntries.length,
    bars: bars.map((bar) => ({ rect: rectFor(bar), widthStyle: bar.style.width || "" })),
    canvasSample: null
  };

  if (!canvas) {
    return visual;
  }

  try {
    const dataUrl = canvas.toDataURL("image/png");
    const image = new Image();
    image.src = dataUrl;
    await image.decode();

    const sampleWidth = 96;
    const sampleHeight = 54;
    const sampleCanvas = document.createElement("canvas");
    sampleCanvas.width = sampleWidth;
    sampleCanvas.height = sampleHeight;
    const context = sampleCanvas.getContext("2d", { willReadFrequently: true });
    context.drawImage(image, 0, 0, sampleWidth, sampleHeight);

    const data = context.getImageData(0, 0, sampleWidth, sampleHeight).data;
    const buckets = new Set();
    let nonTransparentPixels = 0;
    let minLuminance = 255;
    let maxLuminance = 0;
    for (let index = 0; index < data.length; index += 4) {
      const red = data[index];
      const green = data[index + 1];
      const blue = data[index + 2];
      const alpha = data[index + 3];
      if (alpha > 8) {
        nonTransparentPixels += 1;
        buckets.add(`${red >> 4},${green >> 4},${blue >> 4}`);
        const luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722;
        minLuminance = Math.min(minLuminance, luminance);
        maxLuminance = Math.max(maxLuminance, luminance);
      }
    }

    visual.canvasSample = {
      dataUrlLength: dataUrl.length,
      sampledPixels: sampleWidth * sampleHeight,
      nonTransparentPixels,
      distinctColorBuckets: buckets.size,
      minLuminance,
      maxLuminance
    };
  } catch (error) {
    visual.canvasSample = {
      error: error instanceof Error ? error.message : String(error)
    };
  }

  return visual;
})()
'@

  return Invoke-CdpEvaluate -Client $Client -Expression $expression -AwaitPromise
}

function Assert-PageVisualSnapshot {
  param(
    [Parameter(Mandatory = $true)]$Snapshot,
    [Parameter(Mandatory = $true)][string]$Label
  )

  Assert-Condition ($null -ne $Snapshot.canvas) "$Label visual snapshot missing game canvas."
  Assert-Condition ([int]$Snapshot.canvas.width -ge 640 -and [int]$Snapshot.canvas.height -ge 360) "$Label game canvas internal size is too small: $($Snapshot.canvas.width)x$($Snapshot.canvas.height)."
  Assert-Condition ($null -ne $Snapshot.canvas.rect -and [int]$Snapshot.canvas.rect.width -ge 640 -and [int]$Snapshot.canvas.rect.height -ge 360) "$Label game canvas display size is too small."
  Assert-Condition ($null -ne $Snapshot.hudRoot) "$Label visual snapshot missing HUD root."
  Assert-Condition ($null -ne $Snapshot.timer -and -not [string]::IsNullOrWhiteSpace([string]$Snapshot.timer.text)) "$Label visual snapshot missing HUD timer text."
  Assert-Condition ($null -ne $Snapshot.minimap -and [int]$Snapshot.minimap.width -ge 160 -and [int]$Snapshot.minimap.height -ge 160) "$Label minimap canvas is too small or missing."
  Assert-Condition ([int]$Snapshot.skillSlots -ge 3) "$Label expected at least 3 HUD skill slots, got $($Snapshot.skillSlots)."
  Assert-Condition (@($Snapshot.bars).Count -ge 2) "$Label expected health and stamina HUD bars."

  $sample = if (Test-HasField $Snapshot "screenshotSample") { $Snapshot.screenshotSample } else { $Snapshot.canvasSample }
  Assert-Condition ($null -ne $sample) "$Label missing visual pixel sample."
  Assert-Condition (-not (Test-HasField $sample "error")) "$Label visual pixel sample failed: $($sample.error)."
  Assert-Condition ([int]$sample.nonTransparentPixels -ge 4000) "$Label rendered viewport looks transparent or blank: nonTransparent=$($sample.nonTransparentPixels)."
  Assert-Condition ([int]$sample.distinctColorBuckets -ge 16) "$Label rendered viewport lacks visual detail: distinctColorBuckets=$($sample.distinctColorBuckets)."
  Assert-Condition (([double]$sample.maxLuminance - [double]$sample.minLuminance) -ge 12.0) "$Label rendered viewport luminance range is too flat: min=$($sample.minLuminance), max=$($sample.maxLuminance)."
}

function Save-CdpScreenshot {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Path
  )

  $response = Invoke-CdpCommand -Client $Client -Method "Page.captureScreenshot" -Params @{
    format = "png"
    captureBeyondViewport = $false
  }
  $data = [string]$response.result.data
  Assert-Condition (-not [string]::IsNullOrWhiteSpace($data)) "CDP screenshot returned empty data for $($Client.Label)."
  [System.IO.File]::WriteAllBytes($Path, [System.Convert]::FromBase64String($data))
  return $Path
}

function Read-PngVisualSample {
  param([Parameter(Mandatory = $true)][string]$Path)

  Add-Type -AssemblyName System.Drawing
  $bitmap = $null
  try {
    $bitmap = [System.Drawing.Bitmap]::new($Path)
    $targetSamplesX = 128
    $targetSamplesY = 72
    $stepX = [Math]::Max(1, [int][Math]::Floor($bitmap.Width / $targetSamplesX))
    $stepY = [Math]::Max(1, [int][Math]::Floor($bitmap.Height / $targetSamplesY))
    $buckets = [System.Collections.Generic.HashSet[string]]::new()
    $sampledPixels = 0
    $nonTransparentPixels = 0
    $minLuminance = 255.0
    $maxLuminance = 0.0

    for ($y = 0; $y -lt $bitmap.Height; $y += $stepY) {
      for ($x = 0; $x -lt $bitmap.Width; $x += $stepX) {
        $color = $bitmap.GetPixel($x, $y)
        $sampledPixels += 1
        if ($color.A -gt 8) {
          $nonTransparentPixels += 1
          [void]$buckets.Add("$($color.R -shr 4),$($color.G -shr 4),$($color.B -shr 4)")
          $luminance = ($color.R * 0.2126) + ($color.G * 0.7152) + ($color.B * 0.0722)
          $minLuminance = [Math]::Min($minLuminance, $luminance)
          $maxLuminance = [Math]::Max($maxLuminance, $luminance)
        }
      }
    }

    return [pscustomobject]@{
      width = $bitmap.Width
      height = $bitmap.Height
      sampledPixels = $sampledPixels
      nonTransparentPixels = $nonTransparentPixels
      distinctColorBuckets = $buckets.Count
      minLuminance = $minLuminance
      maxLuminance = $maxLuminance
    }
  } catch {
    return [pscustomobject]@{
      error = $_.Exception.Message
    }
  } finally {
    if ($null -ne $bitmap) {
      $bitmap.Dispose()
    }
  }
}

function Capture-PageVisualEvidence {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][string]$Path
  )

  $snapshot = Read-PageVisualSnapshot -Client $Client
  $savedPath = Save-CdpScreenshot -Client $Client -Path $Path
  $snapshot | Add-Member -NotePropertyName screenshotPath -NotePropertyValue $savedPath -Force
  $snapshot | Add-Member -NotePropertyName screenshotSample -NotePropertyValue (Read-PngVisualSample -Path $savedPath) -Force
  return $snapshot
}

function Read-CanvasInputPoint {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][double]$XRatio,
    [Parameter(Mandatory = $true)][double]$YRatio
  )

  $expression = @"
(() => {
  const canvas = document.querySelector("canvas");
  if (!canvas) {
    return null;
  }
  const rect = canvas.getBoundingClientRect();
  return {
    x: Math.round(rect.left + rect.width * $XRatio),
    y: Math.round(rect.top + rect.height * $YRatio),
    width: Math.round(rect.width),
    height: Math.round(rect.height)
  };
})()
"@

  $point = Invoke-CdpEvaluate -Client $Client -Expression $expression
  Assert-Condition ($null -ne $point) "Missing canvas input point for $($Client.Label)."
  return $point
}

function Set-CdpMousePressed {
  param(
    [Parameter(Mandatory = $true)]$Client,
    [Parameter(Mandatory = $true)][bool]$ShouldPress,
    [Parameter(Mandatory = $true)][ref]$MousePressed,
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y
  )

  Invoke-CdpCommand -Client $Client -Method "Input.dispatchMouseEvent" -Params @{
    type = "mouseMoved"
    x = $X
    y = $Y
    button = "none"
    buttons = $(if ($ShouldPress) { 1 } else { 0 })
  } | Out-Null

  if ($ShouldPress -and -not $MousePressed.Value) {
    Invoke-CdpCommand -Client $Client -Method "Input.dispatchMouseEvent" -Params @{
      type = "mousePressed"
      x = $X
      y = $Y
      button = "left"
      buttons = 1
      clickCount = 1
    } | Out-Null
    $MousePressed.Value = $true
  } elseif (-not $ShouldPress -and $MousePressed.Value) {
    Invoke-CdpCommand -Client $Client -Method "Input.dispatchMouseEvent" -Params @{
      type = "mouseReleased"
      x = $X
      y = $Y
      button = "left"
      buttons = 0
      clickCount = 1
    } | Out-Null
    $MousePressed.Value = $false
  }
}

function Start-DualClientInputHold {
  param(
    [Parameter(Mandatory = $true)]$ClientA,
    [Parameter(Mandatory = $true)]$ClientB
  )

  Invoke-CdpCommand -Client $ClientA -Method "Page.bringToFront" -Params @{} | Out-Null
  Invoke-CdpEvaluate -Client $ClientA -Expression "(() => { window.focus(); document.body?.focus?.(); return true; })()" | Out-Null
  Invoke-CdpCommand -Client $ClientB -Method "Page.bringToFront" -Params @{} | Out-Null
  Invoke-CdpEvaluate -Client $ClientB -Expression "(() => { window.focus(); document.body?.focus?.(); return true; })()" | Out-Null

  $pointA = Read-CanvasInputPoint -Client $ClientA -XRatio 0.72 -YRatio 0.46
  $pointB = Read-CanvasInputPoint -Client $ClientB -XRatio 0.28 -YRatio 0.54
  $mousePressedA = $false
  $mousePressedB = $false

  Send-CdpKeyEvent -Client $ClientA -Type "keyDown" -Key "d"
  Send-CdpKeyEvent -Client $ClientB -Type "keyDown" -Key "a"
  Set-CdpMousePressed -Client $ClientA -ShouldPress $true -MousePressed ([ref]$mousePressedA) -X ([int]$pointA.x) -Y ([int]$pointA.y)
  Set-CdpMousePressed -Client $ClientB -ShouldPress $true -MousePressed ([ref]$mousePressedB) -X ([int]$pointB.x) -Y ([int]$pointB.y)

  return [pscustomobject]@{
    pointA = $pointA
    pointB = $pointB
    mousePressedA = $mousePressedA
    mousePressedB = $mousePressedB
  }
}

function Stop-DualClientInputHold {
  param(
    [Parameter(Mandatory = $true)]$ClientA,
    [Parameter(Mandatory = $true)]$ClientB,
    $Hold
  )

  $mousePressedA = $false
  $mousePressedB = $false
  $pointA = [pscustomobject]@{ x = 640; y = 400 }
  $pointB = [pscustomobject]@{ x = 640; y = 400 }
  if ($null -ne $Hold) {
    $mousePressedA = [bool]$Hold.mousePressedA
    $mousePressedB = [bool]$Hold.mousePressedB
    if ($null -ne $Hold.pointA) {
      $pointA = $Hold.pointA
    }
    if ($null -ne $Hold.pointB) {
      $pointB = $Hold.pointB
    }
  }

  Set-CdpMousePressed -Client $ClientA -ShouldPress $false -MousePressed ([ref]$mousePressedA) -X ([int]$pointA.x) -Y ([int]$pointA.y)
  Set-CdpMousePressed -Client $ClientB -ShouldPress $false -MousePressed ([ref]$mousePressedB) -X ([int]$pointB.x) -Y ([int]$pointB.y)
  Send-CdpKeyEvent -Client $ClientA -Type "keyUp" -Key "d"
  Send-CdpKeyEvent -Client $ClientB -Type "keyUp" -Key "a"
}

function Stop-StartedProcess {
  param($Process)

  if ($null -eq $Process) {
    return
  }

  try {
    if (-not $Process.HasExited) {
      Stop-Process -Id $Process.Id -Force
    }
  } catch {
  }
}

$frontendBase = Normalize-BaseUrl $FrontendUrl
$backendApiBase = Normalize-BaseUrl $BackendApiUrl
$workspaceRoot = Get-WorkspaceRoot
$runtimeDir = Join-Path $workspaceRoot ".runtime\battle-zombie-browser-dual-client-smoke"
$clientADir = Join-Path $runtimeDir "client-a"
$clientBDir = Join-Path $runtimeDir "client-b"
$clientA = $null
$clientB = $null
$processA = $null
$processB = $null
$visualEvidencePaths = @()
$visualA = $null
$visualB = $null

try {
  Write-Host "Battle zombie browser dual-client smoke"
  Write-Host "Frontend: $frontendBase"
  Write-Host "Backend API: $backendApiBase"

  $health = Invoke-SmokeJson -Method "GET" -Uri (Join-TestUrl -Base $backendApiBase -Path "/health")
  Assert-Condition ($health.status -eq "ok") "Backend health did not return status=ok."
  Invoke-WebRequest -Uri (Join-TestUrl -Base $frontendBase -Path "/bp14-client.html") -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop | Out-Null

  Reset-RuntimeDir -RuntimeDir $runtimeDir -WorkspaceRoot $workspaceRoot
  $browserExe = Resolve-BrowserPath -RequestedPath $BrowserPath
  $portA = Get-FreeTcpPort
  $portB = Get-FreeTcpPort
  while ($portB -eq $portA) {
    $portB = Get-FreeTcpPort
  }

  $runSuffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
  $handleA = "zba$runSuffix"
  $handleB = "zbb$runSuffix"
  $urlA = New-ClientUrl -BaseUrl $frontendBase -Handle $handleA -PasswordValue $Password -Skin "blue"
  $urlB = New-ClientUrl -BaseUrl $frontendBase -Handle $handleB -PasswordValue $Password -Skin "soldier"

  Write-Host "Launching browser clients: $handleA, $handleB"
  $processA = Start-CdpBrowser -BrowserExe $browserExe -ProfileDir $clientADir -DebugPort $portA -Url $urlA
  $processB = Start-CdpBrowser -BrowserExe $browserExe -ProfileDir $clientBDir -DebugPort $portB -Url $urlB

  $targetA = Wait-CdpTarget -DebugPort $portA -Label "clientA"
  $targetB = Wait-CdpTarget -DebugPort $portB -Label "clientB"
  $clientA = Connect-Cdp -WebSocketUrl $targetA.webSocketDebuggerUrl -Label "clientA"
  $clientB = Connect-Cdp -WebSocketUrl $targetB.webSocketDebuggerUrl -Label "clientB"
  Initialize-CdpPage -Client $clientA
  Initialize-CdpPage -Client $clientB
  Configure-SmokeSkillSlots -Client $clientA -Url $urlA
  Configure-SmokeSkillSlots -Client $clientB -Url $urlB

  Write-Host "Waiting for both browser clients to enter playing..."
  $contextA = Wait-PagePlaying -Client $clientA -Label "clientA" -TimeoutSeconds $PlayingTimeoutSeconds
  $contextB = Wait-PagePlaying -Client $clientB -Label "clientB" -TimeoutSeconds $PlayingTimeoutSeconds

  Assert-Condition ($contextA.battleId -eq $contextB.battleId) "Browser clients exposed different battle ids: A=$($contextA.battleId), B=$($contextB.battleId)."
  Assert-Condition ($contextA.sharedAuthoritativeRuntime -eq $true) "clientA did not persist shared authoritative runtime."
  Assert-Condition ($contextB.sharedAuthoritativeRuntime -eq $true) "clientB did not persist shared authoritative runtime."
  Assert-Condition (-not [string]::IsNullOrWhiteSpace($contextA.localAuthoritativePlayerId)) "clientA missing local authoritative player id."
  Assert-Condition (-not [string]::IsNullOrWhiteSpace($contextB.localAuthoritativePlayerId)) "clientB missing local authoritative player id."
  Assert-Condition ($null -ne $contextA.canvas -and [int]$contextA.canvas.width -gt 0 -and [int]$contextA.canvas.height -gt 0) "clientA game canvas is missing or blank-sized."
  Assert-Condition ($null -ne $contextB.canvas -and [int]$contextB.canvas.width -gt 0 -and [int]$contextB.canvas.height -gt 0) "clientB game canvas is missing or blank-sized."
  if ($CaptureVisualEvidence) {
    $visualA = Capture-PageVisualEvidence -Client $clientA -Path (Join-Path $runtimeDir "client-a-initial.png")
    $visualB = Capture-PageVisualEvidence -Client $clientB -Path (Join-Path $runtimeDir "client-b-initial.png")
    Assert-PageVisualSnapshot -Snapshot $visualA -Label "clientA initial"
    Assert-PageVisualSnapshot -Snapshot $visualB -Label "clientB initial"
    $visualEvidencePaths += [string]$visualA.screenshotPath
    $visualEvidencePaths += [string]$visualB.screenshotPath
  }

  $battleId = [string]$contextA.battleId
  $stateBefore = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
  Assert-Condition ($stateBefore.mapId -eq "winter-hunt-v1") "Backend state expected winter-hunt-v1, got $($stateBefore.mapId)."
  $playersBefore = Read-Array $stateBefore "players"
  Assert-Condition ($playersBefore.Count -eq 12) "Expected 12 backend players, got $($playersBefore.Count)."
  $playerA = @($playersBefore | Where-Object { $_.playerId -ceq $contextA.localAuthoritativePlayerId } | Select-Object -First 1)
  $playerB = @($playersBefore | Where-Object { $_.playerId -ceq $contextB.localAuthoritativePlayerId } | Select-Object -First 1)
  Assert-Condition ($playerA.Count -eq 1 -and $playerA[0].isBot -eq $false) "Backend state missing clientA human player."
  Assert-Condition ($playerB.Count -eq 1 -and $playerB[0].isBot -eq $false) "Backend state missing clientB human player."
  $botPlayers = @($playersBefore | Where-Object { $_.isBot -eq $true })
  Assert-Condition ($botPlayers.Count -eq 10) "Expected 10 zombie bot players, got $($botPlayers.Count)."

  $humanABeforeCritical = Read-PlayerById -State $stateBefore -PlayerId $contextA.localAuthoritativePlayerId
  $criticalBefore = Read-SkillByKind -Player $humanABeforeCritical -Kind "Critical"
  Assert-Condition ([double]$criticalBefore.cooldownMs -le 0 -and [double]$criticalBefore.activeMs -le 0) "Critical skill was not ready before browser R tap."
  $humanAAfterCritical = $null
  $criticalAfter = $null
  $criticalHudProgressSeen = $false
  for ($attempt = 0; $attempt -lt 6; $attempt += 1) {
    Invoke-CdpCommand -Client $clientA -Method "Page.bringToFront" -Params @{} | Out-Null
    Invoke-CdpEvaluate -Client $clientA -Expression "(() => { window.focus(); document.body?.focus?.(); return true; })()" | Out-Null
    Send-CdpKeyEvent -Client $clientA -Type "keyDown" -Key "r"
    Start-Sleep -Milliseconds 120
    Send-CdpKeyEvent -Client $clientA -Type "keyUp" -Key "r"
    Start-Sleep -Milliseconds 350
    $stateAfterCritical = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
    $candidateHuman = Read-PlayerById -State $stateAfterCritical -PlayerId $contextA.localAuthoritativePlayerId
    $candidateCritical = Read-SkillByKind -Player $candidateHuman -Kind "Critical"
    if ([double]$candidateCritical.cooldownMs -gt 0 -and [double]$candidateCritical.activeMs -gt 0) {
      $humanAAfterCritical = $candidateHuman
      $criticalAfter = $candidateCritical
      $criticalHudProgressSeen = Wait-HudSkillProgress -Client $clientA -SlotKey "R"
      break
    }
  }
  Assert-Condition ($null -ne $humanAAfterCritical -and $null -ne $criticalAfter) "Browser R did not start Critical active/cooldown after retries."
  Assert-Condition $criticalHudProgressSeen "HUD skill slot R did not show Critical cooldown/active progress after retries."
  Assert-Condition ([double]$humanAAfterCritical.stamina -lt ([double]$humanABeforeCritical.stamina - 10.0)) "Browser R did not spend stamina for Critical after recovery allowance: before=$($humanABeforeCritical.stamina), after=$($humanAAfterCritical.stamina), cooldown=$($criticalAfter.cooldownMs), active=$($criticalAfter.activeMs), seq=$($humanABeforeCritical.lastClientCommandSeq)->$($humanAAfterCritical.lastClientCommandSeq)."

  $dashHudProgressSeen = $false
  $dashBackendSeen = $false
  $dashAfter = $null
  for ($attempt = 0; $attempt -lt 5; $attempt += 1) {
    Invoke-CdpCommand -Client $clientA -Method "Page.bringToFront" -Params @{} | Out-Null
    Invoke-CdpEvaluate -Client $clientA -Expression "(() => { window.focus(); document.body?.focus?.(); return true; })()" | Out-Null
    Send-CdpKeyEvent -Client $clientA -Type "keyDown" -Key "d"
    Start-Sleep -Milliseconds 220
    Send-CdpKeyEvent -Client $clientA -Type "keyDown" -Key "e"
    Start-Sleep -Milliseconds 120
    Send-CdpKeyEvent -Client $clientA -Type "keyUp" -Key "e"
    Start-Sleep -Milliseconds 260
    Send-CdpKeyEvent -Client $clientA -Type "keyUp" -Key "d"
    Start-Sleep -Milliseconds 550
    $stateAfterDashAttempt = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
    $humanAfterDashAttempt = Read-PlayerById -State $stateAfterDashAttempt -PlayerId $contextA.localAuthoritativePlayerId
    $dashAfter = Read-SkillByKind -Player $humanAfterDashAttempt -Kind "Dash"
    if ([double]$dashAfter.cooldownMs -gt 0 -or [double]$dashAfter.activeMs -gt 0) {
      $dashBackendSeen = $true
    }
    if (Test-HudSkillProgress -Client $clientA -SlotKey "E") {
      $dashHudProgressSeen = $true
      break
    }
  }
  Assert-Condition $dashBackendSeen "Browser E did not start backend Dash cooldown after retries."
  Assert-Condition $dashHudProgressSeen "HUD skill slot E did not show Dash cooldown/active progress after retries."

  $botPositionsBefore = @{}
  foreach ($bot in $botPlayers) {
    if ((Test-HasField $bot "playerId") -and (Test-HasField $bot "position")) {
      $botPositionsBefore[[string]$bot.playerId] = $bot.position
    }
  }

  $humanAInputBefore = Read-PlayerById -State $stateBefore -PlayerId $contextA.localAuthoritativePlayerId
  $humanBInputBefore = Read-PlayerById -State $stateBefore -PlayerId $contextB.localAuthoritativePlayerId
  $projectileEvidenceBefore = Read-ProjectileEvidenceCount -State $stateBefore
  $inputHold = $null
  try {
    $inputHold = Start-DualClientInputHold -ClientA $clientA -ClientB $clientB
    Start-Sleep -Milliseconds 1600
    $stateDuringInput = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
  } finally {
    Stop-DualClientInputHold -ClientA $clientA -ClientB $clientB -Hold $inputHold
  }
  Start-Sleep -Milliseconds 650
  $stateAfterInput = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
  $humanADuringInput = Read-PlayerById -State $stateDuringInput -PlayerId $contextA.localAuthoritativePlayerId
  $humanBDuringInput = Read-PlayerById -State $stateDuringInput -PlayerId $contextB.localAuthoritativePlayerId
  $humanAAfterInput = Read-PlayerById -State $stateAfterInput -PlayerId $contextA.localAuthoritativePlayerId
  $humanBAfterInput = Read-PlayerById -State $stateAfterInput -PlayerId $contextB.localAuthoritativePlayerId
  Assert-Condition (
    [Int64]$humanAAfterInput.lastClientCommandSeq -gt [Int64]$humanAInputBefore.lastClientCommandSeq
  ) "clientA input command sequence did not advance."
  Assert-Condition (
    [Int64]$humanBAfterInput.lastClientCommandSeq -gt [Int64]$humanBInputBefore.lastClientCommandSeq
  ) "clientB input command sequence did not advance."

  $moveA = [Math]::Abs([double]$humanAAfterInput.position.x - [double]$humanAInputBefore.position.x) +
    [Math]::Abs([double]$humanAAfterInput.position.y - [double]$humanAInputBefore.position.y)
  $moveB = [Math]::Abs([double]$humanBAfterInput.position.x - [double]$humanBInputBefore.position.x) +
    [Math]::Abs([double]$humanBAfterInput.position.y - [double]$humanBInputBefore.position.y)
  Assert-Condition ($moveA -gt 0.1) "clientA movement input did not move the backend player."
  Assert-Condition ($moveB -gt 0.1) "clientB movement input did not move the backend player."

  $projectileEvidenceAfterInput = Read-ProjectileEvidenceCount -State $stateAfterInput
  $shootingEvidence =
    ($projectileEvidenceAfterInput -gt $projectileEvidenceBefore) -or
    ([Int64]$humanAAfterInput.ammoInMagazine -lt [Int64]$humanAInputBefore.ammoInMagazine) -or
    ([Int64]$humanBAfterInput.ammoInMagazine -lt [Int64]$humanBInputBefore.ammoInMagazine)
  Assert-Condition $shootingEvidence "Left mouse fire did not create projectile or ammo evidence."

  Start-Sleep -Milliseconds 1400
  $contextAfterA = Get-PageBattleContext -Client $clientA
  $contextAfterB = Get-PageBattleContext -Client $clientB
  Assert-Condition ($contextAfterA.inBattle -eq $true) "clientA left browser battle state after initial verification."
  Assert-Condition ($contextAfterB.inBattle -eq $true) "clientB left browser battle state after initial verification."
  Assert-Condition ($contextAfterA.battleId -eq $battleId) "clientA battle id changed: before=$battleId, after=$($contextAfterA.battleId)."
  Assert-Condition ($contextAfterB.battleId -eq $battleId) "clientB battle id changed: before=$battleId, after=$($contextAfterB.battleId)."
  Assert-Condition ($contextAfterA.timer -ne $contextA.timer) "clientA HUD timer text did not change: $($contextA.timer)."
  Assert-Condition ($contextAfterB.timer -ne $contextB.timer) "clientB HUD timer text did not change: $($contextB.timer)."

  $stateAfter = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
  Assert-Condition ([Int64]$stateAfter.elapsedMs -gt [Int64]$stateBefore.elapsedMs) "Backend elapsedMs did not advance: before=$($stateBefore.elapsedMs), after=$($stateAfter.elapsedMs)."
  Assert-Condition ([Int64]$stateAfter.tick -gt [Int64]$stateBefore.tick) "Backend tick did not advance: before=$($stateBefore.tick), after=$($stateAfter.tick)."

  $playersAfter = Read-Array $stateAfter "players"
  $botMoved = $false
  foreach ($bot in @($playersAfter | Where-Object { $_.isBot -eq $true })) {
    $playerId = [string]$bot.playerId
    if ($botPositionsBefore.ContainsKey($playerId) -and (Test-HasField $bot "position")) {
      $beforePosition = $botPositionsBefore[$playerId]
      $dx = [Math]::Abs([double]$bot.position.x - [double]$beforePosition.x)
      $dy = [Math]::Abs([double]$bot.position.y - [double]$beforePosition.y)
      if (($dx + $dy) -gt 0.1) {
        $botMoved = $true
        break
      }
    }
  }
  Assert-Condition $botMoved "Expected at least one zombie bot to move while both browser clients are in the battle."

  if ($SoakSeconds -gt 0) {
    Write-Host "Soaking browser battle for $SoakSeconds seconds..."
    $soakStartState = $stateAfter
    $soakStartContextA = $contextAfterA
    $soakStartContextB = $contextAfterB
    $soakBotPositionsBefore = @{}
    foreach ($bot in @($playersAfter | Where-Object { $_.isBot -eq $true })) {
      if ((Test-HasField $bot "playerId") -and (Test-HasField $bot "position")) {
        $soakBotPositionsBefore[[string]$bot.playerId] = $bot.position
      }
    }

    $lastElapsedMs = [Int64]$stateAfter.elapsedMs
    $lastTick = [Int64]$stateAfter.tick
    $soakDeadline = [DateTimeOffset]::UtcNow.AddSeconds($SoakSeconds)
    while ([DateTimeOffset]::UtcNow -lt $soakDeadline) {
      Start-Sleep -Milliseconds 2000
      $contextAfterA = Get-PageBattleContext -Client $clientA
      $contextAfterB = Get-PageBattleContext -Client $clientB
      Assert-Condition ($contextAfterA.inBattle -eq $true) "clientA left browser battle during soak."
      Assert-Condition ($contextAfterB.inBattle -eq $true) "clientB left browser battle during soak."
      Assert-Condition ($contextAfterA.battleId -eq $battleId) "clientA battle id changed during soak: before=$battleId, after=$($contextAfterA.battleId)."
      Assert-Condition ($contextAfterB.battleId -eq $battleId) "clientB battle id changed during soak: before=$battleId, after=$($contextAfterB.battleId)."

      $stateAfter = Read-BattleState -ApiBase $backendApiBase -SessionToken $contextA.sessionToken -BattleId $battleId
      Assert-Condition ([Int64]$stateAfter.elapsedMs -gt $lastElapsedMs) "Backend elapsedMs did not continue advancing during soak: before=$lastElapsedMs, after=$($stateAfter.elapsedMs)."
      Assert-Condition ([Int64]$stateAfter.tick -gt $lastTick) "Backend tick did not continue advancing during soak: before=$lastTick, after=$($stateAfter.tick)."
      $lastElapsedMs = [Int64]$stateAfter.elapsedMs
      $lastTick = [Int64]$stateAfter.tick

      $soakPlayers = Read-Array $stateAfter "players"
      Assert-Condition ($soakPlayers.Count -eq 12) "Expected 12 players throughout soak, got $($soakPlayers.Count)."
    }

    $soakElapsedAdvance = [Int64]$stateAfter.elapsedMs - [Int64]$soakStartState.elapsedMs
    $minimumExpectedAdvance = [Math]::Max(1500, [int]($SoakSeconds * 500))
    Assert-Condition (
      $soakElapsedAdvance -ge $minimumExpectedAdvance
    ) "Backend elapsedMs advanced too little during soak: advance=$soakElapsedAdvance, expectedAtLeast=$minimumExpectedAdvance."
    Assert-Condition ($contextAfterA.timer -ne $soakStartContextA.timer) "clientA HUD timer did not change during soak: $($soakStartContextA.timer)."
    Assert-Condition ($contextAfterB.timer -ne $soakStartContextB.timer) "clientB HUD timer did not change during soak: $($soakStartContextB.timer)."

    if ($null -ne $soakStartState.gasZone -and $null -ne $stateAfter.gasZone) {
      Assert-Condition (
        [double]$stateAfter.gasZone.radius -lt [double]$soakStartState.gasZone.radius
      ) "Gas radius did not shrink during soak: before=$($soakStartState.gasZone.radius), after=$($stateAfter.gasZone.radius)."
    }

    $soakBotMoved = $false
    foreach ($bot in @(Read-Array $stateAfter "players" | Where-Object { $_.isBot -eq $true })) {
      $playerId = [string]$bot.playerId
      if ($soakBotPositionsBefore.ContainsKey($playerId) -and (Test-HasField $bot "position")) {
        $beforePosition = $soakBotPositionsBefore[$playerId]
        $dx = [Math]::Abs([double]$bot.position.x - [double]$beforePosition.x)
        $dy = [Math]::Abs([double]$bot.position.y - [double]$beforePosition.y)
        if (($dx + $dy) -gt 0.1) {
          $soakBotMoved = $true
          break
        }
      }
    }
    Assert-Condition $soakBotMoved "Expected at least one zombie bot to keep moving during soak."
  }

  if ($CaptureVisualEvidence) {
    $visualA = Capture-PageVisualEvidence -Client $clientA -Path (Join-Path $runtimeDir "client-a-final.png")
    $visualB = Capture-PageVisualEvidence -Client $clientB -Path (Join-Path $runtimeDir "client-b-final.png")
    Assert-PageVisualSnapshot -Snapshot $visualA -Label "clientA final"
    Assert-PageVisualSnapshot -Snapshot $visualB -Label "clientB final"
    $visualEvidencePaths += [string]$visualA.screenshotPath
    $visualEvidencePaths += [string]$visualB.screenshotPath
  }

  Write-Host "[PASS] zombie browser dual-client"
  Write-Host "battleId=$battleId"
  Write-Host "clientA=$handleA playerId=$($contextA.localAuthoritativePlayerId)"
  Write-Host "clientB=$handleB playerId=$($contextB.localAuthoritativePlayerId)"
  Write-Host "players=$($playersBefore.Count)"
  Write-Host "bots=$($botPlayers.Count)"
  Write-Host "elapsedMs=$($stateBefore.elapsedMs)->$($stateAfter.elapsedMs)"
  Write-Host "tick=$($stateBefore.tick)->$($stateAfter.tick)"
  Write-Host "hudTimerA=$($contextA.timer)->$($contextAfterA.timer)"
  Write-Host "hudTimerB=$($contextB.timer)->$($contextAfterB.timer)"
  Write-Host "clientACommands=$($humanAInputBefore.lastClientCommandSeq)->$($humanAAfterInput.lastClientCommandSeq)"
  Write-Host "clientBCommands=$($humanBInputBefore.lastClientCommandSeq)->$($humanBAfterInput.lastClientCommandSeq)"
  Write-Host "primaryHeldDuring=A:$($humanADuringInput.primaryHeld),B:$($humanBDuringInput.primaryHeld)"
  Write-Host "projectileEvidence=$projectileEvidenceBefore->$projectileEvidenceAfterInput"
  Write-Host "criticalA=stamina:$($humanABeforeCritical.stamina)->$($humanAAfterCritical.stamina),cooldown:$($criticalAfter.cooldownMs),active:$($criticalAfter.activeMs)"
  if ($SoakSeconds -gt 0) {
    Write-Host "soakSeconds=$SoakSeconds"
    Write-Host "soakFinalElapsedMs=$($stateAfter.elapsedMs)"
    Write-Host "soakFinalTick=$($stateAfter.tick)"
    if ($null -ne $stateAfter.gasZone) {
      Write-Host "soakFinalGasRadius=$($stateAfter.gasZone.radius)"
    }
  }
  if ($CaptureVisualEvidence) {
    Write-Host "visualViewportA=$($visualA.screenshotSample.width)x$($visualA.screenshotSample.height),colors=$($visualA.screenshotSample.distinctColorBuckets),luma=$([Math]::Round([double]$visualA.screenshotSample.minLuminance, 1))-$([Math]::Round([double]$visualA.screenshotSample.maxLuminance, 1))"
    Write-Host "visualViewportB=$($visualB.screenshotSample.width)x$($visualB.screenshotSample.height),colors=$($visualB.screenshotSample.distinctColorBuckets),luma=$([Math]::Round([double]$visualB.screenshotSample.minLuminance, 1))-$([Math]::Round([double]$visualB.screenshotSample.maxLuminance, 1))"
    foreach ($path in $visualEvidencePaths) {
      Write-Host "visualEvidence=$path"
    }
  }
} finally {
  Close-Cdp -Client $clientA
  Close-Cdp -Client $clientB
  Stop-StartedProcess -Process $processA
  Stop-StartedProcess -Process $processB
}
