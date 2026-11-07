package com.science.gtnl.common.wireless.remote;

import java.util.Locale;

public enum RemoteDeviceState {

    OPEN,
    NO_GUI,
    UNSUPPORTED,
    UNLOADED,
    STALE,
    DENIED,
    CARD,
    FAILED;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RemoteDeviceState byKey(String key) {
        for (RemoteDeviceState state : values()) if (state.key()
            .equals(key)) return state;
        return NO_GUI;
    }
}
