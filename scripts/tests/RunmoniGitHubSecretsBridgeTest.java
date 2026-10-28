/* Offline fake API tests. No GCM, tokens, signing files, network or secret writes. */
import java.io.*;
import java.util.*;

public class RunmoniGitHubSecretsBridgeTest {
    static int passed;
    static String key = Base64.getEncoder().encodeToString(new byte[32]);
    static String cipher = Base64.getEncoder().encodeToString(new byte[60]);
    static void check(boolean value) { if (!value) throw new AssertionError("Bridge boundary failed; values omitted."); }
    interface Operation { void run() throws Exception; }
    static void rejects(Operation operation) throws Exception {
        try { operation.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected rejection.");
    }
    static class Fake implements RunmoniGitHubSecretsBridge.Api {
        int keyStatus = 200, writeStatus = 201, failAt = -1, writes;
        String publicKey = key;
        List<String> bodies = new ArrayList<>();
        public RunmoniGitHubSecretsBridge.Response call(String method, String path, String json) {
            if (method.equals("GET"))
                return new RunmoniGitHubSecretsBridge.Response(keyStatus, "{\"key_id\":\"fixture-id\",\"key\":\"" + publicKey + "\"}");
            check(method.equals("PUT") && path.startsWith("/actions/secrets/RUNMONI_"));
            bodies.add(json);
            return new RunmoniGitHubSecretsBridge.Response(writes++ == failAt ? 403 : writeStatus, "{}");
        }
    }
    static Properties payload() {
        Properties values = new Properties();
        values.setProperty("format", RunmoniGitHubSecretsBridge.FORMAT);
        values.setProperty("repository", RunmoniGitHubSecretsBridge.REPO);
        values.setProperty("scope", "repository");
        values.setProperty("certificateSha256", RunmoniGitHubSecretsBridge.CERTIFICATE);
        values.setProperty("keyId", "fixture-id"); values.setProperty("publicKey", key);
        for (String name : RunmoniGitHubSecretsBridge.NAMES) values.setProperty(name, cipher);
        return values;
    }
    static void apply(Properties values, Fake server) throws Exception {
        PrintStream original = System.out;
        try (PrintStream sink = new PrintStream(OutputStream.nullOutputStream())) {
            System.setOut(sink); RunmoniGitHubSecretsBridge.apply(values, server);
        } finally { System.setOut(original); }
    }
    public static void main(String[] args) throws Exception {
        Fake server = new Fake();
        String packet = RunmoniGitHubSecretsBridge.prepare(server);
        check(packet.contains("\"scope\":\"repository\"") && packet.contains(key) && !packet.contains("password")); passed++;
        server = new Fake(); server.keyStatus = 403; Fake denied = server;
        rejects(() -> RunmoniGitHubSecretsBridge.prepare(denied)); check(server.writes == 0); passed++;
        Properties wrong = payload(); wrong.setProperty("repository", "other/repository");
        Properties wrongRepo = wrong;
        rejects(() -> apply(wrongRepo, new Fake())); passed++;
        wrong = payload(); wrong.setProperty("certificateSha256", "b".repeat(64)); Properties wrongCert = wrong;
        rejects(() -> apply(wrongCert, new Fake())); passed++;
        wrong = payload(); wrong.setProperty("unexpected", "field"); Properties extra = wrong;
        rejects(() -> apply(extra, new Fake())); passed++;
        server = new Fake(); server.publicKey = Base64.getEncoder().encodeToString(new byte[31]); Fake badKey = server;
        rejects(() -> apply(payload(), badKey)); check(server.writes == 0); passed++;
        server = new Fake(); server.publicKey = Base64.getEncoder().encodeToString(new byte[32]).replaceFirst("A", "B"); Fake changedKey = server;
        rejects(() -> apply(payload(), changedKey)); check(server.writes == 0); passed++;
        server = new Fake(); apply(payload(), server);
        check(server.writes == 4 && server.bodies.stream().allMatch(body -> body.equals("{\"encrypted_value\":\"" + cipher + "\",\"key_id\":\"fixture-id\"}"))); passed++;
        server = new Fake(); server.writeStatus = 204; apply(payload(), server); check(server.writes == 4); passed++;
        server = new Fake(); server.failAt = 1; Fake partial = server;
        rejects(() -> apply(payload(), partial)); check(server.writes == 2); passed++;
        wrong = payload(); wrong.setProperty("RUNMONI_KEYSTORE_BASE64", "not-base64!"); Properties malformed = wrong;
        rejects(() -> apply(malformed, new Fake())); passed++;
        check(RunmoniGitHubSecretsBridge.quote("a\"b\\c\n").equals("\"a\\\"b\\\\c\\n\"")); passed++;
        System.out.println("Offline encrypted-bridge checks passed: " + passed);
    }
}
