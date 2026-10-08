"""Opt-in local engine acceptance test. Uses native client interaction packets, never awards progress."""
import json, math, time
from pathlib import Path
from aero_control import command, inspect, read

OUT=Path(__file__).resolve().parents[1]/'docs/evidence/v2-native-verification.json'
evidence={'checks':[],'routes':[]}
def check(name, condition, detail=None):
    if not condition: raise AssertionError(name+': '+str(detail))
    evidence['checks'].append({'check':name,'passed':True,'detail':detail});print('PASS',name,flush=True)
    OUT.write_text(json.dumps(evidence,indent=2))
def wait(pred,seconds=45):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        s=read()
        if pred(s):return s
        time.sleep(.2)
    raise TimeoutError(str(read()))
def sequence(steps):
    command('sequence',steps=steps,hideHud=False)
    return wait(lambda s:not s.get('directing'),sum(x.get('seconds',.25) for x in steps)+8)
def person(data):return next(iter(data['book']['players'].values()))
def move(pos,yaw=0,pitch=0):return command('move',pos=pos,yaw=yaw,pitch=pitch)

wait(lambda s:s.get('here') and not s.get('active') and s.get('serial')==0,60)
command('resolution',width=960,height=540,distance=6)
command('select',slot=8)
initial=inspect();check('Isolated retreat and starter kit',read()['dimension']=='aero:retreat' and person(initial)['credits']==8 and initial['inventory'][3]['count']==32)
for theme in range(3):
    command('request',value='home');command('request',value='route:'+str(theme));t=time.monotonic()
    wait(lambda s:s.get('sector') and not s.get('building'))
    data=inspect();rooms=data['rooms'];before=person(data)['credits'];route={'theme':theme,'seed':data['book']['seed'],'signatureRooms':[{k:r[k] for k in ('id','kind','links')} for r in rooms],'generationSeconds':round(time.monotonic()-t,2)}
    command('shot',name=f'v2-route-{theme}.png')
    # Cross a real east door including the trim and corridor using ordinary forward movement.
    east=next(r for r in rooms if r['links']&2);x,y,z=east['origin'];move([x+19.5,y+1,z+11.5],-90)
    walked=sequence([{'seconds':2.8,'yaw':-90,'pitch':0,'forward':True}])
    check(f'Theme {theme} native corridor crossing',walked['x']>x+28,walked['x'])
    for r in rooms:
        if r['kind']!='FLOW':continue
        x,y,z=r['origin'];move([x+11.5,y+1,z+11.5],0,75)
        steps=[]
        for node in r['nodes']:
            for _ in range((node['solution']-node['rotation'])%4):steps.append({'seconds':.4,'click':node['pos']})
        sequence(steps);data=inspect()
        check(f'Theme {theme} flow {r["id"]} server award',r['id'] in data['book']['solved'])
        command('interact',pos=r['nodes'][0]['pos']);after=inspect()
        check(f'Theme {theme} flow {r["id"]} cannot pay twice',person(data)['credits']==person(after)['credits'])
    check(f'Theme {theme} three flows pay twelve',person(inspect())['credits']==before+12)
    for r in rooms:
        if r['kind']!='SPECIMEN':continue
        x,y,z=r['origin'];sample=[x+18,y+(5 if theme==1 else 2),z+17]
        move([x+16.5,y+(5 if theme==1 else 1),z+17.5],-90,20)
        command('interact',pos=sample)
    data=inspect();check(f'Theme {theme} optional specimens gathered',len(data['book']['sampled'])==2)
    command('request',value='map');check(f'Theme {theme} native field map',read()['screen']=='AeroScreen');command('play')
    x,y,z=rooms[8]['origin'];move([x+18.5,y+1,z+20.5],-90,0);command('interact',pos=[x+20,y+2,z+20]);data=inspect()
    check(f'Theme {theme} complete and return',data['book']['completed'] and person(data)['journeys']==theme+1 and not read()['sector'])
    route['creditsAfter']=person(data)['credits'];route['discoveries']=person(data)['discoveries'];evidence['routes'].append(route);OUT.write_text(json.dumps(evidence,indent=2));command('play')
# Place a real starter block, mine it with the starter pick, then buy a bundle and expand.
move([52.5,181,127.5],0,35);command('select',slot=3);command('interact',pos=[52,180,129]);data=inspect([[52,181,129]])
check('Native garden placement', 'aero:porcelain' in data['probes'][0]['block'])
count=data['inventory'][3]['count'];command('select',slot=2);sequence([{'seconds':1.5,'mine':[52,181,129]}]);move([52.5,181,129.5]);time.sleep(.6);data=inspect([[52,181,129]])
check('Native garden mining and self drop','minecraft:air' in data['probes'][0]['block'] and sum(i['count'] for i in data['inventory'] if i['id']=='aero:porcelain')==count+1)
command('select',slot=3);move([52.5,181,127.5],0,35);command('interact',pos=[52,180,129])
move([46.5,181,119.5],0,0);prior=person(inspect())['credits'];command('request',value='material:white');data=inspect();check('Workshop bundle and price',person(data)['credits']==prior-2 and sum(i['count'] for i in data['inventory'] if i['id']=='aero:porcelain')==63)
command('play');command('request',value='expand');wait(lambda s:not s.get('building'));data=inspect([[52,181,129],[70,180,140]])
check('Expansion preserves old construction',data['book']['gardenSize']==28 and 'aero:porcelain' in data['probes'][0]['block'] and 'grass_block' in data['probes'][1]['block'])
command('request',value='home');before=person(inspect());command('request',value='leave');s=read();check('Leave returns original world and mode',s['dimension']=='minecraft:overworld' and inspect()['mode']==before['returnMode'])
command('request',value='enter');check('Reentry does not duplicate kit',sum(i['count'] for i in inspect()['inventory'] if i['id']=='aero:field_guide')==1)
command('play');evidence['finalBook']=inspect()['book'];evidence['saveRejoinPending']=True;OUT.write_text(json.dumps(evidence,indent=2));print('Native acceptance sequence complete',flush=True)
