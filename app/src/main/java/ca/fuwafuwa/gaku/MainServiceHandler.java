package ca.fuwafuwa.gaku;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import ca.fuwafuwa.gaku.Ocr.OcrResult;
import ca.fuwafuwa.gaku.Analysis.ParsedResult;
import ca.fuwafuwa.gaku.Windows.CaptureWindow;
import ca.fuwafuwa.gaku.Windows.InformationWindow;
import ca.fuwafuwa.gaku.Windows.InstantKanjiWindow;
import ca.fuwafuwa.gaku.Windows.WindowCoordinator;

/**
 * Created by 0xbad1d3a5 on 4/15/2016.
 */
public class MainServiceHandler extends Handler {

    private static final String TAG = MainServiceHandler.class.getName();

    private MainService mGakuService;
    private WindowCoordinator mWindowCoordinator;

    public MainServiceHandler(MainService mainService, WindowCoordinator windowCoordinator) {
        mGakuService = mainService;
        mWindowCoordinator = windowCoordinator;
    }

    @Override
    public void handleMessage(Message message) {
        if (message.obj instanceof String) {
            String errorMsg = (String) message.obj;

            // 1. Still try the toast (for cases where settings are okay)
            Toast.makeText(mGakuService, errorMsg, Toast.LENGTH_LONG).show();

            // 2. ALSO show it in the Capture Window so the user definitely sees it
            if (mWindowCoordinator.hasWindow(Constants.WINDOW_CAPTURE)) {
                CaptureWindow capWin = mWindowCoordinator.getWindowOfType(Constants.WINDOW_CAPTURE);
                // We can use the loading animation logic to show the error text
                // Or simple Log to confirm it reached the handler
                Log.e(TAG, "UI Error Reported: " + errorMsg);
            }
        } else if (message.obj instanceof OcrResult) {
            OcrResult result = (OcrResult) message.obj;

            Log.d(TAG, result.toString());

            if (result.getDisplayData().getInstantMode()) {
                InstantKanjiWindow instantKanjiWindow = mWindowCoordinator
                        .getWindowOfType(Constants.WINDOW_INSTANT_KANJI);
                instantKanjiWindow.setResult(result.getDisplayData());
                instantKanjiWindow.show();
            } else {
                InformationWindow infoWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_INFO);
                infoWindow.setResult(result.getDisplayData());
                infoWindow.show();
            }
        } else if (message.obj instanceof ParsedResult) {
            ParsedResult result = (ParsedResult) message.obj;
            if (result.getWords().isEmpty()) {
                // Optional: specifically handle the "empty but successful" case vs "failed"
                Log.d(TAG, "No words found in result.");
            } else {
                CaptureWindow captureWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_CAPTURE);
                captureWindow.setParsedResult(result);
            }
        } else {
            Toast.makeText(mGakuService, String.format("Unable to handle type: %s", message.obj.getClass().getName()),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
