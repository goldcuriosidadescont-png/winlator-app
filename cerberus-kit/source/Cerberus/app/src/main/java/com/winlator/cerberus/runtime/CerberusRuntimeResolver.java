package com.winlator.cerberus.runtime;

/** Architecture decides the translator; Wine vs Proton does not. */
public final class CerberusRuntimeResolver {
    private CerberusRuntimeResolver() {}

    public static CerberusRuntimeProfile resolve(RuntimeEngine engine, RuntimeFamily family,
            RuntimeArchitecture architecture, RuntimeBackend requested32) {
        CerberusRuntimeProfile p = new CerberusRuntimeProfile();
        p.engine = engine;
        p.family = family;
        p.architecture = architecture;
        p.prefixArchitecture = architecture.id;
        if (engine == RuntimeEngine.CLASSIC) {
            p.architecture = RuntimeArchitecture.X86_64;
            p.prefixArchitecture = RuntimeArchitecture.X86_64.id;
            p.backend64 = RuntimeBackend.BOX64;
            p.backend32 = RuntimeBackend.BOX64;
        }
        else if (architecture == RuntimeArchitecture.X86_64) {
            p.backend64 = RuntimeBackend.BOX64;
            p.backend32 = RuntimeBackend.BOX64;
        }
        else {
            p.backend64 = RuntimeBackend.FEXCORE;
            p.backend32 = requested32 == RuntimeBackend.WOWBOX64 ? RuntimeBackend.WOWBOX64 : RuntimeBackend.FEXCORE;
        }
        return p;
    }
}
