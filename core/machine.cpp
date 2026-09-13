#include "machine.hpp"
#include <algorithm>
#include <iomanip>
#include <sstream>
#include <stdexcept>

namespace cerberus {
namespace {
constexpr uint32_t CF = 1, PF = 4, AF = 16, ZF = 64, SF = 128, OF = 2048;
uint32_t le32(const uint8_t* p) {
    return uint32_t(p[0]) | (uint32_t(p[1]) << 8) |
           (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24);
}
uint32_t sx8(uint8_t v) { return v & 0x80 ? 0xffffff00u | v : v; }
int64_t signed32(uint32_t v) { return v & 0x80000000u ? int64_t(v) - 0x100000000LL : v; }
void fail(const char* text) { throw std::runtime_error(text); }
}

Machine::Machine() : memory_(MemorySize), pixels_(Width * Height, 0xff101421) {}

bool Machine::load(const std::vector<uint8_t>& data, std::string& error) {
    // Validate before modifying a currently loaded machine.
    error.clear();
    if (data.size() < 24 || data[0] != 'C' || data[1] != '8' ||
        data[2] != '6' || data[3] != 0) {
        error = "Formato esperado: C86 v1. Windows EXE ainda nao e executavel.";
        return false;
    }
    const uint32_t version = le32(&data[4]), base = le32(&data[8]);
    const uint32_t entry = le32(&data[12]), code = le32(&data[16]), size = le32(&data[20]);
    if (version != 1 || base != Base || !code || code > size ||
        size > 1024 * 1024 || data.size() - 24 != size ||
        entry < base || entry - base >= code) {
        error = "Cabecalho C86 invalido: versao, limites ou entry point.";
        return false;
    }
    std::fill(memory_.begin(), memory_.end(), 0);
    std::copy(data.begin() + 24, data.end(), memory_.begin() + Base);
    std::fill(pixels_.begin(), pixels_.end(), 0xff101421);
    regs_.fill(0);
    regs_[ESP] = MemorySize;
    ip_ = entry;
    codeEnd_ = Base + code;
    flags_ = 2;
    frames_ = instructions_ = exitCode_ = 0;
    fault_.clear();
    state_ = State::Ready;
    return true;
}

void Machine::checkMemory(uint32_t a, uint32_t n) const {
    if (a < 0x1000 || a > MemorySize || n > MemorySize - a)
        fail("Acesso fora da memoria guest");
}
uint32_t Machine::read32(uint32_t a) const { checkMemory(a, 4); return le32(&memory_[a]); }
void Machine::write32(uint32_t a, uint32_t v) {
    checkMemory(a, 4);
    // The prototype has immutable code; self-modifying programs are unsupported.
    if (a < codeEnd_ && uint64_t(a) + 4 > Base) fail("Escrita em codigo guest somente leitura");
    for (unsigned i = 0; i < 4; ++i) memory_[a + i] = uint8_t(v >> (8 * i));
}
uint8_t Machine::fetch8() {
    if (ip_ < Base || ip_ >= codeEnd_) fail("Fetch fora da area de codigo guest");
    return memory_[ip_++];
}
uint32_t Machine::fetch32() {
    uint32_t v = 0;
    for (unsigned i = 0; i < 4; ++i) v |= uint32_t(fetch8()) << (8 * i);
    return v;
}
Machine::Decoded Machine::decode() {
    const uint8_t m = fetch8();
    const unsigned mod = m >> 6, r = (m >> 3) & 7, rm = m & 7;
    if (mod == 3) return {r, {true, rm}};
    uint32_t address = 0;
    if (rm == 4) {
        const uint8_t sib = fetch8();
        const unsigned scale = sib >> 6, index = (sib >> 3) & 7, base = sib & 7;
        if (index != 4) address += regs_[index] << scale;
        address += (base == 5 && mod == 0) ? fetch32() : regs_[base];
    } else {
        address = (mod == 0 && rm == 5) ? fetch32() : regs_[rm];
    }
    if (mod == 1) address += sx8(fetch8());
    if (mod == 2) address += fetch32();
    return {r, {false, address}};
}
uint32_t Machine::read(Operand o) const { return o.isRegister ? regs_[o.value] : read32(o.value); }
void Machine::write(Operand o, uint32_t v) { if (o.isRegister) regs_[o.value] = v; else write32(o.value, v); }

uint32_t Machine::alu(unsigned op, uint32_t a, uint32_t b) {
    uint32_t result = 0, extra = 0;
    switch (op) {
        case 0: {
            const uint64_t sum = uint64_t(a) + b;
            result = uint32_t(sum);
            if (sum >> 32) extra |= CF;
            if ((~(a ^ b) & (a ^ result)) >> 31) extra |= OF;
            if ((a ^ b ^ result) & 16) extra |= AF;
            break;
        }
        case 5: case 7:
            result = a - b;
            if (a < b) extra |= CF;
            if (((a ^ b) & (a ^ result)) >> 31) extra |= OF;
            if ((a ^ b ^ result) & 16) extra |= AF;
            break;
        case 1: result = a | b; break;
        case 4: result = a & b; break;
        case 6: result = a ^ b; break;
        default: fail("Operacao ALU nao implementada");
    }
    unsigned ones = 0;
    for (unsigned i = 0; i < 8; ++i) ones += (result >> i) & 1;
    flags_ = 2 | extra | (result == 0 ? ZF : 0) |
             (result >> 31 ? SF : 0) | (!(ones & 1) ? PF : 0);
    return result;
}
bool Machine::condition(unsigned code) const {
    const bool c = flags_ & CF, z = flags_ & ZF, s = flags_ & SF;
    const bool o = flags_ & OF, p = flags_ & PF;
    switch (code) {
        case 0: return o; case 1: return !o; case 2: return c; case 3: return !c;
        case 4: return z; case 5: return !z; case 6: return c || z; case 7: return !c && !z;
        case 8: return s; case 9: return !s; case 10: return p; case 11: return !p;
        case 12: return s != o; case 13: return s == o;
        case 14: return z || (s != o); default: return !z && (s == o);
    }
}
void Machine::push(uint32_t v) {
    const uint32_t sp = regs_[ESP];
    if (sp < StackBottom + 4 || sp > MemorySize) fail("Stack overflow guest");
    write32(sp - 4, v);
    regs_[ESP] = sp - 4;
}
uint32_t Machine::pop() {
    const uint32_t sp = regs_[ESP];
    if (sp < StackBottom || sp > MemorySize - 4) fail("Stack underflow guest");
    const uint32_t v = read32(sp);
    regs_[ESP] += 4;
    return v;
}
void Machine::interrupt(uint8_t n) {
    if (n != 0x80) fail("Interrupcao nao implementada");
    // Cerberus ABI, deliberately NOT Linux int 80h or Windows API.
    switch (regs_[EAX]) {
        case 0: exitCode_ = regs_[EBX]; state_ = State::Halted; break;
        case 1:
            std::fill(pixels_.begin(), pixels_.end(), 0xff000000 | (regs_[EBX] & 0xffffff));
            break;
        case 2: {
            const int64_t x = signed32(regs_[EBX]), y = signed32(regs_[ECX]);
            const int64_t w = signed32(regs_[EDX]), h = signed32(regs_[ESI]);
            if (w <= 0 || h <= 0) break;
            const int left = int(std::clamp<int64_t>(x, 0, Width));
            const int right = int(std::clamp<int64_t>(x + w, 0, Width));
            const int top = int(std::clamp<int64_t>(y, 0, Height));
            const int bottom = int(std::clamp<int64_t>(y + h, 0, Height));
            const uint32_t color = 0xff000000 | (regs_[EDI] & 0xffffff);
            for (int row = top; row < bottom; ++row)
                std::fill(pixels_.begin() + row * Width + left,
                          pixels_.begin() + row * Width + right, color);
            break;
        }
        case 3: regs_[EAX] = std::min(input_.x, uint32_t(Width - 1)); regs_[EBX] = input_.buttons; break;
        case 4: ++frames_; state_ = State::Yielded; break;
        default: fail("Chamada de host Cerberus ABI desconhecida");
    }
}
void Machine::step() {
    const uint8_t op = fetch8();
    if (op >= 0xb8 && op <= 0xbf) { regs_[op - 0xb8] = fetch32(); return; }
    if (op >= 0x50 && op <= 0x57) { push(regs_[op - 0x50]); return; }
    if (op >= 0x58 && op <= 0x5f) { const uint32_t v = pop(); regs_[op - 0x58] = v; return; }
    if (op >= 0x40 && op <= 0x4f) {
        const uint32_t carry = flags_ & CF;
        const unsigned r = op & 7;
        regs_[r] = alu(op < 0x48 ? 0 : 5, regs_[r], 1);
        flags_ = (flags_ & ~CF) | carry;
        return;
    }
    if (op >= 0x70 && op <= 0x7f) {
        const uint32_t offset = sx8(fetch8());
        if (condition(op & 15)) ip_ += offset;
        return;
    }
    switch (op) {
        case 0x90: return;
        case 0x89: case 0x8b: case 0x8d: {
            const auto d = decode();
            if (op == 0x89) write(d.rm, regs_[d.reg]);
            else if (op == 0x8b) regs_[d.reg] = read(d.rm);
            else { if (d.rm.isRegister) fail("LEA exige operando de memoria"); regs_[d.reg] = d.rm.value; }
            return;
        }
        case 0xc7: {
            const auto d = decode();
            if (d.reg != 0) fail("Extensao C7 nao implementada");
            const uint32_t v = fetch32(); write(d.rm, v); return;
        }
        case 0xa1: { const uint32_t a = fetch32(); regs_[EAX] = read32(a); return; }
        case 0xa3: { const uint32_t a = fetch32(); write32(a, regs_[EAX]); return; }
        case 0x01: case 0x03: case 0x09: case 0x0b: case 0x21: case 0x23:
        case 0x29: case 0x2b: case 0x31: case 0x33: case 0x39: case 0x3b: case 0x85: {
            const auto d = decode();
            const bool reversed = op != 0x85 && (op & 2);
            const Operand dest = reversed ? Operand{true, d.reg} : d.rm;
            const uint32_t b = reversed ? read(d.rm) : regs_[d.reg];
            const unsigned operation = op == 0x85 ? 4 : (op >> 3) & 7;
            const uint32_t result = alu(operation, read(dest), b);
            if (operation != 7 && op != 0x85) write(dest, result);
            return;
        }
        case 0x05: case 0x0d: case 0x25: case 0x2d: case 0x35: case 0x3d: case 0xa9: {
            const uint32_t immediate = fetch32();
            const unsigned operation = op == 0xa9 ? 4 : (op >> 3) & 7;
            const uint32_t result = alu(operation, regs_[EAX], immediate);
            if (operation != 7 && op != 0xa9) regs_[EAX] = result;
            return;
        }
        case 0x81: case 0x83: {
            const auto d = decode();
            const uint32_t immediate = op == 0x81 ? fetch32() : sx8(fetch8());
            const uint32_t result = alu(d.reg, read(d.rm), immediate);
            if (d.reg != 7) write(d.rm, result);
            return;
        }
        case 0x68: { const uint32_t v = fetch32(); push(v); return; }
        case 0x6a: { const uint32_t v = sx8(fetch8()); push(v); return; }
        case 0xe8: { const uint32_t delta = fetch32(); push(ip_); ip_ += delta; return; }
        case 0xc3: ip_ = pop(); return;
        case 0xe9: { const uint32_t delta = fetch32(); ip_ += delta; return; }
        case 0xeb: { const uint32_t delta = sx8(fetch8()); ip_ += delta; return; }
        case 0x0f: {
            const uint8_t second = fetch8();
            if (second < 0x80 || second > 0x8f) fail("Opcode 0F nao implementado");
            const uint32_t delta = fetch32();
            if (condition(second & 15)) ip_ += delta;
            return;
        }
        case 0xcd: interrupt(fetch8()); return;
        default: fail("Opcode/prefixo x86 nao implementado");
    }
}
Machine::State Machine::run(uint32_t budget, Input input) {
    if (state_ == State::Faulted || state_ == State::Halted) return state_;
    input_ = input;
    state_ = State::Ready;
    // Hard cap even if a frontend accidentally asks for an unbounded slice.
    budget = std::min(budget, uint32_t(100000));
    for (uint32_t i = 0; i < budget; ++i) {
        const uint32_t start = ip_;
        try { step(); ++instructions_; }
        catch (const std::exception& e) {
            std::ostringstream message;
            message << "EIP=0x" << std::hex << start << ": " << e.what();
            fault_ = message.str();
            state_ = State::Faulted;
        }
        if (state_ != State::Ready) return state_;
    }
    state_ = State::Budget;
    return state_;
}
} // namespace cerberus
