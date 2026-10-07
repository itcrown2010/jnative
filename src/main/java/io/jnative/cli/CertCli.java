package io.jnative.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** `jnative cert` — development TLS certificate helpers (0.31). */
public final class CertCli {

    private static final Pattern PEM_SUBJECT = Pattern.compile("subject\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEM_ISSUER = Pattern.compile("issuer\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private CertCli() {
    }

    public static int dispatch(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return 1;
        }
        return switch (args[1]) {
            case "dev" -> runDev(slice(args, 2));
            case "inspect" -> runInspect(slice(args, 2));
            default -> {
                System.err.println("unknown cert subcommand: " + args[1]);
                printUsage();
                yield 1;
            }
        };
    }

    private static int runDev(String[] args) throws Exception {
        Path outDir = Path.of(".").toAbsolutePath().normalize();
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                outDir = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if (args[i].startsWith("-")) {
                System.err.println("unknown option: " + args[i]);
                printUsage();
                return 1;
            }
        }
        Files.createDirectories(outDir);
        Path crt = outDir.resolve("server.crt");
        Path key = outDir.resolve("server.key");

        if (!opensslAvailable()) {
            System.err.println("error: openssl CLI not found in PATH");
            System.err.println("Install OpenSSL and retry, or generate certificates manually.");
            return 1;
        }

        List<String> cmd = List.of(
                "openssl", "req", "-x509", "-newkey", "rsa:2048",
                "-keyout", key.toString(),
                "-out", crt.toString(),
                "-days", "365",
                "-nodes",
                "-subj", "/CN=localhost",
                "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1"
        );
        int code = runProcess(cmd, outDir);
        if (code != 0) {
            System.err.println("error: openssl failed (exit " + code + ")");
            return 1;
        }

        System.out.println("Development self-signed certificate — NOT for production.");
        System.out.println("Wrote " + crt);
        System.out.println("Wrote " + key);
        System.out.println("Use with: jnative serve App.java --tls " + crt + " " + key);
        return 0;
    }

    private static int runInspect(String[] args) throws Exception {
        if (args.length == 0 || args[0].startsWith("-")) {
            System.err.println("usage: jnative cert inspect <file.crt>");
            return 1;
        }
        Path file = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            System.err.println("error: file not found: " + file);
            return 1;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".key") || name.contains("private")) {
            System.err.println("error: refusing to inspect a private key file");
            return 1;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.contains("BEGIN PRIVATE KEY") || text.contains("BEGIN RSA PRIVATE KEY")) {
            System.err.println("error: file appears to contain a private key — use a .crt/.pem certificate");
            return 1;
        }

        if (opensslAvailable()) {
            List<String> cmd = List.of(
                    "openssl", "x509", "-in", file.toString(), "-noout",
                    "-subject", "-issuer", "-dates", "-ext", "subjectAltName"
            );
            int code = runProcess(cmd, file.getParent());
            if (code == 0) {
                return 0;
            }
            System.err.println("warning: openssl inspect failed, falling back to PEM heuristics");
        } else {
            System.out.println("(openssl not found — showing PEM heuristics only)");
        }
        printPemHeuristics(text);
        return 0;
    }

    private static void printPemHeuristics(String pem) {
        String subject = findPemField(pem, PEM_SUBJECT);
        String issuer = findPemField(pem, PEM_ISSUER);
        if (subject != null) {
            System.out.println("subject=" + subject.trim());
        }
        if (issuer != null) {
            System.out.println("issuer=" + issuer.trim());
        }
        for (String line : pem.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Not Before") || t.startsWith("Not After")
                    || t.startsWith("notBefore=") || t.startsWith("notAfter=")) {
                System.out.println(t);
            }
        }
        Matcher san = Pattern.compile("DNS:[^,\\s]+|IP Address:[^,\\s]+", Pattern.CASE_INSENSITIVE).matcher(pem);
        if (san.find()) {
            System.out.print("subjectAltName=");
            StringBuilder sb = new StringBuilder();
            san.reset();
            while (san.find()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(san.group());
            }
            System.out.println(sb);
        }
        if (subject == null && issuer == null) {
            System.out.println("(no parseable certificate metadata in file)");
        }
    }

    private static String findPemField(String text, Pattern pattern) {
        for (String line : text.split("\n")) {
            Matcher m = pattern.matcher(line.trim());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static boolean opensslAvailable() {
        try {
            Process p = new ProcessBuilder("openssl", "version")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int runProcess(List<String> cmd, Path cwd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        return process.waitFor();
    }

    private static void printUsage() {
        System.out.println("""
                jnative cert — TLS certificate helpers (development)

                Usage:
                  jnative cert dev [--out DIR]     Generate localhost self-signed cert (server.crt + server.key)
                  jnative cert inspect <file.crt>  Show subject, issuer, dates, SAN (never prints private keys)

                Requires openssl in PATH for generation; inspect falls back to PEM heuristics.
                """);
    }

    private static String[] slice(String[] args, int from) {
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }
}
