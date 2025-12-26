package ca.fuwafuwa.kaku;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

<<<<<<< Updated upstream:app/src/main/java/ca/fuwafuwa/kaku/MainServiceHandler.java
import ca.fuwafuwa.kaku.Ocr.OcrResult;
import ca.fuwafuwa.kaku.Windows.InformationWindow;
import ca.fuwafuwa.kaku.Windows.InstantKanjiWindow;
import ca.fuwafuwa.kaku.Windows.WindowCoordinator;
=======
import ca.fuwafuwa.gaku.Ocr.OcrResult;
import ca.fuwafuwa.gaku.Analysis.ParsedResult;
import ca.fuwafuwa.gaku.Windows.CaptureWindow;
import ca.fuwafuwa.gaku.Windows.InformationWindow;
import ca.fuwafuwa.gaku.Windows.InstantKanjiWindow;
import ca.fuwafuwa.gaku.Windows.WindowCoordinator;
>>>>>>> Stashed changes:app/src/main/java/ca/fuwafuwa/gaku/MainServiceHandler.java

/**
 * Created by 0xbad1d3a5 on 4/15/2016.
 */
public class MainServiceHandler extends Handler {

    private static final String TAG = MainServiceHandler.class.getName();

    private MainService mKakuService;
    private WindowCoordinator mWindowCoordinator;

    public MainServiceHandler(MainService mainService, WindowCoordinator windowCoordinator)
    {
        mKakuService = mainService;
        mWindowCoordinator = windowCoordinator;
    }

    @Override
    public void handleMessage(Message message)
    {
        if (message.obj instanceof String){
            Toast.makeText(mKakuService, message.obj.toString(), Toast.LENGTH_SHORT).show();
        }
        else if (message.obj instanceof OcrResult)
        {
            OcrResult result = (OcrResult) message.obj;

            Log.d(TAG, result.toString());

            if (result.getDisplayData().getInstantMode())
            {
                InstantKanjiWindow instantKanjiWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_INSTANT_KANJI);
                instantKanjiWindow.setResult(result.getDisplayData());
                instantKanjiWindow.show();
            }
            else {
                InformationWindow infoWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_INFO);
                infoWindow.setResult(result.getDisplayData());
                infoWindow.show();
            }
<<<<<<< Updated upstream:app/src/main/java/ca/fuwafuwa/kaku/MainServiceHandler.java
        }
        else {
            Toast.makeText(mKakuService, String.format("Unable to handle type: %s", message.obj.getClass().getName()), Toast.LENGTH_SHORT).show();
=======
        } else if (message.obj instanceof ParsedResult) {
            ParsedResult result = (ParsedResult) message.obj;
            Log.d(TAG, result.toString());

            if (result.getDisplayData().getInstantMode()) {
                // For instant mode, we might still want the old popup or the new one?
                // Mockup suggests we want the new one when we CLICK.
                // But ML Kit is fast enough for instant overlay.
            } else {
                CaptureWindow captureWindow = mWindowCoordinator.getWindowOfType(Constants.WINDOW_CAPTURE);
                captureWindow.setParsedResult(result);
            }
        } else {
            Toast.makeText(mGakuService, String.format("Unable to handle type: %s", message.obj.getClass().getName()),
                    Toast.LENGTH_SHORT).show();
>>>>>>> Stashed changes:app/src/main/java/ca/fuwafuwa/gaku/MainServiceHandler.java
        }
    }
}