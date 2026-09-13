package com.cerberus.bionicpc;

import android.app.*;
import android.os.Bundle;
import android.widget.*;

public final class ContainerEditorActivity extends Activity {
    private static final String[] RUNTIMES = {"proton-wine-11.0-2"};
    private static final String[] RESOLUTIONS = {"1280x720", "1600x900", "1920x1080", "960x544"};
    private static final String[] RENDERERS = {"system-vulkan", "turnip-planned", "software"};
    private static final String[] DX = {"auto", "dxvk", "vkd3d", "disabled"};
    private static final String[] MEMORY = {"2048", "4096", "6144", "8192"};

    private ContainerStore store;
    private ContainerStore.Profile current;
    private EditText name;
    private Spinner runtime, resolution, renderer, dx, memory;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        try { store = new ContainerStore(this); }
        catch (Exception e) { Ui.error(this, e); return; }

        String id = getIntent().getStringExtra("container");
        if (id != null) {
            try { current = store.load(id); }
            catch (Exception e) { Ui.error(this, e); return; }
        }

        LinearLayout root = Ui.page(this);
        Ui.kicker(this, root, "CERBERUS  /  CONTAINER PROFILE");
        Ui.title(this, root, current == null ? "Novo container" : "Editar container",
                "Cada perfil mantém WINEPREFIX, drive C:, HOME, TMP e logs isolados.");

        LinearLayout c = Ui.card(this, root);
        Ui.label(this, c, "IDENTIDADE");
        name = Ui.input(this, c, "Nome do container");

        Ui.label(this, c, "RUNTIME");
        runtime = Ui.spinner(this, c, RUNTIMES);
        Ui.label(this, c, "RESOLUÇÃO DO DESKTOP");
        resolution = Ui.spinner(this, c, RESOLUTIONS);
        Ui.label(this, c, "RENDERER");
        renderer = Ui.spinner(this, c, RENDERERS);
        Ui.label(this, c, "DIRECTX TRANSLATION");
        dx = Ui.spinner(this, c, DX);
        Ui.label(this, c, "LIMITE DE MEMÓRIA / MiB");
        memory = Ui.spinner(this, c, MEMORY);

        LinearLayout note = Ui.card(this, root);
        Ui.label(this, note, "IMPORTANTE");
        Ui.text(this, note, "system-vulkan registra a intenção do perfil, mas o backend gráfico embutido ainda não está ligado nesta v0.6. turnip-planned fica explicitamente marcado como não ativo para evitar placebo.", 12, Ui.DIM);

        LinearLayout row = Ui.row(this, root);
        Ui.button(this, row, "SALVAR", true, this::save);
        Ui.button(this, row, "CANCELAR", false, this::finish);

        if (current != null) loadCurrent();
        else {
            name.setText("Gaming Container");
            select(memory, "4096");
        }
    }

    private void loadCurrent() {
        name.setText(current.name);
        select(runtime, current.runtime);
        select(resolution, current.resolution);
        select(renderer, current.renderer);
        select(dx, current.dxMode);
        select(memory, Integer.toString(current.memoryMiB));
    }

    private void save() {
        try {
            String n = name.getText().toString();
            String rt = selected(runtime);
            String rs = selected(resolution);
            String re = selected(renderer);
            String dm = selected(dx);
            int mem = Integer.parseInt(selected(memory));
            if (current == null) {
                current = store.create(n, rt, rs, re, dm, mem);
            } else {
                current.name = n;
                current.runtime = rt;
                current.resolution = rs;
                current.renderer = re;
                current.dxMode = dm;
                current.memoryMiB = mem;
                store.save(current);
            }
            Toast.makeText(this, "Container salvo.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) { Ui.error(this, e); }
    }

    private static String selected(Spinner s) { return String.valueOf(s.getSelectedItem()); }

    private static void select(Spinner s, String value) {
        for (int i = 0; i < s.getCount(); i++) {
            if (String.valueOf(s.getItemAtPosition(i)).equals(value)) { s.setSelection(i); return; }
        }
    }
}
