package com.winlator.cerberus.runtime;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** Downloads only CPU translator packages from the public component catalog into Cerberus-owned storage. */
public final class CerberusCpuComponentRepository {
    private static final String CATALOG = "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/contents.json";
    private CerberusCpuComponentRepository() {}

    public static void loadInstalledSpinner(Activity a, Spinner spinner, String type) {
        ArrayList<String> labels = new ArrayList<>();
        labels.add("Automático / empacotado");
        for (CerberusCpuComponentStore.Info i : CerberusCpuComponentStore.list(a, type)) labels.add(i.toString());
        spinner.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, labels));
    }

    public static void showInstalled(Activity a, String type) {
        ArrayList<CerberusCpuComponentStore.Info> all = CerberusCpuComponentStore.list(a, type);
        if (all.isEmpty()) { AppUtils.showToast(a, "Nenhum " + type + " WCP instalado no Cerberus Bionic."); return; }
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) labels[i] = all.get(i).toString();
        ContentDialog.showSelectionList(a, type + " instalados", labels, false, pos -> {});
    }

    public static void showRepository(Activity a, Spinner spinner, String type) {
        File file = new File(a.getCacheDir(), "cerberus-cpu-contents.json");
        FileUtils.delete(file);
        HttpUtils.download(a, CATALOG, file, success -> {
            if (!success) { AppUtils.showToast(a, "Falha ao baixar catálogo " + type + "."); return; }
            try {
                JSONArray arr = new JSONArray(FileUtils.readString(file));
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<String> urls = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                    if (!type.equalsIgnoreCase(o.optString("type", ""))) continue;
                    String ver = o.optString("versionName", o.optString("verName", ""));
                    String url = o.optString("remoteUrl", "");
                    if (ver.isEmpty() || url.isEmpty() || !isWcpUrl(url)) continue;
                    labels.add(type + " • " + ver + (url.toLowerCase().contains("unix") ? " • Unixlib" : ""));
                    urls.add(url);
                }
                if (labels.isEmpty()) { AppUtils.showToast(a, "Catálogo sem " + type + "."); return; }
                ContentDialog.showSelectionList(a, "Cerberus " + type, labels.toArray(new String[0]), false, pos -> {
                    if (pos.isEmpty()) return;
                    downloadAndInstall(a, spinner, type, urls.get(pos.get(0)));
                });
            }
            catch (Exception e) { AppUtils.showToast(a, "Catálogo " + type + ": " + e.getMessage()); }
            finally { FileUtils.delete(file); }
        });
    }

    private static void downloadAndInstall(Activity a, Spinner spinner, String type, String url) {
        File wcp = new File(a.getCacheDir(), "cerberus-" + type.toLowerCase() + ".wcp");
        FileUtils.delete(wcp);
        HttpUtils.download(a, url, wcp, success -> {
            if (!success) { AppUtils.showToast(a, "Falha ao baixar " + type + " WCP."); return; }
            new Thread(() -> {
                String error = CerberusCpuComponentStore.install(a, wcp, type);
                FileUtils.delete(wcp);
                a.runOnUiThread(() -> {
                    if (error == null) {
                        AppUtils.showToast(a, type + " instalado no Cerberus Bionic.");
                        if (spinner != null) loadInstalledSpinner(a, spinner, type);
                    }
                    else AppUtils.showToast(a, type + ": " + error);
                });
            }).start();
        });
    }

    private static boolean isWcpUrl(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ENGLISH);
        int q = u.indexOf('?'); if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".wcp") || u.endsWith(".wcp.xz") || u.endsWith(".wcp.txz") || u.endsWith(".wcp.zst") || u.endsWith(".wcp.zstd");
    }
}
