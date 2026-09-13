package com.cerberus.bionicpc;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ContainerStore {
    static final String ROOT = "cerberus/containers";

    static final class Profile {
        final String id;
        String name;
        String runtime;
        String resolution;
        String renderer;
        String dxMode;
        int memoryMiB;

        Profile(String id, String name, String runtime, String resolution,
                String renderer, String dxMode, int memoryMiB) {
            this.id = id;
            this.name = name;
            this.runtime = runtime;
            this.resolution = resolution;
            this.renderer = renderer;
            this.dxMode = dxMode;
            this.memoryMiB = memoryMiB;
        }
    }

    private final File root;

    ContainerStore(Context context) throws IOException {
        root = new File(context.getFilesDir(), ROOT);
        mkdir(root);
    }

    List<Profile> list() throws IOException {
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return Collections.emptyList();
        Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());
        ArrayList<Profile> out = new ArrayList<>();
        for (File dir : dirs) {
            try { out.add(load(dir.getName())); } catch (Exception ignored) {}
        }
        return out;
    }

    Profile create(String name, String runtime, String resolution,
                   String renderer, String dxMode, int memoryMiB) throws IOException {
        String id = UUID.randomUUID().toString();
        Profile p = new Profile(id, cleanName(name), runtime, resolution, renderer, dxMode, memoryMiB);
        save(p);
        return p;
    }

    Profile load(String id) throws IOException {
        requireId(id);
        File dir = new File(root, id);
        File cfg = new File(dir, "profile.json");
        if (!cfg.isFile()) throw new FileNotFoundException("profile.json ausente: " + id);
        try {
            JSONObject o = new JSONObject(readText(cfg));
            return new Profile(
                    id,
                    o.optString("name", "Container"),
                    o.optString("runtime", "proton-wine-11.0-2"),
                    o.optString("resolution", "1280x720"),
                    o.optString("renderer", "system-vulkan"),
                    o.optString("dxMode", "auto"),
                    o.optInt("memoryMiB", 4096)
            );
        } catch (Exception e) {
            throw new IOException("Perfil inválido: " + e.getMessage(), e);
        }
    }

    void save(Profile p) throws IOException {
        requireId(p.id);
        p.name = cleanName(p.name);
        File dir = dir(p.id);
        mkdir(dir);
        mkdir(new File(dir, "prefix"));
        mkdir(new File(dir, "drive_c"));
        mkdir(new File(dir, "home"));
        mkdir(new File(dir, "tmp"));
        mkdir(new File(dir, "logs"));

        JSONObject o = new JSONObject();
        try {
            o.put("schema", 1);
            o.put("id", p.id);
            o.put("name", p.name);
            o.put("runtime", p.runtime);
            o.put("resolution", p.resolution);
            o.put("renderer", p.renderer);
            o.put("dxMode", p.dxMode);
            o.put("memoryMiB", p.memoryMiB);
            writeTextAtomic(new File(dir, "profile.json"), o.toString(2));
        } catch (Exception e) {
            throw new IOException("Falha serializando profile.json", e);
        }
        dir.setLastModified(System.currentTimeMillis());
    }

    File dir(String id) throws IOException {
        requireId(id);
        return new File(root, id);
    }

    File prefix(String id) throws IOException { return new File(dir(id), "prefix"); }
    File home(String id) throws IOException { return new File(dir(id), "home"); }
    File tmp(String id) throws IOException { return new File(dir(id), "tmp"); }
    File logs(String id) throws IOException { return new File(dir(id), "logs"); }

    void delete(String id) throws IOException {
        File d = dir(id);
        deleteRecursive(d);
    }

    private static String cleanName(String s) throws IOException {
        String v = s == null ? "" : s.trim();
        if (v.isEmpty() || v.length() > 48 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0)
            throw new IOException("Nome do contêiner inválido.");
        return v;
    }

    private static void requireId(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new IOException("ID de contêiner inválido.");
    }

    private static void mkdir(File f) throws IOException {
        if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Falha criando " + f);
    }

    private static String readText(File f) throws IOException {
        StringBuilder b = new StringBuilder();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) >= 0) b.append(buf, 0, n);
        }
        return b.toString();
    }

    private static void writeTextAtomic(File dst, String text) throws IOException {
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(text);
        }
        if (dst.exists() && !dst.delete()) throw new IOException("Falha substituindo " + dst);
        if (!tmp.renameTo(dst)) throw new IOException("Falha finalizando " + dst);
    }

    static void deleteRecursive(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        if (!f.delete()) throw new IOException("Falha removendo " + f);
    }
}
