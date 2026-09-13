package com.cerberus.bionicpc;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ContainerStore store;
    private LinearLayout containerList;
    private TextView runtimeState;
    private Button installRuntime;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        try { store = new ContainerStore(this); }
        catch (Exception e) { Ui.error(this, e); return; }

        LinearLayout root = Ui.page(this);
        Ui.kicker(this, root, "CERBERUS BIONIC PC  /  v0.6");
        Ui.title(this, root, "Containers", "Prefixos Wine isolados, runtime Bionic e sessão por contêiner.");

        LinearLayout runtime = Ui.card(this, root);
        Ui.label(this, runtime, "RUNTIME");
        runtimeState = Ui.text(this, runtime, RuntimeInstaller.summary(this), 14, Ui.MUTED);
        installRuntime = Ui.button(this, runtime, "INSTALAR RUNTIME", true, this::installRuntime);
        Ui.text(this, runtime, "Box64 0.4.4 Bionic + Proton-Wine 11.0-2. Downloads oficiais são verificados por SHA-256 antes da extração.", 12, Ui.DIM);

        LinearLayout actions = Ui.row(this, root);
        Ui.button(this, actions, "+ NOVO CONTAINER", true, () -> startActivity(new Intent(this, ContainerEditorActivity.class)));
        Ui.button(this, actions, "ATUALIZAR", false, this::refresh);

        Ui.section(this, root, "SEUS CONTAINERS");
        containerList = new LinearLayout(this);
        containerList.setOrientation(LinearLayout.VERTICAL);
        root.addView(containerList, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout info = Ui.card(this, root);
        Ui.label(this, info, "ENGINE STATUS");
        Ui.text(this, info, "Nesta build o lifecycle do container e o Wine/Box64 já são reais. A sessão executa wine --version, wineboot e cmd.exe dentro do WINEPREFIX. O compositor X11/Vulkan embutido para janelas 3D será o próximo backend; a tela desta v0.6 não simula janelas Windows.", 12, Ui.DIM);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        runtimeState.setText(RuntimeInstaller.summary(this));
        installRuntime.setText(RuntimeInstaller.isInstalled(this) ? "REINSTALAR / VALIDAR" : "INSTALAR RUNTIME");
        containerList.removeAllViews();
        try {
            List<ContainerStore.Profile> profiles = store.list();
            if (profiles.isEmpty()) {
                LinearLayout empty = Ui.card(this, containerList);
                Ui.text(this, empty, "Nenhum container criado.", 17, Ui.TEXT);
                Ui.text(this, empty, "Crie um perfil para gerar prefix/, drive_c/, home/, tmp/ e logs/ independentes.", 12, Ui.DIM);
                return;
            }
            for (ContainerStore.Profile p : profiles) renderContainer(p);
        } catch (Exception e) { Ui.error(this, e); }
    }

    private void renderContainer(ContainerStore.Profile p) {
        LinearLayout card = Ui.card(this, containerList);
        Ui.text(this, card, p.name, 20, Ui.TEXT);
        Ui.text(this, card, p.resolution + "  ·  " + p.memoryMiB + " MiB  ·  " + p.runtime, 12, Ui.MUTED);
        Ui.text(this, card, "Renderer: " + p.renderer + "   DirectX: " + p.dxMode, 12, Ui.DIM);
        LinearLayout row = Ui.row(this, card);
        Ui.button(this, row, "INICIAR", true, () -> {
            Intent i = new Intent(this, SessionActivity.class);
            i.putExtra("container", p.id);
            startActivity(i);
        });
        Ui.button(this, row, "EDITAR", false, () -> {
            Intent i = new Intent(this, ContainerEditorActivity.class);
            i.putExtra("container", p.id);
            startActivity(i);
        });
        Ui.button(this, row, "EXCLUIR", false, () -> confirmDelete(p));
    }

    private void confirmDelete(ContainerStore.Profile p) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir " + p.name + "?")
                .setMessage("O prefixo Wine e todos os dados privados deste container serão removidos.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> worker.execute(() -> {
                    try { store.delete(p.id); runOnUiThread(this::refresh); }
                    catch (Exception e) { runOnUiThread(() -> Ui.error(this, e)); }
                })).show();
    }

    private void installRuntime() {
        installRuntime.setEnabled(false);
        runtimeState.setText("Preparando instalação…");
        worker.execute(() -> {
            try {
                RuntimeInstaller.install(this, msg -> runOnUiThread(() -> runtimeState.setText(msg)));
                runOnUiThread(() -> {
                    installRuntime.setEnabled(true);
                    runtimeState.setText(RuntimeInstaller.summary(this));
                    Toast.makeText(this, "Runtime Cerberus pronto.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    installRuntime.setEnabled(true);
                    runtimeState.setText("FAILED · " + e.getMessage());
                    Ui.error(this, e);
                });
            }
        });
    }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}

final class Ui {
    static final int BG = Color.rgb(10, 12, 16);
    static final int CARD = Color.rgb(22, 25, 32);
    static final int TEXT = Color.rgb(244, 246, 250);
    static final int MUTED = Color.rgb(169, 177, 193);
    static final int DIM = Color.rgb(120, 129, 146);
    static final int RED = Color.rgb(239, 68, 68);

    static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static LinearLayout page(Activity a) {
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 18), dp(a, 20), dp(a, 18), dp(a, 36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        a.setContentView(scroll);
        return root;
    }

    static void kicker(Context c, LinearLayout p, String s) {
        TextView t = text(c, p, s, 11, RED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
    }

    static void title(Context c, LinearLayout p, String title, String sub) {
        TextView t = text(c, p, title, 30, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        text(c, p, sub, 13, MUTED);
        Space sp = new Space(c); p.addView(sp, new LinearLayout.LayoutParams(1, dp(c, 14)));
    }

    static void section(Context c, LinearLayout p, String s) {
        TextView t = text(c, p, s, 12, MUTED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 14), 0, dp(c, 6));
    }

    static void label(Context c, LinearLayout p, String s) {
        TextView t = text(c, p, s, 11, RED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
    }

    static TextView text(Context c, LinearLayout p, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        t.setPadding(0, dp(c, 3), 0, dp(c, 3));
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    static LinearLayout card(Context c, LinearLayout p) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD); bg.setCornerRadius(dp(c, 14));
        bg.setStroke(dp(c, 1), Color.rgb(43, 48, 59));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 8));
        p.addView(card, lp);
        return card;
    }

    static LinearLayout row(Context c, LinearLayout p) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 8), 0, 0);
        p.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    static Button button(Context c, LinearLayout p, String label, boolean primary, Runnable action) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? RED : Color.rgb(35, 39, 48));
        bg.setCornerRadius(dp(c, 10));
        if (!primary) bg.setStroke(dp(c, 1), Color.rgb(65, 72, 87));
        b.setBackground(bg);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(primary ? 0 : -2, dp(c, 46), primary ? 1f : 0f);
        lp.setMargins(0, dp(c, 5), dp(c, 8), dp(c, 5));
        p.addView(b, lp);
        return b;
    }

    static EditText input(Context c, LinearLayout p, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        p.addView(e, new LinearLayout.LayoutParams(-1, dp(c, 52)));
        return e;
    }

    static Spinner spinner(Context c, LinearLayout p, String[] values) {
        Spinner s = new Spinner(c);
        ArrayAdapter<String> a = new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(a);
        p.addView(s, new LinearLayout.LayoutParams(-1, dp(c, 52)));
        return s;
    }

    static void error(Context c, Throwable e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        new AlertDialog.Builder(c).setTitle("Cerberus · erro").setMessage(m).setPositiveButton("OK", null).show();
    }
}
