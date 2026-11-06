"""Host-side redacted /membership/me evidence against isolated :8081.

Reads D1 token file but never prints tokens. Output: label, HTTP status, envelope code, data fields.
"""
import json
import sys
import urllib.error
import urllib.request

SESSIONS = r"D:\Android_Work_Server\AuroraAppServer\build\i343d1-sessions.local.json"
BASE = "http://127.0.0.1:8081"


def main() -> int:
    with open(SESSIONS, encoding="utf-8") as f:
        root = json.load(f)
    out = []
    for acc in root["accounts"]:
        label = acc["label"]
        app_code = "i343d1_other_app" if label == "other-app-none" else "calculator_vault"
        req = urllib.request.Request(
            BASE + "/api/v1/membership/me",
            headers={"X-App-Code": app_code, "Authorization": "Bearer " + acc["accessToken"]},
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                status = resp.status
                body = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            status = e.code
            try:
                body = json.loads(e.read().decode("utf-8"))
            except Exception:
                body = {}
        data = body.get("data") or {}
        out.append({
            "label": label,
            "userId": acc.get("userId"),
            "http": status,
            "code": body.get("code"),
            "status": data.get("status"),
            "planType": data.get("planType"),
            "contributingPlanTypes": data.get("contributingPlanTypes"),
            "isLifetime": data.get("isLifetime"),
            "membershipExpiresAt": data.get("membershipExpiresAt"),
            "serverTime": data.get("serverTime"),
            "activeGrantCount": data.get("activeGrantCount"),
        })
    json.dump(out, sys.stdout, ensure_ascii=False, indent=2)
    print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
