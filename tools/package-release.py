#!/usr/bin/env python3
"""Package the native jar and a verified official-CDN Modrinth install manifest."""
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'
VERSION = '2.0.1'

def sha(path, kind):
    return hashlib.new(kind, path.read_bytes()).hexdigest()

def main():
    DIST.mkdir(exist_ok=True)
    mod = ROOT / f'build/libs/frutiger-aero-{VERSION}.jar'
    shader = DIST / 'Aero-MakeUp-shader.zip'
    with ZipFile(mod) as jar:
        metadata = json.loads(jar.read('fabric.mod.json'))
        assert metadata['version'] == VERSION and 'dev/aero/AeroGame.class' in jar.namelist()
    with ZipFile(shader) as archive:
        assert {'LICENSE','COPYING','Credits','AERO-CHANGES.md','shaders/shaders.properties'} <= set(archive.namelist())
        assert archive.testzip() is None
    shutil.copyfile(mod, DIST / mod.name)
    shutil.copyfile(ROOT / f'build/libs/frutiger-aero-{VERSION}-sources.jar', DIST / f'frutiger-aero-{VERSION}-sources.jar')
    files=[]
    api=json.loads((ROOT/'dependencies/fabric-api-lock.json').read_text())['file']
    files.append({'path':'mods/'+api['filename'],'hashes':api['hashes'],'downloads':[api['url']],'fileSize':api['size'],'env':{'client':'required','server':'required'}})
    for entry in json.loads((ROOT/'dependencies/modrinth-lock.json').read_text()):
        path=ROOT/'dependencies'/entry['file']
        assert sha(path,'sha512') == entry['sha512']
        files.append({'path':'mods/'+path.name,'hashes':{'sha1':sha(path,'sha1'),'sha512':entry['sha512']},'downloads':[entry['url']],'fileSize':path.stat().st_size,'env':{'client':'required','server':'unsupported'}})
    index={'formatVersion':1,'game':'minecraft','versionId':VERSION,'name':'Frutiger Aero','summary':'Quiet expeditions, physical flow puzzles and a garden to keep building.','files':files,'dependencies':{'minecraft':'1.21.1','fabric-loader':'0.16.5'}}
    pack=DIST/f'Frutiger-Aero-{VERSION}.mrpack'
    with ZipFile(pack,'w',ZIP_DEFLATED) as archive:
        archive.writestr('modrinth.index.json',json.dumps(index,indent=2))
        archive.write(mod,'overrides/mods/'+mod.name)
        archive.write(shader,'overrides/shaderpacks/'+shader.name)
        archive.writestr('client-overrides/config/iris.properties','enableShaders=true\nshaderPack=Aero-MakeUp-shader.zip\nmaxShadowRenderDistance=8\n')
        archive.writestr('client-overrides/options.txt','renderDistance:8\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\n')
    bundle=DIST/f'Frutiger-Aero-{VERSION}.zip'
    with ZipFile(bundle,'w',ZIP_DEFLATED) as archive:
        archive.write(mod,'mods/'+mod.name)
        archive.write(shader,'shaderpacks/'+shader.name)
        archive.writestr('INSTALL.md', '# Frutiger Aero 2.0.1\n\nRead [installation instructions](docs/INSTALL.md) before copying the mod and shader into a Minecraft Java 1.21.1 Fabric instance. The importable .mrpack on the GitHub release also downloads the required dependencies.\n')
        for name in ('README.md','media/preview.jpg','docs/INSTALL.md','docs/CREDITS.md','docs/licenses/GPL-3.0.txt','docs/licenses/LGPL-3.0.txt','docs/research/experience.md','docs/VALIDATION.md','docs/recording-validation.json','dependencies/modrinth-lock.json','dependencies/fabric-api-lock.json','media/music/CREDITS.md'):
            archive.write(ROOT/name,name)
        archive.write(ROOT/'LICENSE','LICENSE')
    artifacts=[DIST/mod.name,DIST/f'frutiger-aero-{VERSION}-sources.jar',shader,pack,bundle]
    checksums=''.join(sha(path,'sha256')+'  '+path.name+'\n' for path in artifacts)
    (DIST/'SHA256SUMS.txt').write_text(checksums)
    print(checksums,end='')

if __name__=='__main__':main()
