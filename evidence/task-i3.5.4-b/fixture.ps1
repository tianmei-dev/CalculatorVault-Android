# I3.5.4-B PC-side helpers for the 8081 fixture control (loopback + per-start token).
# Tokens (control token, account Bearer tokens) are read from gitignored files and never printed.
$ErrorActionPreference = "Stop"
$Server = "D:\Android_Work_Server\AuroraAppServer"
$Base = "http://127.0.0.1:8081"
$Serial = "emulator-5554"

function Get-ControlToken { (Get-Content "$Server\.local\s32it\payment-control.token" -Raw).Trim() }

$AccountsFile = "$Server\.local\i343d1\payment-accounts.local.json"

function Get-PayAccount([int]$Index) {
    $root = Get-Content $AccountsFile -Raw | ConvertFrom-Json
    return $root.accounts[$Index - 1]
}

function Invoke-Fixture([string]$Method, [string]$Path) {
    $headers = @{ "X-Fixture-Control-Token" = (Get-ControlToken) }
    try {
        $r = Invoke-WebRequest -Uri "$Base$Path" -Method $Method -Headers $headers -UseBasicParsing -TimeoutSec 20
        return "HTTP $($r.StatusCode) $($r.Content)"
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return "HTTP $([int]$resp.StatusCode)" } else { return "ERR $($_.Exception.Message)" }
    }
}

function Get-FakeOrder([string]$OrderNo) { Invoke-Fixture GET "/internal/fixture/fake-payment/orders/$OrderNo" }
function Send-FakeNotify([string]$OrderNo) { Invoke-Fixture POST "/internal/fixture/fake-payment/orders/$OrderNo/notify" }

function Invoke-AsAccount([int]$Index, [string]$Method, [string]$Path, [string]$Body = $null) {
    $acc = Get-PayAccount $Index
    $headers = @{ "X-App-Code" = "calculator_vault"; "Authorization" = "Bearer $($acc.accessToken)" }
    try {
        if ($Body) {
            $r = Invoke-WebRequest -Uri "$Base$Path" -Method $Method -Headers $headers -Body $Body -ContentType "application/json; charset=utf-8" -UseBasicParsing -TimeoutSec 20
        } else {
            $r = Invoke-WebRequest -Uri "$Base$Path" -Method $Method -Headers $headers -UseBasicParsing -TimeoutSec 20
        }
        return "HTTP $($r.StatusCode) " + [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return "HTTP $([int]$resp.StatusCode)" } else { return "ERR $($_.Exception.Message)" }
    }
}

function Wait-Marker([string]$Name, [int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $line = adb -s $Serial logcat -d -s CV_I354B_MARKER:I | Select-String -Pattern "$Name " | Select-Object -Last 1
        if ($line) { return $line.Line.Substring($line.Line.IndexOf($Name)) }
        Start-Sleep -Milliseconds 700
    }
    return $null
}

function Send-Ack { adb -s $Serial shell "echo ok > /data/local/tmp/cv_i354b_ack" | Out-Null }

function Get-DbSummary([long]$UserId) {
    $sql = "SELECT 'orders', COUNT(*), GROUP_CONCAT(CONCAT(order_no,':',plan_code,':',status) ORDER BY id) FROM payment_orders WHERE user_id=$UserId; " +
        "SELECT 'requests', COUNT(*), GROUP_CONCAT(plan_code ORDER BY id) FROM payment_order_requests WHERE user_id=$UserId; " +
        "SELECT 'grants', COUNT(*), GROUP_CONCAT(CONCAT(plan_code,':',source_type,':',IFNULL(revoked_at,'-')) ORDER BY id) FROM membership_grants WHERE user_id=$UserId; " +
        "SELECT 'notifications', COUNT(*), '' FROM payment_notifications WHERE order_no IN (SELECT order_no FROM payment_orders WHERE user_id=$UserId); " +
        "SELECT 'events', COUNT(*), GROUP_CONCAT(CONCAT(IFNULL(e.from_status,'-'),'>',e.to_status) ORDER BY e.id) FROM payment_order_events e JOIN payment_orders o ON o.id=e.order_id WHERE o.user_id=$UserId;"
    $sql | docker exec -i aurora-mysql-it sh -c 'mysql -utest -ptest -N aurora_app_test 2>/dev/null'
}
