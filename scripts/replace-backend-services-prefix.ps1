param(
  [string]$TargetDirectory = "D:\26Spring\Typesafe\slay\TPsys\backend\src\main\scala",
  [string]$SearchText = "services.http4s",
  [string]$ReplacementText = "route"
)

$resolvedTarget = Resolve-Path -LiteralPath $TargetDirectory -ErrorAction Stop
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$changedFiles = @()

Get-ChildItem -LiteralPath $resolvedTarget -Recurse -File | ForEach-Object {
  $path = $_.FullName
  $content = [System.IO.File]::ReadAllText($path)

  if (-not $content.Contains($SearchText)) {
    return
  }

  $updatedContent = $content.Replace($SearchText, $ReplacementText)

  if ($updatedContent -ceq $content) {
    return
  }

  [System.IO.File]::WriteAllText($path, $updatedContent, $utf8NoBom)
  $changedFiles += $path
}

Write-Host ("Target: {0}" -f $resolvedTarget)
Write-Host ("Changed files: {0}" -f $changedFiles.Count)
$changedFiles | ForEach-Object { Write-Host $_ }
