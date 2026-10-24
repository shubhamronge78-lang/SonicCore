/* Minimal host stub of jni.h for the SonicCore DSP harness. */
#ifndef _STUB_JNI_H
#define _STUB_JNI_H
#include <stdint.h>
typedef int jint; typedef long long jlong; typedef short jshort;
typedef float jfloat; typedef unsigned char jboolean; typedef int jsize;
typedef void* jobject; typedef void* jarray; typedef void* jshortArray;
typedef void* jfloatArray;
#define JNIEXPORT
#define JNICALL
#define JNI_TRUE 1
#define JNI_FALSE 0
#define JNI_ABORT 2

/* Host-array registry: the harness registers real C arrays as fake Java
   arrays; the stub JNIEnv hands back pointers into them. */
struct HostArr { void* data; jsize len; int floatIs; };
extern HostArr g_hostArrays[512];
extern int g_hostArrCount;
inline int hostReg(void* data, jsize len, int floatIs) {
    int id = g_hostArrCount++;
    g_hostArrays[id] = { data, len, floatIs };
    return id;
}

struct JNIEnv {
    bool arrayLengthFailure = false;
    bool exceptionPending = false;
    int criticalAcquires = 0;
    int criticalReleases = 0;
    jsize GetArrayLength(jarray a) {
        if (arrayLengthFailure) { exceptionPending = true; return 0; }
        return ((HostArr*)a)->len;
    }
    jboolean ExceptionCheck() { return exceptionPending ? JNI_TRUE : JNI_FALSE; }
    void* GetPrimitiveArrayCritical(jarray a, jboolean*) {
        void* data = ((HostArr*)a)->data;
        if (data != nullptr) criticalAcquires++;
        return data;
    }
    void ReleasePrimitiveArrayCritical(jarray, void*, jint) { criticalReleases++; }
    jfloat* GetFloatArrayElements(jfloatArray a, jboolean*) { return (jfloat*)((HostArr*)a)->data; }
    void ReleaseFloatArrayElements(jfloatArray a, jfloat*, jint) {}
};
typedef JNIEnv* JNIEnvPtr;
#endif
