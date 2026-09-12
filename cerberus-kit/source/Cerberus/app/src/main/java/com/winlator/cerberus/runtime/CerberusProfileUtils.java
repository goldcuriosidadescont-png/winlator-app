package com.winlator.cerberus.runtime;

import org.json.JSONObject;
import java.io.File;
import java.util.Locale;

/** Shared validation helpers for externally supplied Cerberus WCP metadata. */
final class CerberusProfileUtils {
    private CerberusProfileUtils() {}

    static String safeId(String value) {
        if (value == null) return "";
        String id = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-");
        while (id.startsWith("-")) id = id.substring(1);
        while (id.endsWith("-")) id = id.substring(0, id.length() - 1);
        return id;
    }

    static String normalizeRelative(String value) {
        if (value == null) return null;
        String v = value.trim().replace('\\', '/');
        while (v.startsWith("./")) v = v.substring(2);
        if (v.isEmpty() || v.startsWith("/") || v.equals("..") || v.startsWith("../")
                || v.contains("/../") || v.endsWith("/..") || v.indexOf(':') >= 0) return null;
        return v;
    }

    static File resolveInside(File root, String relative) throws Exception {
        String safe = normalizeRelative(relative);
        if (safe == null) return null;
        File result = new File(root, safe);
        String rp = root.getCanonicalPath();
        String cp = result.getCanonicalPath();
        if (!cp.equals(rp) && !cp.startsWith(rp + File.separator)) return null;
        return result;
    }

    static String detectArchitecture(JSONObject profile, String fallback) {
        if (profile == null) return fallback;
        StringBuilder b = new StringBuilder();
        append(b, profile.optString("architecture", ""));
        append(b, profile.optString("arch", ""));
        append(b, profile.optString("versionName", ""));
        append(b, profile.optString("verName", ""));
        JSONObject wine = profile.optJSONObject("wine");
        if (wine != null) {
            append(b, wine.optString("architecture", ""));
            append(b, wine.optString("arch", ""));
            append(b, wine.optString("binPath", ""));
            append(b, wine.optString("libPath", ""));
            append(b, wine.optString("prefixPack", ""));
        }
        // File maps of current ARM64EC WCPs normally carry arm64ec in names/paths.
        append(b, profile.toString());
        String low = b.toString().toLowerCase(Locale.ENGLISH);
        if (low.contains("arm64ec")) return "arm64ec";
        if (low.contains("x86_64") || low.contains("x86-64") || low.contains("amd64")) return "x86_64";
        return fallback;
    }

    private static void append(StringBuilder b, String value) {
        if (value != null && !value.isEmpty()) b.append(' ').append(value);
    }

    static boolean isBuiltinRuntimeId(String id) {
        return id == null || id.isEmpty() || "builtin".equalsIgnoreCase(id) || "builtin-bionic".equalsIgnoreCase(id);
    }
}
