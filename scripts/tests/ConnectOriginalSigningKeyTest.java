/* Offline boundary tests using the existing PUBLIC Android debug fixture key.
 * No production key, new key generation, GitHub access, or real secret writes.
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class ConnectOriginalSigningKeyTest {
    static int passed;
    static void check(boolean value) { if (!value) throw new AssertionError("Boundary assertion failed; values omitted."); }
    interface Operation { void run() throws Exception; }
    static void fixtureConnect(ConnectOriginalSigningKey.Inputs inputs, Fake transport) throws Exception {
        PrintStream original = System.out;
        try (PrintStream sink = new PrintStream(OutputStream.nullOutputStream())) {
            System.setOut(sink);
            ConnectOriginalSigningKey.connect(inputs, transport);
        } finally { System.setOut(original); }
    }
    static void rejects(Operation operation) throws Exception {
        try { operation.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected rejection.");
    }
    static class Fake implements ConnectOriginalSigningKey.Transport {
        int status = 200, creation = 0, publicKey = 0, failWrite = -1, writes;
        List<List<String>> calls = new ArrayList<>();
        List<byte[]> inputs = new ArrayList<>();
        public ConnectOriginalSigningKey.Response call(List<String> arguments, byte[] input) {
            calls.add(List.copyOf(arguments));
            if (arguments.contains("--include"))
                return new ConnectOriginalSigningKey.Response(status == 200 ? 0 : 1, "HTTP/2.0 " + status + "\n{}");
            if (arguments.contains("PUT")) return new ConnectOriginalSigningKey.Response(creation, "{}");
            if (arguments.getLast().endsWith("public-key")) return new ConnectOriginalSigningKey.Response(publicKey, "{}");
            check(arguments.getFirst().equals("secret"));
            for (String argument : arguments) check(!argument.equals(new String(input, StandardCharsets.UTF_8)));
            inputs.add(input.clone());
            return new ConnectOriginalSigningKey.Response(writes++ == failWrite ? 1 : 0, "");
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--echo")) {
            byte[] input = System.in.readAllBytes();
            System.err.write(input); // The production process adapter must discard this.
            System.out.print("received:" + input.length);
            return;
        }
        Path key = Path.of(args[0]).toAbsolutePath(), root = Files.createTempDirectory("runmoni-key-fixture-");
        try {
            Path secrets = Files.createDirectory(root.resolve(".secrets"));
            byte[] original = Files.readAllBytes(key);
            Files.copy(key, secrets.resolve("fixture.keystore"));
            KeyStore store = KeyStore.getInstance(key.toFile(), "android".toCharArray());
            String certificate = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(store.getCertificate("androiddebugkey").getEncoded()));
            Properties properties = new Properties();
            properties.setProperty("storeFile", ".secrets/fixture.keystore");
            properties.setProperty("storePassword", "android"); properties.setProperty("keyPassword", "android");
            properties.setProperty("keyAlias", "androiddebugkey");
            Path settings = secrets.resolve("signing.properties");
            try (OutputStream stream = Files.newOutputStream(settings)) { properties.store(stream, "public fixture only"); }
            byte[] originalSettings = Files.readAllBytes(settings);
            rejects(() -> ConnectOriginalSigningKey.load(root, ConnectOriginalSigningKey.CERTIFICATE)); passed++;
            var valid = ConnectOriginalSigningKey.load(root, certificate);
            check(valid.values.size() == 4);
            check(Arrays.equals(original, Base64.getDecoder().decode(valid.values.get("RUNMONI_KEYSTORE_BASE64")))); passed++;
            Fake happy = new Fake(); fixtureConnect(valid, happy);
            check(happy.writes == 4 && happy.calls.stream().noneMatch(c -> c.contains("PUT"))); passed++;
            Fake denied = new Fake(); denied.status = 403;
            rejects(() -> fixtureConnect(valid, denied)); check(denied.writes == 0); passed++;
            Fake create = new Fake(); create.status = 404;
            fixtureConnect(valid, create);
            check(create.writes == 4 && create.calls.stream().filter(c -> c.contains("PUT")).count() == 1); passed++;
            Fake denyCreate = new Fake(); denyCreate.status = 404; denyCreate.creation = 1;
            rejects(() -> fixtureConnect(valid, denyCreate)); check(denyCreate.writes == 0); passed++;
            Fake denyPublicKey = new Fake(); denyPublicKey.publicKey = 1;
            rejects(() -> fixtureConnect(valid, denyPublicKey)); check(denyPublicKey.writes == 0); passed++;
            Fake partial = new Fake(); partial.failWrite = 2;
            rejects(() -> fixtureConnect(valid, partial)); check(partial.writes == 3); passed++;
            check(Arrays.equals(original, Files.readAllBytes(key)) && Arrays.equals(originalSettings, Files.readAllBytes(settings))); passed++;
            valid.clear(); check(valid.values.values().stream().allMatch(b -> Arrays.equals(b, new byte[b.length]))); passed++;
            properties.setProperty("keyPassword", "wrong-public-fixture-password");
            try (OutputStream stream = Files.newOutputStream(settings)) { properties.store(stream, "public fixture only"); }
            rejects(() -> ConnectOriginalSigningKey.load(root, certificate)); passed++;
            var env = ConnectOriginalSigningKey.childEnvironment();
            check(env.keySet().stream().noneMatch(n -> n.startsWith("GIT_TRACE") || n.startsWith("GCM_TRACE") || n.equals("GH_DEBUG")));
            check(env.get("GIT_TERMINAL_PROMPT").equals("0"));
            String java = Path.of(System.getProperty("java.home"), "bin/java").toString();
            var response = ConnectOriginalSigningKey.process(List.of(java, "-cp", System.getProperty("java.class.path"),
                    "ConnectOriginalSigningKeyTest", "--echo"), "private-fixture-body".getBytes(StandardCharsets.UTF_8), env);
            check(response.exit() == 0 && response.output().equals("received:20")); passed++;
            System.out.println("Offline signing-connection boundary checks passed: " + passed);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
