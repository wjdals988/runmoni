package com.runmoni.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.system.Os;
import android.util.AtomicFile;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Standalone framework-only test-APK target for an actual system-chooser handoff.
 * The Android test APK deduplicates Kotlin/target dependencies and launches this
 * component in its own UID, so this class deliberately has no Kotlin, AndroidX,
 * or target-app references. Private receipts must be redacted before publication.
 */
public final class RunShareReceiverActivity extends Activity {
    private static final String DIRECTORY = "run-share-receiver";
    private static final String TARGET_PACKAGE = "com.runmoni.app";
    private static final String TEST_PACKAGE = "com.runmoni.app.test";
    private static final String PROVIDER = "com.runmoni.app.files";
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final Pattern BASE_NAME = Pattern.compile(
        "received-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern FILE_NAME = Pattern.compile(
        "runmoni-card-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");

    private volatile boolean alive = true;
    private Thread worker;
    private String receiptBase = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        show("공유 이미지를 확인하고 있어요", "runmoni-share-receiver-loading");
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0
                || !TEST_PACKAGE.equals(getPackageName())
                || getApplicationInfo().uid != Process.myUid()) {
            show("공유 검토 실패", "runmoni-share-receiver-failed");
            return;
        }
        String restored = savedInstanceState == null ? null : savedInstanceState.getString("receiptBase");
        receiptBase = restored != null && BASE_NAME.matcher(restored).matches()
            ? restored : "received-" + UUID.randomUUID();
        // A configuration recreation can display this Activity's existing result;
        // it does not assert a second delivery or replace a previous receipt.
        File existing = new File(new File(getCacheDir(), DIRECTORY), receiptBase + ".json");
        if (existing.isFile()) {
            boolean passed = false;
            try (InputStream input = new FileInputStream(existing)) {
                rejectUnless(existing.length() >= 1 && existing.length() <= 65_536, "BAD_PRIVATE_RECEIPT_SIZE");
                JSONObject previous = new JSONObject(new String(readBounded(input, 65_536), StandardCharsets.UTF_8));
                Uri incoming = stream(getIntent());
                passed = previous.getInt("schemaVersion") == 1
                    && receiptBase.equals(previous.getString("receiptBase"))
                    && previous.getBoolean("passed") && incoming != null
                    && incoming.toString().equals(previous.getString("sourceUri"));
            } catch (Throwable ignored) {
                // Never log private receipt contents or malformed parcelables.
            }
            show(passed ? "공유 검토 완료" : "공유 검토 실패",
                passed ? "runmoni-share-receiver-complete" : "runmoni-share-receiver-failed");
            return;
        }
        final Intent request = getIntent();
        worker = new Thread(new Runnable() {
            @Override public void run() { receive(request); }
        }, "run-share-test-receiver");
        worker.start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("receiptBase", receiptBase);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    private void receive(Intent request) {
        File folder = new File(getCacheDir(), DIRECTORY);
        File png = new File(folder, receiptBase + ".png");
        File part = new File(folder, receiptBase + ".part");
        File receipt = new File(folder, receiptBase + ".json");
        JSONObject data = new JSONObject();
        boolean completed = false;
        boolean partOwned = false;
        boolean pngOwned = false;
        try {
            data.put("schemaVersion", 1).put("receiptBase", receiptBase)
                .put("sourceKind", "actual chooser handoff to a separate-UID test-only receiver")
                .put("receivedAtMillis", System.currentTimeMillis()).put("passed", false)
                .put("receiverPackage", getPackageName()).put("receiverLinuxUid", Process.myUid())
                .put("socialAppDeliveryVerified", false).put("networkRequestedByReceiver", false);
            rejectUnless((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0, "RECEIVER_NOT_DEBUG");
            rejectUnless(TEST_PACKAGE.equals(getPackageName()) && getApplicationInfo().uid == Process.myUid(),
                "WRONG_RECEIVER_PROCESS");
            rejectUnless(request != null && Intent.ACTION_SEND.equals(request.getAction()), "BAD_ACTION");
            rejectUnless("image/png".equals(request.getType()), "BAD_MIME");
            rejectUnless(request.getData() == null, "UNEXPECTED_DATA_URI");
            Uri uri = stream(request);
            rejectUnless(uri != null, "MISSING_STREAM");
            // URI appears only in this test APK's private receipt.
            data.put("sourceUri", uri.toString());
            rejectUnless(canonical(uri), "NON_CANONICAL_STREAM");
            ClipData clip = request.getClipData();
            rejectUnless(clip != null && clip.getItemCount() == 1
                && uri.equals(clip.getItemAt(0).getUri())
                && clip.getItemAt(0).getIntent() == null
                && clip.getItemAt(0).getText() == null
                && clip.getItemAt(0).getHtmlText() == null, "CLIP_STREAM_MISMATCH");
            ProviderInfo provider = getPackageManager().resolveContentProvider(PROVIDER, 0);
            rejectUnless(provider != null, "PROVIDER_MISSING");
            rejectUnless(TARGET_PACKAGE.equals(provider.packageName) && !provider.exported
                && provider.grantUriPermissions && provider.applicationInfo != null, "WRONG_PROVIDER");
            rejectUnless((provider.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0, "TARGET_NOT_DEBUG");
            int ownUid = Process.myUid();
            int targetUid = provider.applicationInfo.uid;
            rejectUnless(ownUid != targetUid, "UID_NOT_SEPARATE");
            data.put("targetLinuxUid", targetUid).put("differentLinuxUid", true);
            int read = checkUriPermission(uri, Process.myPid(), ownUid, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            int write = checkUriPermission(uri, Process.myPid(), ownUid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            rejectUnless(read == PackageManager.PERMISSION_GRANTED, "READ_NOT_GRANTED");
            rejectUnless(write == PackageManager.PERMISSION_DENIED, "WRITE_WAS_GRANTED");
            rejectUnless((request.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                && (request.getFlags() & (Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) == 0,
                "UNEXPECTED_GRANT_FLAGS");
            Uri sibling = uri.buildUpon().path("/exports/run-shares/runmoni-card-" + UUID.randomUUID() + ".png").build();
            rejectUnless(checkUriPermission(sibling, Process.myPid(), ownUid, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                == PackageManager.PERMISSION_DENIED, "SIBLING_READ_WAS_GRANTED");
            boolean siblingDeniedByOpen = false;
            try (InputStream ignored = getContentResolver().openInputStream(sibling)) {
                // An absent file is not proof of a denied grant: require SecurityException.
            } catch (SecurityException denied) {
                siblingDeniedByOpen = true;
            }
            rejectUnless(siblingDeniedByOpen, "SIBLING_OPEN_NOT_DENIED");
            data.put("clipDataMatchesSingleStream", true).put("readPermissionGranted", true)
                .put("writePermissionDenied", true).put("siblingReadPermissionDenied", true)
                .put("siblingOpenDeniedBySecurityException", true).put("prefixAndPersistableGrantAbsent", true);
            active();
            byte[] bytes;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                rejectUnless(input != null, "STREAM_UNAVAILABLE");
                bytes = readBounded(input, MAX_BYTES);
            }
            rejectUnless(validPngSignature(bytes), "BAD_PNG_SIGNATURE");
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            rejectUnless("image/png".equals(bounds.outMimeType) && bounds.outWidth == 1080
                && (bounds.outHeight == 1080 || bounds.outHeight == 1350 || bounds.outHeight == 1920), "BAD_PNG_DIMENSIONS");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            rejectUnless(decoded != null, "PNG_DECODE_FAILED");
            try {
                rejectUnless(decoded.getWidth() == bounds.outWidth && decoded.getHeight() == bounds.outHeight,
                    "PNG_DECODE_DIMENSIONS");
            } finally {
                decoded.recycle();
            }
            active();
            rejectUnless(folder.isDirectory() || folder.mkdirs(), "PRIVATE_CACHE_UNAVAILABLE");
            rejectUnless(!png.exists() && !part.exists() && !receipt.exists(), "OWN_OUTPUT_ALREADY_EXISTS");
            rejectUnless(part.createNewFile(), "PRIVATE_OUTPUT_UNAVAILABLE");
            partOwned = true;
            Os.chmod(part.getAbsolutePath(), 0600);
            try (FileOutputStream output = new FileOutputStream(part)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            active();
            rejectUnless(part.renameTo(png), "PRIVATE_OUTPUT_RENAME_FAILED");
            partOwned = false;
            pngOwned = true;
            data.put("width", bounds.outWidth).put("height", bounds.outHeight).put("sizeBytes", bytes.length)
                .put("sha256", sha256(bytes)).put("pngFile", png.getName())
                .put("sourceUriCanonical", true).put("actualReadSucceeded", true)
                .put("completedAtMillis", System.currentTimeMillis()).put("passed", true);
            writeReceipt(receipt, data);
            completed = true;
            postResult(true);
        } catch (Throwable failure) {
            // Never log exception text, Intent.toString(), account data or source URI.
            if (partOwned) part.delete();
            if (pngOwned && !completed) png.delete();
            try {
                data.put("passed", false)
                    .put("failureCode", failure instanceof Rejected ? ((Rejected) failure).code : "RECEIVER_OPERATION_FAILED")
                    .put("failureType", failure.getClass().getSimpleName())
                    .put("completedAtMillis", System.currentTimeMillis());
                if ((folder.isDirectory() || folder.mkdirs()) && !receipt.exists()) writeReceipt(receipt, data);
            } catch (Throwable ignored) {
                // No public logs, broad storage grants or replacement of existing receipts.
            }
            postResult(false);
        }
    }

    private byte[] readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int size = 0;
        while (true) {
            active();
            int count = input.read(buffer);
            if (count < 0) break;
            size += count;
            rejectUnless(size <= limit, "PNG_TOO_LARGE");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void writeReceipt(File file, JSONObject value) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = atomic.startWrite();
        try {
            Os.fchmod(output.getFD(), 0600);
            output.write((value.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
            atomic.finishWrite(output);
        } catch (Exception failure) {
            atomic.failWrite(output);
            throw failure;
        } catch (Error failure) {
            atomic.failWrite(output);
            throw failure;
        }
    }

    private void postResult(final boolean passed) {
        if (!alive) return;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (alive) show(passed ? "공유 검토 완료" : "공유 검토 실패",
                    passed ? "runmoni-share-receiver-complete" : "runmoni-share-receiver-failed");
            }
        });
    }

    private void show(String text, String description) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setContentDescription(description);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(22f);
        view.setPadding(32, 32, 32, 32);
        setContentView(view);
    }

    private void active() {
        if (!alive || Thread.currentThread().isInterrupted()) throw new Rejected("ACTIVITY_CANCELLED");
    }

    private static void rejectUnless(boolean value, String code) {
        if (!value) throw new Rejected(code);
    }

    @SuppressWarnings("deprecation")
    private static Uri stream(Intent source) {
        if (source == null) return null;
        Parcelable parcelable = source.getParcelableExtra(Intent.EXTRA_STREAM);
        return parcelable instanceof Uri ? (Uri) parcelable : null;
    }

    private static boolean canonical(Uri uri) {
        List<String> segments = uri.getPathSegments();
        String path = uri.getPath();
        return "content".equals(uri.getScheme()) && PROVIDER.equals(uri.getAuthority())
            && uri.getQuery() == null && uri.getFragment() == null && path != null
            && path.equals(uri.getEncodedPath()) && segments.size() == 3
            && "exports".equals(segments.get(0)) && "run-shares".equals(segments.get(1))
            && FILE_NAME.matcher(segments.get(2)).matches();
    }

    private static boolean validPngSignature(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) return false;
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) return false;
        }
        return true;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        char[] hex = "0123456789abcdef".toCharArray();
        char[] result = new char[digest.length * 2];
        for (int index = 0; index < digest.length; index++) {
            int value = digest[index] & 0xff;
            result[index * 2] = hex[value >>> 4];
            result[index * 2 + 1] = hex[value & 0x0f];
        }
        return new String(result);
    }

    private static final class Rejected extends RuntimeException {
        final String code;
        Rejected(String code) { this.code = code; }
    }
}
