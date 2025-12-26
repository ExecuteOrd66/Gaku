package ca.fuwafuwa.gaku

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import ca.fuwafuwa.gaku.Windows.InformationWindow
import ca.fuwafuwa.gaku.Windows.WindowCoordinator

class PassthroughActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setupGakuDatabasesAndFiles(this)

        var processText : String? = null
        when {
            intent?.action == Intent.ACTION_PROCESS_TEXT ->
            {
                processText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            }
            intent?.action == Intent.ACTION_SEND ->
            {
                if ("text/plain" == intent.type)
                {
                    processText = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
        }

        if (processText != null)
        {
            val windowCoordinator = WindowCoordinator(applicationContext)
            val infoWindow = windowCoordinator.getWindow(WINDOW_INFO) as InformationWindow

            infoWindow.setResult(processText)
            infoWindow.show()

            finish()
        }
    }
}




