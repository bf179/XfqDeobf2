package de.robv.android.xposed;

import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;

public class XposedBridge {

    public static class Unhook {
        private final Member member;
        public Unhook(Member member) { this.member = member; }
        public Member getHookedMethod() { return member; }
    }

    public static void log(String msg) {
        Log.i("Xposed", msg);
    }

    public static void log(Throwable t) {
        Log.e("Xposed", Log.getStackTraceString(t));
    }

    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook callback) {
        return new XC_MethodHook.Unhook(member);
    }

    public static void hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            hookMethod(ctor, callback);
        }
    }
}
