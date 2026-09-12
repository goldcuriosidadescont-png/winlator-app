package com.winlator.cerberus.runtime;

public final class CerberusRuntimeProfile {
    public RuntimeEngine engine = RuntimeEngine.CLASSIC;
    public RuntimeFamily family = RuntimeFamily.WINE;
    public RuntimeArchitecture architecture = RuntimeArchitecture.X86_64;
    public RuntimeBackend backend64 = RuntimeBackend.BOX64;
    public RuntimeBackend backend32 = RuntimeBackend.BOX64;
    public String runtimeId = "builtin";
    public String box64Version = "";
    public String fexcoreVersion = "";
    public String wowbox64Version = "";
    public String dxvkVersion = "";
    public String vkd3dVersion = "";
    public String prefixArchitecture = RuntimeArchitecture.X86_64.id;

    public CerberusRuntimeProfile copy() {
        CerberusRuntimeProfile p = new CerberusRuntimeProfile();
        p.engine = engine;
        p.family = family;
        p.architecture = architecture;
        p.backend64 = backend64;
        p.backend32 = backend32;
        p.runtimeId = runtimeId;
        p.box64Version = box64Version;
        p.fexcoreVersion = fexcoreVersion;
        p.wowbox64Version = wowbox64Version;
        p.dxvkVersion = dxvkVersion;
        p.vkd3dVersion = vkd3dVersion;
        p.prefixArchitecture = prefixArchitecture;
        return p;
    }

    public boolean isBionic() { return engine == RuntimeEngine.BIONIC; }
    public boolean isArm64ec() { return architecture == RuntimeArchitecture.ARM64EC; }
}
