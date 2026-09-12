#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, shutil
from datetime import datetime, timezone
from pathlib import Path

def sha256(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()
def main():
    ap=argparse.ArgumentParser(); base=Path(__file__).resolve().parent
    ap.add_argument('--source',type=Path,default=base/'source'/'Cerberus'); ap.add_argument('--kit-root',type=Path,default=base); ap.add_argument('--output-name',default='Cerberus-Winlator-V8.2.0-BIONIC-FULL-INTEGRATION.apk'); a=ap.parse_args()
    br=a.source.resolve()/'app'/'build'
    if not br.is_dir(): raise SystemExit(f'[FAIL] app/build ausente: {br}')
    apks=[p for p in br.rglob('*.apk') if p.is_file() and p.stat().st_size>=1024*1024]
    if not apks: raise SystemExit(f'[FAIL] nenhum APK válido em {br}')
    def score(p):
        s=p.as_posix().lower(); n=p.name.lower(); v=0
        if '/outputs/apk/' in s:v+=100
        if '/debug/' in s:v+=60
        if 'debug' in n:v+=40
        if 'androidtest' in s or '/test/' in s or 'test' in n:v-=200
        return (v,p.stat().st_mtime)
    apk=max(apks,key=score); outdir=a.kit_root.resolve()/'output';outdir.mkdir(parents=True,exist_ok=True);out=outdir/a.output_name;shutil.copy2(apk,out)
    if out.stat().st_size!=apk.stat().st_size: raise SystemExit('[FAIL] tamanho divergiu após cópia do APK')
    digest=sha256(out); report='\n'.join([f'source={apk}',f'sourceBytes={apk.stat().st_size}',f'output={out}',f'outputBytes={out.stat().st_size}',f'sha256={digest}',f'selectedScore={score(apk)[0]}',f'generatedUtc={datetime.now(timezone.utc).isoformat()}'])+'\n';(outdir/'LATEST-APK.txt').write_text(report,encoding='utf-8')
    print('[APK]',out);print('[SHA256]',digest);print('[PASS] APK localizado, copiado e verificado.')
if __name__=='__main__':main()
