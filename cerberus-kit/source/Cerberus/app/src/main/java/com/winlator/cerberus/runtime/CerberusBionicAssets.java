package com.winlator.cerberus.runtime;

import android.content.Context;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CerberusBionicAssets {
    public static final String ROOT = "cerberus-bionic";
    public static final String IMAGE_XZ = ROOT + "/imagefs.txz";
    public static final String IMAGE_ZSTD = ROOT + "/imagefs.tzst";
    public static final String PATTERN_X86_XZ = ROOT + "/container-pattern-x86_64.txz";
    public static final String PATTERN_X86_ZSTD = ROOT + "/container-pattern-x86_64.tzst";
    public static final String PATTERN_ARM_XZ = ROOT + "/container-pattern-arm64ec.txz";
    public static final String PATTERN_ARM_ZSTD = ROOT + "/container-pattern-arm64ec.tzst";
    public static final String MANIFEST = ROOT + "/manifest.json";

    private CerberusBionicAssets() {}

    public static boolean exists(Context context, String asset) {
        int slash = asset.lastIndexOf('/');
        String dir = slash > 0 ? asset.substring(0, slash) : "";
        String name = slash > 0 ? asset.substring(slash + 1) : asset;
        try {
            Set<String> files = new HashSet<>(Arrays.asList(context.getAssets().list(dir)));
            return files.contains(name);
        }
        catch (IOException e) { return false; }
    }
}
