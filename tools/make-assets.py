from pathlib import Path
import json, math, subprocess, wave, zipfile, shutil, re
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
assets=ROOT/'src/main/resources/assets/aero'
for name in ['textures/block','models/block','models/item','blockstates','lang','sounds']:
 (assets/name).mkdir(parents=True,exist_ok=True)

palette={
 'porcelain':(231,240,233),'aqua_tile':(74,187,203),'blue_tile':(27,105,184),
 'white_panel':(235,242,234),'lawn':(112,185,40),'foliage':(68,143,28),
 'silver':(173,204,206),'glow_panel':(219,253,233),'control':(28,142,195)
}
def texture(name,color):
 im=Image.new('RGB',(16,16),color);d=ImageDraw.Draw(im)
 if name in ['lawn','foliage']:
  for j,poly in enumerate([[(0,0),(9,0),(0,12)],[(9,0),(16,0),(16,11)],[(0,12),(9,0),(16,11)],[(0,12),(16,11),(16,16),(0,16)]]):
   delta=[8,-9,0,5][j];d.polygon(poly,fill=tuple(max(0,min(255,c+delta)) for c in color))
 elif name=='control':
  d.rectangle((0,0,15,15),fill=(214,232,226));d.rounded_rectangle((2,2,13,13),radius=5,fill=(9,66,98))
  for y in range(3,13):d.line((4,y,11,y),fill=(35+int(35*(12-y)/9),140+int(45*(12-y)/9),194+int(44*(12-y)/9)))
  d.arc((3,2,12,11),190,290,fill=(230,255,255));d.line((7,5,7,8),fill=(225,255,255))
 elif name=='silver':
  for x in range(16):d.line((x,0,x,15),fill=tuple(min(255,c+int(16*math.sin(x/16*math.pi*2))) for c in color))
 elif name=='glow_panel':
  d.rectangle((0,0,15,15),outline=(162,211,197));d.rectangle((2,2,13,13),outline=(237,255,245))
 else:
  grout=tuple(int(c*.86) for c in color);shine=tuple(min(255,c+6) for c in color)
  d.line((0,0,15,0),fill=grout);d.line((0,0,0,15),fill=grout)
  d.line((1,1,15,1),fill=shine);d.line((1,1,1,15),fill=shine)
  if name!='white_panel':d.polygon([(2,2),(13,2),(2,13)],fill=tuple(min(255,c+2) for c in color))
 im.save(assets/f'textures/block/{name}.png')
 (assets/f'models/block/{name}.json').write_text(json.dumps({'parent':'minecraft:block/cube_all','textures':{'all':f'aero:block/{name}'}}))
 (assets/f'models/item/{name}.json').write_text(json.dumps({'parent':f'aero:block/{name}'}))
 (assets/f'blockstates/{name}.json').write_text(json.dumps({'variants':{'':{'model':f'aero:block/{name}'}}}))
for name,color in palette.items():texture(name,color)
(assets/'lang/en_us.json').write_text(json.dumps({f'block.aero.{n}':n.replace('_',' ').title() for n in palette}))

# Clear glass, a faint blue Fresnel-like edge, no diagonal vanilla scratches.
glass=ROOT/'src/main/resources/assets/minecraft/textures/block';glass.mkdir(parents=True,exist_ok=True)
for name,col in [('glass',(218,240,239)),('light_blue_stained_glass',(157,229,241)),('cyan_stained_glass',(112,212,223))]:
 im=Image.new('RGBA',(16,16),(*col,23));d=ImageDraw.Draw(im)
 d.line((0,0,15,0),fill=(*col,36));d.line((0,0,0,15),fill=(*col,36));im.save(glass/f'{name}.png')

sr=22050;duration=60;n=sr*duration;t=np.arange(n,dtype=np.float64)/sr
rng=np.random.default_rng(8102005)
def bandnoise(lo,hi,level):
 freq=np.fft.rfftfreq(n,1/sr);s=np.fft.rfft(rng.normal(size=n))
 response=np.minimum(1,(freq/max(lo,1))**2)/(1+(freq/hi)**4);response[0]=0
 a=np.fft.irfft(s*response,n=n);return a/(np.std(a)+1e-9)*level
def reverb(a,mix):
 result=a.copy()
 for sec,amount in [(0.131,.21),(.241,.18),(.397,.13),(.613,.09),(.887,.07)]:result+=np.roll(a,int(sr*sec))*amount*mix
 return result
def save_sound(name,a):
 if a.ndim==1:a=np.column_stack([a,np.roll(a,211)])
 a=np.clip(a,-.94,.94);wav=ROOT/f'build/audio-{name}.wav';wav.parent.mkdir(exist_ok=True)
 with wave.open(str(wav),'wb') as f:
  f.setnchannels(2);f.setsampwidth(2);f.setframerate(sr);f.writeframes((a*32767).astype('<i2').tobytes())
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','vorbis','-strict','-2','-q:a','5',str(assets/f'sounds/{name}.ogg')],check=True)
 print(name,'rms',round(float(np.sqrt(np.mean(a*a))),4))
for room in ['atrium','pools','garden']:
 water=bandnoise(130,1900,.055 if room!='pools' else .075)*(0.85+.15*np.sin(2*np.pi*t/17))
 hum=np.sin(2*np.pi*62*t)*(.009 if room!='garden' else .003)
 pads=sum(np.sin(2*np.pi*f*t+.4*np.sin(2*np.pi*t/40))*.006 for f in [146.8324,220,277.1826,329.6276])
 tones=np.zeros(n)
 for at,f in [(5,587.33),(12,659.25),(26,440),(39,554.365),(47,880)]:
  dt=t-at;env=np.where(dt>=0,np.exp(-np.maximum(dt,0)/2.5)*(1-np.exp(-np.maximum(dt,0)*30)),0)
  tones+=env*(np.sin(2*np.pi*f*dt)+.18*np.sin(2*np.pi*f*2.01*dt))*.014
 a=reverb(water+hum+tones,1.0 if room=='pools' else .6)+pads
 if room=='garden':a+=bandnoise(40,850,.025)*(1+.18*np.sin(2*np.pi*t/12))
 # A short fade prevents startup clicks. A 60s loop keeps the quiet space continuous.
 a*=np.minimum(1,t/1.2)*np.minimum(1,(duration-t)/1.2)
 save_sound(room,a)
ct=np.arange(sr*4)/sr
chime=sum(np.sin(2*np.pi*f*ct)*np.exp(-ct*(1.8+j*.12))*.13 for j,f in enumerate([659.255,987.767,1318.51]))*(1-np.exp(-ct*60))
save_sound('chime',chime)
(assets/'sounds.json').write_text(json.dumps({n:{'sounds':[{'name':f'aero:{n}','stream':n!='chime'}]} for n in ['atrium','pools','garden','chime']},indent=2))

# Shader adaptation: retains upstream's LGPL source and credit, tuning only what this space needs.
shader=ROOT/'run/shaderpacks/Aero-MakeUp';shader.mkdir(parents=True,exist_ok=True)
shutil.copytree(ROOT/'dependencies/MakeUpUltraFast/shaders',shader/'shaders',dirs_exist_ok=True)
for f in ['LICENSE','Credits','README.md']:shutil.copy2(ROOT/'dependencies/MakeUpUltraFast'/f,shader/f)
cfg=shader/'shaders/lib/config.glsl';s=cfg.read_text()
settings={'COLOR_SCHEME':'99','WATER_ABSORPTION':'0.04','WATER_TURBULENCE':'0.5','WATER_COLOR_R':'0.03','WATER_COLOR_G':'0.18','WATER_COLOR_B':'0.20',
 'OMNI_TINT_CUSTOM':'0.78','AVOID_DARK_LEVEL':'0.060','LIGHT_DAY_COLOR_R':'0.9','LIGHT_DAY_COLOR_G':'0.94','LIGHT_DAY_COLOR_B':'0.92',
 'ZENITH_DAY_COLOR_R':'0.10','ZENITH_DAY_COLOR_G':'0.30','ZENITH_DAY_COLOR_B':'0.57',
 'HORIZON_DAY_COLOR_R':'0.61','HORIZON_DAY_COLOR_G':'0.91','HORIZON_DAY_COLOR_B':'1.10',
 'BLOCKLIGHT_TEMP':'3','SHADOW_DISTANCE_SLIDER':'0','SHADOW_QTY_SLIDER':'2','FOG_ADJUST':'2.0','V_CLOUDS':'1','VOL_LIGHT':'1','REFLECTION_SLIDER':'2'}
for key,value in settings.items():s=re.sub(r'(#define '+key+r'\s+)[^\s/]+',lambda m:m.group(1)+value,s)
cfg.write_text(s)
bp=shader/'shaders/block.properties';s=bp.read_text()
s=re.sub(r'(block\.10400\s*=)',r'\1 aero:porcelain aero:aqua_tile aero:blue_tile aero:white_panel aero:silver aero:glow_panel aero:control',s)
bp.write_text(s)
(shader/'AERO-CHANGES.md').write_text('Adapted for Frutiger Aero by GPT-6.1 Sol Max. Upstream: Javier Garduño, MakeUp Ultra Fast 9.5g, LGPL-3.0. https://github.com/javiergcim/MakeUpUltraFast\nChanges: cool daylight palette, transparent turquoise water, soft shadows, custom ceramic material tags. Original GLSL retained.\n')
(ROOT/'run/config/iris.properties').write_text('enableShaders=true\nshaderPack=Aero-MakeUp\nmaxShadowRenderDistance=8\n')
with zipfile.ZipFile(ROOT/'dist/Aero-MakeUp-shader.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in shader.rglob('*'):
  if p.is_file():z.write(p,p.relative_to(shader))
print('Assets and shader pack assembled')
