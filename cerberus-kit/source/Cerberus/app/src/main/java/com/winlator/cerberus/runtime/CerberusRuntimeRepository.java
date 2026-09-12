package com.winlator.cerberus.runtime;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public final class CerberusRuntimeRepository {
    private static final String CATALOG="https://raw.githubusercontent.com/The412Banner/winlator-contents/main/contents.json";
    private CerberusRuntimeRepository() {}

    public static void loadInstalledSpinner(Activity a, Spinner spinner) {
        ArrayList<String> labels=new ArrayList<>(); labels.add("Bionic embutido");
        for(CerberusRuntimeStore.Info i:CerberusRuntimeStore.list(a)) labels.add(i.toString());
        spinner.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels));
    }
    public static void showInstalled(Activity a) {
        ArrayList<CerberusRuntimeStore.Info> all=CerberusRuntimeStore.list(a);
        if(all.isEmpty()){AppUtils.showToast(a,"Nenhum Wine/Proton WCP instalado no Cerberus Bionic.");return;}
        String[] labels=new String[all.size()]; for(int i=0;i<all.size();i++)labels[i]=all.get(i).toString();
        ContentDialog.showSelectionList(a,"Wine / Proton instalados",labels,false,pos->{});
    }
    public static void showRepository(Activity a, Spinner spinner) {
        File file=new File(a.getCacheDir(),"cerberus-contents.json"); FileUtils.delete(file);
        HttpUtils.download(a,CATALOG,file,success->{
            if(!success){AppUtils.showToast(a,"Falha ao baixar catálogo Wine/Proton.");return;}
            try{
                JSONArray arr=new JSONArray(FileUtils.readString(file)); ArrayList<String> labels=new ArrayList<>(); ArrayList<String> urls=new ArrayList<>();
                for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;String type=o.optString("type","");if(!type.equalsIgnoreCase("Wine")&&!type.equalsIgnoreCase("Proton"))continue;String ver=o.optString("versionName",o.optString("verName",""));String url=o.optString("remoteUrl","");if(ver.isEmpty()||url.isEmpty()||!isWcpUrl(url))continue;String low=(ver+" "+url).toLowerCase(Locale.ENGLISH);String arch=low.contains("arm64ec")?"ARM64EC":"x86_64";labels.add(type+" • "+ver+" • "+arch);urls.add(url);}
                if(labels.isEmpty()){AppUtils.showToast(a,"Catálogo não retornou Wine/Proton compatível.");return;}
                ContentDialog.showSelectionList(a,"Cerberus Wine / Proton",labels.toArray(new String[0]),false,pos->{if(pos.isEmpty())return;int idx=pos.get(0);downloadAndInstall(a,spinner,urls.get(idx));});
            }catch(Exception e){AppUtils.showToast(a,"Falha ao processar catálogo: "+e.getMessage());}
            finally{FileUtils.delete(file);}
        });
    }
    private static void downloadAndInstall(Activity a, Spinner spinner, String url) {
        File wcp=new File(a.getCacheDir(),"cerberus-runtime.wcp"); FileUtils.delete(wcp);
        HttpUtils.download(a,url,wcp,success->{
            if(!success){AppUtils.showToast(a,"Falha ao baixar WCP.");return;}
            new Thread(()->{String error=CerberusRuntimeStore.install(a,wcp);FileUtils.delete(wcp);a.runOnUiThread(()->{if(error==null){AppUtils.showToast(a,"Runtime WCP instalado.");if(spinner!=null)loadInstalledSpinner(a,spinner);}else AppUtils.showToast(a,"WCP: "+error);});}).start();
        });
    }

    private static boolean isWcpUrl(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ENGLISH);
        int q = u.indexOf('?'); if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".wcp") || u.endsWith(".wcp.xz") || u.endsWith(".wcp.txz") || u.endsWith(".wcp.zst") || u.endsWith(".wcp.zstd");
    }
}
