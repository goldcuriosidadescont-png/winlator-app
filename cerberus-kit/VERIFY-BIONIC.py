#!/usr/bin/env python3
import argparse, subprocess, sys
from pathlib import Path
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--require-payload',action='store_true');a=ap.parse_args();tool=Path(__file__).resolve().parent/'tools'/'verify_kit.py';cmd=[sys.executable,str(tool)]+(['--require-payload'] if a.require_payload else []);raise SystemExit(subprocess.call(cmd))
if __name__=='__main__':main()
