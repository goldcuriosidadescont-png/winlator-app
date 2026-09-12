package com.winlator.cerberus.runtime;

import com.winlator.container.Container;
import org.json.JSONObject;
import org.json.JSONException;

/** Runtime metadata is additive and lives in Container.extraData. Old containers remain Classic. */
public final class CerberusContainerRuntime {
    public static final String K_ENGINE = "cerberus.engine";
    public static final String K_FAMILY = "cerberus.family";
    public static final String K_RUNTIME_ID = "cerberus.runtimeId";
    public static final String K_ARCH = "cerberus.arch";
    public static final String K_BACKEND64 = "cerberus.backend64";
    public static final String K_BACKEND32 = "cerberus.backend32";
    public static final String K_BOX64 = "cerberus.box64Version";
    public static final String K_FEX = "cerberus.fexcoreVersion";
    public static final String K_WOW = "cerberus.wowbox64Version";
    public static final String K_DXVK = "cerberus.dxvkVersion";
    public static final String K_VKD3D = "cerberus.vkd3dVersion";
    public static final String K_PREFIX_ARCH = "cerberus.prefixArch";

    private CerberusContainerRuntime() {}

    public static CerberusRuntimeProfile read(Container c) {
        RuntimeEngine engine = RuntimeEngine.fromId(c.getExtra(K_ENGINE, RuntimeEngine.CLASSIC.id));
        RuntimeFamily family = RuntimeFamily.fromId(c.getExtra(K_FAMILY, RuntimeFamily.WINE.id));
        RuntimeArchitecture arch = RuntimeArchitecture.fromId(c.getExtra(K_ARCH, RuntimeArchitecture.X86_64.id));
        RuntimeBackend b32 = RuntimeBackend.fromId(c.getExtra(K_BACKEND32, ""), RuntimeBackend.FEXCORE);
        CerberusRuntimeProfile p = CerberusRuntimeResolver.resolve(engine, family, arch, b32);
        p.runtimeId = c.getExtra(K_RUNTIME_ID, c.getWineVersion());
        p.box64Version = c.getExtra(K_BOX64, "");
        p.fexcoreVersion = c.getExtra(K_FEX, "");
        p.wowbox64Version = c.getExtra(K_WOW, "");
        p.dxvkVersion = c.getExtra(K_DXVK, "");
        p.vkd3dVersion = c.getExtra(K_VKD3D, "");
        p.prefixArchitecture = c.getExtra(K_PREFIX_ARCH, p.architecture.id);
        return p;
    }

    public static JSONObject toJson(CerberusRuntimeProfile p) {
        JSONObject out = new JSONObject();
        try {
            out.put(K_ENGINE, p.engine.id);
            out.put(K_FAMILY, p.family.id);
            out.put(K_RUNTIME_ID, p.runtimeId);
            out.put(K_ARCH, p.architecture.id);
            out.put(K_BACKEND64, p.backend64.id);
            out.put(K_BACKEND32, p.backend32.id);
            if (p.box64Version != null && !p.box64Version.isEmpty()) out.put(K_BOX64, p.box64Version);
            if (p.fexcoreVersion != null && !p.fexcoreVersion.isEmpty()) out.put(K_FEX, p.fexcoreVersion);
            if (p.wowbox64Version != null && !p.wowbox64Version.isEmpty()) out.put(K_WOW, p.wowbox64Version);
            if (p.dxvkVersion != null && !p.dxvkVersion.isEmpty()) out.put(K_DXVK, p.dxvkVersion);
            if (p.vkd3dVersion != null && !p.vkd3dVersion.isEmpty()) out.put(K_VKD3D, p.vkd3dVersion);
            out.put(K_PREFIX_ARCH, p.prefixArchitecture);
        } catch (JSONException ignored) {}
        return out;
    }

    public static void write(Container c, CerberusRuntimeProfile p) {
        c.putExtra(K_ENGINE, p.engine.id);
        c.putExtra(K_FAMILY, p.family.id);
        c.putExtra(K_RUNTIME_ID, p.runtimeId);
        c.putExtra(K_ARCH, p.architecture.id);
        c.putExtra(K_BACKEND64, p.backend64.id);
        c.putExtra(K_BACKEND32, p.backend32.id);
        c.putExtra(K_BOX64, emptyToNull(p.box64Version));
        c.putExtra(K_FEX, emptyToNull(p.fexcoreVersion));
        c.putExtra(K_WOW, emptyToNull(p.wowbox64Version));
        c.putExtra(K_DXVK, emptyToNull(p.dxvkVersion));
        c.putExtra(K_VKD3D, emptyToNull(p.vkd3dVersion));
        c.putExtra(K_PREFIX_ARCH, p.prefixArchitecture);
    }

    private static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }
}
