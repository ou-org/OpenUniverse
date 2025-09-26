package org.ou.common.utils;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class NtpUtils {

    public static String getUtcIsoTime(NTPUDPClient client, InetAddress hostAddr, Integer port) throws Exception {
            TimeInfo info = port == null ? client.getTime(hostAddr) : client.getTime(hostAddr, port);
            Instant instant = Instant.ofEpochMilli(info.getMessage()
                                                     .getTransmitTimeStamp()
                                                     .getTime());
            return DateTimeFormatter.ISO_INSTANT
                    .withZone(ZoneOffset.UTC)
                    .format(instant);
    }

    // public static String getUtcIsoTime(String ntpServer) throws Exception {
    //     NTPUDPClient client = new NTPUDPClient();
    //     client.setDefaultTimeout(1000);
    //     try {
    //         //client.open();
    //         InetAddress hostAddr = InetAddress.getByName(ntpServer);
    //         TimeInfo info = client.getTime(hostAddr);
    //         Instant instant = Instant.ofEpochMilli(info.getMessage()
    //                                                  .getTransmitTimeStamp()
    //                                                  .getTime());
    //         return DateTimeFormatter.ISO_INSTANT
    //                 .withZone(ZoneOffset.UTC)
    //                 .format(instant);
    //     } finally {
    //         //client.close();
    //     }
    // // }

    // public static void main(String[] args) {
    //     try {
    //         String utcIso = getUtcIsoTime("pool.ntp.org");
    //         System.out.println("UTC ISO time: " + utcIso);
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    // }
}
