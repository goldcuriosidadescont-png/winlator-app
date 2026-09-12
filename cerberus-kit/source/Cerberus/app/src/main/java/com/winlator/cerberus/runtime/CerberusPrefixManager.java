package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xenvironment.RootFS;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Locale;

/** Transactional prefix switching. Runtime/engine/architecture changes never silently reuse an incompatible Windows prefix. */
public final class CerberusPrefixManager {
    private CerberusPrefixManager(){}
    public static boolean requiresFreshPrefix(CerberusRuntimeProfile oldProfile,CerberusRuntimeProfile newProfile){
        if(oldProfile==null||newProfile==null)return false;
        if(oldProfile.engine!=newProfile.engine||oldProfile.architecture!=newProfile.architecture||oldProfile.family!=newProfile.family)return true;
        if(newProfile.isBionic()){
            String a=oldProfile.runtimeId==null?"":oldProfile.runtimeId,b=newProfile.runtimeId==null?"":newProfile.runtimeId;
            return !a.equals(b);
        }
        return false;
    }

    public static boolean prepareRuntimeChange(Context context,Container container,CerberusRuntimeProfile oldProfile,CerberusRuntimeProfile newProfile){
        if(!requiresFreshPrefix(oldProfile,newProfile))return true;
        File root=container.getRootDir(),current=new File(root,".wine"),stageRoot=new File(root,".cerberus-prefix-stage"),stagedPrefix=new File(stageRoot,".wine");FileUtils.delete(stageRoot);if(!stageRoot.mkdirs())return false;
        boolean built=newProfile.isBionic()?CerberusBionicContainerPattern.extract(context,newProfile,stageRoot):extractClassicPrefix(context,stageRoot);
        if(!built||!stagedPrefix.isDirectory()||!new File(stagedPrefix,"drive_c/windows/system32").isDirectory()||!new File(stagedPrefix,"system.reg").isFile()){FileUtils.delete(stageRoot);return false;}
        String stamp=String.valueOf(System.currentTimeMillis()),oldEngine=oldProfile.engine.id.toLowerCase(Locale.ENGLISH),oldArch=oldProfile.architecture.id.toLowerCase(Locale.ENGLISH);
        File snapshot=new File(root,".wine-rollback-"+oldEngine+"-"+oldArch+"-"+stamp);boolean hadCurrent=current.exists();if(hadCurrent&&!current.renameTo(snapshot)){FileUtils.delete(stageRoot);return false;}
        boolean committed=stagedPrefix.renameTo(current);if(!committed)committed=FileUtils.copy(stagedPrefix,current,file->FileUtils.chmod(file,0771));FileUtils.delete(stageRoot);
        if(!committed){FileUtils.delete(current);if(hadCurrent)snapshot.renameTo(current);return false;}
        // Runtime/component markers are prefix-specific and must be recreated against the new prefix.
        FileUtils.delete(new File(root,".cerberus-fexcore.json"));FileUtils.delete(new File(root,".cerberus-wowbox64.json"));FileUtils.delete(new File(root,".cerberus-dxvk.json"));FileUtils.delete(new File(root,".cerberus-vkd3d.json"));
        pruneRollbackSnapshots(root,snapshot,2);return true;
    }

    public static boolean prepareArchitectureChange(Container container,String oldArch,String newArch){if(oldArch==null||oldArch.isEmpty()||oldArch.equalsIgnoreCase(newArch))return true;File prefix=new File(container.getRootDir(),".wine");if(!prefix.exists())return true;File snapshot=new File(container.getRootDir(),".wine-rollback-"+oldArch+"-to-"+newArch+"-"+System.currentTimeMillis());return prefix.renameTo(snapshot);}

    private static void pruneRollbackSnapshots(File root,File keep,int max){
        File[]all=root.listFiles((d,n)->n.startsWith(".wine-rollback-"));if(all==null||all.length<=max)return;
        java.util.Arrays.sort(all,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(int i=max;i<all.length;i++)if(keep==null||!all[i].equals(keep))FileUtils.delete(all[i]);
    }
    private static boolean extractClassicPrefix(Context context,File destination){
        if(!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,context,"container_pattern.tzst",destination))return false;
        try{JSONObject common=new JSONObject(FileUtils.readString(context,"common_dlls.json"));return copyCommonDlls(context,destination,common,"x86_64-windows","system32")&&copyCommonDlls(context,destination,common,"i386-windows","syswow64");}catch(Exception e){return false;}
    }
    private static boolean copyCommonDlls(Context context,File destination,JSONObject common,String srcName,String dstName)throws Exception{
        RootFS classic=RootFS.find(context);File srcDir=new File(classic.getRootDir(),"opt/wine/lib/wine/"+srcName);JSONArray names=common.getJSONArray(dstName);
        for(int i=0;i<names.length();i++){String name=names.getString(i);File src=new File(srcDir,name),dst=new File(destination,".wine/drive_c/windows/"+dstName+"/"+name);if(!FileUtils.copy(src,dst))return false;}return true;
    }
}
