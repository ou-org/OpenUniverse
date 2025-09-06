package org.ou.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TerminalUtils {

    public static void setEcho(boolean b) throws IOException {
        new ProcessBuilder("sh", "-c", "stty " + (b ? "" : "-") + "echo").inheritIO().start();
    }

    public static char[] readPassword(String prompt) throws IOException {
        if (!prompt.endsWith(" ")) {
            prompt += ' ';
        }
        System.err.print(prompt);
        setEcho(false);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        char[] password = reader.readLine().toCharArray();
        setEcho(true);
        System.err.println();
        return password;
    }

    public static boolean isYes(String prompt) throws IOException {
        if (!prompt.endsWith(" ")) {
            prompt += ' ';
        }
        System.err.print(prompt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String answer = reader.readLine().strip().toLowerCase(Locale.ENGLISH);
        if (answer.equals("y") || answer.equals("yes")) {
            return true;
        }
        return false;
    }
}
