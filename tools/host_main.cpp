#include "machine.hpp"
#include <fstream>
#include <iostream>
#include <iterator>

int main(int argc, char** argv) {
    if (argc != 3) { std::cerr << "Usage: cerberus-host demo.c86 frame.ppm\n"; return 2; }
    std::ifstream input(argv[1], std::ios::binary);
    if (!input) { std::cerr << "Cannot open guest\n"; return 2; }
    std::vector<uint8_t> bytes{std::istreambuf_iterator<char>(input), {}};
    cerberus::Machine vm;
    std::string error;
    if (!vm.load(bytes, error)) { std::cerr << error << '\n'; return 1; }
    for (int frame = 0; frame < 600; ++frame) {
        auto state = vm.run(20000, {uint32_t(160 + (frame % 120) - 60), 0});
        if (state != cerberus::Machine::State::Yielded) {
            std::cerr << "Unexpected stop: " << vm.fault() << '\n'; return 1;
        }
    }
    std::ofstream out(argv[2], std::ios::binary);
    out << "P6\n320 200\n255\n";
    for (auto p : vm.pixels()) {
        const char rgb[] = {char(p >> 16), char(p >> 8), char(p)};
        out.write(rgb, 3);
    }
    if (!out) { std::cerr << "Cannot write frame\n"; return 1; }
    std::cout << "guest_frames=" << vm.frames() << " instructions=" << vm.instructions() << " fault=none\n";
}
