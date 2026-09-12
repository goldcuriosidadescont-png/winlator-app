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

public final class CerberusGuestComponentRepository {
    private static final String CATALOG="https://raw.githubusercontent.com/The412Banner/winlator-contents/main/contents.json";
    private CerberusGuestComponentRepository(){}
    public static void loadInstalledSpinner(Activity a, Spinner s, String type){ArrayList<String>ls=new ArrayList<>();ls.add("Container pattern / padrão");for(CerberusGuestComponentStore.Info i:CerberusGuestComponentStore.list(a,type))ls.add(i.toString());s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,ls));}
    public static void showInstalled(Activity a,String type){ArrayList<CerberusGuestComponentStore.Info>all=CerberusGuestComponentStore.list(a,type);if(all.isEmpty()){AppUtils.showToast(a,"Nenhum "+type+" Bionic WCP instalado.");return;}String[]l=new String[all.size()];for(int i=0;i<all.size();i++)l[i]=all.get(i).toString();ContentDialog.showSelectionList(a,type+" Bionic instalados",l,false,pos->{});}
    public static void showRepository(Activity a,Spinner spinner,String type){
        File file=new File(a.getCacheDir(),"cerberus-guest-catalog.json");FileUtils.delete(file);
        HttpUtils.download(a,CATALOG,file,ok->{if(!ok){AppUtils.showToast(a,"Falha ao baixar catálogo "+type+".");return;}try{
            JSONArray arr=new JSONArray(FileUtils.readString(file));ArrayList<String>labels=new ArrayList<>(),urls=new ArrayList<>();
            for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null||!type.equalsIgnoreCase(o.optString("type","")))continue;String ver=o.optString("versionName",o.optString("verName","")),url=o.optString("remoteUrl","");if(ver.isEmpty()||url.isEmpty()||!isWcpUrl(url))continue;String low=(ver+" "+url).toLowerCase(Locale.ENGLISH);labels.add(type+" • "+ver+" • "+(low.contains("arm64ec")?"ARM64EC":"x86_64"));urls.add(url);}
            if(labels.isEmpty()){AppUtils.showToast(a,"Catálogo sem "+type+".");return;}ContentDialog.showSelectionList(a,"Cerberus Bionic "+type,labels.toArray(new String[0]),false,pos->{if(pos.isEmpty())return;download(a,spinner,type,urls.get(pos.get(0)));});
        }catch(Exception e){AppUtils.showToast(a,"Catálogo "+type+": "+e.getMessage());}finally{FileUtils.delete(file);}});
    }
    private static void download(Activity a,Spinner spinner,String type,String url){File w=new File(a.getCacheDir(),"cerberus-guest-"+type.toLowerCase(Locale.ENGLISH)+".wcp");FileUtils.delete(w);HttpUtils.download(a,url,w,ok->{if(!ok){AppUtils.showToast(a,"Falha no download "+type+".");return;}new Thread(()->{String e=CerberusGuestComponentStore.install(a,w,type);FileUtils.delete(w);a.runOnUiThread(()->{if(e==null){AppUtils.showToast(a,type+" Bionic instalado.");if(spinner!=null)loadInstalledSpinner(a,spinner,type);}else AppUtils.showToast(a,type+": "+e);});}).start();});}

    private static boolean isWcpUrl(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ENGLISH);
        int q = u.indexOf('?'); if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".wcp") || u.endsWith(".wcp.xz") || u.endsWith(".wcp.txz") || u.endsWith(".wcp.zst") || u.endsWith(".wcp.zstd");
    }
}
