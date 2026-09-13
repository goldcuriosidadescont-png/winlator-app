package dev.cerberus.pc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int OPEN_C86 = 10, INSPECT_PE = 11;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private GameView game;
    private FrameLayout viewport;
    private TextView status;
    private boolean resumed, pausedByUser, destroyed;
    private int requestGeneration;
    private Button pauseButton;
    private byte[] lastProgram;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, dp(5), 0, dp(5)); return v;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v -> action.run()); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(0xff0c1020);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(20), dp(22), dp(24));
        scroll.addView(body); setContentView(scroll);
        // Handle enforced edge-to-edge on target 35, including display cutouts.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            }
            v.setPadding(left, top, right, bottom); return insets;
        });
        scroll.requestApplyInsets();
        TextView wordmark = text("CERBERUS / PC", 30, Color.WHITE);
        wordmark.setTypeface(Typeface.DEFAULT, Typeface.BOLD); body.addView(wordmark);
        body.addView(text("GENESIS 0.1  •  ANDROID ARM64", 12, 0xff45e5bb));
        body.addView(text("Nosso primeiro nucleo x86.", 21, Color.WHITE));
        body.addView(text("Orbit e a demo jogavel do motor. Arraste o dedo para rebater a esfera; a barra superior marca os acertos.", 14, 0xffadb8d0));
        viewport = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xff080c17); background.setCornerRadius(dp(14));
        viewport.setBackground(background); viewport.setClipToOutline(true);
        body.addView(viewport, new LinearLayout.LayoutParams(-1, dp(250)));
        status = text("Preparando demo x86…", 12, 0xff45e5bb); body.addView(status);
        LinearLayout controls = new LinearLayout(this);
        Button restart = button("Reiniciar", () -> { if (lastProgram != null) start(lastProgram); });
        pauseButton = button("Pausar", () -> { pausedByUser = !pausedByUser; syncGame(); });
        controls.addView(restart, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(pauseButton, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(controls);
        body.addView(button("Executar demo Orbit", this::loadDemo));
        body.addView(button("Abrir programa .c86", () -> pick(OPEN_C86)));
        body.addView(button("Inspecionar arquivo .exe", () -> pick(INSPECT_PE)));
        body.addView(text("ESTADO DO PROJETO", 12, 0xffa38aff));
        body.addView(text("CPU x86 parcial • framebuffer 2D • toque e gamepad\nWindows, DirectX, x86-64 e jogos comerciais: ainda indisponiveis.", 14, 0xffadb8d0));
        TextView footer = text("Codigo proprio. Sem root. Sem servico em segundo plano.", 12, 0xff73829f);
        footer.setGravity(Gravity.CENTER); body.addView(footer);
        loadDemo();
    }
    private static byte[] read(InputStream input, int limit, boolean prefixOnly) throws IOException {
        if (input == null) throw new IOException("Nao foi possivel abrir o arquivo");
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            while (out.size() < limit) {
                int n = in.read(buffer, 0, Math.min(buffer.length, limit - out.size()));
                if (n < 0) return out.toByteArray();
                if (n == 0) { int one = in.read(); if (one < 0) return out.toByteArray(); out.write(one); }
                else out.write(buffer, 0, n);
            }
            if (!prefixOnly && in.read() != -1) throw new IOException("Programa excede 1 MiB + cabecalho");
            return out.toByteArray();
        }
    }
    private void loadDemo() {
        final int generation = ++requestGeneration;
        io.execute(() -> {
            try {
                byte[] bytes = read(getAssets().open("orbit.c86"), 1024 * 1024 + 24, false);
                runOnUiThread(() -> { if (!destroyed && generation == requestGeneration) start(bytes); });
            } catch (IOException e) { showError(e.getMessage()); }
        });
    }
    private void start(byte[] bytes) {
        if (destroyed) return;
        if (game != null) game.close();
        viewport.removeAllViews(); lastProgram = bytes; pausedByUser = false;
        game = new GameView(this, bytes, value -> status.setText(value));
        viewport.addView(game, new FrameLayout.LayoutParams(-1, -1));
        status.setText("Executando programa x86…"); syncGame(); game.requestFocus();
    }
    private void syncGame() {
        if (game == null) return;
        boolean active = resumed && !pausedByUser;
        if (active) game.resume();
        else game.pause();
        pauseButton.setText(pausedByUser ? "Continuar" : "Pausar");
    }
    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        startActivityForResult(intent, request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null ||
            (request != OPEN_C86 && request != INSPECT_PE)) return;
        final Uri uri = data.getData();
        final int generation = request == OPEN_C86 ? ++requestGeneration : requestGeneration;
        io.execute(() -> {
            try {
                byte[] bytes = read(getContentResolver().openInputStream(uri), 1024 * 1024 + 24, request == INSPECT_PE);
                if (request == OPEN_C86) runOnUiThread(() -> { if (!destroyed && generation == requestGeneration) start(bytes); });
                else {
                    String info = PeInspector.inspect(bytes);
                    runOnUiThread(() -> { if (!destroyed) new AlertDialog.Builder(this).setTitle("Inspecao PE")
                        .setMessage(info).setPositiveButton("Fechar", null).show(); });
                }
            } catch (IOException | SecurityException e) { showError(e.getMessage()); }
        });
    }
    private void showError(String error) {
        runOnUiThread(() -> { if (!destroyed) status.setText("Erro: " + error); });
    }
    @Override protected void onResume() { super.onResume(); resumed = true; syncGame(); }
    @Override protected void onPause() { resumed = false; syncGame(); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed = true; if (game != null) game.close(); io.shutdown(); super.onDestroy();
    }
}
