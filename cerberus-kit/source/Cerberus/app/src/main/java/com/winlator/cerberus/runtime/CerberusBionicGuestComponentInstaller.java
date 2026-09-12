package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** Installs selected DXVK/VKD3D WCP file mappings into the current Bionic Windows prefix. */
public final class CerberusBionicGuestComponentInstaller {
    private CerberusBionicGuestComponentInstaller(){}

    public static boolean ensure(Context c,Container container,CerberusRuntimeProfile p){
        if(!p.isBionic())return true;
        if(p.dxvkVersion!=null&&!p.dxvkVersion.isEmpty()){
            CerberusGuestComponentStore.Info i=CerberusGuestComponentStore.get(c,CerberusGuestComponentStore.TYPE_DXVK,p.dxvkVersion);
            if(i==null||!i.architecture.equalsIgnoreCase(p.architecture.id)||!installIfNeeded(container,i))return false;
        }
        if(p.vkd3dVersion!=null&&!p.vkd3dVersion.isEmpty()){
            CerberusGuestComponentStore.Info i=CerberusGuestComponentStore.get(c,CerberusGuestComponentStore.TYPE_VKD3D,p.vkd3dVersion);
            if(i==null||!i.architecture.equalsIgnoreCase(p.architecture.id)||!installIfNeeded(container,i))return false;
        }
        return true;
    }

    private static synchronized boolean installIfNeeded(Container container,CerberusGuestComponentStore.Info info){
        File marker=new File(container.getRootDir(),".cerberus-"+info.type.toLowerCase(Locale.ENGLISH)+".json");
        if(markerMatchesAndPayloadPresent(container,marker,info))return true;
        File stage=new File(container.getRootDir(),".cerberus-component-stage-"+info.type.toLowerCase(Locale.ENGLISH));FileUtils.delete(stage);if(!stage.mkdirs())return false;
        try{
            byte[]raw=CerberusWcpUtils.readProfile(info.wcp);if(raw==null)return false;JSONObject p=new JSONObject(new String(raw,StandardCharsets.UTF_8));JSONArray fs=p.optJSONArray("files");if(fs==null||!CerberusWcpUtils.extract(info.wcp,stage))return false;
            File system32=new File(container.getRootDir(),".wine/drive_c/windows/system32"),syswow64=new File(container.getRootDir(),".wine/drive_c/windows/syswow64");if(!system32.isDirectory()&&!system32.mkdirs())return false;if(!syswow64.isDirectory()&&!syswow64.mkdirs())return false;
            ArrayList<File>srcs=new ArrayList<>(),dsts=new ArrayList<>();JSONArray markerTargets=new JSONArray();
            for(int n=0;n<fs.length();n++){
                JSONObject f=fs.optJSONObject(n);if(f==null)continue;File src=CerberusProfileUtils.resolveInside(stage,f.optString("source",""));if(src==null||!src.isFile())return false;
                File dst=resolve(system32,syswow64,f.optString("target",""));if(dst==null)return false;srcs.add(src);dsts.add(dst);markerTargets.put(markerTarget(system32,syswow64,dst));
            }
            // Validate the whole mapping before mutating the prefix.
            for(int n=0;n<srcs.size();n++){File dst=dsts.get(n);File parent=dst.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())return false;if(!FileUtils.copy(srcs.get(n),dst))return false;}
            removeStaleTargets(marker,system32,syswow64,markerTargets);
            JSONObject m=new JSONObject();m.put("schema",2);m.put("id",info.id);m.put("type",info.type);m.put("versionName",info.versionName);m.put("architecture",info.architecture);m.put("targets",markerTargets);
            return FileUtils.writeString(marker,m.toString());
        }catch(Exception e){return false;}finally{FileUtils.delete(stage);}
    }

    private static boolean markerMatchesAndPayloadPresent(Container container,File marker,CerberusGuestComponentStore.Info info){
        try{
            if(!marker.isFile())return false;JSONObject m=new JSONObject(FileUtils.readString(marker));if(!info.id.equals(m.optString("id","")))return false;
            JSONArray targets=m.optJSONArray("targets");if(targets==null||targets.length()==0)return false;
            File system32=new File(container.getRootDir(),".wine/drive_c/windows/system32"),syswow64=new File(container.getRootDir(),".wine/drive_c/windows/syswow64");
            for(int i=0;i<targets.length();i++){File f=resolveMarker(system32,syswow64,targets.optString(i,""));if(f==null||!f.isFile())return false;}
            return true;
        }catch(Exception e){return false;}
    }

    private static void removeStaleTargets(File marker,File system32,File syswow64,JSONArray keep){
        try{
            if(!marker.isFile())return;JSONArray old=new JSONObject(FileUtils.readString(marker)).optJSONArray("targets");if(old==null)return;
            for(int i=0;i<old.length();i++){String t=old.optString(i,"");boolean retained=false;for(int k=0;k<keep.length();k++)if(t.equals(keep.optString(k,""))){retained=true;break;}if(!retained){File f=resolveMarker(system32,syswow64,t);if(f!=null)FileUtils.delete(f);}}
        }catch(Exception ignored){}
    }

    private static String markerTarget(File system32,File syswow64,File dst)throws Exception{
        String d=dst.getCanonicalPath(),s32=system32.getCanonicalPath(),sw=syswow64.getCanonicalPath();
        if(d.startsWith(s32+File.separator))return "system32/"+d.substring(s32.length()+1).replace(File.separatorChar,'/');
        if(d.startsWith(sw+File.separator))return "syswow64/"+d.substring(sw.length()+1).replace(File.separatorChar,'/');
        throw new IllegalArgumentException("target outside prefix");
    }
    private static File resolveMarker(File system32,File syswow64,String target)throws Exception{
        String t=target.replace('\\','/');if(t.startsWith("system32/"))return safeChild(system32,t.substring(9));if(t.startsWith("syswow64/"))return safeChild(syswow64,t.substring(9));return null;
    }
    private static File resolve(File system32,File syswow64,String target)throws Exception{
        String t=target==null?"":target.replace('\\','/'),low=t.toLowerCase(Locale.ENGLISH);File base;String tail;
        if(t.contains("${system32}")){base=system32;tail=t.substring(t.indexOf("${system32}")+11);}
        else if(t.contains("${syswow64}")){base=syswow64;tail=t.substring(t.indexOf("${syswow64}")+11);}
        else if(low.contains("system32/")){base=system32;tail=t.substring(low.indexOf("system32/")+9);}
        else if(low.contains("syswow64/")){base=syswow64;tail=t.substring(low.indexOf("syswow64/")+9);}
        else return null;while(tail.startsWith("/"))tail=tail.substring(1);return safeChild(base,tail);
    }
    private static File safeChild(File base,String tail)throws Exception{if(CerberusProfileUtils.normalizeRelative(tail)==null)return null;File r=new File(base,tail);String bp=base.getCanonicalPath(),rp=r.getCanonicalPath();return rp.startsWith(bp+File.separator)?r:null;}
}
