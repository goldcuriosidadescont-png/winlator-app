#include <jni.h>
#include <cstring>
#include <new>
#include "machine.hpp"
using cerberus::Machine;
static Machine* vm(jlong p) { return reinterpret_cast<Machine*>(static_cast<intptr_t>(p)); }
static void error(JNIEnv* env, const char* type, const char* message) {
    const jclass cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
// The Java wrapper confines all calls, including destruction, to one worker.
extern "C" JNIEXPORT jlong JNICALL
Java_dev_cerberus_pc_NativeCore_create(JNIEnv* env, jclass) {
    try { return static_cast<jlong>(reinterpret_cast<intptr_t>(new Machine())); }
    catch (const std::exception& e) { error(env,"java/lang/IllegalStateException",e.what()); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_dev_cerberus_pc_NativeCore_destroy(JNIEnv*, jclass, jlong p) { delete vm(p); }
extern "C" JNIEXPORT void JNICALL
Java_dev_cerberus_pc_NativeCore_load(JNIEnv* env, jclass, jlong p, jbyteArray bytes) {
    if (!p || !bytes) { error(env,"java/lang/IllegalArgumentException","Missing machine or image"); return; }
    const jsize n = env->GetArrayLength(bytes);
    if (n < 24 || n > 1024 * 1024 + 24) { error(env,"java/lang/IllegalArgumentException","C86 size limit: 1 MiB + header"); return; }
    try {
        std::vector<uint8_t> data(static_cast<size_t>(n));
        env->GetByteArrayRegion(bytes,0,n,reinterpret_cast<jbyte*>(data.data()));
        if (env->ExceptionCheck()) return;
        std::string message;
        if (!vm(p)->load(data,message)) error(env,"java/lang/IllegalArgumentException",message.c_str());
    } catch (const std::exception& e) { error(env,"java/lang/IllegalStateException",e.what()); }
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_cerberus_pc_NativeCore_step(JNIEnv* env, jclass, jlong p, jint x, jint buttons, jintArray pixels) {
    if (!p || !pixels || env->GetArrayLength(pixels) != Machine::Width * Machine::Height) {
        error(env,"java/lang/IllegalArgumentException","Invalid frame target"); return 4;
    }
    try {
        auto* m = vm(p);
        const auto state = m->run(10000,{static_cast<uint32_t>(x),static_cast<uint32_t>(buttons)});
        if (state == Machine::State::Yielded) {
            static_assert(sizeof(jint) == sizeof(uint32_t));
            void* target = env->GetPrimitiveArrayCritical(pixels,nullptr);
            if (!target) return 4;
            std::memcpy(target,m->pixels().data(),m->pixels().size()*sizeof(uint32_t));
            env->ReleasePrimitiveArrayCritical(pixels,target,0);
        }
        return static_cast<jint>(state);
    } catch (const std::exception& e) { error(env,"java/lang/IllegalStateException",e.what()); return 4; }
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_cerberus_pc_NativeCore_report(JNIEnv* env, jclass, jlong p) {
    if (!p) return env->NewStringUTF("Core closed");
    try {
        auto* m=vm(p);
        std::string result;
        if (m->state()==Machine::State::Faulted) result=m->fault();
        else if (m->state()==Machine::State::Halted) result="Encerrado; codigo="+std::to_string(m->exitCode());
        else result="Frames guest: "+std::to_string(m->frames())+"  |  Instrucoes: "+std::to_string(m->instructions());
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& e) { error(env,"java/lang/IllegalStateException",e.what()); return nullptr; }
}
