#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: APPLY-R6-PREFIX-PROTON-UI.py <Cerberus source>')

root = Path(sys.argv[1]).resolve()
launcher = root / 'app/src/main/java/com/winlator/xenvironment/components/CerberusBionicProgramLauncherComponent.java'
prefix_manager = root / 'app/src/main/java/com/winlator/cerberus/runtime/CerberusPrefixManager.java'
preflight = root / 'app/src/main/java/com/winlator/cerberus/runtime/CerberusRuntimePreflight.java'
fragment = root / 'app/src/main/java/com/winlator/ContainerDetailFragment.java'
layout = root / 'app/src/main/res/layout/container_detail_fragment.xml'
build = root / 'app/build.gradle'

java = r'''package com.winlator.xenvironment.components;

import android.os.Process;
import com.winlator.cerberus.runtime.CerberusRuntimeProfile;
import com.winlator.cerberus.runtime.CerberusRuntimeStore;
import com.winlator.cerberus.runtime.RuntimeArchitecture;
import com.winlator.cerberus.runtime.RuntimeBackend;
import com.winlator.cerberus.runtime.RuntimeFamily;
import com.winlator.core.Callback;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.LocaleHelper;
import com.winlator.core.ProcessHelper;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xenvironment.RootFS;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Cerberus Bionic launcher R6.
 *
 * Prefix initialization is an explicit two-stage lifecycle:
 *   1) wineboot.exe -u with sync features disabled only for bootstrap;
 *   2) commit a runtime fingerprint only after wineboot exits cleanly;
 *   3) launch Explorer/game.
 *
 * An interrupted bootstrap deliberately leaves PREFIX_INIT_MARKER behind.
 * CerberusRuntimePreflight then rebuilds the prefix from the exact selected
 * Wine/Proton WCP prefixPack on the next launch instead of reusing a partially
 * migrated prefix.
 */
public final class CerberusBionicProgramLauncherComponent extends EnvironmentComponent {
    public static final String BUILD_MARKER = "Cerberus-Bionic-Launcher-R6";
    public static final String PREFIX_INIT_MARKER = ".cerberus-prefix-r6.initializing";
    public static final String PREFIX_READY_MARKER = ".cerberus-prefix-r6.ready";

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
        synchronized (lock) {
            stop();
            pid = exec();
        }
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

    private String fingerprint() {
        String runtime = profile.runtimeId == null ? "" : profile.runtimeId;
        return "r6|" + profile.family.id + "|" + profile.architecture.id + "|" + runtime;
    }

    private boolean markerMatches(File marker) {
        try {
            return marker.isFile() && fingerprint().equals(FileUtils.readString(marker).trim());
        }
        catch (Exception ignored) { return false; }
    }

    private boolean writeMarker(File marker) {
        try { return FileUtils.writeString(marker, fingerprint()); }
        catch (Exception ignored) { return false; }
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

        File box64 = firstFile(root, "usr/local/bin/box64", "usr/bin/box64");
        if (profile.architecture == RuntimeArchitecture.X86_64 && box64 == null) return -1;

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

        List<String> nativeLibs = new ArrayList<>();
        addDir(nativeLibs, new File(root, "usr/lib/aarch64-linux-android"));
        addDir(nativeLibs, new File(root, "usr/lib/aarch64-linux-gnu"));
        addDir(nativeLibs, rootFS.getLibDir());
        if (!nativeLibs.isEmpty()) env.put("LD_LIBRARY_PATH", join(nativeLibs) + ":/system/lib64");
        else env.put("LD_LIBRARY_PATH", "/system/lib64");

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

        // Proton WCP remains a Cerberus Bionic runtime.  We launch its own
        // packaged Wine loader/prefixPack and never borrow the Classic GLIBC
        // Proton wrapper.  These flags are only family identification; the WCP
        // remains authoritative for binary/library layout.
        if (profile.family == RuntimeFamily.PROTON) {
            env.put("CERBERUS_PROTON", "1");
            env.put("PROTON_NO_ESYNC", "0");
            env.put("PROTON_NO_FSYNC", "0");
        }

        if (extraEnv != null) env.putAll(extraEnv);

        // Engine-owned paths win over stale Classic/container variables.
        env.put("HOME", homePath);
        env.put("WINEPREFIX", prefixPath);
        env.put("WINELOADER", wineFile.getPath());
        if (wineserver.isFile()) env.put("WINESERVER", wineserver.getPath());

        File tmp = new File(root, "tmp");
        if (!tmp.isDirectory()) tmp.mkdirs();
        File shm = new File(root, "tmp/shm");
        if (!shm.isDirectory()) shm.mkdirs();
        File usrTmp = new File(root, "usr/tmp");
        if (!usrTmp.isDirectory()) usrTmp.mkdirs();

        File initMarker = new File(homePath, PREFIX_INIT_MARKER);
        File readyMarker = new File(homePath, PREFIX_READY_MARKER);
        final boolean bootstrapPrefix = !markerMatches(readyMarker);

        String command;
        if (bootstrapPrefix) {
            if (!writeMarker(initMarker)) return -1;
            // First-run prefix migration is deliberately conservative.  The
            // user's normal sync settings are restored for the real launch.
            env.put("WINEESYNC", "0");
            env.put("WINEFSYNC", "0");
            env.put("WINEDLLOVERRIDES", "winemenubuilder.exe=d");
            String bootstrapArgs = "wineboot.exe -u";
            if (profile.architecture == RuntimeArchitecture.X86_64) {
                command = box64.getPath() + " " + wineFile.getPath() + " " + bootstrapArgs;
            }
            else {
                // ARM64EC uses the native ARM64 Wine loader. FEXCore/WOWBox64
                // is selected by HODLL for guest PE execution, not by wrapping
                // the native loader in Box64.
                command = wineFile.getPath() + " " + bootstrapArgs;
            }
        }
        else {
            String args = guestExecutable.startsWith("wine ") ? guestExecutable.substring(5) : guestExecutable;
            if (profile.architecture == RuntimeArchitecture.X86_64) {
                command = box64.getPath() + " " + wineFile.getPath() + " " + args;
            }
            else {
                command = wineFile.getPath() + " " + args;
            }
        }

        return ProcessHelper.exec(command, env, root, status -> {
            synchronized (lock) {
                pid = -1;
                if (bootstrapPrefix) {
                    if (status == 0 && writeMarker(readyMarker)) {
                        FileUtils.delete(initMarker);
                        pid = exec();
                        if (pid != -1) return;
                    }
                    // Keep .initializing on failure. The next background
                    // preflight will transactionally rebuild the prefix.
                }
            }
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
launcher.write_text(java, encoding='utf-8')

# Prefix recovery is deliberately executed from the existing Bionic preflight,
# which R3 already moved off XServerDisplayActivity's UI thread.  Never unpack a
# prefix from EnvironmentComponent.start().
pm = prefix_manager.read_text(encoding='utf-8')
needle = '    private static void pruneRollbackSnapshots(File root,File keep,int max){\n'
if needle not in pm:
    raise SystemExit('[FAIL] CerberusPrefixManager insertion point not found')
recovery = r'''    /** R6: recover only a prefix whose explicit first-boot transaction was interrupted. */
    public static boolean recoverInterruptedInitialization(Context context,Container container,CerberusRuntimeProfile profile){
        if(context==null||container==null||profile==null||!profile.isBionic())return true;
        File root=container.getRootDir();
        File init=new File(root,".cerberus-prefix-r6.initializing");
        if(!init.isFile())return true;
        File ready=new File(root,".cerberus-prefix-r6.ready");
        if(ready.isFile()){FileUtils.delete(init);return true;}

        File stageRoot=new File(root,".cerberus-prefix-r6-recovery");
        File stagedPrefix=new File(stageRoot,".wine");
        File current=new File(root,".wine");
        File failed=new File(root,".wine-interrupted-r6-"+System.currentTimeMillis());
        FileUtils.delete(stageRoot);
        if(!stageRoot.mkdirs())return false;
        boolean built=CerberusBionicContainerPattern.extract(context,profile,stageRoot);
        if(!built||!stagedPrefix.isDirectory()||!new File(stagedPrefix,"drive_c/windows/system32").isDirectory()||!new File(stagedPrefix,"system.reg").isFile()){
            FileUtils.delete(stageRoot);return false;
        }
        if(current.exists()&&!current.renameTo(failed)){FileUtils.delete(stageRoot);return false;}
        boolean committed=stagedPrefix.renameTo(current);
        if(!committed)committed=FileUtils.copy(stagedPrefix,current,file->FileUtils.chmod(file,0771));
        FileUtils.delete(stageRoot);
        if(!committed){FileUtils.delete(current);if(failed.exists())failed.renameTo(current);return false;}

        // Component payloads are prefix-specific and must be recreated by the
        // remainder of preflight after the clean runtime prefix is committed.
        FileUtils.delete(new File(root,".cerberus-fexcore.json"));
        FileUtils.delete(new File(root,".cerberus-wowbox64.json"));
        FileUtils.delete(new File(root,".cerberus-dxvk.json"));
        FileUtils.delete(new File(root,".cerberus-vkd3d.json"));
        FileUtils.delete(ready);
        FileUtils.delete(init);
        FileUtils.delete(failed);
        return true;
    }

'''
pm = pm.replace(needle, recovery + needle, 1)
# Any deliberate runtime/prefix switch invalidates the R6 bootstrap fingerprint.
switch_marker = '        FileUtils.delete(new File(root,".cerberus-fexcore.json"));FileUtils.delete(new File(root,".cerberus-wowbox64.json"));FileUtils.delete(new File(root,".cerberus-dxvk.json"));FileUtils.delete(new File(root,".cerberus-vkd3d.json"));\n'
if switch_marker not in pm:
    raise SystemExit('[FAIL] CerberusPrefixManager component marker line not found')
pm = pm.replace(switch_marker, switch_marker + '        FileUtils.delete(new File(root,".cerberus-prefix-r6.initializing"));FileUtils.delete(new File(root,".cerberus-prefix-r6.ready"));\n', 1)
prefix_manager.write_text(pm, encoding='utf-8')

pf = preflight.read_text(encoding='utf-8')
pf_needle = '        RootFS bionic=RootFS.findBionic(context);File root=bionic.getRootDir();File prefix=new File(container.getRootDir(),".wine");\n'
if pf_needle not in pf:
    raise SystemExit('[FAIL] CerberusRuntimePreflight R5 baseline not found')
pf = pf.replace(pf_needle, pf_needle + '        if(!CerberusPrefixManager.recoverInterruptedInitialization(context,container,p))return "Falha ao recuperar prefix Bionic interrompido";\n', 1)
preflight.write_text(pf, encoding='utf-8')

# Add an id to the complete Box64 fieldset so both the label and spinner are
# hidden together. The setting remains untouched for Classic/GLIBC containers.
xml = layout.read_text(encoding='utf-8')
box_block = '''                <FrameLayout\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:layout_marginTop="8dp">\n\n                    <LinearLayout style="@style/FieldSet">\n                        <TextView\n                            android:layout_width="wrap_content"\n                            android:layout_height="wrap_content"\n                            android:text="@string/box64_preset" />'''
box_block_r6 = '''                <FrameLayout\n                    android:id="@+id/LLBox64PresetPanel"\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:layout_marginTop="8dp">\n\n                    <LinearLayout style="@style/FieldSet">\n                        <TextView\n                            android:layout_width="wrap_content"\n                            android:layout_height="wrap_content"\n                            android:text="@string/box64_preset" />'''
if box_block not in xml:
    raise SystemExit('[FAIL] Box64 preset layout block not found')
xml = xml.replace(box_block, box_block_r6, 1)
layout.write_text(xml, encoding='utf-8')

fr = fragment.read_text(encoding='utf-8')
# Preserve the existing Classic preset when the field is hidden for Bionic + FEXCore.
old_box = '                String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);\n                String desktopTheme = getDesktopTheme(view);\n                CerberusRuntimeProfile pendingRuntime = readCerberusRuntimeUI(sRuntimeEngine, sRuntimeFamily, sRuntimeArch, sBackend32);\n'
new_box = '                String box64Preset = isEditMode() ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.DEFAULT);\n                String desktopTheme = getDesktopTheme(view);\n                CerberusRuntimeProfile pendingRuntime = readCerberusRuntimeUI(sRuntimeEngine, sRuntimeFamily, sRuntimeArch, sBackend32);\n                if (!(pendingRuntime.isBionic() && pendingRuntime.backend64 == RuntimeBackend.FEXCORE)) box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);\n'
if old_box not in fr:
    raise SystemExit('[FAIL] ContainerDetailFragment Box64 save baseline not found')
fr = fr.replace(old_box, new_box, 1)
ui_needle = '        View runtimePackagePanel = engine.getRootView().findViewById(R.id.LLCerberusRuntimePackage);\n        if (runtimePackagePanel != null) runtimePackagePanel.setVisibility(p.engine == RuntimeEngine.BIONIC ? View.VISIBLE : View.GONE);\n'
ui_r6 = ui_needle + '        View box64PresetPanel = engine.getRootView().findViewById(R.id.LLBox64PresetPanel);\n        boolean hideLegacyBox64Preset = p.engine == RuntimeEngine.BIONIC && p.backend64 == RuntimeBackend.FEXCORE;\n        if (box64PresetPanel != null) box64PresetPanel.setVisibility(hideLegacyBox64Preset ? View.GONE : View.VISIBLE);\n'
if ui_needle not in fr:
    raise SystemExit('[FAIL] ContainerDetailFragment runtime UI insertion point not found')
fr = fr.replace(ui_needle, ui_r6, 1)
fragment.write_text(fr, encoding='utf-8')

text = build.read_text(encoding='utf-8')
if 'versionCode 824' not in text or '8.2.0-Cerberus-Bionic-R5-Container-Boot' not in text:
    raise SystemExit('[FAIL] unexpected R5 build.gradle baseline')
text = text.replace('versionCode 824', 'versionCode 825', 1)
text = text.replace('8.2.0-Cerberus-Bionic-R5-Container-Boot', '8.2.0-Cerberus-Bionic-R6-Prefix-Proton-UI', 1)
build.write_text(text, encoding='utf-8')

print('[PASS] R6 transactional prefix lifecycle installed')
print('[PASS] interrupted prefix recovery stays in background preflight')
print('[PASS] Proton WCP uses its own Bionic Wine loader/prefixPack')
print('[PASS] ARM64EC keeps native Wine + FEXCore/WOWBox64 HODLL bootstrap')
print('[PASS] Box64 preset hidden only for Bionic + FEXCore; Classic/GLIBC preserved')
print('[PASS] versionCode=825')
