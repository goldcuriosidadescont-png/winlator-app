package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.core.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** Cerberus-owned package store for FEXCore/WOWBox64 WCPs. Donor application code is never used. */
public final class CerberusCpuComponentStore {
    public static final String TYPE_FEX = "FEXCore";
    public static final String TYPE_WOW = "WOWBox64";

    public static final class Info {
        public final String id, type, versionName;
        public final boolean unixlib;
        public final File wcp, metadata;
        Info(String id,String type,String versionName,boolean unixlib,File wcp,File metadata){this.id=id;this.type=type;this.versionName=versionName;this.unixlib=unixlib;this.wcp=wcp;this.metadata=metadata;}
        @Override public String toString(){return type+" "+versionName+(unixlib?" [Unixlib]":"");}
    }

    private CerberusCpuComponentStore() {}
    public static File root(Context c){File f=new File(c.getFilesDir(),"cerberus-bionic/cpu-components");if(!f.isDirectory())f.mkdirs();return f;}

    public static synchronized String install(Context c, File sourceWcp, String expectedType) {
        File validation=null, temp=null, tempMeta=null, backup=null, backupMeta=null;
        try {
            if(sourceWcp==null||!sourceWcp.isFile()||sourceWcp.length()==0)return "WCP ausente/vazio";
            byte[]raw=CerberusWcpUtils.readProfile(sourceWcp);if(raw==null||raw.length==0)return "profile.json ausente no WCP";
            JSONObject profile=new JSONObject(new String(raw,StandardCharsets.UTF_8));
            String type=profile.optString("type","");
            if(!TYPE_FEX.equalsIgnoreCase(type)&&!TYPE_WOW.equalsIgnoreCase(type))return "WCP não é FEXCore/WOWBox64";
            if(expectedType!=null&&!expectedType.isEmpty()&&!expectedType.equalsIgnoreCase(type))return "Tipo inesperado: "+type;
            String version=profile.optString("versionName",profile.optString("verName",""));if(version.trim().isEmpty())return "versionName ausente";
            JSONArray files=profile.optJSONArray("files");if(files==null||files.length()==0)return "files[] ausente";

            boolean hasArm64=false,hasWowFex=false,hasWowBox=false,unixlib=false;
            for(int i=0;i<files.length();i++){
                JSONObject item=files.optJSONObject(i);if(item==null)continue;
                String source=CerberusProfileUtils.normalizeRelative(item.optString("source",""));
                if(source==null)return "source inválido em files[]";
                String target=item.optString("target","").toLowerCase(Locale.ENGLISH);
                String both=source.toLowerCase(Locale.ENGLISH)+" "+target;
                if(both.contains("libarm64ecfex.dll"))hasArm64=true;
                if(both.contains("libwow64fex.dll"))hasWowFex=true;
                if(both.contains("wowbox64.dll"))hasWowBox=true;
                if(both.contains("libarm64ecfex.so")||both.contains("libwow64fex.so"))unixlib=true;
            }
            if(TYPE_FEX.equalsIgnoreCase(type)&&(!hasArm64||!hasWowFex))return "FEXCore WCP não contém as duas DLLs obrigatórias";
            if(TYPE_WOW.equalsIgnoreCase(type)&&!hasWowBox)return "WOWBox64 WCP não contém wowbox64.dll";

            // Validate the whole archive, not just profile.json, before accepting it into the store.
            validation=new File(c.getCacheDir(),"cerberus-wcp-validate-"+System.nanoTime());
            if(!validation.mkdirs()||!CerberusWcpUtils.extract(sourceWcp,validation))return "falha ao validar/extrair WCP";
            for(int i=0;i<files.length();i++){
                JSONObject item=files.optJSONObject(i);if(item==null)continue;
                File src=CerberusProfileUtils.resolveInside(validation,item.optString("source",""));
                if(src==null||!src.exists())return "payload ausente: "+item.optString("source","");
            }

            String id=CerberusProfileUtils.safeId(type.toLowerCase(Locale.ENGLISH)+"-"+version+(unixlib?"-unix":""));
            if(id.isEmpty())return "ID inválido";
            File typeDir=new File(root(c),TYPE_FEX.equalsIgnoreCase(type)?"fexcore":"wowbox64");if(!typeDir.isDirectory()&&!typeDir.mkdirs())return "falha ao criar store";
            File dst=new File(typeDir,id+".wcp"),meta=new File(typeDir,id+".json");
            temp=new File(typeDir,"."+id+"-"+System.nanoTime()+".tmp");
            tempMeta=new File(typeDir,"."+id+"-"+System.nanoTime()+".json.tmp");
            if(!FileUtils.copy(sourceWcp,temp)||temp.length()!=sourceWcp.length())return "falha ao copiar WCP para staging";
            JSONObject m=new JSONObject();m.put("schema",2);m.put("id",id);m.put("type",type);m.put("versionName",version);m.put("unixlib",unixlib);m.put("bytes",temp.length());
            if(!FileUtils.writeString(tempMeta,m.toString()))return "falha ao gravar metadata staging";

            backup=new File(typeDir,"."+id+".wcp.bak");backupMeta=new File(typeDir,"."+id+".json.bak");FileUtils.delete(backup);FileUtils.delete(backupMeta);
            if(dst.exists()&&!dst.renameTo(backup))return "falha ao preservar WCP anterior";
            if(meta.exists()&&!meta.renameTo(backupMeta)){if(backup.exists())backup.renameTo(dst);return "falha ao preservar metadata anterior";}
            if(!temp.renameTo(dst)||!tempMeta.renameTo(meta)){
                FileUtils.delete(dst);FileUtils.delete(meta);if(backup.exists())backup.renameTo(dst);if(backupMeta.exists())backupMeta.renameTo(meta);return "falha no commit do WCP";
            }
            temp=null;tempMeta=null;FileUtils.delete(backup);FileUtils.delete(backupMeta);return null;
        }catch(Exception e){return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
        finally{if(validation!=null)FileUtils.delete(validation);if(temp!=null)FileUtils.delete(temp);if(tempMeta!=null)FileUtils.delete(tempMeta);}
    }

    public static ArrayList<Info> list(Context c,String type){
        ArrayList<Info>out=new ArrayList<>();String folder=TYPE_WOW.equalsIgnoreCase(type)?"wowbox64":"fexcore";File dir=new File(root(c),folder);
        File[]metas=dir.listFiles((d,n)->n.toLowerCase(Locale.ENGLISH).endsWith(".json")&&!n.startsWith("."));if(metas==null)return out;
        for(File meta:metas)try{JSONObject m=new JSONObject(FileUtils.readString(meta));String id=m.getString("id");File wcp=new File(dir,id+".wcp");if(wcp.isFile())out.add(new Info(id,m.getString("type"),m.getString("versionName"),m.optBoolean("unixlib",false),wcp,meta));}catch(Exception ignored){}
        out.sort((a,b)->a.toString().compareToIgnoreCase(b.toString()));return out;
    }
    public static Info get(Context c,String type,String id){if(id==null||id.isEmpty())return null;for(Info i:list(c,type))if(i.id.equals(id))return i;return null;}
    public static Info first(Context c,String type){ArrayList<Info>all=list(c,type);return all.isEmpty()?null:all.get(0);}
}
