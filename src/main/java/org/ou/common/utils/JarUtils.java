package org.ou.common.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public class JarUtils {

    /**
     * Runs jarsigner verification on the given jar file and saves report into
     * repoDir/jarsigner_report.txt
     *
     * @param repoDir Directory where report file will be saved
     * @param jarPath Path to jar file (e.g. self-jar)
     * @throws IOException if process fails or verification fails
     * @throws InterruptedException if process is interrupted
     */
    public static void createJarSignerReport(Path jarPath, Path reportFilePath) throws IOException, InterruptedException {
        File reportFile = reportFilePath.toFile();

        ProcessBuilder pb = new ProcessBuilder(
                "jarsigner",
                "-verify",
                "-verbose",
                "-certs",
                jarPath.toString()
        );
        pb.redirectErrorStream(true); // merge stderr into stdout
        pb.redirectOutput(reportFile);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Error: JAR signature verification failed. See report: " + reportFile);
        }
    }

    public static void createJarSHA256Report(Path jarPath, Path reportFilePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new BufferedInputStream(Files.newInputStream(jarPath))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hashBytes = digest.digest();
        Files.writeString(reportFilePath, HexFormat.of().formatHex(hashBytes).toLowerCase(Locale.ENGLISH));
    }

    /**
     * Returns the path to the currently running JAR file. If not running from a
     * JAR (e.g. from IDE/classes), returns null.
     */
    public static Path getSelfJar() {
        try {
            String pathStr = JarUtils.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            if (pathStr.endsWith("/")) 
                return null; // Running from IDE or classes, not a JAR
            return Path.of(pathStr);
        } catch (Exception e) {
            e.printStackTrace();
            return null; // fallback if something went wrong
        }
    }
}
