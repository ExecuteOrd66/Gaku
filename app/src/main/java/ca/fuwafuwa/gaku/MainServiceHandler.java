package ca.fuwafuwa.gaku;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import ca.fuwafuwa.gaku.Ocr.OcrResult;
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
            Toast.makeText(mGakuService, message.obj.toString(), Toast.LENGTH_SHORT).show();
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
        } else {
            Toast.makeText(mGakuService, String.format("Unable to handle type: %s", message.obj.getClass().getName()),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
