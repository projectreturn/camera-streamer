package de.local.tapoviewer;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class NetworkUtil {
    private NetworkUtil() {}

    public static String findLocalIpv4() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                for (java.net.InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    public static String subnetPrefix(String ip) {
        if (ip == null) return null;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return null;
        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return null;
            }
            return parts[0] + "." + parts[1] + "." + parts[2] + ".";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isValidIpv4(String ip) {
        String prefix = subnetPrefix(ip);
        if (prefix == null) return false;
        int last = Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1));
        return last > 0 && last < 255;
    }
}
