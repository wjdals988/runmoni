package com.runmoni.review;
import android.os.SystemClock;
import com.android.uiautomator.core.Configurator;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import java.io.File;
/** Shell observer only. No Runmoni lifecycle or notification cadence changes. */
public final class LiveDump extends UiAutomatorTestCase {
    public void testDumpLiveHierarchy() throws Exception {
        Configurator.getInstance().setWaitForIdleTimeout(0);
        File target = new File("/data/local/tmp/runmoni-background-host.xml");
        if (target.exists()) assertTrue(target.delete());
        long deadline = SystemClock.uptimeMillis() + 10000L;
        do {
            // API29 resolves this basename under /data/local/tmp; an absolute name would nest that path.
            getUiDevice().dumpWindowHierarchy(target.getName());
            if (target.isFile() && target.length() > 0) break;
            // A missing/empty active root can be retried without requiring an idle interval.
            if (SystemClock.uptimeMillis() >= deadline) break;
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Fresh active-window hierarchy exists", target.isFile() && target.length() > 0);
        System.out.println("UI hierarchy dumped to: " + target.getAbsolutePath());
    }
}
