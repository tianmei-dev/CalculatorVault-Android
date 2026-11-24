from pathlib import Path
import subprocess, time, struct, json
ROOT = Path(__file__).resolve().parent
ADB = r"C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SERIAL = "emulator-5580"
PACKAGE = "com.aurora.calculatorvault.screenshotpreview"
ACTIVITY = PACKAGE + "/com.aurora.calculatorvault.screenshotpreview.ScreenshotsActivity"
def adb(*args, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, check=check)
if adb("shell", "getprop", "sys.boot_completed").stdout.strip() != b"1":
    raise SystemExit("Emulator is not ready")
for key in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]:
    adb("shell", "settings", "put", "global", key, "0")
adb("shell", "settings", "put", "system", "font_scale", "1.0")
adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "enter")
adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "clock", "-e", "hhmm", "0941")
adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "notifications", "-e", "visible", "false")
results=[]
for number, page in enumerate(["calculator", "management", "lock", "album", "settings", "vip"], 1):
    adb("shell", "am", "force-stop", PACKAGE)
    result=adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "screen", page)
    print(page, result.stdout.decode(errors="replace"), flush=True)
    time.sleep(3)
    image=adb("exec-out", "screencap", "-p").stdout
    assert image[:8] == b"\x89PNG\r\n\x1a\n", "Invalid screenshot"
    target=ROOT/f"{number:02d}-{page}.png"
    target.write_bytes(image)
    results.append({"page":page,"file":target.name,"size":struct.unpack(">II",image[16:24])})
(ROOT/"screenshots.json").write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding="utf-8")
print("Captured", len(results), "screenshots", flush=True)
