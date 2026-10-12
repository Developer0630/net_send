package com.netsend.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

public final class NetworkValidation {
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private NetworkValidation() {}

    public static Inet4Address ipv4(String value) {
        if (value == null || !IPV4.matcher(value.trim()).matches()) throw new IllegalArgumentException("Nhập địa chỉ IPv4 hợp lệ.");
        String[] octets = value.trim().split("\\.");
        byte[] address = new byte[4];
        for (int i = 0; i < octets.length; i++) {
            if (octets[i].length() > 1 && octets[i].startsWith("0"))
                throw new IllegalArgumentException("Địa chỉ IPv4 không được dùng octet có số 0 đứng đầu.");
            int octet;
            try { octet = Integer.parseInt(octets[i]); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Địa chỉ IPv4 không hợp lệ.", e); }
            if (octet < 0 || octet > 255) throw new IllegalArgumentException("Mỗi octet IPv4 phải nằm trong khoảng 0–255.");
            address[i] = (byte) octet;
        }
        try { return (Inet4Address) InetAddress.getByAddress(address); }
        catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("Địa chỉ IPv4 không hợp lệ.", e); }
    }

    public static void port(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port phải nằm trong khoảng 1–65535.");
    }

    public static void multicastAddress(String value) {
        if (!ipv4(value).isMulticastAddress()) throw new IllegalArgumentException("Địa chỉ group phải thuộc dải multicast IPv4 224.0.0.0/4.");
        if (value.startsWith("224.0.0.")) throw new IllegalArgumentException("Không dùng dải multicast cục bộ dành riêng 224.0.0.0/24.");
    }

    public static String message(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Nội dung tin nhắn không được để trống.");
        if (value.length() > 1000) throw new IllegalArgumentException("Tin nhắn tối đa 1000 ký tự.");
        return value;
    }
}
