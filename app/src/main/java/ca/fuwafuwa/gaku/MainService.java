package ca.fuwafuwa.gaku;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import ca.fuwafuwa.gaku.Interfaces.Stoppable;
import ca.fuwafuwa.gaku.Windows.Window;
import ca.fuwafuwa.gaku.Windows.WindowCoordinator;

import static androidx.core.app.NotificationCompat.FLAG_FOREGROUND_SERVICE;
import static androidx.core.app.NotificationCompat.FLAG_ONGOING_EVENT;

/**
 * Created by 0xbad1d3a5 on 4/9/2016.
 */
public class MainService extends Service implements Stoppable {

    private static final String TAG = MainService.class.getName();

    public static class CloseMainService extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "GOT CLOSE");
            context.stopService(new Intent(context, MainService.class));
        }
    }

    public static class ToggleImagePreviewMainService extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = context.getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean imagePreview = prefs.getBoolean(Constants.GAKU_PREF_IMAGE_FILTER, true);
            prefs.edit().putBoolean(Constants.GAKU_PREF_IMAGE_FILTER, !imagePreview).apply();

            GakuTools.startGakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class ToggleShowHideMainService extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = context.getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean shown = prefs.getBoolean(Constants.GAKU_PREF_SHOW_HIDE, true);
            prefs.edit().putBoolean(Constants.GAKU_PREF_SHOW_HIDE, !shown).apply();

            GakuTools.startGakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class TogglePageModeMainService extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = context.getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
            TextDirection textDirection = TextDirection
                    .valueOf(prefs.getString(Constants.GAKU_PREF_TEXT_DIRECTION, TextDirection.AUTO.toString()));
            textDirection = TextDirection.Companion.getByValue((textDirection.ordinal() + 1) % 3);
            prefs.edit().putString(Constants.GAKU_PREF_TEXT_DIRECTION, textDirection.toString()).apply();

            GakuTools.startGakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class ToggleInstantModeMainService extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = context.getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean pageMode = prefs.getBoolean(Constants.GAKU_PREF_INSTANT_MODE, true);
            prefs.edit().putBoolean(Constants.GAKU_PREF_INSTANT_MODE, !pageMode).apply();

            GakuTools.startGakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class ScreenOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = context.getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.GAKU_PREF_SHOW_HIDE, false).apply();

            GakuTools.startGakuService(context, new Intent(context, MainService.class));
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.d(TAG, "Stopping projection");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (MediaProjectionStopCallback.this == mMediaProjectionStopCallback) {
                        if (mVirtualDisplay != null) {
                            mVirtualDisplay.release();
                        }
                        mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                        mMediaProjection = null;
                        mImageReader.close();
                    }
                }
            });
        }
    }

    private static boolean isGakuRunning = false;

    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int NOTIFICATION_ID = 1;

    private final Object mScreenshotLock = new Object();

    private IntentFilter mIntentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    private ScreenOffReceiver mScreenOffReceiver = new ScreenOffReceiver();

    private Intent mProjectionResultIntent;
    private int mProjectionResultCode;

    private WindowManager mWindowManager;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private MainServiceHandler mHandler;

    private int mRotation;
    private Point mRealDisplaySize = new Point();

    private MediaProjectionStopCallback mMediaProjectionStopCallback;
    private WindowCoordinator mWindowCoordinator = new WindowCoordinator(this);

    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!isGakuRunning) {
            SharedPreferences prefs = getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.GAKU_PREF_SHOW_HIDE, true).apply();
        }

        Log.d(TAG, "CREATING MAINSERVICE: " + System.identityHashCode(this));
        Toast.makeText(this, "Starting capture window...", Toast.LENGTH_LONG).show();

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mHandler = new MainServiceHandler(this, mWindowCoordinator);

        // Set preferences for ratings
        SharedPreferences prefs = getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
        int timesLaunched = prefs.getInt(Constants.GAKU_PREF_TIMES_LAUNCHED, 1);
        prefs.edit().putInt(Constants.GAKU_PREF_TIMES_LAUNCHED, timesLaunched + 1).apply();

        registerReceiver(mScreenOffReceiver, mIntentFilter);

        startForeground(NOTIFICATION_ID, getNotification());
        isGakuRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent.getExtras() != null &&
                intent.getExtras().containsKey(Constants.EXTRA_PROJECTION_RESULT_CODE) &&
                intent.getExtras().containsKey(Constants.EXTRA_PROJECTION_RESULT_INTENT)) {
            mProjectionResultIntent = (Intent) intent.getExtras().get(Constants.EXTRA_PROJECTION_RESULT_INTENT);
            mProjectionResultCode = intent.getExtras().getInt(Constants.EXTRA_PROJECTION_RESULT_CODE);
        }

        // Determine if we need to start/stop the capture service
        SharedPreferences prefs = getSharedPreferences(Constants.GAKU_PREF_FILE, Context.MODE_PRIVATE);
        Boolean shown = prefs.getBoolean(Constants.GAKU_PREF_SHOW_HIDE, true);
        if (shown) {
            // Re-init CaptureWindow as well as prefs may have changed (BroadcastReceiver go
            // to onStartCommand())
            mWindowCoordinator.getWindow(Constants.WINDOW_CAPTURE).reInit(new Window.ReinitOptions());
        } else {
            mWindowCoordinator.stopAllWindows();
            stop();
        }

        // Set notification text
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, getNotification());

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mWindowCoordinator.hasWindow(Constants.WINDOW_CAPTURE) && mMediaProjection != null) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                Log.d(TAG, "Orientation changed. Re-creating Virtual Display.");
                mRotation = rotation;
                createVirtualDisplay(); // This will now use the new rotation
                mWindowCoordinator.reinitAllWindows();
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenOffReceiver);
        stopForeground(true);
        Log.d(TAG, "DESTORYING MAINSERVICE: " + System.identityHashCode(this));

        stop();
        mWindowCoordinator.stopAllWindows();
        mWindowCoordinator = null;
        isGakuRunning = false;

        Log.d(TAG, String.format("MAINSERVICE: %s DESTROYED", System.identityHashCode(this)));
        super.onDestroy();
    }

    @Override
    public void stop() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }

    public static boolean IsRunning() {
        return isGakuRunning;
    }

    /**
     * This function is here as a bug fix against
     * {@link #onConfigurationChanged(Configuration)} not
     * triggering when the app is first started and immediately switches to another
     * orientation. In
     * such a case onConfigurationChanged will not trigger and
     * {@link Window#reInit(ca.fuwafuwa.gaku.Windows.Window.ReinitOptions)} will not
     * update the LayoutParams.
     */
    public void onCaptureWindowFinishedInitializing() {
        if (mMediaProjection == null) {
            Log.d(TAG, "mMediaProjection is null");
            mMediaProjection = mMediaProjectionManager.getMediaProjection(mProjectionResultCode,
                    mProjectionResultIntent);
            mMediaProjectionStopCallback = new MediaProjectionStopCallback();
            mMediaProjection.registerCallback(mMediaProjectionStopCallback, mHandler);
        }
        createVirtualDisplay();
    }

    public Handler getHandler() {
        return mHandler;
    }

    public Bitmap getScreenshotBitmap() {
        synchronized (mScreenshotLock) {
            if (mImageReader == null) {
                return null;
            }

            try {
                // Try to get the latest image
                Image image = mImageReader.acquireLatestImage();
                if (image == null) {
                    long startTime = System.nanoTime();
                    while (image == null && System.nanoTime() < startTime + 2000000000) {
                        try {
                            mScreenshotLock.wait(20);
                        } catch (InterruptedException e) {
                            return null;
                        }
                        if (mImageReader == null)
                            return null;
                        image = mImageReader.acquireLatestImage();
                    }
                }

                if (image != null) {
                    try {
                        return convertImageToBitmap(image);
                    } finally {
                        image.close();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting screenshot", e);
            }
            return null;
        }
    }

    private Bitmap convertImageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(),
                Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private Notification getNotification() {
        String channelId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel();
        } else {
            channelId = "";
        }

        PendingIntent toggleShowHide = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_TOGGLE_SHOW_HIDE,
                new Intent(this, ToggleShowHideMainService.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggleImagePreview = PendingIntent.getBroadcast(this,
                Constants.REQUEST_SERVICE_TOGGLE_IMAGE_PREVIEW, new Intent(this, ToggleImagePreviewMainService.class),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent togglePageMode = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_TOGGLE_PAGE_MODE,
                new Intent(this, TogglePageModeMainService.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggleInstantMode = PendingIntent.getBroadcast(this,
                Constants.REQUEST_SERVICE_TOGGLE_INSTANT_MODE, new Intent(this, ToggleInstantModeMainService.class),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent closeMainService = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_SHUTDOWN,
                new Intent(this, CloseMainService.class), PendingIntent.FLAG_IMMUTABLE);

        Prefs prefs = GakuTools.getPrefs(this);

        String contentTitle = "Gaku";
        switch (prefs.getTextDirectionSetting()) {
            case AUTO:
                contentTitle = "Gaku is determining text direction automatically";
                break;
            case VERTICAL:
                contentTitle = "Gaku is reading text vertically";
                break;
            case HORIZONTAL:
                contentTitle = "Gaku is reading text horizontally";
                break;
        }

        Notification n;
        if (prefs.getShowHideSetting()) {
            n = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.gaku_notification_icon)
                    .setContentTitle(contentTitle)
                    .setContentText(String.format("Instant mode %s, black and white filter %s",
                            prefs.getInstantModeSetting() ? "on" : "off", prefs.getImageFilterSetting() ? "on" : "off"))
                    .setContentIntent(toggleShowHide)
                    .addAction(0, "Instant Mode", toggleInstantMode)
                    .addAction(0, "Image Filter", toggleImagePreview)
                    .addAction(0, "Shutdown", closeMainService)
                    .build();
        } else {
            n = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.gaku_notification_icon)
                    .setContentTitle("Gaku is hidden and in power-saving mode")
                    .setContentIntent(toggleShowHide)
                    .build();
        }

        n.flags = FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE;

        return n;
    }

    private void createVirtualDisplay() {
        // display metrics
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int mDensity = metrics.densityDpi;
        mDisplay = mWindowManager.getDefaultDisplay();

        // get width and height
        mDisplay.getRealSize(mRealDisplaySize);

        // start capture reader
        Log.d(TAG, String.format("Starting Projection: %dx%d", mRealDisplaySize.x, mRealDisplaySize.y));

        synchronized (mScreenshotLock) {
            if (mVirtualDisplay == null) {
                mImageReader = ImageReader.newInstance(mRealDisplaySize.x, mRealDisplaySize.y, PixelFormat.RGBA_8888,
                        2);
                mVirtualDisplay = mMediaProjection.createVirtualDisplay(getClass().getName(), mRealDisplaySize.x,
                        mRealDisplaySize.y, mDensity, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
            } else {
                ImageReader newImageReader = ImageReader.newInstance(mRealDisplaySize.x, mRealDisplaySize.y,
                        PixelFormat.RGBA_8888, 2);
                mVirtualDisplay.resize(mRealDisplaySize.x, mRealDisplaySize.y, mDensity);
                mVirtualDisplay.setSurface(newImageReader.getSurface());
                if (mImageReader != null) {
                    mImageReader.close();
                }
                mImageReader = newImageReader;
            }
        }
    }

    private void stopVirtualDisplay() {
        synchronized (mScreenshotLock) {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        String channelId = Constants.GAKU_CHANNEL_ID;
        String channelName = Constants.GAKU_CHANNEL_NAME;

        NotificationChannel channel = new NotificationChannel(channelId, channelName,
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(channel);

        return channelId;
    }
}
