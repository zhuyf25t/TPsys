[CmdletBinding()]
param(
  [string]$BaseUrl = "http://127.0.0.1:5173/api"
)

$ErrorActionPreference = "Stop"

if (-not $PSBoundParameters.ContainsKey("BaseUrl") -and -not [string]::IsNullOrWhiteSpace($env:SLAY_DEMO_API_BASE)) {
  $BaseUrl = $env:SLAY_DEMO_API_BASE
}

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Older PowerShell hosts may not allow changing output encoding; smoke can still run.
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
    [string]$Method = "GET",
    $Body = $null
  )

  $uri = Join-DemoUrl -Path $Path
  $parameters = @{
    Uri = $uri
    Method = $Method
    TimeoutSec = 10
    ErrorAction = "Stop"
  }

  if ($null -ne $Body) {
    $parameters["ContentType"] = "application/json; charset=utf-8"
    $parameters["Body"] = ($Body | ConvertTo-Json -Depth 8 -Compress)
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

function Assert-Condition {
  param(
    [bool]$Condition,
    [string]$Message
  )

  if (-not $Condition) {
    throw $Message
  }
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

function New-SmokeHandle {
  param([string]$Prefix)

  $stamp = Get-Date -Format "HHmmss"
  $random = Get-Random -Minimum 10 -Maximum 99
  return "$Prefix$stamp$random"
}

function Register-SmokeAccount {
  param([string]$Handle)

  $response = Invoke-DemoApi -Method "POST" -Path "/identity/register" -Body @{
    handle = $Handle
    password = "secret"
    skinId = "blue"
  }

  Assert-Condition ($response.handle -eq $Handle) "Register returned unexpected handle: expected=$Handle actual=$($response.handle)"
  Assert-Condition (-not [string]::IsNullOrWhiteSpace([string]$response.session)) "Register did not return a session for $Handle."

  return $response
}

function Get-ArrayCount {
  param($Value)
  if ($null -eq $Value) {
    return 0
  }

  return @($Value).Count
}

function Test-HasProperty {
  param(
    $Object,
    [string]$Name
  )

  return $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
}

function Get-JsonArray {
  param(
    $Object,
    [string]$Name
  )

  if (-not (Test-HasProperty -Object $Object -Name $Name)) {
    return @()
  }

  $value = $Object.PSObject.Properties[$Name].Value
  if ($null -eq $value) {
    return @()
  }

  return @($value)
}

function Find-FriendRequestMail {
  param(
    $Mails,
    [string]$RequestId
  )

  $expectedMailId = "mail-friend-$RequestId"
  return @($Mails) | Where-Object {
    $_.id -eq $expectedMailId -or $_.friendRequestId -eq $RequestId
  } | Select-Object -First 1
}

function Test-FriendRequestFlow {
  param(
    [string]$SourceHandle,
    [string]$TargetHandle,
    [ValidateSet("accepted", "rejected")][string]$Decision
  )

  $createResponse = Invoke-DemoApi -Method "POST" -Path "/social/friend-requests" -Body @{
    sourceHandle = $SourceHandle
    targetHandle = $TargetHandle
  }

  $request = $createResponse.request
  Assert-Condition ($null -ne $request) "Friend request did not return request: $SourceHandle -> $TargetHandle"
  Assert-Condition (-not [string]::IsNullOrWhiteSpace([string]$request.id)) "Friend request did not return request.id."
  Assert-Condition ($request.status -eq "pending") "New friend request status is not pending: $($request.status)"

  $mailbox = Invoke-DemoApi -Path "/mails?ownerHandle=$TargetHandle"
  Assert-Condition (Test-HasProperty -Object $mailbox -Name "mails") "Mailbox response is missing mails: owner=$TargetHandle"

  $mail = Find-FriendRequestMail -Mails (Get-JsonArray -Object $mailbox -Name "mails") -RequestId $request.id
  Assert-Condition ($null -ne $mail) "Target mailbox does not contain friend request mail: owner=$TargetHandle request=$($request.id)"
  Assert-Condition ($mail.unread -eq $true) "Friend request mail should start unread: mail=$($mail.id)"

  $readResponse = Invoke-DemoApi -Method "POST" -Path "/mails/read" -Body @{
    ownerHandle = $TargetHandle
    mailId = $mail.id
  }
  Assert-Condition ($readResponse.ok -eq $true) "Failed to mark mail as read: mail=$($mail.id)"

  $mailboxRead = Invoke-DemoApi -Path "/mails?ownerHandle=$TargetHandle"
  $mailRead = Find-FriendRequestMail -Mails (Get-JsonArray -Object $mailboxRead -Name "mails") -RequestId $request.id
  Assert-Condition ($null -ne $mailRead) "Friend request mail missing after read mark: request=$($request.id)"
  Assert-Condition ($mailRead.unread -eq $false) "Friend request mail should be read immediately after /mails/read: mail=$($mailRead.id)"

  $repeatReadResponse = Invoke-DemoApi -Method "POST" -Path "/mails/read" -Body @{
    ownerHandle = $TargetHandle
    mailId = $mail.id
  }
  Assert-Condition ($repeatReadResponse.ok -eq $true) "Repeated mark read should be idempotent: mail=$($mail.id)"

  $respondResponse = Invoke-DemoApi -Method "POST" -Path "/social/friend-requests/respond" -Body @{
    requestId = $request.id
    actorHandle = $TargetHandle
    decision = $Decision
  }

  Assert-Condition ($respondResponse.request.status -eq $Decision) "Friend response status mismatch: expected=$Decision actual=$($respondResponse.request.status)"

  $mailboxAfter = Invoke-DemoApi -Path "/mails?ownerHandle=$TargetHandle"
  $mailAfter = Find-FriendRequestMail -Mails (Get-JsonArray -Object $mailboxAfter -Name "mails") -RequestId $request.id
  Assert-Condition ($null -ne $mailAfter) "Original friend request mail missing after response: request=$($request.id)"
  Assert-Condition ($mailAfter.unread -eq $false) "Original friend request mail should be read after response: mail=$($mailAfter.id)"
  Assert-Condition ($mailAfter.friendRequestStatus -eq $Decision) "Mail friend request status mismatch after response: expected=$Decision actual=$($mailAfter.friendRequestStatus)"

  return @{
    requestId = $request.id
    mailId = $mail.id
    decision = $Decision
  }
}

$script:ApiBase = Normalize-BaseUrl -Value $BaseUrl

Write-Host "Demo smoke base URL: $script:ApiBase"

Write-Step "health"
$health = Invoke-DemoApi -Path "/health"
Assert-Condition ($health.status -eq "ok") "/health did not return ok."
Write-Pass "/health ok service=$($health.service) port=$($health.port)"

Write-Step "identity accounts"
$accounts = Invoke-DemoApi -Path "/identity/accounts"
Assert-Condition (Test-HasProperty -Object $accounts -Name "accounts") "/identity/accounts response is missing accounts."
Write-Pass "accounts readable count=$(Get-ArrayCount -Value (Get-JsonArray -Object $accounts -Name 'accounts'))"

Write-Step "register smoke accounts"
$handleA = New-SmokeHandle -Prefix "smkA"
$handleB = New-SmokeHandle -Prefix "smkB"
$accountA = Register-SmokeAccount -Handle $handleA
$accountB = Register-SmokeAccount -Handle $handleB
Write-Pass "registered $($accountA.handle), $($accountB.handle)"

Write-Step "battle results"
$battleResults = Invoke-DemoApi -Method "POST" -Path "/battleresultlist" -Body @{
  userToken = $accountA.session
  limit = 1
}
Assert-Condition (Test-HasProperty -Object $battleResults -Name "results") "/battleresultlist response is missing results."
Write-Pass "battle results readable count=$(Get-ArrayCount -Value (Get-JsonArray -Object $battleResults -Name 'results'))"

Write-Step "replay catalog"
$replayCatalog = Invoke-DemoApi -Path "/replay/catalog"
Assert-Condition (Test-HasProperty -Object $replayCatalog -Name "replays") "/replay/catalog response is missing replays."
Write-Pass "replay catalog readable count=$(Get-ArrayCount -Value (Get-JsonArray -Object $replayCatalog -Name 'replays'))"

Write-Step "forum topics"
$forumTopics = Invoke-DemoApi -Path "/forum/topics"
Assert-Condition (Test-HasProperty -Object $forumTopics -Name "topics") "/forum/topics response is missing topics."
Write-Pass "forum topics readable count=$(Get-ArrayCount -Value (Get-JsonArray -Object $forumTopics -Name 'topics'))"

Write-Step "friend request accept flow"
$accepted = Test-FriendRequestFlow -SourceHandle $handleA -TargetHandle $handleB -Decision "accepted"
Write-Pass "$handleA -> $handleB accepted request=$($accepted.requestId) mail=$($accepted.mailId)"

Write-Step "friend request reject flow"
$rejected = Test-FriendRequestFlow -SourceHandle $handleB -TargetHandle $handleA -Decision "rejected"
Write-Pass "$handleB -> $handleA rejected request=$($rejected.requestId) mail=$($rejected.mailId)"

Write-Host ""
Write-Host "Demo smoke passed. All checked endpoints are reachable through the same BaseUrl." -ForegroundColor Green
