$ErrorActionPreference = "Stop"

$BaseUrl = if ($env:SLAY_DEMO_API_BASE) { $env:SLAY_DEMO_API_BASE } else { "http://127.0.0.1:8080/api" }
$BaseUrl = $BaseUrl.TrimEnd("/")
$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$RunSuffix = [string]$RunId
if ($RunSuffix.Length -gt 8) {
  $RunSuffix = $RunSuffix.Substring($RunSuffix.Length - 8)
}

$HandleOne = "zm$($RunSuffix)a"
$HandleTwo = "zm$($RunSuffix)b"
$SmokePassword = "pass1234"
$ExpectedWinterCapacity = 6
$JoinOne = $null
$JoinTwo = $null
$SessionOne = $null
$SessionTwo = $null

function Invoke-SmokeJson {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null
  )

  $uri = "$BaseUrl$Path"
  $parameters = @{
    Method = $Method
    Uri = $uri
    Headers = @{ "Accept" = "application/json" }
    TimeoutSec = 10
  }

  if ($null -ne $Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
  }

  try {
    return Invoke-RestMethod @parameters
  } catch {
    $detail = $_.Exception.Message
    if (-not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
      $detail = "$detail :: $($_.ErrorDetails.Message)"
    }
    throw "Request failed: $Method $uri :: $detail"
  }
}

function Invoke-BattleMessage {
  param(
    [string]$Name,
    [string]$UserToken,
    [hashtable]$Body
  )

  $payload = @{}
  foreach ($key in $Body.Keys) {
    $payload[$key] = $Body[$key]
  }
  $payload.userToken = $UserToken

  Invoke-SmokeJson "POST" "/$Name" $payload
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

function New-SmokeSession {
  param(
    [string]$Handle,
    [string]$Skin
  )

  $registration = Invoke-SmokeJson "POST" "/identity/register" @{
    handle = $Handle
    password = $SmokePassword
    skinId = $Skin
  }

  Assert-Condition (-not [string]::IsNullOrWhiteSpace([string]$registration.session)) "Register did not return a session token for $Handle."
  return [string]$registration.session
}

function Join-WinterQueue {
  param(
    [string]$Handle,
    [string]$SessionToken,
    [string]$Skin,
    [string]$QueueRequestId
  )

  Invoke-BattleMessage "battlequeuejoin" $SessionToken @{
    handle = $Handle
    sessionToken = $SessionToken
    queueRequestId = $QueueRequestId
    rating = "1200"
    skin = $Skin
    modeId = "winter"
  }
}

function Read-QueueStatus {
  param(
    [string]$SessionToken,
    [string]$TicketId
  )

  Invoke-BattleMessage "battlequeuestatus" $SessionToken @{
    ticketId = $TicketId
  }
}

function Read-BattleState {
  param(
    [string]$SessionToken,
    [string]$BattleId
  )

  Invoke-BattleMessage "battlestateread" $SessionToken @{
    battleId = $BattleId
  }
}

function Send-IdleCommand {
  param(
    [string]$SessionToken,
    [string]$BattleId,
    [string]$PlayerId,
    [string]$TicketId,
    [int64]$ClientTick
  )

  Invoke-BattleMessage "battlecommand" $SessionToken @{
    battleId = $BattleId
    playerId = $PlayerId
    ticketId = $TicketId
    clientTick = $ClientTick
    movement = @{ x = 0; y = 0 }
    aim = @{ x = 1; y = 0 }
    primaryHeld = $false
    reloadPressed = $false
    castDash = $false
    switchWeaponDirection = 0
  } | Out-Null
}

function Leave-QueueQuietly {
  param(
    [string]$SessionToken,
    [object]$Join
  )

  if ($null -ne $Join -and (Test-HasField $Join "ticketId")) {
    try {
      Invoke-BattleMessage "battlequeueleave" $SessionToken @{ ticketId = $Join.ticketId } | Out-Null
    } catch {
    }
  }
}

Write-Host "Battle zombie multiplayer smoke"
Write-Host "Base URL: $BaseUrl"
Write-Host "Handles: $HandleOne, $HandleTwo"
Write-Host ""

try {
  $SessionOne = New-SmokeSession $HandleOne "blue"
  $SessionTwo = New-SmokeSession $HandleTwo "soldier"

  $JoinOne = Join-WinterQueue $HandleOne $SessionOne "blue" "zombie-mp-$RunId-a"
  Assert-Condition ($JoinOne.modeId -eq "winter") "First join resolved wrong modeId=$($JoinOne.modeId)."
  Assert-Condition ($JoinOne.mapId -eq "winter-hunt-v1") "First join resolved wrong mapId=$($JoinOne.mapId)."
  Assert-Condition ([int]$JoinOne.capacity -eq $ExpectedWinterCapacity) "First join expected capacity $ExpectedWinterCapacity, got $($JoinOne.capacity)."

  $JoinTwo = Join-WinterQueue $HandleTwo $SessionTwo "red" "zombie-mp-$RunId-b"
  Assert-Condition ($JoinTwo.roomId -eq $JoinOne.roomId) "Winter players did not join same room: first=$($JoinOne.roomId), second=$($JoinTwo.roomId)."
  Assert-Condition ($JoinTwo.modeId -eq "winter") "Second join resolved wrong modeId=$($JoinTwo.modeId)."
  Assert-Condition ($JoinTwo.mapId -eq "winter-hunt-v1") "Second join resolved wrong mapId=$($JoinTwo.mapId)."
  Assert-Condition ([int]$JoinTwo.capacity -eq $ExpectedWinterCapacity) "Second join expected capacity $ExpectedWinterCapacity, got $($JoinTwo.capacity)."

  $startsAt = [Math]::Max([Int64]$JoinOne.startsAt, [Int64]$JoinTwo.startsAt)
  $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $waitMs = [Math]::Max(0, $startsAt - $nowMs + 300)
  if ($waitMs -gt 0) {
    Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
  }

  $statusOne = $null
  $statusTwo = $null
  for ($i = 0; $i -lt 48; $i++) {
    $statusOne = Read-QueueStatus $SessionOne $JoinOne.ticketId
    $statusTwo = Read-QueueStatus $SessionTwo $JoinTwo.ticketId
    if (
      $null -ne $statusOne.battleSession -and
      $null -ne $statusTwo.battleSession -and
      $statusOne.phase -eq "active" -and
      $statusTwo.phase -eq "active"
    ) {
      break
    }
    Start-Sleep -Milliseconds 250
  }

  Assert-Condition ($null -ne $statusOne.battleSession) "First ticket did not receive an active battle session."
  Assert-Condition ($null -ne $statusTwo.battleSession) "Second ticket did not receive an active battle session."
  Assert-Condition ($statusOne.phase -eq "active" -and $statusTwo.phase -eq "active") "Expected active queue phase, got first=$($statusOne.phase), second=$($statusTwo.phase)."
  Assert-Condition ($statusOne.battleSession.battleId -eq $statusTwo.battleSession.battleId) "Tickets resolved different battle ids: first=$($statusOne.battleSession.battleId), second=$($statusTwo.battleSession.battleId)."

  $session = $statusOne.battleSession
  Assert-Condition ($session.modeId -eq "winter") "Active session resolved wrong modeId=$($session.modeId)."
  Assert-Condition ($session.mapId -eq "winter-hunt-v1") "Active session resolved wrong mapId=$($session.mapId)."
  Assert-Condition ([int]$session.capacity -eq $ExpectedWinterCapacity) "Active session expected capacity $ExpectedWinterCapacity, got $($session.capacity)."

  $roster = Read-Array $session "roster"
  $rosterPlayerIds = @($roster | ForEach-Object { $_.playerId })
  Assert-Condition ($rosterPlayerIds -contains $JoinOne.playerId) "Roster is missing first joined player."
  Assert-Condition ($rosterPlayerIds -contains $JoinTwo.playerId) "Roster is missing second joined player."

  Assert-Condition ($null -ne $session.bootstrap -and (Test-HasField $session.bootstrap "seats")) "Active session bootstrap seats are missing."
  $seats = Read-Array $session.bootstrap "seats"
  Assert-Condition ($seats.Count -eq $ExpectedWinterCapacity) "Expected $ExpectedWinterCapacity bootstrap seats, got $($seats.Count)."
  $seatOne = @($seats | Where-Object { $_.playerId -ceq $JoinOne.playerId } | Select-Object -First 1)
  $seatTwo = @($seats | Where-Object { $_.playerId -ceq $JoinTwo.playerId } | Select-Object -First 1)
  Assert-Condition ($seatOne.Count -eq 1 -and $seatOne[0].isBot -eq $false) "First joined player bootstrap seat is missing or marked bot."
  Assert-Condition ($seatTwo.Count -eq 1 -and $seatTwo[0].isBot -eq $false) "Second joined player bootstrap seat is missing or marked bot."
  $botSeats = @($seats | Where-Object { $_.isBot -eq $true })
  $expectedBotSeats = $ExpectedWinterCapacity - $roster.Count
  Assert-Condition ($botSeats.Count -eq $expectedBotSeats) "Expected bot seats to fill capacity: roster=$($roster.Count), expectedBots=$expectedBotSeats, actualBots=$($botSeats.Count)."
  $spawnIndexes = @($seats | ForEach-Object { [int]$_.spawnPointIndex } | Sort-Object)
  Assert-Condition (
    $spawnIndexes.Count -eq $ExpectedWinterCapacity -and
    $spawnIndexes[0] -eq 0 -and
    $spawnIndexes[$ExpectedWinterCapacity - 1] -eq ($ExpectedWinterCapacity - 1)
  ) "Expected spawn indexes 0..$($ExpectedWinterCapacity - 1), got $($spawnIndexes -join ', ')."

  $battleId = [string]$session.battleId
  $stateBefore = Read-BattleState $SessionOne $battleId
  Assert-Condition ($stateBefore.mapId -eq "winter-hunt-v1") "Runtime state resolved wrong mapId=$($stateBefore.mapId)."
  $playersBefore = Read-Array $stateBefore "players"
  Assert-Condition ($playersBefore.Count -eq $ExpectedWinterCapacity) "Expected $ExpectedWinterCapacity runtime players, got $($playersBefore.Count)."
  $playerOne = @($playersBefore | Where-Object { $_.playerId -ceq $JoinOne.playerId } | Select-Object -First 1)
  $playerTwo = @($playersBefore | Where-Object { $_.playerId -ceq $JoinTwo.playerId } | Select-Object -First 1)
  Assert-Condition ($playerOne.Count -eq 1 -and $playerOne[0].isBot -eq $false) "First runtime player is missing or marked bot."
  Assert-Condition ($playerTwo.Count -eq 1 -and $playerTwo[0].isBot -eq $false) "Second runtime player is missing or marked bot."

  Send-IdleCommand $SessionOne $battleId $JoinOne.playerId $JoinOne.ticketId 1
  Send-IdleCommand $SessionTwo $battleId $JoinTwo.playerId $JoinTwo.ticketId 1

  $botPositionsBefore = @{}
  foreach ($bot in @($playersBefore | Where-Object { $_.isBot -eq $true })) {
    if ((Test-HasField $bot "playerId") -and (Test-HasField $bot "position")) {
      $botPositionsBefore[[string]$bot.playerId] = $bot.position
    }
  }

  Start-Sleep -Milliseconds 1200
  $stateAfter = Read-BattleState $SessionOne $battleId
  Assert-Condition ([Int64]$stateAfter.elapsedMs -gt [Int64]$stateBefore.elapsedMs) "Timer did not advance: before=$($stateBefore.elapsedMs), after=$($stateAfter.elapsedMs)."
  Assert-Condition ([Int64]$stateAfter.tick -gt [Int64]$stateBefore.tick) "Tick did not advance: before=$($stateBefore.tick), after=$($stateAfter.tick)."

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
  Assert-Condition $botMoved "Expected at least one zombie bot to move between state reads."

  Write-Host "[PASS] zombie multiplayer"
  Write-Host "battleId=$battleId"
  Write-Host "roomId=$($JoinOne.roomId)"
  Write-Host "roster=$($roster.Count)"
  Write-Host "botSeats=$($botSeats.Count)"
  Write-Host "elapsedMs=$($stateBefore.elapsedMs)->$($stateAfter.elapsedMs)"
  Write-Host "tick=$($stateBefore.tick)->$($stateAfter.tick)"
} finally {
  Leave-QueueQuietly $SessionTwo $JoinTwo
  Leave-QueueQuietly $SessionOne $JoinOne
}
