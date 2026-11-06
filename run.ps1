param(
  [ValidateSet('app', 'db-test', 'backend')]
  [string]$Mode = 'app'
)

Set-Location $PSScriptRoot

$databaseKeys = @(
  'SUPABASE_DB_URL',
  'SUPABASE_DB_USER',
  'SUPABASE_DB_PASSWORD'
)
$doctorMappingKeys = @(
  'CLYVR_DEMO_DOCTOR_ID_D1',
  'CLYVR_DEMO_DOCTOR_ID_D2',
  'CLYVR_DEMO_DOCTOR_ID_D3',
  'CLYVR_DEMO_DOCTOR_ID_D4'
)
$backendKeys = @(
  'BACKEND_PORT',
  'BACKEND_BIND_ADDRESS',
  'CLYVR_BACKEND_URL',
  'CLYVR_HASH_SIGNER_KEY',
  'CLYVR_HASH_SALT_SEPARATOR'
)
$allowedKeys = $databaseKeys + $doctorMappingKeys + $backendKeys
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile -PathType Leaf) {
  $seenKeys = @{}
  $lineNumber = 0
  foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
    $lineNumber++
    $entry = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($entry) -or $entry.StartsWith('#')) {
      continue
    }

    if ($entry -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
      Write-Error "Malformed .env entry at line $lineNumber."
      exit 1
    }

    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if (
      $value.StartsWith('"') -or $value.StartsWith("'") -or
      $value.EndsWith('"') -or $value.EndsWith("'")
    ) {
      if (
        $value.Length -lt 2 -or $value[0] -ne $value[$value.Length - 1] -or
        ($value[0] -ne '"' -and $value[0] -ne "'")
      ) {
        Write-Error "Malformed .env value at line $lineNumber."
        exit 1
      }
      $value = $value.Substring(1, $value.Length - 2)
    }

    if ($allowedKeys -contains $key) {
      if ($seenKeys.ContainsKey($key)) {
        Write-Error "Duplicate .env variable at line $lineNumber."
        exit 1
      }
      $seenKeys[$key] = $true
      if (-not (Test-Path "Env:$key")) {
        Set-Item -Path "Env:$key" -Value $value
      }
    }
  }
}

if ($Mode -eq 'db-test') {
  $missingKeys = @($databaseKeys | Where-Object {
    -not (Test-Path "Env:$_") -or
      [string]::IsNullOrWhiteSpace((Get-Item "Env:$_").Value)
  })
  if ($missingKeys.Count -gt 0) {
    Write-Error "Missing required database environment variable(s): $($missingKeys -join ', ')."
    exit 1
  }
}

if ($Mode -eq 'backend') {
  if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error 'Maven is required to run the backend. Install Maven and try again.'
    exit 1
  }
  $requiredBackendKeys = $databaseKeys + $doctorMappingKeys + @(
    'CLYVR_HASH_SIGNER_KEY',
    'CLYVR_HASH_SALT_SEPARATOR'
  )
  $missingBackendKeys = @($requiredBackendKeys | Where-Object {
    -not (Test-Path "Env:$_") -or
      [string]::IsNullOrWhiteSpace((Get-Item "Env:$_").Value)
  })
  if ($missingBackendKeys.Count -gt 0) {
    Write-Error "Missing required local backend environment variable(s): $($missingBackendKeys -join ', ')."
    exit 1
  }
  & mvn -f .\backend\pom.xml spring-boot:run
  exit $LASTEXITCODE
}

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
  Write-Error 'Java JDK 21 is required. Add javac to PATH and try again.'
  exit 1
}

New-Item -ItemType Directory -Force .\out | Out-Null
$files = @(Get-ChildItem .\src -Recurse -Filter *.java | ForEach-Object FullName)
& javac -encoding UTF-8 -d .\out $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$mainClass = if ($Mode -eq 'db-test') {
  'clyvr.database.DatabaseConnectionTest'
} else {
  'clyvr.controller.AppController'
}
$classpath = '.\out;.\lib\postgresql-42.7.14.jar'
if ($Mode -eq 'app') {
  foreach ($key in $databaseKeys + $doctorMappingKeys) {
    Remove-Item -Path "Env:$key" -ErrorAction SilentlyContinue
  }
  Remove-Item -Path Env:CLYVR_HASH_SIGNER_KEY -ErrorAction SilentlyContinue
  Remove-Item -Path Env:CLYVR_HASH_SALT_SEPARATOR -ErrorAction SilentlyContinue
}
& java -cp $classpath $mainClass
exit $LASTEXITCODE
