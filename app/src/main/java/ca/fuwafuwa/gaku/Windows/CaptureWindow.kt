package ca.fuwafuwa.gaku.Windows

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
import ca.fuwafuwa.gaku.XmlParsers.CommonParser
import java.io.FileOutputStream
import java.io.IOException

/**
 * CaptureWindow handles the Region of Interest (ROI) selection,
 * UI presets, and initiates the OCR pipeline.
 */
class CaptureWindow(context: Context, windowCoordinator: WindowCoordinator) : Window(context, windowCoordinator, R.layout.window_capture), WindowListener
{
    private val mOcr: OcrRunnable
    private val mWindowBox: View
    private val mImageView: ImageView
    private val mWordOverlay: WordOverlayView
    private val mFadeRepeat: Animation
    
    // REQ-007 & REQ-008 UI Elements
    private val mPresetBar: View
    private val mGuideH: View
    private val mGuideV: View

    private var mLastDoubleTapTime: Long
    private val mLastDoubleTapIgnoreDelay: Long
    private var mInLongPress: Boolean = false
    private var mProcessingOcr: Boolean = false
    
    // Flag to lock movement until long press
    private var mIsEditMode: Boolean = false
    
    // Offsets for manual movement handling
    private var mDragOffsetX: Int = 0
    private var mDragOffsetY: Int = 0

    private var mCommonParser: CommonParser? = null

    private inner class ScreenshotForOcr(val crop: Bitmap?, val orig: Bitmap?, val params: BoxParams?) {
        fun recycle() {
            if (crop != null && !crop.isRecycled) crop.recycle()
            if (orig != null && !orig.isRecycled) orig.recycle()
        }
    }

    init
    {
        this.mCommonParser = CommonParser(context)

        mImageView = window.findViewById(R.id.capture_image)
        mWordOverlay = window.findViewById(R.id.word_overlay)
        
        mWordOverlay.setOnWordClickListener(object : WordOverlayView.OnWordClickListener {
            override fun onWordClicked(word: ParsedWord, isVertical: Boolean) {
                // We don't use isVertical for positioning anymore, per simplify request
                this@CaptureWindow.onWordClicked(word)
            }
            override fun onBlankSpaceClicked() {
                hideInstantWindows()
            }
        })
        
        mWindowBox = window.findViewById(R.id.capture_box)
        mPresetBar = window.findViewById(R.id.preset_bar)
        mGuideH = window.findViewById(R.id.guide_h)
        mGuideV = window.findViewById(R.id.guide_v)

        mFadeRepeat = AnimationUtils.loadAnimation(this.context, R.anim.fade_repeat)
        
        mLastDoubleTapTime = System.currentTimeMillis()
        mLastDoubleTapIgnoreDelay = 500
        mInLongPress = false
        mProcessingOcr = false
        mIsEditMode = false

        // Ensure window view allows haptics
        window.isHapticFeedbackEnabled = true

        // Initialize Presets and Border
        setupPresets()
        updateBorderVisuals()

        mOcr = OcrRunnable(this.context, this)
        val ocrThread = Thread(mOcr)
        ocrThread.name = String.format("OcrThread%d", System.nanoTime())
        ocrThread.isDaemon = true
        ocrThread.start()
        
        mOcr.warmUp()

        mWindowBox.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener
        {
            override fun onGlobalLayout()
            {
                (context as MainService).onCaptureWindowFinishedInitializing()
                mWindowBox.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        show()
    }

    override fun reInit(options: Window.ReinitOptions)
    {
        super.reInit(options)
        updateBorderVisuals()
    }

    private fun setupPresets() {
        val container = window.findViewById<LinearLayout>(R.id.preset_container)
        container.removeAllViews()
        
        CapturePreset.values().forEach { preset ->
            val btn = Button(context).apply {
                text = preset.label
                textSize = 10f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(context, R.drawable.bg_solid_border_corners_0_white_black_round)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
                
                val lp = LinearLayout.LayoutParams(dpToPx(context, 70), dpToPx(context, 40))
                lp.setMargins(dpToPx(context, 4), 0, dpToPx(context, 4), 0)
                layoutParams = lp
                
                setOnClickListener { applyPreset(preset) }
            }
            container.addView(btn)
        }
    }

    private fun applyPreset(preset: CapturePreset) {
        params.width = dpToPx(context, preset.widthDp)
        params.height = dpToPx(context, preset.heightDp)
        
        params.x = (getRealDisplaySize().x / 2) - (params.width / 2)
        params.y = (getRealDisplaySize().y / 2) - (params.height / 2)
        
        windowManager.updateViewLayout(window, params)
        blinkBorder()
        mPresetBar.visibility = View.GONE
        mIsEditMode = false 
    }

    private fun updateBorderVisuals() {
        val prefs = getPrefs(context)
        val color = Color.parseColor(prefs.borderColor)
        val thickness = dpToPx(context, prefs.borderThickness)
        
        val shape = GradientDrawable().apply {
            setStroke(thickness, color)
            val alphaColor = (color and 0x00FFFFFF) or 0x1A000000 
            setColor(alphaColor)
        }
        mWindowBox.background = shape
    }

    private fun blinkBorder() {
        mWindowBox.alpha = 0.3f
        mWindowBox.animate().alpha(1.0f).setDuration(300).start()
    }

    fun showGuides(h: Boolean, v: Boolean) {
        val prefs = getPrefs(context)
        if (!prefs.snapEnabled) return

        mGuideH.visibility = if (v) View.VISIBLE else View.GONE
        mGuideV.visibility = if (h) View.VISIBLE else View.GONE
        
        mGuideH.removeCallbacks(hideGuidesRunnable)
        mGuideH.postDelayed(hideGuidesRunnable, 800)
    }

    private val hideGuidesRunnable = Runnable {
        mGuideH.visibility = View.GONE
        mGuideV.visibility = View.GONE
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

        // Removed default call to hideInstantWindows here to prevent jitter closing.
        // It's now handled explicitly in onUp if clicking blank space.

        if (e.action == MotionEvent.ACTION_DOWN) {
            // Capture offsets for manual movement
            mDragOffsetX = params.x - e.rawX.toInt()
            mDragOffsetY = params.y - e.rawY.toInt()
            
            super.onTouch(e)
            return true
        }

        if (e.action == MotionEvent.ACTION_MOVE)
        {
            if (System.currentTimeMillis() > mLastDoubleTapTime + mLastDoubleTapIgnoreDelay)
            {
                mOcr.cancel()
            }

            // Manual movement logic
            if (mIsEditMode) {
                // Only clear overlay if we are actually moving the window
                mImageView.setImageResource(0)
                mWordOverlay.visibility = View.GONE
                updateWindowFlags(false)
                
                // Also close any popup if moving
                hideInstantWindows()

                params.x = mDragOffsetX + e.rawX.toInt()
                params.y = mDragOffsetY + e.rawY.toInt()
                
                // Keep within screen bounds
                val display = getRealDisplaySize()
                val statusBar = getStatusBarHeight()
                
                if (params.x < 0) params.x = 0
                else if (params.x + params.width > display.x) params.x = display.x - params.width
                
                if (params.y < 0) params.y = 0
                else if (params.y + params.height > display.y) params.y = display.y - params.height - statusBar
                
                windowManager.updateViewLayout(window, params)
            }
            
            return true
        }

        if (e.action == MotionEvent.ACTION_UP) {
            if (mIsEditMode) {
                mIsEditMode = false
                mPresetBar.postDelayed({ mPresetBar.visibility = View.GONE }, 2000)
            } else {
                // If not in edit mode (just a tap)
                // Check if we hit a word? WordOverlay handles clicks internally via its own OnTouch listener.
                // However, WordOverlay is transparent. 
                // If the click falls through to here, it means we clicked "blank space" on the capture window.
                
                // But wait, WordOverlayView fills the parent. 
                // We need to know if WordOverlay handled it. 
                // Actually, since WordOverlay is on top, if we receive onTouch here in Window, 
                // it implies WordOverlay didn't consume it?
                // No, Window's onTouch is called by WindowView's onTouchEvent which calls mDetector.
                
                // Simplest fix: Just hide the popup (WordDetailWindow) here.
                // If a word was clicked, WordDetailWindow will be shown by onWordClicked immediately after.
                // But if we hide it here, it might flicker or close immediately.
                // A better approach is to check if WordOverlay handled the tap.
                
                // Let's rely on WordOverlayView logic. If it didn't find a word, it does nothing.
                // If we want "Click blank space to close popup", we can just call hideInstantWindows().
                // However, we MUST NOT clear the results (mWordOverlay.visibility = GONE) on tap.
                
                hideInstantWindows()
            }

            onUp(e)
            return true
        }

        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // Prevent default window scrolling if not in edit mode
        if (!mIsEditMode) return true
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    override fun onLongPress(e: MotionEvent)
    {
        // Unlock Edit Mode and show Preset Bar
        mInLongPress = true
        mIsEditMode = true
        
        // Re-calculate offsets on long press to ensure smooth transition
        mDragOffsetX = params.x - e.rawX.toInt()
        mDragOffsetY = params.y - e.rawY.toInt()

        val prefs = getPrefs(context)
        if (prefs.showPresetBar) mPresetBar.visibility = View.VISIBLE
        
        window.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onResize(e: MotionEvent): Boolean
    {
        // Allow resize anytime (no edit mode check)
        
        // Handle touch up to reset state when resizing ends
        if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
            mIsEditMode = false
            mPresetBar.postDelayed({ mPresetBar.visibility = View.GONE }, 2000)
        }

        hideInstantWindows()
        mOcr.cancel()
        
        // Kept results visible during resize
        return super.onResize(e)
    }

    override fun onUp(e: MotionEvent): Boolean
    {
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
            mImageView.imageAlpha = 0
            mWindowBox.animation = mFadeRepeat
            mWindowBox.startAnimation(mFadeRepeat)
        }
    }

    fun stopLoadingAnimation(instant: Boolean)
    {
        (context as MainService).handler.post {
            mProcessingOcr = false
            mWindowBox.clearAnimation()
            
            if (instant)
            {
                mImageView.imageAlpha = 255
            } else
            {
                mImageView.imageAlpha = 255
                mImageView.setImageResource(0)
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
        // Close existing popup first (optional, but cleaner)
        hideInstantWindows()

        val wordDetailWindow = windowCoordinator.getWindowOfType<WordDetailWindow>(WINDOW_WORD_DETAIL)
        wordDetailWindow.setWord(word)
        wordDetailWindow.setOnStatusChangeListener {
            mWordOverlay.invalidate()
        }
        
        // Calculate Global Bounding Box of the Word
        val borderOffset = dpToPx(context, 1) + 1
        val relativeRect = word.boundingBox
        
        // CaptureWindow X + Border + Word X
        val globalLeft = params.x + borderOffset + relativeRect.left
        val globalTop = params.y + borderOffset + relativeRect.top
        
        val globalRect = Rect(globalLeft, globalTop, 
                            globalLeft + relativeRect.width(), 
                            globalTop + relativeRect.height())
                            
        // Calculate Global Bounding Box of the Capture Window
        val captureRect = Rect(params.x, params.y, params.x + params.width, params.y + params.height)
        
        wordDetailWindow.showForWordBounds(globalRect, captureRect)
    }

    override fun getDefaultParams(): WindowManager.LayoutParams
    {
        val params = super.getDefaultParams()
        params.x = realDisplaySize.x / 2 - params.width / 2
        params.y = realDisplaySize.y / 4 - params.height / 2
        params.alpha = 0.8F
        return params
    }

    private fun performOcr(instant: Boolean)
    {
        if (mProcessingOcr) return
        mProcessingOcr = true
        mLastDoubleTapTime = System.currentTimeMillis()

        showLoadingAnimation()

        val viewPos = IntArray(2)
        mWindowBox.getLocationOnScreen(viewPos)
        val box = BoxParams(viewPos[0], viewPos[1], params.width, params.height)

        Thread {
            try
            {
                if (!instant)
                {
                    var attempts = 0
                    while (!mOcr.isReadyForOcr && attempts < 20)
                    {
                        Thread.sleep(50)
                        attempts++
                    }
                }

                val ocrData = getOcrData(box)
                if (ocrData == null || ocrData.crop == null || ocrData.orig == null || ocrData.params == null)
                {
                    stopLoadingAnimation(instant)
                    return@Thread
                }

                mOcr.runTess(OcrParams(
                    ocrData.crop,
                    ocrData.orig,
                    ocrData.params,
                    ocrData.params.x,
                    ocrData.params.y,
                    instant
                ))

            } catch (e: Exception)
            {
                e.printStackTrace()
                stopLoadingAnimation(instant)
            }
        }.start()
    }

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
        val bitmap = (context as MainService).screenshotBitmap
        if (bitmap == null) return null

        val croppedBitmap = getCroppedBitmap(bitmap, box)
        return ScreenshotForOcr(croppedBitmap, bitmap, box)
    }

    private fun getCroppedBitmap(screenshot: Bitmap, box: BoxParams): Bitmap
    {
        val borderSize = dpToPx(context, 1) + 1
        
        // Ensure we don't crop outside bounds
        val startX = box.x + borderSize
        val startY = box.y + borderSize
        var width = box.width - 2 * borderSize
        var height = box.height - 2 * borderSize
        
        // Validation
        if (startX < 0 || startY < 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        if (width <= 0) width = 1
        if (height <= 0) height = 1
        if (startX + width > screenshot.width) width = screenshot.width - startX
        if (startY + height > screenshot.height) height = screenshot.height - startY
        
        return Bitmap.createBitmap(screenshot, startX, startY, width, height)
    }

    companion object
    {
        private val TAG = CaptureWindow::class.java.name
    }
}