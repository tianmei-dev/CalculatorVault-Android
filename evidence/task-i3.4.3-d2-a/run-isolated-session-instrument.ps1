# I3.4.3-D2-A: build isolated Debug, push D1 sessions (no token echo), install -r, am instrument.
# Prerequisites: 8081 s32it up; run Server scripts\i343d1-fixture.bat seed if tokens were revoked.
# Usage (from repo root): powershell -File evidence/task-i3.4.3-d2-a/run-isolated-session-instrument.ps1
$ErrorActionPreference = "Stop"
$serial = "emulator-5554"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$serverSessions = "D:\Android_Work_Server\AuroraAppServer\build\i343d1-sessions.local.json"
$deviceSessions = "/data/local/tmp/cv_i343d1_sessions.local.json"
$pkg = "com.aurora.calculatorvault"
$testPkg = "com.aurora.calculatorvault.test"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$clazz = "com.aurora.calculatorvault.feature.login.session.IsolatedTestSessionInstrumentationTest"

if (-not (Test-Path $serverSessions)) {
    throw "Missing D1 sessions file (run server i343d1-fixture seed). Path not printed with contents."
}

adb -s $serial get-state | Out-Null
Write-Host "Pushing session fixture to device path (contents not printed)..."
adb -s $serial push $serverSessions $deviceSessions | Out-Null

Write-Host "Building isolated Debug app + androidTest..."
Push-Location $repo
try {
    .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest "-PISOLATED_MEMBERSHIP_TEST=true"
} finally {
    Pop-Location
}

$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repo "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
Write-Host "adb install -r (app)..."
adb -s $serial install -r $apk
Write-Host "adb install -r (androidTest)..."
adb -s $serial install -r $testApk

Write-Host "am instrument (IsolatedTestSessionInstrumentationTest)..."
adb -s $serial shell am instrument -w -r -e class $clazz "$testPkg/$runner"
Write-Host "Done."
