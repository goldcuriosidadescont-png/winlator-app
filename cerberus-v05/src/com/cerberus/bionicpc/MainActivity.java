package com.cerberus.bionicpc;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.system.Os;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button bootstrapButton;
    private Button versionButton;
    private Button winebootButton;
    private Button cmdButton;

    private File cerberusRoot;
    private File runtimeRoot;
    private File box64;
    private File wine;
    private File prefix;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        resolvePaths();
        refreshStatus("READY: bootstrap runtime");
        worker.execute(() -> bootstrap(false));
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(13, 11, 9));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(32), dp(22), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CERBERUS BIONIC PC\nWINDOWS RUNTIME 0.5.0");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.rgb(224, 216, 205));
        status.setTextSize(14);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(0, dp(24), 0, dp(20));
        root.addView(status);

        bootstrapButton = button("BOOTSTRAP CONTAINER");
        versionButton = button("TEST WINE VERSION");
        winebootButton = button("RUN WINEBOOT");
        cmdButton = button("RUN WINDOWS CMD /C VER");

        root.addView(bootstrapButton);
        root.addView(versionButton);
        root.addView(winebootButton);
        root.addView(cmdButton);

        bootstrapButton.setOnClickListener(v -> worker.execute(() -> bootstrap(true)));
        versionButton.setOnClickListener(v -> worker.execute(this::testWineVersion));
        winebootButton.setOnClickListener(v -> worker.execute(this::runWineboot));
        cmdButton.setOnClickListener(v -> worker.execute(this::runWindowsVersion));

        setContentView(scroll);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void resolvePaths() {
        cerberusRoot = new File(getFilesDir(), "cerberus");
        runtimeRoot = new File(cerberusRoot, "runtime");
        box64 = new File(runtimeRoot, "box64/box64");
        wine = new File(runtimeRoot, "proton/bin/wine");
        prefix = new File(cerberusRoot, "containers/default/prefix");
    }

    private void bootstrap(boolean force) {
        try {
            setButtons(false);
            log("CERBERUS BOOTSTRAP");
            log("HOST: ARM64 / ANDROID BIONIC");
            log("CONTAINER: default");

            File marker = new File(runtimeRoot, ".v050-installed");
            if (force && marker.exists()) marker.delete();

            if (!marker.exists()) {
                log("RUNTIME: installing embedded Box64 + Proton-Wine...");
                deleteRecursively(runtimeRoot);
                runtimeRoot.mkdirs();
                copyAssetTree("runtime", runtimeRoot);
                chmodRuntime();
                marker.createNewFile();
                log("RUNTIME: INSTALLED");
            } else {
                chmodRuntime();
                log("RUNTIME: PRESENT");
            }

            new File(cerberusRoot, "tmp").mkdirs();
            prefix.mkdirs();

            log("BOX64: " + passFail(box64.isFile()));
            log("PROTON WINE: " + passFail(wine.isFile()));
            log("PREFIX ROOT: " + passFail(prefix.isDirectory()));

            if (!box64.isFile() || !wine.isFile()) {
                log("FATAL: embedded runtime incomplete");
                return;
            }

            CommandResult version = run(Arrays.asList(box64.getAbsolutePath(), wine.getAbsolutePath(), "--version"), 30);
            log("WINE VERSION RC=" + version.code);
            appendOutput(version.output);

            if (version.code == 0) {
                File winebootOk = new File(prefix, ".cerberus-wineboot-ok");
                if (!winebootOk.exists()) {
                    log("WINEBOOT: initializing C:\\ ...");
                    CommandResult wb = run(Arrays.asList(box64.getAbsolutePath(), wine.getAbsolutePath(), "wineboot", "-u"), 180);
                    log("WINEBOOT RC=" + wb.code);
                    appendOutput(wb.output);
                    if (wb.code == 0) winebootOk.createNewFile();
                } else {
                    log("WINEBOOT: PASS (cached)");
                }
            }

            summarize();
        } catch (Throwable t) {
            log("BOOTSTRAP ERROR: " + t);
        } finally {
            setButtons(true);
        }
    }

    private void testWineVersion() {
        if (!runtimeReady()) return;
        setButtons(false);
        CommandResult r = run(Arrays.asList(box64.getAbsolutePath(), wine.getAbsolutePath(), "--version"), 30);
        log("wine --version => RC=" + r.code);
        appendOutput(r.output);
        setButtons(true);
    }

    private void runWineboot() {
        if (!runtimeReady()) return;
        setButtons(false);
        CommandResult r = run(Arrays.asList(box64.getAbsolutePath(), wine.getAbsolutePath(), "wineboot", "-u"), 180);
        log("wineboot -u => RC=" + r.code);
        appendOutput(r.output);
        if (r.code == 0) {
            try { new File(prefix, ".cerberus-wineboot-ok").createNewFile(); } catch (Exception ignored) {}
        }
        summarize();
        setButtons(true);
    }

    private void runWindowsVersion() {
        if (!runtimeReady()) return;
        setButtons(false);
        CommandResult r = run(Arrays.asList(box64.getAbsolutePath(), wine.getAbsolutePath(), "cmd", "/c", "ver"), 60);
        log("cmd /c ver => RC=" + r.code);
        appendOutput(r.output);
        setButtons(true);
    }

    private boolean runtimeReady() {
        if (!box64.isFile() || !wine.isFile()) {
            log("Runtime not installed. Run BOOTSTRAP CONTAINER first.");
            return false;
        }
        return true;
    }

    private void summarize() {
        File driveC = new File(prefix, "drive_c");
        File systemReg = new File(prefix, "system.reg");
        File ok = new File(prefix, ".cerberus-wineboot-ok");
        log("");
        log("=== CERBERUS CONTAINER STATUS ===");
        log("BOX64 BIONIC: " + passFail(box64.isFile()));
        log("PROTON 11: " + passFail(wine.isFile()));
        log("CONTAINER DEFAULT: " + passFail(prefix.isDirectory()));
        log("DRIVE C: " + passFail(driveC.isDirectory()));
        log("REGISTRY: " + passFail(systemReg.isFile()));
        log("WINEBOOT: " + passFail(ok.isFile()));
        log("GRAPHICS/X11: NEXT STAGE");
    }

    private String passFail(boolean v) { return v ? "PASS" : "WAIT"; }

    private void chmodRuntime() throws Exception {
        if (box64.exists()) Os.chmod(box64.getAbsolutePath(), 0700);
        File bin = new File(runtimeRoot, "proton/bin");
        File[] files = bin.listFiles();
        if (files != null) {
            for (File f : files) if (f.isFile()) {
                try { Os.chmod(f.getAbsolutePath(), 0700); } catch (Throwable ignored) {}
            }
        }
    }

    private Map<String,String> environment() {
        Map<String,String> env = new HashMap<>(System.getenv());
        File proton = new File(runtimeRoot, "proton");
        File compat = new File(runtimeRoot, "compatlib");
        File tmp = new File(cerberusRoot, "tmp");
        tmp.mkdirs();

        String guestLib = new File(proton, "lib").getAbsolutePath();
        String wineUnix = new File(proton, "lib/wine/x86_64-unix").getAbsolutePath();
        env.put("HOME", cerberusRoot.getAbsolutePath());
        env.put("TMPDIR", tmp.getAbsolutePath());
        env.put("WINEPREFIX", prefix.getAbsolutePath());
        env.put("WINEDEBUG", "-all");
        env.put("LC_ALL", "C.UTF-8");
        env.put("BOX64_NOBANNER", "1");
        env.put("BOX64_DYNAREC", "1");
        env.put("BOX64_DYNAREC_BIGBLOCK", "2");
        env.put("BOX64_DYNAREC_SAFEFLAGS", "1");
        env.put("BOX64_AVX", "2");
        env.put("BOX64_PATH", new File(proton, "bin").getAbsolutePath());
        env.put("BOX64_LD_LIBRARY_PATH", guestLib + ":" + wineUnix + ":" + compat.getAbsolutePath());
        env.put("PROTON_NO_KERNEL_NTSYNC", "1");
        return env;
    }

    private CommandResult run(List<String> command, int timeoutSeconds) {
        StringBuilder out = new StringBuilder();
        int code = -999;
        Process process = null;
        try {
            log("$ " + String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command));
            pb.directory(cerberusRoot);
            pb.redirectErrorStream(true);
            Map<String,String> target = pb.environment();
            target.clear();
            target.putAll(environment());
            process = pb.start();

            final Process p = process;
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(timeoutSeconds * 1000L);
                    try { p.exitValue(); } catch (IllegalThreadStateException alive) { p.destroy(); }
                } catch (InterruptedException ignored) {}
            });
            killer.setDaemon(true);
            killer.start();

            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null) {
                if (lines++ < 120) out.append(line).append('\n');
            }
            code = process.waitFor();
            killer.interrupt();
        } catch (Throwable t) {
            out.append(t.toString());
            if (process != null) process.destroy();
        }
        return new CommandResult(code, out.toString());
    }

    private void copyAssetTree(String assetPath, File dst) throws Exception {
        AssetManager am = getAssets();
        String[] children = am.list(assetPath);
        if (children != null && children.length > 0) {
            if (!dst.exists() && !dst.mkdirs()) throw new IllegalStateException("mkdir failed: " + dst);
            for (String child : children) {
                copyAssetTree(assetPath + "/" + child, new File(dst, child));
            }
            return;
        }

        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = am.open(assetPath, AssetManager.ACCESS_STREAMING);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void deleteRecursively(File f) {
        if (!f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursively(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private void appendOutput(String text) {
        if (text == null || text.trim().isEmpty()) return;
        for (String line : text.split("\\r?\\n")) if (!line.isEmpty()) log("  " + line);
    }

    private void refreshStatus(String text) {
        runOnUiThread(() -> status.setText(text + "\n"));
    }

    private void log(String line) {
        runOnUiThread(() -> status.append(line + "\n"));
    }

    private void setButtons(boolean enabled) {
        runOnUiThread(() -> {
            bootstrapButton.setEnabled(enabled);
            versionButton.setEnabled(enabled);
            winebootButton.setEnabled(enabled);
            cmdButton.setEnabled(enabled);
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private static final class CommandResult {
        final int code;
        final String output;
        CommandResult(int code, String output) { this.code = code; this.output = output; }
    }
}
