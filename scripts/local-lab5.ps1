[CmdletBinding()]
param(
    [ValidateSet("Start", "Stop", "Status")]
    [string]$Action = "Start",
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $ProjectRoot "backend"
$RuntimeDir = Join-Path $env:LOCALAPPDATA "AltHuntLocal"
$DownloadsDir = Join-Path $RuntimeDir "downloads"
$PostgresDir = Join-Path $RuntimeDir "postgresql-17.11"
$PostgresRoot = Join-Path $PostgresDir "pgsql"
$PortablePostgresBin = Join-Path $PostgresRoot "bin"
$DataDir = Join-Path $RuntimeDir "data"
$NodeDir = Join-Path $RuntimeDir "node-v24.21.0-win-x64"
$NodeExe = Join-Path $NodeDir "node.exe"
$NpmCmd = Join-Path $NodeDir "npm.cmd"
$ViewerScript = Join-Path $PSScriptRoot "local-db-viewer.js"
$PostgresArchive = Join-Path $DownloadsDir "postgresql-17.11.zip"
$NodeArchive = Join-Path $DownloadsDir "node-v24.21.0-win-x64.zip"
$PostgresUrl = "https://get.enterprisedb.com/postgresql/postgresql-17.11-1-windows-x64-binaries.zip"
$PostgresSha256 = "6EABDF00D2893713B75DB4336A23C3FDF505F056E217EC6E2E95D901750CFEA3"
$NodeBaseUrl = "https://nodejs.org/download/release/v24.21.0"
$ApiPidFile = Join-Path $RuntimeDir "api.pid"
$ViewerPidFile = Join-Path $RuntimeDir "viewer.pid"
$PostgresBinFile = Join-Path $RuntimeDir "postgres-bin.txt"
$PostgresPortFile = Join-Path $RuntimeDir "postgres.port"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Test-Http([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Wait-Http([string]$Url, [int]$Seconds = 30) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-Http $Url) { return }
        Start-Sleep -Seconds 1
    }
    throw "Service did not respond within $Seconds seconds: $Url"
}

function Stop-SavedProcess([string]$PidFile, [string]$Name) {
    if (-not (Test-Path -LiteralPath $PidFile)) { return }
    $savedPid = [int](Get-Content -LiteralPath $PidFile -Raw)
    $process = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq "node") {
        Stop-Process -Id $savedPid -Force
        Write-Host "$Name stopped."
    }
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
}

function Test-PostgresBin([string]$BinDir) {
    if (-not $BinDir) { return $false }
    foreach ($file in @("postgres.exe", "initdb.exe", "pg_ctl.exe", "pg_isready.exe", "psql.exe", "createdb.exe")) {
        if (-not (Test-Path -LiteralPath (Join-Path $BinDir $file))) { return $false }
    }
    return $true
}

function Find-PostgresBin {
    if (Test-Path -LiteralPath $PostgresBinFile) {
        $savedBin = (Get-Content -LiteralPath $PostgresBinFile -Raw).Trim()
        if (Test-PostgresBin $savedBin) { return $savedBin }
    }

    $pathPostgres = Get-Command postgres.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pathPostgres) {
        $pathBin = Split-Path -Parent $pathPostgres.Source
        if (Test-PostgresBin $pathBin) { return $pathBin }
    }

    $programFilesPostgres = Join-Path $env:ProgramFiles "PostgreSQL"
    if (Test-Path -LiteralPath $programFilesPostgres) {
        $installedBins = Get-ChildItem -LiteralPath $programFilesPostgres -Directory -ErrorAction SilentlyContinue |
            Sort-Object { try { [version]$_.Name } catch { [version]"0.0" } } -Descending |
            ForEach-Object { Join-Path $_.FullName "bin" }
        foreach ($installedBin in $installedBins) {
            if (Test-PostgresBin $installedBin) { return $installedBin }
        }
    }

    if (Test-PostgresBin $PortablePostgresBin) { return $PortablePostgresBin }
    return $null
}

function Get-PostgresPort {
    if (Test-Path -LiteralPath $PostgresPortFile) {
        $savedPort = 0
        if ([int]::TryParse((Get-Content -LiteralPath $PostgresPortFile -Raw).Trim(), [ref]$savedPort) -and $savedPort -gt 0) {
            return $savedPort
        }
    }
    return 5432
}

function Test-TcpPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        return $connect.AsyncWaitHandle.WaitOne(300) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Find-FreePostgresPort([int]$PreferredPort) {
    if (-not (Test-TcpPort $PreferredPort)) { return $PreferredPort }
    foreach ($candidate in 55432..55442) {
        if (-not (Test-TcpPort $candidate)) { return $candidate }
    }
    throw "No free local PostgreSQL port found (checked 55432-55442)."
}

function Show-Status {
    $statusBin = Find-PostgresBin
    $statusPort = Get-PostgresPort
    $pgIsReady = if ($statusBin) { Join-Path $statusBin "pg_isready.exe" } else { "" }
    $postgres = if ($pgIsReady -and (Test-Path $pgIsReady)) { & $pgIsReady -h 127.0.0.1 -p $statusPort -d bugs -U bugs 2>$null } else { "binaries not found" }
    $api = if (Test-Http "http://127.0.0.1:8080/api/health") { "running" } else { "not responding" }
    $viewer = if (Test-Http "http://127.0.0.1:8090/") { "running" } else { "not responding" }
    Write-Host "PostgreSQL: $postgres"
    Write-Host "API:        $api"
    Write-Host "Viewer:     $viewer"
}

function Download-VerifiedFile([string]$Url, [string]$Destination, [string]$ExpectedHash) {
    if (Test-Path -LiteralPath $Destination) {
        $actual = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
        if ($actual -eq $ExpectedHash) { return }
        Remove-Item -LiteralPath $Destination -Force
    }
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    $actual = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
    if ($actual -ne $ExpectedHash) {
        Remove-Item -LiteralPath $Destination -Force
        throw "Downloaded file checksum mismatch: $Destination"
    }
}

if ($Action -eq "Status") {
    Show-Status
    exit 0
}

if ($Action -eq "Stop") {
    Write-Step "Stopping the local ALTHUNT environment"
    Stop-SavedProcess $ViewerPidFile "Viewer"
    Stop-SavedProcess $ApiPidFile "API"
    $stopBin = Find-PostgresBin
    $pgCtl = if ($stopBin) { Join-Path $stopBin "pg_ctl.exe" } else { "" }
    if ($pgCtl -and (Test-Path $pgCtl) -and (Test-Path (Join-Path $DataDir "PG_VERSION"))) {
        & $pgCtl -D $DataDir -m fast -w stop 2>$null
    }
    Show-Status
    exit 0
}

if (-not [Environment]::Is64BitOperatingSystem) {
    throw "This script supports 64-bit Windows only."
}
if (-not (Test-Path -LiteralPath $BackendDir)) {
    throw "The backend directory was not found. Run this script from a cloned ALTHUNT repository."
}

New-Item -ItemType Directory -Force -Path $RuntimeDir, $DownloadsDir | Out-Null

Write-Step "Preparing PostgreSQL"
$PostgresBin = Find-PostgresBin
if ($PostgresBin) {
    Write-Host "Found PostgreSQL: $PostgresBin"
} else {
    Write-Host "Installed PostgreSQL was not found; using portable PostgreSQL 17.11."
    Download-VerifiedFile $PostgresUrl $PostgresArchive $PostgresSha256
    if (Test-Path $PostgresDir) { Remove-Item -LiteralPath $PostgresDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $PostgresDir | Out-Null
    Expand-Archive -LiteralPath $PostgresArchive -DestinationPath $PostgresDir -Force
    $PostgresBin = $PortablePostgresBin
    if (-not (Test-PostgresBin $PostgresBin)) { throw "Required PostgreSQL programs were not found in the archive." }
}
Set-Content -LiteralPath $PostgresBinFile -Value $PostgresBin -Encoding UTF8

Write-Step "Preparing Node.js 24.21.0"
$shasumsPath = Join-Path $DownloadsDir "node-SHASUMS256.txt"
Invoke-WebRequest -Uri "$NodeBaseUrl/SHASUMS256.txt" -OutFile $shasumsPath -UseBasicParsing
$nodeFileName = Split-Path -Leaf $NodeArchive
$nodeHashLine = Get-Content $shasumsPath | Where-Object { $_ -match "\s+$([regex]::Escape($nodeFileName))$" } | Select-Object -First 1
if (-not $nodeHashLine) { throw "$nodeFileName was not found in the official SHASUMS256.txt" }
$nodeHash = ($nodeHashLine -split "\s+")[0].ToUpperInvariant()
Download-VerifiedFile "$NodeBaseUrl/$nodeFileName" $NodeArchive $nodeHash
if (-not (Test-Path $NodeExe)) {
    Expand-Archive -LiteralPath $NodeArchive -DestinationPath $RuntimeDir -Force
}

$initDb = Join-Path $PostgresBin "initdb.exe"
$pgCtl = Join-Path $PostgresBin "pg_ctl.exe"
$pgIsReady = Join-Path $PostgresBin "pg_isready.exe"
$createdb = Join-Path $PostgresBin "createdb.exe"
$psql = Join-Path $PostgresBin "psql.exe"

if (-not (Test-Path (Join-Path $DataDir "PG_VERSION"))) {
    Write-Step "Creating the persistent PostgreSQL cluster"
    & $initDb -D $DataDir -U bugs -A trust -E UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw "initdb exited with code $LASTEXITCODE" }
}

$PostgresPort = Get-PostgresPort
& $pgCtl -D $DataDir status *> $null
$clusterIsRunning = $LASTEXITCODE -eq 0
if (-not $clusterIsRunning) {
    $PostgresPort = Find-FreePostgresPort $PostgresPort
    Set-Content -LiteralPath $PostgresPortFile -Value $PostgresPort -Encoding Ascii
}

Write-Step "Starting PostgreSQL on 127.0.0.1:$PostgresPort"
if (-not $clusterIsRunning) {
    $postgresLog = Join-Path $RuntimeDir "postgres.log"
    & $pgCtl -D $DataDir -l $postgresLog -o "-h 127.0.0.1 -p $PostgresPort" -w start
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL did not start. Log: $postgresLog" }
}

$databaseExists = & $psql -h 127.0.0.1 -p $PostgresPort -U bugs -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='bugs'"
if ($databaseExists -ne "1") {
    & $createdb -h 127.0.0.1 -p $PostgresPort -U bugs bugs
    if ($LASTEXITCODE -ne 0) { throw "Could not create the bugs database" }
}

Write-Step "Installing API dependencies"
& $NpmCmd install --prefix $BackendDir --omit=dev --no-audit --no-fund --package-lock=false
if ($LASTEXITCODE -ne 0) { throw "npm install exited with code $LASTEXITCODE" }

if (-not (Test-Http "http://127.0.0.1:8080/api/health")) {
    Write-Step "Starting the API and migrations"
    $env:DATABASE_URL = "postgres://bugs:bugs@127.0.0.1:$PostgresPort/bugs"
    $env:DATABASE_SSL = "false"
    $env:PORT = "8080"
    $env:CORS_ORIGIN = "*"
    $apiProcess = Start-Process -FilePath $NodeExe -ArgumentList "src/server.js" -WorkingDirectory $BackendDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $RuntimeDir "api.stdout.log") `
        -RedirectStandardError (Join-Path $RuntimeDir "api.stderr.log") -PassThru
    Set-Content -LiteralPath $ApiPidFile -Value $apiProcess.Id -Encoding Ascii
    Wait-Http "http://127.0.0.1:8080/api/health" 30
}

if (-not (Test-Http "http://127.0.0.1:8090/")) {
    Write-Step "Starting the database viewer"
    $env:BUGS_PSQL = $psql
    $env:BUGS_PG_PORT = [string]$PostgresPort
    $env:BUGS_VIEWER_PORT = "8090"
    $viewerProcess = Start-Process -FilePath $NodeExe -ArgumentList $ViewerScript -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $RuntimeDir "viewer.stdout.log") `
        -RedirectStandardError (Join-Path $RuntimeDir "viewer.stderr.log") -PassThru
    Set-Content -LiteralPath $ViewerPidFile -Value $viewerProcess.Id -Encoding Ascii
    Wait-Http "http://127.0.0.1:8090/" 30
}

Write-Step "ALTHUNT is ready"
Show-Status
Write-Host "`nDatabase: bugs@127.0.0.1:$PostgresPort (user bugs)"
Write-Host "API:    http://127.0.0.1:8080/api"
Write-Host "Viewer:   http://127.0.0.1:8090/"
Write-Host "Data:     $DataDir"
Write-Host "Stop: powershell -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Action Stop"

if (-not $NoBrowser) {
    Start-Process "http://127.0.0.1:8090/"
}
