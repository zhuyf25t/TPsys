[CmdletBinding()]
param(
  [ValidateSet("winter", "default", "autumn", "normal")]
  [string]$ModeId = "default",
  [string]$SummaryPath = ""
)

$ErrorActionPreference = "Stop"

$BaseUrl = if ($env:SLAY_DEMO_API_BASE) { $env:SLAY_DEMO_API_BASE } else { "http://127.0.0.1:5173/api" }
$BaseUrl = $BaseUrl.TrimEnd("/")
$Results = New-Object System.Collections.Generic.List[object]

function Add-Result {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Detail
  )

  $Results.Add([pscustomobject]@{
    Name = $Name
    Passed = $Passed
    Detail = $Detail
  }) | Out-Null
}

function Invoke-ContractJson {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null
  )

  $request = Convert-ContractRequest -Method $Method -Path $Path -Body $Body
  $uri = "$BaseUrl$($request.Path)"
  $parameters = @{
    Method = $request.Method
    Uri = $uri
    Headers = @{ "Accept" = "application/json" }
    TimeoutSec = 8
  }

  if ($null -ne $request.Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = ($request.Body | ConvertTo-Json -Depth 8 -Compress)
  }

  try {
    return Invoke-RestMethod @parameters
  } catch {
    throw "Request failed: $($request.Method) $uri :: $($_.Exception.Message)"
  }
}

function Convert-ContractRequest {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null
  )

  $pathOnly = Get-ContractPathOnly $Path
  $convertedMethod = $Method
  $convertedPath = $Path
  $convertedBody = $Body

  switch -Regex ($pathOnly) {
    '^/battle/results$' {
      $convertedMethod = "POST"
      $convertedPath = "/battleresultlist"
      $convertedBody = Add-BattleUserToken (Merge-ContractBody $Body @{
        battleId = Get-ContractQueryValue $Path "battleId"
        handle = Get-ContractQueryValue $Path "handle"
        limit = Get-ContractQueryInt $Path "limit" 10
      })
      break
    }
    '^/battle/queue/join$' {
      $convertedMethod = "POST"
      $convertedPath = "/battlequeuejoin"
      $convertedBody = Add-BattleUserToken (Copy-ContractBody $Body)
      break
    }
    '^/battle/queue/status$' {
      $convertedMethod = "POST"
      $convertedPath = "/battlequeuestatus"
      $convertedBody = Add-BattleUserToken (Merge-ContractBody $Body @{
        ticketId = Get-ContractQueryValue $Path "ticketId"
      })
      break
    }
    '^/battle/queue/leave$' {
      $convertedMethod = "POST"
      $convertedPath = "/battlequeueleave"
      $convertedBody = Add-BattleUserToken (Copy-ContractBody $Body)
      break
    }
  }

  return [pscustomobject]@{
    Method = $convertedMethod
    Path = $convertedPath
    Body = $convertedBody
  }
}

function Get-ContractPathOnly {
  param([string]$Path)

  $queryStart = $Path.IndexOf("?")
  if ($queryStart -lt 0) {
    return $Path
  }

  return $Path.Substring(0, $queryStart)
}

function Get-ContractQueryValue {
  param(
    [string]$Path,
    [string]$Name
  )

  $queryStart = $Path.IndexOf("?")
  if ($queryStart -lt 0 -or $queryStart -ge ($Path.Length - 1)) {
    return $null
  }

  $query = $Path.Substring($queryStart + 1)
  foreach ($part in $query.Split("&")) {
    if ([string]::IsNullOrWhiteSpace($part)) {
      continue
    }

    $pair = $part.Split("=", 2)
    $key = [uri]::UnescapeDataString($pair[0])
    if ($key -ne $Name) {
      continue
    }

    if ($pair.Count -lt 2) {
      return ""
    }

    return [uri]::UnescapeDataString($pair[1])
  }

  return $null
}

function Get-ContractQueryInt {
  param(
    [string]$Path,
    [string]$Name,
    [int]$DefaultValue
  )

  $value = Get-ContractQueryValue $Path $Name
  if ([string]::IsNullOrWhiteSpace($value)) {
    return $DefaultValue
  }

  $parsed = 0
  if ([int]::TryParse([string]$value, [ref]$parsed)) {
    return $parsed
  }

  return $DefaultValue
}

function Copy-ContractBody {
  param([object]$Body)

  $copy = @{}
  if ($null -eq $Body) {
    return $copy
  }

  if ($Body -is [hashtable]) {
    foreach ($key in $Body.Keys) {
      $copy[$key] = $Body[$key]
    }
    return $copy
  }

  foreach ($property in $Body.PSObject.Properties) {
    $copy[$property.Name] = $property.Value
  }
  return $copy
}

function Merge-ContractBody {
  param(
    [object]$Body,
    [hashtable]$Defaults
  )

  $copy = Copy-ContractBody $Body
  foreach ($key in $Defaults.Keys) {
    if (-not $copy.ContainsKey($key) -and $null -ne $Defaults[$key]) {
      $copy[$key] = $Defaults[$key]
    }
  }
  return $copy
}

function Add-BattleUserToken {
  param([hashtable]$Body)

  $copy = Copy-ContractBody $Body
  if ($copy.ContainsKey("userToken") -and -not [string]::IsNullOrWhiteSpace([string]$copy["userToken"])) {
    return $copy
  }

  if ($copy.ContainsKey("sessionToken") -and -not [string]::IsNullOrWhiteSpace([string]$copy["sessionToken"])) {
    $copy["userToken"] = [string]$copy["sessionToken"]
    if ([string]::IsNullOrWhiteSpace([string]$Script:BattleApiUserToken)) {
      $Script:BattleApiUserToken = [string]$copy["sessionToken"]
    }
    return $copy
  }

  if ([string]::IsNullOrWhiteSpace([string]$Script:BattleApiUserToken)) {
    throw "No battle user token is available for APIMessageRouter request."
  }

  $copy["userToken"] = [string]$Script:BattleApiUserToken
  return $copy
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

function Test-Fields {
  param(
    [object]$Value,
    [string[]]$Fields
  )

  $missing = @($Fields | Where-Object { -not (Test-HasField $Value $_) })
  return $missing
}

function Test-ArrayEnvelope {
  param(
    [object]$Payload,
    [string]$Field
  )

  if (-not (Test-HasField $Payload $Field)) {
    throw "Missing envelope field '$Field'."
  }

  $value = $Payload.$Field
  if ($null -eq $value) {
    throw "Envelope field '$Field' is null."
  }

  if ($value -isnot [System.Array]) {
    throw "Envelope field '$Field' is not an array."
  }

  return ,$value
}

function Test-NumberOrNull {
  param(
    [object]$Value
  )

  return (
    $null -eq $Value -or
    $Value -is [byte] -or
    $Value -is [int16] -or
    $Value -is [int32] -or
    $Value -is [int64] -or
    $Value -is [single] -or
    $Value -is [double] -or
    $Value -is [decimal]
  )
}

function Get-AuthoritativePlacementScore {
  param(
    [object]$Placement
  )

  if ($null -eq $Placement) {
    return 0
  }

  $ladder = @(12, 9, 7, 5, 3, 1)
  $placementIndex = [Math]::Max([Int32]$Placement - 1, 0)
  $ladderIndex = [Math]::Min($placementIndex, $ladder.Count - 1)
  return $ladder[$ladderIndex]
}

function Test-NumberOrNullFields {
  param(
    [object]$Value,
    [string[]]$Fields
  )

  $invalid = @()
  foreach ($field in $Fields) {
    if (-not (Test-HasField $Value $field) -or -not (Test-NumberOrNull $Value.$field)) {
      $actual = if (Test-HasField $Value $field) { $Value.$field } else { "<missing>" }
      $invalid += "$field=$actual"
    }
  }

  return $invalid
}

function Assert-ReplaySourceMail {
  param(
    [object]$Mail,
    [string]$BattleId,
    [string]$OwnerHandle,
    [string]$Label
  )

  $missingSourceFields = Test-Fields $Mail @("sourceBattleId", "sourcePath", "sourceLabel")
  if ($missingSourceFields.Count -gt 0) {
    throw "$Label missing replay source fields: $($missingSourceFields -join ', ')."
  }
  if ($Mail.sourceBattleId -ne $BattleId) {
    throw "$Label sourceBattleId mismatch: sourceBattleId=$($Mail.sourceBattleId), expected=$BattleId."
  }
  $sourcePath = [string]$Mail.sourcePath
  $queryStart = $sourcePath.IndexOf("?")
  $actualPath = if ($queryStart -ge 0) { $sourcePath.Substring(0, $queryStart) } else { $sourcePath }
  $actualQuery = if ($queryStart -ge 0 -and $queryStart -lt $sourcePath.Length - 1) { $sourcePath.Substring($queryStart + 1) } else { "" }
  $expectedPath = "/replay/$BattleId"
  if ($actualPath -ne $expectedPath) {
    throw "$Label sourcePath path mismatch: sourcePath=$sourcePath, expected path=$expectedPath."
  }
  if ([string]::IsNullOrWhiteSpace($actualQuery)) {
    throw "$Label sourcePath missing owner handle query: sourcePath=$sourcePath."
  }
  $queryFields = @{}
  foreach ($pair in ($actualQuery -split "&")) {
    if ([string]::IsNullOrWhiteSpace($pair)) {
      continue
    }
    $separator = $pair.IndexOf("=")
    $rawKey = if ($separator -ge 0) { $pair.Substring(0, $separator) } else { $pair }
    $rawValue = if ($separator -ge 0) { $pair.Substring($separator + 1) } else { "" }
    $key = [uri]::UnescapeDataString($rawKey.Replace("+", " "))
    $value = [uri]::UnescapeDataString($rawValue.Replace("+", " "))
    $queryFields[$key] = $value
  }
  if (-not $queryFields.ContainsKey("handle") -or $queryFields["handle"] -ne $OwnerHandle) {
    throw "$Label sourcePath handle query mismatch: handle=$($queryFields["handle"]), expected=$OwnerHandle."
  }
  if ([string]::IsNullOrWhiteSpace([string]$Mail.sourceLabel)) {
    throw "$Label sourceLabel must be non-empty."
  }
}

function Assert-ReplaySettlementMatchesResult {
  param(
    [object]$Replay,
    [object]$Result,
    [string]$OwnerHandle,
    [string]$Label,
    [bool]$ExpectHandleField
  )

  if ($ExpectHandleField) {
    $missingHandleFields = Test-Fields $Replay @("handle", "displayName")
    if ($missingHandleFields.Count -gt 0) {
      throw "$Label replay missing handle fields: $($missingHandleFields -join ', ')."
    }
    if ($Replay.handle -ne $OwnerHandle) {
      throw "$Label replay handle mismatch: handle=$($Replay.handle), expected=$OwnerHandle."
    }
  }
  if ([Int32]$Replay.score -ne [Int32]$Result.score) {
    throw "$Label replay score mismatch: replay=$($Replay.score), result=$($Result.score), handle=$OwnerHandle."
  }
  if ([Int32]$Replay.placement -ne [Int32]$Result.placement) {
    throw "$Label replay placement mismatch: replay=$($Replay.placement), result=$($Result.placement), handle=$OwnerHandle."
  }
  if (
    $Replay.ratingBefore -ne $Result.ratingBefore -or
    $Replay.ratingDelta -ne $Result.ratingDelta -or
    $Replay.ratingAfter -ne $Result.ratingAfter
  ) {
    throw "$Label replay rating mismatch for handle=${OwnerHandle}: replay=($($Replay.ratingBefore), $($Replay.ratingDelta), $($Replay.ratingAfter)); result=($($Result.ratingBefore), $($Result.ratingDelta), $($Result.ratingAfter))."
  }
}

function Test-Endpoint {
  param(
    [string]$Name,
    [scriptblock]$Check
  )

  try {
    $detail = & $Check
    Add-Result $Name $true $detail
  } catch {
    Add-Result $Name $false $_.Exception.Message
  }
}

$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$RunSuffix = [string]$RunId
$RunSuffix = $RunSuffix.Substring([Math]::Max(0, $RunSuffix.Length - 8))
$SmokeHandle = "fs$RunSuffix"
$SmokePassword = "pass1234"

Write-Host "Authoritative finish/result/replay smoke"
Write-Host "Base URL: $BaseUrl"
Write-Host "Mode ID: $ModeId"
Write-Host "Note: backend must already be running with an active battle_runtime_rules duration short enough for this smoke."
Write-Host ""

Test-Endpoint "finish -> resultReady/replayReady -> result/replay/mails/rating input" {
  $joins = @()

  try {
    $participants = @($SmokeHandle)
    for ($participantIndex = 2; $participantIndex -le 6; $participantIndex++) {
      $participants += "fs$RunSuffix$participantIndex"
    }

    $sessionsByHandle = @{}
    foreach ($handle in $participants) {
      $registration = Invoke-ContractJson "POST" "/identity/register" @{
        handle = $handle
        password = $SmokePassword
        skinId = "blue"
      }
      $missingRegistration = Test-Fields $registration @("handle", "session")
      if ($missingRegistration.Count -gt 0) {
        throw "Registration missing fields for handle=${handle}: $($missingRegistration -join ', ')"
      }
      if ([string]::IsNullOrWhiteSpace([string]$registration.session)) {
        throw "Registration returned an empty session for handle=$handle."
      }
      $sessionsByHandle[$handle] = [string]$registration.session
    }

    foreach ($handle in $participants) {
      $join = Invoke-ContractJson "POST" "/battle/queue/join" @{
        handle = $handle
        sessionToken = $sessionsByHandle[$handle]
        queueRequestId = "$RunSuffix-$handle"
        rating = "1200"
        skin = "blue"
        modeId = $ModeId
      }
      $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
      if ($missingJoin.Count -gt 0) {
        throw "Queue join missing fields for handle=${handle}: $($missingJoin -join ', ')"
      }
      $joins += $join
    }

    $join = $joins[0]
    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if (
        $null -ne $status.battleSession -and
        ($status.phase -eq "active" -or $status.phase -eq "finished")
      ) {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession) {
      throw "Battle session was not created."
    }
    if ($status.phase -ne "active" -and $status.phase -ne "finished") {
      throw "Expected active or finished battle before finish wait, got phase=$($status.phase)."
    }

    $battleId = $status.battleSession.battleId
    $finishedState = $null
    $state = $null
    for ($i = 0; $i -lt 60; $i++) {
      $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      if ($state.phase -eq "finished" -and $state.resultReady -eq $true -and $state.replayReady -eq $true) {
        $finishedState = $state
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $finishedState) {
      $lastPhase = if ($null -ne $state) { $state.phase } else { "<none>" }
      $lastResultReady = if ($null -ne $state) { $state.resultReady } else { "<none>" }
      $lastReplayReady = if ($null -ne $state) { $state.replayReady } else { "<none>" }
      throw "Battle did not reach finished + ready within 15s: phase=$lastPhase, resultReady=$lastResultReady, replayReady=$lastReplayReady."
    }

    $resultsPayload = Invoke-ContractJson "GET" "/battle/results?battleId=$([uri]::EscapeDataString($battleId))"
    $results = Test-ArrayEnvelope $resultsPayload "results"
    $relatedResults = @($results | Where-Object { $_.battleId -ceq $battleId -and $_.handle -ceq $SmokeHandle })
    if ($relatedResults.Count -lt 1) {
      $resultHandles = @($results | ForEach-Object { $_.handle })
      throw "No result for handle=$SmokeHandle in battleId=$battleId. Returned handles=$($resultHandles -join ', ')."
    }
    $currentResult = $relatedResults | Select-Object -First 1
    $missingResult = Test-Fields $currentResult @("battleId", "resultId", "handle", "ratingBefore", "ratingDelta", "ratingAfter")
    if ($missingResult.Count -gt 0) {
      throw "Result missing fields: $($missingResult -join ', ')."
    }
    if (-not (Test-NumberOrNull $currentResult.score)) {
      throw "Result score must be a number/null value: score=$($currentResult.score)."
    }
    if ($null -eq $currentResult.score) {
      throw "Authoritative settlement score must be present for server-owned result."
    }
    $expectedScore = Get-AuthoritativePlacementScore $currentResult.placement
    if ([Int32]$currentResult.score -ne $expectedScore) {
      throw "Authoritative settlement score mismatch: result score=$($currentResult.score), placement=$($currentResult.placement), expected=$expectedScore."
    }
    $invalidRatingFields = Test-NumberOrNullFields $currentResult @("ratingBefore", "ratingDelta", "ratingAfter")
    if ($invalidRatingFields.Count -gt 0) {
      throw "Result rating fields must be number/null values: $($invalidRatingFields -join ', ')."
    }
    if (
      $null -ne $currentResult.ratingBefore -and
      $null -ne $currentResult.ratingDelta -and
      $null -ne $currentResult.ratingAfter
    ) {
      $expectedRatingAfter = [decimal]$currentResult.ratingBefore + [decimal]$currentResult.ratingDelta
      if ([decimal]$currentResult.ratingAfter -ne $expectedRatingAfter) {
        throw "Rating arithmetic mismatch: ratingBefore=$($currentResult.ratingBefore), ratingDelta=$($currentResult.ratingDelta), ratingAfter=$($currentResult.ratingAfter), expected=$expectedRatingAfter."
      }
    }

    $secondaryHandle = $participants[1]
    $secondaryResult = @(
      $results |
        Where-Object { $_.battleId -ceq $battleId -and $_.handle -ceq $secondaryHandle } |
        Select-Object -First 1
    )
    if ($secondaryResult.Count -lt 1) {
      $resultHandles = @($results | ForEach-Object { $_.handle })
      throw "No secondary result for handle=$secondaryHandle in battleId=$battleId. Returned handles=$($resultHandles -join ', ')."
    }
    $invalidSecondaryRatingFields = Test-NumberOrNullFields $secondaryResult[0] @("ratingBefore", "ratingDelta", "ratingAfter")
    if ($invalidSecondaryRatingFields.Count -gt 0) {
      throw "Secondary result rating fields must be number/null values: $($invalidSecondaryRatingFields -join ', ')."
    }

    $handleResultsPayload = Invoke-ContractJson "GET" "/battle/results?handle=$([uri]::EscapeDataString($SmokeHandle))&limit=10"
    $handleResults = Test-ArrayEnvelope $handleResultsPayload "results"
    $handleResult = @(
      $handleResults |
        Where-Object { $_.battleId -ceq $battleId -and $_.resultId -ceq $currentResult.resultId -and $_.handle -ceq $SmokeHandle } |
        Select-Object -First 1
    )
    if ($handleResult.Count -lt 1) {
      $handleResultIds = @($handleResults | ForEach-Object { $_.resultId })
      throw "Handle-filtered battle results did not include current resultId=$($currentResult.resultId). Returned result ids=$($handleResultIds -join ', ')."
    }
    $invalidHandleRatingFields = Test-NumberOrNullFields $handleResult[0] @("ratingBefore", "ratingDelta", "ratingAfter")
    if ($invalidHandleRatingFields.Count -gt 0) {
      throw "Handle-filtered result rating fields must be number/null values: $($invalidHandleRatingFields -join ', ')."
    }
    if (
      $handleResult[0].ratingBefore -ne $currentResult.ratingBefore -or
      $handleResult[0].ratingDelta -ne $currentResult.ratingDelta -or
      $handleResult[0].ratingAfter -ne $currentResult.ratingAfter
    ) {
      throw "Handle-filtered result rating mismatch for resultId=$($currentResult.resultId)."
    }

    $replayPayload = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($battleId))?handle=$([uri]::EscapeDataString($SmokeHandle))"
    if (-not (Test-HasField $replayPayload "replay") -or $null -eq $replayPayload.replay) {
      throw "Replay catalog did not return replay for battleId=$battleId."
    }
    $replay = $replayPayload.replay
    $missingReplay = Test-Fields $replay @("replayId", "battleId", "playbackAvailable", "frames", "frameCount", "ratingBefore", "ratingDelta", "ratingAfter")
    if ($missingReplay.Count -gt 0) {
      throw "Replay missing fields: $($missingReplay -join ', ')"
    }
    if ($replay.battleId -ne $battleId -or $replay.replayId -ne $battleId) {
      throw "Replay identity mismatch: battleId=$($replay.battleId), replayId=$($replay.replayId), expected=$battleId."
    }
    if (-not (Test-NumberOrNull $replay.score)) {
      throw "Replay score must be a number/null value: score=$($replay.score)."
    }
    if ($null -eq $replay.score) {
      throw "Authoritative replay settlement score must be present."
    }
    if ([Int32]$replay.score -ne [Int32]$currentResult.score) {
      throw "Authoritative replay settlement score mismatch: replay score=$($replay.score), result score=$($currentResult.score)."
    }
    $invalidReplayRatingFields = Test-NumberOrNullFields $replay @("ratingBefore", "ratingDelta", "ratingAfter")
    if ($invalidReplayRatingFields.Count -gt 0) {
      throw "Replay rating fields must be number/null values: $($invalidReplayRatingFields -join ', ')."
    }
    if (
      $replay.ratingBefore -ne $currentResult.ratingBefore -or
      $replay.ratingDelta -ne $currentResult.ratingDelta -or
      $replay.ratingAfter -ne $currentResult.ratingAfter
    ) {
      throw "Replay rating mismatch: replay=($($replay.ratingBefore), $($replay.ratingDelta), $($replay.ratingAfter)); currentResult=($($currentResult.ratingBefore), $($currentResult.ratingDelta), $($currentResult.ratingAfter))."
    }
    Assert-ReplaySettlementMatchesResult $replay $currentResult $SmokeHandle "Primary replay detail" $true

    $secondaryReplayPayload = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($battleId))?handle=$([uri]::EscapeDataString($secondaryHandle))"
    if (-not (Test-HasField $secondaryReplayPayload "replay") -or $null -eq $secondaryReplayPayload.replay) {
      throw "Secondary replay catalog did not return replay for battleId=$battleId."
    }
    $secondaryReplay = $secondaryReplayPayload.replay
    $missingSecondaryReplay = Test-Fields $secondaryReplay @("replayId", "battleId", "handle", "score", "placement", "ratingBefore", "ratingDelta", "ratingAfter")
    if ($missingSecondaryReplay.Count -gt 0) {
      throw "Secondary replay missing fields: $($missingSecondaryReplay -join ', ')"
    }
    if ($secondaryReplay.battleId -ne $battleId -or $secondaryReplay.replayId -ne $battleId) {
      throw "Secondary replay identity mismatch: battleId=$($secondaryReplay.battleId), replayId=$($secondaryReplay.replayId), expected=$battleId."
    }
    Assert-ReplaySettlementMatchesResult $secondaryReplay $secondaryResult[0] $secondaryHandle "Secondary replay detail" $true

    $replayCatalogPayload = Invoke-ContractJson "GET" "/replay/catalog?limit=10"
    $replayCatalog = Test-ArrayEnvelope $replayCatalogPayload "replays"
    $catalogReplay = @($replayCatalog | Where-Object { $_.replayId -ceq $battleId -and $_.battleId -ceq $battleId } | Select-Object -First 1)
    if ($catalogReplay.Count -lt 1) {
      $catalogReplayIds = @($replayCatalog | ForEach-Object { $_.replayId })
      throw "Replay catalog list did not include replayId=$battleId. Returned replay ids=$($catalogReplayIds -join ', ')."
    }
    $missingCatalogReplay = Test-Fields $catalogReplay[0] @("replayId", "battleId", "ratingBefore", "ratingDelta", "ratingAfter")
    if ($missingCatalogReplay.Count -gt 0) {
      throw "Replay catalog item missing fields: $($missingCatalogReplay -join ', ')."
    }
    $invalidCatalogReplayRatingFields = Test-NumberOrNullFields $catalogReplay[0] @("ratingBefore", "ratingDelta", "ratingAfter")
    if ($invalidCatalogReplayRatingFields.Count -gt 0) {
      throw "Replay catalog item rating fields must be number/null values: $($invalidCatalogReplayRatingFields -join ', ')."
    }
    if (
      $catalogReplay[0].ratingBefore -ne $currentResult.ratingBefore -or
      $catalogReplay[0].ratingDelta -ne $currentResult.ratingDelta -or
      $catalogReplay[0].ratingAfter -ne $currentResult.ratingAfter
    ) {
      throw "Replay catalog item rating mismatch for replayId=$battleId."
    }

    $secondaryReplayCatalogPayload = Invoke-ContractJson "GET" "/replay/catalog?limit=10&handle=$([uri]::EscapeDataString($secondaryHandle))"
    $secondaryReplayCatalog = Test-ArrayEnvelope $secondaryReplayCatalogPayload "replays"
    $secondaryCatalogReplay = @($secondaryReplayCatalog | Where-Object { $_.replayId -ceq $battleId -and $_.battleId -ceq $battleId } | Select-Object -First 1)
    if ($secondaryCatalogReplay.Count -lt 1) {
      $catalogReplayIds = @($secondaryReplayCatalog | ForEach-Object { $_.replayId })
      throw "Secondary replay catalog list did not include replayId=$battleId. Returned replay ids=$($catalogReplayIds -join ', ')."
    }
    $missingSecondaryCatalogReplay = Test-Fields $secondaryCatalogReplay[0] @("replayId", "battleId", "score", "placement", "ratingBefore", "ratingDelta", "ratingAfter")
    if ($missingSecondaryCatalogReplay.Count -gt 0) {
      throw "Secondary replay catalog item missing fields: $($missingSecondaryCatalogReplay -join ', ')."
    }
    Assert-ReplaySettlementMatchesResult $secondaryCatalogReplay[0] $secondaryResult[0] $secondaryHandle "Secondary replay catalog item" $false

    $frameArrayCount = if ($null -eq $replay.frames) {
      0
    } elseif ($replay.frames -is [System.Array]) {
      $replay.frames.Count
    } else {
      1
    }
    $frameCount = [Int32]$replay.frameCount
    if ($frameArrayCount -lt 1 -and $frameCount -lt 1) {
      throw "Replay did not include frames or frameCount: frames=$frameArrayCount, frameCount=$frameCount."
    }
    if ($frameArrayCount -gt 0 -and $frameCount -ne $frameArrayCount) {
      throw "Authoritative sparse replay frameCount mismatch: frames=$frameArrayCount, frameCount=$frameCount."
    }
    if ($frameCount -ge 2 -and $replay.playbackAvailable -ne $true) {
      throw "Authoritative replay with playable frames should be playable: playbackAvailable=$($replay.playbackAvailable), frameCount=$frameCount."
    }

    $postedComment = Invoke-ContractJson "POST" "/replay/catalog/$([uri]::EscapeDataString($battleId))/comments" @{
      authorHandle = $secondaryHandle
      body = "Finish smoke replay comment $RunId $ModeId"
    }
    $missingPostedCommentEnvelope = Test-Fields $postedComment @("comment")
    if ($missingPostedCommentEnvelope.Count -gt 0) {
      throw "Posted replay comment response missing envelope fields: $($missingPostedCommentEnvelope -join ', ')."
    }
    $missingPostedComment = Test-Fields $postedComment.comment @("id", "replayId", "authorHandle", "body", "createdAt")
    if ($missingPostedComment.Count -gt 0) {
      throw "Posted replay comment missing fields: $($missingPostedComment -join ', ')."
    }
    if ($postedComment.comment.replayId -ne $battleId -or $postedComment.comment.authorHandle -ne $secondaryHandle) {
      throw "Posted replay comment identity mismatch: replayId=$($postedComment.comment.replayId), author=$($postedComment.comment.authorHandle), expected replay=$battleId author=$secondaryHandle."
    }
    $commentsPayload = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($battleId))/comments"
    $comments = Test-ArrayEnvelope $commentsPayload "comments"
    $commentMatch = @($comments | Where-Object { $_.id -ceq $postedComment.comment.id -and $_.replayId -ceq $battleId } | Select-Object -First 1)
    if ($commentMatch.Count -lt 1) {
      $commentIds = @($comments | ForEach-Object { $_.id })
      throw "Replay comments did not include the posted comment id=$($postedComment.comment.id). Returned comment ids=$($commentIds -join ', ')."
    }

    $mailsPayload = Invoke-ContractJson "GET" "/mails?ownerHandle=$([uri]::EscapeDataString($SmokeHandle))"
    $mails = Test-ArrayEnvelope $mailsPayload "mails"
    $battleMailId = "mail-battle-$($currentResult.resultId)"
    $battleMail = @($mails | Where-Object { $_.id -ceq $battleMailId -and $_.ownerHandle -ceq $SmokeHandle } | Select-Object -First 1)
    if ($battleMail.Count -lt 1) {
      $mailIds = @($mails | ForEach-Object { $_.id })
      throw "No battle result mail found for resultId=$($currentResult.resultId). Expected id=$battleMailId. Returned mail ids=$($mailIds -join ', ')."
    }
    $missingBattleMail = Test-Fields $battleMail[0] @("id", "ownerHandle", "kind", "subject", "excerpt", "senderLabel", "unread", "important", "createdAt")
    if ($missingBattleMail.Count -gt 0) {
      throw "Battle result mail missing fields: $($missingBattleMail -join ', ')."
    }
    if ($battleMail[0].kind -ne "battle") {
      throw "Battle result mail kind mismatch: id=$battleMailId, kind=$($battleMail[0].kind)."
    }
    Assert-ReplaySourceMail $battleMail[0] $battleId $SmokeHandle "Battle result mail"

    $ratingMailChecked = "not-required"
    if ($null -ne $currentResult.ratingDelta -and [decimal]$currentResult.ratingDelta -ne 0) {
      $ratingMailId = "mail-rating-$($currentResult.resultId)"
      $ratingMail = @($mails | Where-Object { $_.id -ceq $ratingMailId -and $_.ownerHandle -ceq $SmokeHandle } | Select-Object -First 1)
      if ($ratingMail.Count -lt 1) {
        $mailIds = @($mails | ForEach-Object { $_.id })
        throw "No rating mail found for non-zero ratingDelta=$($currentResult.ratingDelta), resultId=$($currentResult.resultId). Expected id=$ratingMailId. Returned mail ids=$($mailIds -join ', ')."
      }
      $missingRatingMail = Test-Fields $ratingMail[0] @("id", "ownerHandle", "kind", "subject", "excerpt", "senderLabel", "unread", "important", "createdAt")
      if ($missingRatingMail.Count -gt 0) {
        throw "Rating mail missing fields: $($missingRatingMail -join ', ')."
      }
      Assert-ReplaySourceMail $ratingMail[0] $battleId $SmokeHandle "Rating mail"
      $ratingMailChecked = "present"
    }

    $detailParts = @(
      "modeId=$ModeId",
      "battleId=$battleId",
      "resultId=$($currentResult.resultId)",
      "resultCount=$($relatedResults.Count)",
      "handleResultCount=$($handleResults.Count)",
      "score=$($currentResult.score)",
      "replayScore=$($replay.score)",
      "secondaryHandle=$secondaryHandle",
      "secondaryReplayScore=$($secondaryReplay.score)",
      "frames=$frameArrayCount",
      "frameCount=$frameCount",
      "playbackAvailable=$($replay.playbackAvailable)",
      "replayComments=$($comments.Count)",
      "postedComment=$($postedComment.comment.id)",
      "catalogRating=$($catalogReplay[0].ratingDelta)",
      "secondaryCatalogRating=$($secondaryReplayCatalog[0].ratingDelta)",
      "mails=$($mails.Count)",
      "battleMail=$battleMailId",
      "ratingDelta=$($currentResult.ratingDelta)",
      "ratingMail=$ratingMailChecked"
    )
    return ($detailParts -join "; ")

    "battleId=$battleId; resultId=$($currentResult.resultId); resultCount=$($relatedResults.Count); handleResultCount=$($handleResults.Count); score=$($currentResult.score); replayScore=$($replay.score); secondaryHandle=$secondaryHandle; secondaryReplayScore=$($secondaryReplay.score); frames=$frameArrayCount; frameCount=$frameCount; playbackAvailable=$($replay.playbackAvailable); catalogRating=$($catalogReplay[0].ratingDelta); secondaryCatalogRating=$($secondaryCatalogReplay[0].ratingDelta); mails=$($mails.Count); battleMail=$battleMailId; ratingDelta=$($currentResult.ratingDelta); ratingMail=$ratingMailChecked"
  } finally {
    foreach ($queuedJoin in @($joins)) {
      if ($null -ne $queuedJoin -and (Test-HasField $queuedJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $queuedJoin.ticketId } | Out-Null } catch {}
      }
    }
  }
}

Write-Host "Results:"
foreach ($result in $Results) {
  $status = if ($result.Passed) { "PASS" } else { "FAIL" }
  Write-Host ("[{0}] {1} - {2}" -f $status, $result.Name, $result.Detail)
}

if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
  $summaryResults = @($Results | ForEach-Object {
    [pscustomobject]@{
      name = [string]$_.Name
      passed = [bool]$_.Passed
      detail = [string]$_.Detail
    }
  })
  $summary = [ordered]@{
    modeId = $ModeId
    baseUrl = $BaseUrl
    generatedAt = [DateTimeOffset]::UtcNow.ToString("O")
    passed = (@($summaryResults | Where-Object { -not $_.passed }).Count -eq 0)
    results = $summaryResults
  }
  $summaryDir = Split-Path -Parent $SummaryPath
  if (-not [string]::IsNullOrWhiteSpace($summaryDir)) {
    New-Item -ItemType Directory -Force -Path $summaryDir | Out-Null
  }
  $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $SummaryPath -Encoding UTF8
}

$failures = @($Results | Where-Object { -not $_.Passed })
if ($failures.Count -gt 0) {
  Write-Host ""
  Write-Host "Authoritative finish smoke failed: $($failures.Count) failure(s)."
  exit 1
}

Write-Host ""
Write-Host "Authoritative finish smoke passed."
