package com.winlator.cerberus.runtime;

import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * WCP compatibility layer.
 *
 * Winlator component repositories contain both compressed-TAR WCPs and a number
 * of historical ZIP packages renamed to .wcp.  TAR packages produced with
 * "tar ... ." also store profile.json as "./profile.json".  Keep all of those
 * representations behind one audited reader/extractor instead of making every
 * store guess the package format.
 */
public final class CerberusWcpUtils {
    private static final int BUFFER_SIZE = 64 * 1024;
    private CerberusWcpUtils() {}

    /** Detects compressed TAR formats used by prefixPack and the majority of WCPs. */
    public static TarCompressorUtils.Type detect(File file) {
        if (file == null || !file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] h = new byte[6];
            int n = in.read(h);
            if (n >= 6 && (h[0] & 255) == 0xFD && h[1] == 0x37 && h[2] == 0x7A
                    && h[3] == 0x58 && h[4] == 0x5A && h[5] == 0x00) {
                return TarCompressorUtils.Type.XZ;
            }
            if (n >= 4 && (h[0] & 255) == 0x28 && (h[1] & 255) == 0xB5
                    && (h[2] & 255) == 0x2F && (h[3] & 255) == 0xFD) {
                return TarCompressorUtils.Type.ZSTD;
            }
        }
        catch (Exception ignored) {}
        return null;
    }

    private static boolean isZip(File file) {
        if (file == null || !file.isFile()) return false;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] h = new byte[4];
            if (in.read(h) != 4) return false;
            return h[0] == 'P' && h[1] == 'K'
                    && ((h[2] == 3 && h[3] == 4) || (h[2] == 5 && h[3] == 6) || (h[2] == 7 && h[3] == 8));
        }
        catch (IOException ignored) { return false; }
    }

    private static String normalizeEntry(String name) {
        if (name == null) return "";
        String value = name.replace('\\', '/');
        while (value.startsWith("./")) value = value.substring(2);
        return value;
    }

    public static byte[] readProfile(File file) {
        TarCompressorUtils.Type type = detect(file);
        if (type != null) {
            // TarCompressorUtils normalizes optional leading "./" now.
            return TarCompressorUtils.read(type, file, "profile.json");
        }
        if (!isZip(file)) return null;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !"profile.json".equals(normalizeEntry(entry.getName()))) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                while ((n = zip.read(buffer)) != -1) out.write(buffer, 0, n);
                return out.toByteArray();
            }
        }
        catch (IOException ignored) {}
        return null;
    }

    public static boolean extract(File file, File destination) {
        TarCompressorUtils.Type type = detect(file);
        if (type != null) return TarCompressorUtils.extract(type, file, destination);
        if (!isZip(file)) return false;
        if (!destination.isDirectory() && !destination.mkdirs()) return false;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File out = safeDestination(destination, entry.getName());
                if (out == null) return false;
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) return false;
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
                try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = zip.read(buffer)) != -1) stream.write(buffer, 0, n);
                }
                // WCP payloads include DLLs plus native helpers. 0771 matches the
                // existing Winlator extraction policy and keeps native binaries runnable.
                FileUtils.chmod(out, 0771);
            }
            return true;
        }
        catch (IOException ignored) { return false; }
    }

    private static File safeDestination(File destination, String archiveName) throws IOException {
        if (archiveName == null || archiveName.isEmpty()) return null;
        String raw = archiveName.replace('\\', '/');
        if (raw.startsWith("/") || raw.matches("^[A-Za-z]:.*")) return null;
        String normalized = normalizeEntry(raw);
        if (normalized.isEmpty() || normalized.equals("..") || normalized.startsWith("../") || normalized.contains("/../")) return null;
        File base = destination.getCanonicalFile();
        File out = new File(base, normalized).getCanonicalFile();
        String bp = base.getPath();
        String op = out.getPath();
        return op.equals(bp) || op.startsWith(bp + File.separator) ? out : null;
    }
}
