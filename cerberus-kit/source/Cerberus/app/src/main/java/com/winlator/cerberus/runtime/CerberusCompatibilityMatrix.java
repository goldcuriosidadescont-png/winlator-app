package com.winlator.cerberus.runtime;

public final class CerberusCompatibilityMatrix {
    private CerberusCompatibilityMatrix() {}

    public static String validate(CerberusRuntimeProfile p) {
        if (p == null) return "Perfil de runtime ausente";
        if (p.engine == RuntimeEngine.CLASSIC) {
            if (p.architecture != RuntimeArchitecture.X86_64) return "Classic GLIBC suporta somente x86_64";
            if (p.backend64 != RuntimeBackend.BOX64 || p.backend32 != RuntimeBackend.BOX64) return "Classic x86_64 requer Box64";
            return null;
        }
        if (p.architecture == RuntimeArchitecture.X86_64) {
            if (p.backend64 != RuntimeBackend.BOX64 || p.backend32 != RuntimeBackend.BOX64) return "Bionic x86_64 requer Box64";
            return null;
        }
        if (p.backend64 != RuntimeBackend.FEXCORE) return "ARM64EC requer FEXCore no backend 64-bit";
        if (p.backend32 != RuntimeBackend.FEXCORE && p.backend32 != RuntimeBackend.WOWBOX64) return "ARM64EC 32-bit requer FEXCore ou WOWBox64";
        return null;
    }
}
