package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xenvironment.RootFS;
import java.io.File;

/** Installs only the donor Bionic filesystem payload into a separate root. */
public final class CerberusBionicInstaller {
    // v2 invalidates roots created by the earlier installer that could be marked valid before structural checks.
    public static final int VERSION = 2;
    private CerberusBionicInstaller() {}

    public static synchronized boolean ensureInstalled(Context context) {
        RootFS rootFS = RootFS.findBionic(context);
        if (isInstallationUsable(rootFS)) return true;

        File root = rootFS.getRootDir();
        if (root.exists() && !FileUtils.delete(root)) return false;
        if (!root.mkdirs() && !root.isDirectory()) return false;

        String asset;
        TarCompressorUtils.Type type;
        if (CerberusBionicAssets.exists(context, CerberusBionicAssets.IMAGE_XZ)) {
            asset = CerberusBionicAssets.IMAGE_XZ;
            type = TarCompressorUtils.Type.XZ;
        }
        else if (CerberusBionicAssets.exists(context, CerberusBionicAssets.IMAGE_ZSTD)) {
            asset = CerberusBionicAssets.IMAGE_ZSTD;
            type = TarCompressorUtils.Type.ZSTD;
        }
        else {
            FileUtils.delete(root);
            return false;
        }

        if (!TarCompressorUtils.extract(type, context, asset, root)) {
            FileUtils.delete(root);
            return false;
        }

        // Create only runtime-owned writable directories; never use these to fake a valid imagefs.
        new File(root, "home/xuser").mkdirs();
        new File(root, "usr/tmp").mkdirs();
        new File(root, "tmp/shm").mkdirs();
        new File(root, "tmp").mkdirs();

        if (!hasRequiredLayout(root)) {
            FileUtils.delete(root);
            return false;
        }
        rootFS.createRFSVersionFile(VERSION);
        if (!isInstallationUsable(rootFS)) {
            FileUtils.delete(root);
            return false;
        }
        return true;
    }

    public static boolean isInstallationUsable(RootFS rootFS) {
        return rootFS != null && rootFS.isValid() && rootFS.getVersion() >= VERSION
                && hasRequiredLayout(rootFS.getRootDir());
    }

    private static boolean hasRequiredLayout(File root) {
        return root != null && root.isDirectory()
                && new File(root, "usr").isDirectory()
                && new File(root, "usr/bin").isDirectory()
                && new File(root, "usr/lib").isDirectory()
                && new File(root, "home").isDirectory()
                && new File(root, "tmp").isDirectory();
    }
}
