#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
command -v javac >/dev/null 2>&1 || { echo 'JDK 17 required to test JNI/Java.' >&2; exit 1; }
cerberus_jdk="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
mkdir -p build/bridge
g++ -std=c++17 -Wall -Wextra -Werror -O2 -fPIC -shared \
    -Icore -I"$cerberus_jdk/include" -I"$cerberus_jdk/include/linux" \
    core/machine.cpp app/src/main/cpp/bridge.cpp -o build/bridge/libcerberus.so
javac -d build/bridge app/src/main/java/dev/cerberus/pc/NativeCore.java \
    app/src/main/java/dev/cerberus/pc/PeInspector.java tests/HostBridgeTest.java
java -Xcheck:jni -Djava.library.path=build/bridge -cp build/bridge \
    dev.cerberus.pc.HostBridgeTest app/src/main/assets/orbit.c86
