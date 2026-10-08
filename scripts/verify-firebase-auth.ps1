$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOldJava = $env:JAVA_HOME
$taskOldPath = $env:Path
try {
    Set-Location -LiteralPath $taskRoot
    if ((& node --version) -ne 'v24.16.0') { throw 'Firebase gate requires Node 24.16.0' }
    if ((& npm --version) -ne '11.17.0') { throw 'Firebase gate requires npm 11.17.0' }
    $taskCli = $env:GEMS_FIREBASE_CLI_JS
    if (-not $taskCli) { $taskCli = Join-Path $taskRoot 'node_modules/firebase-tools/lib/bin/firebase.js' }
    if (-not (Test-Path -LiteralPath $taskCli -PathType Leaf)) { throw 'Firebase CLI 15.32.1 must be provided locally through GEMS_FIREBASE_CLI_JS; this gate never installs tools.' }
    $taskCli = (Resolve-Path -LiteralPath $taskCli).Path
    $taskCliPackage = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $taskCli))) 'package.json'
    $taskMetadata = Get-Content -Raw -LiteralPath $taskCliPackage | ConvertFrom-Json
    if ($taskMetadata.name -ne 'firebase-tools' -or $taskMetadata.version -ne '15.32.1') { throw 'Firebase CLI pin mismatch' }
    if ($env:FIREBASE_AUTH_EMULATOR_HOST) { throw 'Run the gate with FIREBASE_AUTH_EMULATOR_HOST unset; the CLI owns the test process environment.' }
    if (Test-Path -LiteralPath 'D:/Program Files/Java/jdk21') { $env:JAVA_HOME = 'D:/Program Files/Java/jdk21'; $env:Path = "$env:JAVA_HOME/bin;$env:Path" }
    $taskMaven = $env:GEMS_MAVEN_CMD
    if (-not $taskMaven) {
        if (Test-Path -LiteralPath 'D:/Program Files/Apache/Maven/bin/mvn.cmd') { $taskMaven = 'D:/Program Files/Apache/Maven/bin/mvn.cmd' }
        else { $taskMaven = (Get-Command mvn -ErrorAction Stop).Source }
    }
    $taskGateDir = Join-Path $taskRoot 'target/firebase-emulator-gate'
    New-Item -ItemType Directory -Path $taskGateDir -Force | Out-Null
    $taskConfig = Join-Path $taskGateDir 'firebase.json'
    [IO.File]::WriteAllText($taskConfig, '{"emulators":{"auth":{"host":"127.0.0.1","port":9099},"ui":{"enabled":false},"singleProjectMode":true}}', [Text.UTF8Encoding]::new($false))
    $taskDriver = Join-Path $taskGateDir 'run-tests.ps1'
    $taskMavenLiteral = $taskMaven.Replace("'", "''")
    $taskRootLiteral = $taskRoot.Replace("'", "''")
    [IO.File]::WriteAllText($taskDriver, "`$ErrorActionPreference = 'Stop'`nSet-Location -LiteralPath '$taskRootLiteral'`n& '$taskMavenLiteral' -B -pl gems-firebase-auth -am -Pfirebase-emulator test-compile failsafe:integration-test failsafe:verify`nexit `$LASTEXITCODE`n", [Text.UTF8Encoding]::new($false))
    $taskPowerShell = (Get-Process -Id $PID).ProcessName
    $taskDriverLiteral = $taskDriver.Replace("'", "''")
    $taskEncodedDriver = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("& '$taskDriverLiteral'; exit `$LASTEXITCODE"))
    $taskCommand = $taskPowerShell + ' -NoProfile -EncodedCommand ' + $taskEncodedDriver
    Set-Location -LiteralPath $taskGateDir
    & node $taskCli emulators:exec --only auth --project demo-gems --config $taskConfig --non-interactive $taskCommand
    exit $LASTEXITCODE
} catch {
    Write-Error $_
    exit 1
} finally {
    $env:JAVA_HOME = $taskOldJava
    $env:Path = $taskOldPath
}
