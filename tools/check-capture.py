#!/usr/bin/env python3
"""Audit every encoded source-frame gap; export timing never substitutes for source timing."""
import argparse, json, subprocess
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('video',type=Path)
parser.add_argument('--start',type=float,default=0)
parser.add_argument('--end',type=float)
parser.add_argument('--limit-ms',type=float,default=50)
args=parser.parse_args()
probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','packet=pts_time,duration_time:stream=duration','-of','json',str(args.video)]))
packets=probe['packets']
times=sorted(float(p['pts_time']) for p in packets)
end=args.end if args.end is not None else max(float(probe['streams'][0]['duration']),max(float(p['pts_time'])+float(p.get('duration_time',0)) for p in packets))
times.append(end)
intervals=[(a,b,(b-a)*1000) for a,b in zip(times,times[1:]) if b>args.start and a<end]
gaps=sorted(g for _,_,g in intervals)
def percentile(q):return round(gaps[min(len(gaps)-1,int(len(gaps)*q))],3)
report={'video':args.video.name,'checkedStart':args.start,'checkedEnd':end,'sourceFrames':len(packets),'intervalsChecked':len(gaps),'p50Ms':percentile(.5),'p95Ms':percentile(.95),'p99Ms':percentile(.99),'maxMs':round(max(gaps),3),'stalls':[{'at':round(a,3),'gapMs':round(g,3)} for a,_,g in intervals if g>args.limit_ms+.01]}
report['passed']=not report['stalls']
print(json.dumps(report,indent=2))
args.video.with_suffix('.gaps.json').write_text(json.dumps(report,indent=2)+'\n')
raise SystemExit(0 if report['passed'] else 1)
