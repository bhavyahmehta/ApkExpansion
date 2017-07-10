package com.demo.apkexpansion;

/**
 * Created by bhavya on 07-07-2017.
 */

/**
 *  This file contains basic configuration change that need to do
 *  to make this apk expansion working
 */

public class Config {

    /**
     * Main expansion file name under zip obb
     */
    static final String EX_MAIN_FILE_NAME = "<enter name>";

    /**
     * Main expansion file extension under zip obb
     */
    static final String EX_MAIN_FILE_EXTN = "<enter extension>";

    /**
     * Main expansion file version
     */
    static final int EXPANSION_MAIN_VERSION = 0;// <enter your expansion file version>

    /**
     * Main expansion file size
     */
    static final int EXPANSION_MAIN_FILE_SIZE = 0;// <enter your expansion file file size in bytes>

     /**
     * Modify this as your app from GooglePlay
     * get it from Google play developer console under
     * App > Development tools > Services and APIs > Licensing & in-app billing
     */
    static final String BASE64_PUBLIC_KEY = "<enter base64 public key>";

    /**
     * Here is where you place the data that the validator will use to determine
     * if the file was delivered correctly. This is encoded in the source code
     * so the application can easily determine whether the file has been
     * properly delivered without having to talk to the server.
     */
    static final XAPKFile[] xAPKS = {
            new XAPKFile(
                    // true signifies a main file
                    true,
                    // the version of the APK that the file was uploaded against
                    EXPANSION_MAIN_VERSION,
                    // the length of the file in bytes
                    EXPANSION_MAIN_FILE_SIZE
            ),
    };
}
