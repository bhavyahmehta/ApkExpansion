package com.demo.apkexpansion;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Messenger;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.google.android.vending.expansion.downloader.Constants;
import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

import static android.R.attr.data;

/**
 * Created by bhavya on 07-07-2017.
 */

/**
 * This implements the IDownloaderClient that the client marshaler will talk to as
 * messages are delivered from the DownloaderService.
 */
public class MainActivity extends AppCompatActivity implements IDownloaderClient {

    private ProgressBar mPB;
    private TextView mStatusText;
    private ImageView mImage;
    private int mState;
    private IStub mDownloaderClientStub;
    /**
     * Calculating a moving average for the validation speed so we don't get
     * jumpy calculations for time etc.
     */
    static private final float SMOOTHING_FACTOR = 0.005f;

    /**
     * Used by the async task
     */
    private boolean mCancelValidation;

    /**
     * Called when the activity is first create; we wouldn't create a layout in
     * the case where we have the file and are moving to another activity
     * without downloading.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDownloaderClientStub = DownloaderClientMarshaller.CreateStub
                (this, SampleDownloaderService.class);
        setContentView(R.layout.activity_main);
        setUpViews();

        /**
         * Before we do anything, are the files we expect already here and
         * delivered (presumably by Market)
         */
        if (!expansionFilesDelivered()) {
            try {
                Intent launchIntent = MainActivity.this.getIntent();
                Intent intentToLaunchThisActivityFromNotification = new Intent(
                        MainActivity.this, MainActivity.this.getClass());
                intentToLaunchThisActivityFromNotification.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intentToLaunchThisActivityFromNotification.setAction(launchIntent.getAction());

                if (launchIntent.getCategories() != null) {
                    for (String category : launchIntent.getCategories()) {
                        intentToLaunchThisActivityFromNotification.addCategory(category);
                    }
                }
                // Build PendingIntent used to open this activity from
                // Notification
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        MainActivity.this,
                        0, intentToLaunchThisActivityFromNotification,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                // Request to start the download
                DownloaderClientMarshaller.startDownloadServiceIfRequired(this,
                        pendingIntent, SampleDownloaderService.class);

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

        } else {
            validateXAPKZipFiles();
        }
    }

    private void setUpViews() {
        mPB = (ProgressBar) findViewById(R.id.progressBar);
        mStatusText = (TextView) findViewById(R.id.statusText);
        mImage = (ImageView) findViewById(R.id.image);
    }

    /**
     * Go through each of the APK Expansion files defined in the structure above
     * and determine if the files are present and match the required size.
     *
     * @return true if they are present.
     */
    boolean expansionFilesDelivered() {
        for (XAPKFile xf : Config.xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(this, xf.mIsMain, xf.mFileVersion);
            if (!Helpers.doesFileExist(this, fileName, xf.mFileSize, false))
                return false;
        }
        return true;
    }

    /**
     * Connect the stub to our service on start.
     */
    @Override
    protected void onStart() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
        }
        super.onStart();
    }

    /**
     * Disconnect the stub from our service on stop
     */
    @Override
    protected void onStop() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.disconnect(this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        this.mCancelValidation = true;
        super.onDestroy();
    }

    /**
     * Critical implementation detail. In onServiceConnected we create the
     * remote service and marshaler. This is how we pass the client information
     * back to the service so the client can be properly notified of changes. We
     * must do this every time we reconnect to the service.
     */
    @Override
    public void onServiceConnected(Messenger m) {
        IDownloaderService mRemoteService = DownloaderServiceMarshaller.CreateProxy(m);
        mRemoteService.onClientUpdated(mDownloaderClientStub.getMessenger());
    }

    /**
     * The download state should trigger changes in the UI --- it may be useful
     * to show the state as being indeterminate at times. This sample can be
     * considered a guideline.
     */
    @Override
    public void onDownloadStateChanged(int newState) {
        setState(newState);
        boolean indeterminate;
        switch (newState) {
            case IDownloaderClient.STATE_IDLE:
                // STATE_IDLE means the service is listening, so it's
                // safe to start making calls via mRemoteService.
                indeterminate = true;
                break;
            case IDownloaderClient.STATE_CONNECTING:
            case IDownloaderClient.STATE_FETCHING_URL:
                indeterminate = true;
                break;
            case IDownloaderClient.STATE_DOWNLOADING:
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_FAILED_CANCELED:
            case IDownloaderClient.STATE_FAILED:
            case IDownloaderClient.STATE_FAILED_FETCHING_URL:
            case IDownloaderClient.STATE_FAILED_UNLICENSED:
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_NEED_CELLULAR_PERMISSION:
            case IDownloaderClient.STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION:
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_BY_REQUEST:
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_ROAMING:
            case IDownloaderClient.STATE_PAUSED_SDCARD_UNAVAILABLE:
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_COMPLETED:
                validateXAPKZipFiles();
                return;
            default:
                indeterminate = true;
        }
        mPB.setIndeterminate(indeterminate);
    }

    /**
     * Sets the state of the various controls based on the progressinfo object
     * sent from the downloader service.
     */
    @Override
    public void onDownloadProgress(DownloadProgressInfo progress) {
        mPB.setMax((int) (progress.mOverallTotal >> 8));
        mPB.setProgress((int) (progress.mOverallProgress >> 8));
    }

    private void setState(int newState) {
        if (mState != newState) {
            mState = newState;
            mStatusText.setText(Helpers.getDownloaderStringResourceIDFromState(newState));
        }
    }

    /**
     * Go through each of the Expansion APK files and open each as a zip file.
     * Calculate the CRC for each file and return false if any fail to match.
     *
     * @return true if XAPKZipFile is successful
     */
    void validateXAPKZipFiles() {
        AsyncTask<Object, DownloadProgressInfo, Boolean> validationTask = new AsyncTask<Object, DownloadProgressInfo, Boolean>() {

            @Override
            protected void onPreExecute() {
                mStatusText.setText(R.string.text_verifying_download);
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Object... params) {
                for (XAPKFile xf : Config.xAPKS) {
                    String fileName = Helpers.getExpansionAPKFileName(
                            MainActivity.this,
                            xf.mIsMain, xf.mFileVersion);
                    if (!Helpers.doesFileExist(MainActivity.this, fileName,
                            xf.mFileSize, false))
                        return false;
                    fileName = Helpers
                            .generateSaveFileName(MainActivity.this, fileName);
                    ZipResourceFile zrf;
                    byte[] buf = new byte[1024 * 256];
                    try {
                        zrf = new ZipResourceFile(fileName);
                        ZipResourceFile.ZipEntryRO[] entries = zrf.getAllEntries();
                        /**
                         * First calculate the total compressed length
                         */
                        long totalCompressedLength = 0;
                        for (ZipResourceFile.ZipEntryRO entry : entries) {
                            totalCompressedLength += entry.mCompressedLength;
                        }
                        float averageVerifySpeed = 0;
                        long totalBytesRemaining = totalCompressedLength;
                        long timeRemaining;
                        /**
                         * Then calculate a CRC for every file in the Zip file,
                         * comparing it to what is stored in the Zip directory.
                         * Note that for compressed Zip files we must extract
                         * the contents to do this comparison.
                         */
                        for (ZipResourceFile.ZipEntryRO entry : entries) {
                            if (-1 != entry.mCRC32) {
                                long length = entry.mUncompressedLength;
                                CRC32 crc = new CRC32();
                                DataInputStream dis = null;
                                try {
                                    dis = new DataInputStream(
                                            zrf.getInputStream(entry.mFileName));

                                    long startTime = SystemClock.uptimeMillis();
                                    while (length > 0) {
                                        int seek = (int) (length > buf.length ? buf.length
                                                : length);
                                        dis.readFully(buf, 0, seek);
                                        crc.update(buf, 0, seek);
                                        length -= seek;
                                        long currentTime = SystemClock.uptimeMillis();
                                        long timePassed = currentTime - startTime;
                                        if (timePassed > 0) {
                                            float currentSpeedSample = (float) seek
                                                    / (float) timePassed;
                                            if (0 != averageVerifySpeed) {
                                                averageVerifySpeed = SMOOTHING_FACTOR
                                                        * currentSpeedSample
                                                        + (1 - SMOOTHING_FACTOR)
                                                        * averageVerifySpeed;
                                            } else {
                                                averageVerifySpeed = currentSpeedSample;
                                            }
                                            totalBytesRemaining -= seek;
                                            timeRemaining = (long) (totalBytesRemaining / averageVerifySpeed);
                                            this.publishProgress(
                                                    new DownloadProgressInfo(
                                                            totalCompressedLength,
                                                            totalCompressedLength
                                                                    - totalBytesRemaining,
                                                            timeRemaining,
                                                            averageVerifySpeed)
                                            );
                                        }
                                        startTime = currentTime;
                                        if (mCancelValidation)
                                            return true;
                                    }
                                    if (crc.getValue() != entry.mCRC32) {
                                        Log.e(Constants.TAG,
                                                "CRC does not match for entry: "
                                                        + entry.mFileName);
                                        Log.e(Constants.TAG,
                                                "In file: " + entry.getZipFileName());
                                        return false;
                                    }
                                } finally {
                                    if (null != dis) {
                                        dis.close();
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(DownloadProgressInfo... values) {
                onDownloadProgress(values[0]);
                super.onProgressUpdate(values);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    mStatusText.setText(R.string.text_validation_complete);
                    readXAPKZipFiles();
                } else {
                    mStatusText.setText(R.string.text_validation_failed);
                }
                super.onPostExecute(result);
            }

        };
        validationTask.execute(new Object());
    }

    void readXAPKZipFiles() {
        AsyncTask<Void, Void, String> diskWriteTask = new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mStatusText.setText(R.string.text_reading_start);
            }

            @Override
            protected String doInBackground(Void... params) {
                try {
                    // Get a ZipResourceFile representing a merger of both the main and patch files
                    ZipResourceFile expansionFile =
                            APKExpansionSupport.getAPKExpansionZipFile(MainActivity.this,Config.EXPANSION_MAIN_VERSION, 0);

                    if (expansionFile != null) {
                        // Get an input stream for a known file inside the expansion file ZIPs
                        InputStream fileStream = expansionFile.getInputStream(Config.EX_MAIN_FILE_NAME +"."+ Config.EX_MAIN_FILE_EXTN);
                        if (fileStream != null) {
                            return getFilePath(MainActivity.this, Config.EX_MAIN_FILE_NAME, Config.EX_MAIN_FILE_EXTN, fileStream);
                        }
                    }
                } catch (Exception e) {
                   e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String path) {
                // hide loader with/out  error message
                super.onPostExecute(path);
                if(!TextUtils.isEmpty(path)) {
                    File file = new File(path);
                    if(file.exists()){
                        mStatusText.setText(R.string.text_reading_complete);
                        mPB.setVisibility(View.GONE);
                        mImage.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
                    } else{
                        mStatusText.setText(R.string.text_reading_failed);
                    }
                }
            }
        };
        diskWriteTask.execute();
    }

     /*Create a path where we will place our private file on external
     storage.*/
    public String getFilePath(Context context, String name,
                              String extension, InputStream is) throws Exception {
        File file = new File(context.getExternalFilesDir(null), name + "." + extension);
        OutputStream os = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        os.write(data);
        is.close();
        os.close();
        return file.getPath();
    }

}
