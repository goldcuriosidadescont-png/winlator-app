#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re, shutil, subprocess
from datetime import datetime, timezone
from pathlib import Path
TOKENS=('bionic','arm64ec','fexcore','fex-core','wowbox64','wow-box64','container_pattern_bionic','container-pattern-bionic')
ALLOWED={'.tzst','.txz','.xz','.zst','.wcp','.so','.dll','.json','.conf','.rc','.txt'}
COMPRESSED={'.txz','.xz','.tzst','.zst'}
def sha256(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
def lfs_pointer(p):
    if p.stat().st_size>4096:return False
    try:return p.read_text(errors='ignore').splitlines()[0]=='version https://git-lfs.github.com/spec/v1'
    except:return False
def run(cmd,cwd):
    print('[RUN]',' '.join(map(str,cmd)));p=subprocess.run(cmd,cwd=cwd)
    if p.returncode:raise SystemExit(f'[FAIL] comando falhou ({p.returncode})')
def copy(src,dst):dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst);return dst
def choose(cands,arch=None):
    if not cands:return None
    def sc(p):
        low=p.as_posix().lower();name=p.name.lower();s=0
        if 'bionic' in low:s+=80
        if 'glibc' in low:s-=120
        if name.startswith(('imagefs','rootfs')):s+=60
        if 'container' in low and 'pattern' in low:s+=50
        if arch=='arm64ec':s+=120 if 'arm64ec' in low else -20
        if arch=='x86_64':s+=120 if re.search(r'x86_64|x64',low) else (-20 if 'arm64ec' in low else 0)
        return (s,p.stat().st_size)
    return max(cands,key=sc)
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--donor',type=Path,required=True);ap.add_argument('--source',type=Path,required=True);a=ap.parse_args();donor=a.donor.resolve();src=a.source.resolve();assets=donor/'app'/'src'/'main'/'assets'
    if not assets.is_dir():raise SystemExit(f'[FAIL] donor sem assets: {assets}')
    dest=src/'app'/'src'/'main'/'assets'/'cerberus-bionic';shutil.rmtree(dest,ignore_errors=True);dest.mkdir(parents=True)
    selected=[]
    for f in assets.rglob('*'):
        if not f.is_file() or f.suffix.lower() not in ALLOWED:continue
        rel=f.relative_to(assets).as_posix().lower();leaf=f.name.lower();ok=leaf in {'container_pattern_x86_64.tzst','container_pattern_arm64ec.tzst'} or any(t in rel for t in TOKENS) or bool(re.match(r'^(imagefs|rootfs)([_\.-].*)?\.(tzst|txz|zst|xz)$',leaf))
        if ok:selected.append(f)
    if not selected:raise SystemExit('[FAIL] nenhum payload Bionic seletivo encontrado; importação da base inteira foi bloqueada.')
    pointers=[p for p in selected if lfs_pointer(p)]
    if pointers:
        check=subprocess.run(['git','lfs','version'],cwd=donor,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if check.returncode:raise SystemExit('[FAIL] Git-LFS necessário para os payloads selecionados.')
        inc=','.join(p.relative_to(donor).as_posix() for p in pointers);run(['git','lfs','pull',f'--include={inc}','--exclude='],donor)
        bad=[p for p in pointers if lfs_pointer(p)]
        if bad:raise SystemExit('[FAIL] Git-LFS não resolveu: '+', '.join(x.name for x in bad))
    entries=[]
    for f in selected:
        rel=f.relative_to(assets);out=copy(f,dest/'donor'/rel);entries.append({'source':rel.as_posix(),'bytes':out.stat().st_size,'sha256':sha256(out)})
    compressed=[p for p in selected if p.suffix.lower() in COMPRESSED]
    images=[p for p in compressed if p.name.lower().startswith(('imagefs','rootfs'))];image=choose(images)
    if not image:raise SystemExit('[FAIL] imagefs/rootfs Bionic não localizado.')
    ext='txz' if image.suffix.lower() in {'.txz','.xz'} else 'tzst';copy(image,dest/f'imagefs.{ext}')
    patterns=[p for p in compressed if re.search(r'container.*pattern|pattern.*container',p.as_posix().lower())]
    arm_specific=[p for p in patterns if 'arm64ec' in p.as_posix().lower()];x86_specific=[p for p in patterns if re.search(r'x86_64|x64',p.as_posix().lower())]
    generic=[p for p in patterns if p not in arm_specific and p not in x86_specific]
    arm=choose(arm_specific,'arm64ec') or choose(generic,'arm64ec');x86=choose(x86_specific,'x86_64') or choose(generic,'x86_64')
    if not arm or not x86:raise SystemExit('[FAIL] container pattern Bionic x86_64/ARM64EC não localizado.')
    for p,arch in ((x86,'x86_64'),(arm,'arm64ec')):
        e='txz' if p.suffix.lower() in {'.txz','.xz'} else 'tzst';copy(p,dest/f'container-pattern-{arch}.{e}')
    wcps=[p for p in selected if p.suffix.lower()=='.wcp'];fex=choose([p for p in wcps if 'fex' in p.as_posix().lower()]);wow=choose([p for p in wcps if 'wowbox' in p.as_posix().lower()])
    if fex:copy(fex,dest/'components'/'fexcore.wcp')
    if wow:copy(wow,dest/'components'/'wowbox64.wcp')
    refout=Path(__file__).resolve().parent/'work'/'donor-code-reference';shutil.rmtree(refout,ignore_errors=True);refout.mkdir(parents=True,exist_ok=True);refs=[]
    java=donor/'app'/'src'/'main'/'java';names={'BionicProgramLauncherComponent.java','FEXCoreManager.java','FEXCorePreset.java','ContentsManager.java','ContentProfile.java','WineInfo.java'}
    if java.is_dir():
        for f in java.rglob('*.java'):
            if f.name in names:rel=f.relative_to(donor);copy(f,refout/('__'.join(rel.parts)));refs.append(rel.as_posix())
    head=subprocess.run(['git','rev-parse','HEAD'],cwd=donor,stdout=subprocess.PIPE,text=True).stdout.strip() if (donor/'.git').exists() else 'unknown'
    manifest={'schema':3,'policy':'Cerberus base; donor payload/reference only','donor':'Succubussix/winlator-bionic-glibc','donorCommit':head,'generatedUtc':datetime.now(timezone.utc).isoformat(),'assets':entries,'referenceCode':refs,'canonicalImage':f'imagefs.{ext}','x86PatternSource':x86.relative_to(assets).as_posix(),'armPatternSource':arm.relative_to(assets).as_posix(),'sharedPattern':x86.resolve()==arm.resolve(),'fexWcp':bool(fex),'wowWcp':bool(wow)}
    (dest/'manifest.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding='utf-8')
    print(f'[PASS] payload Bionic seletivo importado: {len(selected)} arquivo(s).');print('[PASS] código/UI do donor NÃO foi importado em app/src/main/java.')
if __name__=='__main__':main()
