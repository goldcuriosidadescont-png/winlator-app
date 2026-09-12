#!/usr/bin/env python3
from __future__ import annotations
import argparse, re, shutil, subprocess
from pathlib import Path

def fail(msg): raise SystemExit('[FAIL] '+msg)
def elf_machine(p:Path):
    tool=shutil.which('readelf')
    if not tool:return None
    s=subprocess.run([tool,'-h',str(p)],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True).stdout
    m=re.search(r'Machine:\s*(.+)',s);return m.group(1).strip() if m else None

def main():
    base=Path(__file__).resolve().parent;ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,default=base/'source'/'Cerberus');ap.add_argument('--sdk',type=Path);a=ap.parse_args();src=a.source.resolve();g=(src/'app'/'build.gradle').read_text(encoding='utf-8',errors='replace')
    m=re.search(r'ndkVersion\s+[\'\"]([^\'\"]+)',g);ndkver=m.group(1) if m else None
    sdk=a.sdk.resolve() if a.sdk else None
    if not sdk:
        lp=src/'local.properties'
        if lp.is_file():
            for line in lp.read_text(errors='replace').splitlines():
                if line.strip().startswith('sdk.dir='):
                    v=line.split('=',1)[1].replace('\\:',';COLON;').replace('\\','/').replace(';COLON;',':');sdk=Path(v).expanduser().resolve();break
    if not sdk or not sdk.is_dir():fail('Android SDK não localizado; rode CONFIGURE-CERBERUS-SDK.py primeiro.')
    ndk=sdk/'ndk'/ndkver if ndkver else None
    if not ndk or not ndk.is_dir():fail(f'NDK {ndkver} não localizado em {sdk}/ndk.')
    candidates=[]
    for p in ndk.rglob('libomp.so'):
        low=p.as_posix().lower()
        score=0
        if 'aarch64' in low or 'arm64' in low:score+=100
        if '/lib/linux/' in low:score+=30
        if 'x86_64' in low or '/x86/' in low:score-=150
        candidates.append((score,p))
    if not candidates:fail('libomp.so não existe no NDK instalado.')
    candidates.sort(key=lambda x:(x[0],-len(x[1].as_posix())),reverse=True);libomp=candidates[0][1]
    machine=elf_machine(libomp)
    if machine and 'AArch64' not in machine:fail(f'libomp candidato não é AArch64: {libomp} ({machine})')
    dst=src/'app'/'src'/'main'/'jniLibs'/'arm64-v8a'/'libomp.so';dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(libomp,dst)
    if dst.stat().st_size<10000:fail('libomp copiado parece inválido/pequeno.')
    print('[PASS] libomp.so sincronizado do NDK compatível:');print(' source=',libomp);print(' output=',dst);print(' bytes=',dst.stat().st_size)
if __name__=='__main__':main()
