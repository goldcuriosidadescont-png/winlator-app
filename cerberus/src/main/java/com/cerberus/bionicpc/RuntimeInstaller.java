package com.cerberus.bionicpc;

import android.content.Context;
import android.os.StatFs;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

final class RuntimeInstaller {
    static final String BOX_URL = "https://github.com/The412Banner/Nightlies/releases/download/Box64-Bionic/Box64-0.4.4-Bionic.wcp";
    static final String BOX_SHA = "b092973f60555d6d5605a260b52acb1ccb4767aee631732f20b7626bd8fb05eb";
    static final String PROTON_URL = "https://github.com/GameNative/proton-wine/releases/download/proton-11.0-2-20260903/proton-wine-11.0-2-x86_64.wcp.xz";
    static final String PROTON_SHA = "d5bc98a15e1a73876048ae19adc53550bbaea9c835493cf67cc028e3fe88a57e";

    interface Listener { void onProgress(String message); }

    static File runtimeRoot(Context c) { return new File(c.getFilesDir(), "cerberus/runtime"); }
    static File boxRoot(Context c) { return new File(runtimeRoot(c), "box64"); }
    static File protonRoot(Context c) { return new File(runtimeRoot(c), "proton"); }
    static File boxBinary(Context c) { return new File(boxRoot(c), "box64"); }

    static File wineBinary(Context c) {
        File w64 = new File(protonRoot(c), "bin/wine64");
        if (w64.isFile()) return w64;
        return new File(protonRoot(c), "bin/wine");
    }

    static boolean isInstalled(Context c) {
        return boxBinary(c).isFile() && wineBinary(c).isFile() && new File(protonRoot(c), "lib").isDirectory();
    }

    static String summary(Context c) {
        if (!isInstalled(c)) return "Runtime ausente · toque em INSTALAR RUNTIME";
        return "READY · Box64 0.4.4 Bionic + Proton-Wine 11.0-2";
    }

    static void install(Context c, Listener l) throws Exception {
        long free = new StatFs(c.getFilesDir().getAbsolutePath()).getAvailableBytes();
        if (free < 1200L * 1024L * 1024L) throw new IOException("É necessário pelo menos 1.2 GiB livre para instalar o runtime.");

        File base = runtimeRoot(c);
        File downloads = new File(base, "downloads");
        if (!downloads.isDirectory() && !downloads.mkdirs()) throw new IOException("Falha criando downloads.");

        File boxPack = new File(downloads, "Box64-0.4.4-Bionic.wcp");
        File protonPack = new File(downloads, "proton-wine-11.0-2-x86_64.wcp.xz");

        l.onProgress("1/6 · Baixando Box64 0.4.4 Bionic…");
        download(BOX_URL, boxPack, 64L * 1024L * 1024L, l);
        verify(boxPack, BOX_SHA);

        l.onProgress("2/6 · Baixando Proton-Wine 11.0-2…");
        download(PROTON_URL, protonPack, 700L * 1024L * 1024L, l);
        verify(protonPack, PROTON_SHA);

        l.onProgress("3/6 · Extraindo Box64…");
        File boxStage = new File(base, ".box64-stage");
        resetDir(boxStage);
        extractXzTar(boxPack, boxStage, 128L * 1024L * 1024L);
        if (!new File(boxStage, "box64").isFile()) throw new IOException("WCP Box64 sem binário box64 na raiz.");

        l.onProgress("4/6 · Extraindo Proton-Wine…");
        File protonStage = new File(base, ".proton-stage");
        resetDir(protonStage);
        extractXzTar(protonPack, protonStage, 3L * 1024L * 1024L * 1024L);
        File wine = new File(protonStage, "bin/wine64");
        if (!wine.isFile()) wine = new File(protonStage, "bin/wine");
        if (!wine.isFile()) throw new IOException("WCP Proton sem bin/wine ou bin/wine64.");
        if (!new File(protonStage, "lib").isDirectory()) throw new IOException("WCP Proton sem lib/.");

        l.onProgress("5/6 · Ativando runtime no sandbox…");
        replaceDir(boxStage, boxRoot(c));
        replaceDir(protonStage, protonRoot(c));
        makeExecutable(boxBinary(c));
        makeTreeExecutable(new File(protonRoot(c), "bin"));

        l.onProgress("6/6 · Runtime instalado e validado.");
    }

    private static void download(String rawUrl, File out, long max, Listener l) throws Exception {
        if (out.isFile()) {
            l.onProgress("Pacote existente: " + out.getName() + " · verificando…");
            return;
        }
        File part = new File(out.getParentFile(), out.getName() + ".part");
        if (part.exists() && !part.delete()) throw new IOException("Falha limpando download parcial.");
        URL url = new URL(rawUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setRequestProperty("User-Agent", "CerberusBionicPC/0.6");
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " em " + rawUrl);
        long total = con.getContentLengthLong();
        long done = 0;
        long lastReport = 0;
        try (InputStream in = new BufferedInputStream(con.getInputStream(), 64 * 1024);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(part), 64 * 1024)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                done += n;
                if (done > max) throw new IOException("Pacote excedeu limite de segurança.");
                os.write(buf, 0, n);
                if (done - lastReport >= 16L * 1024L * 1024L) {
                    lastReport = done;
                    String m = total > 0
                            ? String.format(Locale.US, "%s · %.1f%%", out.getName(), done * 100.0 / total)
                            : out.getName() + " · " + (done / (1024 * 1024)) + " MiB";
                    l.onProgress(m);
                }
            }
        } finally { con.disconnect(); }
        if (!part.renameTo(out)) throw new IOException("Falha finalizando download: " + out.getName());
    }

    private static void verify(File file, String expected) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[128 * 1024]; int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest()) b.append(String.format(Locale.US, "%02x", x & 0xff));
        if (!b.toString().equalsIgnoreCase(expected)) {
            file.delete();
            throw new IOException("SHA-256 inválido para " + file.getName() + "\nobtido=" + b + "\nesperado=" + expected);
        }
    }

    private static void extractXzTar(File archive, File dest, long maxExpanded) throws Exception {
        String base = dest.getCanonicalPath() + File.separator;
        long expanded = 0;
        try (InputStream fi = new BufferedInputStream(new FileInputStream(archive));
             XZCompressorInputStream xz = new XZCompressorInputStream(fi);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = tar.getNextTarEntry()) != null) {
                if (e.isSymbolicLink() || e.isLink()) continue;
                String name = e.getName();
                while (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty()) continue;
                File target = new File(dest, name);
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(base)) throw new IOException("Path traversal bloqueado: " + name);
                if (e.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Falha criando " + target);
                    continue;
                }
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Falha criando " + parent);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                    int n;
                    while ((n = tar.read(buf)) != -1) {
                        expanded += n;
                        if (expanded > maxExpanded) throw new IOException("Runtime expandido excedeu limite.");
                        out.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static void resetDir(File d) throws IOException {
        if (d.exists()) ContainerStore.deleteRecursive(d);
        if (!d.mkdirs()) throw new IOException("Falha criando " + d);
    }

    private static void replaceDir(File src, File dst) throws IOException {
        if (dst.exists()) ContainerStore.deleteRecursive(dst);
        File parent = dst.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Falha criando " + parent);
        if (!src.renameTo(dst)) throw new IOException("Falha ativando " + dst.getName());
    }

    private static void makeExecutable(File f) throws IOException {
        if (!f.isFile() || !f.setExecutable(true, true)) throw new IOException("Falha chmod +x: " + f);
    }

    private static void makeTreeExecutable(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) makeTreeExecutable(f);
            else f.setExecutable(true, true);
        }
    }
}
