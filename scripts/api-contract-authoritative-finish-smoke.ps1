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

  $uri = "$BaseUrl$Path"
  $parameters = @{
    Method = $Method
    Uri = $uri
    Headers = @{ "Accept" = "application/json" }
    TimeoutSec = 8
  }

  if ($null -ne $Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }

  try {
    return Invoke-RestMethod @parameters
  } catch {
    throw "Request failed: $Method $uri :: $($_.Exception.Message)"
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
$SmokeHandle = "finish-smoke-player-$RunId"

Write-Host "Authoritative finish/result/replay smoke"
Write-Host "Base URL: $BaseUrl"
Write-Host "Note: backend must already be running with a short SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS."
Write-Host ""

Test-Endpoint "finish -> resultReady/replayReady -> result/replay/mails/rating input" {
  $join = $null

  try {
    $join = Invoke-ContractJson "POST" "/battle/queue/join" @{
      handle = $SmokeHandle
      rating = "1200"
      skin = "blue"
    }
    $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoin.Count -gt 0) {
      throw "Queue join missing fields: $($missingJoin -join ', ')"
    }

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

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

    $replayPayload = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($battleId))"
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

    "battleId=$battleId; resultId=$($currentResult.resultId); resultCount=$($relatedResults.Count); handleResultCount=$($handleResults.Count); score=$($currentResult.score); replayScore=$($replay.score); frames=$frameArrayCount; frameCount=$frameCount; playbackAvailable=$($replay.playbackAvailable); catalogRating=$($catalogReplay[0].ratingDelta); mails=$($mails.Count); battleMail=$battleMailId; ratingDelta=$($currentResult.ratingDelta); ratingMail=$ratingMailChecked"
  } finally {
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Write-Host "Results:"
foreach ($result in $Results) {
  $status = if ($result.Passed) { "PASS" } else { "FAIL" }
  Write-Host ("[{0}] {1} - {2}" -f $status, $result.Name, $result.Detail)
}

$failures = @($Results | Where-Object { -not $_.Passed })
if ($failures.Count -gt 0) {
  Write-Host ""
  Write-Host "Authoritative finish smoke failed: $($failures.Count) failure(s)."
  exit 1
}

Write-Host ""
Write-Host "Authoritative finish smoke passed."
