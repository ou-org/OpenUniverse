package org.ou.common.utils;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.socket.IceSocketWrapper;
import org.ice4j.socket.IceUdpSocketWrapper;
import org.ice4j.stunclient.SimpleAddressDetector;


public class StunUtils {
public static void main2(String[] args) throws Exception {
        // STUN server
        TransportAddress stunServer =
                new TransportAddress("stun.l.google.com", 19302, Transport.UDP);

        // Start detector
        SimpleAddressDetector detector = new SimpleAddressDetector(stunServer);
        detector.start();

        try {
            // Create a local UDP socket (0 = any free port)
            DatagramSocket localSocket = new DatagramSocket(0);

            // Wrap the socket for ice4j
            IceSocketWrapper iceSocket = new IceUdpSocketWrapper(localSocket);

            // Ask STUN for the public mapping
            TransportAddress publicAddr = detector.getMappingFor(iceSocket);

            if (publicAddr != null) {
                System.out.println("Public IP: " + publicAddr.getAddress().getHostAddress());
                System.out.println("Public Port: " + publicAddr.getPort());
            } else {
                System.out.println("Could not determine public IP (maybe blocked by NAT/firewall).");
            }

            localSocket.close();
        } finally {
            detector.shutDown();
        }
}
    public static void main(String[] args) throws Exception {
        // 1️⃣ Get public IPv4 via STUN
        try {
            //TransportAddress stunServer = new TransportAddress("stun.l.google.com", 19302, Transport.UDP);
            TransportAddress stunServer = new TransportAddress("stun.l.google.com", 3478, Transport.UDP);
            SimpleAddressDetector detector = new SimpleAddressDetector(stunServer);
            detector.start();

            DatagramSocket socket = new DatagramSocket(0);
            IceUdpSocketWrapper iceSocket = new IceUdpSocketWrapper(socket);
            TransportAddress publicIPv4 = detector.getMappingFor(iceSocket);
            socket.close();
            detector.shutDown();

            if (publicIPv4 != null) {
                System.out.println("Public IPv4: " + publicIPv4.getAddress().getHostAddress());
            } else {
                System.out.println("Public IPv4: could not detect (NAT blocked STUN)");
            }
        } catch (Exception e) {
            System.out.println("STUN failed: " + e.getMessage());
        }

        // 2️⃣ Get all global IPv6 addresses
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface ni : Collections.list(interfaces)) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            for (InetAddress addr : Collections.list(addresses)) {
                if (addr instanceof Inet6Address) {
                    String s = addr.getHostAddress();
                    // Exclude link-local and unique-local
                    if (!s.startsWith("fe80") && !s.startsWith("fc")) {
                        System.out.println("Global IPv6: " + s);
                    }
                }
            }
        }
    }
}


