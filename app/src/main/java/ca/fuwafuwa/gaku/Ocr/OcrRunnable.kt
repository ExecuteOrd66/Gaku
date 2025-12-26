package ca.fuwafuwa.gaku.Ocr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import ca.fuwafuwa.gaku.*
import ca.fuwafuwa.gaku.Interfaces.Stoppable
import ca.fuwafuwa.gaku.MainService
import ca.fuwafuwa.gaku.Windows.CaptureWindow
import ca.fuwafuwa.gaku.Windows.Data.ChoiceCertainty
import ca.fuwafuwa.gaku.Windows.Data.DisplayDataOcr
import ca.fuwafuwa.gaku.Windows.Data.SquareCharOcr

class OcrRunnable(context: Context, private var mCaptureWindow: CaptureWindow?) : Runnable, Stoppable {
    private val mContext: MainService = context as MainService
    private val mOcrLock = java.lang.Object()
    private val mSimilarChars = loadSimilarChars()
    private val mCommonMistakes = loadCommonMistakes()
    private var mTextRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private var mThreadRunning = true
    private var mOcrParams: OcrParams? = null
    private var mIsReady = false

    val isReadyForOcr: Boolean
        get() = mOcrParams == null

    init {
        mOcrParams = null
    }

    fun warmUp() {
        if (mIsReady) return

        Log.d(TAG, "Warming up OCR engine.")

        // safeInitClient returns true if successful, false if it crashed
        if (!safeInitClient()) {
            sendToastToContext("Failed to initialize MLKit (Check Logs)")
            return
        }

        val dummyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val image = InputImage.fromBitmap(dummyBitmap, 0)

        // Force non-null assertion (!!) is safe here because safeInitClient returned true
        mTextRecognizer!!.process(image)
            .addOnSuccessListener {
                Log.d(TAG, "OCR engine is ready.")
                mIsReady = true
                sendModelReadyBroadcast()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR engine warm-up failed.", e)
                mIsReady = true // Mark ready anyway to unblock UI
                sendModelReadyBroadcast()
            }
    }

    private fun safeInitClient(): Boolean {
        if (mTextRecognizer != null) return true

        return try {
            val options = JapaneseTextRecognizerOptions.Builder().build()
            mTextRecognizer = TextRecognition.getClient(options)
            true
        } catch (e: Throwable) {
            // This catch block is CRITICAL. It catches UnsatisfiedLinkError (Native crash)
            // and standard Exceptions, printing them to Logcat so you can finally see them.
            Log.e(TAG, "CRITICAL ERROR: Could not create TextRecognition client", e)
            e.printStackTrace()
            false
        }
    }

    private fun sendModelReadyBroadcast() {
        val intent = Intent(ACTION_MODEL_READY)
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

    override fun run() {
        // 1. Initialize safely on the background thread.
        // If this fails, we exit the thread to prevent a crash loop.
        if (!safeInitClient()) {
            Log.e(TAG, "Stopping OcrRunnable because OCR Client failed to initialize.")
            return
        }

        while (mThreadRunning) {
            try {
                synchronized(mOcrLock) {
                    if (mOcrParams == null) {
                        mOcrLock.wait()
                    }

                    if (!mThreadRunning) return

                    // Local capture to ensure thread safety
                    val ocrParams = mOcrParams
                    // Safely unwrap the recognizer (we know it's not null due to safeInitClient)
                    val recognizer = mTextRecognizer

                    if (ocrParams != null && recognizer != null) {
                        Log.d(TAG, "Processing OCR with params $ocrParams")
                        val startTime = System.currentTimeMillis()
                        mCaptureWindow?.showLoadingAnimation()

                        try {
                            // 2. Prepare the InputImage
                            val image = InputImage.fromBitmap(ocrParams.bitmap, 0)

                            // 3. Process blocking (Tasks.await is safe here in background thread)
                            val result = Tasks.await(recognizer.process(image), 10, TimeUnit.SECONDS)

                            val displayData = getDisplayData(ocrParams, result)
                            processDisplayData(displayData)

                            if (displayData.text.isNotEmpty()) {
                                val ocrTime = System.currentTimeMillis() - startTime
                                sendOcrResultToContext(OcrResult(displayData, ocrTime))
                            } else {
                                sendToastToContext("No Characters Recognized.")
                            }
                        } catch (e: Exception) {
                            when (e) {
                                is ExecutionException, is InterruptedException, is TimeoutException -> {
                                    Log.e(TAG, "OCR failed", e)
                                    sendToastToContext("OCR Failed: ${e.message}")
                                }
                                else -> {
                                    Log.e(TAG, "Unexpected OCR Error", e)
                                    // Don't throw; just log so the thread keeps living
                                }
                            }
                        } finally {
                            mCaptureWindow?.stopLoadingAnimation(ocrParams.instantMode)
                            mOcrParams = null
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "OcrRunnable interrupted, shutting down.")
                mThreadRunning = false
            } catch (e: Exception) {
                // Catch global exceptions to keep the thread alive
                Log.e(TAG, "General Error in OcrRunnable Loop", e)
                e.printStackTrace()
            }
        }

        Log.d(TAG, "THREAD STOPPED")
        mTextRecognizer?.close()
    }

    fun runTess(ocrParams: OcrParams) {
        synchronized(mOcrLock) {
            if (!mThreadRunning) return
            mOcrParams = ocrParams
            mOcrLock.notify()
            Log.d(TAG, "NOTIFIED")
        }
    }

    fun cancel() {
        Log.d(TAG, "CANCELED (Not implemented for ML Kit)")
    }

    override fun stop() {
        synchronized(mOcrLock) {
            mThreadRunning = false
            mCaptureWindow = null
            mOcrParams = null
            mOcrLock.notify()
        }
    }

    private fun processDisplayData(displayData: DisplayDataOcr) {
        val squareChars = displayData.squareChars.filterIsInstance<SquareCharOcr>()
        for (squareChar in squareChars) {
            mSimilarChars[squareChar.char]?.forEach {
                squareChar.addChoice(it, ChoiceCertainty.UNCERTAIN)
            }
        }

        for (squareChar in squareChars) {
            correctCommonMistake(squareChar, "く")
            correctCommonMistake(squareChar, "し")
            correctCommonMistake(squareChar, "じ")
            correctCommonMistake(squareChar, "え")
            correctCommonMistake(squareChar, "、")
            correctCommonMistake(squareChar, "。")
            correctKanjiOne(squareChar)
            correctKatakanaDash(squareChar)
        }
    }

    private fun correctCommonMistake(squareChar: SquareCharOcr, char: String) {
        if (mCommonMistakes[squareChar.char] == char) {
            val prev = squareChar.prev
            val next = squareChar.next
            if (prev?.char?.let { LangUtils.IsJapaneseChar(it[0]) } == true ||
                next?.char?.let { LangUtils.IsJapaneseChar(it[0]) } == true) {
                squareChar.addChoice(char, ChoiceCertainty.CERTAIN)
            }
        }
    }

    private fun correctKatakanaDash(squareChar: SquareCharOcr) {
        if (mCommonMistakes[squareChar.char] != null) {
            val prev = squareChar.prev
            if (prev?.char?.let { LangUtils.IsKatakana(it[0]) } == true) {
                squareChar.addChoice("ー", ChoiceCertainty.CERTAIN)
            }
        }
    }

    private fun correctKanjiOne(squareChar: SquareCharOcr) {
        if (mCommonMistakes[squareChar.char] != null) {
            val next = squareChar.next
            if (next?.char?.let { LangUtils.IsKanji(it[0]) || LangUtils.IsHiragana(it[0]) } == true) {
                squareChar.addChoice("一", ChoiceCertainty.CERTAIN)
            }
        }
    }

    private fun getDisplayData(ocrParams: OcrParams, visionText: Text): DisplayDataOcr {
        val bitmap = ocrParams.originalBitmap
        val boxParams = ocrParams.box
        val ocrChars = ArrayList<SquareCharOcr>()
        val displayData = DisplayDataOcr(bitmap, boxParams, ocrParams.instantMode, ocrChars)

        // 1. Sort the BLOCKS, not the individual symbols.
        // This preserves the internal Left-to-Right order of horizontal text,
        // while still ordering the blocks themselves in Japanese/Manga order (Right-to-Left, Top-to-Bottom).
        val sortedBlocks = visionText.textBlocks.sortedWith(Comparator { a, b ->
            val rectA = a.boundingBox ?: return@Comparator 0
            val rectB = b.boundingBox ?: return@Comparator 0

            // If blocks are vertically aligned (similar X coordinates), sort Top-to-Bottom.
            // If they are horizontally separated, sort Right-to-Left (Manga style).
            val threshold = 20 

            if (Math.abs(rectA.left - rectB.left) > threshold) {
                // Right to Left (Manga Columns)
                rectB.left - rectA.left
            } else {
                // Top to Bottom (Standard Rows)
                rectA.top - rectB.top
            }
        })

        // 2. Iterate through hierarchy to collect symbols in the correct order
        for (block in sortedBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    for (symbol in element.symbols) {
                        
                        val choices = ArrayList<Pair<String, Double>>()
                        choices.add(Pair(symbol.text, symbol.confidence.toDouble()))

                        val pos = symbol.boundingBox ?: android.graphics.Rect()

                        // Calculate relative position to the original full-screen bitmap
                        ocrChars.add(SquareCharOcr(
                            displayData,
                            choices,
                            intArrayOf(
                                pos.left + ocrParams.offsetX, 
                                pos.top + ocrParams.offsetY, 
                                pos.right + ocrParams.offsetX, 
                                pos.bottom + ocrParams.offsetY
                            )
                        ))
                    }
                }
            }
        }

        displayData.assignIndicies()
        return displayData
    }

    private fun loadSimilarChars(): Map<String, List<String>> {
        val similarChars = HashMap<String, MutableList<String>>()
        for (list in OcrCorrection.CommonLookalikes) {
            for ((index, kana) in list.withIndex()) {
                if (list.size <= 1) continue

                val others = list.toMutableList().apply { removeAt(index) }
                val existing = similarChars.getOrPut(kana) { mutableListOf() }
                others.forEach {
                    if (!existing.contains(it)) {
                        existing.add(it)
                    }
                }
            }
        }
        return similarChars
    }

    private fun loadCommonMistakes(): HashMap<String, String> {
        val commonMistakes = HashMap<String, String>()
        for (pair in OcrCorrection.CommonMistakes) {
            for (c in pair.first) {
                commonMistakes[c] = pair.second
            }
        }
        return commonMistakes
    }

    private fun sendOcrResultToContext(result: OcrResult) {
        Message.obtain(mContext.handler, 0, result).sendToTarget()
    }

    private fun sendToastToContext(message: String) {
        Message.obtain(mContext.handler, 0, message).sendToTarget()
    }

    @Throws(FileNotFoundException::class)
    private fun saveBitmap(bitmap: Bitmap, name: String = "screen") {
        try {
            val fs = "${mContext.filesDir.absolutePath}/$SCREENSHOT_FOLDER_NAME/${name}_${System.nanoTime()}.png"
            Log.d(TAG, fs)
            FileOutputStream(fs).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch(e: Exception) {
            Log.e(TAG, "Failed to save bitmap", e)
        }
    }

    companion object {
        private val TAG = OcrRunnable::class.java.name
        const val ACTION_MODEL_READY = "ca.fuwafuwa.gaku.MODEL_READY"
    }
}




