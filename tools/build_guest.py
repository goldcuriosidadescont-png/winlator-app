#!/usr/bin/env python3
from pathlib import Path
import argparse
import struct
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
def build(output):
    with tempfile.TemporaryDirectory(prefix='cerberus-guest-') as tmp:
        tmp = Path(tmp)
        subprocess.run(['as', '--32', str(ROOT / 'guest/orbit.s'), '-o', str(tmp / 'orbit.o')], check=True)
        subprocess.run(['ld', '-m', 'elf_i386', '-T', str(ROOT / 'guest/flat.ld'), str(tmp / 'orbit.o'), '-o', str(tmp / 'orbit.elf')], check=True)
        subprocess.run(['objcopy', '-O', 'binary', str(tmp / 'orbit.elf'), str(tmp / 'orbit.bin')], check=True)
        symbols = subprocess.check_output(['nm', str(tmp / 'orbit.elf')], text=True)
        addresses = {line.split()[2]: int(line.split()[0], 16) for line in symbols.splitlines() if len(line.split()) == 3}
        raw = (tmp / 'orbit.bin').read_bytes()
        code_size = addresses['__code_end'] - 0x10000
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(struct.pack('<4sIIIII', b'C86\0', 1, 0x10000, addresses['_start'], code_size, len(raw)) + raw)
        print(f'{output.name}: {len(raw)} bytes image; {code_size} bytes x86 code')
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'app/src/main/assets/orbit.c86')
    build(parser.parse_args().output)
