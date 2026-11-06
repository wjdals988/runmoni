package com.runmoni.review;
import com.android.uiautomator.core.Configurator;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import java.io.File;
/** Shell observer only. It never starts/stops Runmoni or changes its notification cadence. */
public final class LiveDump extends UiAutomatorTestCase {
    public void testDumpLiveHierarchy() throws Exception {
        Configurator.getInstance().setWaitForIdleTimeout(0);
        File target = new File("/data/local/tmp/runmoni-background-host.xml");
        if (target.exists()) assertTrue(target.delete());
        getUiDevice().dumpWindowHierarchy(target.getName());
        assertTrue("Fresh active-window hierarchy exists", target.isFile() && target.length() > 0);
        System.out.println("UI hierarchy dumped to: " + target.getAbsolutePath());
    }
}
