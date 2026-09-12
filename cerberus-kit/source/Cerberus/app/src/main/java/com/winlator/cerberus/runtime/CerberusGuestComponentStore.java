package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.core.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** WCP store for Windows guest graphics components used only by the Bionic engine. */
public final class CerberusGuestComponentStore {
    public static final String TYPE_DXVK="DXVK", TYPE_VKD3D="VKD3D";
    public static final class Info{
        public final String id,type,versionName,architecture;public final File wcp,metadata;
        Info(String id,String type,String versionName,String architecture,File wcp,File metadata){this.id=id;this.type=type;this.versionName=versionName;this.architecture=architecture;this.wcp=wcp;this.metadata=metadata;}
        @Override public String toString(){return type+" "+versionName+" ["+architecture+"]";}
    }
    private CerberusGuestComponentStore(){}
    public static File root(Context c){File f=new File(c.getFilesDir(),"cerberus-bionic/guest-components");if(!f.isDirectory())f.mkdirs();return f;}

    public static synchronized String install(Context c,File sourceWcp,String expectedType){
        File validation=null,temp=null,tempMeta=null,backup=null,backupMeta=null;
        try{
            if(sourceWcp==null||!sourceWcp.isFile()||sourceWcp.length()==0)return "WCP ausente/vazio";
            byte[]raw=CerberusWcpUtils.readProfile(sourceWcp);if(raw==null||raw.length==0)return "profile.json ausente";
            JSONObject p=new JSONObject(new String(raw,StandardCharsets.UTF_8));String type=p.optString("type","");
            if(!TYPE_DXVK.equalsIgnoreCase(type)&&!TYPE_VKD3D.equalsIgnoreCase(type))return "WCP não é DXVK/VKD3D";
            if(expectedType!=null&&!expectedType.isEmpty()&&!expectedType.equalsIgnoreCase(type))return "Tipo inesperado: "+type;
            String ver=p.optString("versionName",p.optString("verName",""));if(ver.trim().isEmpty())return "versionName ausente";
            JSONArray files=p.optJSONArray("files");if(files==null||files.length()==0)return "files[] ausente";
            boolean system=false,wow=false;
            for(int i=0;i<files.length();i++){
                JSONObject f=files.optJSONObject(i);if(f==null)continue;
                if(CerberusProfileUtils.normalizeRelative(f.optString("source",""))==null)return "source inválido em files[]";
                String t=f.optString("target","").toLowerCase(Locale.ENGLISH);if(t.contains("system32"))system=true;if(t.contains("syswow64"))wow=true;
            }
            if(!system)return "WCP sem payload system32";
            String arch=CerberusProfileUtils.detectArchitecture(p,"x86_64");

            validation=new File(c.getCacheDir(),"cerberus-guest-validate-"+System.nanoTime());
            if(!validation.mkdirs()||!CerberusWcpUtils.extract(sourceWcp,validation))return "falha ao validar/extrair WCP";
            for(int i=0;i<files.length();i++){
                JSONObject f=files.optJSONObject(i);if(f==null)continue;File src=CerberusProfileUtils.resolveInside(validation,f.optString("source",""));
                if(src==null||!src.isFile())return "payload ausente: "+f.optString("source","");
            }

            String id=CerberusProfileUtils.safeId(type.toLowerCase(Locale.ENGLISH)+"-"+ver+"-"+arch);if(id.isEmpty())return "ID inválido";
            File dir=new File(root(c),type.toLowerCase(Locale.ENGLISH));if(!dir.isDirectory()&&!dir.mkdirs())return "falha ao criar store";
            File dst=new File(dir,id+".wcp"),meta=new File(dir,id+".json");temp=new File(dir,"."+id+"-"+System.nanoTime()+".tmp");tempMeta=new File(dir,"."+id+"-"+System.nanoTime()+".json.tmp");
            if(!FileUtils.copy(sourceWcp,temp)||temp.length()!=sourceWcp.length())return "falha ao copiar WCP";
            JSONObject m=new JSONObject();m.put("schema",2);m.put("id",id);m.put("type",type);m.put("versionName",ver);m.put("architecture",arch);m.put("hasSyswow64",wow);m.put("bytes",temp.length());
            if(!FileUtils.writeString(tempMeta,m.toString()))return "falha ao gravar metadata staging";
            backup=new File(dir,"."+id+".wcp.bak");backupMeta=new File(dir,"."+id+".json.bak");FileUtils.delete(backup);FileUtils.delete(backupMeta);
            if(dst.exists()&&!dst.renameTo(backup))return "falha ao preservar WCP anterior";
            if(meta.exists()&&!meta.renameTo(backupMeta)){if(backup.exists())backup.renameTo(dst);return "falha ao preservar metadata anterior";}
            if(!temp.renameTo(dst)||!tempMeta.renameTo(meta)){FileUtils.delete(dst);FileUtils.delete(meta);if(backup.exists())backup.renameTo(dst);if(backupMeta.exists())backupMeta.renameTo(meta);return "falha no commit";}
            temp=null;tempMeta=null;FileUtils.delete(backup);FileUtils.delete(backupMeta);return null;
        }catch(Exception e){return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
        finally{if(validation!=null)FileUtils.delete(validation);if(temp!=null)FileUtils.delete(temp);if(tempMeta!=null)FileUtils.delete(tempMeta);}
    }

    public static ArrayList<Info> list(Context c,String type){
        ArrayList<Info>out=new ArrayList<>();File dir=new File(root(c),type.toLowerCase(Locale.ENGLISH));File[]ms=dir.listFiles((d,n)->n.endsWith(".json")&&!n.startsWith("."));if(ms==null)return out;
        for(File m:ms)try{JSONObject o=new JSONObject(FileUtils.readString(m));String id=o.getString("id");File w=new File(dir,id+".wcp");if(w.isFile())out.add(new Info(id,o.getString("type"),o.getString("versionName"),o.optString("architecture","x86_64"),w,m));}catch(Exception ignored){}
        out.sort((a,b)->a.toString().compareToIgnoreCase(b.toString()));return out;
    }
    public static Info get(Context c,String type,String id){if(id==null||id.isEmpty())return null;for(Info i:list(c,type))if(i.id.equals(id))return i;return null;}
}
