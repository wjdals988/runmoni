/* Original Windows PC only. Java11+, existing Git/GCM login and native curl.exe.
 * prepare: prints ONLY GitHub's encryption PUBLIC key packet.
 * apply <file>: uploads ONLY GitHub-sealed ciphertext; never reads signing secrets.
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class RunmoniGitHubSecretsBridge {
    static final String REPO = "wjdals988/runmoni";
    static final String CERTIFICATE = "a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a";
    static final String FORMAT = "runmoni-github-sealed-secrets-v1";
    static final String API = "https://api.github.com/repos/" + REPO;
    static final String MARKER = "RUNMONI_HTTP_STATUS:";
    static final List<String> NAMES = List.of("RUNMONI_STORE_PASSWORD", "RUNMONI_KEY_ALIAS",
            "RUNMONI_KEY_PASSWORD", "RUNMONI_KEYSTORE_BASE64");
    static class BridgeError extends Exception { BridgeError(String message) { super(message); } }
    static class Response {
        final int status; final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
    interface Api { Response call(String method, String path, String json) throws Exception; }

    static Map<String,String> publicKey(Response response) throws Exception {
        if (response.status != 200)
            throw new BridgeError("GitHub public-key lookup failed (HTTP " + response.status + "). No signing data sent.");
        Map<String,String> result = new HashMap<>();
        for (String name : List.of("key_id", "key")) {
            Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body);
            if (!matcher.find()) throw new BridgeError("GitHub public-key response is incomplete.");
            result.put(name, matcher.group(1));
        }
        if (!result.get("key_id").matches("[A-Za-z0-9_-]{1,128}"))
            throw new BridgeError("GitHub encryption key ID is invalid.");
        byte[] decoded = Base64.getDecoder().decode(result.get("key"));
        if (decoded.length != 32 || !Base64.getEncoder().encodeToString(decoded).equals(result.get("key")))
            throw new BridgeError("GitHub encryption public key is invalid.");
        return result;
    }

    static String prepare(Api api) throws Exception {
        Map<String,String> key = publicKey(api.call("GET", "/actions/secrets/public-key", null));
        return "{\"repository\":\"" + REPO + "\",\"scope\":\"repository\",\"keyId\":\""
                + key.get("key_id") + "\",\"publicKey\":\"" + key.get("key") + "\"}";
    }

    static void apply(Properties payload, Api api) throws Exception {
        Set<String> expected = new HashSet<>(NAMES);
        expected.addAll(List.of("format", "repository", "scope", "certificateSha256", "keyId", "publicKey"));
        if (!payload.stringPropertyNames().equals(expected) || !FORMAT.equals(payload.getProperty("format"))
                || !REPO.equals(payload.getProperty("repository")) || !"repository".equals(payload.getProperty("scope"))
                || !CERTIFICATE.equals(payload.getProperty("certificateSha256")))
            throw new BridgeError("Encrypted payload target or signing certificate differs. Nothing uploaded.");
        for (String name : NAMES) {
            String value = payload.getProperty(name);
            if (value == null || !value.matches("[A-Za-z0-9+/=]+"))
                throw new BridgeError("Encrypted payload is incomplete. Nothing uploaded.");
            int length = Base64.getDecoder().decode(value).length;
            if (length <= 48 || length > 65584)
                throw new BridgeError("Encrypted payload size is invalid. Nothing uploaded.");
        }
        Map<String,String> current = publicKey(api.call("GET", "/actions/secrets/public-key", null));
        if (!current.get("key_id").equals(payload.getProperty("keyId"))
                || !current.get("key").equals(payload.getProperty("publicKey")))
            throw new BridgeError("GitHub encryption public key changed. Request a fresh public packet; do not regenerate the signing key.");
        int written = 0;
        for (String name : NAMES) {
            String body = "{\"encrypted_value\":\"" + payload.getProperty(name)
                    + "\",\"key_id\":\"" + current.get("key_id") + "\"}";
            Response response = api.call("PUT", "/actions/secrets/" + name, body);
            if (response.status != 201 && response.status != 204)
                throw new BridgeError("GitHub secret write failed (HTTP " + response.status + ") after " + written
                        + " entries. Do not start a release; keep the same signing key.");
            written++;
        }
        System.out.println("{\"repository\":\"" + REPO + "\",\"scope\":\"repository\",\"certificateSha256\":\""
                + CERTIFICATE + "\",\"connectedSecretCount\":4,\"connectedAt\":\"" + Instant.now()
                + "\",\"appPublished\":false}");
    }

    static Map<String,String> childEnvironment() {
        Map<String,String> env = new HashMap<>(System.getenv());
        env.keySet().removeIf(name -> name.toUpperCase(Locale.ROOT).startsWith("GIT_TRACE")
                || name.toUpperCase(Locale.ROOT).startsWith("GCM_TRACE")
                || Set.of("GIT_CURL_VERBOSE", "SSLKEYLOGFILE").contains(name.toUpperCase(Locale.ROOT)));
        env.put("GIT_TERMINAL_PROMPT", "0"); env.put("GCM_INTERACTIVE", "never");
        return env;
    }

    static byte[] process(List<String> command, byte[] input, Map<String,String> env) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear(); builder.environment().putAll(env);
        Process process = builder.start();
        CompletableFuture<byte[]> captured = new CompletableFuture<>();
        Thread reader = new Thread(() -> {
            try { captured.complete(process.getInputStream().readNBytes(1048577)); }
            catch (IOException failure) { captured.completeExceptionally(failure); }
        });
        reader.setDaemon(true); reader.start();
        try {
            try (OutputStream stream = process.getOutputStream()) { if (input != null) stream.write(input); }
            if (!process.waitFor(60, TimeUnit.SECONDS)) throw new BridgeError("Git/curl command timed out. Values omitted.");
            byte[] output = captured.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || output.length > 1048576)
                throw new BridgeError("Git/curl command failed or exceeded its bound. Raw details omitted.");
            return output;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            process.getInputStream().close();
        }
    }

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    static Api originalPcApi() throws Exception {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"))
            throw new BridgeError("Run on the original Windows PC. Cloud-proxy credentials are never read.");
        Map<String,String> env = childEnvironment();
        String helper = new String(process(List.of("git", "config", "--get-all", "credential.helper"), null, env), StandardCharsets.UTF_8);
        if (!helper.toLowerCase(Locale.ROOT).contains("manager"))
            throw new BridgeError("Existing Windows Git Credential Manager login is required.");
        String credentials = new String(process(List.of("git", "credential", "fill"),
                "protocol=https\nhost=github.com\nusername=wjdals988\n\n".getBytes(StandardCharsets.UTF_8), env), StandardCharsets.UTF_8);
        String token = credentials.lines().filter(line -> line.startsWith("password="))
                .map(line -> line.substring(9)).findFirst().orElse("");
        if (!token.matches("[A-Za-z0-9_.:/+=-]{1,4096}"))
            throw new BridgeError("Existing GitHub login is unavailable. Credential values omitted.");
        return (method, path, json) -> {
            if (!(method.equals("GET") && path.equals("/actions/secrets/public-key"))
                    && !(method.equals("PUT") && NAMES.stream().anyMatch(n -> path.equals("/actions/secrets/" + n))))
                throw new BridgeError("Unexpected GitHub operation. Nothing sent.");
            String config = "header = " + quote("Authorization: Bearer " + token) + "\n"
                    + "header = \"Accept: application/vnd.github+json\"\n"
                    + "header = \"X-GitHub-Api-Version: 2026-03-10\"\n";
            if (json != null) config += "header = \"Content-Type: application/json\"\ndata-binary = " + quote(json) + "\n";
            List<String> command = new ArrayList<>(List.of("curl.exe", "-q", "--silent", "--show-error", "--http1.1",
                    "--proto", "=https", "--connect-timeout", "15", "--max-time", "45", "--max-filesize", "1048576",
                    "--max-redirs", "0", "--request", method, "--config", "-", "--write-out", "\n" + MARKER + "%{http_code}", API + path));
            String output = new String(process(command, config.getBytes(StandardCharsets.UTF_8), env), StandardCharsets.UTF_8);
            int split = output.lastIndexOf(MARKER);
            if (split < 0 || !output.substring(split + MARKER.length()).matches("[1-5][0-9]{2}"))
                throw new BridgeError("GitHub HTTP status was not confirmed. Raw output omitted.");
            return new Response(Integer.parseInt(output.substring(split + MARKER.length())), output.substring(0, split));
        };
    }

    public static void main(String[] args) {
        try {
            if (args.length == 1 && args[0].equals("prepare")) {
                System.out.println(prepare(originalPcApi()));
            } else if (args.length == 2 && args[0].equals("apply")) {
                Path file = Path.of(args[1]);
                if (!Files.isRegularFile(file) || Files.size(file) > 262144)
                    throw new BridgeError("Encrypted payload file is missing or oversized.");
                Properties payload = new Properties();
                try (InputStream stream = Files.newInputStream(file)) { payload.load(stream); }
                apply(payload, originalPcApi());
            } else throw new BridgeError("Usage: java RunmoniGitHubSecretsBridge.java prepare | apply <encrypted-file>");
        } catch (Exception error) {
            System.err.println(error instanceof BridgeError ? error.getMessage() : "Bridge failed. Raw credential/API diagnostics omitted.");
            System.exit(1);
        }
    }
}
