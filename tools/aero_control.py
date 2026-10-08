#!/usr/bin/env python3
"""Drive opt-in native Minecraft QA. Requires runClient -Pdemo; absent in ordinary play."""
import json
import pathlib
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN = ROOT / "run"

def read(name="aero-state.json"):
    for _ in range(20):
        try:
            return json.loads((RUN / name).read_text())
        except (FileNotFoundError, json.JSONDecodeError):
            time.sleep(.1)
    raise RuntimeError(f"No readable {name}")

def command(action, timeout=12, **values):
    token = str(time.time_ns())
    message = {"id": token, "action": action, **values}
    temporary = RUN / "control.tmp"
    temporary.write_text(json.dumps(message))
    temporary.replace(RUN / "control.json")
    if action == "close":
        return message
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = read()
        if state.get("command") == token:
            return state
        time.sleep(.15)
    raise TimeoutError(f"Native client did not acknowledge {action}")

def inspect(positions=None):
    path = RUN / "aero-inspection.json"
    previous = path.stat().st_mtime_ns if path.exists() else 0
    command("inspect", **({"positions": positions} if positions else {}))
    for _ in range(30):
        if path.exists() and path.stat().st_mtime_ns > previous:
            return read(path.name)
        time.sleep(.1)
    raise TimeoutError("Server inspection did not complete")

if __name__ == "__main__":
    request = json.loads(sys.argv[1])
    action = request.pop("action")
    print(json.dumps(inspect(**request) if action == "inspect" else command(action, **request), indent=2))
