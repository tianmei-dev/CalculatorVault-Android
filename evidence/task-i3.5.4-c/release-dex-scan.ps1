# Scans the Release APK's DEX files for test-only symbols and secrets.
# Secret values are loaded from local files and only reported as found/not-found, never printed.
param(
    [string]$Apk = 'D:\Android_Work\CalculatorVault-Android\app\build\outputs\apk\release\app-release-unsigned.apk',
    [string]$Out = 'D:\Android_Work\CalculatorVault-Android\evidence\task-i3.5.4-c\release-dex-scan.txt'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [IO.Compression.ZipFile]::OpenRead($Apk)
$dex = @{}
foreach ($e in $zip.Entries | Where-Object { $_.FullName -like 'classes*.dex' }) {
    $ms = New-Object IO.MemoryStream
    $s = $e.Open(); $s.CopyTo($ms); $s.Close()
    $dex[$e.FullName] = $ms.ToArray()
}
$zip.Dispose()

function Find-Bytes([byte[]]$hay, [byte[]]$needle) {
    $first = $needle[0]
    $i = [Array]::IndexOf($hay, $first)
    while ($i -ge 0 -and $i -le $hay.Length - $needle.Length) {
        $ok = $true
        for ($j = 1; $j -lt $needle.Length; $j++) { if ($hay[$i + $j] -ne $needle[$j]) { $ok = $false; break } }
        if ($ok) { return $true }
        $i = [Array]::IndexOf($hay, $first, $i + 1)
    }
    return $false
}

$public = @(
    'FakeMembershipProvider',
    'IsolatedTestSessionAccess', 'IsolatedTestSessionBridge', 'TestSessionInstaller',
    'EntitlementInstrumentationBridge', 'PaymentQueryProbe', 'IsolatedPaymentE2ETest',
    '/internal/fixture', 'X-Fixture-Control-Token', 'fake-payment', 'fixture/payment',
    'cv_i354b', 'cv_i343d1', 'CV_I354B_MARKER',
    '10.0.2.2:8081', '192.168.0.107:8081', ':8081'
)
$secretFiles = @(
    'D:\Android_Work_Server\AuroraAppServer\.local\s32it\payment-control.token'
) + (Get-ChildItem 'D:\Android_Work_Server\AuroraAppServer\.local\i343d1\*.local.json' | ForEach-Object FullName)
$secrets = New-Object System.Collections.Generic.List[string]
foreach ($f in $secretFiles) {
    $raw = (Get-Content $f -Raw).Trim()
    if ($f -like '*.token') { $secrets.Add($raw); continue }
    foreach ($m in [regex]::Matches($raw, '"(?:token|accessToken|access_token|controlToken|openid)"\s*:\s*"([^"]{16,})"')) {
        $secrets.Add($m.Groups[1].Value)
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("APK: $Apk")
$lines.Add("APK SHA256: " + (Get-FileHash $Apk -Algorithm SHA256).Hash)
$lines.Add("DEX files: " + (($dex.Keys | Sort-Object) -join ', '))
$hits = 0
foreach ($p in $public) {
    $b = [Text.Encoding]::UTF8.GetBytes($p)
    $where = @($dex.Keys | Where-Object { Find-Bytes $dex[$_] $b })
    if ($where.Count) { $hits++ }
    $lines.Add(("{0,-36} found={1} {2}" -f $p, [bool]$where.Count, ($where -join ',')))
}
$k = 0
foreach ($s in $secrets) {
    $k++
    $b = [Text.Encoding]::UTF8.GetBytes($s)
    $where = @($dex.Keys | Where-Object { Find-Bytes $dex[$_] $b })
    if ($where.Count) { $hits++ }
    $lines.Add(("secret#{0,-3} (len={1}) found={2}" -f $k, $s.Length, [bool]$where.Count))
}
$lines.Add("secrets checked: $($secrets.Count)")
$lines.Add("TOTAL HITS: $hits")
$lines | Set-Content -Path $Out -Encoding UTF8
$lines
