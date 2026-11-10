package com.runmoni.review;

import android.os.IBinder;
import java.lang.reflect.Method;

/** Only restores default policy(0) on the owned default display(0). Not an APK or a network tool. */
public final class RotationPolicy {
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("No arguments: only default display/default mode is allowed");
        int uid = android.os.Process.myUid();
        if (uid != 2000 && uid != 0) throw new SecurityException("Shell/root review process only");
        Class<?> manager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) manager.getDeclaredMethod("getService", String.class).invoke(null, "window");
        if (binder == null) throw new IllegalStateException("Window manager binder is unavailable");
        Class<?> stub = Class.forName("android.view.IWindowManager$Stub");
        Object service = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
        Class<?> api = Class.forName("android.view.IWindowManager");
        Method setter = api.getDeclaredMethod("setFixedToUserRotation", int.class, int.class);
        setter.invoke(service, 0, 0);
        System.out.println("{\"displayId\":0,\"requestedFixedToUserRotationMode\":0,\"binderInvocationSucceeded\":true}");
    }
}
