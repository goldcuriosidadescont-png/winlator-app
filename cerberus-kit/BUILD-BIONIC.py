#!/usr/bin/env python3
from __future__ import annotations
import argparse, os, subprocess, sys
from pathlib import Path
def run(cmd,cwd=None):
    print('[RUN]',' '.join(map(str,cmd)))
    if os.name=='nt' and str(cmd[0]).lower().endswith(('.bat','.cmd')):cmd=['cmd','/c',*map(str,cmd)]
    p=subprocess.run(cmd,cwd=cwd)
    if p.returncode:raise SystemExit(f'[FAIL] comando falhou ({p.returncode}): {cmd[0]}')
def main():
    base=Path(__file__).resolve().parent;ap=argparse.ArgumentParser();ap.add_argument('--sdk',type=Path);ap.add_argument('--no-clean',action='store_true');a=ap.parse_args();src=base/'source'/'Cerberus'
    cfg=[sys.executable,str(base/'CONFIGURE-CERBERUS-SDK.py'),'--source',str(src)]+(['--sdk',str(a.sdk)] if a.sdk else []);run(cfg);run([sys.executable,str(base/'SYNC-NDK-RUNTIME.py'),'--source',str(src)]+(['--sdk',str(a.sdk)] if a.sdk else []));run([sys.executable,str(base/'VERIFY-BIONIC.py'),'--require-payload'])
    gradle=src/('gradlew.bat' if os.name=='nt' else 'gradlew');
    if not gradle.is_file():raise SystemExit('[FAIL] Gradle wrapper ausente.')
    # ZIP extraction may discard POSIX executable permission.
    if os.name!='nt':gradle.chmod(gradle.stat().st_mode | 0o100)
    try:run([str(gradle),'--stop'],cwd=src)
    except SystemExit:print('[WARN] gradlew --stop falhou; prosseguindo.')
    tasks=[] if a.no_clean else ['clean'];tasks+=['assembleDebug','--no-parallel','--max-workers=2'];run([str(gradle),*tasks],cwd=src)
    run([sys.executable,str(base/'COLLECT-APK.py'),'--source',str(src),'--kit-root',str(base),'--output-name','Cerberus-Winlator-V8.2.0-BIONIC-FULL-INTEGRATION.apk'])
    print('[PASS] BUILD + APK COLLECTION SUCCESSFUL')
if __name__=='__main__':main()
