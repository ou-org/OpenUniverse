package org.ou.common.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenInfo;

// https://www.ietf.org/rfc/rfc3161.txt - RFC 3161 Time-Stamp Protocol (TSP)

public class TsaUtils {

    public static byte[] generateTsaRequest(byte[] digest, ASN1ObjectIdentifier oid, BigInteger nonce) throws IOException {
        // "2.16.840.1.101.3.4.2.1"); // SHA-256 OID
        // new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)
        // ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1");

        TimeStampRequestGenerator tsqGen = new TimeStampRequestGenerator();
        tsqGen.setCertReq(true); // ask TSA to include its certificate
        TimeStampRequest request = tsqGen.generate(new AlgorithmIdentifier(oid), digest, nonce);
        return request.getEncoded();
    }

    public static byte[] getTsaResponse(URL tsaUrl, byte[] requestBytes) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) tsaUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/timestamp-query");
        conn.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBytes);
        }
        byte[] respBytes;
        try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            respBytes = baos.toByteArray();
        }
        conn.disconnect();
        return respBytes;
    }

    public static void parseTsaResponse(byte[] respBytes, Map<String, Object> map) throws IOException, TSPException {
        TimeStampResponse response = new TimeStampResponse(respBytes);
        TimeStampToken tsToken = response.getTimeStampToken();
        TimeStampTokenInfo tsTokenInfo = tsToken.getTimeStampInfo();
        map.put("timestamp", tsTokenInfo.getGenTime());
        map.put("serial", tsTokenInfo.getSerialNumber());
    }

    public static String normalizeDigestName(String bcName) {
        switch (bcName.toUpperCase()) {
            case "SHA1":
                return "SHA-1";
            case "SHA224":
                return "SHA-224";
            case "SHA256":
                return "SHA-256";
            case "SHA384":
                return "SHA-384";
            case "SHA512":
                return "SHA-512";
            default:
                return bcName; // leave as-is
        }
    }
}
