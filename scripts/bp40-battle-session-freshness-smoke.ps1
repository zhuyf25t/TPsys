$ErrorActionPreference = "Stop"

$BaseUrl = if ($env:SLAY_DEMO_API_BASE) { $env:SLAY_DEMO_API_BASE } else { "http://127.0.0.1:5173/api" }
$BaseUrl = $BaseUrl.TrimEnd("/")
$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$RunSuffix = ([string]$RunId)
if ($RunSuffix.Length -gt 8) {
  $RunSuffix = $RunSuffix.Substring($RunSuffix.Length - 8)
}
$SmokeHandle = "bp40$RunSuffix"
$SmokeSession = $null

function Invoke-Bp40Json {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null
  )

  $parameters = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = @{ "Accept" = "application/json" }
    TimeoutSec = 8
  }

  if ($null -ne $Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }

  return Invoke-RestMethod @parameters
}

function Invoke-Bp40BattleMessage {
  param(
    [string]$Name,
    [object]$Body
  )

  $payload = @{}
  foreach ($property in $Body.PSObject.Properties) {
    $payload[$property.Name] = $property.Value
  }
  $payload.userToken = $SmokeSession

  return Invoke-Bp40Json "POST" "/$Name" $payload
}

function New-Bp40SmokeSession {
  $account = Invoke-Bp40Json "POST" "/identity/register" @{
    handle = $SmokeHandle
    password = "secret"
    skinId = "blue"
  }

  if ([string]::IsNullOrWhiteSpace([string]$account.session)) {
    throw "Identity register did not return a session token for handle=$SmokeHandle."
  }

  return [string]$account.session
}

function Test-HasField {
  param(
    [object]$Value,
    [string]$Field
  )

  return $null -ne ($Value.PSObject.Properties | Where-Object { $_.Name -ceq $Field } | Select-Object -First 1)
}

function Join-Bp40Queue {
  param(
    [string]$QueueRequestId
  )

  return Invoke-Bp40BattleMessage "battlequeuejoin" ([pscustomobject]@{
    handle = $SmokeHandle
    sessionToken = $SmokeSession
    queueRequestId = $QueueRequestId
    rating = "1200"
    skin = "blue"
  })
}

function Wait-Bp40BattleSession {
  param(
    [string]$TicketId
  )

  $status = $null
  for ($i = 0; $i -lt 60; $i++) {
    $status = Invoke-Bp40BattleMessage "battlequeuestatus" ([pscustomobject]@{
      ticketId = $TicketId
    })
    if ($null -ne $status.battleSession) {
      return $status
    }

    Start-Sleep -Milliseconds 250
  }

  throw "Battle session was not created for ticketId=$TicketId."
}

function Wait-Bp40ElapsedAtLeast {
  param(
    [string]$BattleId,
    [int]$MinimumElapsedMs
  )

  $state = $null
  for ($i = 0; $i -lt 40; $i++) {
    $state = Invoke-Bp40BattleMessage "battlestateread" ([pscustomobject]@{
      battleId = $BattleId
    })
    if ([int64]$state.elapsedMs -ge $MinimumElapsedMs) {
      return $state
    }

    Start-Sleep -Milliseconds 150
  }

  $actual = if ($null -ne $state) { $state.elapsedMs } else { "<none>" }
  throw "Battle $BattleId did not advance to elapsed >= $MinimumElapsedMs ms; actual=$actual."
}

function Read-Bp40State {
  param(
    [string]$BattleId
  )

  return Invoke-Bp40BattleMessage "battlestateread" ([pscustomobject]@{
    battleId = $BattleId
  })
}

$roundOne = $null
$roundTwo = $null
$freshWaitingJoin = $null

Write-Host "BP-40 battle session freshness smoke"
Write-Host "Base URL: $BaseUrl"
Write-Host "Handle: $SmokeHandle"
Write-Host ""

try {
  $SmokeSession = New-Bp40SmokeSession
  $roundOne = Join-Bp40Queue "bp40-round-1-$RunId"
  $sameRequestJoin = Join-Bp40Queue "bp40-round-1-$RunId"
  if ($sameRequestJoin.ticketId -ne $roundOne.ticketId -or $sameRequestJoin.roomId -ne $roundOne.roomId) {
    throw "Same queueRequestId should be idempotent: first=$($roundOne.ticketId)/$($roundOne.roomId), second=$($sameRequestJoin.ticketId)/$($sameRequestJoin.roomId)."
  }

  $freshWaitingJoin = Join-Bp40Queue "bp40-round-waiting-fresh-$RunId"
  if ($freshWaitingJoin.ticketId -eq $roundOne.ticketId -or $freshWaitingJoin.roomId -eq $roundOne.roomId) {
    throw "Fresh queueRequestId reused an existing waiting ticket/room: first=$($roundOne.ticketId)/$($roundOne.roomId), fresh=$($freshWaitingJoin.ticketId)/$($freshWaitingJoin.roomId)."
  }
  Invoke-Bp40BattleMessage "battlequeueleave" ([pscustomobject]@{ ticketId = $freshWaitingJoin.ticketId }) | Out-Null
  $freshWaitingJoin = $null

  $statusOne = Wait-Bp40BattleSession $roundOne.ticketId
  $battleOneId = [string]$statusOne.battleSession.battleId
  $stateOne = Wait-Bp40ElapsedAtLeast $battleOneId 1500

  $roundTwo = Join-Bp40Queue "bp40-round-2-$RunId"
  $statusTwo = Wait-Bp40BattleSession $roundTwo.ticketId
  $battleTwoId = [string]$statusTwo.battleSession.battleId
  $stateTwo = Read-Bp40State $battleTwoId

  if ($battleTwoId -eq $battleOneId) {
    throw "Second round reused battleId=$battleTwoId."
  }
  if ([int64]$stateTwo.elapsedMs -gt 1500) {
    throw "Second round inherited elapsed time: battleId=$battleTwoId elapsedMs=$($stateTwo.elapsedMs), firstElapsedMs=$($stateOne.elapsedMs)."
  }
  if ([int64]$stateTwo.durationMs -le 0) {
    throw "Second round returned invalid durationMs=$($stateTwo.durationMs)."
  }

  $remainingOne = [int64]$stateOne.durationMs - [int64]$stateOne.elapsedMs
  $remainingTwo = [int64]$stateTwo.durationMs - [int64]$stateTwo.elapsedMs
  if ($remainingTwo -lt $remainingOne) {
    throw "Second round timer did not reset: firstRemainingMs=$remainingOne, secondRemainingMs=$remainingTwo."
  }

  Write-Host "[PASS] BP-40 freshness"
  Write-Host "round1BattleId=$battleOneId"
  Write-Host "round1ElapsedMs=$($stateOne.elapsedMs)"
  Write-Host "round1RemainingMs=$remainingOne"
  Write-Host "round2BattleId=$battleTwoId"
  Write-Host "round2ElapsedMs=$($stateTwo.elapsedMs)"
  Write-Host "round2RemainingMs=$remainingTwo"
} finally {
  if ($null -ne $roundTwo -and (Test-HasField $roundTwo "ticketId")) {
    try { Invoke-Bp40BattleMessage "battlequeueleave" ([pscustomobject]@{ ticketId = $roundTwo.ticketId }) | Out-Null } catch {}
  }
  if ($null -ne $freshWaitingJoin -and (Test-HasField $freshWaitingJoin "ticketId")) {
    try { Invoke-Bp40BattleMessage "battlequeueleave" ([pscustomobject]@{ ticketId = $freshWaitingJoin.ticketId }) | Out-Null } catch {}
  }
  if ($null -ne $roundOne -and (Test-HasField $roundOne "ticketId")) {
    try { Invoke-Bp40BattleMessage "battlequeueleave" ([pscustomobject]@{ ticketId = $roundOne.ticketId }) | Out-Null } catch {}
  }
}
