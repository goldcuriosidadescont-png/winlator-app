package dev.cerberus.pc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/** Exercises the same Java/JNI boundary as Android using a host shared library. */
public final class HostBridgeTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        byte[] demo = Files.readAllBytes(Path.of(args[0]));
        int[] pixels = new int[64000];
        for (int cycle = 0; cycle < 20; ++cycle) {
            try (NativeCore core = new NativeCore(demo)) {
                for (int frame = 0; frame < 120; ++frame) check(core.tick(160, 0, pixels) == 1);
                check(core.status().contains("120"));
                check(Arrays.stream(pixels).distinct().count() >= 3);
            }
        }
        try (NativeCore ignored = new NativeCore(new byte[24])) { throw new AssertionError("Invalid image accepted"); }
        catch (IllegalArgumentException expected) { /* Correct bounded loader rejection. */ }
        byte[] pe = new byte[512];
        pe[0]='M'; pe[1]='Z'; pe[0x3c]=(byte)0x80; pe[0x80]='P'; pe[0x81]='E';
        pe[0x84]=0x4c; pe[0x85]=1; pe[0x86]=3; pe[0x94]=(byte)0xe0;
        pe[0x98]=0x0b; pe[0x99]=1;
        check(PeInspector.inspect(pe).contains("x86 (32 bits)"));
        for (int n=0;n<pe.length;++n) check(PeInspector.inspect(Arrays.copyOf(pe,n)) != null);
        Random random = new Random(86);
        for (int n=0;n<2000;++n) { byte[] junk=new byte[random.nextInt(1024)]; random.nextBytes(junk); check(PeInspector.inspect(junk)!=null); }
        System.out.println("PASS: JNI lifecycle, 2400 frames, invalid image rejection, PE header/truncation/random inputs");
    }
}
