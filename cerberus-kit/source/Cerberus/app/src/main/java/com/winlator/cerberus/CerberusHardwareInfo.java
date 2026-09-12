package com.winlator.cerberus;

import android.content.Context;
import android.os.Build;

import com.winlator.core.GPUHelper;


/**
 * Immutable hardware snapshot for Cerberus profile resolution.
 * This class performs detection only; it never writes system state.
 */
public final class CerberusHardwareInfo {
    public final String manufacturer;
    public final String model;
    public final String device;
    public final String board;
    public final String hardware;
    public final String socManufacturer;
    public final String socModel;
    public final String gpuRenderer;
    public final short adrenoModel;
    public final CerberusDeviceProfile profile;

    private CerberusHardwareInfo(
            String manufacturer,
            String model,
            String device,
            String board,
            String hardware,
            String socManufacturer,
            String socModel,
            String gpuRenderer,
            short adrenoModel,
            CerberusDeviceProfile profile) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.device = device;
        this.board = board;
        this.hardware = hardware;
        this.socManufacturer = socManufacturer;
        this.socModel = socModel;
        this.gpuRenderer = gpuRenderer;
        this.adrenoModel = adrenoModel;
        this.profile = profile;
    }

    public static CerberusHardwareInfo capture(Context context) {
        final String manufacturer = safe(Build.MANUFACTURER);
        final String model = safe(Build.MODEL);
        final String device = safe(Build.DEVICE);
        final String board = safe(Build.BOARD);
        final String hardware = safe(Build.HARDWARE);

        String socManufacturer = "";
        String socModel = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socManufacturer = safe(Build.SOC_MANUFACTURER);
            socModel = safe(Build.SOC_MODEL);
        }

        String gpuRenderer = "";
        short adrenoModel = 0;
        try {
            gpuRenderer = safe(GPUHelper.glGetRenderer(context));
            adrenoModel = GPUHelper.getAdrenoModelId(context);
        }
        catch (Throwable ignored) {
            // Detection must never prevent Winlator from starting.
        }

        final CerberusDeviceProfile profile = CerberusProfileResolver.resolve(
                socModel, board, hardware, gpuRenderer, adrenoModel);

        return new CerberusHardwareInfo(
                manufacturer,
                model,
                device,
                board,
                hardware,
                socManufacturer,
                socModel,
                gpuRenderer,
                adrenoModel,
                profile);
    }

    public String getSocDisplayName() {
        if (!socModel.isEmpty()) return socModel;
        if (!hardware.isEmpty()) return hardware;
        if (!board.isEmpty()) return board;
        return "unknown";
    }

    public String getGpuDisplayName() {
        if (!gpuRenderer.isEmpty()) return gpuRenderer;
        if (adrenoModel > 0) return "Adreno " + adrenoModel;
        return "unknown";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
