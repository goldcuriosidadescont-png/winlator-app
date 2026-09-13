package com.cerberus.bionicpc;

import android.app.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class SessionActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ContainerStore store;
    private ContainerStore.Profile profile;
    private TextView state, console, prefixState;
    private Button boot, probe, stop;
    private volatile Process current;
    private volatile boolean closing;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("container");
        try {
            store = new ContainerStore(this);
            profile = store.load(id);
        } catch (Exception e) { Ui.error(this, e); return; }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CerberusBionicPC:session");
        wakeLock.acquire(30 * 60 * 1000L);

        LinearLayout root = Ui.page(this);
        Ui.kicker(this, root, "CERBERUS  /  LIVE CONTAINER SESSION");
        Ui.title(this, root, profile.name, profile.resolution + " · " + profile.runtime + " · " + profile.renderer);

        LinearLayout health = Ui.card(this, root);
        Ui.label(this, health, "SESSION STATE");
        state = Ui.text(this, health, "IDLE", 18, Ui.TEXT);
        state.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        prefixState = Ui.text(this, health, prefixReport(), 12, Ui.MUTED);
        prefixState.setTypeface(Typeface.MONOSPACE);

        LinearLayout row = Ui.row(this, root);
        boot = Ui.button(this, row, "BOOT WINDOWS", true, this::bootWindows);
        probe = Ui.button(this, row, "CMD PROBE", false, this::windowsProbe);
        stop = Ui.button(this, row, "STOP", false, this::stopProcess);
        stop.setEnabled(false);

        LinearLayout desktop = Ui.card(this, root);
        Ui.label(this, desktop, "WINDOW BACKEND");
        Ui.text(this, desktop, "Console/Wine prefix: ATIVO", 13, Ui.TEXT);
        Ui.text(this, desktop, "X11 compositor + Vulkan surface: AINDA NÃO EMBUTIDO", 13, Ui.RED);
        Ui.text(this, desktop, "Esta tela não desenha um desktop falso. BOOT WINDOWS executa o Wine real via Box64 e inicializa o WINEPREFIX; CMD PROBE executa cmd.exe /c ver no mesmo container.", 12, Ui.DIM);

        LinearLayout logCard = Ui.card(this, root);
        Ui.label(this, logCard, "RUNTIME LOG");
        console = Ui.text(this, logCard, "Aguardando ação…\n", 11, Ui.MUTED);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextIsSelectable(true);

        if (!RuntimeInstaller.isInstalled(this)) {
            state.setText("RUNTIME MISSING");
            append("Box64/Proton não instalados. Volte e use INSTALAR RUNTIME.");
            setActions(false);
        } else {
            append("runtime=" + RuntimeInstaller.summary(this));
            append("box64=" + RuntimeInstaller.boxBinary(this));
            append("wine=" + RuntimeInstaller.wineBinary(this));
            append("prefix=" + safePrefix());
            worker.execute(this::runtimeVersion);
        }
    }

    private void runtimeVersion() {
        try {
            setState("PROBING RUNTIME");
            Result r = runWine(new String[]{"--version"}, 30);
            append("[wine --version] exit=" + r.code);
            append(r.output.trim());
            setState(r.code == 0 ? "RUNTIME READY" : "RUNTIME FAILED");
        } catch (Exception e) { fail(e); }
    }

    private void bootWindows() {
        setActions(false);
        worker.execute(() -> {
            try {
                setState("BOOTING WINEPREFIX");
                append("--- wineboot -u ---");
                Result r = runWine(new String[]{"wineboot", "-u"}, 180);
                append("wineboot exit=" + r.code);
                if (!r.output.trim().isEmpty()) append(r.output.trim());
                refreshPrefix();
                if (r.code != 0) throw new IOException("wineboot falhou com exit=" + r.code);
                File prefix = store.prefix(profile.id);
                boolean ready = new File(prefix, "system.reg").isFile()
                        && new File(prefix, "user.reg").isFile()
                        && new File(prefix, "drive_c/windows").isDirectory();
                if (!ready) throw new IOException("wineboot terminou, mas o prefixo ficou incompleto.");
                setState("WINDOWS PREFIX READY");
                append("CERBERUS_PREFIX_READY");
            } catch (Exception e) { fail(e); }
            finally { runOnUiThread(() -> setActions(true)); }
        });
    }

    private void windowsProbe() {
        setActions(false);
        worker.execute(() -> {
            try {
                setState("RUNNING CMD.EXE");
                Result r = runWine(new String[]{"cmd", "/c", "echo CERBERUS_WINDOWS_OK && ver"}, 60);
                append("cmd.exe exit=" + r.code);
                append(r.output.trim());
                if (r.code != 0) throw new IOException("cmd.exe falhou com exit=" + r.code);
                setState("WINDOWS USERLAND OK");
            } catch (Exception e) { fail(e); }
            finally { runOnUiThread(() -> setActions(true)); }
        });
    }

    private Result runWine(String[] wineArgs, int timeoutSeconds) throws Exception {
        File box = RuntimeInstaller.boxBinary(this);
        File wine = RuntimeInstaller.wineBinary(this);
        if (!box.isFile() || !wine.isFile()) throw new FileNotFoundException("Runtime incompleto.");

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(box.getAbsolutePath());
        cmd.add(wine.getAbsolutePath());
        Collections.addAll(cmd, wineArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(store.dir(profile.id));
        Map<String,String> env = pb.environment();
        File proton = RuntimeInstaller.protonRoot(this);
        File prefix = store.prefix(profile.id);
        File home = store.home(profile.id);
        File tmp = store.tmp(profile.id);

        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", tmp.getAbsolutePath());
        env.put("XDG_RUNTIME_DIR", tmp.getAbsolutePath());
        env.put("WINEPREFIX", prefix.getAbsolutePath());
        env.put("WINEDEBUG", "-all");
        env.put("WINEDLLOVERRIDES", "winemenubuilder.exe=d");
        env.put("LC_ALL", "C.UTF-8");
        env.put("PATH", new File(proton, "bin").getAbsolutePath() + ":/system/bin");
        env.put("BOX64_PATH", new File(proton, "bin").getAbsolutePath());
        env.put("BOX64_NOBANNER", "1");
        env.put("BOX64_LOG", "0");
        env.put("BOX64_DYNAREC", "1");
        env.put("BOX64_DYNAREC_BIGBLOCK", "2");
        env.put("BOX64_LD_LIBRARY_PATH", libraryPath(proton));
        env.put("CERBERUS_CONTAINER", profile.id);
        env.put("CERBERUS_RESOLUTION", profile.resolution);
        env.put("CERBERUS_RENDERER", profile.renderer);
        env.put("CERBERUS_DX_MODE", profile.dxMode);
        env.put("CERBERUS_MEMORY_MIB", Integer.toString(profile.memoryMiB));

        append("$ " + String.join(" ", cmd));
        Process p = pb.start();
        current = p;
        runOnUiThread(() -> stop.setEnabled(true));
        StringBuilder output = new StringBuilder();
        Thread drainer = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (output.length() < 256 * 1024) output.append(line).append('\n');
                    append(line);
                }
            } catch (IOException ignored) {}
        }, "cerberus-runtime-log");
        drainer.start();

        boolean exited = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!exited) {
            p.destroy();
            if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly();
            drainer.join(2000);
            current = null;
            throw new IOException("Timeout de " + timeoutSeconds + "s executando Wine.");
        }
        drainer.join(3000);
        int code = p.exitValue();
        current = null;
        runOnUiThread(() -> stop.setEnabled(false));
        persistLog(output.toString());
        return new Result(code, output.toString());
    }

    private static String libraryPath(File proton) {
        ArrayList<String> libs = new ArrayList<>();
        File lib = new File(proton, "lib");
        File lib64 = new File(proton, "lib64");
        if (lib.isDirectory()) libs.add(lib.getAbsolutePath());
        if (lib64.isDirectory()) libs.add(lib64.getAbsolutePath());
        File wineLib = new File(lib, "wine/x86_64-unix");
        if (wineLib.isDirectory()) libs.add(wineLib.getAbsolutePath());
        return String.join(":", libs);
    }

    private void persistLog(String text) {
        try {
            File f = new File(store.logs(profile.id), "last-runtime.log");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(text); }
        } catch (Exception ignored) {}
    }

    private String prefixReport() {
        try {
            File p = store.prefix(profile.id);
            return "prefix=" + p + "\n"
                    + "system.reg=" + new File(p, "system.reg").isFile() + "  "
                    + "user.reg=" + new File(p, "user.reg").isFile() + "  "
                    + "windows=" + new File(p, "drive_c/windows").isDirectory();
        } catch (Exception e) { return "prefix=ERROR: " + e.getMessage(); }
    }

    private String safePrefix() {
        try { return store.prefix(profile.id).getAbsolutePath(); }
        catch (Exception e) { return "<error>"; }
    }

    private void refreshPrefix() { runOnUiThread(() -> prefixState.setText(prefixReport())); }
    private void setState(String s) { runOnUiThread(() -> state.setText(s)); }

    private void append(String s) {
        if (s == null || s.isEmpty()) return;
        runOnUiThread(() -> {
            if (closing) return;
            CharSequence old = console.getText();
            String next = old + s + "\n";
            if (next.length() > 90000) next = next.substring(next.length() - 70000);
            console.setText(next);
        });
    }

    private void fail(Exception e) {
        setState("FAILED");
        append("ERROR: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    private void setActions(boolean enabled) {
        runOnUiThread(() -> {
            boot.setEnabled(enabled && RuntimeInstaller.isInstalled(this));
            probe.setEnabled(enabled && RuntimeInstaller.isInstalled(this));
        });
    }

    private void stopProcess() {
        Process p = current;
        if (p != null) {
            append("STOP requested");
            p.destroy();
            worker.execute(() -> {
                try { if (!p.waitFor(1500, TimeUnit.MILLISECONDS)) p.destroyForcibly(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
    }

    @Override protected void onDestroy() {
        closing = true;
        Process p = current;
        if (p != null) p.destroyForcibly();
        worker.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    private static final class Result {
        final int code; final String output;
        Result(int code, String output) { this.code = code; this.output = output; }
    }
}
