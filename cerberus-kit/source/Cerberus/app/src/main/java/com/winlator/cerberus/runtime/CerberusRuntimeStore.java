package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.core.FileUtils;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Cerberus-owned WCP store. It stores packages; the Bionic engine itself remains donor-independent. */
public final class CerberusRuntimeStore {
    public static final class Info {
        public final String id, type, versionName, architecture, binPath, libPath, prefixPack;
        public final File root;
        Info(String id, String type, String versionName, String architecture, String binPath, String libPath, String prefixPack, File root) {
            this.id=id; this.type=type; this.versionName=versionName; this.architecture=architecture;
            this.binPath=binPath; this.libPath=libPath; this.prefixPack=prefixPack; this.root=root;
        }
        @Override public String toString() { return type + " " + versionName + " [" + architecture + "]"; }
    }

    private CerberusRuntimeStore() {}
    public static File root(Context c) { File f=new File(c.getFilesDir(),"cerberus-bionic/runtime-store"); if(!f.isDirectory()) f.mkdirs(); return f; }

    public static synchronized String install(Context c, File wcp) {
        File staging = null;
        File backup = null;
        try {
            if (wcp == null || !wcp.isFile() || wcp.length() <= 0) return "WCP ausente/vazio";
            byte[] raw=CerberusWcpUtils.readProfile(wcp);
            if(raw==null || raw.length==0) return "profile.json ausente na raiz do WCP";
            JSONObject p=new JSONObject(new String(raw, StandardCharsets.UTF_8));
            String type=p.optString("type","");
            if(!type.equalsIgnoreCase("Wine") && !type.equalsIgnoreCase("Proton")) return "WCP não é Wine/Proton";
            String version=p.optString("versionName",p.optString("verName",""));
            if(version.trim().isEmpty()) return "versionName ausente";
            JSONObject wine=p.optJSONObject("wine");
            if(wine==null) return "objeto wine ausente";
            String bin=CerberusProfileUtils.normalizeRelative(wine.optString("binPath",""));
            String lib=CerberusProfileUtils.normalizeRelative(wine.optString("libPath",""));
            String prefix=CerberusProfileUtils.normalizeRelative(wine.optString("prefixPack",""));
            if(bin==null||lib==null||prefix==null) return "binPath/libPath/prefixPack inválido";
            String arch=CerberusProfileUtils.detectArchitecture(p, "x86_64");
            String id=CerberusProfileUtils.safeId(type.toLowerCase()+"-"+version+"-"+arch);
            if(id.isEmpty()) return "ID do runtime inválido";

            File store=root(c);
            staging=new File(store,".staging-"+id+"-"+System.nanoTime());
            if(!staging.mkdirs()) return "falha ao criar staging";
            if(!CerberusWcpUtils.extract(wcp,staging)) return "falha ao extrair WCP";

            File binDir=CerberusProfileUtils.resolveInside(staging,bin);
            File libDir=CerberusProfileUtils.resolveInside(staging,lib);
            File prefixFile=CerberusProfileUtils.resolveInside(staging,prefix);
            if(binDir==null||!binDir.isDirectory() || (!new File(binDir,"wine").isFile() && !new File(binDir,"wine64").isFile())) return "wine/wine64 ausente no binPath";
            if(libDir==null||!libDir.isDirectory()) return "libPath ausente";
            if(prefixFile==null||!prefixFile.isFile()||prefixFile.length()==0) return "prefixPack ausente/vazio";
            chmodTree(binDir);

            JSONObject meta=new JSONObject();
            meta.put("schema",2);meta.put("id",id);meta.put("type",type);meta.put("versionName",version);
            meta.put("architecture",arch);meta.put("binPath",bin);meta.put("libPath",lib);meta.put("prefixPack",prefix);meta.put("sourceBytes",wcp.length());
            if(!FileUtils.writeString(new File(staging,".cerberus-runtime.json"),meta.toString())) return "falha ao gravar metadata";

            File dst=new File(store,id);
            backup=new File(store,".backup-"+id+"-"+System.nanoTime());
            if(dst.exists() && !dst.renameTo(backup)) return "falha ao preservar runtime anterior";
            if(!staging.renameTo(dst)) {
                if (backup.exists()) backup.renameTo(dst);
                return "falha no commit do runtime";
            }
            staging=null;
            if (backup.exists()) FileUtils.delete(backup);
            return null;
        } catch(Exception e){ return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()); }
        finally { if (staging != null) FileUtils.delete(staging); }
    }

    private static void chmodTree(File f) {
        if (f == null || !f.exists()) return;
        FileUtils.chmod(f, 0771);
        if (f.isDirectory()) { File[] all=f.listFiles(); if(all!=null) for(File x:all) chmodTree(x); }
    }

    public static ArrayList<Info> list(Context c) {
        ArrayList<Info> out=new ArrayList<>(); File[] dirs=root(c).listFiles(); if(dirs==null)return out;
        for(File d:dirs){
            if(!d.isDirectory()||d.getName().startsWith("."))continue;
            try{
                File metadata = new File(d,".cerberus-runtime.json");
                if (!metadata.isFile()) continue;
                JSONObject m=new JSONObject(FileUtils.readString(metadata));
                String bin=CerberusProfileUtils.normalizeRelative(m.optString("binPath",""));
                String lib=CerberusProfileUtils.normalizeRelative(m.optString("libPath",""));
                String prefix=CerberusProfileUtils.normalizeRelative(m.optString("prefixPack",""));
                if(bin==null||lib==null||prefix==null)continue;
                out.add(new Info(m.getString("id"),m.getString("type"),m.getString("versionName"),m.optString("architecture","x86_64"),bin,lib,prefix,d));
            }catch(Exception ignored){}
        }
        out.sort((a,b)->a.toString().compareToIgnoreCase(b.toString()));
        return out;
    }
    public static Info get(Context c,String id){ if(id==null||id.isEmpty())return null; for(Info i:list(c))if(i.id.equals(id))return i;return null; }
}
