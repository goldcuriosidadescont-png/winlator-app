#!/usr/bin/env python3
from __future__ import annotations
import argparse, os, shutil, subprocess, sys
from pathlib import Path
REPO='https://github.com/Succubussix/winlator-bionic-glibc.git';BRANCH='winlator_bionic';PIN='1a070485e5060d85e75f41c34e91587557c65b73'
def run(cmd,cwd=None,env=None):
    print('[RUN]',' '.join(map(str,cmd)));p=subprocess.run(cmd,cwd=cwd,env=env)
    if p.returncode:raise SystemExit(f'[FAIL] comando falhou ({p.returncode}): {cmd[0]}')
def main():
    base=Path(__file__).resolve().parent;ap=argparse.ArgumentParser();ap.add_argument('--donor',type=Path,default=base/'reference'/'BionicDonor');ap.add_argument('--offline',action='store_true');a=ap.parse_args();source=base/'source'/'Cerberus';donor=a.donor.resolve();
    if not (source/'app'/'build.gradle').is_file():raise SystemExit('[FAIL] base Cerberus ausente/corrompida.')
    git=shutil.which('git');
    if not git:raise SystemExit('[FAIL] git não encontrado.')
    donor.parent.mkdir(parents=True,exist_ok=True)
    if not (donor/'.git').exists():
        if a.offline:raise SystemExit('[FAIL] donor local ausente em modo offline.')
        shutil.rmtree(donor,ignore_errors=True);env=os.environ.copy();env['GIT_LFS_SKIP_SMUDGE']='1';run([git,'clone','--depth','1','--branch',BRANCH,'--single-branch',REPO,str(donor)],env=env)
    if not a.offline:
        env=os.environ.copy();env['GIT_LFS_SKIP_SMUDGE']='1';run([git,'fetch','--depth','1','origin',PIN],cwd=donor,env=env);run([git,'checkout','--detach','FETCH_HEAD'],cwd=donor)
    head=subprocess.run([git,'rev-parse','HEAD'],cwd=donor,stdout=subprocess.PIPE,text=True,check=True).stdout.strip();print('[DONOR] HEAD='+head)
    if head.lower()!=PIN.lower():raise SystemExit(f'[FAIL] donor fora do pin esperado {PIN}: {head}')
    run([sys.executable,str(base/'IMPORT-BIONIC-DONOR.py'),'--donor',str(donor),'--source',str(source)])
    run([sys.executable,str(base/'VERIFY-BIONIC.py'),'--require-payload'])
    print('[PASS] PREPARE concluído. Base compilada continua em source/Cerberus.')
if __name__=='__main__':main()
