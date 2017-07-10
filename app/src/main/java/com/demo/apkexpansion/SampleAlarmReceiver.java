package com.demo.apkexpansion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;

/**
 * Created by bhavya.mehta on 05-07-2017.
 */


/**
 * You should start your derived downloader class when this receiver gets the message
 * from the alarm service using the provided service helper function within the
 * DownloaderClientMarshaller.
 */
public class SampleAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            DownloaderClientMarshaller.startDownloadServiceIfRequired(context, intent, SampleDownloaderService.class);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

}