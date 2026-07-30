#include <jni.h>
#include <android/log.h>
#include <lsplant.hpp>

#define LOG_TAG "XfqDeobf2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool sLsplantReady = false;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Initialize LSPlant
    lsplant::InitInfo initInfo{
        .inline_hooker = [](auto t, auto r) { return lsplant::IsInitSuccess(lsplant::MakeDobbyInlineHooker(t, r)); },
        .inline_unhooker = [](auto t) { return lsplant::IsInitSuccess(lsplant::MakeDobbyInlineUnhooker(t)); },
        .art_symbol_resolver = [](auto symbol) { return lsplant::ElfSymbolResolver(symbol); },
        .art_symbol_prefix_resolver = [](auto symbol) { return lsplant::ElfSymbolResolver(symbol); },
        .hook_callback = [](auto, auto) { return nullptr; },
    };

    auto result = lsplant::Init(env, initInfo);
    if (!result.ok()) {
        LOGE("LSPlant init failed: %.*s", (int)result.error().message().size(), result.error().message().data());
        return JNI_OK; // Don't crash, continue without LSPlant
    }

    sLsplantReady = true;
    LOGI("LSPlant initialized successfully");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fanqie_xfqdeobf2_HookManager_nativeInit(JNIEnv*, jclass) {
    return sLsplantReady ? JNI_TRUE : JNI_FALSE;
}
