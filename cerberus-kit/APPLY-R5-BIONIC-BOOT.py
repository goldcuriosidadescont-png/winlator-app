#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: APPLY-R5-BIONIC-BOOT.py <Cerberus source>')

root = Path(sys.argv[1]).resolve()
launcher = root / 'app/src/main/java/com/winlator/xenvironment/components/CerberusBionicProgramLauncherComponent.java'
build = root / 'app/build.gradle'

java = r'''package com.winlator.xenvironment.components;

import android.os.Process;
import com.winlator.cerberus.runtime.CerberusRuntimeProfile;
import com.winlator.cerberus.runtime.CerberusRuntimeStore;
import com.winlator.cerberus.runtime.RuntimeArchitecture;
import com.winlator.cerberus.runtime.RuntimeBackend;
import com.winlator.core.Callback;
import com.winlator.core.EnvVars;
import com.winlator.core.LocaleHelper;
import com.winlator.core.ProcessHelper;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xenvironment.RootFS;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Cerberus-owned Bionic launcher.
 *
 * R5 fixes the Bionic simple-container bootstrap.  The previous integration
 * started Box64 with Wine but never exported Box64's guest search paths.  It
 * also preferred /usr/bin/box64 instead of the Winlator /usr/local/bin/box64
 * location and relied on HOME to infer WINEPREFIX.  That allows preflight to
 * pass while Box64 exits before Wine maps its first X11 window.
 */
public final class CerberusBionicProgramLauncherComponent extends EnvironmentComponent {
    private static final Object lock = new Object();
    private static int pid = -1;
    private String guestExecutable;
    private EnvVars extraEnv;
    private final CerberusRuntimeProfile profile;
    private Callback<Integer> terminationCallback;

    public CerberusBionicProgramLauncherComponent(CerberusRuntimeProfile profile) { this.profile = profile; }
    public void setGuestExecutable(String value) { guestExecutable = value; }
    public void setEnvVars(EnvVars value) { extraEnv = value; }
    public void setTerminationCallback(Callback<Integer> value) { terminationCallback = value; }

    @Override public void start() {
        synchronized (lock) { stop(); pid = exec(); }
    }

    @Override public void stop() {
        synchronized (lock) {
            if (pid != -1) {
                Process.killProcess(pid);
                pid = -1;
            }
        }
    }

    private static String preferPath(File root, String preferred, String fallback) {
        File a = new File(root, preferred);
        return a.exists() ? a.getPath() : new File(root, fallback).getPath();
    }

    private static void addDir(List<String> out, File dir) {
        if (dir != null && dir.isDirectory()) {
            String path = dir.getPath();
            if (!out.contains(path)) out.add(path);
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(':');
            result.append(value);
        }
        return result.toString();
    }

    private static File firstFile(File root, String... relativePaths) {
        for (String relative : relativePaths) {
            File file = new File(root, relative);
            if (file.isFile()) return file;
        }
        return null;
    }

    private int exec() {
        if (profile == null || guestExecutable == null || guestExecutable.trim().isEmpty()) return -1;

        RootFS rootFS = environment.getRootFS();
        File root = rootFS.getRootDir();
        String rootPath = root.getPath();
        String homePath = rootPath + RootFS.HOME_PATH;
        String prefixPath = rootPath + RootFS.WINEPREFIX;

        CerberusRuntimeStore.Info installedRuntime = CerberusRuntimeStore.get(environment.getContext(), profile.runtimeId);
        String runtimeBin = installedRuntime != null
                ? new File(installedRuntime.root, installedRuntime.binPath).getPath()
                : rootPath + rootFS.getWinePath() + "/bin";
        String runtimeLib = installedRuntime != null
                ? new File(installedRuntime.root, installedRuntime.libPath).getPath()
                : rootPath + rootFS.getWinePath() + "/lib";

        File wineFile = new File(runtimeBin, "wine");
        if (!wineFile.isFile()) wineFile = new File(runtimeBin, "wine64");
        if (!wineFile.isFile()) return -1;

        // Prefer Winlator's canonical Box64 location.  Some Bionic images also
        // ship /usr/bin/box64 as a compatibility entry, but it is not the
        // canonical launcher used by the standard Winlator execution path.
        File box64 = firstFile(root, "usr/local/bin/box64", "usr/bin/box64");
        if (profile.architecture == RuntimeArchitecture.X86_64 && box64 == null) return -1;

        // Extraction should preserve these bits, but repairing them here makes
        // first boot resilient to archives/filesystems that lost +x.
        wineFile.setExecutable(true, false);
        if (box64 != null) box64.setExecutable(true, false);

        EnvVars env = new EnvVars();
        LocaleHelper.setEnvVars(env);

        env.put("HOME", homePath);
        env.put("USER", RootFS.USER);
        env.put("TMPDIR", rootPath + "/tmp");
        env.put("DISPLAY", ":0");
        env.put("PREFIX", rootPath + "/usr");
        env.put("PWD", homePath);
        env.put("WINEPREFIX", prefixPath);
        env.put("WINELOADER", wineFile.getPath());

        File wineserver = new File(runtimeBin, "wineserver");
        if (wineserver.isFile()) env.put("WINESERVER", wineserver.getPath());

        List<String> path = new ArrayList<>();
        addDir(path, new File(runtimeBin));
        addDir(path, new File(root, "usr/local/bin"));
        addDir(path, new File(root, "usr/bin"));
        env.put("PATH", join(path));

        // Native Android/Bionic search path: do NOT substitute Wine's x86_64
        // runtime library directory here.  This path is consumed by the ARM64
        // Box64 process itself.
        List<String> nativeLibs = new ArrayList<>();
        addDir(nativeLibs, new File(root, "usr/lib/aarch64-linux-android"));
        addDir(nativeLibs, new File(root, "usr/lib/aarch64-linux-gnu"));
        addDir(nativeLibs, rootFS.getLibDir());
        if (!nativeLibs.isEmpty()) env.put("LD_LIBRARY_PATH", join(nativeLibs) + ":/system/lib64");
        else env.put("LD_LIBRARY_PATH", "/system/lib64");

        // These are the missing variables in R4.  Box64 uses them to locate
        // the x86_64 Wine loader and guest ELF dependencies.  Keep the list
        // layout-agnostic because Bionic donor revisions have used both
        // Debian-style multiarch and Wine-local directories.
        List<String> boxPath = new ArrayList<>();
        addDir(boxPath, new File(runtimeBin));
        addDir(boxPath, new File(root, "usr/local/bin"));
        addDir(boxPath, new File(root, "usr/bin"));
        env.put("BOX64_PATH", join(boxPath));

        List<String> boxLibs = new ArrayList<>();
        addDir(boxLibs, new File(runtimeLib));
        addDir(boxLibs, new File(root, "opt/wine/lib"));
        addDir(boxLibs, new File(root, "opt/wine/lib64"));
        addDir(boxLibs, new File(root, "opt/wine/lib/wine/x86_64-unix"));
        addDir(boxLibs, new File(root, "opt/wine/lib64/wine/x86_64-unix"));
        addDir(boxLibs, new File(root, "lib/x86_64-linux-gnu"));
        addDir(boxLibs, new File(root, "usr/lib/x86_64-linux-gnu"));
        addDir(boxLibs, new File(root, "usr/lib/wine/x86_64-unix"));
        addDir(boxLibs, new File(root, "usr/lib64"));
        // /usr/lib is retained last for Bionic images that keep guest and
        // native compatibility libraries in the same directory.
        addDir(boxLibs, new File(root, "usr/lib"));
        env.put("BOX64_LD_LIBRARY_PATH", join(boxLibs));
        env.put("BOX64_NOBANNER", "1");
        env.put("BOX64_DYNAREC", "1");
        env.put("BOX64_UNITYPLAYER", "0");
        env.put("BOX64_DYNACACHE", "0");

        File box64RC = firstFile(root, "etc/config.box64rc", "usr/etc/config.box64rc");
        if (box64RC != null) env.put("BOX64_RCFILE", box64RC.getPath());

        env.put("XDG_DATA_DIRS", rootPath + "/usr/share");
        env.put("XDG_CONFIG_DIRS", preferPath(root, "usr/etc/xdg", "etc/xdg"));
        env.put("XDG_RUNTIME_DIR", rootPath + "/tmp");
        env.put("FONTCONFIG_PATH", preferPath(root, "usr/etc/fonts", "etc/fonts"));
        env.put("GST_PLUGIN_PATH", rootPath + "/usr/lib/gstreamer-1.0");
        env.put("GST_PLUGIN_FEATURE_RANK", "ximagesink:3000");
        env.put("VK_LAYER_PATH", rootPath + "/usr/share/vulkan/implicit_layer.d:" + rootPath + "/usr/share/vulkan/explicit_layer.d");
        env.put("ENABLE_UTIL_LAYER", "1");

        File alsaConfig = new File(root, "usr/share/alsa/alsa.conf");
        File androidAServer = new File(root, "usr/etc/alsa/conf.d/android_aserver.conf");
        if (alsaConfig.isFile()) {
            env.put("ALSA_CONFIG_PATH", androidAServer.isFile()
                    ? alsaConfig.getPath() + ":" + androidAServer.getPath()
                    : alsaConfig.getPath());
        }
        File alsaPlugin = new File(root, "usr/lib/alsa-lib");
        if (alsaPlugin.isDirectory()) env.put("ALSA_PLUGIN_DIR", alsaPlugin.getPath());

        File opensslConf = new File(root, "usr/etc/tls/openssl.cnf");
        File sslCert = new File(root, "usr/etc/tls/cert.pem");
        File sslDir = new File(root, "usr/etc/tls/certs");
        if (opensslConf.isFile()) env.put("OPENSSL_CONF", opensslConf.getPath());
        if (sslCert.isFile()) env.put("SSL_CERT_FILE", sslCert.getPath());
        if (sslDir.isDirectory()) env.put("SSL_CERT_DIR", sslDir.getPath());

        File sysvPreload = new File(root, "usr/lib/libandroid-sysvshm.so");
        if (sysvPreload.isFile()) env.put("LD_PRELOAD", sysvPreload.getPath());

        env.put("WINE_NO_DUPLICATE_EXPLORER", "1");
        env.put("WINE_DISABLE_FULLSCREEN_HACK", "1");
        env.put("WINE_DO_NOT_UPDATE_IF_TABLE", "1");
        env.put("WINE_X11FORCEGLX", "1");
        env.put("ANDROID_SYSVSHM_SERVER", rootPath + UnixSocketConfig.SYSVSHM_SERVER_PATH);

        if (profile.architecture == RuntimeArchitecture.ARM64EC) {
            env.put("HODLL", profile.backend32 == RuntimeBackend.WOWBOX64 ? "wowbox64.dll" : "libwow64fex.dll");
            if (extraEnv == null || !extraEnv.has("FEX_SMCCHECKS")) env.put("FEX_SMCCHECKS", "0");
        }

        // User/container variables deliberately win over defaults.
        if (extraEnv != null) env.putAll(extraEnv);

        File tmp = new File(root, "tmp");
        if (!tmp.isDirectory()) tmp.mkdirs();
        File shm = new File(root, "tmp/shm");
        if (!shm.isDirectory()) shm.mkdirs();
        File usrTmp = new File(root, "usr/tmp");
        if (!usrTmp.isDirectory()) usrTmp.mkdirs();

        String args = guestExecutable.startsWith("wine ") ? guestExecutable.substring(5) : guestExecutable;
        String command;
        if (profile.architecture == RuntimeArchitecture.X86_64) {
            command = box64.getPath() + " " + wineFile.getPath() + " " + args;
        }
        else {
            command = wineFile.getPath() + " " + args;
        }

        return ProcessHelper.exec(command, env, root, status -> {
            synchronized (lock) { pid = -1; }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    @Override public void onPause() {
        synchronized (lock) {
            if (pid == -1) return;
            List<ProcessHelper.PStat> processes = ProcessHelper.getChildProcesses();
            for (int i = processes.size() - 1; i >= 0; i--) {
                ProcessHelper.PStat p = processes.get(i);
                if (p.guestProcess && p.state != ProcessHelper.PState.STOPPED) ProcessHelper.suspendProcess(p.pid);
            }
        }
    }

    @Override public void onResume() {
        synchronized (lock) {
            if (pid == -1) return;
            for (ProcessHelper.PStat p : ProcessHelper.getChildProcesses()) {
                if (p.guestProcess && p.state == ProcessHelper.PState.STOPPED) ProcessHelper.resumeProcess(p.pid);
            }
        }
    }
}
'''

launcher.parent.mkdir(parents=True, exist_ok=True)
launcher.write_text(java, encoding='utf-8')

text = build.read_text(encoding='utf-8')
if 'versionCode 823' not in text or '8.2.0-Cerberus-Bionic-R4-16K-Compat' not in text:
    raise SystemExit('[FAIL] unexpected R4 build.gradle baseline')
text = text.replace('versionCode 823', 'versionCode 824', 1)
text = text.replace('8.2.0-Cerberus-Bionic-R4-16K-Compat', '8.2.0-Cerberus-Bionic-R5-Container-Boot', 1)
build.write_text(text, encoding='utf-8')

print('[PASS] R5 Bionic container bootstrap patch applied')
print('[PASS] Box64 guest PATH/LD_LIBRARY_PATH exported')
print('[PASS] explicit WINEPREFIX/WINELOADER configured')
print('[PASS] /usr/local/bin/box64 preferred with /usr/bin fallback')
print('[PASS] versionCode=824')
