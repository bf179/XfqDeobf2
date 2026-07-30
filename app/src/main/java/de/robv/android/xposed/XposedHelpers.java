package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

public class XposedHelpers {

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        try {
            Object callback = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
            Class<?>[] paramTypes = new Class<?>[parameterTypesAndCallback.length - 1];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = (Class<?>) parameterTypesAndCallback[i];
            }
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            return XposedBridge.hookMethod(method, (XC_MethodHook) callback);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        Class<?> clazz = findClass(className, classLoader);
        if (clazz == null) throw new ClassNotFoundException(className).fillInStackTrace();
        return findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
    }
}
