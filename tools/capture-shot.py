#!/usr/bin/env python3
"""Record native framebuffer gameplay and synchronized app audio; reject stalled takes."""
import json,subprocess,sys,time
from pathlib import Path
from aero_control import command,read

ROOT=Path(__file__).resolve().parents[1]
spec=json.loads(Path(sys.argv[1]).read_text())
target=ROOT/'docs/evidence/shots'/spec['name'];target.parent.mkdir(exist_ok=True)
audio=target.with_name(target.stem+'.audio.mp4')
for suffix in ('.ready','.stop','.capture.json'):
 Path(str(audio)+suffix).unlink(missing_ok=True)
command('move',pos=spec['pos'],yaw=spec['yaw'],pitch=spec['pitch'])
command('hide')
if 'slot' in spec:command('select',slot=spec['slot'])
command('focus');time.sleep(spec.get('warmup',6))
pid=next(line.split()[0] for line in subprocess.check_output(['/Library/Java/JavaVirtualMachines/jdk-22.jdk/Contents/Home/bin/jps','-l'],text=True).splitlines() if 'devlaunchinjector.Main' in line)
duration=sum(step['seconds'] for step in spec['steps'])
with target.with_suffix('.log').open('w') as log:
 proc=subprocess.Popen([str(ROOT/'tools/record-minecraft'),pid,str(audio),str(duration+8)],stdout=log,stderr=log)
 capture_requested=False;sequence_requested=False;take_complete=False
 try:
  ready=Path(str(audio)+'.ready');end=time.monotonic()+15
  while not ready.exists():
   if proc.poll() is not None or time.monotonic()>end:raise RuntimeError('App audio recorder did not start')
   time.sleep(.05)
  capture_requested=True
  started=command('captureStart',name=target.stem).get('capture',{})
  if not started.get('active') or started.get('name')!=target.stem:raise RuntimeError('Native framebuffer capture did not start')
  trace=ROOT/'run/aero-frame-times.json';trace.unlink(missing_ok=True)
  sequence_requested=True
  sequence=command('sequence',steps=spec['steps'],hideHud=True)
  samples=[];end=time.monotonic()+duration+4
  while time.monotonic()<end:
   if proc.poll() is not None:raise RuntimeError('App audio recorder ended before the sequence completed')
   state=read();samples.append(state)
   if not state.get('directing'):
    take_complete=True;break
   time.sleep(.3)
  if not take_complete:raise RuntimeError('Native gameplay sequence did not finish')
 finally:
  failed=sys.exc_info()[0] is not None;cleanup_errors=[]
  if capture_requested:
   try:command('captureStop')
   except Exception as error:cleanup_errors.append(f'Native capture shutdown failed: {error}')
  if sequence_requested and not take_complete:
   try:command('play')
   except Exception as error:cleanup_errors.append(f'Gameplay sequence shutdown failed: {error}')
  try:Path(str(audio)+'.stop').touch()
  except OSError as error:cleanup_errors.append(f'App audio stop signal failed: {error}')
  try:proc.wait(timeout=15)
  except subprocess.TimeoutExpired:
   cleanup_errors.append('App audio recorder did not finalize within 15 seconds')
   proc.terminate()
   try:proc.wait(timeout=3)
   except subprocess.TimeoutExpired:proc.kill();proc.wait()
  if cleanup_errors:
   if failed:print('\n'.join(cleanup_errors),file=sys.stderr)
   else:raise RuntimeError('; '.join(cleanup_errors))
 if proc.returncode:raise RuntimeError('App audio recorder failed')
if not trace.exists():raise RuntimeError('Missing complete native frame-time trace')
frame_times=json.loads(trace.read_text())
if frame_times.get('commandId')!=sequence['command']:raise RuntimeError('Native trace belongs to another sequence')
if frame_times.get('stopReason')!='completed':raise RuntimeError('Native gameplay sequence was interrupted')
target.with_suffix('.native-frames.json').write_text(json.dumps(frame_times,indent=2))
folder=ROOT/'run/aero-frames'/target.stem;end=time.monotonic()+15
while not (folder/'frames.json').exists():
 if time.monotonic()>end:raise RuntimeError('Native framebuffer writer did not finish')
 time.sleep(.1)
frames=json.loads((folder/'frames.json').read_text())
action_start=(frame_times['startNs']-frames['frames'][0]['timestampNs'])/1e9
meta={'spec':spec,'samples':samples,'source':'native Minecraft framebuffer and app audio','actionStart':action_start,'nativeFrameTrace':target.with_suffix('.native-frames.json').name}
target.with_suffix('.json').write_text(json.dumps(meta,indent=2))
if frame_times['countOver50Ms']:raise RuntimeError(f"Native render stalled: max {frame_times['maxMs']:.1f} ms")
command('resolution',width=640,height=360,distance=5,fps=15)
try:
 subprocess.run([sys.executable,str(ROOT/'tools/encode-native-capture.py'),str(folder),str(audio),str(target)],check=True)
finally:
 command('resolution',width=640,height=360,distance=5,fps=60)
subprocess.run([sys.executable,str(ROOT/'tools/check-capture.py'),str(target)],check=True)
for frame in frames['frames']:(folder/frame['file']).unlink()
meta['rawImagesRemovedAfterVerifiedEncoding']=True
target.with_suffix('.json').write_text(json.dumps(meta,indent=2))
print(target)
