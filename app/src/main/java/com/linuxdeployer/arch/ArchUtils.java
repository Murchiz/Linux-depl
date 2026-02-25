package com.linuxdeployer.arch;

import android.os.Build;
import java.util.Arrays;

public class ArchUtils {
    public enum Architecture {
        ARMV7L,
        AARCH64,
        UNKNOWN
    }

    public static Architecture getDeviceArchitecture() {
        String[] abis = Build.SUPPORTED_ABIS;
        for (String abi : abis) {
            if (abi.contains("arm64-v8a")) return Architecture.AARCH64;
            if (abi.contains("armeabi-v7a")) return Architecture.ARMV7L;
        }

        // Fallback to system property
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return Architecture.AARCH64;
        if (arch.contains("armv7") || arch.contains("armeabi")) return Architecture.ARMV7L;

        return Architecture.UNKNOWN;
    }

    public static String getAlpineUrl(Architecture arch) {
        String baseUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.18/releases/";
        switch (arch) {
            case AARCH64:
                return baseUrl + "aarch64/alpine-minirootfs-3.18.4-aarch64.tar.gz";
            case ARMV7L:
                return baseUrl + "armv7/alpine-minirootfs-3.18.4-armv7.tar.gz";
            default:
                return null;
        }
    }
}
