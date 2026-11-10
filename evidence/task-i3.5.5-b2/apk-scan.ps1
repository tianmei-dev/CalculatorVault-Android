# I3.5.5-B2 APK / DEX safety scan. Secret values are only reported as found / not found.
param(
    [string]$ArtifactDir = 'D:\Android_Work\CalculatorVault-Android\build\i355b2-artifacts',
    [string]$Out = 'D:\Android_Work\CalculatorVault-Android\evidence\task-i3.5.5-b2\apk-scan.txt'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-Apk([string]$path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($path)
    $dex = @{}; $other = @{}
    foreach ($e in $zip.Entries) {
        if ($e.Length -eq 0) { continue }
        $isDex = $e.FullName -like 'classes*.dex'
        $isText = $e.FullName -match '\.(xml|arsc|json|properties|txt|pem|key|jks|keystore|p12|bks)$' -or $e.FullName -like 'assets/*'
        if (-not ($isDex -or $isText)) { continue }
        $ms = New-Object IO.MemoryStream
        $s = $e.Open(); $s.CopyTo($ms); $s.Close()
        if ($isDex) { $dex[$e.FullName] = $ms.ToArray() } else { $other[$e.FullName] = $ms.ToArray() }
    }
    $zip.Dispose()
    return @{ Dex = $dex; Other = $other }
}

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

function Hits($apk, [string]$needle) {
    $b = [Text.Encoding]::UTF8.GetBytes($needle)
    $u16 = [Text.Encoding]::Unicode.GetBytes($needle) # resources.arsc may store UTF-16
    $where = @()
    foreach ($k in $apk.Dex.Keys) { if (Find-Bytes $apk.Dex[$k] $b) { $where += $k } }
    foreach ($k in $apk.Other.Keys) { if ((Find-Bytes $apk.Other[$k] $b) -or (Find-Bytes $apk.Other[$k] $u16)) { $where += $k } }
    return ,$where
}

$testSymbols = @(
    'FakeMembershipProvider', 'IsolatedTestSessionAccess', 'IsolatedTestSessionBridge', 'TestSessionInstaller',
    'EntitlementInstrumentationBridge', 'PaymentQueryProbe', 'IsolatedPaymentE2ETest', 'RealMembershipE2EInstrumentationTest',
    '/internal/fixture', 'X-Fixture-Control-Token', 'fake-payment', 'fixture/payment',
    'cv_i354b', 'cv_i343d1', 'CV_I354B_MARKER', 'i343d1-sessions'
)
$isolatedHosts = @('10.0.2.2:8081', '192.168.0.107:8081', ':8081', ':8082')
$merchantIndicators = @(
    '-----BEGIN', 'PRIVATE KEY', 'merchantKey', 'merchant_key', 'merchantSecret', 'mchKey', 'mch_key',
    'appSecret', 'app_secret', 'signKey', 'sign_key', 'yusen', 'Yusen', 'YUSEN'
)
$secretFiles = @('D:\Android_Work_Server\AuroraAppServer\.local\s32it\payment-control.token') +
    (Get-ChildItem 'D:\Android_Work_Server\AuroraAppServer\.local\i343d1\*.local.json' | ForEach-Object FullName)
$secrets = New-Object System.Collections.Generic.List[string]
foreach ($f in $secretFiles) {
    $raw = (Get-Content $f -Raw).Trim()
    if ($f -like '*.token') { $secrets.Add($raw); continue }
    foreach ($m in [regex]::Matches($raw, '"(?:accessToken|openid)"\s*:\s*"([^"]{16,})"')) { $secrets.Add($m.Groups[1].Value) }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("secrets loaded: $($secrets.Count) (values never printed)")
foreach ($apkFile in Get-ChildItem $ArtifactDir -Filter *.apk | Sort-Object Name) {
    $apk = Read-Apk $apkFile.FullName
    $lines.Add('')
    $lines.Add("== $($apkFile.Name)  SHA256=" + (Get-FileHash $apkFile.FullName).Hash.Substring(0, 16) + "…  dex=$($apk.Dex.Count)")
    foreach ($group in @(@('test', $testSymbols), @('isolated-host', $isolatedHosts), @('merchant', $merchantIndicators))) {
        $found = @()
        foreach ($n in $group[1]) { $w = Hits $apk $n; if ($w.Count) { $found += "$n@$(($w | Sort-Object -Unique) -join ',')" } }
        $lines.Add(("  {0,-14} hits={1} {2}" -f $group[0], $found.Count, ($found -join '; ')))
    }
    $sHits = 0
    foreach ($s in $secrets) { if ((Hits $apk $s).Count) { $sHits++ } }
    $lines.Add(("  {0,-14} hits={1}" -f 'test-secrets', $sHits))
}
$lines | Set-Content -Path $Out -Encoding UTF8
$lines
