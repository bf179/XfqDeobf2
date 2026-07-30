package com.fanqie.xfqdeobf2

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.lang.reflect.Proxy

class XpEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "XfqDeobf2"
        private const val QQ_PACKAGE = "com.tencent.mobileqq"
        private const val PIC_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent"

        private var savedImagePath: String? = null
        private var hookContext: Context? = null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != QQ_PACKAGE) return

        val ctx = try {
            // Get application context via ActivityThread (available in LSPosed)
            val atClass = Class.forName("android.app.ActivityThread")
            val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
            atClass.getMethod("getApplication").invoke(currentAt) as? android.content.Context
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to get app context: $e")
            null
        } ?: return

        SettingsActivity.init(ctx)
        XposedBridge.log("$TAG: Loaded into QQ")

        try {
            hookPicComponent(lpparam.classLoader)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: hookPicComponent failed: $e")
        }
    }

    private fun hookPicComponent(classLoader: ClassLoader) {
        val picClass = try {
            XposedHelpers.findClass(PIC_COMPONENT, classLoader)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: AIOPicContentComponent not found, QQ version may not be supported")
            return
        }

        // Hook all constructors of AIOPicContentComponent
        XposedBridge.hookAllConstructors(picClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!SettingsActivity.enabled) return
                val component = param.thisObject
                findAndHookMenuMethod(component.javaClass, classLoader)
            }
        })

        XposedBridge.log("$TAG: AIOPicContentComponent hooked")
    }

    private fun findAndHookMenuMethod(componentClass: Class<*>, classLoader: ClassLoader) {
        // Look for a method that returns List and seems to build the menu
        for (method in componentClass.declaredMethods) {
            if (method.returnType == List::class.java && method.parameterTypes.isEmpty()) {
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!SettingsActivity.enabled) return
                            addDeobfuscateMenuItem(param, classLoader)
                        }
                    })
                    XposedBridge.log("$TAG: Hooked menu method: ${method.name} on ${componentClass.simpleName}")
                    break // Only hook the first matching method
                } catch (e: Throwable) {
                    // Try next method
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addDeobfuscateMenuItem(param: XC_MethodHook.MethodHookParam, classLoader: ClassLoader) {
        val menuList = param.result as? MutableList<Any> ?: return
        if (menuList.isEmpty()) return // No menu items yet (image not loaded)

        try {
            val menuItem = createMenuItem(classLoader, param)
            if (menuItem != null) {
                menuList.add(menuItem)
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to add menu item: $e")
        }
    }

    private fun createMenuItem(classLoader: ClassLoader, param: XC_MethodHook.MethodHookParam): Any? {
        // Try to get the image path from the component
        val component = param.thisObject
        val imagePath = tryGetImagePath(component)
        if (imagePath == null) {
            XposedBridge.log("$TAG: Cannot get image path")
            return null
        }
        savedImagePath = imagePath

        // Get context from the component (for Toast and UI)
        val ctx = tryGetContext(component) ?: return null
        hookContext = ctx

        // Try to create QQ menu item using reflection
        try {
            // Attempt 1: Use AbstractQQCustomMenuItem (NT QQ 9.0+)
            return createAbstractQQMenuItem(classLoader, ctx, imagePath)
        } catch (e1: Throwable) {
            XposedBridge.log("$TAG: AbstractQQCustomMenuItem failed, trying TextMenuItem: $e1")
        }

        try {
            // Attempt 2: Try simpler QQ menu item (TextMenuItem or similar)
            return createSimpleMenuItem(classLoader, ctx, imagePath)
        } catch (e2: Throwable) {
            XposedBridge.log("$TAG: All menu creation methods failed: $e2")
        }

        return null
    }

    private fun createAbstractQQMenuItem(classLoader: ClassLoader, ctx: Context, imagePath: String): Any? {
        val menuClass = XposedHelpers.findClass(
            "com.tencent.qqnt.aio.menu.ui.AbstractQQCustomMenuItem", classLoader
        )

        val handler = Handler(Looper.getMainLooper())

        // Create a Runnable for the click action
        val onClickRunnable = Runnable {
            performDeobfuscation(ctx, imagePath)
        }

        // Create a Proxy that implements the abstract methods
        return Proxy.newProxyInstance(classLoader, arrayOf<Class<*>>(menuClass), { _, method, args ->
            when (method.name) {
                "getTitle" -> "解混淆"
                "getIcon" -> tryGetMenuIcon(ctx)
                "getId" -> android.R.id.copy  // Use a standard ID
                "onClick" -> {
                    handler.post(onClickRunnable)
                    null
                }
                "toString" -> "QQCustomMenuItem{title='解混淆'}"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === args?.get(0)
                else -> {
                    // Return default values for other methods
                    when (method.returnType) {
                        Int::class.javaPrimitiveType -> 0
                        Boolean::class.javaPrimitiveType -> false
                        Long::class.javaPrimitiveType -> 0L
                        Float::class.javaPrimitiveType -> 0f
                        Double::class.javaPrimitiveType -> 0.0
                        else -> null
                    }
                }
            }
        })
    }

    private fun createSimpleMenuItem(classLoader: ClassLoader, ctx: Context, imagePath: String): Any? {
        // Fallback: try QQCustomMenuItem interface (non-abstract, simpler)
        val menuClass = try {
            XposedHelpers.findClass("com.tencent.qqnt.aio.menu.ui.QQCustomMenuItem", classLoader)
        } catch (e: Throwable) {
            // Try older class name
            XposedHelpers.findClass("com.tencent.mobileqq.aio.msglist.holder.component.pic.QQCustomMenuItem", classLoader)
        }

        val handler = Handler(Looper.getMainLooper())

        return Proxy.newProxyInstance(classLoader, arrayOf<Class<*>>(menuClass), { _, method, args ->
            when (method.name) {
                "getTitle" -> "解混淆"
                "onClick" -> {
                    handler.post { performDeobfuscation(ctx, imagePath) }
                    null
                }
                "toString" -> "解混淆"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === args?.get(0)
                else -> {
                    when (method.returnType) {
                        Int::class.javaPrimitiveType -> 0
                        Boolean::class.javaPrimitiveType -> false
                        Void.TYPE -> null
                        else -> null
                    }
                }
            }
        })
    }

    private fun tryGetImagePath(component: Any): String? {
        try {
            // Try to find a method that returns the local image path
            for (method in component.javaClass.declaredMethods) {
                if (method.returnType == String::class.java && method.parameterTypes.isEmpty()) {
                    method.isAccessible = true
                    val result = method.invoke(component) as? String
                    if (result != null && (result.endsWith(".jpg") || result.endsWith(".png") ||
                                result.endsWith(".jpeg") || result.endsWith(".bmp") ||
                                result.endsWith(".gif") || result.startsWith("/"))
                    ) {
                        return result
                    }
                }
            }
            // Try common field names
            for (field in component.javaClass.declaredFields) {
                if (field.type == String::class.java) {
                    field.isAccessible = true
                    val value = field.get(component) as? String
                    if (value != null && (value.endsWith(".jpg") || value.endsWith(".png") ||
                                value.startsWith("/data/") || value.startsWith("/storage/"))
                    ) {
                        return value
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }
        return null
    }

    private fun tryGetContext(component: Any): Context? {
        try {
            // Try to find a Context field or getContext method
            for (method in component.javaClass.declaredMethods) {
                if (method.returnType == Context::class.java && method.parameterTypes.isEmpty()) {
                    method.isAccessible = true
                    return method.invoke(component) as? Context
                }
            }
            for (field in component.javaClass.declaredFields) {
                if (Context::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    return field.get(component) as? Context
                }
            }
            // Fallback: use Application context via reflection
            try {
                val atClass = Class.forName("android.app.ActivityThread")
                val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
                return atClass.getMethod("getApplication").invoke(currentAt) as? Context
            } catch (e2: Throwable) {
                return null
            }
        } catch (e: Throwable) {
            return null
        }
    }

    private fun tryGetMenuIcon(ctx: Context): Int {
        try {
            return ctx.resources.getIdentifier("qui_tuning", "drawable", ctx.packageName)
        } catch (e: Throwable) {
            return 0
        }
    }

    // ==================== Deobfuscation ====================

    private fun performDeobfuscation(ctx: Context, imagePath: String) {
        if (!File(imagePath).exists()) {
            showToast(ctx, "请先查看原图后重试")
            return
        }

        showToast(ctx, "正在解混淆...")

        Thread {
            try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val src = BitmapFactory.decodeFile(imagePath, options)
                    ?: throw Exception("无法解码图片")

                val result = try {
                    GilbertCurve.deobfuscate(src, SettingsActivity.deobfKey)
                } finally {
                    src.recycle()
                }

                Handler(Looper.getMainLooper()).post {
                    saveResult(ctx, result)
                }
            } catch (e: OutOfMemoryError) {
                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, "图片过大，内存不足")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, "解混淆失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun saveResult(ctx: Context, result: GilbertCurve.DeobfResult) {
        Thread {
            try {
                val name = "fanqie_deobf_${System.currentTimeMillis()}.png"
                val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FanqieDeobf")
                    }
                    val uri = ctx.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: throw Exception("无法创建媒体文件")
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: throw Exception("无法打开输出流")
                    true
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "FanqieDeobf"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, name)
                    file.outputStream().use { out ->
                        result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null
                    )
                    true
                }
                result.bitmap.recycle()

                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, "已保存到 Pictures/FanqieDeobf")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, "保存失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun showToast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
