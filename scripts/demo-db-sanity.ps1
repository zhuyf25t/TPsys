[CmdletBinding()]
param(
  [string]$BaseUrl = "http://127.0.0.1:5173/api",
  [string]$Owner = "admin",
  [int]$SampleSize = 5
)

$ErrorActionPreference = "Stop"

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Older PowerShell hosts may not allow changing output encoding.
}

function Normalize-BaseUrl {
  param([string]$Value)

  $trimmed = ""
  if ($null -ne $Value) {
    $trimmed = $Value.Trim()
  }

  if ([string]::IsNullOrWhiteSpace($trimmed)) {
    throw "BaseUrl is empty. Example: -BaseUrl http://127.0.0.1:5173/api"
  }

  return $trimmed.TrimEnd("/")
}

function Normalize-Owner {
  param([string]$Value)

  $trimmed = ""
  if ($null -ne $Value) {
    $trimmed = $Value.Trim()
  }

  if ([string]::IsNullOrWhiteSpace($trimmed)) {
    throw "Owner is empty. Example: -Owner admin"
  }

  return $trimmed
}

function Join-DemoUrl {
  param([string]$Path)

  $normalizedPath = $Path
  if (-not $normalizedPath.StartsWith("/")) {
    $normalizedPath = "/$normalizedPath"
  }

  return "$script:ApiBase$normalizedPath"
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

    $reader = New-Object System.IO.StreamReader($stream)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  } catch {
    return ""
  }
}

function Invoke-DemoApi {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Method = "GET"
  )

  $uri = Join-DemoUrl -Path $Path
  $parameters = @{
    Uri = $uri
    Method = $Method
    TimeoutSec = 10
    ErrorAction = "Stop"
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

    throw "$Method $uri failed.$status$bodyText"
  }
}

function Find-ObjectProperty {
  param(
    $Object,
    [string]$Name
  )

  if ($null -eq $Object -or $null -eq $Object.PSObject) {
    return $null
  }

  foreach ($property in $Object.PSObject.Properties) {
    if ($property.Name -ceq $Name) {
      return $property
    }
  }

  return $null
}

function Require-FieldValue {
  param(
    $Object,
    [string]$Field,
    [string]$Context
  )

  $property = Find-ObjectProperty -Object $Object -Name $Field
  if ($null -eq $property) {
    throw "$Context is missing required field '$Field'."
  }

  return $property.Value
}

function Require-TextField {
  param(
    $Object,
    [string]$Field,
    [string]$Context
  )

  $value = Require-FieldValue -Object $Object -Field $Field -Context $Context
  $text = [string]$value
  if ([string]::IsNullOrWhiteSpace($text)) {
    throw "$Context field '$Field' is empty."
  }

  return $text.Trim()
}

function Require-NumberField {
  param(
    $Object,
    [string]$Field,
    [string]$Context
  )

  $value = Require-FieldValue -Object $Object -Field $Field -Context $Context
  $number = 0.0
  $text = [string]$value
  if (-not [double]::TryParse($text, [System.Globalization.NumberStyles]::Any, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$number)) {
    throw "$Context field '$Field' is not numeric."
  }

  return $number
}

function Require-BooleanField {
  param(
    $Object,
    [string]$Field,
    [string]$Context
  )

  $value = Require-FieldValue -Object $Object -Field $Field -Context $Context
  if ($value -is [bool]) {
    return $value
  }

  $boolean = $false
  $text = [string]$value
  if (-not [bool]::TryParse($text, [ref]$boolean)) {
    throw "$Context field '$Field' is not boolean."
  }

  return $boolean
}

function Require-ArrayField {
  param(
    $Payload,
    [string]$Field,
    [string]$Endpoint
  )

  $property = Find-ObjectProperty -Object $Payload -Name $Field
  if ($null -eq $property) {
    throw "$Endpoint response is missing required array field '$Field'."
  }

  if ($null -eq $property.Value) {
    throw "$Endpoint field '$Field' is null."
  }

  if ($property.Value -isnot [System.Array]) {
    throw "$Endpoint field '$Field' is not an array."
  }

  return @($property.Value)
}

function Join-Values {
  param(
    $Values,
    [string]$EmptyLabel = "empty"
  )

  $items = @($Values | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
  if ($items.Count -eq 0) {
    return $EmptyLabel
  }

  return ($items -join ", ")
}

function Write-Step {
  param([string]$Message)
  Write-Host ""
  Write-Host "== $Message ==" -ForegroundColor Cyan
}

function Write-Pass {
  param([string]$Message)
  Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Fail {
  param([string]$Message)
  Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Run-Check {
  param(
    [string]$Name,
    [scriptblock]$Action
  )

  Write-Step -Message $Name
  try {
    & $Action
  } catch {
    $message = $_.Exception.Message
    $script:Failures += "${Name}: $message"
    Write-Fail $message
  }
}

function Get-RecordHandle {
  param($Record)
  return Require-TextField -Object $Record -Field "handle" -Context "account"
}

function Build-RatingBoard {
  param(
    $Accounts,
    $Results
  )

  $byHandle = @{}

  foreach ($record in @($Results)) {
    $handle = Require-TextField -Object $record -Field "handle" -Context "battle result"
    $key = $handle.Trim().ToLowerInvariant()
    $rating = Require-NumberField -Object $record -Field "ratingAfter" -Context "battle result"
    $seenAt = Require-NumberField -Object $record -Field "finishedAt" -Context "battle result"

    if (-not $byHandle.ContainsKey($key)) {
      $byHandle[$key] = [pscustomobject]@{
        handle = $handle
        rating = $rating
        seenAt = $seenAt
      }
      continue
    }

    $current = $byHandle[$key]
    if ($seenAt -gt $current.seenAt -or ($seenAt -eq $current.seenAt -and $rating -gt $current.rating)) {
      $byHandle[$key] = [pscustomobject]@{
        handle = $handle
        rating = $rating
        seenAt = $seenAt
      }
    }
  }

  foreach ($account in @($Accounts)) {
    $handle = Require-TextField -Object $account -Field "handle" -Context "account"
    $key = $handle.Trim().ToLowerInvariant()
    if (-not $byHandle.ContainsKey($key)) {
      $byHandle[$key] = [pscustomobject]@{
        handle = $handle
        rating = 1200
        seenAt = 0
      }
    }
  }

  return @($byHandle.Values | Sort-Object -Property @{ Expression = { $_.rating }; Descending = $true }, @{ Expression = { $_.handle }; Ascending = $true })
}

function Build-ContributionBoard {
  param(
    $Accounts,
    $Results,
    $Adjustments
  )

  $entries = @{}

  foreach ($account in @($Accounts)) {
    $handle = Require-TextField -Object $account -Field "handle" -Context "account"
    $key = $handle.Trim().ToLowerInvariant()
    if (-not $entries.ContainsKey($key)) {
      $entries[$key] = [pscustomobject]@{
        handle = $handle
        battleCount = 0
        adjustmentTotal = 0
        score = 0
      }
    }
  }

  foreach ($record in @($Results)) {
    $handle = Require-TextField -Object $record -Field "handle" -Context "battle result"
    $key = $handle.Trim().ToLowerInvariant()
    if (-not $entries.ContainsKey($key)) {
      $entries[$key] = [pscustomobject]@{
        handle = $handle
        battleCount = 0
        adjustmentTotal = 0
        score = 0
      }
    }

    $entries[$key].battleCount += 1
  }

  foreach ($adjustment in @($Adjustments)) {
    $handle = Require-TextField -Object $adjustment -Field "targetHandle" -Context "contribution adjustment"
    $key = $handle.Trim().ToLowerInvariant()
    if (-not $entries.ContainsKey($key)) {
      $entries[$key] = [pscustomobject]@{
        handle = $handle
        battleCount = 0
        adjustmentTotal = 0
        score = 0
      }
    }

    $entries[$key].adjustmentTotal += [int](Require-NumberField -Object $adjustment -Field "delta" -Context "contribution adjustment")
  }

  foreach ($entry in $entries.Values) {
    $entry.score = [Math]::Max(0, $entry.battleCount + $entry.adjustmentTotal)
  }

  return @($entries.Values | Sort-Object -Property @{ Expression = { $_.score }; Descending = $true }, @{ Expression = { $_.handle }; Ascending = $true })
}

function Format-RatingSamples {
  param($Entries)

  $samples = @($Entries | Select-Object -First $script:SampleLimit | ForEach-Object {
    "$($_.handle)/$([int][Math]::Round($_.rating))"
  })

  return Join-Values -Values $samples
}

function Format-ContributionSamples {
  param($Entries)

  $samples = @($Entries | Select-Object -First $script:SampleLimit | ForEach-Object {
    "$($_.handle)/score=$($_.score)"
  })

  return Join-Values -Values $samples
}

function Format-AdjustmentSamples {
  param($Adjustments)

  $samples = @($Adjustments | Select-Object -First $script:SampleLimit | ForEach-Object {
    $target = Require-TextField -Object $_ -Field "targetHandle" -Context "contribution adjustment"
    $delta = [int](Require-NumberField -Object $_ -Field "delta" -Context "contribution adjustment")
    $actor = Require-TextField -Object $_ -Field "actorHandle" -Context "contribution adjustment"
    "$target/delta=$delta/actor=$actor"
  })

  return Join-Values -Values $samples
}

function Format-MailSamples {
  param($Mails)

  $samples = @($Mails | Select-Object -First $script:SampleLimit | ForEach-Object {
    $id = Require-TextField -Object $_ -Field "id" -Context "mail"
    $kind = Require-TextField -Object $_ -Field "kind" -Context "mail"
    $unread = Require-BooleanField -Object $_ -Field "unread" -Context "mail"
    $subject = Require-TextField -Object $_ -Field "subject" -Context "mail"
    "$id/$kind/unread=$unread/subject=$subject"
  })

  return Join-Values -Values $samples
}

$script:ApiBase = Normalize-BaseUrl -Value $BaseUrl
$script:OwnerHandle = Normalize-Owner -Value $Owner
$script:SampleLimit = [Math]::Max(1, $SampleSize)
$script:Failures = @()
$script:Accounts = @()
$script:BattleResults = @()
$script:Adjustments = @()

Write-Host "Demo DB sanity base URL: $script:ApiBase"
Write-Host "Mailbox owner: $script:OwnerHandle"

Run-Check -Name "health" -Action {
  $health = Invoke-DemoApi -Path "/health"
  $status = Require-TextField -Object $health -Field "status" -Context "/health"
  if ($status -ne "ok") {
    throw "/health returned status=$status"
  }

  $service = Require-TextField -Object $health -Field "service" -Context "/health"
  $port = [int](Require-NumberField -Object $health -Field "port" -Context "/health")
  $storageMode = Require-TextField -Object $health -Field "storageMode" -Context "/health"
  if ($storageMode -ne "postgres") {
    throw "/health returned storageMode=$storageMode; expected postgres. Set SLAY_DEMO_STORAGE_MODE=postgres before starting the backend."
  }

  Write-Pass "/health readable service=$service port=$port status=$status storageMode=$storageMode"
}

Run-Check -Name "identity accounts" -Action {
  $payload = Invoke-DemoApi -Path "/identity/accounts"
  $script:Accounts = @(Require-ArrayField -Payload $payload -Field "accounts" -Endpoint "/identity/accounts")
  $handles = @($script:Accounts | Select-Object -First $script:SampleLimit | ForEach-Object {
    Get-RecordHandle -Record $_
  })
  Write-Pass "/identity/accounts readable count=$($script:Accounts.Count) handles=$(Join-Values -Values $handles)"
}

Run-Check -Name "rating source" -Action {
  $payload = Invoke-DemoApi -Path "/battle/results?limit=200"
  $script:BattleResults = @(Require-ArrayField -Payload $payload -Field "results" -Endpoint "/battle/results")
  $ratingBoard = @(Build-RatingBoard -Accounts $script:Accounts -Results $script:BattleResults)
  Write-Pass "rating readable via /battle/results?limit=200 + /identity/accounts; sourceResults=$($script:BattleResults.Count) boardEntries=$($ratingBoard.Count) top=$(Format-RatingSamples -Entries $ratingBoard)"
}

Run-Check -Name "contribution adjustments" -Action {
  $payload = Invoke-DemoApi -Path "/governance/contribution-adjustments?limit=50"
  $script:Adjustments = @(Require-ArrayField -Payload $payload -Field "adjustments" -Endpoint "/governance/contribution-adjustments")
  $contributionBoard = @(Build-ContributionBoard -Accounts $script:Accounts -Results $script:BattleResults -Adjustments $script:Adjustments)
  if ($script:Adjustments.Count -eq 0) {
    Write-Pass "/governance/contribution-adjustments readable count=0 entries=empty; contributionBoard=$(Format-ContributionSamples -Entries $contributionBoard)"
  } else {
    Write-Pass "/governance/contribution-adjustments readable count=$($script:Adjustments.Count) entries=$(Format-AdjustmentSamples -Adjustments $script:Adjustments); contributionBoard=$(Format-ContributionSamples -Entries $contributionBoard)"
  }
}

Run-Check -Name "mails" -Action {
  $encodedOwner = [System.Uri]::EscapeDataString($script:OwnerHandle)
  $payload = Invoke-DemoApi -Path "/mails?ownerHandle=$encodedOwner"
  $mails = @(Require-ArrayField -Payload $payload -Field "mails" -Endpoint "/mails")
  Write-Pass "/mails readable ownerHandle=$script:OwnerHandle count=$($mails.Count) entries=$(Format-MailSamples -Mails $mails)"
}

Write-Host ""
if ($script:Failures.Count -gt 0) {
  Write-Host "FAIL: demo DB sanity found $($script:Failures.Count) critical issue(s)." -ForegroundColor Red
  foreach ($failure in $script:Failures) {
    Write-Host " - $failure" -ForegroundColor Red
  }
  Write-Host "Operator conclusion: if a friend's computer and this host show different data, confirm the friend is using the host Vite Network URL and the frontend .env.local uses /api."
  exit 1
}

Write-Host "PASS: demo DB sanity APIs are readable through $script:ApiBase." -ForegroundColor Green
Write-Host "Operator conclusion: if a friend's computer and this host show different data, confirm the friend is using the host Vite Network URL and the frontend .env.local uses /api."
