[CmdletBinding()]
param(
  [string]$OutputDir = ".\.runtime\bp44-battle-feel-suite",
  [string]$FrontendUrl = "http://127.0.0.1:5173",
  [string]$BackendUrl = "http://127.0.0.1:8080",
  [string]$Password = "bp28-pass",
  [string]$BrowserPath,
  [ValidateSet("winter", "default", "autumn", "normal")]
  [string]$ModeId = "winter",
  [int]$PreInputSettleMs = 1700,
  [int]$PlayingTimeoutSeconds = 45,
  [int]$FrameSampleSeconds = 4,
  [switch]$Headful,
  [switch]$DisableGpu,
  [switch]$SkipStraightFire
)

# BP-44A battle feel suite: wraps the existing BP-28 render-feel smoke and
# emits one compact aggregate summary without changing gameplay or renderer code.

$ErrorActionPreference = "Stop"

try {
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
  # Older Windows PowerShell hosts can still run the suite without changing output encoding.
}

function Get-PropertyValue {
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

function Convert-ToCompactVfxMetric {
  param($Metric)

  if ($null -eq $Metric) {
    return $null
  }

  return [ordered]@{
    available = Get-PropertyValue -InputObject $Metric -Name "available"
    status = Get-PropertyValue -InputObject $Metric -Name "status"
    created = Get-PropertyValue -InputObject $Metric -Name "createdDelta"
    destroyed = Get-PropertyValue -InputObject $Metric -Name "destroyedDelta"
    peak = Get-PropertyValue -InputObject $Metric -Name "peakActiveTransientCount"
  }
}

function Convert-ToCompactHudMetric {
  param($Metric)

  if ($null -eq $Metric) {
    return $null
  }

  return [ordered]@{
    available = Get-PropertyValue -InputObject $Metric -Name "available"
    status = Get-PropertyValue -InputObject $Metric -Name "status"
    render = Get-PropertyValue -InputObject $Metric -Name "minimapRenderDelta"
    staticRedraw = Get-PropertyValue -InputObject $Metric -Name "minimapStaticLayerRedrawDelta"
  }
}

function Convert-ToCompactCorrectionMetric {
  param($Metric)

  if ($null -eq $Metric) {
    return $null
  }

  return [ordered]@{
    available = Get-PropertyValue -InputObject $Metric -Name "available"
    status = Get-PropertyValue -InputObject $Metric -Name "status"
    hard = Get-PropertyValue -InputObject $Metric -Name "hardSnapDelta"
    soft = Get-PropertyValue -InputObject $Metric -Name "softCorrectionDelta"
  }
}

function Convert-ToCompactVisionMetric {
  param($Metric)

  if ($null -eq $Metric) {
    return $null
  }

  $camera = Get-PropertyValue -InputObject $Metric -Name "camera"
  $viewport = Get-PropertyValue -InputObject $Metric -Name "viewport"
  $lookAhead = Get-PropertyValue -InputObject $Metric -Name "lookAhead"
  $motion = Get-PropertyValue -InputObject $Metric -Name "localHeroScreenMotion"
  $worldView = Get-PropertyValue -InputObject $camera -Name "worldView"
  $screenPxPerWorldUnit = Get-PropertyValue -InputObject $camera -Name "screenPxPerWorldUnit"

  return [ordered]@{
    available = Get-PropertyValue -InputObject $Metric -Name "available"
    status = Get-PropertyValue -InputObject $Metric -Name "status"
    viewport = [ordered]@{
      windowWidth = Get-PropertyValue -InputObject $viewport -Name "windowInnerWidth"
      windowHeight = Get-PropertyValue -InputObject $viewport -Name "windowInnerHeight"
      devicePixelRatio = Get-PropertyValue -InputObject $viewport -Name "devicePixelRatio"
    }
    camera = [ordered]@{
      width = Get-PropertyValue -InputObject $camera -Name "width"
      height = Get-PropertyValue -InputObject $camera -Name "height"
      zoom = Get-PropertyValue -InputObject $camera -Name "zoom"
      screenPxPerWorldUnit = Get-PropertyValue -InputObject $screenPxPerWorldUnit -Name "average"
      worldViewWidth = Get-PropertyValue -InputObject $worldView -Name "width"
      worldViewHeight = Get-PropertyValue -InputObject $worldView -Name "height"
    }
    localHeroScreenMotion = [ordered]@{
      available = Get-PropertyValue -InputObject $motion -Name "available"
      sampleCount = Get-PropertyValue -InputObject $motion -Name "sampleCount"
      worldDistance = Get-PropertyValue -InputObject $motion -Name "totalWorldDistance"
      screenDistancePx = Get-PropertyValue -InputObject $motion -Name "totalScreenDistancePx"
      avgScreenPxPerSecond = Get-PropertyValue -InputObject $motion -Name "averageScreenPxPerSecond"
      maxScreenPxPerSecond = Get-PropertyValue -InputObject $motion -Name "maxScreenPxPerSecond"
      firstScreenPxPerSecond = Get-PropertyValue -InputObject $motion -Name "firstMeasuredScreenPxPerSecond"
    }
    lookAhead = [ordered]@{
      available = ($null -ne $lookAhead)
      offsetDistance = Get-PropertyValue -InputObject $lookAhead -Name "actualOffsetDistance"
      targetAheadDistance = Get-PropertyValue -InputObject $lookAhead -Name "targetAheadDistance"
    }
    gaps = @(Get-PropertyValue -InputObject $Metric -Name "gaps" -DefaultValue @())
  }
}

function Convert-ToCompactRafMetric {
  param($Raf)

  if ($null -eq $Raf) {
    return $null
  }

  $inputPhase = Get-PropertyValue -InputObject (Get-PropertyValue -InputObject $Raf -Name "byDiagnosticPhase") -Name "input"
  return [ordered]@{
    frameCount = Get-PropertyValue -InputObject $inputPhase -Name "frameCount"
    over25 = Get-PropertyValue -InputObject $inputPhase -Name "over25ms"
    over40 = Get-PropertyValue -InputObject $inputPhase -Name "over40ms"
    maxMs = Get-PropertyValue -InputObject $inputPhase -Name "maxMs"
    p95Ms = Get-PropertyValue -InputObject $inputPhase -Name "p95Ms"
  }
}

function Convert-ToCompactCommandMetric {
  param($Probe)

  if ($null -eq $Probe) {
    return $null
  }

  return [ordered]@{
    available = Get-PropertyValue -InputObject $Probe -Name "available"
    requests = Get-PropertyValue -InputObject $Probe -Name "requestCount"
    failed = Get-PropertyValue -InputObject $Probe -Name "failedCount"
    responseParseFailed = Get-PropertyValue -InputObject $Probe -Name "responseParseFailedCount"
    castBlinkTrueCount = Get-PropertyValue -InputObject $Probe -Name "castBlinkTrueCount"
    castFreezeTrueCount = Get-PropertyValue -InputObject $Probe -Name "castFreezeTrueCount"
    blinkAppliedCount = Get-PropertyValue -InputObject $Probe -Name "blinkAppliedCount"
    blinkNoopCount = Get-PropertyValue -InputObject $Probe -Name "blinkNoopCount"
    freezeAppliedCount = Get-PropertyValue -InputObject $Probe -Name "freezeAppliedCount"
    freezeNoopCount = Get-PropertyValue -InputObject $Probe -Name "freezeNoopCount"
    skillOutcomeCount = Get-PropertyValue -InputObject $Probe -Name "skillOutcomeCount"
    skillOutcomeReasons = Get-PropertyValue -InputObject $Probe -Name "skillOutcomeReasons"
    skillNoopWithoutReasonCount = Get-PropertyValue -InputObject $Probe -Name "skillNoopWithoutReasonCount"
    responseCommandStatusCounts = Get-PropertyValue -InputObject $Probe -Name "responseCommandStatusCounts"
    responseCommandReasonCounts = Get-PropertyValue -InputObject $Probe -Name "responseCommandReasonCounts"
    p95Ms = Get-PropertyValue -InputObject $Probe -Name "p95DurationMs"
    maxMs = Get-PropertyValue -InputObject $Probe -Name "maxDurationMs"
  }
}

function Convert-ToCompactHitDisputeMetric {
  param(
    $Samples,
    [AllowEmptyCollection()][object[]]$Failures = @()
  )

  $failureList = @($Failures | Where-Object { $null -ne $_ })
  return [ordered]@{
    available = Get-PropertyValue -InputObject $Samples -Name "available"
    status = Get-PropertyValue -InputObject $Samples -Name "status"
    serverTerminalCount = Get-PropertyValue -InputObject $Samples -Name "serverTerminalCount"
    clientTerminalCount = Get-PropertyValue -InputObject $Samples -Name "clientTerminalCount"
    relevantServerTerminalCount = Get-PropertyValue -InputObject $Samples -Name "relevantServerTerminalCount"
    relevantClientTerminalCount = Get-PropertyValue -InputObject $Samples -Name "relevantClientTerminalCount"
    relevantOwnerPlayerIds = @(Get-PropertyValue -InputObject $Samples -Name "relevantOwnerPlayerIds" -DefaultValue @())
    sampleCount = Get-PropertyValue -InputObject $Samples -Name "sampleCount"
    serverReasonSummary = Get-PropertyValue -InputObject $Samples -Name "serverReasonSummary"
    clientReasonSummary = Get-PropertyValue -InputObject $Samples -Name "clientReasonSummary"
    failureCount = $failureList.Count
    failures = @($failureList | Select-Object -First 5)
  }
}

function Convert-ToCompactScenarioSummary {
  param(
    [Parameter(Mandatory = $true)]$Summary,
    [Parameter(Mandatory = $true)][string]$SummaryPath,
    [Parameter(Mandatory = $true)][string]$LogPath,
    [Parameter(Mandatory = $true)][int]$ExitCode
  )

  $warnings = @(Get-PropertyValue -InputObject $Summary -Name "warnings" -DefaultValue @())
  $input = Get-PropertyValue -InputObject $Summary -Name "input"
  $inputClients = Get-PropertyValue -InputObject $input -Name "clients"
  $localFeedback = Get-PropertyValue -InputObject $Summary -Name "localFeedbackLatencyMetric"
  $vfxMetric = Get-PropertyValue -InputObject $Summary -Name "vfxMetric"
  $hudMetric = Get-PropertyValue -InputObject $Summary -Name "hudMetric"
  $localHeroCorrectionMetric = Get-PropertyValue -InputObject $Summary -Name "localHeroCorrectionMetric"
  $visionMetric = Get-PropertyValue -InputObject $Summary -Name "visionMetric"
  $raf = Get-PropertyValue -InputObject $Summary -Name "raf"
  $hitDisputeSamples = Get-PropertyValue -InputObject $Summary -Name "hitDisputeSamples"
  $hitDisputeAssertionFailures = @(Get-PropertyValue -InputObject $Summary -Name "hitDisputeAssertionFailures" -DefaultValue @())

  $commandClientA = Get-PropertyValue -InputObject $input -Name "commandFetchProbe"
  $commandClientB = $null
  if ($null -ne $inputClients) {
    $commandClientA = Get-PropertyValue -InputObject (Get-PropertyValue -InputObject $inputClients -Name "clientA") -Name "commandFetchProbe" -DefaultValue $commandClientA
    $commandClientB = Get-PropertyValue -InputObject (Get-PropertyValue -InputObject $inputClients -Name "clientB") -Name "commandFetchProbe"
  }

  return [ordered]@{
    ok = ([bool](Get-PropertyValue -InputObject $Summary -Name "ok") -and $ExitCode -eq 0)
    exitCode = $ExitCode
    scenario = Get-PropertyValue -InputObject $Summary -Name "scenario"
    inputDurationMs = Get-PropertyValue -InputObject $Summary -Name "inputDurationMs"
    sameBattle = Get-PropertyValue -InputObject $Summary -Name "sameBattle"
    warnings = [ordered]@{
      count = $warnings.Count
      list = @($warnings)
    }
    latency = [ordered]@{
      motionMs = Get-PropertyValue -InputObject $localFeedback -Name "motionLatencyMs"
      muzzleMs = Get-PropertyValue -InputObject $localFeedback -Name "muzzleLatencyMs"
      motionBasis = Get-PropertyValue -InputObject $localFeedback -Name "motionLatencyBasis"
      muzzleBasis = Get-PropertyValue -InputObject $localFeedback -Name "muzzleLatencyBasis"
      complete = Get-PropertyValue -InputObject $localFeedback -Name "complete"
    }
    commandFetch = [ordered]@{
      clientA = Convert-ToCompactCommandMetric -Probe $commandClientA
      clientB = Convert-ToCompactCommandMetric -Probe $commandClientB
    }
    hitDispute = Convert-ToCompactHitDisputeMetric -Samples $hitDisputeSamples -Failures $hitDisputeAssertionFailures
    inputSummary = [ordered]@{
      firePressCount = Get-PropertyValue -InputObject $input -Name "firePressCount"
      fireReleaseCount = Get-PropertyValue -InputObject $input -Name "fireReleaseCount"
      aimSampleCount = Get-PropertyValue -InputObject $input -Name "aimSampleCount"
      skillTapCount = Get-PropertyValue -InputObject $input -Name "skillTapCount"
      skillKeys = @(Get-PropertyValue -InputObject $input -Name "skillKeys" -DefaultValue @())
      targetedSkillTapCount = Get-PropertyValue -InputObject $input -Name "targetedSkillTapCount"
      targetedSkillKeys = @(Get-PropertyValue -InputObject $input -Name "targetedSkillKeys" -DefaultValue @())
      targetedConfirmCount = Get-PropertyValue -InputObject $input -Name "targetedConfirmCount"
    }
    rafInput = [ordered]@{
      clientA = Convert-ToCompactRafMetric -Raf (Get-PropertyValue -InputObject $raf -Name "clientA")
      clientB = Convert-ToCompactRafMetric -Raf (Get-PropertyValue -InputObject $raf -Name "clientB")
    }
    vfxMetric = [ordered]@{
      clientA = Convert-ToCompactVfxMetric -Metric (Get-PropertyValue -InputObject $vfxMetric -Name "clientA")
      clientB = Convert-ToCompactVfxMetric -Metric (Get-PropertyValue -InputObject $vfxMetric -Name "clientB")
    }
    hudMetric = [ordered]@{
      clientA = Convert-ToCompactHudMetric -Metric (Get-PropertyValue -InputObject $hudMetric -Name "clientA")
      clientB = Convert-ToCompactHudMetric -Metric (Get-PropertyValue -InputObject $hudMetric -Name "clientB")
    }
    localHeroCorrection = [ordered]@{
      clientA = Convert-ToCompactCorrectionMetric -Metric (Get-PropertyValue -InputObject $localHeroCorrectionMetric -Name "clientA")
      clientB = Convert-ToCompactCorrectionMetric -Metric (Get-PropertyValue -InputObject $localHeroCorrectionMetric -Name "clientB")
    }
    visionMetric = [ordered]@{
      clientA = Convert-ToCompactVisionMetric -Metric (Get-PropertyValue -InputObject $visionMetric -Name "clientA")
      clientB = Convert-ToCompactVisionMetric -Metric (Get-PropertyValue -InputObject $visionMetric -Name "clientB")
    }
    files = [ordered]@{
      summary = $SummaryPath
      log = $LogPath
    }
  }
}

function New-FailedScenarioSummary {
  param(
    [Parameter(Mandatory = $true)][string]$Scenario,
    [Parameter(Mandatory = $true)][int]$InputDurationMs,
    [Parameter(Mandatory = $true)][string]$SummaryPath,
    [Parameter(Mandatory = $true)][string]$LogPath,
    [Parameter(Mandatory = $true)][int]$ExitCode
  )

  $tail = @()
  if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
    $tail = @(Get-Content -LiteralPath $LogPath -Tail 24)
  }

  return [ordered]@{
    ok = $false
    exitCode = $ExitCode
    scenario = $Scenario
    inputDurationMs = $InputDurationMs
    sameBattle = $null
    warnings = [ordered]@{
      count = 0
      list = @()
    }
    error = [ordered]@{
      message = "BP-28 render-feel smoke failed or did not write a readable summary."
      logTail = $tail
    }
    files = [ordered]@{
      summary = $SummaryPath
      log = $LogPath
    }
  }
}

function Invoke-FeelSmokeScenario {
  param(
    [Parameter(Mandatory = $true)][string]$Scenario,
    [Parameter(Mandatory = $true)][int]$InputDurationMs,
    [Parameter(Mandatory = $true)][string]$SummaryPath,
    [Parameter(Mandatory = $true)][string]$LogPath
  )

  $scriptPath = Join-Path $PSScriptRoot "bp28-render-feel-smoke.ps1"
  $arguments = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $scriptPath,
    "-FrontendUrl",
    $FrontendUrl,
    "-BackendUrl",
    $BackendUrl,
    "-Password",
    $Password,
    "-PlayingTimeoutSeconds",
    "$PlayingTimeoutSeconds",
    "-FrameSampleSeconds",
    "$FrameSampleSeconds",
    "-Scenario",
    $Scenario,
    "-ModeId",
    $ModeId,
    "-PreInputSettleMs",
    "$PreInputSettleMs",
    "-InputDurationMs",
    "$InputDurationMs",
    "-SummaryPath",
    $SummaryPath
  )

  if (-not [string]::IsNullOrWhiteSpace($BrowserPath)) {
    $arguments += @("-BrowserPath", $BrowserPath)
  }
  if ($Headful) {
    $arguments += "-Headful"
  }
  if ($DisableGpu) {
    $arguments += "-DisableGpu"
  }

  $exitCode = 0
  try {
    & powershell @arguments *> $LogPath
    if ($null -ne $LASTEXITCODE) {
      $exitCode = [int]$LASTEXITCODE
    }
  } catch {
    if ($null -ne $LASTEXITCODE) {
      $exitCode = [int]$LASTEXITCODE
    } else {
      $exitCode = 1
    }

    Add-Content -LiteralPath $LogPath -Value ""
    Add-Content -LiteralPath $LogPath -Value "BP-44 wrapper caught scenario process failure: $($_.Exception.Message)"
  }

  if ((Test-Path -LiteralPath $SummaryPath -PathType Leaf)) {
    try {
      $summary = Get-Content -LiteralPath $SummaryPath -Raw | ConvertFrom-Json
      return Convert-ToCompactScenarioSummary -Summary $summary -SummaryPath $SummaryPath -LogPath $LogPath -ExitCode $exitCode
    } catch {
      return New-FailedScenarioSummary -Scenario $Scenario -InputDurationMs $InputDurationMs -SummaryPath $SummaryPath -LogPath $LogPath -ExitCode 1
    }
  }

  return New-FailedScenarioSummary -Scenario $Scenario -InputDurationMs $InputDurationMs -SummaryPath $SummaryPath -LogPath $LogPath -ExitCode $exitCode
}

if ([System.IO.Path]::IsPathRooted($OutputDir)) {
  $resolvedOutputDir = [System.IO.Path]::GetFullPath($OutputDir)
} else {
  $resolvedOutputDir = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDir))
}
New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

$scenarioSpecs = @(
  [pscustomobject]@{ Scenario = "MixedMovement"; InputDurationMs = 3500 },
  [pscustomobject]@{ Scenario = "SkillPressure"; InputDurationMs = 3500 },
  [pscustomobject]@{ Scenario = "TargetedSkillPressure"; InputDurationMs = 4500 },
  [pscustomobject]@{ Scenario = "DualClientPressure"; InputDurationMs = 3500 }
)
if (-not $SkipStraightFire) {
  $scenarioSpecs += [pscustomobject]@{ Scenario = "StraightFire"; InputDurationMs = 1800 }
}

$scenarioResults = @()
foreach ($spec in $scenarioSpecs) {
  $summaryPath = Join-Path $resolvedOutputDir "$($spec.Scenario)-summary.json"
  $logPath = Join-Path $resolvedOutputDir "$($spec.Scenario).log"

  Write-Host "Running $($spec.Scenario) inputDurationMs=$($spec.InputDurationMs) ..."
  $scenarioResults += Invoke-FeelSmokeScenario `
    -Scenario $spec.Scenario `
    -InputDurationMs $spec.InputDurationMs `
    -SummaryPath $summaryPath `
    -LogPath $logPath
}

$suiteOk = $true
foreach ($scenarioResult in $scenarioResults) {
  if (-not [bool]$scenarioResult.ok) {
    $suiteOk = $false
  }
}

$suiteSummaryPath = Join-Path $resolvedOutputDir "suite-summary.json"
$suiteSummary = [ordered]@{
  ok = $suiteOk
  suite = "BP-44A battle-feel"
  modeId = $ModeId
  preInputSettleMs = $PreInputSettleMs
  generatedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
  outputDir = $resolvedOutputDir
  headless = (-not [bool]$Headful)
  frontendUrl = $FrontendUrl
  backendUrl = $BackendUrl
  scenarios = @($scenarioResults)
}

$suiteSummary | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $suiteSummaryPath -Encoding UTF8

Write-Host ""
Write-Host "BP-44A battle feel suite summary: $suiteSummaryPath"
foreach ($result in $scenarioResults) {
  $warningCount = Get-PropertyValue -InputObject $result.warnings -Name "count" -DefaultValue 0
  $latency = Get-PropertyValue -InputObject $result -Name "latency"
  $motionMs = Get-PropertyValue -InputObject $latency -Name "motionMs"
  $muzzleMs = Get-PropertyValue -InputObject $latency -Name "muzzleMs"
  $hitDispute = Get-PropertyValue -InputObject $result -Name "hitDispute"
  $hitDisputeSamples = Get-PropertyValue -InputObject $hitDispute -Name "sampleCount"
  $hitDisputeFailures = Get-PropertyValue -InputObject $hitDispute -Name "failureCount" -DefaultValue 0
  Write-Host ("{0} ok={1} sameBattle={2} warnings={3} motionMs={4} muzzleMs={5} hitDisputeSamples={6} hitDisputeFailures={7}" -f `
    $result.scenario,
    $result.ok,
    $result.sameBattle,
    $warningCount,
    $motionMs,
    $muzzleMs,
    $hitDisputeSamples,
    $hitDisputeFailures)
}

if (-not $suiteOk) {
  exit 1
}
