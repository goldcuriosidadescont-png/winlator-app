#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/host
CXX="${CXX:-g++}"
SANITIZERS="${SANITIZERS:-1}"
flags="-std=c++17 -Wall -Wextra -Wpedantic -Werror -O1 -g -mno-red-zone"
if [ "$SANITIZERS" = 1 ]; then
    flags="$flags -fsanitize=address,undefined -fno-omit-frame-pointer -fno-pie -no-pie"
fi
"$CXX" $flags -Icore core/machine.cpp tests/core_tests.cpp -o build/host/core-tests
"$CXX" $flags -Icore core/machine.cpp tools/host_main.cpp -o build/host/cerberus-host
python3 tools/build_guest.py --output build/host/orbit.c86
cmp app/src/main/assets/orbit.c86 build/host/orbit.c86
build/host/core-tests app/src/main/assets/orbit.c86
build/host/cerberus-host app/src/main/assets/orbit.c86 build/host/orbit.ppm
