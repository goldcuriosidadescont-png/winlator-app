package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.xenvironment.RootFS;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** Installs only selected FEXCore/WOWBox64 WCP payload files into the isolated Bionic engine/prefix. */
public final class CerberusBionicCpuComponentInstaller {
    public static final String FEX_ASSET="cerberus-bionic/components/fexcore.wcp";
    public static final String WOW_ASSET="cerberus-bionic/components/wowbox64.wcp";
    private static final String FEX_MARKER=".cerberus-fexcore.json",WOW_MARKER=".cerberus-wowbox64.json";
    private CerberusBionicCpuComponentInstaller(){}

    public static boolean ensure(Context c,Container container,CerberusRuntimeProfile p){
        if(!p.isArm64ec())return true;
        File system32=new File(container.getRootDir(),".wine/drive_c/windows/system32");if(!system32.isDirectory()&&!system32.mkdirs())return false;

        boolean explicitFex=p.fexcoreVersion!=null&&!p.fexcoreVersion.isEmpty();
        CerberusCpuComponentStore.Info fex=pick(c,CerberusCpuComponentStore.TYPE_FEX,p.fexcoreVersion);
        if(explicitFex&&fex==null)return false; // never silently substitute another selected version
        boolean fexAsset=CerberusBionicAssets.exists(c,FEX_ASSET);
        String desiredFex=fex!=null?fex.id:(fexAsset?"bundled-wcp":"builtin-pattern");
        boolean fexDllsPresent=new File(system32,"libarm64ecfex.dll").isFile()&&new File(system32,"libwow64fex.dll").isFile();
        if(!markerMatches(container,FEX_MARKER,desiredFex)||!fexDllsPresent){
            boolean ok=fex!=null?installWcp(c,container,fex.wcp,CerberusCpuComponentStore.TYPE_FEX,fex.id)
                    :fexAsset?installWcpAsset(c,container,FEX_ASSET,CerberusCpuComponentStore.TYPE_FEX,"bundled-wcp")
                    :fexDllsPresent&&writeMarker(container,FEX_MARKER,"builtin-pattern","donor-container-pattern",hasBionicFexUnixlib(c));
            if(!ok)return false;
        }

        if(p.backend32==RuntimeBackend.WOWBOX64){
            boolean explicitWow=p.wowbox64Version!=null&&!p.wowbox64Version.isEmpty();
            CerberusCpuComponentStore.Info wow=pick(c,CerberusCpuComponentStore.TYPE_WOW,p.wowbox64Version);
            if(explicitWow&&wow==null)return false;
            boolean wowAsset=CerberusBionicAssets.exists(c,WOW_ASSET);
            String desiredWow=wow!=null?wow.id:(wowAsset?"bundled-wcp":"builtin-pattern");
            boolean wowDllPresent=new File(system32,"wowbox64.dll").isFile();
            if(!markerMatches(container,WOW_MARKER,desiredWow)||!wowDllPresent){
                boolean ok=wow!=null?installWcp(c,container,wow.wcp,CerberusCpuComponentStore.TYPE_WOW,wow.id)
                        :wowAsset?installWcpAsset(c,container,WOW_ASSET,CerberusCpuComponentStore.TYPE_WOW,"bundled-wcp")
                        :wowDllPresent&&writeMarker(container,WOW_MARKER,"builtin-pattern","donor-container-pattern",false);
                if(!ok)return false;
            }
        }
        return true;
    }

    private static CerberusCpuComponentStore.Info pick(Context c,String type,String id){
        if(id!=null&&!id.isEmpty())return CerberusCpuComponentStore.get(c,type,id);
        return CerberusCpuComponentStore.first(c,type);
    }

    private static boolean hasBionicFexUnixlib(Context c){
        RootFS root=RootFS.findBionic(c);
        String[]dirs={"usr/lib","usr/lib64","usr/local/lib","usr/lib/wine/aarch64-unix","usr/lib/wine/arm64ec-unix","usr/local/lib/wine/aarch64-unix","lib","lib64","system/lib64"};
        for(String d:dirs)if(new File(root.getRootDir(),d+"/libarm64ecfex.so").isFile()||new File(root.getRootDir(),d+"/libwow64fex.so").isFile())return true;
        return false;
    }

    private static boolean markerMatches(Container container,String marker,String id){
        try{File f=new File(container.getRootDir(),marker);if(!f.isFile())return false;JSONObject o=new JSONObject(FileUtils.readString(f));return id.equals(o.optString("id",""));}catch(Exception e){return false;}
    }

    private static boolean installWcpAsset(Context c,Container container,String asset,String expectedType,String id){
        if(!CerberusBionicAssets.exists(c,asset))return false;
        File temp=new File(c.getCacheDir(),"cerberus-component-"+expectedType.toLowerCase(Locale.ENGLISH)+"-"+System.nanoTime()+".wcp");FileUtils.delete(temp);
        FileUtils.copy(c,asset,temp);
        if (!temp.isFile() || temp.length() == 0) return false;
        try{return installWcp(c,container,temp,expectedType,id);}finally{FileUtils.delete(temp);}
    }

    private static synchronized boolean installWcp(Context c,Container container,File wcp,String expectedType,String id){
        File stage=new File(c.getCacheDir(),"cerberus-component-stage-"+System.nanoTime());FileUtils.delete(stage);if(!stage.mkdirs())return false;
        try{
            byte[]raw=CerberusWcpUtils.readProfile(wcp);if(raw==null)return false;
            JSONObject profile=new JSONObject(new String(raw,StandardCharsets.UTF_8));String type=profile.optString("type","");if(!expectedType.equalsIgnoreCase(type))return false;
            JSONArray files=profile.optJSONArray("files");if(files==null||files.length()==0||!CerberusWcpUtils.extract(wcp,stage))return false;
            RootFS bionic=RootFS.findBionic(c);File system32=new File(container.getRootDir(),".wine/drive_c/windows/system32"),syswow64=new File(container.getRootDir(),".wine/drive_c/windows/syswow64");
            if(!system32.isDirectory()&&!system32.mkdirs())return false;if(!syswow64.isDirectory()&&!syswow64.mkdirs())return false;

            ArrayList<File>sources=new ArrayList<>(),targets=new ArrayList<>();boolean hasArmDll=false,hasWowFexDll=false,hasWowBoxDll=false,hasArmSo=false,hasWowSo=false;
            for(int i=0;i<files.length();i++){
                JSONObject f=files.optJSONObject(i);if(f==null)continue;String source=f.optString("source",""),target=f.optString("target","");
                File src=CerberusProfileUtils.resolveInside(stage,source);if(src==null||!src.exists()||target.isEmpty())return false;
                File dst=resolveTarget(bionic,system32,syswow64,target);if(dst==null)return false;
                String name=dst.getName().toLowerCase(Locale.ENGLISH);if(name.equals("libarm64ecfex.dll"))hasArmDll=true;if(name.equals("libwow64fex.dll"))hasWowFexDll=true;if(name.equals("wowbox64.dll"))hasWowBoxDll=true;if(name.equals("libarm64ecfex.so"))hasArmSo=true;if(name.equals("libwow64fex.so"))hasWowSo=true;
                sources.add(src);targets.add(dst);
            }
            if(CerberusCpuComponentStore.TYPE_FEX.equalsIgnoreCase(type)&&(!hasArmDll||!hasWowFexDll))return false;
            if(CerberusCpuComponentStore.TYPE_WOW.equalsIgnoreCase(type)&&!hasWowBoxDll)return false;

            // All mappings were validated before the first destination is modified.
            for(int i=0;i<sources.size();i++){
                File src=sources.get(i),dst=targets.get(i);File parent=dst.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())return false;
                if(src.isDirectory()){if(!FileUtils.copy(src,dst,file->FileUtils.chmod(file,0771)))return false;}else if(!FileUtils.copy(src,dst))return false;
            }

            if(CerberusCpuComponentStore.TYPE_FEX.equalsIgnoreCase(type)){
                if(!hasArmSo)deleteBionicFile(bionic,"libarm64ecfex.so");if(!hasWowSo)deleteBionicFile(bionic,"libwow64fex.so");
                if(!new File(system32,"libarm64ecfex.dll").isFile()||!new File(system32,"libwow64fex.dll").isFile())return false;
                return writeMarker(container,FEX_MARKER,id,profile.optString("versionName",""),hasArmSo||hasWowSo);
            }
            if(!new File(system32,"wowbox64.dll").isFile())return false;
            return writeMarker(container,WOW_MARKER,id,profile.optString("versionName",""),false);
        }catch(Exception e){return false;}finally{FileUtils.delete(stage);}
    }

    private static void deleteBionicFile(RootFS root,String filename){
        String[]dirs={"usr/lib","usr/lib64","usr/local/lib","usr/lib/wine/aarch64-unix","usr/lib/wine/arm64ec-unix","usr/local/lib/wine/aarch64-unix","lib","lib64","system/lib64"};
        for(String d:dirs)FileUtils.delete(new File(root.getRootDir(),d+"/"+filename));
    }
    private static boolean writeMarker(Container container,String marker,String id,String version,boolean unixlib){try{JSONObject o=new JSONObject();o.put("schema",2);o.put("id",id);o.put("versionName",version);o.put("unixlib",unixlib);return FileUtils.writeString(new File(container.getRootDir(),marker),o.toString());}catch(Exception e){return false;}}
    private static String trimSlash(String v){String s=v;while(s.startsWith("/"))s=s.substring(1);return s;}

    private static File resolveTarget(RootFS root,File system32,File syswow64,String target)throws Exception{
        String t=target==null?"":target.replace('\\','/').trim();
        String low=t.toLowerCase(Locale.ENGLISH);
        if(t.contains("${system32}"))return safeChild(system32,trimSlash(t.substring(t.indexOf("${system32}")+11)));
        if(t.contains("${syswow64}"))return safeChild(syswow64,trimSlash(t.substring(t.indexOf("${syswow64}")+11)));
        if(low.contains("system32/"))return safeChild(system32,trimSlash(t.substring(low.indexOf("system32/")+9)));
        if(low.contains("syswow64/"))return safeChild(syswow64,trimSlash(t.substring(low.indexOf("syswow64/")+9)));

        t=t.replace("${root}","").replace("${imagefs}","")
                .replace("${libdir}","usr/lib").replace("${bindir}","usr/bin").replace("${usr}","usr");
        return safeChild(root.getRootDir(),trimSlash(t));
    }

    private static File safeChild(File base,String tail)throws Exception{
        String safe=CerberusProfileUtils.normalizeRelative(tail);
        if(safe==null)return null;
        File root=base.getCanonicalFile(),result=new File(root,safe).getCanonicalFile();
        String bp=root.getPath(),rp=result.getPath();
        return rp.startsWith(bp+File.separator)?result:null;
    }
}
