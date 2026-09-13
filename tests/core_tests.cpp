#include "machine.hpp"
#include <algorithm>
#include <fstream>
#include <iostream>
#include <iterator>
#include <random>
#include <stdexcept>
using cerberus::Machine;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(std::string(__func__) + ": " + #x); } while (0)
static void u32(std::vector<uint8_t>& v, uint32_t n) {
    for (unsigned i = 0; i < 4; ++i) v.push_back(uint8_t(n >> (8 * i)));
}
static std::vector<uint8_t> pack(std::vector<uint8_t> code) {
    std::vector<uint8_t> v{'C','8','6',0};
    u32(v,1); u32(v,Machine::Base); u32(v,Machine::Base);
    u32(v,uint32_t(code.size())); u32(v,uint32_t(code.size()));
    v.insert(v.end(),code.begin(),code.end()); return v;
}
static void load(Machine& m, const std::vector<uint8_t>& code) {
    std::string e; CHECK(m.load(pack(code),e)); CHECK(e.empty());
}
static void containers() {
    Machine m; std::string e;
    CHECK(!m.load({},e));
    auto good = pack({0x90}); CHECK(m.load(good,e));
    for (size_t n = 0; n < good.size(); ++n) {
        auto truncated = good; truncated.resize(n); CHECK(!m.load(truncated,e));
    }
    auto wrong = good; wrong[4] = 2; CHECK(!m.load(wrong,e));
    wrong = good; wrong[12] = 1; CHECK(!m.load(wrong,e));
    wrong = good; wrong.push_back(0); CHECK(!m.load(wrong,e));
    CHECK(m.run(1,{}) == Machine::State::Budget);
    CHECK(m.run(1,{}) == Machine::State::Faulted);
}
static void arithmetic() {
    std::mt19937 rng(0xc86);
    Machine m;
    for (unsigned i = 0; i < 5000; ++i) {
        uint32_t a = rng(), b = rng(), expected = 0, referenceFlags = 0;
        const unsigned which = i % 5;
        const uint8_t ops[] = {0x01,0x29,0x21,0x09,0x31};
        std::vector<uint8_t> code{0xb8}; u32(code,a); code.push_back(0xbb); u32(code,b);
        code.push_back(ops[which]); code.push_back(0xd8);
        load(m,code); CHECK(m.run(3,{}) == Machine::State::Budget);
        switch (which) { case 0: expected=a+b; break; case 1: expected=a-b; break;
            case 2: expected=a&b; break; case 3: expected=a|b; break; default: expected=a^b; }
        CHECK(m.reg(Machine::EAX) == expected);
#if defined(__x86_64__)
        uint32_t result = a; uint64_t f = 0;
        switch (which) {
            case 0: asm volatile("addl %2,%0; pushfq; popq %1" : "+r"(result), "=r"(f) : "r"(b) : "cc"); break;
            case 1: asm volatile("subl %2,%0; pushfq; popq %1" : "+r"(result), "=r"(f) : "r"(b) : "cc"); break;
            case 2: asm volatile("andl %2,%0; pushfq; popq %1" : "+r"(result), "=r"(f) : "r"(b) : "cc"); break;
            case 3: asm volatile("orl %2,%0; pushfq; popq %1" : "+r"(result), "=r"(f) : "r"(b) : "cc"); break;
            default: asm volatile("xorl %2,%0; pushfq; popq %1" : "+r"(result), "=r"(f) : "r"(b) : "cc");
        }
        referenceFlags = uint32_t(f);
        const uint32_t mask = which < 2 ? 0x8d5 : 0x8c5;
        CHECK(result == expected); CHECK((m.flags() & mask) == (referenceFlags & mask));
#else
        (void)referenceFlags;
#endif
    }
    load(m,{0xb8,0xff,0xff,0xff,0xff,0x83,0xc0,1,0x40});
    m.run(3,{}); CHECK(m.reg(0)==1); CHECK(m.flags()&1);
    load(m,{0x6a,0xff,0x58}); m.run(2,{}); CHECK(m.reg(0)==0xffffffff);
}
static void conditions() {
    const std::pair<uint32_t,uint32_t> pairs[] = {{0,0},{0,1},{1,0},{0x80000000,1},{0x7fffffff,0xffffffff}};
    for (auto pair : pairs) for (unsigned cc=0;cc<16;++cc) {
        uint32_t a=pair.first,b=pair.second,r=a-b;
        bool c=a<b,z=r==0,s=bool(r>>31),o=bool(((a^b)&(a^r))>>31);
        bool p=(__builtin_popcount(r&255)%2)==0;
        const bool expected[]={o,!o,c,!c,z,!z,c||z,!c&&!z,s,!s,p,!p,s!=o,s==o,z||s!=o,!z&&s==o};
        std::vector<uint8_t> code{0xb8}; u32(code,a); code.push_back(0x3d);u32(code,b);
        code.push_back(uint8_t(0x70+cc));code.push_back(5);
        code.push_back(0xb9);u32(code,1);code.push_back(0x90);
        Machine m;load(m,code);m.run(3,{});
        CHECK(m.ip()==Machine::Base+12+(expected[cc]?5:0));
    }
}
static void addressing_and_stack() {
    Machine m;
    load(m,{0xbb,0,0,0x12,0,0xbe,3,0,0,0,0xc7,0x44,0xb3,0xfc,42,0,0,0,
            0x8b,0x44,0xb3,0xfc,0x50,0x59});
    m.run(6,{}); CHECK(m.reg(0)==42); CHECK(m.reg(1)==42);
    CHECK(m.reg(Machine::ESP)==Machine::MemorySize);
    load(m,{0xe8,7,0,0,0,0xb9,7,0,0,0,0xeb,6,0xb8,42,0,0,0,0xc3,0x90});
    m.run(6,{}); CHECK(m.reg(0)==42); CHECK(m.reg(1)==7); CHECK(m.reg(4)==Machine::MemorySize);
    load(m,{0x58}); CHECK(m.run(1,{})==Machine::State::Faulted);
    load(m,{0xbc,0,0,0,0,0x50}); CHECK(m.run(2,{})==Machine::State::Faulted);
}
static void faults_and_budget() {
    Machine m;
    for (auto code : {std::vector<uint8_t>{0xa1,0,0,0,0},
                      std::vector<uint8_t>{0xa1,0xff,0xff,0xff,0xff},
                      std::vector<uint8_t>{0x0f,0x0b},
                      std::vector<uint8_t>{0xb8,0},
                      std::vector<uint8_t>{0xa3,0,0,1,0},
                      std::vector<uint8_t>{0xcd,0x81}}) {
        load(m,code);CHECK(m.run(100,{})==Machine::State::Faulted);CHECK(!m.fault().empty());
        CHECK(m.run(100,{})==Machine::State::Faulted);
    }
    load(m,{0xeb,0xfe});CHECK(m.run(100,{})==Machine::State::Budget);CHECK(m.instructions()==100);
    load(m,{0xb8,0,0,0,0,0xbb,42,0,0,0,0xcd,0x80});
    CHECK(m.run(10,{})==Machine::State::Halted);CHECK(m.exitCode()==42);
}
static void graphics() {
    Machine m;
    std::vector<uint8_t> code;
    auto mov=[&](unsigned r,uint32_t v){code.push_back(uint8_t(0xb8+r));u32(code,v);};
    mov(0,2);mov(3,0xfffffffc);mov(1,0xfffffffc);mov(2,8);mov(6,8);mov(7,0x123456);
    code.insert(code.end(),{0xcd,0x80,0xb8,4,0,0,0,0xcd,0x80});
    load(m,code);CHECK(m.run(100,{})==Machine::State::Yielded);
    CHECK(std::count(m.pixels().begin(),m.pixels().end(),0xff123456)==16);CHECK(m.frames()==1);
    load(m,{0xb8,3,0,0,0,0xcd,0x80});m.run(2,{999,3});CHECK(m.reg(0)==319);CHECK(m.reg(3)==3);
}
static void bounded_fuzz() {
    std::mt19937 rng(0xb10c);Machine m;std::string e;
    for(int i=0;i<2000;++i) {
        std::vector<uint8_t> raw(1+rng()%128);
        for(auto& byte:raw)byte=uint8_t(rng());
        CHECK(m.load(pack(raw),e));m.run(200,{});
    }
}
static void demo(const char* path) {
    std::ifstream f(path,std::ios::binary);CHECK(bool(f));
    std::vector<uint8_t> bytes{std::istreambuf_iterator<char>(f),{}};
    Machine m;std::string e;CHECK(m.load(bytes,e));
    for(int i=0;i<10000;++i) CHECK(m.run(10000,{uint32_t(i%320),0})==Machine::State::Yielded);
    CHECK(m.frames()==10000);CHECK(m.fault().empty());
}
int main(int argc,char** argv) {
    try {
        CHECK(argc==2);
        containers();arithmetic();conditions();addressing_and_stack();faults_and_budget();graphics();bounded_fuzz();demo(argv[1]);
        std::cout<<"PASS: containers, 5000 ALU vectors, 80 branches, addressing/stack, faults/budgets, graphics/input, 2000 bounded fuzz inputs, 10000 demo frames\n";
    } catch(const std::exception& e){std::cerr<<"FAIL: "<<e.what()<<'\n';return 1;}
}
