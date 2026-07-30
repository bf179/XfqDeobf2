package de.robv.android.xposed;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;

public class XposedBridge {

    public static class Unhook {
        private final Member member;
        public Unhook(Member member) { this.member = member; }
        public Member getHookedMethod() { return member; }
    }

    public static void log(String msg) {
        System.out.println("[Xposed] " + msg);
    }

    public static void log(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        System.out.println("[Xposed] " + sw.toString());
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
