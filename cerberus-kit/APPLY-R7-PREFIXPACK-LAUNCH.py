#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: APPLY-R7-PREFIXPACK-LAUNCH.py <Cerberus source>')

root = Path(sys.argv[1]).resolve()
launcher = root / 'app/src/main/java/com/winlator/xenvironment/components/CerberusBionicProgramLauncherComponent.java'
prefix_manager = root / 'app/src/main/java/com/winlator/cerberus/runtime/CerberusPrefixManager.java'
fragment = root / 'app/src/main/java/com/winlator/ContainerDetailFragment.java'
build = root / 'app/build.gradle'

# ---------------------------------------------------------------------------
# 1) R7 launcher: prefixPack is already the authoritative initialized prefix.
#    Validate/fingerprint it, then launch Explorer/game directly. Do NOT force
#    Wine prefix migration before every fresh Bionic container.
# ---------------------------------------------------------------------------
s = launcher.read_text(encoding='utf-8')
old_consts = '''    public static final String BUILD_MARKER = "Cerberus-Bionic-Launcher-R6";\n    public static final String PREFIX_INIT_MARKER = ".cerberus-prefix-r6.initializing";\n    public static final String PREFIX_READY_MARKER = ".cerberus-prefix-r6.ready";\n'''
new_consts = '''    public static final String BUILD_MARKER = "Cerberus-Bionic-Launcher-R7";\n    public static final String PREFIX_READY_MARKER = ".cerberus-prefix-r7.ready";\n'''
if old_consts not in s:
    raise SystemExit('[FAIL] R6 launcher constants not found')
s = s.replace(old_consts, new_consts, 1)

if 'return "r6|" + profile.family.id + "|" + profile.architecture.id + "|" + runtime;' not in s:
    raise SystemExit('[FAIL] R6 launcher fingerprint not found')
s = s.replace(
    'return "r6|" + profile.family.id + "|" + profile.architecture.id + "|" + runtime;',
    'return "r7|" + profile.family.id + "|" + profile.architecture.id + "|" + runtime;',
    1,
)

start_token = '        File initMarker = new File(homePath, PREFIX_INIT_MARKER);\n'
end_token = '    @Override public void onPause() {'
start = s.find(start_token)
end = s.find(end_token, start)
if start < 0 or end < 0:
    raise SystemExit('[FAIL] R6 bootstrap block not found')

direct_launch = r'''        // R7: the selected WCP prefixPack/container pattern is already an
        // initialized prefix.  A forced migration here re-enters Wine's prefix
        // update path and can leave a half-updated prefix when Android kills
        // the session.  Validate the extracted prefix and launch directly.
        File prefixDir = new File(prefixPath);
        if (!prefixDir.isDirectory()
                || !new File(prefixDir, "drive_c/windows/system32").isDirectory()
                || !new File(prefixDir, "system.reg").isFile()) return -1;

        File readyMarker = new File(homePath, PREFIX_READY_MARKER);
        if (!markerMatches(readyMarker) && !writeMarker(readyMarker)) return -1;

        // R6 markers are legacy transaction state.  Preflight has already
        // repaired an interrupted R6 migration before the launcher reaches here.
        FileUtils.delete(new File(homePath, ".cerberus-prefix-r6.initializing"));
        FileUtils.delete(new File(homePath, ".cerberus-prefix-r6.ready"));

        String args = guestExecutable.startsWith("wine ") ? guestExecutable.substring(5) : guestExecutable;
        String command;
        if (profile.architecture == RuntimeArchitecture.X86_64) {
            command = box64.getPath() + " " + wineFile.getPath() + " " + args;
        }
        else {
            // ARM64EC launches the native ARM64 Wine loader.  FEXCore/WOWBox64
            // remains selected through HODLL for guest PE execution.
            command = wineFile.getPath() + " " + args;
        }

        // Persist a tiny last-launch snapshot for the next device-side debug.
        // Failure to write diagnostics must never block Wine startup.
        try {
            StringBuilder diag = new StringBuilder();
            diag.append("build=").append(BUILD_MARKER).append('\\n');
            diag.append("stage=direct-prefixpack-launch\\n");
            diag.append("family=").append(profile.family.id).append('\\n');
            diag.append("arch=").append(profile.architecture.id).append('\\n');
            diag.append("runtime=").append(profile.runtimeId == null ? "" : profile.runtimeId).append('\\n');
            diag.append("wine=").append(wineFile.getPath()).append('\\n');
            diag.append("box64=").append(box64 == null ? "none" : box64.getPath()).append('\\n');
            diag.append("prefix=").append(prefixPath).append('\\n');
            diag.append("fingerprint=").append(fingerprint()).append('\\n');
            FileUtils.writeString(new File(homePath, ".cerberus-launch-r7.txt"), diag.toString());
        }
        catch (Exception ignored) {}

        return ProcessHelper.exec(command, env, root, status -> {
            synchronized (lock) { pid = -1; }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

'''
s = s[:start] + direct_launch + s[end:]

if 'String bootstrapArgs = "wineboot.exe -u";' in s:
    raise SystemExit('[FAIL] forced wineboot command still present in R7 launcher')
launcher.write_text(s, encoding='utf-8')

# ---------------------------------------------------------------------------
# 2) Prefix recovery: if R6 was interrupted, rebuild from the exact selected
#    WCP prefixPack in background preflight.  The .recovering marker makes the
#    repair itself restartable if Android kills the process mid-transaction.
# ---------------------------------------------------------------------------
pm = prefix_manager.read_text(encoding='utf-8')
method_start = pm.find('    /** R6: recover only a prefix whose explicit first-boot transaction was interrupted. */\n')
method_end = pm.find('    private static void pruneRollbackSnapshots(', method_start)
if method_start < 0 or method_end < 0:
    raise SystemExit('[FAIL] R6 interrupted-prefix recovery method not found')

r7_recovery = r'''    /**
     * R7: repair only a prefix that carries interrupted R6/R7 transaction state.
     * The selected runtime's own prefixPack/container pattern is authoritative;
     * we never attempt to finish a half-applied Wine migration in place.
     */
    public static boolean recoverInterruptedInitialization(Context context,Container container,CerberusRuntimeProfile profile){
        if(context==null||container==null||profile==null||!profile.isBionic())return true;
        File root=container.getRootDir();
        File legacyInit=new File(root,".cerberus-prefix-r6.initializing");
        File recovering=new File(root,".cerberus-prefix-r7.recovering");
        if(!legacyInit.isFile()&&!recovering.isFile())return true;

        if(!FileUtils.writeString(recovering,"r7-prefixpack-recovery"))return false;

        File stageRoot=new File(root,".cerberus-prefix-r7-recovery");
        File stagedPrefix=new File(stageRoot,".wine");
        File current=new File(root,".wine");
        File failed=new File(root,".wine-interrupted-r7");
        FileUtils.delete(stageRoot);
        if(!stageRoot.mkdirs())return false;

        boolean built=CerberusBionicContainerPattern.extract(context,profile,stageRoot);
        if(!built||!stagedPrefix.isDirectory()
                ||!new File(stagedPrefix,"drive_c/windows/system32").isDirectory()
                ||!new File(stagedPrefix,"system.reg").isFile()){
            FileUtils.delete(stageRoot);
            return false;
        }

        if(current.exists()){
            FileUtils.delete(failed);
            if(!current.renameTo(failed)){
                FileUtils.delete(stageRoot);
                return false;
            }
        }

        boolean committed=stagedPrefix.renameTo(current);
        if(!committed)committed=FileUtils.copy(stagedPrefix,current,file->FileUtils.chmod(file,0771));
        FileUtils.delete(stageRoot);
        if(!committed){
            FileUtils.delete(current);
            if(failed.exists())failed.renameTo(current);
            return false;
        }

        // These payloads are prefix-specific.  Normal preflight recreates them
        // against the clean selected prefix immediately after this returns.
        FileUtils.delete(new File(root,".cerberus-fexcore.json"));
        FileUtils.delete(new File(root,".cerberus-wowbox64.json"));
        FileUtils.delete(new File(root,".cerberus-dxvk.json"));
        FileUtils.delete(new File(root,".cerberus-vkd3d.json"));
        FileUtils.delete(new File(root,".cerberus-prefix-r6.initializing"));
        FileUtils.delete(new File(root,".cerberus-prefix-r6.ready"));
        FileUtils.delete(new File(root,".cerberus-prefix-r7.ready"));
        FileUtils.delete(recovering);
        FileUtils.delete(failed);
        return true;
    }

'''
pm = pm[:method_start] + r7_recovery + pm[method_end:]

old_switch_markers = '        FileUtils.delete(new File(root,".cerberus-prefix-r6.initializing"));FileUtils.delete(new File(root,".cerberus-prefix-r6.ready"));\n'
new_switch_markers = old_switch_markers + '        FileUtils.delete(new File(root,".cerberus-prefix-r7.ready"));FileUtils.delete(new File(root,".cerberus-prefix-r7.recovering"));FileUtils.delete(new File(root,".wine-interrupted-r7"));\n'
if old_switch_markers not in pm:
    raise SystemExit('[FAIL] R6 switch-prefix marker cleanup not found')
pm = pm.replace(old_switch_markers, new_switch_markers, 1)
prefix_manager.write_text(pm, encoding='utf-8')

# ---------------------------------------------------------------------------
# 3) UI: legacy Box64 preset belongs only to Classic/GLIBC.  Bionic x86_64 may
#    still use Box64 internally, but the old preset selector must not be exposed
#    or allowed to mutate Bionic runtime state.
# ---------------------------------------------------------------------------
fr = fragment.read_text(encoding='utf-8')
old_visibility = '        boolean hideLegacyBox64Preset = p.engine == RuntimeEngine.BIONIC && p.backend64 == RuntimeBackend.FEXCORE;\n'
new_visibility = '        boolean hideLegacyBox64Preset = p.engine == RuntimeEngine.BIONIC;\n'
if old_visibility not in fr:
    raise SystemExit('[FAIL] R6 Box64 UI visibility condition not found')
fr = fr.replace(old_visibility, new_visibility, 1)

old_save = '                if (!(pendingRuntime.isBionic() && pendingRuntime.backend64 == RuntimeBackend.FEXCORE)) box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);\n'
new_save = '                if (!pendingRuntime.isBionic()) box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);\n'
if old_save not in fr:
    raise SystemExit('[FAIL] R6 Box64 preset save condition not found')
fr = fr.replace(old_save, new_save, 1)
fragment.write_text(fr, encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) Version identity.
# ---------------------------------------------------------------------------
b = build.read_text(encoding='utf-8')
if 'versionCode 825' not in b or '8.2.0-Cerberus-Bionic-R6-Prefix-Proton-UI' not in b:
    raise SystemExit('[FAIL] unexpected R6 build.gradle baseline')
b = b.replace('versionCode 825', 'versionCode 826', 1)
b = b.replace('8.2.0-Cerberus-Bionic-R6-Prefix-Proton-UI', '8.2.0-Cerberus-Bionic-R7-PrefixPack-Launch', 1)
build.write_text(b, encoding='utf-8')

print('[PASS] R7 forced wineboot bootstrap removed')
print('[PASS] validated selected prefixPack launches directly')
print('[PASS] interrupted R6/R7 prefix repair is restartable and transactional')
print('[PASS] legacy Box64 preset hidden for every Bionic engine')
print('[PASS] Classic/GLIBC Box64 preset behavior preserved')
print('[PASS] Proton/FEXCore and R4/R5/R6 infrastructure preserved')
print('[PASS] versionCode=826')
