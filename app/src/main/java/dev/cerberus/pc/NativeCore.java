package dev.cerberus.pc;

/** Confined to GameView's single executor; never pass handles to guest code. */
final class NativeCore implements AutoCloseable {
    static { System.loadLibrary("cerberus"); }
    private long handle;
    NativeCore(byte[] program) {
        handle = create();
        try { load(handle, program); }
        catch (RuntimeException | Error e) { close(); throw e; }
    }
    int tick(int x, int buttons, int[] pixels) { return step(handle, x, buttons, pixels); }
    String status() { return report(handle); }
    @Override public void close() { if (handle != 0) { destroy(handle); handle = 0; } }
    private static native long create();
    private static native void destroy(long handle);
    private static native void load(long handle, byte[] image);
    private static native int step(long handle, int x, int buttons, int[] pixels);
    private static native String report(long handle);
}
