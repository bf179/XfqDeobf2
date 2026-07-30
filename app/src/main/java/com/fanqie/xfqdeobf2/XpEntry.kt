package com.fanqie.xfqdeobf2

import android.app.Activity
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
import java.io.FileOutputStream
import java.lang.reflect.Proxy

class XpEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "XfqDeobf2"
        private const val QQ_PACKAGE = "com.tencent.mobileqq"
        private const val PIC_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent"
        private const val BASE_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent"
    }

    // ---- Xposed Entry ----

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != QQ_PACKAGE) return

        XposedBridge.log("$TAG: Loaded into QQ process=${lpparam.processName}")

        try {
            SettingsActivity.initAppContext(lpparam)
            XposedBridge.log("$TAG: SettingsActivity context initialized")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to init settings context: $e")
        }

        try {
            hookPicComponent(lpparam.classLoader)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: hookPicComponent exception: $e")
        }

        try {
            hookBaseComponent(lpparam.classLoader)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: hookBaseComponent exception: $e")
        }
    }

    // ---- Hook AIOPicContentComponent ----

    private fun hookPicComponent(classLoader: ClassLoader) {
        val picClass: Class<*>
        try {
            picClass = XposedHelpers.findClass(PIC_COMPONENT, classLoader) ?: run {
                XposedBridge.log("$TAG: $PIC_COMPONENT not found")
                return
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $PIC_COMPONENT not found: $e")
            return
        }

        XposedBridge.log("$TAG: Found $PIC_COMPONENT, setting up menu hooks")

        // Walk class hierarchy to find menu-building methods
        var hookedCount = 0
        var clazz: Class<*>? = picClass
        while (clazz != null && clazz != Any::class.java) {
            for (method in clazz.declaredMethods) {
                try {
                    method.isAccessible = true
                    val ret = method.returnType

                    // Match methods that return List or Array (menu item lists)
                    if (java.util.List::class.java.isAssignableFrom(ret) || ret.isArray) {
                        XposedBridge.log("$TAG: Hook menu candidate ${clazz.simpleName}.${method.name}() -> ${ret.simpleName}")
                        XposedBridge.hookMethod(method, createMenuItemListHook())
                        hookedCount++
                    }
                } catch (t: Throwable) {
                    // skip methods that can't be hooked
                }
            }
            clazz = clazz.superclass
        }

        XposedBridge.log("$TAG: Hooked $hookedCount menu methods across $PIC_COMPONENT hierarchy")
    }

    // ---- Hook BaseContentComponent as fallback ----

    private fun hookBaseComponent(classLoader: ClassLoader) {
        val baseClass: Class<*>
        try {
            baseClass = XposedHelpers.findClass(BASE_COMPONENT, classLoader) ?: run {
                XposedBridge.log("$TAG: $BASE_COMPONENT not found (non-critical)")
                return
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $BASE_COMPONENT not found: $e")
            return
        }

        XposedBridge.log("$TAG: Found $BASE_COMPONENT, setting up fallback menu hooks")

        var hookedCount = 0
        var clazz: Class<*>? = baseClass
        while (clazz != null && clazz != Any::class.java) {
            for (method in clazz.declaredMethods) {
                try {
                    method.isAccessible = true
                    val ret = method.returnType
                    if (java.util.List::class.java.isAssignableFrom(ret) || ret.isArray) {
                        XposedBridge.log("$TAG: Hook base menu candidate ${clazz.simpleName}.${method.name}() -> ${ret.simpleName}")
                        XposedBridge.hookMethod(method, createMenuItemListHook())
                        hookedCount++
                    }
                } catch (t: Throwable) {
                }
            }
            clazz = clazz.superclass
        }

        XposedBridge.log("$TAG: Hooked $hookedCount fallback menu methods")
    }

    // ---- Menu List Hook ----

    private fun createMenuItemListHook(): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!SettingsActivity.enabled) return

                try {
                    val result = param.result
                    // Handle both List and Array returns
                    if (result is MutableList<*>) {
                        addMenuItemToList(param, result as MutableList<Any>)
                    } else if (result != null && result.javaClass.isArray) {
                        addMenuItemToArray(param, result)
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Error in menu hook: $e")
                }
            }
        }
    }

    private fun addMenuItemToList(param: XC_MethodHook.MethodHookParam, list: MutableList<Any>) {
        if (list.isEmpty()) return

        val ctx = getContext() ?: return
        val imagePath = getImagePathFromComponent(param.thisObject) ?: return
        val menuItem = createQQMenuItem(ctx, imagePath) ?: return

        list.add(menuItem)
        XposedBridge.log("$TAG: Added deobfuscate menu item")
    }

    private fun addMenuItemToArray(param: XC_MethodHook.MethodHookParam, arr: Any) {
        val len = java.lang.reflect.Array.getLength(arr)
        if (len == 0) return

        val ctx = getContext() ?: return
        val imagePath = getImagePathFromComponent(param.thisObject) ?: return
        val menuItem = createQQMenuItem(ctx, imagePath) ?: return

        val componentType = arr.javaClass.componentType
        val newArr = java.lang.reflect.Array.newInstance(componentType, len + 1)
        System.arraycopy(arr, 0, newArr, 0, len)
        java.lang.reflect.Array.set(newArr, len, menuItem)
        param.result = newArr
        XposedBridge.log("$TAG: Added deobfuscate menu item to array")
    }

    // ---- QQ Menu Item Creation ----

    private fun createQQMenuItem(ctx: Context, imagePath: String): Any? {
        val classLoader = ctx.classLoader

        // Try AbstractQQCustomMenuItem (NT QQ)
        try {
            val menuItemClass = XposedHelpers.findClass(
                "com.tencent.qqnt.aio.menu.ui.AbstractQQCustomMenuItem", classLoader
            )
            if (menuItemClass != null) {
                val handler = Handler(Looper.getMainLooper())
                return Proxy.newProxyInstance(classLoader, arrayOf<Class<*>>(menuItemClass)) { _, method, args ->
                    when (method.name) {
                        "getTitle" -> "解混淆"
                        "onClick" -> {
                            handler.post { performDeobfuscation(ctx, imagePath) }
                            null
                        }
                        "toString" -> "QQCustomMenuItem{解混淆}"
                        "hashCode" -> System.identityHashCode(this)
                        "equals" -> this === args?.get(0)
                        else -> when (method.returnType) {
                            Int::class.javaPrimitiveType -> 0
                            Boolean::class.javaPrimitiveType -> false
                            Void.TYPE -> null
                            else -> null
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: AbstractQQCustomMenuItem proxy failed: $e")
        }

        // Try QQCustomMenuItem interface
        try {
            val menuItemClass = XposedHelpers.findClass(
                "com.tencent.qqnt.aio.menu.ui.QQCustomMenuItem", classLoader
            )
            if (menuItemClass != null) {
                val handler = Handler(Looper.getMainLooper())
                return Proxy.newProxyInstance(classLoader, arrayOf<Class<*>>(menuItemClass)) { _, method, args ->
                    when (method.name) {
                        "getTitle" -> "解混淆"
                        "onClick" -> {
                            handler.post { performDeobfuscation(ctx, imagePath) }
                            null
                        }
                        "toString" -> "解混淆"
                        "hashCode" -> System.identityHashCode(this)
                        "equals" -> this === args?.get(0)
                        else -> when (method.returnType) {
                            Int::class.javaPrimitiveType -> 0
                            Boolean::class.javaPrimitiveType -> false
                            Void.TYPE -> null
                            else -> null
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: QQCustomMenuItem proxy failed: $e")
        }

        return null
    }

    // ---- Utility: get image path ----

    private fun getImagePathFromComponent(component: Any): String? {
        try {
            val aiomsg = getFieldValue(component, "aiomsg") ?: getFieldValue(component, "msg")
            if (aiomsg != null) {
                val path = callMethod(aiomsg, "getLocalPath") as? String
                if (path != null && File(path).exists()) return path
            }
        } catch (e: Throwable) {
        }

        // Fallback: scan fields for a valid file path
        return tryGetImagePathFromFields(component)
    }

    private fun tryGetImagePathFromFields(obj: Any): String? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                field.isAccessible = true
                try {
                    val value = field.get(obj)
                    if (value is String) {
                        val f = File(value)
                        if (f.exists() && f.isFile && f.length() > 0) {
                            val name = f.name.lowercase()
                            if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".bmp") ||
                                name.endsWith(".gif") || name.endsWith(".webp")
                            ) {
                                return value
                            }
                        }
                    }
                } catch (e: Throwable) {
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    // ---- Utility: get Context ----

    private fun getContext(): Context? {
        return try {
            val activity = getCurrentActivity()
            activity ?: run {
                val atClass = Class.forName("android.app.ActivityThread")
                val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
                atClass.getMethod("getApplication").invoke(currentAt) as? Context
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun getCurrentActivity(): Activity? {
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField = atClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            val activities = activitiesField.get(currentAt) as? Map<*, *> ?: return null
            for (record in activities.values) {
                val recordClass = record?.javaClass ?: continue
                val pausedField = try {
                    recordClass.getDeclaredField("paused")
                } catch (e: NoSuchFieldException) {
                    continue
                }
                pausedField.isAccessible = true
                val paused = pausedField.getBoolean(record)
                if (!paused) {
                    val activityField = recordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    return activityField.get(record) as? Activity
                }
            }
            null
        } catch (e: Throwable) {
            null
        }
    }

    // ---- Reflection Helpers ----

    private fun getFieldValue(obj: Any, fieldName: String): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(obj)
            } catch (e: NoSuchFieldException) {
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun callMethod(obj: Any, methodName: String, vararg args: Any): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (m in clazz.declaredMethods) {
                if (m.name == methodName && m.parameterTypes.size == args.size) {
                    m.isAccessible = true
                    return try {
                        m.invoke(obj, *args)
                    } catch (e: Throwable) {
                        null
                    }
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    // ---- Deobfuscation Logic ----

    private fun performDeobfuscation(ctx: Context, imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) {
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

                val w = src.width
                val h = src.height
                val n = w * h
                if (n <= 0) throw Exception("图片尺寸异常")
                if (n > 8_000_000) throw Exception("图片过大 ($w×$h), 请使用更小尺寸的图片")

                val pixels = IntArray(n)
                src.getPixels(pixels, 0, w, 0, 0, w, h)
                src.recycle()

                val curve = gilbertCurve(w, h)
                val key = SettingsActivity.deobfKey
                val offset = Math.round(key * n).toInt()
                val out = IntArray(n)
                for (i in 0 until n) {
                    out[curve[i]] = pixels[curve[(i + offset) % n]]
                }

                val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                result.setPixels(out, 0, w, 0, 0, w, h)

                Handler(Looper.getMainLooper()).post {
                    saveResult(ctx, result)
                }
            } catch (oom: OutOfMemoryError) {
                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, "图片过大, 内存不足")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, "解混淆失败: ${e.message}")
                }
                XposedBridge.log("$TAG: Deobfuscation failed: $e")
            }
        }.start()
    }

    // ---- Gilbert Curve ----

    private fun gilbertCurve(w: Int, h: Int): IntArray {
        val curve = IntArray(w * h)
        val idx = intArrayOf(0)
        if (w >= h) {
            gilbertGen(0, 0, w, 0, 0, h, w, curve, idx)
        } else {
            gilbertGen(0, 0, 0, h, w, 0, w, curve, idx)
        }
        return curve
    }

    private fun gilbertGen(
        x: Int, y: Int, ax: Int, ay: Int, bx: Int, by: Int,
        imgW: Int, curve: IntArray, idx: IntArray
    ) {
        val w = Math.abs(ax + ay)
        val h = Math.abs(bx + by)
        val dax = if (ax > 0) 1 else if (ax < 0) -1 else 0
        val day = if (ay > 0) 1 else if (ay < 0) -1 else 0
        val dbx = if (bx > 0) 1 else if (bx < 0) -1 else 0
        val dby = if (by > 0) 1 else if (by < 0) -1 else 0

        if (h == 1) {
            var xx = x; var yy = y
            repeat(w) {
                curve[idx[0]++] = xx + yy * imgW
                xx += dax; yy += day
            }
            return
        }
        if (w == 1) {
            var xx = x; var yy = y
            repeat(h) {
                curve[idx[0]++] = xx + yy * imgW
                xx += dbx; yy += dby
            }
            return
        }
        var ax2 = Math.floorDiv(ax, 2)
        var ay2 = Math.floorDiv(ay, 2)
        var bx2 = Math.floorDiv(bx, 2)
        var by2 = Math.floorDiv(by, 2)
        val w2 = Math.abs(ax2 + ay2)
        val h2 = Math.abs(bx2 + by2)
        if (2 * w > 3 * h) {
            if (w2 % 2 != 0 && w > 2) { ax2 += dax; ay2 += day }
            gilbertGen(x, y, ax2, ay2, bx, by, imgW, curve, idx)
            gilbertGen(x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by, imgW, curve, idx)
        } else {
            if (h2 % 2 != 0 && h > 2) { bx2 += dbx; by2 += dby }
            gilbertGen(x, y, bx2, by2, ax2, ay2, imgW, curve, idx)
            gilbertGen(x + bx2, y + by2, ax, ay, bx - bx2, by - by2, imgW, curve, idx)
            gilbertGen(
                x + (ax - dax) + (bx2 - dbx),
                y + (ay - day) + (by2 - dby),
                -bx2, -by2, -(ax - ax2), -(ay - ay2),
                imgW, curve, idx
            )
        }
    }

    // ---- Save Result ----

    private fun saveResult(ctx: Context, bitmap: Bitmap) {
        Thread {
            try {
                val name = "fanqie_deobf_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FanqieDeobf")
                    }
                    val uri = ctx.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: throw Exception("无法创建媒体文件")
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: throw Exception("无法打开输出流")
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "FanqieDeobf"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, name)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null
                    )
                }
                bitmap.recycle()
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
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
