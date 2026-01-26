package ca.fuwafuwa.gaku.Windows

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import ca.fuwafuwa.gaku.*
import ca.fuwafuwa.gaku.Ocr.BoxParams
import ca.fuwafuwa.gaku.Ocr.OcrParams
import ca.fuwafuwa.gaku.Ocr.OcrRunnable
import ca.fuwafuwa.gaku.Prefs
import ca.fuwafuwa.gaku.R
import ca.fuwafuwa.gaku.Windows.Interfaces.WindowListener
import ca.fuwafuwa.gaku.Windows.Views.WordOverlayView
import ca.fuwafuwa.gaku.Analysis.ParsedResult
import ca.fuwafuwa.gaku.Analysis.ParsedWord
import ca.fuwafuwa.gaku.Network.JitenApiClient
import ca.fuwafuwa.gaku.XmlParsers.CommonParser
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by 0xbad1d3a5 on 4/13/2016.
 */
class CaptureWindow(context: Context, windowCoordinator: WindowCoordinator) : Window(context, windowCoordinator, R.layout.window_capture), WindowListener
{
    private val mOcr: OcrRunnable
    private val mWindowBox: View
    private val mImageView: ImageView
    private val mWordOverlay: WordOverlayView
    private val mFadeRepeat: Animation
    private val mBorderDefault: Drawable?
    private val mBorderReady: Drawable?

    private var mPrefs: Prefs?
    private var mLastDoubleTapTime: Long
    private val mLastDoubleTapIgnoreDelay: Long
    private var mInLongPress: Boolean = false
    private var mProcessingPreview: Boolean = false
    private var mProcessingOcr: Boolean = false
    private var mScreenshotForOcr: ScreenshotForOcr? = null

    private var mCommonParser: CommonParser? = null

    // Helper class to hold data
    private inner class ScreenshotForOcr(val crop: Bitmap?, val orig: Bitmap?, val params: BoxParams?) {
        fun recycle() {
            if (crop != null && !crop.isRecycled) crop.recycle()
            if (orig != null && !orig.isRecycled) orig.recycle()
        }
    }

    init
    {
        show()

        this.mCommonParser = CommonParser(context)

        mImageView = window.findViewById(R.id.capture_image)
        mWordOverlay = window.findViewById(R.id.word_overlay)
        mWordOverlay.setOnWordClickListener { word -> onWordClicked(word) }
        

        mFadeRepeat = AnimationUtils.loadAnimation(this.context, R.anim.fade_repeat)
        
        // FIX: Use ContextCompat for modern Android compatibility
        mBorderDefault = ContextCompat.getDrawable(this.context, R.drawable.bg_translucent_border_0_blue_blue)
        mBorderReady = ContextCompat.getDrawable(this.context, R.drawable.bg_transparent_border_0_nil_ready)

        mLastDoubleTapTime = System.currentTimeMillis()
        mLastDoubleTapIgnoreDelay = 500
        mInLongPress = false
        mProcessingPreview = false
        mProcessingOcr = false
        mScreenshotForOcr = null

        mPrefs = getPrefs(context)

        mOcr = OcrRunnable(this.context, this)
        val ocrThread = Thread(mOcr)
        ocrThread.name = String.format("OcrThread%d", System.nanoTime())
        ocrThread.isDaemon = true
        ocrThread.start()
        
        // FIX: Initialize MLKit immediately so it's ready when the user captures
        mOcr.warmUp()

        // Need to wait for the view to finish updating before we try to determine its location
        mWindowBox = window.findViewById(R.id.capture_box)
        mWindowBox.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener
        {
            override fun onGlobalLayout()
            {
                (context as MainService).onCaptureWindowFinishedInitializing()
                mWindowBox.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    override fun reInit(options: Window.ReinitOptions)
    {
        mPrefs = getPrefs(context)
        super.reInit(options)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean
    {
        mLastDoubleTapTime = System.currentTimeMillis()
        performOcr(false)
        return true
    }

    override fun onTouch(e: MotionEvent): Boolean
    {
        if (e.pointerCount >= 3)
        {
            val currentContext = context
            if (currentContext != null) {
                Toast.makeText(currentContext, "Closing Gaku...", Toast.LENGTH_SHORT).show()
                (currentContext as? MainService)?.stopSelf()
            }
            return true
        }

        hideInstantWindows()

        if (e.action == MotionEvent.ACTION_MOVE && !mInLongPress && !mProcessingOcr)
        {
            mImageView.setImageResource(0)
            mWordOverlay.visibility = View.GONE
            updateWindowFlags(false)
        }

        if (!mInLongPress && !mProcessingOcr)
        {
            setBorderStyle(e)
        }

        if (e.action == MotionEvent.ACTION_MOVE)
        {
            if (System.currentTimeMillis() > mLastDoubleTapTime + mLastDoubleTapIgnoreDelay)
            {
                mOcr.cancel()
            }
        }

        return super.onTouch(e)
    }

    override fun onLongPress(e: MotionEvent)
    {
        Log.d(TAG, "onLongPress")
        mInLongPress = true
    }

    override fun onResize(e: MotionEvent): Boolean
    {
        hideInstantWindows()

        mOcr.cancel()
        mImageView.setImageResource(0)
        setBorderStyle(e)
        return super.onResize(e)
    }

    override fun onUp(e: MotionEvent): Boolean
    {
        Log.d(TAG, String.format("onUp - mInLongPress: %b | mProcessingPreview: %b | mProcessingOcr: %b", mInLongPress, mProcessingPreview, mProcessingOcr))

        if (!mInLongPress && !mProcessingPreview && !mProcessingOcr)
        {
            Log.d(TAG, "onUp - SetPreviewImage")
            setBorderStyle(e)
            mProcessingPreview = true
            setCroppedScreenshot()
        }

        mInLongPress = false

        return true
    }

    override fun stop()
    {
        mOcr.stop()
        super.stop()
    }

    fun showLoadingAnimation()
    {
        (context as MainService).handler.post {
            Log.d(TAG, "showLoadingAnimation")
            mWindowBox.background = mBorderDefault
            mImageView.imageAlpha = 0
            mWindowBox.animation = mFadeRepeat
            mWindowBox.startAnimation(mFadeRepeat)
        }
    }

    fun stopLoadingAnimation(instant: Boolean)
    {
        (context as MainService).handler.post {
            mProcessingOcr = false
            mWindowBox.background = mBorderReady
            mWindowBox.clearAnimation()
            
            if (instant)
            {
                mImageView.imageAlpha = 255
            } else
            {
                mImageView.imageAlpha = 255
                mImageView.setImageResource(0)
                
                mScreenshotForOcr?.recycle() 
                mScreenshotForOcr = null
            }
        }
    }

    fun hideInstantWindows()
    {
        windowCoordinator.getWindow(WINDOW_INSTANT_KANJI).hide()
        windowCoordinator.getWindow(WINDOW_WORD_DETAIL).hide()
    }

    fun setParsedResult(result: ParsedResult)
    {
        val prefs = getPrefs(context)
        (context as MainService).handler.post {
            mWordOverlay.setTextDirection(prefs.textDirectionSetting)
            mWordOverlay.setParsedResult(result)
            mWordOverlay.visibility = View.VISIBLE
            updateWindowFlags(true)
        }
    }

    private fun updateWindowFlags(focusable: Boolean) {
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        }
        if (addedToWindowManager) {
            windowManager.updateViewLayout(window, params)
        }
    }

    private fun onWordClicked(word: ParsedWord)
    {
        val wordDetailWindow = windowCoordinator.getWindowOfType<WordDetailWindow>(WINDOW_WORD_DETAIL)
        wordDetailWindow.setWord(word)
        wordDetailWindow.setOnStatusChangeListener {
            mWordOverlay.invalidate()
        }
        
        // Calculate the same border offset used in cropping
        val borderOffset = dpToPx(context, 1) + 1
        
        // Position popup above the word, relative to screen
        val rect = word.boundingBox
        val x = params.x + borderOffset + rect.left + (rect.width() / 2)
        val y = params.y + borderOffset + rect.top - 200 // Offset upwards
        
        wordDetailWindow.showAt(x, y)
    }

    override fun getDefaultParams(): WindowManager.LayoutParams
    {
        val params = super.getDefaultParams()
        params.x = realDisplaySize.x / 2 - params.width / 2
        params.y = realDisplaySize.y / 4 - params.height / 2
        params.alpha = 0.8F
        return params
    }

    // FIX: Refactored to avoid accessing Views on background thread
    private fun setCroppedScreenshot()
    {
        // 1. Calculate BoxParams on the UI Thread
        val viewPos = IntArray(2)
        mWindowBox.getLocationOnScreen(viewPos)
        val box = BoxParams(viewPos[0], viewPos[1], params.width, params.height)

        val thread = Thread(Runnable {
            
            // 2. Pass the pre-calculated box to the generator
            val ocrScreenshot = getOcrData(box)

            if (ocrScreenshot == null || ocrScreenshot.crop == null || ocrScreenshot.orig == null || ocrScreenshot.params == null)
            {
                mProcessingPreview = false
                return@Runnable
            }

            (context as MainService).handler.post {
                mScreenshotForOcr = ocrScreenshot

                mImageView.setImageBitmap(mScreenshotForOcr!!.crop)

                if (mPrefs!!.instantModeSetting && System.currentTimeMillis() > mLastDoubleTapTime + mLastDoubleTapIgnoreDelay)
                {
                    val sizeForInstant = minSize * 3
                    if (sizeForInstant >= mScreenshotForOcr!!.params!!.width || sizeForInstant >= mScreenshotForOcr!!.params!!.height)
                    {
                        performOcr(true)
                    }
                }

                mProcessingPreview = false
            }
        })
        thread.start()
    }

    private fun setBorderStyle(e: MotionEvent)
    {
        when (e.action)
        {
            MotionEvent.ACTION_DOWN -> mWindowBox.background = mBorderDefault
            MotionEvent.ACTION_UP -> mWindowBox.background = mBorderReady
        }
    }

    private fun performOcr(instant: Boolean)
    {
        mProcessingOcr = true

        try
        {
            if (!instant)
            {
                while (!mOcr.isReadyForOcr)
                {
                    mOcr.cancel()
                    Thread.sleep(10)
                }
            }

            if (mScreenshotForOcr == null)
            {
                mProcessingOcr = false
                return
            }

            // Note: Changed name to runTess to runTess (user can rename method in OcrRunnable to match if desired, 
            // but for now relying on previous OcrRunnable code which had runTess)
            mOcr.runTess(OcrParams(mScreenshotForOcr!!.crop!!, mScreenshotForOcr!!.crop!!, mScreenshotForOcr!!.params!!, mScreenshotForOcr!!.params!!.x, mScreenshotForOcr!!.params!!.y, instant))
        } catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    // FIX: Renamed from getter to normal function, accepts box params explicitly
    @Throws(Exception::class)
    private fun getOcrData(box: BoxParams): ScreenshotForOcr?
    {
        try
        {
            return getReadyScreenshot(box)
        } catch (e: Exception)
        {
            e.printStackTrace()
        }
        return null
    }

    @Throws(Exception::class)
    private fun getReadyScreenshot(box: BoxParams): ScreenshotForOcr?
    {
        Log.d(TAG, String.format("X:%d Y:%d (%dx%d)", box.x, box.y, box.width, box.height))

        var screenshotReady = false
        val startTime = System.currentTimeMillis()
        var screenshot: Bitmap? = null

        do
        {
            val bitmap = (context as MainService).screenshotBitmap
            if (bitmap == null) {
                Thread.sleep(10)
                continue
            }
            screenshot = bitmap
            screenshotReady = checkScreenshotIsReady(screenshot!!, box)

        } while (!screenshotReady && System.currentTimeMillis() < startTime + 2000)

        if (screenshot == null || !screenshotReady)
        {
            if (screenshot != null) {
                val croppedBitmap = getCroppedBitmap(screenshot!!, box)
                saveBitmap(screenshot!!, String.format("error_(%d,%d)_(%d,%d)", box.x, box.y, box.width, box.height))
                saveBitmap(croppedBitmap, String.format("error_(%d,%d)_(%d,%d)", box.x, box.y, box.width, box.height))
            }
            return null
        }

        val croppedBitmap = getCroppedBitmap(screenshot!!, box)

        return ScreenshotForOcr(croppedBitmap, screenshot, box)
    }

    private fun checkScreenshotIsReady(screenshot: Bitmap, box: BoxParams): Boolean
    {
        val readyColor = ContextCompat.getColor(context, R.color.red_capture_window_ready)
        
        // Safety check to ensure box is within screenshot bounds (prevent crash if screen rotated/resized)
        if (box.x + box.width > screenshot.width || box.y + box.height > screenshot.height) {
            return false
        }

        val screenshotColor = screenshot.getPixel(box.x, box.y)

        if (readyColor != screenshotColor && isAcceptableAlternateReadyColor(screenshotColor))
        {
            return false
        }

        for (x in box.x until box.x + box.width)
        {
            if (!isRGBWithinTolerance(readyColor, screenshot.getPixel(x, box.y)))
            {
                return false
            }
        }

        for (x in box.x until box.x + box.width)
        {
            if (!isRGBWithinTolerance(readyColor, screenshot.getPixel(x, box.y + box.height - 1)))
            {
                return false
            }
        }

        for (y in box.y until box.y + box.height)
        {
            if (!isRGBWithinTolerance(readyColor, screenshot.getPixel(box.x, y)))
            {
                return false
            }
        }

        for (y in box.y until box.y + box.height)
        {
            if (!isRGBWithinTolerance(readyColor, screenshot.getPixel(box.x + box.width - 1, y)))
            {
                return false
            }
        }
        
        return true
    }

    /**
     * Looks like sometimes the screenshot just has a color that is 100% totally wrong. Let's just accept any red that's "red enough"
     * @param screenshotColor
     * @return
     */
    private fun isAcceptableAlternateReadyColor(screenshotColor: Int): Boolean
    {
        val R = screenshotColor shr 16 and 0xFF
        val G = screenshotColor shr 8 and 0xFF
        val B = screenshotColor and 0xFF

        var isValid = true

        if (G * 10 > R)
        {
            isValid = false
        }

        if (B * 10 > R)
        {
            isValid = false
        }

        return isValid
    }

    private fun isRGBWithinTolerance(color: Int, colorToCheck: Int): Boolean
    {
        val redRatio = (colorToCheck shr 16 and 0xFF) / 3;
        var isColorWithinTolerance: Boolean = ((colorToCheck and 0xFF) < redRatio)
        isColorWithinTolerance = isColorWithinTolerance and ((colorToCheck shr 8 and 0xFF) < redRatio)
        isColorWithinTolerance = isColorWithinTolerance and ((colorToCheck shr 16 and 0xFF) > 140)
        // Log.d("RGB", "R: ${colorToCheck shr 16 and 0xFF} G: ${colorToCheck shr 8 and 0xFF}B: ${colorToCheck and 0xFF}")

        return isColorWithinTolerance
    }



    private fun getCroppedBitmap(screenshot: Bitmap, box: BoxParams): Bitmap
    {
        val borderSize = dpToPx(context, 1) + 1
        
        // Safety check for bounds
        val width = if (box.width - 2 * borderSize > 0) box.width - 2 * borderSize else 1
        val height = if (box.height - 2 * borderSize > 0) box.height - 2 * borderSize else 1
        
        return Bitmap.createBitmap(screenshot,
                box.x + borderSize,
                box.y + borderSize,
                width,
                height)
    }

    @Throws(IOException::class)
    private fun saveBitmap(bitmap: Bitmap, name: String)
    {
        val fs = String.format("%s/%s/%s_%d.png", context.filesDir.absolutePath, SCREENSHOT_FOLDER_NAME, name, System.nanoTime())
        Log.d(TAG, fs)
        FileOutputStream(fs).use { out ->
             bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    companion object
    {
        private val TAG = CaptureWindow::class.java.name
    }
}



