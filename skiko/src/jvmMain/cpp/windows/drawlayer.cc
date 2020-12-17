#define WIN32_LEAN_AND_MEAN
#include <jawt_md.h>

extern "C" jboolean Skiko_GetAWT(JNIEnv *env, JAWT *awt);

extern "C"
{
    JNIEXPORT jobject JNICALL Java_org_jetbrains_skiko_HardwareLayer_createRedrawer(JNIEnv *env, jobject canvas, jobject layer)
    {
        static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("org/jetbrains/skiko/redrawer/WindowsRedrawer"));
        static jmethodID constructor = env->GetMethodID(cls, "<init>", "(Lorg/jetbrains/skiko/HardwareLayer;)V");
        return env->NewObject(cls, constructor, layer);
    }

    JNIEXPORT jlong JNICALL Java_org_jetbrains_skiko_HardwareLayer_getWindowHandle(JNIEnv *env, jobject canvas)
    {
        JAWT awt;
        awt.version = (jint)JAWT_VERSION_9;
        if (!Skiko_GetAWT(env, &awt))
        {
            fprintf(stderr, "JAWT_GetAWT failed! Result is JNI_FALSE\n");
            return -1;
        }

        JAWT_DrawingSurface *ds = awt.GetDrawingSurface(env, canvas);
        ds->Lock(ds);
        JAWT_DrawingSurfaceInfo *dsi = ds->GetDrawingSurfaceInfo(ds);
        JAWT_Win32DrawingSurfaceInfo *dsi_win = (JAWT_Win32DrawingSurfaceInfo *)dsi->platformInfo;

        HWND hwnd = dsi_win->hwnd;

        ds->FreeDrawingSurfaceInfo(dsi);
        ds->Unlock(ds);
        awt.FreeDrawingSurface(ds);

        return (jlong)hwnd;
    }
} // extern "C"