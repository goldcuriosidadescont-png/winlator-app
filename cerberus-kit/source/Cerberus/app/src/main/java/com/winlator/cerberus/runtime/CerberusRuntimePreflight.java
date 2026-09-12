package com.winlator.cerberus.runtime;

import android.content.Context;
import com.winlator.container.Container;
import com.winlator.xenvironment.RootFS;
import java.io.File;

/** Blocks inconsistent Bionic launches before Wine starts. */
public final class CerberusRuntimePreflight {
    private CerberusRuntimePreflight(){}
    public static String check(Context context,Container container,CerberusRuntimeProfile p){
        String compatibility=CerberusCompatibilityMatrix.validate(p);if(compatibility!=null)return compatibility;if(!p.isBionic())return null;
        if(!CerberusBionicInstaller.ensureInstalled(context))return "Bionic imagefs ausente, incompleto ou incompatível com esta build";
        RootFS bionic=RootFS.findBionic(context);File root=bionic.getRootDir();File prefix=new File(container.getRootDir(),".wine");
        if(!prefix.isDirectory()||!new File(prefix,"drive_c/windows/system32").isDirectory()||!new File(prefix,"system.reg").isFile())return "Prefix Bionic ausente/inválido";

        boolean builtin=CerberusProfileUtils.isBuiltinRuntimeId(p.runtimeId);CerberusRuntimeStore.Info runtime=builtin?null:CerberusRuntimeStore.get(context,p.runtimeId);
        if(!builtin&&runtime==null)return "Runtime WCP selecionado não está instalado: "+p.runtimeId;
        if(runtime==null&&p.family==RuntimeFamily.PROTON)return "Proton Bionic requer um runtime Proton WCP instalado; o runtime embutido fornece somente Wine";
        if(runtime!=null){
            if(!runtime.architecture.equalsIgnoreCase(p.architecture.id))return "Arquitetura do WCP não corresponde ao container";
            if(!runtime.type.equalsIgnoreCase(p.family.label))return "Família do WCP não corresponde ao container";
            File bin=new File(runtime.root,runtime.binPath),lib=new File(runtime.root,runtime.libPath),pack=new File(runtime.root,runtime.prefixPack);
            if(!new File(bin,"wine").isFile()&&!new File(bin,"wine64").isFile())return "Wine executável ausente no runtime WCP";
            if(!lib.isDirectory())return "Bibliotecas do runtime WCP ausentes";if(!pack.isFile())return "prefixPack do runtime WCP ausente";
        }else{
            File builtinWine=new File(root,bionic.getWinePath()+"/bin/wine"),builtin64=new File(root,bionic.getWinePath()+"/bin/wine64");
            if(!builtinWine.isFile()&&!builtin64.isFile())return "Nenhum Wine Bionic embutido encontrado; instale um runtime WCP";
        }
        if(p.architecture==RuntimeArchitecture.X86_64&&!new File(root,"usr/bin/box64").isFile()&&!new File(root,"usr/local/bin/box64").isFile())return "Box64 Bionic não encontrado em /usr/bin ou /usr/local/bin";
        if(!CerberusBionicGuestComponentInstaller.ensure(context,container,p))return "DXVK/VKD3D Bionic selecionado está ausente, corrompido ou não corresponde à arquitetura";
        if(p.architecture==RuntimeArchitecture.ARM64EC){
            if(!CerberusBionicCpuComponentInstaller.ensure(context,container,p))return "FEXCore/WOWBox64 selecionado está ausente ou inválido";
            File system32=new File(prefix,"drive_c/windows/system32");if(!new File(system32,"libarm64ecfex.dll").isFile())return "FEXCore ARM64EC não instalado: libarm64ecfex.dll ausente";
            String wowDll=p.backend32==RuntimeBackend.WOWBOX64?"wowbox64.dll":"libwow64fex.dll";if(!new File(system32,wowDll).isFile())return "Backend 32-bit ausente: "+wowDll;
        }
        return null;
    }
}
