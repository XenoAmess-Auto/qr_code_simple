package com.github.sisong;

/**
 * ApkDiffPatch Android JNI wrapper (MIT, from github.com/sisong/ApkDiffPatch official release).
 * Native libs libapkpatch.so + libc++_shared.so are packaged in jniLibs for all four ABIs.
 */
public class ApkPatch {
    static {
        // The official ApkPatch.java has no loading logic; add it here (libc++_shared is
        // resolved automatically by the Android loader).
        System.loadLibrary("apkpatch");
    }

    // return 0 is ok; patchFilePath file created by ZipDiff
    public static native int patch(String oldApkPath, String patchFilePath, String outNewApkPath,
                                   long maxUncompressMemory, String tempUncompressFilePath, int threadNum);
}
