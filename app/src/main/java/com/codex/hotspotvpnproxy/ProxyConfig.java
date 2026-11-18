package com.codex.hotspotvpnproxy;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProxyConfig {
    private static final String PREFS = "proxy_config";
    private static final String KEY_BIND_ADDRESS = "bind_address";
    private static final String KEY_HTTP_PORT = "http_port";
    private static final String KEY_SOCKS_PORT = "socks_port";
    private static final String KEY_HTTP_ENABLED = "http_enabled";
    private static final String KEY_SOCKS_ENABLED = "socks_enabled";
    private static final String KEY_LAN_ONLY = "lan_only";

    public final String bindAddress;
    public final int httpPort;
    public final int socksPort;
    public final boolean httpEnabled;
    public final boolean socksEnabled;
    public final boolean lanOnly;

    public ProxyConfig(String bindAddress, int httpPort, int socksPort, boolean httpEnabled,
            boolean socksEnabled, boolean lanOnly) {
        this.bindAddress = normalizeBindAddress(bindAddress);
        this.httpPort = httpPort;
        this.socksPort = socksPort;
        this.httpEnabled = httpEnabled;
        this.socksEnabled = socksEnabled;
        this.lanOnly = lanOnly;
    }

    public static ProxyConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ProxyConfig(
                prefs.getString(KEY_BIND_ADDRESS, "0.0.0.0"),
                prefs.getInt(KEY_HTTP_PORT, 8080),
                prefs.getInt(KEY_SOCKS_PORT, 1080),
                prefs.getBoolean(KEY_HTTP_ENABLED, true),
                prefs.getBoolean(KEY_SOCKS_ENABLED, true),
                prefs.getBoolean(KEY_LAN_ONLY, true));
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BIND_ADDRESS, bindAddress)
                .putInt(KEY_HTTP_PORT, httpPort)
                .putInt(KEY_SOCKS_PORT, socksPort)
                .putBoolean(KEY_HTTP_ENABLED, httpEnabled)
                .putBoolean(KEY_SOCKS_ENABLED, socksEnabled)
                .putBoolean(KEY_LAN_ONLY, lanOnly)
                .apply();
    }

    public boolean hasEnabledProxy() {
        return httpEnabled || socksEnabled;
    }

    public static int parsePort(String value, int fallback) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port > 0 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static String normalizeBindAddress(String address) {
        if (address == null) {
            return "0.0.0.0";
        }
        String trimmed = address.trim();
        return trimmed.length() == 0 ? "0.0.0.0" : trimmed;
    }
}
