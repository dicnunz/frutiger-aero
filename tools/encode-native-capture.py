#!/usr/bin/env python3
"""Encode actual framebuffer images at their recorded times and synchronize native app audio."""
import argparse,json,math,subprocess
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('frames',type=Path)
parser.add_argument('audio',type=Path)
parser.add_argument('output',type=Path)
args=parser.parse_args()
data=json.loads((args.frames/'frames.json').read_text())
if not data.get('success') or data.get('overflow') or data.get('errors'):raise RuntimeError(f"Framebuffer capture failed: {data.get('overflow')} dropped, {len(data.get('errors',[]))} errors")
frames=data['frames'];assert len(frames)>1
first=frames[0]['timestampNs'];end=data['stopNs'];duration=(end-first)/1e9
intervals=[(b['timestampNs']-a['timestampNs'])/1e6 for a,b in zip(frames,frames[1:])]
intervals.append((end-frames[-1]['timestampNs'])/1e6)
if max(intervals)>50:raise RuntimeError(f'Framebuffer source stalled {max(intervals):.1f} ms')
audit=json.loads(Path(str(args.audio)+'.capture.json').read_text())
first_sc=next(f['pts'] for f in audit['frames'] if f['status']==0)
packets=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','packet=pts_time','-of','json',str(args.audio)]))['packets']
first_encoded=min(float(p['pts_time']) for p in packets)
audio_offset=first/1e9-(first_sc-first_encoded)
if audio_offset<0:raise RuntimeError('Audio clock began after native framebuffer capture')
audio_probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','a:0','-show_entries','stream=sample_rate:packet=pts_time,duration_time','-of','json',str(args.audio)]))
if not audio_probe.get('streams') or not audio_probe.get('packets'):raise RuntimeError('App audio recording has no audio packets')
sample_rate=int(audio_probe['streams'][0]['sample_rate'])
if sample_rate<=0:raise RuntimeError('App audio recording has an invalid sample rate')
audio_packets=[]
for packet in audio_probe['packets']:
 start=float(packet['pts_time']);length=float(packet['duration_time'])
 if not math.isfinite(start) or not math.isfinite(length) or length<=0:raise RuntimeError('App audio recording has invalid packet timing')
 audio_packets.append((start,start+length))
audio_packets.sort()
audio_start=audio_packets[0][0];audio_end=max(stop for _,stop in audio_packets)
audio_required_end=audio_offset+duration
# Native AAC has 1024 samples per packet. Padding may cover one boundary packet only.
audio_rounding=min(max(stop-start for start,stop in audio_packets),1024/sample_rate)+1/sample_rate
audio_leading=max(0,audio_start-audio_offset);audio_trailing=max(0,audio_required_end-audio_end)
if audio_leading>audio_rounding or audio_trailing>audio_rounding:
 raise RuntimeError(f'App audio does not cover native capture: missing {audio_leading:.4f}s at start, {audio_trailing:.4f}s at end; packet allowance {audio_rounding:.4f}s')
audio_gap=max([0]+[max(0,min(next_start,audio_required_end)-max(stop,audio_offset)) for (_,stop),(next_start,_) in zip(audio_packets,audio_packets[1:])])
if audio_gap>1/sample_rate+.000002:raise RuntimeError(f'App audio has a {audio_gap:.4f}s packet gap during native capture')
audio_coverage={'start':audio_start,'end':audio_end,'requiredStart':audio_offset,'requiredEnd':audio_required_end,'packetRoundingAllowance':audio_rounding,'missingStart':audio_leading,'missingEnd':audio_trailing,'maxInternalGap':audio_gap}
concat=args.frames/'frames.ffconcat'
lines=['ffconcat version 1.0']
for i,f in enumerate(frames):
 image=(args.frames/f['file']).resolve()
 next_time=frames[i+1]['timestampNs'] if i+1<len(frames) else end
 lines += ["file '"+str(image).replace("'","'\\''")+"'",'option framerate 1000',f'duration {(next_time-f["timestampNs"])/1e9:.9f}']
lines += ["file '"+str((args.frames/frames[-1]['file']).resolve()).replace("'","'\\''")+"'",'option framerate 1000']
concat.write_text('\n'.join(lines)+'\n')
cmd=['ffmpeg','-y','-hide_banner','-loglevel','warning','-threads','1','-f','concat','-safe','0','-i',str(concat),'-threads','1','-i',str(args.audio),'-filter_complex',f'[1:a]atrim=start={audio_offset}:duration={duration},asetpts=PTS-STARTPTS,apad,atrim=duration={duration}[audio]','-map','0:v:0','-map','[audio]','-c:v','libx264','-preset','fast','-crf','16','-threads','2','-pix_fmt','yuv420p','-fps_mode','vfr','-enc_time_base','1:1000','-video_track_timescale','60000','-c:a','aac','-b:a','192k','-ar','48000','-movflags','+faststart','-t',str(duration),str(args.output)]
subprocess.run(cmd,check=True)
encoded=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','packet=pts_time','-of','json',str(args.output)]))['packets']
actual=sorted(float(p['pts_time']) for p in encoded)
expected=[(f['timestampNs']-first)/1e9 for f in frames]
if len(actual) not in (len(expected),len(expected)+1):raise RuntimeError('Encoded frame count differs from captured frames')
max_clock_error=max(abs(a-b) for a,b in zip(actual,expected))
if max_clock_error>.002:raise RuntimeError(f'Encoding changed capture timing by {max_clock_error:.4f} seconds')
report={'source':'Minecraft native framebuffer images','frames':len(frames),'firstFrameNs':first,'duration':duration,'audioOffset':audio_offset,'audioCoverage':audio_coverage,'sourceMaxGapMs':max(intervals),'maxClockErrorMs':max_clock_error*1000,'speed':1,'generatedFrames':0,'output':str(args.output)}
args.output.with_suffix('.source.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
