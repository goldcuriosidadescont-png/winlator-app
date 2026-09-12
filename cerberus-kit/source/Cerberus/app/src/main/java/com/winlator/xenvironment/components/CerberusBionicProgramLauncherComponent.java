package com.winlator.xenvironment.components;

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
import java.util.List;

/** Cerberus-owned Bionic launcher. Donor app/UI code is not compiled. */
public final class CerberusBionicProgramLauncherComponent extends EnvironmentComponent {
    private static final Object lock = new Object();
    private static int pid = -1;
    private String guestExecutable;
    private EnvVars extraEnv;
    private CerberusRuntimeProfile profile;
    private Callback<Integer> terminationCallback;

    public CerberusBionicProgramLauncherComponent(CerberusRuntimeProfile profile) { this.profile = profile; }
    public void setGuestExecutable(String value) { guestExecutable = value; }
    public void setEnvVars(EnvVars value) { extraEnv = value; }
    public void setTerminationCallback(Callback<Integer> value) { terminationCallback = value; }

    @Override public void start() {
        synchronized (lock) { stop(); pid = exec(); }
    }

    @Override public void stop() {
        synchronized (lock) { if (pid != -1) { Process.killProcess(pid); pid = -1; } }
    }

    private static String preferPath(File root, String preferred, String fallback) {
        File a = new File(root, preferred);
        if (a.exists()) return a.getPath();
        return new File(root, fallback).getPath();
    }

    private int exec() {
        if (profile == null || guestExecutable == null || guestExecutable.trim().isEmpty()) return -1;

        RootFS rootFS = environment.getRootFS();
        File root = rootFS.getRootDir();
        String rootPath = root.getPath();
        EnvVars env = new EnvVars();
        LocaleHelper.setEnvVars(env);

        CerberusRuntimeStore.Info installedRuntime = CerberusRuntimeStore.get(environment.getContext(), profile.runtimeId);
        String runtimeBin = installedRuntime != null
                ? new File(installedRuntime.root, installedRuntime.binPath).getPath()
                : rootPath + rootFS.getWinePath() + "/bin";
        String runtimeLib = installedRuntime != null
                ? new File(installedRuntime.root, installedRuntime.libPath).getPath()
                : rootPath + "/usr/lib";

        // Bionic root environment. Paths intentionally follow the donor imagefs layout;
        // old /etc/xdg and /etc/fonts paths used by the first Cerberus integration were wrong.
        env.put("HOME", rootPath + RootFS.HOME_PATH);
        env.put("USER", RootFS.USER);
        env.put("TMPDIR", rootPath + "/usr/tmp");
        env.put("DISPLAY", ":0");
        env.put("PREFIX", rootPath + "/usr");
        env.put("PATH", runtimeBin + ":" + rootPath + "/usr/bin:" + rootPath + "/usr/local/bin");
        env.put("LD_LIBRARY_PATH", runtimeLib + ":" + rootPath + "/usr/lib:/system/lib64");
        env.put("XDG_DATA_DIRS", rootPath + "/usr/share");
        env.put("XDG_CONFIG_DIRS", preferPath(root, "usr/etc/xdg", "etc/xdg"));
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
            // ARM64EC 64-bit is translated by FEXCore. The WOW64 backend is independently selectable.
            env.put("HODLL", profile.backend32 == RuntimeBackend.WOWBOX64 ? "wowbox64.dll" : "libwow64fex.dll");
            // Correct spelling only. Never emit the obsolete FEX_SMC_CHECKS variable.
            if (extraEnv == null || !extraEnv.has("FEX_SMCCHECKS")) env.put("FEX_SMCCHECKS", "0");
        }
        // User/container variables deliberately win over defaults, including LD_PRELOAD.
        if (extraEnv != null) env.putAll(extraEnv);

        File tmp = new File(root, "usr/tmp");
        if (!tmp.isDirectory()) tmp.mkdirs();
        File shm = new File(root, "tmp/shm");
        if (!shm.isDirectory()) shm.mkdirs();

        String wine = runtimeBin + "/wine";
        if (!new File(wine).isFile()) wine = runtimeBin + "/wine64";
        if (!new File(wine).isFile()) return -1;

        String args = guestExecutable.startsWith("wine ") ? guestExecutable.substring(5) : guestExecutable;
        String command;
        if (profile.architecture == RuntimeArchitecture.X86_64) {
            File b1 = new File(root, "usr/bin/box64");
            File b2 = new File(root, "usr/local/bin/box64");
            File box = b1.isFile() ? b1 : b2;
            if (!box.isFile()) return -1;
            command = box.getPath() + " " + wine + " " + args;
        }
        else command = wine + " " + args;

        return ProcessHelper.exec(command, env, root, status -> {
            synchronized (lock) { pid = -1; }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    @Override public void onPause() {
        synchronized (lock) {
            if (pid == -1) return;
            List<ProcessHelper.PStat> processes = ProcessHelper.getChildProcesses();
            for (int i = processes.size()-1; i >= 0; i--) {
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
