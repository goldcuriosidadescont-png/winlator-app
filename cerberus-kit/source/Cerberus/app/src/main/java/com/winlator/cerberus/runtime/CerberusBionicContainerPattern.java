package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import java.io.File;

public final class CerberusBionicContainerPattern {
    private CerberusBionicContainerPattern(){}
    public static boolean extract(Context context,CerberusRuntimeProfile profile,File destination){
        boolean builtin=CerberusProfileUtils.isBuiltinRuntimeId(profile.runtimeId);CerberusRuntimeStore.Info runtime=builtin?null:CerberusRuntimeStore.get(context,profile.runtimeId);
        if(!builtin&&runtime==null)return false; // never replace a deleted selected runtime with the embedded one
        if(runtime!=null){File pack=new File(runtime.root,runtime.prefixPack);return pack.isFile()&&extractWcpPrefix(context,pack,destination);}
        RuntimeArchitecture arch=profile.architecture;String xz=arch==RuntimeArchitecture.ARM64EC?CerberusBionicAssets.PATTERN_ARM_XZ:CerberusBionicAssets.PATTERN_X86_XZ;String zstd=arch==RuntimeArchitecture.ARM64EC?CerberusBionicAssets.PATTERN_ARM_ZSTD:CerberusBionicAssets.PATTERN_X86_ZSTD;
        if(CerberusBionicAssets.exists(context,xz))return validateExtract(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ,context,xz,destination),destination);
        if(CerberusBionicAssets.exists(context,zstd))return validateExtract(TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,context,zstd,destination),destination);return false;
    }
    private static boolean extractWcpPrefix(Context context,File pack,File destination){
        TarCompressorUtils.Type type=CerberusWcpUtils.detect(pack);if(type==null)return false;File staging=new File(context.getCacheDir(),"cerberus-prefix-staging-"+System.nanoTime());FileUtils.delete(staging);if(!staging.mkdirs())return false;
        try{if(!TarCompressorUtils.extract(type,pack,staging))return false;File prefix=findPrefix(staging,0);if(prefix==null)return false;File target=new File(destination,".wine");FileUtils.delete(target);return FileUtils.copy(prefix,target,file->FileUtils.chmod(file,0771))&&validPrefix(target);}finally{FileUtils.delete(staging);}
    }
    private static boolean validateExtract(boolean ok,File destination){return ok&&validPrefix(new File(destination,".wine"));}
    private static boolean validPrefix(File prefix){return prefix.isDirectory()&&new File(prefix,"drive_c/windows/system32").isDirectory()&&new File(prefix,"system.reg").isFile();}
    private static File findPrefix(File dir,int depth){if(dir==null||depth>5)return null;if(validPrefix(dir))return dir;File[]files=dir.listFiles();if(files==null)return null;for(File f:files)if(f.isDirectory()){File hit=findPrefix(f,depth+1);if(hit!=null)return hit;}return null;}
}
