#!/usr/bin/env python3
from __future__ import annotations
import argparse, os, re, shutil, subprocess, sys
from pathlib import Path

def fail(msg:str)->None: raise SystemExit('[FAIL] '+msg)
def run(cmd, cwd=None):
    print('[RUN]', ' '.join(map(str,cmd)))
    if os.name=='nt' and str(cmd[0]).lower().endswith(('.bat','.cmd')): cmd=['cmd','/c',*map(str,cmd)]
    p=subprocess.run(cmd,cwd=cwd)
    if p.returncode: fail(f'comando falhou ({p.returncode}): {cmd[0]}')

def sdkmanager(sdk:Path):
    base=sdk/'cmdline-tools'
    c=[base/'latest'/'bin'/('sdkmanager.bat' if os.name=='nt' else 'sdkmanager')]
    if base.is_dir(): c += [d/'bin'/('sdkmanager.bat' if os.name=='nt' else 'sdkmanager') for d in sorted(base.iterdir(), reverse=True) if d.is_dir()]
    return next((x for x in c if x.is_file()),None)

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--source',type=Path,default=Path(__file__).resolve().parent/'source'/'Cerberus'); ap.add_argument('--sdk',type=Path); ap.add_argument('--no-install',action='store_true'); a=ap.parse_args()
    src=a.source.resolve(); gradle=src/'app'/'build.gradle'
    if not gradle.is_file(): fail(f'app/build.gradle ausente: {gradle}')
    g=gradle.read_text(encoding='utf-8',errors='replace')
    def match(rx,default=None):
        m=re.search(rx,g,re.M|re.S); return m.group(1) if m else default
    compile_sdk=match(r'compileSdk(?:Version)?\s*[= ]\s*(\d+)','35')
    build_tools=match(r'buildToolsVersion\s+[\'\"]([^\'\"]+)[\'\"]')
    ndk=match(r'ndkVersion\s+[\'\"]([^\'\"]+)[\'\"]')
    cmake=match(r'externalNativeBuild\s*\{.*?cmake\s*\{.*?version\s+[\'\"]([^\'\"]+)[\'\"]','3.22.1')
    java=shutil.which('java')
    if not java: fail('Java não encontrado; use JDK 17+.')
    ver=subprocess.run([java,'-version'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True).stdout
    m=re.search(r'version\s+"(\d+)',ver)
    if not m or int(m.group(1))<17: fail('JDK 17+ obrigatório. Detectado: '+ver.splitlines()[0])
    print('[JAVA]',ver.splitlines()[0]); print(f'[REQ] compileSdk={compile_sdk} buildTools={build_tools} ndk={ndk} cmake={cmake}')

    candidates=[]
    if a.sdk: candidates.append(a.sdk)
    for key in ('ANDROID_SDK_ROOT','ANDROID_HOME'):
        if os.environ.get(key): candidates.append(Path(os.environ[key]))
    if os.name=='nt' and os.environ.get('LOCALAPPDATA'): candidates.append(Path(os.environ['LOCALAPPDATA'])/'Android'/'Sdk')
    lp=src/'local.properties'
    if lp.is_file():
        for line in lp.read_text(errors='replace').splitlines():
            if line.strip().startswith('sdk.dir='):
                value=line.split('=',1)[1].replace('\\:',';COLON;').replace('\\','/').replace(';COLON;',':')
                candidates.append(Path(value)); break
    sdk=next((p.expanduser().resolve() for p in candidates if p and p.expanduser().is_dir()),None)
    if not sdk: fail('Android SDK não encontrado. Defina ANDROID_SDK_ROOT ou use --sdk.')
    print('[SDK]',sdk)
    lp.write_text('sdk.dir='+sdk.as_posix()+'\n',encoding='ascii')
    os.environ['ANDROID_SDK_ROOT']=str(sdk); os.environ['ANDROID_HOME']=str(sdk)
    req=[(f'platforms;android-{compile_sdk}',sdk/'platforms'/f'android-{compile_sdk}'/'android.jar')]
    if build_tools:req.append((f'build-tools;{build_tools}',sdk/'build-tools'/build_tools))
    if ndk:req.append((f'ndk;{ndk}',sdk/'ndk'/ndk))
    if cmake:req.append((f'cmake;{cmake}',sdk/'cmake'/cmake))
    mgr=sdkmanager(sdk)
    for pkg,probe in req:
        if probe.exists(): print('[PASS]',pkg); continue
        if a.no_install or not mgr: fail(f'Falta {pkg}; sdkmanager não localizado/instalação desabilitada.')
        run([str(mgr),f'--sdk_root={sdk}','--install',pkg])
        if not probe.exists(): fail(f'{pkg} continuou ausente após sdkmanager.')
        print('[PASS]',pkg)
    print('[PASS] SDK/local.properties prontos.')
if __name__=='__main__': main()
