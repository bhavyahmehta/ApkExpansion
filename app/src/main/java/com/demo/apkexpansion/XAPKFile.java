package com.demo.apkexpansion;

/**
 * Created by bhavya.mehta on 07-07-2017.
 */

/**
 * This is a little helper class that demonstrates simple testing of an
 * Expansion APK file delivered by Market.
 */

public class XAPKFile {
        final boolean mIsMain;
        final int mFileVersion;
        final long mFileSize;

        XAPKFile(boolean isMain, int fileVersion, long fileSize) {
            mIsMain = isMain;
            mFileVersion = fileVersion;
            mFileSize = fileSize;
        }
}