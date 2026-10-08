#!/usr/bin/env python3
"""Record native gameplay after preparation. Recorded steps only use normal input and interaction."""
import json, subprocess, sys, time
from pathlib import Path
from aero_control import command, read

ROOT=Path(__file__).resolve().parents[1]
spec=json.loads(Path(sys.argv[1]).read_text())
target=ROOT/'docs/evidence/shots'/spec['name']
target.parent.mkdir(exist_ok=True)
for suffix in (".ready", ".stop"):
    Path(str(target)+suffix).unlink(missing_ok=True)
command('move',pos=spec['pos'],yaw=spec['yaw'],pitch=spec['pitch'])
command('hide')
if 'slot' in spec:command('select',slot=spec['slot'])
time.sleep(4)
pid=next(line.split()[0] for line in subprocess.check_output(['/Library/Java/JavaVirtualMachines/jdk-22.jdk/Contents/Home/bin/jps','-l'],text=True).splitlines() if 'devlaunchinjector.Main' in line)
duration=sum(step['seconds'] for step in spec['steps'])
with (target.with_suffix('.log')).open('w') as log:
    proc=subprocess.Popen([str(ROOT/'tools/record-minecraft'),pid,str(target),str(duration+2.6)],stdout=log,stderr=log)
    ready=Path(str(target)+'.ready');end=time.monotonic()+15
    while not ready.exists():
        if proc.poll() is not None or time.monotonic()>end:raise RuntimeError('Recorder did not start')
        time.sleep(.05)
    time.sleep(.6)
    command('sequence',steps=spec['steps'],hideHud=True)
    samples=[];end=time.monotonic()+duration+4
    while time.monotonic()<end:
        state=read();samples.append(state)
        if not state.get('directing'):break
        time.sleep(.3)
    proc.wait(timeout=15)
    if proc.returncode:raise RuntimeError('Recorder failed')
target.with_suffix('.json').write_text(json.dumps({'spec':spec,'samples':samples,'source':'native ScreenCaptureKit window and app audio'},indent=2))
print(target)
