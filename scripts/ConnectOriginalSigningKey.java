/* Run with JDK21 from the ORIGINAL PC's Runmoni checkout:
 *   java scripts/ConnectOriginalSigningKey.java          (local preflight)
 *   java scripts/ConnectOriginalSigningKey.java --connect
 * Uses the retained .secrets files; never creates or exports a replacement key.
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ConnectOriginalSigningKey {
    static final String CERTIFICATE = "2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830";
    static final String REPO = "wjdals988/runmoni";
    static final String ENVIRONMENT = "android-release";
    static class ConnectionError extends Exception {
        ConnectionError(String message) { super(message); }
    }
    // Deliberately no generated toString() containing credential fields.
    static final class Inputs {
        final Map<String, byte[]> values = new LinkedHashMap<>();
        void clear() { values.values().forEach(v -> Arrays.fill(v, (byte) 0)); }
    }
    record Response(int exit, String output) {}
    interface Transport { Response call(List<String> arguments, byte[] input) throws Exception; }

    static String required(Properties properties, String name) throws ConnectionError {
        String value = properties.getProperty(name);
        if (value == null || value.isEmpty())
            throw new ConnectionError("Retained signing.properties is incomplete. No values were printed.");
        return value;
    }

    static Inputs load(Path root, String expectedCertificate) throws Exception {
        Path settings = root.resolve(".secrets/signing.properties");
        if (!Files.isRegularFile(settings))
            throw new ConnectionError("Retained .secrets/signing.properties is missing on this machine.");
        if (Files.size(settings) > 65536)
            throw new ConnectionError("Retained signing.properties exceeds the supported size.");
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(settings)) { properties.load(stream); }
        Path key = root.resolve(required(properties, "storeFile")).normalize();
        if (!Files.isRegularFile(key))
            throw new ConnectionError("The ORIGINAL retained keystore file is missing. No key was generated.");
        if (Files.size(key) <= 0 || Files.size(key) > 48000)
            throw new ConnectionError("Retained keystore exceeds GitHub secret size limits.");
        String storePassword = required(properties, "storePassword");
        String alias = required(properties, "keyAlias");
        String keyPassword = required(properties, "keyPassword");
        byte[] raw;
        try (InputStream stream = Files.newInputStream(key)) { raw = stream.readNBytes(48001); }
        char[] storeChars = storePassword.toCharArray(), keyChars = keyPassword.toCharArray();
        try {
            if (raw.length == 0 || raw.length > 48000)
                throw new ConnectionError("Retained keystore exceeds GitHub secret size limits.");
            boolean jks = raw.length >= 4 && raw[0] == (byte) 0xfe && raw[1] == (byte) 0xed
                    && raw[2] == (byte) 0xfe && raw[3] == (byte) 0xed;
            KeyStore store = KeyStore.getInstance(jks ? "JKS" : "PKCS12");
            store.load(new ByteArrayInputStream(raw), storeChars);
            var certificate = store.getCertificate(alias);
            if (certificate == null)
                throw new ConnectionError("Retained key alias has no certificate.");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            if (!digest.equals(expectedCertificate))
                throw new ConnectionError("Certificate differs from the existing public Runmoni app. Connection stopped.");
            if (!(store.getKey(alias, keyChars) instanceof PrivateKey privateKey))
                throw new ConnectionError("The retained private key could not be opened.");
            byte[] challenge = "Runmoni retained signing-key ownership preflight v1".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey); signer.update(challenge); byte[] proof = signer.sign();
            signer.initVerify(certificate.getPublicKey()); signer.update(challenge);
            if (!signer.verify(proof))
                throw new ConnectionError("Retained private key does not match the public certificate.");
            Inputs inputs = new Inputs();
            inputs.values.put("RUNMONI_STORE_PASSWORD", storePassword.getBytes(StandardCharsets.UTF_8));
            inputs.values.put("RUNMONI_KEY_ALIAS", alias.getBytes(StandardCharsets.UTF_8));
            inputs.values.put("RUNMONI_KEY_PASSWORD", keyPassword.getBytes(StandardCharsets.UTF_8));
            inputs.values.put("RUNMONI_KEYSTORE_BASE64", Base64.getEncoder().encode(raw));
            return inputs;
        } finally {
            Arrays.fill(storeChars, '\0'); Arrays.fill(keyChars, '\0');
            Arrays.fill(raw, (byte) 0);
        }
    }

    static void connect(Inputs inputs, Transport gh) throws Exception {
        String path = "repos/" + REPO + "/environments/" + ENVIRONMENT;
        Response existing = gh.call(List.of("api", "--include", path), null);
        var status = Pattern.compile("HTTP/\\S+\\s+(\\d{3})").matcher(existing.output());
        int code = status.find() ? Integer.parseInt(status.group(1)) : 0;
        if (code == 404) {
            if (gh.call(List.of("api", "--method", "PUT", path), null).exit() != 0)
                throw new ConnectionError("GitHub environment creation was denied. No signing secrets were sent.");
        } else if (existing.exit() != 0 || code != 200) {
            throw new ConnectionError("GitHub environment access failed. No signing secrets were sent.");
        }
        if (gh.call(List.of("api", path + "/secrets/public-key"), null).exit() != 0)
            throw new ConnectionError("GitHub signing-secret access was denied. No signing secrets were sent.");
        int written = 0;
        for (var entry : inputs.values.entrySet()) {
            Response result = gh.call(List.of("secret", "set", entry.getKey(), "--repo", REPO,
                                             "--env", ENVIRONMENT), entry.getValue());
            if (result.exit() != 0)
                throw new ConnectionError("Connection stopped after " + written + " secret entries. Re-run only after fixing GitHub access; do not start a release yet.");
            written++;
            System.out.println("Connected " + entry.getKey() + " (value omitted).");
        }
    }

    static Map<String, String> childEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).startsWith("GIT_TRACE")
                || k.toUpperCase(Locale.ROOT).startsWith("GCM_TRACE")
                || Set.of("GH_DEBUG", "GIT_CURL_VERBOSE", "SSLKEYLOGFILE").contains(k.toUpperCase(Locale.ROOT)));
        env.put("GH_PROMPT_DISABLED", "1"); env.put("GIT_TERMINAL_PROMPT", "0"); env.put("GCM_INTERACTIVE", "never");
        return env;
    }

    static Response process(List<String> command, byte[] input, Map<String, String> env) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear(); builder.environment().putAll(env);
        Process child = builder.start();
        try {
            // API/GCM stdout stays in memory, never a credential file or console.
            var captured = new java.util.concurrent.CompletableFuture<byte[]>();
            Thread.ofPlatform().daemon().start(() -> {
                try { captured.complete(child.getInputStream().readNBytes(1048577)); }
                catch (IOException failure) { captured.completeExceptionally(failure); }
            });
            try (OutputStream stream = child.getOutputStream()) { if (input != null) stream.write(input); }
            if (!child.waitFor(60, TimeUnit.SECONDS))
                throw new ConnectionError("GitHub/Git command timed out. Raw output omitted.");
            byte[] bytes = captured.get(5, TimeUnit.SECONDS);
            if (bytes.length > 1048576)
                throw new ConnectionError("GitHub/Git response exceeded its size limit. Raw output omitted.");
            return new Response(child.exitValue(), new String(bytes, StandardCharsets.UTF_8));
        } finally {
            if (child.isAlive()) child.destroyForcibly();
            child.getInputStream().close();
        }
    }

    static Transport github(Map<String, String> env) throws Exception {
        if (process(List.of("gh", "auth", "status", "--hostname", "github.com"), null, env).exit() != 0) {
            // Reuse the ORIGINAL Windows PC's GCM login, never cloud-proxy credentials.
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                    || env.containsKey("GH_TOKEN") || env.containsKey("GITHUB_TOKEN"))
                throw new ConnectionError("Existing GitHub authentication is not usable. No interactive login was started.");
            Response helper = process(List.of("git", "config", "--get-all", "credential.helper"), null, env);
            if (helper.exit() != 0 || !helper.output().toLowerCase(Locale.ROOT).contains("manager"))
                throw new ConnectionError("Use an existing GitHub CLI or Windows GCM login on the original PC.");
            Response credentials = process(List.of("git", "credential", "fill"),
                    "protocol=https\nhost=github.com\nusername=wjdals988\n\n".getBytes(StandardCharsets.UTF_8), env);
            String token = credentials.output().lines().filter(l -> l.startsWith("password="))
                    .map(l -> l.substring(9)).findFirst().orElse("");
            if (credentials.exit() != 0 || token.isEmpty())
                throw new ConnectionError("Existing Windows GCM authentication is unavailable. Credential output omitted.");
            env.put("GH_TOKEN", token);
        }
        return (arguments, input) -> {
            List<String> command = new ArrayList<>(List.of("gh")); command.addAll(arguments);
            return process(command, input, env);
        };
    }

    public static void main(String[] args) {
        Inputs inputs = null;
        int exit = 0;
        try {
            if (args.length > 1 || (args.length == 1 && !args[0].equals("--connect")))
                throw new ConnectionError("Usage: java scripts/ConnectOriginalSigningKey.java [--connect]");
            inputs = load(Path.of("").toAbsolutePath(), CERTIFICATE);
            System.out.println("Original public certificate and retained private key verified. Values omitted.");
            if (args.length == 0) {
                System.out.println("Local preflight only. GitHub was not contacted. Use --connect to link the retained key.");
            } else {
                connect(inputs, github(childEnvironment()));
                System.out.println("All four retained signing inputs connected to android-release. No app was published.");
            }
        } catch (Exception error) {
            System.err.println(error instanceof ConnectionError ? error.getMessage()
                    : "Connection failed; raw credential/key diagnostics omitted. Check the retained files, JDK21, gh and GitHub permissions.");
            exit = 1;
        } finally { if (inputs != null) inputs.clear(); }
        if (exit != 0) System.exit(exit);
    }
}
