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

function Invoke-ContractJsonExpectError {
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
    Invoke-RestMethod @parameters | Out-Null
    throw "Expected request to fail: $Method $uri"
  } catch {
    $response = $_.Exception.Response
    if ($null -eq $response) {
      throw
    }

    $statusCode = [int]$response.StatusCode
    $payload = $null

    try {
      $bodyText = $_.ErrorDetails.Message
      if ($null -ne $response.Content) {
        $contentText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not [string]::IsNullOrWhiteSpace($contentText)) {
          $bodyText = $contentText
        }
      } elseif ([string]::IsNullOrWhiteSpace($bodyText) -and $response.PSObject.Methods.Name -contains "GetResponseStream") {
        $stream = $response.GetResponseStream()
        if ($null -ne $stream) {
          $reader = New-Object System.IO.StreamReader($stream)
          $bodyText = $reader.ReadToEnd()
        }
      }

      if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
        $payload = $bodyText | ConvertFrom-Json
        if (
          $null -ne $payload -and
          (Test-HasField $payload "message") -and
          -not (Test-HasField $payload "error")
        ) {
          $payload | Add-Member -NotePropertyName "error" -NotePropertyValue $payload.message -Force
        }
        if ($null -ne $payload -and (Test-HasField $payload "message")) {
          $legacyCode = Convert-BattleApiMessageToLegacyCode ([string]$payload.message)
          if (-not [string]::IsNullOrWhiteSpace($legacyCode)) {
            $payload | Add-Member -NotePropertyName "code" -NotePropertyValue $legacyCode -Force
            $payload | Add-Member -NotePropertyName "error" -NotePropertyValue $legacyCode -Force
          }
        }
      }
    } catch {
      $payload = $null
    }

    return [pscustomobject]@{
      StatusCode = $statusCode
      Payload = $payload
    }
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
    '^/battle/rooms/snapshot$' {
      $convertedMethod = "POST"
      $convertedPath = "/battleroomsnapshot"
      $convertedBody = Add-BattleUserToken (Merge-ContractBody $Body @{
        roomId = Get-ContractQueryValue $Path "roomId"
      })
      break
    }
    '^/battle/rooms/heartbeat$' {
      $convertedMethod = "POST"
      $convertedPath = "/battleroomheartbeat"
      $convertedBody = Add-BattleUserToken (Copy-ContractBody $Body)
      break
    }
    '^/battle/commands$' {
      $convertedMethod = "POST"
      $convertedPath = "/battlecommand"
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

function Convert-BattleApiMessageToLegacyCode {
  param([string]$Message)

  switch -Regex ($Message) {
    '^roomId is required\.?$' { return "invalid_room_id" }
    '^Battle room was not found\.?$' { return "room_not_found" }
    '^Invalid battle command field: PrimaryHeld\.?$' { return "missing_primary_held" }
    '^Invalid battle command field: ReloadPressed\.?$' { return "missing_reload_pressed" }
    '^Invalid battle command field: SwitchWeaponDirection\.?$' { return "missing_switch_weapon_direction" }
    default { return "" }
  }
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

  $copy["userToken"] = Get-BattleApiUserToken
  return $copy
}

function Get-BattleApiUserToken {
  if ([string]::IsNullOrWhiteSpace([string]$Script:BattleApiUserToken)) {
    $handle = New-BattleSmokeHandle "api"
    $Script:BattleApiUserToken = Get-SmokeSessionToken $handle
  }

  return $Script:BattleApiUserToken
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

function Test-NoFields {
  param(
    [object]$Value,
    [string[]]$Fields
  )

  $present = @($Fields | Where-Object { Test-HasField $Value $_ })
  return $present
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

function Test-PlayerPistolWeaponSync {
  param(
    [object]$Player,
    [string]$Context,
    [int]$ExpectedAmmoInMagazine = -1,
    [int]$ExpectedReserveAmmo = -1,
    [int]$ExpectedReloadRemainingMs = -1
  )

  if (-not (Test-HasField $Player "weapons")) {
    throw "$Context missing weapons field."
  }
  if ($null -eq $Player.weapons -or $Player.weapons -isnot [System.Array]) {
    throw "$Context weapons field is not an array."
  }
  $weapons = @($Player.weapons | ForEach-Object { $_ })
  if ($weapons.Count -lt 1) {
    throw "$Context expected at least one server weapon."
  }

  $missingPlayerWeaponFields = Test-Fields $Player @("currentWeaponIndex", "currentWeaponKind", "ammoInMagazine", "magazineSize", "reserveAmmo", "fireCooldownMs", "reloadRemainingMs")
  if ($missingPlayerWeaponFields.Count -gt 0) {
    throw "$Context player missing current weapon fields: $($missingPlayerWeaponFields -join ', ')"
  }

  $currentWeaponIndex = [int]$Player.currentWeaponIndex
  if ($currentWeaponIndex -lt 0 -or $currentWeaponIndex -ge $weapons.Count) {
    throw "$Context currentWeaponIndex out of range: index=$currentWeaponIndex, weapons=$($weapons.Count)."
  }

  $currentWeapon = $weapons[$currentWeaponIndex]
  $missingCurrentWeaponFields = Test-Fields $currentWeapon @("weaponKind", "ammoInMagazine", "magazineSize", "reserveAmmo", "fireCooldownMs", "reloadRemainingMs")
  if ($missingCurrentWeaponFields.Count -gt 0) {
    throw "$Context current weapon missing fields: $($missingCurrentWeaponFields -join ', ')"
  }
  if (
    $currentWeapon.weaponKind -ne $Player.currentWeaponKind -or
    $currentWeapon.ammoInMagazine -ne $Player.ammoInMagazine -or
    $currentWeapon.magazineSize -ne $Player.magazineSize -or
    $currentWeapon.reserveAmmo -ne $Player.reserveAmmo -or
    $currentWeapon.fireCooldownMs -ne $Player.fireCooldownMs -or
    $currentWeapon.reloadRemainingMs -ne $Player.reloadRemainingMs
  ) {
    throw "$Context current weapon/scalar mismatch: weapon[$currentWeaponIndex]=$($currentWeapon.weaponKind) $($currentWeapon.ammoInMagazine)/$($currentWeapon.magazineSize)/$($currentWeapon.reserveAmmo) cooldown=$($currentWeapon.fireCooldownMs) reload=$($currentWeapon.reloadRemainingMs); scalar=$($Player.currentWeaponKind) $($Player.ammoInMagazine)/$($Player.magazineSize)/$($Player.reserveAmmo) cooldown=$($Player.fireCooldownMs) reload=$($Player.reloadRemainingMs)"
  }

  $pistol = @($weapons | Where-Object { $_.weaponKind -ceq "Pistol" } | Select-Object -First 1)
  if ($pistol.Count -lt 1) {
    throw "$Context expected a Pistol weapon in inventory, got $($weapons.Count) weapons."
  }

  $weapon = $pistol[0]
  $missingWeaponFields = Test-Fields $weapon @("weaponKind", "ammoInMagazine", "magazineSize", "reserveAmmo", "fireCooldownMs", "reloadRemainingMs")
  if ($missingWeaponFields.Count -gt 0) {
    throw "$Context pistol weapon missing fields: $($missingWeaponFields -join ', ')"
  }

  if ($weapon.weaponKind -ne "Pistol") {
    throw "$Context expected Pistol weapon, got $($weapon.weaponKind)."
  }

  if ($ExpectedAmmoInMagazine -ge 0 -and $weapon.ammoInMagazine -ne $ExpectedAmmoInMagazine) {
    throw "$Context expected pistol.ammoInMagazine=$ExpectedAmmoInMagazine, got $($weapon.ammoInMagazine)."
  }
  if ($ExpectedReserveAmmo -ge 0 -and $weapon.reserveAmmo -ne $ExpectedReserveAmmo) {
    throw "$Context expected pistol.reserveAmmo=$ExpectedReserveAmmo, got $($weapon.reserveAmmo)."
  }
  if ($ExpectedReloadRemainingMs -ge 0 -and $weapon.reloadRemainingMs -ne $ExpectedReloadRemainingMs) {
    throw "$Context expected pistol.reloadRemainingMs=$ExpectedReloadRemainingMs, got $($weapon.reloadRemainingMs)."
  }
}

function New-BattleSmokeHandle {
  param(
    [string]$Label
  )

  $Script:BattleHandleCounter += 1
  $safeLabel = ([string]$Label).ToLowerInvariant() -replace "[^a-z0-9_-]", ""
  if ([string]::IsNullOrWhiteSpace($safeLabel)) {
    $safeLabel = "p"
  }

  $prefix = "c$Script:BattleRunSuffix$($Script:BattleHandleCounter.ToString("00"))"
  $maxLabelLength = [Math]::Max(0, 16 - $prefix.Length)
  if ($safeLabel.Length -gt $maxLabelLength) {
    $safeLabel = $safeLabel.Substring(0, $maxLabelLength)
  }

  return "$prefix$safeLabel"
}

function Get-SmokeSessionToken {
  param(
    [string]$Handle
  )

  if ($Script:RegisteredSmokeSessions.ContainsKey($Handle)) {
    return $Script:RegisteredSmokeSessions[$Handle]
  }

  $registration = Invoke-ContractJson "POST" "/identity/register" @{
    handle = $Handle
    password = $Script:SmokePassword
    skinId = "blue"
  }
  $missingRegistration = Test-Fields $registration @("handle", "session")
  if ($missingRegistration.Count -gt 0) {
    throw "Battle smoke identity registration missing fields for handle=${Handle}: $($missingRegistration -join ', ')"
  }
  if ([string]::IsNullOrWhiteSpace([string]$registration.session)) {
    throw "Battle smoke identity registration returned an empty session for handle=$Handle."
  }

  $Script:RegisteredSmokeSessions[$Handle] = [string]$registration.session
  return $Script:RegisteredSmokeSessions[$Handle]
}

function Join-AuthenticatedBattleQueue {
  param(
    [string]$Handle,
    [string]$Rating = "1200",
    [string]$Skin = "blue",
    [string]$QueueRequestId = "",
    [string]$ModeId = "default"
  )

  $body = @{
    handle = $Handle
    sessionToken = Get-SmokeSessionToken $Handle
    rating = $Rating
    skin = $Skin
  }
  if (-not [string]::IsNullOrWhiteSpace($QueueRequestId)) {
    $body.queueRequestId = $QueueRequestId
  }
  if (-not [string]::IsNullOrWhiteSpace($ModeId)) {
    $body.modeId = $ModeId
  }

  Invoke-ContractJson "POST" "/battle/queue/join" $body
}

function Add-AuthenticatedBattlePeers {
  param(
    [object]$PrimaryJoin,
    [string]$Label,
    [int]$Count,
    [string]$Rating = "1200",
    [string]$Skin = "blue"
  )

  $joins = @()
  for ($peerIndex = 1; $peerIndex -le $Count; $peerIndex++) {
    $peerHandle = New-BattleSmokeHandle "$Label$peerIndex"
    $peerJoin = Join-AuthenticatedBattleQueue -Handle $peerHandle -Rating $Rating -Skin $Skin -QueueRequestId "contract-$peerHandle"
    if ($null -ne $PrimaryJoin -and (Test-HasField $PrimaryJoin "roomId") -and $peerJoin.roomId -ne $PrimaryJoin.roomId) {
      throw "Authenticated battle peer joined a different room: primary=$($PrimaryJoin.roomId), peer=$($peerJoin.roomId), handle=$peerHandle."
    }
    $joins += $peerJoin
  }

  return ,$joins
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
$Script:BattleRunSuffix = [string]$RunId
$Script:BattleRunSuffix = $Script:BattleRunSuffix.Substring([Math]::Max(0, $Script:BattleRunSuffix.Length - 7))
$Script:BattleHandleCounter = 0
$Script:SmokePassword = "pass1234"
$Script:RegisteredSmokeSessions = @{}
$Script:BattleApiUserToken = ""
$SmokeSource = "contract-smoke-source-$RunId"
$SmokeTarget = "contract-smoke-target-$RunId"
$SmokePlayer = "contract-smoke-player-$RunId"

Write-Host "API contract field smoke"
Write-Host "Base URL: $BaseUrl"
Write-Host ""

Test-Endpoint "GET /health" {
  $payload = Invoke-ContractJson "GET" "/health"
  $missing = Test-Fields $payload @("status", "service")
  if ($missing.Count -gt 0) {
    throw "Missing fields: $($missing -join ', ')"
  }
  "status=$($payload.status)"
}

Test-Endpoint "GET /identity/accounts" {
  $payload = Invoke-ContractJson "GET" "/identity/accounts"
  $accounts = Test-ArrayEnvelope $payload "accounts"
  if ($accounts.Count -gt 0) {
    $missing = Test-Fields $accounts[0] @("handle", "displayName", "skinId")
    if ($missing.Count -gt 0) {
      throw "First account missing fields: $($missing -join ', ')"
    }
    $legacyPresent = Test-NoFields $accounts[0] @("id", "userId", "name", "playerName")
    if ($legacyPresent.Count -gt 0) {
      throw "First account contains legacy fields: $($legacyPresent -join ', ')"
    }
  }
  "accounts=$($accounts.Count); item fields checked and legacy identity fields rejected when present"
}

Test-Endpoint "GET /bots/profiles" {
  $payload = Invoke-ContractJson "GET" "/bots/profiles"
  $profiles = Test-ArrayEnvelope $payload "profiles"
  if ($profiles.Count -gt 0) {
    $missing = Test-Fields $profiles[0] @("botId", "handle", "displayName", "initialRating", "profileTone", "strategyLabel", "skin")
    if ($missing.Count -gt 0) {
      throw "First bot profile missing fields: $($missing -join ', ')"
    }
    $legacyPresent = Test-NoFields $profiles[0] @("id", "name", "rating", "playerName")
    if ($legacyPresent.Count -gt 0) {
      throw "First bot profile contains legacy fields: $($legacyPresent -join ', ')"
    }
    $missingSkin = Test-Fields $profiles[0].skin @("avatarKey", "textureKey", "label")
    if ($missingSkin.Count -gt 0) {
      throw "First bot profile skin missing fields: $($missingSkin -join ', ')"
    }
    $legacySkinPresent = Test-NoFields $profiles[0].skin @("id", "name")
    if ($legacySkinPresent.Count -gt 0) {
      throw "First bot profile skin contains legacy fields: $($legacySkinPresent -join ', ')"
    }
  }
  "profiles=$($profiles.Count); formal bot fields checked and legacy bot fields rejected when present"
}

Test-Endpoint "GET /mails?ownerHandle=admin" {
  $payload = Invoke-ContractJson "GET" "/mails?ownerHandle=admin"
  $mails = Test-ArrayEnvelope $payload "mails"
  if ($mails.Count -gt 0) {
    $missing = Test-Fields $mails[0] @("id", "ownerHandle", "kind", "subject", "excerpt", "senderLabel", "unread", "important", "createdAt")
    if ($missing.Count -gt 0) {
      throw "First mail missing fields: $($missing -join ', ')"
    }
    $legacyPresent = Test-NoFields $mails[0] @("mailId", "owner", "sender", "createdAtLabel")
    if ($legacyPresent.Count -gt 0) {
      throw "First mail contains legacy fields: $($legacyPresent -join ', ')"
    }
  }
  "mails=$($mails.Count); item fields checked and legacy mail fields rejected when present"
}

Test-Endpoint "POST /social/friend-requests" {
  $payload = Invoke-ContractJson "POST" "/social/friend-requests" @{
    sourceHandle = $SmokeSource
    targetHandle = $SmokeTarget
  }
  $missingEnvelope = Test-Fields $payload @("created", "alreadySent", "request", "mail")
  if ($missingEnvelope.Count -gt 0) {
    throw "Missing envelope fields: $($missingEnvelope -join ', ')"
  }
  $missingRequest = Test-Fields $payload.request @("id", "sourceHandle", "targetHandle", "createdAt", "status", "respondedAt")
  if ($missingRequest.Count -gt 0) {
    throw "Request missing fields: $($missingRequest -join ', ')"
  }
  $legacyRequestPresent = Test-NoFields $payload.request @("requestId", "fromHandle", "toHandle", "playerName")
  if ($legacyRequestPresent.Count -gt 0) {
    throw "Friend request contains legacy fields: $($legacyRequestPresent -join ', ')"
  }
  if ($null -ne $payload.mail) {
    $missingMail = Test-Fields $payload.mail @("id", "ownerHandle", "kind", "subject", "excerpt", "senderLabel", "unread", "important", "createdAt")
    if ($missingMail.Count -gt 0) {
      throw "Friend request mail missing fields: $($missingMail -join ', ')"
    }
    $legacyMailPresent = Test-NoFields $payload.mail @("mailId", "owner", "sender", "createdAtLabel")
    if ($legacyMailPresent.Count -gt 0) {
      throw "Friend request mail contains legacy fields: $($legacyMailPresent -join ', ')"
    }
  }
  "created=$($payload.created); request fields checked and legacy social fields rejected"
}

Test-Endpoint "POST/GET /forum/topics formal fields" {
  $created = Invoke-ContractJson "POST" "/forum/topics" @{
    title = "Contract smoke topic $RunId"
    body = "Contract smoke body $RunId"
    tag = "contract"
    author = $SmokePlayer
  }
  $missingCreatedEnvelope = Test-Fields $created @("topic")
  if ($missingCreatedEnvelope.Count -gt 0) {
    throw "Created forum response missing envelope fields: $($missingCreatedEnvelope -join ', ')"
  }

  $topic = $created.topic
  $missingTopic = Test-Fields $topic @("id", "title", "author", "excerpt", "tag", "replies", "updatedAt", "createdAt", "body", "replyItems", "viewerVote", "score")
  if ($missingTopic.Count -gt 0) {
    throw "Created forum topic missing fields: $($missingTopic -join ', ')"
  }
  $legacyTopicPresent = Test-NoFields $topic @("threadId", "authorHandle", "replyCount", "playerName")
  if ($legacyTopicPresent.Count -gt 0) {
    throw "Created forum topic contains legacy fields: $($legacyTopicPresent -join ', ')"
  }

  $replyPayload = Invoke-ContractJson "POST" "/forum/topics/$([uri]::EscapeDataString($topic.id))/replies" @{
    body = "Contract smoke reply $RunId"
    author = $SmokePlayer
  }
  $replyTopic = $replyPayload.topic
  $replyItems = @($replyTopic.replyItems)
  if ($replyItems.Count -lt 1) {
    throw "Created forum reply was not returned in replyItems."
  }
  $reply = $replyItems[$replyItems.Count - 1]
  $missingReply = Test-Fields $reply @("id", "author", "body", "publishedAt", "viewerVote", "score")
  if ($missingReply.Count -gt 0) {
    throw "Created forum reply missing fields: $($missingReply -join ', ')"
  }
  $legacyReplyPresent = Test-NoFields $reply @("replyId", "authorHandle", "playerName")
  if ($legacyReplyPresent.Count -gt 0) {
    throw "Created forum reply contains legacy fields: $($legacyReplyPresent -join ', ')"
  }

  $loaded = Invoke-ContractJson "GET" "/forum/topics/$([uri]::EscapeDataString($topic.id))"
  $missingLoadedEnvelope = Test-Fields $loaded @("topic")
  if ($missingLoadedEnvelope.Count -gt 0) {
    throw "Loaded forum response missing envelope fields: $($missingLoadedEnvelope -join ', ')"
  }

  "topicId=$($loaded.topic.id); replyItems=$(@($loaded.topic.replyItems).Count); legacy forum fields rejected"
}

Test-Endpoint "POST /governance/contribution-adjustments" {
  $payload = Invoke-ContractJson "POST" "/governance/contribution-adjustments" @{
    actorHandle = "admin"
    targetHandle = $SmokeTarget
    delta = 1
    reason = "contract smoke"
    sourceLabel = "contract smoke"
    sourcePath = "/contract-smoke"
  }
  $missingEnvelope = Test-Fields $payload @("ok", "adjustment", "mail")
  if ($missingEnvelope.Count -gt 0) {
    throw "Contribution adjustment response missing envelope fields: $($missingEnvelope -join ', ')"
  }
  $missingAdjustment = Test-Fields $payload.adjustment @("id", "actorHandle", "targetHandle", "delta", "reason", "createdAt", "sourceLabel", "sourcePath")
  if ($missingAdjustment.Count -gt 0) {
    throw "Contribution adjustment missing fields: $($missingAdjustment -join ', ')"
  }
  $legacyAdjustmentPresent = Test-NoFields $payload.adjustment @("actor", "target", "playerName", "userId")
  if ($legacyAdjustmentPresent.Count -gt 0) {
    throw "Contribution adjustment contains legacy fields: $($legacyAdjustmentPresent -join ', ')"
  }
  "adjustmentId=$($payload.adjustment.id); formal contribution adjustment fields checked"
}

Test-Endpoint "POST /governance/admin-notifications" {
  $expectedGovernanceActor = if ($SmokePlayer.Length -gt 32) { $SmokePlayer.Substring(0, 32) } else { $SmokePlayer }
  $payload = Invoke-ContractJson "POST" "/governance/admin-notifications" @{
    actorHandle = $SmokePlayer
    kind = "bot_suggestion"
    targetType = "bot"
    targetId = "contract-smoke-bot-$RunId"
    targetTitle = "Contract smoke bot"
    targetPath = "/profile/contract-smoke-bot"
    body = "Contract smoke governance notification"
  }
  $missingEnvelope = Test-Fields $payload @("ok", "notification", "mail")
  if ($missingEnvelope.Count -gt 0) {
    throw "Governance notification response missing envelope fields: $($missingEnvelope -join ', ')"
  }
  $missingNotification = Test-Fields $payload.notification @("id", "actorHandle", "kind", "targetType", "targetId", "targetTitle", "targetPath", "body", "createdAt", "mailId")
  if ($missingNotification.Count -gt 0) {
    throw "Governance notification missing fields: $($missingNotification -join ', ')"
  }
  $legacyNotificationPresent = Test-NoFields $payload.notification @("mail", "mailID", "mail_id", "actor")
  if ($legacyNotificationPresent.Count -gt 0) {
    throw "Governance notification contains legacy fields: $($legacyNotificationPresent -join ', ')"
  }
  if ($payload.notification.actorHandle -ne $expectedGovernanceActor) {
    throw "Governance notification actor normalization mismatch: $($payload.notification.actorHandle)"
  }
  $missingMail = Test-Fields $payload.mail @(
    "id",
    "ownerHandle",
    "kind",
    "subject",
    "excerpt",
    "senderLabel",
    "unread",
    "important",
    "createdAt",
    "governanceActorHandle",
    "governanceTargetPath",
    "governanceTargetLabel"
  )
  if ($missingMail.Count -gt 0) {
    throw "Governance notification mail missing fields: $($missingMail -join ', ')"
  }
  if ($payload.mail.governanceActorHandle -ne $expectedGovernanceActor) {
    throw "Governance response mail actor metadata mismatch: $($payload.mail.governanceActorHandle)"
  }
  if ($payload.mail.governanceTargetPath -ne "/profile/contract-smoke-bot") {
    throw "Governance response mail target path metadata mismatch: $($payload.mail.governanceTargetPath)"
  }
  if ($payload.mail.governanceTargetLabel -ne "Contract smoke bot") {
    throw "Governance response mail target label metadata mismatch: $($payload.mail.governanceTargetLabel)"
  }

  $mailsPayload = Invoke-ContractJson "GET" "/mails?ownerHandle=admin"
  $mails = Test-ArrayEnvelope $mailsPayload "mails"
  $createdMail = @($mails | Where-Object { $_.id -ceq $payload.notification.mailId } | Select-Object -First 1)
  if ($createdMail.Count -lt 1) {
    throw "Governance notification mail was not returned by GET /mails?ownerHandle=admin."
  }
  $missingGetMail = Test-Fields $createdMail[0] @(
    "governanceActorHandle",
    "governanceTargetPath",
    "governanceTargetLabel"
  )
  if ($missingGetMail.Count -gt 0) {
    throw "Governance GET mail missing metadata fields: $($missingGetMail -join ', ')"
  }
  if ($createdMail[0].governanceActorHandle -ne $expectedGovernanceActor) {
    throw "Governance GET mail actor metadata mismatch: $($createdMail[0].governanceActorHandle)"
  }
  if ($createdMail[0].governanceTargetPath -ne "/profile/contract-smoke-bot") {
    throw "Governance GET mail target path metadata mismatch: $($createdMail[0].governanceTargetPath)"
  }
  if ($createdMail[0].governanceTargetLabel -ne "Contract smoke bot") {
    throw "Governance GET mail target label metadata mismatch: $($createdMail[0].governanceTargetLabel)"
  }
  "notificationId=$($payload.notification.id); mailId=$($payload.notification.mailId); governance mail metadata checked in POST and GET"
}

Test-Endpoint "GET /governance/admin-notifications?limit=10" {
  $payload = Invoke-ContractJson "GET" "/governance/admin-notifications?limit=10"
  $notifications = Test-ArrayEnvelope $payload "notifications"
  if ($notifications.Count -gt 0) {
    $missing = Test-Fields $notifications[0] @("id", "actorHandle", "kind", "targetType", "targetId", "targetTitle", "targetPath", "body", "createdAt", "mailId")
    if ($missing.Count -gt 0) {
      throw "First notification missing fields: $($missing -join ', ')"
    }
    $legacyPresent = Test-NoFields $notifications[0] @("mail", "mailID", "mail_id", "actor")
    if ($legacyPresent.Count -gt 0) {
      throw "First notification contains legacy fields: $($legacyPresent -join ', ')"
    }
  }
  "notifications=$($notifications.Count); item fields checked and legacy governance fields rejected when present"
}

Test-Endpoint "GET /battle/results?limit=10" {
  $payload = Invoke-ContractJson "GET" "/battle/results?limit=10"
  $results = Test-ArrayEnvelope $payload "results"
  if ($results.Count -gt 0) {
    $missing = Test-Fields $results[0] @(
      "battleId",
      "handle",
      "displayName",
      "finishedAt",
      "finishedAtLabel",
      "durationMs",
      "score",
      "placement",
      "aliveAtEnd",
      "ratingBefore",
      "ratingDelta",
      "ratingAfter",
      "resultLabel",
      "modeLabel",
      "mapLabel",
      "highlightLine",
      "playersLine",
      "timelineHint",
      "currentLoadout"
    )
    if ($missing.Count -gt 0) {
      throw "First battle result missing fields: $($missing -join ', ')"
    }

    $legacyPresent = Test-NoFields $results[0] @("playerName", "id")
    if ($legacyPresent.Count -gt 0) {
      throw "First battle result contains legacy fields: $($legacyPresent -join ', ')"
    }
  }
  "results=$($results.Count); formal fields checked and legacy battle result fields rejected when results exist"
}

Test-Endpoint "POST/GET /replay/catalog preserves frames" {
  $replayId = "contract-smoke-replay-$RunId"
  $frames = @(
    @{
      elapsedMs = 0
      worldSize = @{ x = 1280; y = 720 }
      heroes = @(
        @{
          heroId = "hero-a"
          displayName = "Smoke A"
          position = @{ x = 10; y = 20 }
          hp = 100
          maxHp = 100
          alive = $true
          lifeState = "alive"
          score = 0
          facing = 0
          currentWeaponKind = $null
          eliminatedAtMs = $null
        }
      )
      projectiles = @()
      pickups = @()
      eventMessages = @("start")
    },
    @{
      elapsedMs = 120
      worldSize = @{ x = 1280; y = 720 }
      heroes = @(
        @{
          heroId = "hero-a"
          displayName = "Smoke A"
          position = @{ x = 24; y = 20 }
          hp = 98
          maxHp = 100
          alive = $true
          lifeState = "alive"
          score = 10
          facing = 0
          currentWeaponKind = $null
          eliminatedAtMs = $null
        }
      )
      projectiles = @()
      pickups = @()
      eventMessages = @("move")
    }
  )

  $created = Invoke-ContractJson "POST" "/replay/catalog" @{
    replayId = $replayId
    battleId = "contract-smoke-battle-$RunId"
    handle = $SmokePlayer
    displayName = $SmokePlayer
    finishedAt = $RunId
    finishedAtLabel = "contract smoke"
    title = "Contract smoke replay"
    modeLabel = "contract"
    resultLabel = "complete"
    mapLabel = "smoke"
    highlightLine = "frames preserved"
    coverLabel = "smoke"
    playersLine = "Smoke A"
    timelineHint = "frames should round trip"
    score = 10
    placement = 1
    durationMs = 120
    aliveAtEnd = $true
    thumbnailDataUrl = $null
    currentLoadout = $null
    frameCount = $frames.Count
    playbackAvailable = $true
    framesJson = ($frames | ConvertTo-Json -Depth 8 -Compress)
  }

  $missingCreated = Test-Fields $created.replay @("replayId", "battleId", "frames", "frameCount", "playbackAvailable", "ratingBefore", "ratingDelta", "ratingAfter")
  if ($missingCreated.Count -gt 0) {
    throw "Created replay missing fields: $($missingCreated -join ', ')"
  }
  $invalidCreatedRatingFields = Test-NumberOrNullFields $created.replay @("ratingBefore", "ratingDelta", "ratingAfter")
  if ($invalidCreatedRatingFields.Count -gt 0) {
    throw "Created replay rating fields must be number/null values: $($invalidCreatedRatingFields -join ', ')"
  }
  if ($null -ne $created.replay.ratingBefore -or $null -ne $created.replay.ratingDelta -or $null -ne $created.replay.ratingAfter) {
    throw "Created replay without battle result should expose null rating fields."
  }
  $legacyCreatedReplayPresent = Test-NoFields $created.replay @("id")
  if ($legacyCreatedReplayPresent.Count -gt 0) {
    throw "Created replay contains legacy fields: $($legacyCreatedReplayPresent -join ', ')"
  }
  if ($created.replay.frames.Count -ne 2 -or -not $created.replay.playbackAvailable) {
    throw "Created replay did not preserve playable frames."
  }

  $loaded = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($replayId))"
  $missingLoaded = Test-Fields $loaded.replay @("replayId", "battleId", "frames", "frameCount", "playbackAvailable", "ratingBefore", "ratingDelta", "ratingAfter")
  if ($missingLoaded.Count -gt 0) {
    throw "Loaded replay missing fields: $($missingLoaded -join ', ')"
  }
  $invalidLoadedRatingFields = Test-NumberOrNullFields $loaded.replay @("ratingBefore", "ratingDelta", "ratingAfter")
  if ($invalidLoadedRatingFields.Count -gt 0) {
    throw "Loaded replay rating fields must be number/null values: $($invalidLoadedRatingFields -join ', ')"
  }
  if ($null -ne $loaded.replay.ratingBefore -or $null -ne $loaded.replay.ratingDelta -or $null -ne $loaded.replay.ratingAfter) {
    throw "Loaded replay without battle result should expose null rating fields."
  }
  $legacyLoadedReplayPresent = Test-NoFields $loaded.replay @("id")
  if ($legacyLoadedReplayPresent.Count -gt 0) {
    throw "Loaded replay contains legacy fields: $($legacyLoadedReplayPresent -join ', ')"
  }
  if ($loaded.replay.frames.Count -ne 2 -or $loaded.replay.frameCount -ne 2 -or -not $loaded.replay.playbackAvailable) {
    throw "Loaded replay frames/playback flags mismatch."
  }

  $catalogPayload = Invoke-ContractJson "GET" "/replay/catalog?limit=5"
  $catalogReplays = Test-ArrayEnvelope $catalogPayload "replays"
  $catalogReplay = @($catalogReplays | Where-Object { $_.replayId -ceq $replayId } | Select-Object -First 1)
  if ($catalogReplay.Count -lt 1) {
    $catalogReplayIds = @($catalogReplays | ForEach-Object { $_.replayId })
    throw "Replay catalog list did not include replayId=$replayId. Returned replay ids=$($catalogReplayIds -join ', ')."
  }
  $missingCatalogReplay = Test-Fields $catalogReplay[0] @("replayId", "battleId", "frameCount", "playbackAvailable", "ratingBefore", "ratingDelta", "ratingAfter")
  if ($missingCatalogReplay.Count -gt 0) {
    throw "Catalog replay missing fields: $($missingCatalogReplay -join ', ')"
  }
  $invalidCatalogRatingFields = Test-NumberOrNullFields $catalogReplay[0] @("ratingBefore", "ratingDelta", "ratingAfter")
  if ($invalidCatalogRatingFields.Count -gt 0) {
    throw "Catalog replay rating fields must be number/null values: $($invalidCatalogRatingFields -join ', ')"
  }
  if ($null -ne $catalogReplay[0].ratingBefore -or $null -ne $catalogReplay[0].ratingDelta -or $null -ne $catalogReplay[0].ratingAfter) {
    throw "Catalog replay without battle result should expose null rating fields."
  }

  "replayId=$($loaded.replay.replayId); frames=$($loaded.replay.frames.Count); playbackAvailable=$($loaded.replay.playbackAvailable); catalogRating=null"
}

Test-Endpoint "GET/POST /replay/catalog/:replayId/comments" {
  $commentReplayId = "contract-smoke-comments-$RunId"
  $createdReplay = Invoke-ContractJson "POST" "/replay/catalog" @{
    replayId = $commentReplayId
    battleId = "contract-smoke-battle-comments-$RunId"
    handle = $SmokePlayer
    displayName = $SmokePlayer
    finishedAt = $RunId
    finishedAtLabel = "contract smoke"
    title = "Contract smoke comments replay"
    modeLabel = "contract"
    resultLabel = "complete"
    mapLabel = "smoke"
    highlightLine = "comments smoke"
    coverLabel = "smoke"
    playersLine = "Smoke A"
    timelineHint = "comments should round trip"
    score = 10
    placement = 1
    durationMs = 120
    aliveAtEnd = $true
    thumbnailDataUrl = $null
    currentLoadout = $null
    frameCount = 0
    playbackAvailable = $false
    framesJson = "[]"
  }

  $missingReplay = Test-Fields $createdReplay.replay @("replayId", "battleId")
  if ($missingReplay.Count -gt 0) {
    throw "Comment smoke replay missing fields: $($missingReplay -join ', ')"
  }

  $initial = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($commentReplayId))/comments"
  $missingInitialEnvelope = Test-Fields $initial @("comments")
  if ($missingInitialEnvelope.Count -gt 0) {
    throw "Initial comments response missing envelope fields: $($missingInitialEnvelope -join ', ')"
  }
  if ($initial.comments.Count -ne 0) {
    throw "Initial comments list was not empty."
  }

  $posted = Invoke-ContractJson "POST" "/replay/catalog/$([uri]::EscapeDataString($commentReplayId))/comments" @{
    authorHandle = $SmokePlayer
    body = "Contract smoke comment $RunId"
  }
  $missingPostedEnvelope = Test-Fields $posted @("comment")
  if ($missingPostedEnvelope.Count -gt 0) {
    throw "Posted comment response missing envelope fields: $($missingPostedEnvelope -join ', ')"
  }
  $missingPostedComment = Test-Fields $posted.comment @("id", "replayId", "authorHandle", "body", "createdAt")
  if ($missingPostedComment.Count -gt 0) {
    throw "Posted comment missing fields: $($missingPostedComment -join ', ')"
  }
  $legacyPostedCommentPresent = Test-NoFields $posted.comment @("author")
  if ($legacyPostedCommentPresent.Count -gt 0) {
    throw "Posted comment contains legacy fields: $($legacyPostedCommentPresent -join ', ')"
  }

  $loaded = Invoke-ContractJson "GET" "/replay/catalog/$([uri]::EscapeDataString($commentReplayId))/comments"
  $comments = Test-ArrayEnvelope $loaded "comments"
  if ($comments.Count -lt 1) {
    throw "Comments list did not return the posted comment."
  }
  $lastComment = $comments[$comments.Count - 1]
  $missingLoadedComment = Test-Fields $lastComment @("id", "replayId", "authorHandle", "body", "createdAt")
  if ($missingLoadedComment.Count -gt 0) {
    throw "Loaded comment missing fields: $($missingLoadedComment -join ', ')"
  }

  "replayId=$commentReplayId; comments=$($comments.Count); shared comments API works"
}

Test-Endpoint "GET /battle/state/stream SSE state frame" {
  $join = $null
  $extraJoins = @()
  $client = $null
  $response = $null
  $stream = $null
  $reader = $null

  try {
    $sseHandle = New-BattleSmokeHandle "sse"
    $join = Join-AuthenticatedBattleQueue -Handle $sseHandle -Rating "1200" -Skin "blue" -QueueRequestId "contract-$sseHandle"
    $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoin.Count -gt 0) {
      throw "SSE state smoke queue join missing fields: $($missingJoin -join ', ')"
    }
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if ($null -ne $status.battleSession -and $status.phase -eq "active") {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession) {
      throw "SSE state smoke battle session was not created."
    }
    if ($status.phase -ne "active") {
      throw "SSE state smoke expected active battle, got phase=$($status.phase)"
    }

    $battleId = $status.battleSession.battleId
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(8)

    $streamUri = "$BaseUrl/battle/state/stream?battleId=$([uri]::EscapeDataString($battleId))"
    $response = $client.GetAsync($streamUri, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
      throw "SSE state stream returned HTTP $([int]$response.StatusCode)."
    }

    $contentType = $response.Content.Headers.ContentType
    if ($null -eq $contentType -or $contentType.MediaType -ne "text/event-stream") {
      $actualContentType = if ($null -ne $contentType) { $contentType.ToString() } else { "<missing>" }
      throw "SSE state stream content type mismatch: $actualContentType"
    }

    $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)

    $statePayloads = @()
    $stateEventPending = $false
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(5)
    for ($lineIndex = 0; $lineIndex -lt 96 -and [DateTimeOffset]::UtcNow -lt $deadline; $lineIndex++) {
      $readTask = $reader.ReadLineAsync()
      $remainingMs = [Math]::Max(1, [int]($deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds)
      if (-not $readTask.Wait($remainingMs)) {
        throw "SSE state stream timed out while reading line."
      }

      $line = $readTask.Result
      if ($null -eq $line) {
        break
      }
      if ($line -eq "event: state") {
        $stateEventPending = $true
      } elseif ($line.StartsWith("data:")) {
        $dataLine = $line.Substring(5).TrimStart()
        if ($stateEventPending -and -not [string]::IsNullOrWhiteSpace($dataLine)) {
          $payload = $dataLine | ConvertFrom-Json
          if ($payload.battleId -ne $battleId) {
            throw "SSE state payload battleId mismatch: expected=$battleId actual=$($payload.battleId)"
          }
          if (-not (Test-HasField $payload "tick") -or -not (Test-NumberOrNull $payload.tick) -or $null -eq $payload.tick) {
            $actualTick = if (Test-HasField $payload "tick") { $payload.tick } else { "<missing>" }
            throw "SSE state payload tick must be a number, got $actualTick."
          }
          $statePayloads += $payload
          $stateEventPending = $false
          if ($statePayloads.Count -ge 2) {
            break
          }
        }
      }
    }

    if ($statePayloads.Count -lt 2) {
      throw "SSE state stream must emit at least two state payloads; got $($statePayloads.Count)."
    }

    $firstStatePayload = $statePayloads[0]
    $secondStatePayload = $statePayloads[1]
    $players = Test-ArrayEnvelope $firstStatePayload "players"
    if ($players.Count -lt 1) {
      throw "SSE state payload expected at least one player."
    }

    "battleId=$battleId; players=$($players.Count); ticks=$($firstStatePayload.tick),$($secondStatePayload.tick); contentType=$($contentType.MediaType)"
  } finally {
    if ($null -ne $reader) {
      $reader.Dispose()
    } elseif ($null -ne $stream) {
      $stream.Dispose()
    }
    if ($null -ne $response) {
      $response.Dispose()
    }
    if ($null -ne $client) {
      $client.Dispose()
    }
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/commands server pistol ammo/reload + medkit pickup" {
  $join = $null
  $extraJoins = @()

  try {
    $ammoHandle = New-BattleSmokeHandle "ammo"
    $ammoPeerHandles = @()
    for ($extraSeat = 1; $extraSeat -le 5; $extraSeat++) {
      $ammoPeerHandles += New-BattleSmokeHandle "ammo$extraSeat"
    }
    Get-SmokeSessionToken $ammoHandle | Out-Null
    foreach ($peerHandle in $ammoPeerHandles) {
      Get-SmokeSessionToken $peerHandle | Out-Null
    }

    $join = Join-AuthenticatedBattleQueue -Handle $ammoHandle -Rating "1210" -Skin "blue" -QueueRequestId "contract-$ammoHandle"
    $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoin.Count -gt 0) {
      throw "Ammo smoke queue join missing fields: $($missingJoin -join ', ')"
    }

    for ($extraSeat = 0; $extraSeat -lt $ammoPeerHandles.Count; $extraSeat++) {
      $peerHandle = $ammoPeerHandles[$extraSeat]
      $extraJoin = Join-AuthenticatedBattleQueue -Handle $peerHandle -Rating "1210" -Skin "blue" -QueueRequestId "contract-$peerHandle"
      $missingExtraJoin = Test-Fields $extraJoin @("ticketId", "playerId", "roomId", "startsAt")
      if ($missingExtraJoin.Count -gt 0) {
        throw "Ammo smoke extra queue join $($extraSeat + 1) missing fields: $($missingExtraJoin -join ', ')"
      }
      if ($extraJoin.roomId -ne $join.roomId) {
        throw "Ammo smoke extra queue join $($extraSeat + 1) landed in different room: primary=$($join.roomId), extra=$($extraJoin.roomId)"
      }
      $extraJoins += $extraJoin
    }

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if ($null -ne $status.battleSession -and $status.phase -eq "active") {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession) {
      throw "Ammo smoke battle session was not created."
    }
    if ($status.phase -ne "active") {
      throw "Ammo smoke expected active battle, got phase=$($status.phase)"
    }

    $battleId = $status.battleSession.battleId
    $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $missingReadinessFields = Test-Fields $state @("resultReady", "replayReady")
    if ($missingReadinessFields.Count -gt 0) {
      throw "Battle state missing readiness fields: $($missingReadinessFields -join ', ')"
    }
    if ($state.resultReady -ne $false -or $state.replayReady -ne $false) {
      throw "Active battle initial readiness mismatch: resultReady=$($state.resultReady), replayReady=$($state.replayReady)"
    }
    $missingWorldSizeFields = Test-Fields $state.worldSize @("x", "y")
    if ($missingWorldSizeFields.Count -gt 0) {
      throw "Battle state worldSize missing fields: $($missingWorldSizeFields -join ', ')"
    }
    if ($state.worldSize.x -ne 2560 -or $state.worldSize.y -ne 1600) {
      throw "Battle state worldSize mismatch: x=$($state.worldSize.x), y=$($state.worldSize.y)"
    }
    $initialSlowFields = Test-ArrayEnvelope $state "slowFields"
    if ($initialSlowFields.Count -ne 0) {
      throw "Freeze smoke expected no initial slowFields, got $($initialSlowFields.Count)."
    }
    $player = @($state.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($player.Count -lt 1) {
      throw "Ammo smoke player was not present in battle state."
    }
    $missingPlayerFields = Test-Fields $player[0] @("currentWeaponKind", "weapons", "ammoInMagazine", "magazineSize", "reserveAmmo", "fireCooldownMs", "reloadRemainingMs", "skills", "respawnMs")
    if ($missingPlayerFields.Count -gt 0) {
      throw "Ammo smoke player missing weapon fields: $($missingPlayerFields -join ', ')"
    }
    if ($player[0].currentWeaponKind -ne "Pistol" -or $player[0].ammoInMagazine -ne 12 -or $player[0].magazineSize -ne 12 -or $player[0].reserveAmmo -ne 48 -or $player[0].reloadRemainingMs -ne 0) {
      throw "Ammo smoke initial pistol state mismatch: weapon=$($player[0].currentWeaponKind), ammo=$($player[0].ammoInMagazine), mag=$($player[0].magazineSize), reserve=$($player[0].reserveAmmo), reload=$($player[0].reloadRemainingMs)"
    }
    Test-PlayerPistolWeaponSync $player[0] "Ammo smoke initial pistol state" 12 48 0
    if ($player[0].respawnMs -ne 0) {
      throw "Ammo smoke initial respawnMs mismatch: respawnMs=$($player[0].respawnMs)"
    }
    $initialSkills = Test-ArrayEnvelope $player[0] "skills"
    $initialBlink = @($initialSkills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
    $initialDash = @($initialSkills | Where-Object { $_.kind -ceq "Dash" } | Select-Object -First 1)
    $initialFreeze = @($initialSkills | Where-Object { $_.kind -ceq "Freeze" } | Select-Object -First 1)
    if ($initialBlink.Count -lt 1) {
      throw "Blink smoke initial player skills did not include Blink."
    }
    if ($initialDash.Count -lt 1) {
      throw "Dash smoke initial player skills did not include Dash."
    }
    if ($initialFreeze.Count -lt 1) {
      throw "Freeze smoke initial player skills did not include Freeze."
    }
    $missingBlinkFields = Test-Fields $initialBlink[0] @("kind", "cooldownMs", "activeMs")
    if ($missingBlinkFields.Count -gt 0) {
      throw "Blink smoke initial Blink missing fields: $($missingBlinkFields -join ', ')"
    }
    $missingDashFields = Test-Fields $initialDash[0] @("kind", "cooldownMs", "activeMs")
    if ($missingDashFields.Count -gt 0) {
      throw "Dash smoke initial Dash missing fields: $($missingDashFields -join ', ')"
    }
    $missingFreezeFields = Test-Fields $initialFreeze[0] @("kind", "cooldownMs", "activeMs")
    if ($missingFreezeFields.Count -gt 0) {
      throw "Freeze smoke initial Freeze missing fields: $($missingFreezeFields -join ', ')"
    }
    if ($initialBlink[0].cooldownMs -ne 0 -or $initialBlink[0].activeMs -ne 0) {
      throw "Blink smoke initial Blink state mismatch: cooldownMs=$($initialBlink[0].cooldownMs), activeMs=$($initialBlink[0].activeMs)"
    }
    if ($initialDash[0].cooldownMs -ne 0 -or $initialDash[0].activeMs -ne 0) {
      throw "Dash smoke initial Dash state mismatch: cooldownMs=$($initialDash[0].cooldownMs), activeMs=$($initialDash[0].activeMs)"
    }
    if ($initialFreeze[0].cooldownMs -ne 0 -or $initialFreeze[0].activeMs -ne 0) {
      throw "Freeze smoke initial Freeze state mismatch: cooldownMs=$($initialFreeze[0].cooldownMs), activeMs=$($initialFreeze[0].activeMs)"
    }

    $initialBlinkStartX = [double]$player[0].position.x
    $initialBlinkStartY = [double]$player[0].position.y
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 101
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $true
      pointerWorld = @{ x = -1; y = -1 }
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterOutOfBoundsBlink = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterOutOfBoundsBlinkPlayer = @($afterOutOfBoundsBlink.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterOutOfBoundsBlinkPlayer.Count -lt 1) {
      throw "Blink smoke player disappeared after out-of-bounds Blink command."
    }
    $outOfBoundsBlinkSkill = @($afterOutOfBoundsBlinkPlayer[0].skills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
    if ($outOfBoundsBlinkSkill.Count -lt 1 -or $outOfBoundsBlinkSkill[0].cooldownMs -ne 0 -or $outOfBoundsBlinkSkill[0].activeMs -ne 0) {
      throw "Blink smoke out-of-bounds target triggered cooldown/active: cooldownMs=$($outOfBoundsBlinkSkill[0].cooldownMs), activeMs=$($outOfBoundsBlinkSkill[0].activeMs)"
    }
    $outOfBoundsBlinkDelta = [math]::Sqrt([math]::Pow(([double]$afterOutOfBoundsBlinkPlayer[0].position.x - $initialBlinkStartX), 2) + [math]::Pow(([double]$afterOutOfBoundsBlinkPlayer[0].position.y - $initialBlinkStartY), 2))
    if ($outOfBoundsBlinkDelta -gt 20) {
      throw "Blink smoke out-of-bounds target moved player unexpectedly: delta=$outOfBoundsBlinkDelta"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 102
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $true
      pointerWorld = @{ x = 1000; y = 800 }
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterOverRangeBlink = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterOverRangeBlinkPlayer = @($afterOverRangeBlink.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterOverRangeBlinkPlayer.Count -lt 1) {
      throw "Blink smoke player disappeared after over-range Blink command."
    }
    $overRangeBlinkSkill = @($afterOverRangeBlinkPlayer[0].skills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
    if ($overRangeBlinkSkill.Count -lt 1 -or $overRangeBlinkSkill[0].cooldownMs -ne 0 -or $overRangeBlinkSkill[0].activeMs -ne 0) {
      throw "Blink smoke over-range target triggered cooldown/active: cooldownMs=$($overRangeBlinkSkill[0].cooldownMs), activeMs=$($overRangeBlinkSkill[0].activeMs)"
    }
    $overRangeBlinkDelta = [math]::Sqrt([math]::Pow(([double]$afterOverRangeBlinkPlayer[0].position.x - $initialBlinkStartX), 2) + [math]::Pow(([double]$afterOverRangeBlinkPlayer[0].position.y - $initialBlinkStartY), 2))
    if ($overRangeBlinkDelta -gt 30) {
      throw "Blink smoke over-range target moved player unexpectedly: delta=$overRangeBlinkDelta"
    }

    $blinkTarget = @{
      x = [double]$afterOverRangeBlinkPlayer[0].position.x
      y = [double]$afterOverRangeBlinkPlayer[0].position.y + 40
    }
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 103
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $true
      pointerWorld = $blinkTarget
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterBlink = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterBlinkPlayer = @($afterBlink.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterBlinkPlayer.Count -lt 1) {
      throw "Blink smoke player disappeared after legal Blink command."
    }
    $blinkTargetDelta = [math]::Sqrt([math]::Pow(([double]$afterBlinkPlayer[0].position.x - [double]$blinkTarget.x), 2) + [math]::Pow(([double]$afterBlinkPlayer[0].position.y - [double]$blinkTarget.y), 2))
    if ($blinkTargetDelta -gt 18) {
      throw "Blink smoke expected position near legal target, got delta=$blinkTargetDelta"
    }
    $afterBlinkSkills = Test-ArrayEnvelope $afterBlinkPlayer[0] "skills"
    $afterBlinkSkill = @($afterBlinkSkills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
    $afterBlinkDash = @($afterBlinkSkills | Where-Object { $_.kind -ceq "Dash" } | Select-Object -First 1)
    if ($afterBlinkSkill.Count -lt 1 -or $afterBlinkSkill[0].cooldownMs -le 0 -or $afterBlinkSkill[0].activeMs -lt 0) {
      throw "Blink smoke cooldown/active mismatch after legal Blink: cooldownMs=$($afterBlinkSkill[0].cooldownMs), activeMs=$($afterBlinkSkill[0].activeMs)"
    }
    if ($afterBlinkDash.Count -lt 1) {
      throw "Blink smoke Dash skill disappeared after legal Blink."
    }

    $blinkCooldownAfterLegal = [int64]$afterBlinkSkill[0].cooldownMs
    $blinkXAfterLegal = [double]$afterBlinkPlayer[0].position.x
    $blinkYAfterLegal = [double]$afterBlinkPlayer[0].position.y
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 104
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $true
      pointerWorld = @{ x = 1601; y = 901 }
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterIllegalBlinkCooldown = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterIllegalBlinkCooldownPlayer = @($afterIllegalBlinkCooldown.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterIllegalBlinkCooldownPlayer.Count -lt 1) {
      throw "Blink smoke player disappeared after illegal post-legal Blink command."
    }
    $postLegalIllegalBlinkSkill = @($afterIllegalBlinkCooldownPlayer[0].skills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
    if ($postLegalIllegalBlinkSkill.Count -lt 1) {
      throw "Blink smoke Blink skill disappeared after illegal post-legal Blink command."
    }
    if ($postLegalIllegalBlinkSkill[0].cooldownMs -gt $blinkCooldownAfterLegal) {
      throw "Blink smoke illegal post-legal target reset cooldown upward: before=$blinkCooldownAfterLegal, after=$($postLegalIllegalBlinkSkill[0].cooldownMs)"
    }
    $postLegalIllegalBlinkDelta = [math]::Sqrt([math]::Pow(([double]$afterIllegalBlinkCooldownPlayer[0].position.x - $blinkXAfterLegal), 2) + [math]::Pow(([double]$afterIllegalBlinkCooldownPlayer[0].position.y - $blinkYAfterLegal), 2))
    if ($postLegalIllegalBlinkDelta -gt 20) {
      throw "Blink smoke illegal post-legal target moved player unexpectedly: delta=$postLegalIllegalBlinkDelta"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 201
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $true
      pointerWorld = @{ x = -1; y = -1 }
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterOutOfBoundsFreeze = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterOutOfBoundsFreezeFields = Test-ArrayEnvelope $afterOutOfBoundsFreeze "slowFields"
    if ($afterOutOfBoundsFreezeFields.Count -ne 0) {
      throw "Freeze smoke out-of-bounds target created slowFields=$($afterOutOfBoundsFreezeFields.Count)."
    }
    $afterOutOfBoundsFreezePlayer = @($afterOutOfBoundsFreeze.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterOutOfBoundsFreezePlayer.Count -lt 1) {
      throw "Freeze smoke player disappeared after out-of-bounds command."
    }
    $outOfBoundsFreezeSkill = @($afterOutOfBoundsFreezePlayer[0].skills | Where-Object { $_.kind -ceq "Freeze" } | Select-Object -First 1)
    if ($outOfBoundsFreezeSkill.Count -lt 1 -or $outOfBoundsFreezeSkill[0].cooldownMs -ne 0 -or $outOfBoundsFreezeSkill[0].activeMs -ne 0) {
      throw "Freeze smoke out-of-bounds target triggered cooldown/active: cooldownMs=$($outOfBoundsFreezeSkill[0].cooldownMs), activeMs=$($outOfBoundsFreezeSkill[0].activeMs)"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 202
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $true
      pointerWorld = @{ x = 1600; y = 900 }
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterOverRangeFreeze = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterOverRangeFreezeFields = Test-ArrayEnvelope $afterOverRangeFreeze "slowFields"
    if ($afterOverRangeFreezeFields.Count -ne 0) {
      throw "Freeze smoke over-range target created slowFields=$($afterOverRangeFreezeFields.Count)."
    }
    $afterOverRangeFreezePlayer = @($afterOverRangeFreeze.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterOverRangeFreezePlayer.Count -lt 1) {
      throw "Freeze smoke player disappeared after over-range command."
    }
    $overRangeFreezeSkill = @($afterOverRangeFreezePlayer[0].skills | Where-Object { $_.kind -ceq "Freeze" } | Select-Object -First 1)
    if ($overRangeFreezeSkill.Count -lt 1 -or $overRangeFreezeSkill[0].cooldownMs -ne 0 -or $overRangeFreezeSkill[0].activeMs -ne 0) {
      throw "Freeze smoke over-range target triggered cooldown/active: cooldownMs=$($overRangeFreezeSkill[0].cooldownMs), activeMs=$($overRangeFreezeSkill[0].activeMs)"
    }

    $freezeTarget = @{
      x = [double]$afterOverRangeFreezePlayer[0].position.x + 40
      y = [double]$afterOverRangeFreezePlayer[0].position.y
    }
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 203
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $true
      pointerWorld = $freezeTarget
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterFreeze = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterFreezeFields = Test-ArrayEnvelope $afterFreeze "slowFields"
    if ($afterFreezeFields.Count -ne 1) {
      throw "Freeze smoke expected one slow field after legal Freeze, got $($afterFreezeFields.Count)."
    }
    $freezeField = $afterFreezeFields[0]
    $missingFreezeFieldFields = Test-Fields $freezeField @("fieldId", "ownerPlayerId", "ownerHeroId", "position", "radius", "ttlMs", "durationMs")
    if ($missingFreezeFieldFields.Count -gt 0) {
      throw "Freeze smoke slow field missing fields: $($missingFreezeFieldFields -join ', ')"
    }
    if ($freezeField.ownerPlayerId -ne $join.playerId -or $freezeField.radius -ne 150 -or $freezeField.durationMs -ne 10000 -or $freezeField.ttlMs -le 0) {
      throw "Freeze smoke slow field mismatch: owner=$($freezeField.ownerPlayerId), radius=$($freezeField.radius), ttlMs=$($freezeField.ttlMs), durationMs=$($freezeField.durationMs)"
    }
    $afterFreezePlayer = @($afterFreeze.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterFreezePlayer.Count -lt 1) {
      throw "Freeze smoke player disappeared after legal Freeze command."
    }
    $afterFreezeSkill = @($afterFreezePlayer[0].skills | Where-Object { $_.kind -ceq "Freeze" } | Select-Object -First 1)
    if ($afterFreezeSkill.Count -lt 1 -or $afterFreezeSkill[0].cooldownMs -le 0 -or $afterFreezeSkill[0].activeMs -le 0) {
      throw "Freeze smoke cooldown/active mismatch after legal Freeze: cooldownMs=$($afterFreezeSkill[0].cooldownMs), activeMs=$($afterFreezeSkill[0].activeMs)"
    }
    Start-Sleep -Milliseconds 160
    $laterFreeze = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $laterFreezeFields = Test-ArrayEnvelope $laterFreeze "slowFields"
    if ($laterFreezeFields.Count -ne 1 -or $laterFreezeFields[0].ttlMs -ge $freezeField.ttlMs) {
      $laterTtl = if ($laterFreezeFields.Count -gt 0) { $laterFreezeFields[0].ttlMs } else { "<missing>" }
      throw "Freeze smoke ttlMs did not decrease: first=$($freezeField.ttlMs), later=$laterTtl"
    }

    $slowMovePlayerBefore = @($laterFreeze.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($slowMovePlayerBefore.Count -lt 1) {
      throw "Freeze smoke player disappeared before slow movement check."
    }
    $slowMoveStartX = [double]$slowMovePlayerBefore[0].position.x
    $slowMoveStartY = [double]$slowMovePlayerBefore[0].position.y
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 204
      movement = @{ x = 1; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 420
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 205
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    $afterSlowMove = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterSlowMovePlayer = @($afterSlowMove.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterSlowMovePlayer.Count -lt 1) {
      throw "Freeze smoke player disappeared after slow movement check."
    }
    $slowMoveDelta = [math]::Sqrt([math]::Pow(([double]$afterSlowMovePlayer[0].position.x - $slowMoveStartX), 2) + [math]::Pow(([double]$afterSlowMovePlayer[0].position.y - $slowMoveStartY), 2))
    if ($slowMoveDelta -gt 70) {
      throw "Freeze smoke expected slowed movement under reasonable no-field bound, got delta=$slowMoveDelta"
    }

    Start-Sleep -Milliseconds 10500
    $afterFreezeExpiry = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterFreezeExpiryFields = Test-ArrayEnvelope $afterFreezeExpiry "slowFields"
    if ($afterFreezeExpiryFields.Count -ne 0) {
      throw "Freeze smoke expected slowFields to be removed after duration expiry, got $($afterFreezeExpiryFields.Count)."
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 300
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 1
    } | Out-Null

    $afterWeaponSwitch = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterWeaponSwitchPlayer = @($afterWeaponSwitch.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterWeaponSwitchPlayer.Count -lt 1) {
      throw "Weapon switch smoke player disappeared after switch command."
    }
    if ($afterWeaponSwitchPlayer[0].currentWeaponIndex -ne 1 -or $afterWeaponSwitchPlayer[0].currentWeaponKind -ne "Gatling") {
      throw "Weapon switch smoke expected spawn Gatling after switch: index=$($afterWeaponSwitchPlayer[0].currentWeaponIndex), kind=$($afterWeaponSwitchPlayer[0].currentWeaponKind)"
    }
    Test-PlayerPistolWeaponSync $afterWeaponSwitchPlayer[0] "Weapon switch smoke Gatling state"

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 301
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 0
      switchWeaponIndex = 0
    } | Out-Null

    $afterWeaponSwitch = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterWeaponSwitchPlayer = @($afterWeaponSwitch.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterWeaponSwitchPlayer.Count -lt 1) {
      throw "Weapon switch smoke player disappeared after switching back to pistol."
    }
    if ($afterWeaponSwitchPlayer[0].currentWeaponIndex -ne 0 -or $afterWeaponSwitchPlayer[0].currentWeaponKind -ne "Pistol") {
      throw "Weapon switch smoke expected Pistol after explicit switch back: index=$($afterWeaponSwitchPlayer[0].currentWeaponIndex), kind=$($afterWeaponSwitchPlayer[0].currentWeaponKind)"
    }
    Test-PlayerPistolWeaponSync $afterWeaponSwitchPlayer[0] "Weapon switch smoke pistol state"

    $initialPickups = Test-ArrayEnvelope $state "pickups"
    $initialMedkit = @($initialPickups | Where-Object { $_.pickupId -ceq "pickup-medkit-1" } | Select-Object -First 1)
    $initialRocketPickup = @($initialPickups | Where-Object { $_.pickupId -ceq "pickup-rocket-1" } | Select-Object -First 1)
    $initialSpawnWeaponPickup = @($initialPickups | Where-Object { $_.pickupId -ceq "pickup-gatling-1" } | Select-Object -First 1)
    if ($initialMedkit.Count -lt 1) {
      throw "Medkit smoke initial state did not include pickup-medkit-1."
    }
    if ($initialRocketPickup.Count -lt 1) {
      throw "Weapon pickup smoke initial state did not include pickup-rocket-1."
    }
    if ($initialSpawnWeaponPickup.Count -lt 1) {
      throw "Weapon pickup smoke initial state did not include pickup-gatling-1."
    }
    $missingMedkitFields = Test-Fields $initialMedkit[0] @("pickupId", "kind", "position", "available", "respawnMs")
    if ($missingMedkitFields.Count -gt 0) {
      throw "Medkit smoke pickup missing fields: $($missingMedkitFields -join ', ')"
    }
    $missingMedkitPositionFields = Test-Fields $initialMedkit[0].position @("x", "y")
    if ($missingMedkitPositionFields.Count -gt 0) {
      throw "Medkit smoke pickup position missing fields: $($missingMedkitPositionFields -join ', ')"
    }
    if ($initialMedkit[0].kind -ne "Medkit" -or $initialMedkit[0].available -ne $true -or $initialMedkit[0].respawnMs -ne 0) {
      throw "Medkit smoke initial state mismatch: kind=$($initialMedkit[0].kind), available=$($initialMedkit[0].available), respawnMs=$($initialMedkit[0].respawnMs)"
    }
    if (Test-HasField $initialMedkit[0] "weaponKind") {
      throw "Medkit smoke pickup unexpectedly included weaponKind=$($initialMedkit[0].weaponKind)"
    }

    $missingWeaponPickupFields = Test-Fields $initialRocketPickup[0] @("pickupId", "kind", "weaponKind", "position", "available", "respawnMs")
    if ($missingWeaponPickupFields.Count -gt 0) {
      throw "Weapon pickup smoke missing fields: $($missingWeaponPickupFields -join ', ')"
    }
    $missingWeaponPickupPositionFields = Test-Fields $initialRocketPickup[0].position @("x", "y")
    if ($missingWeaponPickupPositionFields.Count -gt 0) {
      throw "Weapon pickup smoke position missing fields: $($missingWeaponPickupPositionFields -join ', ')"
    }
    if ($initialRocketPickup[0].kind -ne "Weapon" -or $initialRocketPickup[0].weaponKind -ne "RocketLauncher" -or $initialRocketPickup[0].available -ne $true -or $initialRocketPickup[0].respawnMs -ne 0) {
      throw "Weapon pickup smoke initial Rocket state mismatch: kind=$($initialRocketPickup[0].kind), weaponKind=$($initialRocketPickup[0].weaponKind), available=$($initialRocketPickup[0].available), respawnMs=$($initialRocketPickup[0].respawnMs)"
    }
    if ($initialRocketPickup[0].position.x -ne 1280 -or $initialRocketPickup[0].position.y -ne 256) {
      throw "Weapon pickup smoke Rocket position mismatch: x=$($initialRocketPickup[0].position.x), y=$($initialRocketPickup[0].position.y)"
    }
    if ($initialSpawnWeaponPickup[0].kind -ne "Weapon" -or $initialSpawnWeaponPickup[0].weaponKind -ne "Gatling" -or $initialSpawnWeaponPickup[0].available -ne $false -or $initialSpawnWeaponPickup[0].respawnMs -le 0 -or $initialSpawnWeaponPickup[0].respawnMs -gt 10000) {
      throw "Weapon pickup smoke spawn Gatling was not consumed on spawn: kind=$($initialSpawnWeaponPickup[0].kind), weaponKind=$($initialSpawnWeaponPickup[0].weaponKind), available=$($initialSpawnWeaponPickup[0].available), respawnMs=$($initialSpawnWeaponPickup[0].respawnMs)"
    }

    $initialWeapons = Test-ArrayEnvelope $player[0] "weapons"
    $initialGatlingWeapon = @($initialWeapons | Where-Object { $_.weaponKind -ceq "Gatling" } | Select-Object -First 1)
    if ($initialGatlingWeapon.Count -lt 1) {
      throw "Weapon pickup smoke did not add spawn Gatling to player inventory."
    }
    if ($initialGatlingWeapon[0].ammoInMagazine -ne 0 -or $initialGatlingWeapon[0].magazineSize -ne 0 -or $initialGatlingWeapon[0].reserveAmmo -ne 0) {
      throw "Weapon pickup smoke Gatling state mismatch: ammo=$($initialGatlingWeapon[0].ammoInMagazine), mag=$($initialGatlingWeapon[0].magazineSize), reserve=$($initialGatlingWeapon[0].reserveAmmo)"
    }

    $beforeAmmoShot = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $beforeAmmoShotPlayer = @($beforeAmmoShot.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($beforeAmmoShotPlayer.Count -lt 1) {
      throw "Ammo smoke player disappeared before shot setup."
    }
    if ($beforeAmmoShotPlayer[0].alive -ne $true) {
      for ($ammoRespawnPoll = 0; $ammoRespawnPoll -lt 45; $ammoRespawnPoll++) {
        Start-Sleep -Milliseconds 100
        $beforeAmmoShot = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
        $beforeAmmoShotPlayer = @($beforeAmmoShot.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
        if ($beforeAmmoShotPlayer.Count -lt 1) {
          throw "Ammo smoke player disappeared while waiting for alive shot setup."
        }
        if ($beforeAmmoShotPlayer[0].alive -eq $true) {
          break
        }
      }
      if ($beforeAmmoShotPlayer[0].alive -ne $true) {
        throw "Ammo smoke player was not alive before shot setup: hp=$($beforeAmmoShotPlayer[0].hp), respawnMs=$($beforeAmmoShotPlayer[0].respawnMs)"
      }
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 302
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $true
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 120
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 303
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 120

    $afterShot = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterShotPlayer = @($afterShot.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterShotPlayer.Count -lt 1) {
      throw "Ammo smoke player disappeared after shot."
    }
    if ($afterShotPlayer[0].ammoInMagazine -ne 11 -or $afterShotPlayer[0].reserveAmmo -ne 48) {
      throw "Ammo smoke shot did not consume exactly one round: ammo=$($afterShotPlayer[0].ammoInMagazine), reserve=$($afterShotPlayer[0].reserveAmmo)"
    }
    Test-PlayerPistolWeaponSync $afterShotPlayer[0] "Ammo smoke after shot" 11 48

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 304
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $false
      reloadPressed = $true
      castDash = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 120

    $duringReload = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $duringReloadPlayer = @($duringReload.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($duringReloadPlayer.Count -lt 1) {
      throw "Ammo smoke player disappeared during reload."
    }
    if ($duringReloadPlayer[0].reloadRemainingMs -le 0) {
      throw "Ammo smoke reload did not start: reload=$($duringReloadPlayer[0].reloadRemainingMs)"
    }
    if ($duringReloadPlayer[0].reloadPressed -ne $false) {
      throw "Ammo smoke reloadPressed was not consumed."
    }
    Test-PlayerPistolWeaponSync $duringReloadPlayer[0] "Ammo smoke during reload"

    $afterReload = $null
    $afterReloadPlayer = @()
    for ($reloadPoll = 0; $reloadPoll -lt 20; $reloadPoll++) {
      Start-Sleep -Milliseconds 120
      $afterReload = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $afterReloadPlayer = @($afterReload.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      if ($afterReloadPlayer.Count -lt 1) {
        throw "Ammo smoke player disappeared after reload."
      }
      if ($afterReloadPlayer[0].ammoInMagazine -eq 12 -and $afterReloadPlayer[0].reserveAmmo -eq 47 -and $afterReloadPlayer[0].reloadRemainingMs -eq 0) {
        break
      }
      if ($afterReloadPlayer[0].reloadRemainingMs -eq 0 -and $afterReloadPlayer[0].ammoInMagazine -lt 12 -and $afterReloadPlayer[0].reserveAmmo -gt 0) {
        Invoke-ContractJson "POST" "/battle/commands" @{
          battleId = $battleId
          playerId = $join.playerId
          ticketId = $join.ticketId
          clientTick = 410 + $reloadPoll
          movement = @{ x = 0; y = 0 }
          aim = @{ x = 0; y = -1 }
          primaryHeld = $false
          reloadPressed = $true
          castDash = $false
          switchWeaponDirection = 0
        } | Out-Null
      }
    }
    $reloadCompletedNormally = $afterReloadPlayer[0].ammoInMagazine -eq 12 -and $afterReloadPlayer[0].reserveAmmo -eq 47 -and $afterReloadPlayer[0].reloadRemainingMs -eq 0
    if (-not $reloadCompletedNormally -and $afterReloadPlayer[0].alive -ne $true) {
      for ($reloadRespawnPoll = 0; $reloadRespawnPoll -lt 45; $reloadRespawnPoll++) {
        Start-Sleep -Milliseconds 100
        $afterReload = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
        $afterReloadPlayer = @($afterReload.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
        if ($afterReloadPlayer.Count -lt 1) {
          throw "Ammo smoke player disappeared while waiting for respawn after interrupted reload."
        }
        if ($afterReloadPlayer[0].alive -eq $true) {
          break
        }
      }
      if ($afterReloadPlayer[0].alive -ne $true) {
        throw "Ammo smoke player did not respawn after interrupted reload: hp=$($afterReloadPlayer[0].hp), respawnMs=$($afterReloadPlayer[0].respawnMs)"
      }
    } elseif (-not $reloadCompletedNormally) {
      throw "Ammo smoke reload completion mismatch: ammo=$($afterReloadPlayer[0].ammoInMagazine), reserve=$($afterReloadPlayer[0].reserveAmmo), reload=$($afterReloadPlayer[0].reloadRemainingMs), alive=$($afterReloadPlayer[0].alive), respawnMs=$($afterReloadPlayer[0].respawnMs)"
    }
    if ($reloadCompletedNormally) {
      Test-PlayerPistolWeaponSync $afterReloadPlayer[0] "Ammo smoke after reload" 12 47 0
    } else {
      Test-PlayerPistolWeaponSync $afterReloadPlayer[0] "Ammo smoke after interrupted reload respawn" 12 48 0
    }

    if ($afterReloadPlayer[0].alive -ne $true) {
      for ($medkitRespawnPoll = 0; $medkitRespawnPoll -lt 45; $medkitRespawnPoll++) {
        Start-Sleep -Milliseconds 100
        $afterReload = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
        $afterReloadPlayer = @($afterReload.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
        if ($afterReloadPlayer.Count -lt 1) {
          throw "Medkit smoke player disappeared while waiting for alive pickup setup."
        }
        if ($afterReloadPlayer[0].alive -eq $true) {
          break
        }
      }
      if ($afterReloadPlayer[0].alive -ne $true) {
        throw "Medkit smoke player was not alive before pickup setup: hp=$($afterReloadPlayer[0].hp), respawnMs=$($afterReloadPlayer[0].respawnMs)"
      }
    }

    if ($afterReloadPlayer[0].ammoInMagazine -ne 12 -or $afterReloadPlayer[0].reloadRemainingMs -ne 0) {
      throw "Ammo smoke auto reload setup expected full ready pistol, got ammo=$($afterReloadPlayer[0].ammoInMagazine), reload=$($afterReloadPlayer[0].reloadRemainingMs)"
    }
    $autoReloadStartReserve = [int]$afterReloadPlayer[0].reserveAmmo
    if ($autoReloadStartReserve -lt 12) {
      throw "Ammo smoke auto reload setup expected at least 12 reserve rounds, got reserve=$autoReloadStartReserve"
    }

    $autoShotPlayer = $afterReloadPlayer
    for ($autoShot = 1; $autoShot -le 12; $autoShot++) {
      Invoke-ContractJson "POST" "/battle/commands" @{
        battleId = $battleId
        playerId = $join.playerId
        ticketId = $join.ticketId
        clientTick = 5000 + ($autoShot * 2)
        movement = @{ x = 0; y = 0 }
        aim = @{ x = 0; y = -1 }
        primaryHeld = $true
        reloadPressed = $false
        castDash = $false
        castBlink = $false
        castFreeze = $false
        switchWeaponDirection = 0
      } | Out-Null
      Start-Sleep -Milliseconds 90
      Invoke-ContractJson "POST" "/battle/commands" @{
        battleId = $battleId
        playerId = $join.playerId
        ticketId = $join.ticketId
        clientTick = 5001 + ($autoShot * 2)
        movement = @{ x = 0; y = 0 }
        aim = @{ x = 0; y = -1 }
        primaryHeld = $false
        reloadPressed = $false
        castDash = $false
        castBlink = $false
        castFreeze = $false
        switchWeaponDirection = 0
      } | Out-Null
      Start-Sleep -Milliseconds 80

      $autoShotState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $autoShotPlayer = @($autoShotState.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      if ($autoShotPlayer.Count -lt 1) {
        throw "Ammo smoke auto reload player disappeared after shot $autoShot."
      }
      if ($autoShotPlayer[0].alive -ne $true) {
        throw "Ammo smoke auto reload player died after shot ${autoShot}: hp=$($autoShotPlayer[0].hp), respawnMs=$($autoShotPlayer[0].respawnMs)"
      }

      $expectedAutoAmmo = 12 - $autoShot
      if ($autoShotPlayer[0].ammoInMagazine -ne $expectedAutoAmmo) {
        throw "Ammo smoke auto reload shot $autoShot ammo mismatch: expected=$expectedAutoAmmo, actual=$($autoShotPlayer[0].ammoInMagazine)"
      }
      if ($autoShotPlayer[0].reserveAmmo -ne $autoReloadStartReserve) {
        throw "Ammo smoke auto reload reserve changed before reload completed: start=$autoReloadStartReserve, actual=$($autoShotPlayer[0].reserveAmmo)"
      }
      if ($autoShot -lt 12 -and $autoShotPlayer[0].reloadRemainingMs -ne 0) {
        throw "Ammo smoke auto reload started early after shot ${autoShot}: reload=$($autoShotPlayer[0].reloadRemainingMs)"
      }
      if ($autoShot -lt 12) {
        Start-Sleep -Milliseconds 320
      }
    }

    if ($autoShotPlayer[0].ammoInMagazine -ne 0 -or $autoShotPlayer[0].reloadRemainingMs -le 0 -or $autoShotPlayer[0].reloadRemainingMs -gt 1000) {
      throw "Ammo smoke empty magazine did not start bounded auto reload: ammo=$($autoShotPlayer[0].ammoInMagazine), reload=$($autoShotPlayer[0].reloadRemainingMs)"
    }
    Test-PlayerPistolWeaponSync $autoShotPlayer[0] "Ammo smoke empty magazine auto reload started" 0 $autoReloadStartReserve

    $autoReloadExpectedReserve = $autoReloadStartReserve - 12
    $autoReloadCompletedPlayer = $autoShotPlayer
    for ($autoReloadPoll = 0; $autoReloadPoll -lt 20; $autoReloadPoll++) {
      Start-Sleep -Milliseconds 120
      $autoReloadState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $autoReloadCompletedPlayer = @($autoReloadState.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      if ($autoReloadCompletedPlayer.Count -lt 1) {
        throw "Ammo smoke auto reload player disappeared while waiting for completion."
      }
      if ($autoReloadCompletedPlayer[0].ammoInMagazine -eq 12 -and $autoReloadCompletedPlayer[0].reserveAmmo -eq $autoReloadExpectedReserve -and $autoReloadCompletedPlayer[0].reloadRemainingMs -eq 0) {
        break
      }
    }
    if ($autoReloadCompletedPlayer[0].ammoInMagazine -ne 12 -or $autoReloadCompletedPlayer[0].reserveAmmo -ne $autoReloadExpectedReserve -or $autoReloadCompletedPlayer[0].reloadRemainingMs -ne 0) {
      throw "Ammo smoke auto reload completion mismatch: ammo=$($autoReloadCompletedPlayer[0].ammoInMagazine), reserve=$($autoReloadCompletedPlayer[0].reserveAmmo), reload=$($autoReloadCompletedPlayer[0].reloadRemainingMs), expectedReserve=$autoReloadExpectedReserve"
    }
    Test-PlayerPistolWeaponSync $autoReloadCompletedPlayer[0] "Ammo smoke auto reload completed" 12 $autoReloadExpectedReserve 0
    $afterReload = $autoReloadState
    $afterReloadPlayer = $autoReloadCompletedPlayer

    $afterPickup = $null
    $afterPickupPlayer = @()
    $medkitContactPoints = @(
      @{ x = [double]$initialMedkit[0].position.x + 36; y = [double]$initialMedkit[0].position.y + 192 },
      @{ x = [double]$initialMedkit[0].position.x + 36; y = [double]$initialMedkit[0].position.y }
    )
    $medkitMoveStep = 0
    foreach ($medkitContactPoint in $medkitContactPoints) {
      for ($medkitStep = 0; $medkitStep -lt 42; $medkitStep++) {
        $medkitMoveState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
        $medkitMovePlayer = @($medkitMoveState.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
        $medkitMovePickup = @($medkitMoveState.pickups | Where-Object { $_.pickupId -ceq "pickup-medkit-1" } | Select-Object -First 1)
        if ($medkitMovePlayer.Count -lt 1) {
          throw "Medkit smoke player disappeared while moving to pickup."
        }
        if ($medkitMovePickup.Count -lt 1) {
          throw "Medkit smoke pickup disappeared while moving to pickup."
        }
        if ($medkitMovePickup[0].available -eq $false) {
          $afterPickup = $medkitMoveState
          $afterPickupPlayer = $medkitMovePlayer
          break
        }

        $medkitDx = [double]$medkitContactPoint.x - [double]$medkitMovePlayer[0].position.x
        $medkitDy = [double]$medkitContactPoint.y - [double]$medkitMovePlayer[0].position.y
        $medkitDistance = [math]::Sqrt($medkitDx * $medkitDx + $medkitDy * $medkitDy)
        $medkitMoveX = if ($medkitDistance -gt 0.001) { $medkitDx / $medkitDistance } else { 0 }
        $medkitMoveY = if ($medkitDistance -gt 0.001) { $medkitDy / $medkitDistance } else { 0 }

        Invoke-ContractJson "POST" "/battle/commands" @{
          battleId = $battleId
          playerId = $join.playerId
          ticketId = $join.ticketId
          clientTick = 6000 + $medkitMoveStep
          movement = @{ x = $medkitMoveX; y = $medkitMoveY }
          aim = @{ x = $medkitMoveX; y = $medkitMoveY }
          primaryHeld = $false
          reloadPressed = $false
          castDash = $false
          castBlink = $false
          pointerWorld = @{
            x = [double]$medkitContactPoint.x
            y = [double]$medkitContactPoint.y
          }
          switchWeaponDirection = 0
        } | Out-Null
        $medkitMoveStep += 1
        Start-Sleep -Milliseconds 120
      }
      if ($null -ne $afterPickup) {
        break
      }
    }

    if ($null -eq $afterPickup) {
      $afterPickup = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $afterPickupPlayer = @($afterPickup.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    }
    if ($afterPickupPlayer.Count -lt 1) {
      throw "Medkit smoke player disappeared after pickup movement."
    }
    if ($afterPickupPlayer[0].hp -gt $afterPickupPlayer[0].maxHp) {
      throw "Medkit smoke player HP exceeded max HP: hp=$($afterPickupPlayer[0].hp), maxHp=$($afterPickupPlayer[0].maxHp)"
    }
    Test-PlayerPistolWeaponSync $afterPickupPlayer[0] "Medkit smoke after pickup"

    $afterPickupMedkit = @($afterPickup.pickups | Where-Object { $_.pickupId -ceq "pickup-medkit-1" } | Select-Object -First 1)
    if ($afterPickupMedkit.Count -lt 1) {
      throw "Medkit smoke pickup disappeared after pickup movement."
    }
    if ($afterPickupMedkit[0].available -ne $false -or $afterPickupMedkit[0].respawnMs -le 0 -or $afterPickupMedkit[0].respawnMs -gt 10000) {
      throw "Medkit smoke pickup was not consumed with respawn timer: available=$($afterPickupMedkit[0].available), respawnMs=$($afterPickupMedkit[0].respawnMs)"
    }
    $afterPickupEvents = Test-ArrayEnvelope $afterPickup "events"
    $medkitHealEvent = @(
      $afterPickupEvents |
        Where-Object {
          $_.type -ceq "heal" -and
          $_.kind -ceq "heal" -and
          (
            ($null -ne $_.source -and $_.source.playerId -ceq $join.playerId) -or
            ($null -ne $_.target -and $_.target.playerId -ceq $join.playerId)
          )
        } |
        Select-Object -First 1
    )
    if ($medkitHealEvent.Count -lt 1) {
      throw "Medkit smoke did not find related heal event in battle state events."
    }

    $afterPickupSpawnWeapon = @($afterPickup.pickups | Where-Object { $_.pickupId -ceq "pickup-gatling-1" } | Select-Object -First 1)
    if ($afterPickupSpawnWeapon.Count -lt 1) {
      throw "Weapon pickup smoke spawn Gatling pickup disappeared after medkit movement."
    }
    if ($afterPickupSpawnWeapon[0].kind -ne "Weapon" -or $afterPickupSpawnWeapon[0].weaponKind -ne "Gatling") {
      throw "Weapon pickup smoke spawn Gatling identity changed: kind=$($afterPickupSpawnWeapon[0].kind), weaponKind=$($afterPickupSpawnWeapon[0].weaponKind)"
    }
    if ($afterPickupSpawnWeapon[0].available -eq $false -and ($afterPickupSpawnWeapon[0].respawnMs -le 0 -or $afterPickupSpawnWeapon[0].respawnMs -gt 10000)) {
      throw "Weapon pickup smoke spawn Gatling consumed timer was invalid: available=$($afterPickupSpawnWeapon[0].available), respawnMs=$($afterPickupSpawnWeapon[0].respawnMs)"
    }
    if ($afterPickupSpawnWeapon[0].available -eq $true -and $afterPickupSpawnWeapon[0].respawnMs -ne 0) {
      throw "Weapon pickup smoke spawn Gatling available timer was invalid: available=$($afterPickupSpawnWeapon[0].available), respawnMs=$($afterPickupSpawnWeapon[0].respawnMs)"
    }

    $afterPickupWeapons = Test-ArrayEnvelope $afterPickupPlayer[0] "weapons"
    $afterPickupGatlingWeapon = @($afterPickupWeapons | Where-Object { $_.weaponKind -ceq "Gatling" } | Select-Object -First 1)
    if ($afterPickupGatlingWeapon.Count -lt 1) {
      throw "Weapon pickup smoke player inventory lost spawn Gatling after medkit movement."
    }
    if ($afterPickupGatlingWeapon[0].ammoInMagazine -ne 0 -or $afterPickupGatlingWeapon[0].magazineSize -ne 0 -or $afterPickupGatlingWeapon[0].reserveAmmo -ne 0) {
      throw "Weapon pickup smoke Gatling inventory mismatch after medkit movement: ammo=$($afterPickupGatlingWeapon[0].ammoInMagazine), mag=$($afterPickupGatlingWeapon[0].magazineSize), reserve=$($afterPickupGatlingWeapon[0].reserveAmmo)"
    }

    $weaponPickupEvent = @(
      $afterPickupEvents |
        Where-Object {
          $_.type -ceq "pickup" -and
          $_.kind -ceq "pickup" -and
          $null -ne $_.source -and
          $null -ne $_.target -and
          $_.source.playerId -ceq $join.playerId -and
          $_.target.playerId -ceq $join.playerId
        } |
        Select-Object -First 1
    )
    if ($weaponPickupEvent.Count -lt 1) {
      throw "Weapon pickup smoke did not find related pickup event in battle state events."
    }

    $dashSetupPoint = @{ x = 1040; y = 320 }
    $beforeDash = $afterPickup
    $beforeDashPlayer = $afterPickupPlayer
    for ($dashSetupStep = 0; $dashSetupStep -lt 35; $dashSetupStep++) {
      $beforeDash = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $beforeDashPlayer = @($beforeDash.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      if ($beforeDashPlayer.Count -lt 1) {
        throw "Dash smoke player disappeared while moving to dash setup point."
      }
      if ($beforeDashPlayer[0].alive -ne $true) {
        throw "Dash smoke player died before dash setup: hp=$($beforeDashPlayer[0].hp), respawnMs=$($beforeDashPlayer[0].respawnMs)"
      }

      $dashSetupDx = [double]$dashSetupPoint.x - [double]$beforeDashPlayer[0].position.x
      $dashSetupDy = [double]$dashSetupPoint.y - [double]$beforeDashPlayer[0].position.y
      $dashSetupDistance = [math]::Sqrt($dashSetupDx * $dashSetupDx + $dashSetupDy * $dashSetupDy)
      if ($dashSetupDistance -le 14) {
        break
      }

      $dashSetupMoveX = if ($dashSetupDistance -gt 0.001) { $dashSetupDx / $dashSetupDistance } else { 0 }
      $dashSetupMoveY = if ($dashSetupDistance -gt 0.001) { $dashSetupDy / $dashSetupDistance } else { 0 }
      $dashSetupBlinkSkill = @($beforeDashPlayer[0].skills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
      $canBlinkToDashSetup = $dashSetupBlinkSkill.Count -gt 0 -and $dashSetupBlinkSkill[0].cooldownMs -eq 0 -and $dashSetupDistance -gt 120 -and $dashSetupDistance -le 250

      Invoke-ContractJson "POST" "/battle/commands" @{
        battleId = $battleId
        playerId = $join.playerId
        ticketId = $join.ticketId
        clientTick = 7000 + $dashSetupStep
        movement = if ($canBlinkToDashSetup) { @{ x = 0; y = 0 } } else { @{ x = $dashSetupMoveX; y = $dashSetupMoveY } }
        aim = @{ x = $dashSetupMoveX; y = $dashSetupMoveY }
        primaryHeld = $false
        reloadPressed = $false
        castDash = $false
        castBlink = $canBlinkToDashSetup
        pointerWorld = @{
          x = [double]$dashSetupPoint.x
          y = [double]$dashSetupPoint.y
        }
        switchWeaponDirection = 0
      } | Out-Null
      Start-Sleep -Milliseconds 120
    }

    $dashStartX = [double]$beforeDashPlayer[0].position.x
    $dashStartY = [double]$beforeDashPlayer[0].position.y
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 8000
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = 1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $true
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 120

    $afterDash = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    if ($afterDash.phase -ne "active") {
      throw "Dash smoke expected battle phase to remain active, got phase=$($afterDash.phase)"
    }
    $afterDashPlayer = @($afterDash.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterDashPlayer.Count -lt 1) {
      throw "Dash smoke player disappeared after castDash command."
    }
    $dashDelta = [math]::Sqrt([math]::Pow(([double]$afterDashPlayer[0].position.x - $dashStartX), 2) + [math]::Pow(([double]$afterDashPlayer[0].position.y - $dashStartY), 2))
    if ($dashDelta -lt 120) {
      throw "Dash smoke expected significant server-owned displacement, got delta=$dashDelta"
    }
    $afterDashSkills = Test-ArrayEnvelope $afterDashPlayer[0] "skills"
    $afterDashSkill = @($afterDashSkills | Where-Object { $_.kind -ceq "Dash" } | Select-Object -First 1)
    if ($afterDashSkill.Count -lt 1) {
      throw "Dash smoke player skills did not include Dash after cast."
    }
    if ($afterDashSkill[0].cooldownMs -le 0 -or $afterDashSkill[0].activeMs -lt 0) {
      throw "Dash smoke cooldown/active mismatch: cooldownMs=$($afterDashSkill[0].cooldownMs), activeMs=$($afterDashSkill[0].activeMs)"
    }

    Start-Sleep -Milliseconds 250
    $laterPickupState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $laterMedkit = @($laterPickupState.pickups | Where-Object { $_.pickupId -ceq "pickup-medkit-1" } | Select-Object -First 1)
    if ($laterMedkit.Count -lt 1) {
      throw "Medkit smoke pickup disappeared during respawn countdown."
    }
    if ($laterMedkit[0].available -ne $false -or $laterMedkit[0].respawnMs -le 0 -or $laterMedkit[0].respawnMs -ge $afterPickupMedkit[0].respawnMs) {
      throw "Medkit smoke respawn countdown did not decrease: first=$($afterPickupMedkit[0].respawnMs), later=$($laterMedkit[0].respawnMs), available=$($laterMedkit[0].available)"
    }

    "battleId=$battleId; pistol ammo/manual reload/auto reload, medkit behavior, spawn Gatling pickup, Rocket pickup catalog, weapon switch, and Dash checked"
  } finally {
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/commands authoritative obstacle collision" {
  $join = $null
  $extraJoins = @()

  try {
    $obstacleHandle = New-BattleSmokeHandle "obst"
    $join = Join-AuthenticatedBattleQueue -Handle $obstacleHandle -Rating "1215" -Skin "blue" -QueueRequestId "contract-$obstacleHandle"
    $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoin.Count -gt 0) {
      throw "Obstacle smoke queue join missing fields: $($missingJoin -join ', ')"
    }
    foreach ($suffix in @("obstb", "obstc", "obstd")) {
      $extraHandle = New-BattleSmokeHandle $suffix
      $extraJoin = Join-AuthenticatedBattleQueue -Handle $extraHandle -Rating "1215" -Skin "blue" -QueueRequestId "contract-$extraHandle"
      $missingExtraJoin = Test-Fields $extraJoin @("ticketId", "playerId", "roomId", "startsAt")
      if ($missingExtraJoin.Count -gt 0) {
        throw "Obstacle smoke extra queue join missing fields: $($missingExtraJoin -join ', ')"
      }
      if ($extraJoin.roomId -ne $join.roomId) {
        throw "Obstacle smoke expected extra player to join same room: first=$($join.roomId), extra=$($extraJoin.roomId)"
      }
      $extraJoins += $extraJoin
    }
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if ($null -ne $status.battleSession -and $status.phase -eq "active") {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession) {
      throw "Obstacle smoke battle session was not created."
    }
    if ($status.phase -ne "active") {
      throw "Obstacle smoke expected active battle, got phase=$($status.phase)"
    }

    $battleId = $status.battleSession.battleId
    $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $player = @($state.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($player.Count -lt 1) {
      throw "Obstacle smoke player was not present in battle state."
    }
    $movementJoin = $extraJoins[2]
    $movementPlayer = @($state.players | Where-Object { $_.playerId -ceq $movementJoin.playerId } | Select-Object -First 1)
    if ($movementPlayer.Count -lt 1) {
      throw "Obstacle smoke movement player was not present in battle state."
    }
    if ([math]::Abs([double]$movementPlayer[0].position.x - 1600.0) -gt 0.1 -or [math]::Abs([double]$movementPlayer[0].position.y - 320.0) -gt 0.1) {
      throw "Obstacle smoke movement player expected spawn point 3 at (1600, 320), got ($($movementPlayer[0].position.x), $($movementPlayer[0].position.y))"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $movementJoin.playerId
      ticketId = $movementJoin.ticketId
      clientTick = 1
      movement = @{ x = 0; y = 1 }
      aim = @{ x = 0; y = 1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 2000
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $movementJoin.playerId
      ticketId = $movementJoin.ticketId
      clientTick = 2
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = 1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80

    $afterMovement = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterMovementPlayer = @($afterMovement.players | Where-Object { $_.playerId -ceq $movementJoin.playerId } | Select-Object -First 1)
    if ($afterMovementPlayer.Count -lt 1) {
      throw "Obstacle smoke movement player disappeared after moving into lane wall."
    }
    if ([math]::Abs([double]$afterMovementPlayer[0].position.x - 1600.0) -gt 0.1) {
      throw "Obstacle smoke movement collision changed x lane unexpectedly: x=$($afterMovementPlayer[0].position.x)"
    }
    if ([double]$afterMovementPlayer[0].position.y -gt 590.0) {
      throw "Obstacle smoke movement passed through right lane wall: y=$($afterMovementPlayer[0].position.y)"
    }
    if ([double]$afterMovementPlayer[0].position.y -le [double]$movementPlayer[0].position.y) {
      throw "Obstacle smoke movement did not advance before blocker: initial=$($movementPlayer[0].position.y), after=$($afterMovementPlayer[0].position.y)"
    }

    $coverNw = @{ x = 416; y = 416 }
    $blinkStartX = [double]$player[0].position.x
    $blinkStartY = [double]$player[0].position.y
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 1
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $true
      castFreeze = $false
      pointerWorld = $coverNw
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80

    $afterBlockedBlink = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterBlockedBlinkPlayer = @($afterBlockedBlink.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterBlockedBlinkPlayer.Count -lt 1) {
      throw "Obstacle smoke player disappeared after blocked Blink."
    }
    $blockedBlinkSkill = @($afterBlockedBlinkPlayer[0].skills | Where-Object { $_.kind -ceq "Blink" } | Select-Object -First 1)
    if ($blockedBlinkSkill.Count -lt 1 -or $blockedBlinkSkill[0].cooldownMs -ne 0 -or $blockedBlinkSkill[0].activeMs -ne 0) {
      throw "Obstacle smoke Blink into cover triggered cooldown/active: cooldownMs=$($blockedBlinkSkill[0].cooldownMs), activeMs=$($blockedBlinkSkill[0].activeMs)"
    }
    $blockedBlinkDelta = [math]::Sqrt([math]::Pow(([double]$afterBlockedBlinkPlayer[0].position.x - $blinkStartX), 2) + [math]::Pow(([double]$afterBlockedBlinkPlayer[0].position.y - $blinkStartY), 2))
    if ($blockedBlinkDelta -gt 20) {
      throw "Obstacle smoke Blink into cover moved player unexpectedly: delta=$blockedBlinkDelta"
    }

    $shotStartX = [double]$afterBlockedBlinkPlayer[0].position.x
    $shotStartY = [double]$afterBlockedBlinkPlayer[0].position.y
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 2
      movement = @{ x = 0; y = 0 }
      aim = @{ x = ([double]$coverNw.x - $shotStartX); y = ([double]$coverNw.y - $shotStartY) }
      primaryHeld = $true
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 3
      movement = @{ x = 0; y = 0 }
      aim = @{ x = ([double]$coverNw.x - $shotStartX); y = ([double]$coverNw.y - $shotStartY) }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 260

    $afterObstacleShot = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterObstacleShotPlayer = @($afterObstacleShot.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterObstacleShotPlayer.Count -lt 1) {
      throw "Obstacle smoke player disappeared after obstacle shot."
    }
    if ($afterObstacleShotPlayer[0].ammoInMagazine -ne 11) {
      throw "Obstacle smoke expected one fired pistol round, got ammoInMagazine=$($afterObstacleShotPlayer[0].ammoInMagazine)"
    }
    $ownerProjectiles = @(
      $afterObstacleShot.projectiles |
        Where-Object { $_.ownerHeroId -ceq $afterObstacleShotPlayer[0].heroId -and $_.kind -ceq "pistol-bullet" }
    )
    if ($ownerProjectiles.Count -gt 0) {
      $sample = $ownerProjectiles[0]
      throw "Obstacle smoke expected pistol bullet to be removed by cover-nw, found $($ownerProjectiles.Count) owner projectile(s); sample position=($($sample.position.x), $($sample.position.y)), ttlMs=$($sample.ttlMs)"
    }

    "battleId=$battleId; ordinary movement stopped before lane wall; blocked Blink into cover-nw and pistol projectile obstacle removal checked"
  } finally {
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/commands authoritative terminal elimination" {
  $join = $null
  $victimJoin = $null
  $extraJoins = @()

  try {
    $killerHandle = New-BattleSmokeHandle "kill"
    $victimHandle = New-BattleSmokeHandle "vict"
    $join = Join-AuthenticatedBattleQueue -Handle $killerHandle -Rating "1220" -Skin "blue" -QueueRequestId "contract-$killerHandle"
    $victimJoin = Join-AuthenticatedBattleQueue -Handle $victimHandle -Rating "1221" -Skin "red" -QueueRequestId "contract-$victimHandle"
    if ($join.roomId -ne $victimJoin.roomId) {
      throw "Elimination smoke joins landed in different rooms: killer=$($join.roomId), victim=$($victimJoin.roomId)"
    }
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if ($null -ne $status.battleSession -and $status.phase -eq "active") {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession -or $status.phase -ne "active") {
      throw "Elimination smoke expected active battle, got phase=$($status.phase)"
    }

    $battleId = $status.battleSession.battleId
    $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $killer = @($state.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($killer.Count -lt 1) {
      throw "Elimination smoke missing killer in initial battle state."
    }

    $laneSetupPoint = @{ x = 1100; y = 800 }
    for ($setupStep = 0; $setupStep -lt 24; $setupStep++) {
      $setupState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $setupKiller = @($setupState.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      if ($setupKiller.Count -lt 1) {
        throw "Elimination smoke missing killer during lane setup."
      }
      if ($setupKiller[0].alive -ne $true) {
        throw "Elimination smoke killer died during lane setup: hp=$($setupKiller[0].hp), respawnMs=$($setupKiller[0].respawnMs)"
      }

      $setupDx = [double]$laneSetupPoint.x - [double]$setupKiller[0].position.x
      $setupDy = [double]$laneSetupPoint.y - [double]$setupKiller[0].position.y
      $setupDistance = [math]::Sqrt($setupDx * $setupDx + $setupDy * $setupDy)
      if ($setupDistance -le 18) {
        $state = $setupState
        $killer = $setupKiller
        break
      }

      $setupMoveX = if ($setupDistance -gt 0.001) { $setupDx / $setupDistance } else { 0 }
      $setupMoveY = if ($setupDistance -gt 0.001) { $setupDy / $setupDistance } else { 0 }
      Invoke-ContractJson "POST" "/battle/commands" @{
        battleId = $battleId
        playerId = $join.playerId
        ticketId = $join.ticketId
        clientTick = 600 + $setupStep
        movement = @{ x = $setupMoveX; y = $setupMoveY }
        aim = @{ x = 1; y = 0 }
        primaryHeld = $false
        reloadPressed = $false
        castDash = $false
        castBlink = $false
        castFreeze = $false
        switchWeaponDirection = 0
      } | Out-Null
      Start-Sleep -Milliseconds 120

      if ($setupStep -eq 23) {
        $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
        $killer = @($state.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      }
    }

    $victim = @(
      $state.players |
        Where-Object {
          $_.playerId -cne $join.playerId -and
          $_.alive -eq $true -and
          [double]$_.position.x -gt [double]$killer[0].position.x -and
          [math]::Abs([double]$_.position.y - [double]$killer[0].position.y) -le 80
        } |
        Sort-Object {
          -1.0 * [double]$_.position.x
        } |
        Select-Object -First 1
    )
    if ($victim.Count -lt 1) {
      $victim = @(
        $state.players |
          Where-Object { $_.playerId -cne $join.playerId -and $_.alive -eq $true } |
          Sort-Object {
            [math]::Sqrt(
              [math]::Pow(([double]$_.position.x - [double]$killer[0].position.x), 2) +
              [math]::Pow(([double]$_.position.y - [double]$killer[0].position.y), 2)
            )
          } |
          Select-Object -First 1
      )
    }
    if ($victim.Count -lt 1) {
      throw "Elimination smoke missing victim in initial battle state."
    }
    $victimPlayerId = $victim[0].playerId
    $victimDistance = [math]::Sqrt([math]::Pow(([double]$victim[0].position.x - [double]$killer[0].position.x), 2) + [math]::Pow(([double]$victim[0].position.y - [double]$killer[0].position.y), 2))
    if ($victimDistance -gt 1150) {
      throw "Elimination smoke nearest victim was too far for pistol setup: victim=$victimPlayerId distance=$victimDistance"
    }
    $missingVictimFields = Test-Fields $victim[0] @("hp", "maxHp", "alive", "eliminatedAtMs", "respawnMs", "position")
    if ($missingVictimFields.Count -gt 0) {
      throw "Elimination smoke victim missing fields: $($missingVictimFields -join ', ')"
    }
    if ($victim[0].respawnMs -ne 0 -or $victim[0].alive -ne $true) {
      throw "Elimination smoke initial victim state mismatch: alive=$($victim[0].alive), respawnMs=$($victim[0].respawnMs)"
    }

    $eliminatedVictim = $null
    for ($shot = 0; $shot -lt 12; $shot++) {
      $shotState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
      $shotKiller = @($shotState.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
      $shotVictim = @($shotState.players | Where-Object { $_.playerId -ceq $victimPlayerId } | Select-Object -First 1)
      if ($shotKiller.Count -lt 1 -or $shotVictim.Count -lt 1) {
        throw "Elimination smoke missing players during lethal shot loop."
      }
      if ($shotVictim[0].alive -eq $false -and $shotVictim[0].respawnMs -eq 0) {
        $eliminatedVictim = $shotVictim[0]
        break
      }

      $aimX = [double]$shotVictim[0].position.x - [double]$shotKiller[0].position.x
      $aimY = [double]$shotVictim[0].position.y - [double]$shotKiller[0].position.y
      Invoke-ContractJson "POST" "/battle/commands" @{
        battleId = $battleId
        playerId = $join.playerId
        ticketId = $join.ticketId
        clientTick = 700 + ($shot * 2)
        movement = @{ x = 0; y = 0 }
        aim = @{ x = $aimX; y = $aimY }
        primaryHeld = $true
        reloadPressed = $false
        castDash = $false
        castBlink = $false
        castFreeze = $false
        switchWeaponDirection = 0
      } | Out-Null
      Start-Sleep -Milliseconds 360
      Invoke-ContractJson "POST" "/battle/commands" @{
        battleId = $battleId
        playerId = $join.playerId
        ticketId = $join.ticketId
        clientTick = 701 + ($shot * 2)
        movement = @{ x = 0; y = 0 }
        aim = @{ x = $aimX; y = $aimY }
        primaryHeld = $false
        reloadPressed = $false
        castDash = $false
        castBlink = $false
        castFreeze = $false
        switchWeaponDirection = 0
      } | Out-Null
      Start-Sleep -Milliseconds 180
    }

    $afterKill = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    if ($afterKill.phase -ne "active" -and $afterKill.phase -ne "finished") {
      throw "Elimination smoke expected active or finished battle after lethal damage, got phase=$($afterKill.phase)"
    }
    $afterKillVictim = @($afterKill.players | Where-Object { $_.playerId -ceq $victimPlayerId } | Select-Object -First 1)
    if ($afterKillVictim.Count -lt 1) {
      throw "Elimination smoke victim disappeared after lethal damage."
    }
    if ($null -eq $eliminatedVictim) {
      $eliminatedVictim = $afterKillVictim[0]
    }
    if ($afterKillVictim[0].alive -ne $false -or $afterKillVictim[0].hp -ne 0 -or $afterKillVictim[0].respawnMs -ne 0) {
      throw "Elimination smoke victim did not enter terminal dead state: alive=$($afterKillVictim[0].alive), hp=$($afterKillVictim[0].hp), respawnMs=$($afterKillVictim[0].respawnMs)"
    }
    if ($null -eq $afterKillVictim[0].eliminatedAtMs) {
      throw "Elimination smoke victim did not retain eliminatedAtMs."
    }

    Start-Sleep -Milliseconds 3400
    $afterNoRespawn = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterNoRespawnVictim = @($afterNoRespawn.players | Where-Object { $_.playerId -ceq $victimPlayerId } | Select-Object -First 1)
    if ($afterNoRespawnVictim.Count -lt 1) {
      throw "Elimination smoke victim disappeared after no-respawn wait."
    }
    if ($afterNoRespawnVictim[0].alive -ne $false -or $afterNoRespawnVictim[0].hp -ne 0 -or $afterNoRespawnVictim[0].respawnMs -ne 0) {
      throw "Elimination smoke victim respawned unexpectedly: alive=$($afterNoRespawnVictim[0].alive), hp=$($afterNoRespawnVictim[0].hp), respawnMs=$($afterNoRespawnVictim[0].respawnMs)"
    }
    if ($afterNoRespawnVictim[0].eliminatedAtMs -ne $afterKillVictim[0].eliminatedAtMs) {
      throw "Elimination smoke eliminatedAtMs changed after no-respawn wait: before=$($afterKillVictim[0].eliminatedAtMs), after=$($afterNoRespawnVictim[0].eliminatedAtMs)"
    }

    $afterNoRespawnEvents = Test-ArrayEnvelope $afterNoRespawn "events"
    $respawnEvent = @(
      $afterNoRespawnEvents |
        Where-Object {
          $_.type -ceq "respawn" -and
          $_.kind -ceq "respawn" -and
          $null -ne $_.target -and
          $_.target.playerId -ceq $victimPlayerId
        } |
        Select-Object -First 1
    )
    if ($respawnEvent.Count -gt 0) {
      throw "Elimination smoke found unexpected respawn event: eventId=$($respawnEvent[0].eventId)"
    }

    "battleId=$battleId; victim stayed eliminated with respawnMs=0; no respawn event emitted"
  } finally {
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $victimJoin -and (Test-HasField $victimJoin "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $victimJoin.ticketId } | Out-Null } catch {}
    }
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/commands Shift sprint moves farther than walk" {
  $join = $null
  $extraJoins = @()

  try {
    $sprintHandle = New-BattleSmokeHandle "sprint"
    $join = Join-AuthenticatedBattleQueue -Handle $sprintHandle -Rating "1215" -Skin "blue" -QueueRequestId "contract-$sprintHandle"
    $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoin.Count -gt 0) {
      throw "Sprint smoke queue join missing fields: $($missingJoin -join ', ')"
    }
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if ($null -ne $status.battleSession -and $status.phase -eq "active") {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession -or $status.phase -ne "active") {
      throw "Sprint smoke battle session was not active."
    }

    $battleId = $status.battleSession.battleId
    $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $player = @($state.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($player.Count -lt 1) {
      throw "Sprint smoke player was not present in battle state."
    }
    $missingInitialSprintFields = Test-Fields $player[0] @("position", "sprint", "stamina", "maxStamina")
    if ($missingInitialSprintFields.Count -gt 0) {
      throw "Sprint smoke player missing fields: $($missingInitialSprintFields -join ', ')"
    }
    $initialStamina = [double]$player[0].stamina
    if ($initialStamina -le 0 -or [double]$player[0].maxStamina -lt $initialStamina) {
      throw "Sprint smoke initial stamina invalid: stamina=$($player[0].stamina), maxStamina=$($player[0].maxStamina)"
    }

    $walkStart = $player[0].position
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 1
      movement = @{ x = 1; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      sprint = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 260

    $afterWalk = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $walkPlayer = @($afterWalk.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($walkPlayer.Count -lt 1) {
      throw "Sprint smoke player disappeared after walk sample."
    }
    $missingWalkSprintFields = Test-Fields $walkPlayer[0] @("position", "sprint", "stamina")
    if ($missingWalkSprintFields.Count -gt 0) {
      throw "Sprint smoke walk player missing fields: $($missingWalkSprintFields -join ', ')"
    }
    $walkDistance = [math]::Sqrt([math]::Pow(([double]$walkPlayer[0].position.x - [double]$walkStart.x), 2) + [math]::Pow(([double]$walkPlayer[0].position.y - [double]$walkStart.y), 2))
    if ($walkDistance -lt 8) {
      throw "Sprint smoke walk sample was too short: walkDistance=$walkDistance"
    }
    if ([double]$walkPlayer[0].stamina -lt ($initialStamina - 0.1)) {
      throw "Sprint smoke walk unexpectedly consumed stamina: initial=$initialStamina, afterWalk=$($walkPlayer[0].stamina)"
    }
    if ($walkPlayer[0].sprint -ne $false) {
      throw "Sprint smoke walk should not mark sprint=true."
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 2
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      sprint = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80

    $sprintStartState = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $sprintStartPlayer = @($sprintStartState.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($sprintStartPlayer.Count -lt 1) {
      throw "Sprint smoke player disappeared before sprint sample."
    }
    $missingSprintStartFields = Test-Fields $sprintStartPlayer[0] @("position", "stamina")
    if ($missingSprintStartFields.Count -gt 0) {
      throw "Sprint smoke start player missing fields: $($missingSprintStartFields -join ', ')"
    }
    $sprintStartStamina = [double]$sprintStartPlayer[0].stamina

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 3
      movement = @{ x = 1; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      sprint = $true
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 260

    $afterSprint = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $sprintPlayer = @($afterSprint.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($sprintPlayer.Count -lt 1) {
      throw "Sprint smoke player disappeared after sprint sample."
    }
    $missingSprintFields = Test-Fields $sprintPlayer[0] @("position", "sprint", "stamina")
    if ($missingSprintFields.Count -gt 0) {
      throw "Sprint smoke sprint player missing fields: $($missingSprintFields -join ', ')"
    }
    $sprintDistance = [math]::Sqrt([math]::Pow(([double]$sprintPlayer[0].position.x - [double]$sprintStartPlayer[0].position.x), 2) + [math]::Pow(([double]$sprintPlayer[0].position.y - [double]$sprintStartPlayer[0].position.y), 2))
    if ($sprintDistance -le ($walkDistance * 1.25)) {
      throw "Sprint smoke expected sprint to move farther than walk: walk=$walkDistance, sprint=$sprintDistance"
    }
    if ($sprintPlayer[0].sprint -ne $true) {
      throw "Sprint smoke expected sprint=true while stamina exists."
    }
    $afterSprintStamina = [double]$sprintPlayer[0].stamina
    if ($afterSprintStamina -ge $sprintStartStamina) {
      throw "Sprint smoke expected sprint to consume stamina: before=$sprintStartStamina, after=$afterSprintStamina"
    }
    if ([Math]::Abs($afterSprintStamina - [Math]::Round($afterSprintStamina)) -lt 0.0001) {
      throw "Sprint smoke expected precise fractional stamina after sprint, got $afterSprintStamina"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 4
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      sprint = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 260

    $afterRecover = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $recoverPlayer = @($afterRecover.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($recoverPlayer.Count -lt 1) {
      throw "Sprint smoke player disappeared after recovery sample."
    }
    $missingRecoverFields = Test-Fields $recoverPlayer[0] @("sprint", "stamina")
    if ($missingRecoverFields.Count -gt 0) {
      throw "Sprint smoke recovery player missing fields: $($missingRecoverFields -join ', ')"
    }
    if ($recoverPlayer[0].sprint -ne $false) {
      throw "Sprint smoke expected idle command to clear effective sprint."
    }
    $recoveredStamina = [double]$recoverPlayer[0].stamina
    if ($recoveredStamina -le $afterSprintStamina) {
      throw "Sprint smoke expected idle recovery to increase stamina: afterSprint=$afterSprintStamina, recovered=$recoveredStamina"
    }

    "battleId=$battleId; walkDistance=$walkDistance; sprintDistance=$sprintDistance; stamina=$initialStamina->$afterSprintStamina->$recoveredStamina"
  } finally {
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/commands Freeze projectile slow + expiry" {
  $join = $null
  $extraJoins = @()

  try {
    $freezeHandle = New-BattleSmokeHandle "freeze"
    $join = Join-AuthenticatedBattleQueue -Handle $freezeHandle -Rating "1215" -Skin "blue" -QueueRequestId "contract-$freezeHandle"
    $missingJoin = Test-Fields $join @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoin.Count -gt 0) {
      throw "Freeze projectile smoke queue join missing fields: $($missingJoin -join ', ')"
    }
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, [Int64]$join.startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $status = $null
    for ($i = 0; $i -lt 40; $i++) {
      $status = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($join.ticketId))"
      if ($null -ne $status.battleSession -and $status.phase -eq "active") {
        break
      }
      Start-Sleep -Milliseconds 250
    }
    if ($null -eq $status.battleSession -or $status.phase -ne "active") {
      throw "Freeze projectile smoke battle session was not active."
    }

    $battleId = $status.battleSession.battleId
    $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $player = @($state.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($player.Count -lt 1) {
      throw "Freeze projectile smoke player was not present in battle state."
    }

    $freezeTarget = @{
      x = [double]$player[0].position.x
      y = [double]$player[0].position.y - 80
    }
    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 1
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $true
      pointerWorld = $freezeTarget
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80

    $afterFreeze = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterFreezeFields = Test-ArrayEnvelope $afterFreeze "slowFields"
    if ($afterFreezeFields.Count -ne 1) {
      throw "Freeze projectile smoke expected one active slow field, got $($afterFreezeFields.Count)."
    }
    $afterFreezePlayer = @($afterFreeze.players | Where-Object { $_.playerId -ceq $join.playerId } | Select-Object -First 1)
    if ($afterFreezePlayer.Count -lt 1) {
      throw "Freeze projectile smoke player disappeared after Freeze."
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 2
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $true
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null
    Start-Sleep -Milliseconds 80

    $projectileSampleA = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $sampleAProjectiles = Test-ArrayEnvelope $projectileSampleA "projectiles"
    $projectileA = @(
      $sampleAProjectiles |
        Where-Object { $_.ownerHeroId -ceq $afterFreezePlayer[0].heroId -and $_.kind -ceq "pistol-bullet" } |
        Sort-Object ttlMs -Descending |
        Select-Object -First 1
    )
    if ($projectileA.Count -lt 1) {
      throw "Freeze projectile smoke did not find owner pistol projectile spawned inside active slow field."
    }

    Start-Sleep -Milliseconds 100
    $projectileSampleB = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $sampleBProjectiles = Test-ArrayEnvelope $projectileSampleB "projectiles"
    $projectileB = @(
      $sampleBProjectiles |
        Where-Object { $_.projectileId -ceq $projectileA[0].projectileId } |
        Select-Object -First 1
    )
    if ($projectileB.Count -lt 1) {
      throw "Freeze projectile smoke owner pistol projectile disappeared before displacement sample completed."
    }

    $elapsedProjectileMs = [double]$projectileA[0].ttlMs - [double]$projectileB[0].ttlMs
    if ($elapsedProjectileMs -lt 40) {
      throw "Freeze projectile smoke sample interval was too short: elapsedMs=$elapsedProjectileMs"
    }
    $actualProjectileDelta = [math]::Sqrt([math]::Pow(([double]$projectileB[0].position.x - [double]$projectileA[0].position.x), 2) + [math]::Pow(([double]$projectileB[0].position.y - [double]$projectileA[0].position.y), 2))
    $normalProjectileDelta = 1400 * ($elapsedProjectileMs / 1000)
    if ($actualProjectileDelta -ge ($normalProjectileDelta * 0.85)) {
      throw "Freeze projectile smoke expected displacement substantially below normal speed: actual=$actualProjectileDelta, normal=$normalProjectileDelta, elapsedMs=$elapsedProjectileMs"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $join.playerId
      ticketId = $join.ticketId
      clientTick = 3
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 0; y = -1 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      castBlink = $false
      castFreeze = $false
      switchWeaponDirection = 0
    } | Out-Null

    Start-Sleep -Milliseconds 10500
    $afterFreezeExpiry = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $afterFreezeExpiryFields = Test-ArrayEnvelope $afterFreezeExpiry "slowFields"
    if ($afterFreezeExpiryFields.Count -ne 0) {
      throw "Freeze projectile smoke expected slowFields to be removed after duration expiry, got $($afterFreezeExpiryFields.Count)."
    }

    "battleId=$battleId; projectile slow delta=$actualProjectileDelta vs normal=$normalProjectileDelta; slowFields expired"
  } finally {
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $join -and (Test-HasField $join "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $join.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/commands authoritative ownership" {
  $joinOne = $null
  $joinTwo = $null
  $extraJoins = @()
  $handleOne = New-BattleSmokeHandle "owna"
  $handleTwo = New-BattleSmokeHandle "ownb"

  try {
    $joinOne = Join-AuthenticatedBattleQueue -Handle $handleOne -Rating "1230" -Skin "blue" -QueueRequestId "contract-$handleOne"
    $missingJoinOne = Test-Fields $joinOne @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoinOne.Count -gt 0) {
      throw "Ownership smoke first queue join missing fields: $($missingJoinOne -join ', ')"
    }

    $joinTwo = Join-AuthenticatedBattleQueue -Handle $handleTwo -Rating "1220" -Skin "red" -QueueRequestId "contract-$handleTwo"
    $missingJoinTwo = Test-Fields $joinTwo @("ticketId", "playerId", "roomId", "startsAt")
    if ($missingJoinTwo.Count -gt 0) {
      throw "Ownership smoke second queue join missing fields: $($missingJoinTwo -join ', ')"
    }
    if ($joinTwo.roomId -ne $joinOne.roomId) {
      throw "Ownership smoke players did not join the same room: first=$($joinOne.roomId), second=$($joinTwo.roomId)"
    }
    $startsAt = [Math]::Max([Int64]$joinOne.startsAt, [Int64]$joinTwo.startsAt)
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $waitMs = [Math]::Max(0, $startsAt - $nowMs + 250)
    if ($waitMs -gt 0) {
      Start-Sleep -Milliseconds ([Int32][Math]::Min($waitMs, 15000))
    }

    $statusOne = $null
    $statusTwo = $null
    for ($i = 0; $i -lt 40; $i++) {
      $statusOne = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($joinOne.ticketId))"
      $statusTwo = Invoke-ContractJson "GET" "/battle/queue/status?ticketId=$([uri]::EscapeDataString($joinTwo.ticketId))"
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

    if ($null -eq $statusOne.battleSession -or $null -eq $statusTwo.battleSession) {
      throw "Ownership smoke battle session was not created for both tickets."
    }
    if ($statusOne.phase -ne "active" -or $statusTwo.phase -ne "active") {
      throw "Ownership smoke expected active statuses, got first=$($statusOne.phase), second=$($statusTwo.phase)"
    }
    if ($statusOne.battleSession.battleId -ne $statusTwo.battleSession.battleId) {
      throw "Ownership smoke statuses resolved different battles: first=$($statusOne.battleSession.battleId), second=$($statusTwo.battleSession.battleId)"
    }

    $battleId = $statusOne.battleSession.battleId
    $state = Invoke-ContractJson "GET" "/battle/state/$([uri]::EscapeDataString($battleId))"
    $players = Test-ArrayEnvelope $state "players"
    $playerOne = @($players | Where-Object { $_.playerId -ceq $joinOne.playerId } | Select-Object -First 1)
    $playerTwo = @($players | Where-Object { $_.playerId -ceq $joinTwo.playerId } | Select-Object -First 1)
    if ($playerOne.Count -lt 1 -or $playerTwo.Count -lt 1) {
      throw "Ownership smoke battle state did not include both joined players."
    }
    $missingPlayerOne = Test-Fields $playerOne[0] @("playerId", "isBot", "seat")
    $missingPlayerTwo = Test-Fields $playerTwo[0] @("playerId", "isBot", "seat")
    if ($missingPlayerOne.Count -gt 0 -or $missingPlayerTwo.Count -gt 0) {
      throw "Ownership smoke player fields missing: first=$($missingPlayerOne -join ', '), second=$($missingPlayerTwo -join ', ')"
    }
    if ($playerOne[0].isBot -ne $false -or $playerTwo[0].isBot -ne $false) {
      throw "Ownership smoke expected both joined players to be real players: firstIsBot=$($playerOne[0].isBot), secondIsBot=$($playerTwo[0].isBot)"
    }
    if ($playerOne[0].seat -eq $playerTwo[0].seat) {
      throw "Ownership smoke expected distinct seats, got seat=$($playerOne[0].seat)"
    }

    Invoke-ContractJson "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $joinOne.playerId
      ticketId = $joinOne.ticketId
      clientTick = 1
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 0
    } | Out-Null

    $wrongOwner = Invoke-ContractJsonExpectError "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $joinTwo.playerId
      ticketId = $joinOne.ticketId
      clientTick = 2
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 0
    }
    if ($wrongOwner.StatusCode -ne 403) {
      throw "Ownership smoke expected wrong-owner command to return HTTP 403, got $($wrongOwner.StatusCode)."
    }
    if ($null -eq $wrongOwner.Payload -or $wrongOwner.Payload.error -ne "command_not_authorized") {
      $errorValue = if ($null -ne $wrongOwner.Payload) { $wrongOwner.Payload.error } else { "<no payload>" }
      throw "Ownership smoke expected command_not_authorized error, got $errorValue."
    }

    $missingTicket = Invoke-ContractJsonExpectError "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $joinTwo.playerId
      clientTick = 3
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
      castDash = $false
      switchWeaponDirection = 0
    }
    if ($missingTicket.StatusCode -ne 403) {
      throw "Ownership smoke expected missing-ticket command to return HTTP 403, got $($missingTicket.StatusCode)."
    }
    if ($null -eq $missingTicket.Payload -or $missingTicket.Payload.error -ne "command_not_authorized") {
      $errorValue = if ($null -ne $missingTicket.Payload) { $missingTicket.Payload.error } else { "<no payload>" }
      throw "Ownership smoke expected missing-ticket command_not_authorized error, got $errorValue."
    }

    $missingPrimaryHeld = Invoke-ContractJsonExpectError "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $joinOne.playerId
      ticketId = $joinOne.ticketId
      clientTick = 4
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      reloadPressed = $false
      switchWeaponDirection = 0
    }
    if ($missingPrimaryHeld.StatusCode -ne 400 -or $null -eq $missingPrimaryHeld.Payload -or $missingPrimaryHeld.Payload.error -ne "missing_primary_held") {
      $errorValue = if ($null -ne $missingPrimaryHeld.Payload) { $missingPrimaryHeld.Payload.error } else { "<no payload>" }
      throw "Ownership smoke expected missing_primary_held 400, got status=$($missingPrimaryHeld.StatusCode), error=$errorValue."
    }

    $missingReloadPressed = Invoke-ContractJsonExpectError "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $joinOne.playerId
      ticketId = $joinOne.ticketId
      clientTick = 5
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      switchWeaponDirection = 0
    }
    if ($missingReloadPressed.StatusCode -ne 400 -or $null -eq $missingReloadPressed.Payload -or $missingReloadPressed.Payload.error -ne "missing_reload_pressed") {
      $errorValue = if ($null -ne $missingReloadPressed.Payload) { $missingReloadPressed.Payload.error } else { "<no payload>" }
      throw "Ownership smoke expected missing_reload_pressed 400, got status=$($missingReloadPressed.StatusCode), error=$errorValue."
    }

    $missingSwitchDirection = Invoke-ContractJsonExpectError "POST" "/battle/commands" @{
      battleId = $battleId
      playerId = $joinOne.playerId
      ticketId = $joinOne.ticketId
      clientTick = 6
      movement = @{ x = 0; y = 0 }
      aim = @{ x = 1; y = 0 }
      primaryHeld = $false
      reloadPressed = $false
    }
    if ($missingSwitchDirection.StatusCode -ne 400 -or $null -eq $missingSwitchDirection.Payload -or $missingSwitchDirection.Payload.error -ne "missing_switch_weapon_direction") {
      $errorValue = if ($null -ne $missingSwitchDirection.Payload) { $missingSwitchDirection.Payload.error } else { "<no payload>" }
      throw "Ownership smoke expected missing_switch_weapon_direction 400, got status=$($missingSwitchDirection.StatusCode), error=$errorValue."
    }

    "battleId=$battleId; roomId=$($joinOne.roomId); players=$($joinOne.playerId),$($joinTwo.playerId); wrong-owner/missing-ticket and required-field commands rejected"
  } finally {
    foreach ($extraJoin in $extraJoins) {
      if ($null -ne $extraJoin -and (Test-HasField $extraJoin "ticketId")) {
        try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $extraJoin.ticketId } | Out-Null } catch {}
      }
    }
    if ($null -ne $joinTwo -and (Test-HasField $joinTwo "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $joinTwo.ticketId } | Out-Null } catch {}
    }
    if ($null -ne $joinOne -and (Test-HasField $joinOne "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $joinOne.ticketId } | Out-Null } catch {}
    }
  }
}

Test-Endpoint "POST /battle/queue/join + room snapshot" {
  $join = $null
  $secondJoin = $null
  $primaryHandle = New-BattleSmokeHandle "rooma"
  $secondHandle = New-BattleSmokeHandle "roomb"

  try {
    $join = Join-AuthenticatedBattleQueue -Handle $primaryHandle -Rating "1200" -Skin "blue" -QueueRequestId "contract-$primaryHandle"
    $missingJoin = Test-Fields $join @("ticketId", "roomId", "createdAt", "startsAt", "deadline", "participants", "capacity", "durationMs")
    if ($missingJoin.Count -gt 0) {
      throw "Queue join missing fields: $($missingJoin -join ', ')"
    }
    $legacyJoinPresent = Test-NoFields $join @("matchId", "players", "queuedHandles")
    if ($legacyJoinPresent.Count -gt 0) {
      throw "Queue join contains frontend/local alias fields: $($legacyJoinPresent -join ', ')"
    }
    if ($join.participants.Count -gt 0) {
      $missingJoinParticipant = Test-Fields $join.participants[0] @("playerId", "handle", "joinedAt", "lastSeen")
      if ($missingJoinParticipant.Count -gt 0) {
        throw "Queue participant missing fields: $($missingJoinParticipant -join ', ')"
      }
      $legacyJoinParticipantPresent = Test-NoFields $join.participants[0] @("playerName")
      if ($legacyJoinParticipantPresent.Count -gt 0) {
        throw "Queue participant contains legacy fields: $($legacyJoinParticipantPresent -join ', ')"
      }
    }

    $secondJoin = Join-AuthenticatedBattleQueue -Handle $secondHandle -Rating "1190" -Skin "red" -QueueRequestId "contract-$secondHandle"
    $missingSecondJoin = Test-Fields $secondJoin @("ticketId", "roomId", "createdAt", "startsAt", "deadline", "participants", "capacity", "durationMs")
    if ($missingSecondJoin.Count -gt 0) {
      throw "Second queue join missing fields: $($missingSecondJoin -join ', ')"
    }
    if ($secondJoin.roomId -ne $join.roomId) {
      throw "Two consecutive queue joins did not land in the same room: first=$($join.roomId), second=$($secondJoin.roomId)"
    }
    if ($secondJoin.participants.Count -lt 2) {
      throw "Second queue join snapshot did not include at least two participants."
    }
    $participantHandles = @($secondJoin.participants | ForEach-Object { $_.handle })
    if (-not ($participantHandles -contains $primaryHandle) -or -not ($participantHandles -contains $secondHandle)) {
      throw "Second queue join snapshot did not include both smoke handles: $($participantHandles -join ', ')"
    }

    $heartbeat = Invoke-ContractJson "POST" "/battle/rooms/heartbeat" @{
      roomId = $join.roomId
      ticketId = $join.ticketId
      handle = $primaryHandle
    }
    $missingHeartbeat = Test-Fields $heartbeat @("roomId", "serverTime", "participants", "capacity", "phase")
    if ($missingHeartbeat.Count -gt 0) {
      throw "Heartbeat snapshot missing fields: $($missingHeartbeat -join ', ')"
    }
    $legacyHeartbeatPresent = Test-NoFields $heartbeat @("matchId", "players", "queuedHandles")
    if ($legacyHeartbeatPresent.Count -gt 0) {
      throw "Heartbeat snapshot contains frontend/local alias fields: $($legacyHeartbeatPresent -join ', ')"
    }
    if ($heartbeat.participants.Count -gt 0) {
      $missingHeartbeatParticipant = Test-Fields $heartbeat.participants[0] @("playerId", "handle", "joinedAt", "lastSeen")
      if ($missingHeartbeatParticipant.Count -gt 0) {
        throw "Heartbeat participant missing fields: $($missingHeartbeatParticipant -join ', ')"
      }
      $legacyHeartbeatParticipantPresent = Test-NoFields $heartbeat.participants[0] @("playerName")
      if ($legacyHeartbeatParticipantPresent.Count -gt 0) {
        throw "Heartbeat participant contains legacy fields: $($legacyHeartbeatParticipantPresent -join ', ')"
      }
    }

    $snapshot = Invoke-ContractJson "GET" "/battle/rooms/snapshot?roomId=$([uri]::EscapeDataString($join.roomId))"
    $missingSnapshot = Test-Fields $snapshot @("roomId", "serverTime", "participants", "capacity", "phase")
    if ($missingSnapshot.Count -gt 0) {
      throw "Room snapshot missing fields: $($missingSnapshot -join ', ')"
    }
    $legacySnapshotPresent = Test-NoFields $snapshot @("matchId", "players", "queuedHandles")
    if ($legacySnapshotPresent.Count -gt 0) {
      throw "Room snapshot contains frontend/local alias fields: $($legacySnapshotPresent -join ', ')"
    }
    if ($snapshot.participants.Count -gt 0) {
      $missingSnapshotParticipant = Test-Fields $snapshot.participants[0] @("playerId", "handle", "joinedAt", "lastSeen")
      if ($missingSnapshotParticipant.Count -gt 0) {
        throw "Room snapshot participant missing fields: $($missingSnapshotParticipant -join ', ')"
      }
      $legacySnapshotParticipantPresent = Test-NoFields $snapshot.participants[0] @("playerName")
      if ($legacySnapshotParticipantPresent.Count -gt 0) {
        throw "Room snapshot participant contains legacy fields: $($legacySnapshotParticipantPresent -join ', ')"
      }
    }

    $missingSnapshotRoomId = Invoke-ContractJsonExpectError "GET" "/battle/rooms/snapshot"
    if ($missingSnapshotRoomId.StatusCode -ne 400 -or $missingSnapshotRoomId.Payload.code -ne "invalid_room_id") {
      throw "Missing room snapshot id expected 400 invalid_room_id, got status=$($missingSnapshotRoomId.StatusCode), code=$($missingSnapshotRoomId.Payload.code)"
    }

    $missingHeartbeatRoomId = Invoke-ContractJsonExpectError "POST" "/battle/rooms/heartbeat" @{}
    if ($missingHeartbeatRoomId.StatusCode -ne 400 -or $missingHeartbeatRoomId.Payload.code -ne "invalid_room_id") {
      throw "Missing room heartbeat id expected 400 invalid_room_id, got status=$($missingHeartbeatRoomId.StatusCode), code=$($missingHeartbeatRoomId.Payload.code)"
    }

    $missingRoomSnapshot = Invoke-ContractJsonExpectError "GET" "/battle/rooms/snapshot?roomId=contract-missing-room"
    if ($missingRoomSnapshot.StatusCode -ne 404 -or $missingRoomSnapshot.Payload.code -ne "room_not_found") {
      throw "Unknown room snapshot expected 404 room_not_found, got status=$($missingRoomSnapshot.StatusCode), code=$($missingRoomSnapshot.Payload.code)"
    }

    "roomId=$($snapshot.roomId); participants=$($snapshot.participants.Count); same-room handles=$primaryHandle,$secondHandle; phase=$($snapshot.phase); missing-id errors verified"
  } finally {
    if ($null -ne $secondJoin -and (Test-HasField $secondJoin "ticketId")) {
      try { Invoke-ContractJson "POST" "/battle/queue/leave" @{ ticketId = $secondJoin.ticketId } | Out-Null } catch {}
    }
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
  Write-Host "API contract field smoke failed: $($failures.Count) failure(s)."
  exit 1
}

Write-Host ""
Write-Host "API contract field smoke passed."
