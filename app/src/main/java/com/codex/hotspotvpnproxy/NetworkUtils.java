package com.codex.hotspotvpnproxy;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class NetworkUtils {
    private NetworkUtils() {
    }

    public static String getLocalIpSummary() {
        List<String> addresses = getLocalIpAddresses();
        if (addresses.isEmpty()) {
            return "未检测到局域网 IP";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < addresses.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(addresses.get(i));
        }
        return builder.toString();
    }

    public static InetAddress getBestLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<NetworkInterface> all = Collections.list(interfaces);
            InetAddress fallback = null;
            for (NetworkInterface networkInterface : all) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                for (InetAddress address : Collections.list(inetAddresses)) {
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address) {
                        String name = networkInterface.getName().toLowerCase();
                        if (name.contains("wlan") || name.contains("ap")
                                || name.contains("swlan") || name.contains("rndis")) {
                            return address;
                        }
                        if (fallback == null) {
                            fallback = address;
                        }
                    }
                }
            }
            if (fallback != null) {
                return fallback;
            }
        } catch (SocketException ignored) {
        }
        return InetAddress.getLoopbackAddress();
    }

    public static boolean isPrivateOrLocal(InetAddress address) {
        if (address == null) {
            return false;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (address instanceof Inet4Address && raw.length == 4) {
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;
            return first == 100 && second >= 64 && second <= 127;
        }
        if (address instanceof Inet6Address && raw.length == 16) {
            int first = raw[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    public static boolean isVpnActive(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getLocalIpAddresses() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address
                            || (address instanceof Inet6Address && !address.isMulticastAddress())) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return result;
    }
}
