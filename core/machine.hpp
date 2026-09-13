#pragma once
#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace cerberus {
// Guest addresses are offsets into this owned buffer, never host pointers.
class Machine {
public:
    static constexpr uint32_t Base = 0x10000, MemorySize = 2 * 1024 * 1024;
    static constexpr uint32_t StackBottom = 0x180000;
    static constexpr int Width = 320, Height = 200;
    enum Register { EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI };
    enum class State { Ready, Yielded, Budget, Halted, Faulted };
    struct Input { uint32_t x = 160, buttons = 0; };

    Machine();
    bool load(const std::vector<uint8_t>& container, std::string& error);
    State run(uint32_t budget, Input input);
    const std::vector<uint32_t>& pixels() const { return pixels_; }
    uint32_t reg(unsigned i) const { return regs_.at(i); }
    uint32_t ip() const { return ip_; }
    uint32_t flags() const { return flags_; }
    uint64_t instructions() const { return instructions_; }
    uint64_t frames() const { return frames_; }
    uint32_t exitCode() const { return exitCode_; }
    State state() const { return state_; }
    const std::string& fault() const { return fault_; }

private:
    struct Operand { bool isRegister; uint32_t value; };
    struct Decoded { unsigned reg; Operand rm; };
    std::vector<uint8_t> memory_;
    std::vector<uint32_t> pixels_;
    std::array<uint32_t, 8> regs_{};
    uint32_t ip_ = 0, codeEnd_ = 0, flags_ = 2, exitCode_ = 0;
    uint64_t instructions_ = 0, frames_ = 0;
    State state_ = State::Halted;
    std::string fault_;
    Input input_{};
    uint8_t fetch8();
    uint32_t fetch32();
    uint32_t read32(uint32_t address) const;
    void write32(uint32_t address, uint32_t value);
    void checkMemory(uint32_t address, uint32_t size) const;
    Decoded decode();
    uint32_t read(Operand o) const;
    void write(Operand o, uint32_t v);
    uint32_t alu(unsigned operation, uint32_t a, uint32_t b);
    bool condition(unsigned code) const;
    void push(uint32_t v);
    uint32_t pop();
    void interrupt(uint8_t number);
    void step();
};
} // namespace cerberus
