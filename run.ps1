Set-Location $PSScriptRoot
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
  Write-Error 'Java JDK 21 is required. Add javac to PATH and try again.'
  exit 1
}
New-Item -ItemType Directory -Force .\out | Out-Null
$files = @(Get-ChildItem .\src -Recurse -Filter *.java | ForEach-Object FullName)
& javac -encoding UTF-8 -d .\out $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp .\out clyvr.controller.AppController
