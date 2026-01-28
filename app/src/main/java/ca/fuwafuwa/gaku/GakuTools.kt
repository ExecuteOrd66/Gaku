@file:JvmName("GakuTools")

package ca.fuwafuwa.gaku

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast

import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

import java.util.ArrayList

private const val TAG = "GakuTools"
private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create()

enum class TextDirection(val value: Int) {
    AUTO(0),
    HORIZONTAL(1),
    VERTICAL(2);

    companion object {
        private val values = values();
        fun getByValue(value: Int) = values.firstOrNull { it.value == value }
    }
}

data class Prefs(
    val textDirectionSetting: TextDirection,
    val imageFilterSetting: Boolean,
    val instantModeSetting: Boolean,
    val showHideSetting: Boolean,
    val snapEnabled: Boolean,          // New
    val showPresetBar: Boolean,       // New
    val borderThickness: Int,         // New
    val borderColor: String           // New
)

fun getPrefs(context: Context): Prefs {
    val prefs = context.getSharedPreferences(GAKU_PREF_FILE, Context.MODE_PRIVATE)
    val defaultPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

    return Prefs(
        TextDirection.valueOf(prefs.getString(GAKU_PREF_TEXT_DIRECTION, TextDirection.AUTO.toString())!!),
        prefs.getBoolean(GAKU_PREF_IMAGE_FILTER, true),
        prefs.getBoolean(GAKU_PREF_INSTANT_MODE, true),
        prefs.getBoolean(GAKU_PREF_SHOW_HIDE, true),
        defaultPrefs.getBoolean(GAKU_PREF_SNAP_ENABLED, true),
        defaultPrefs.getBoolean(GAKU_PREF_SHOW_PRESET_BAR, true),
        defaultPrefs.getString(GAKU_PREF_BORDER_THICKNESS, "2")!!.toInt(),
        defaultPrefs.getString(GAKU_PREF_BORDER_COLOR, "#0064ff")!!
    )
}


fun toJson(obj: Any): String
{
    return gson.toJson(obj)
}

fun dpToPx(context: Context, dp: Int): Int
{
    val displayMetrics = context.resources.displayMetrics
    return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
}

fun pxToDp(context: Context, px: Int): Int
{
    val displayMetrics = context.resources.displayMetrics
    return Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
}

/**
 * Splits `text` into individual unicode characters as a list of strings
 * @param text Text to split
 * @return List of strings with each string representing one unicode character
 */
fun splitTextByChar(text: String): List<String>
{
    val charList = ArrayList<String>()

    val length = text.length
    var offset = 0
    while (offset < length)
    {
        val curr = text.codePointAt(offset)
        val charz = String(intArrayOf(curr), 0, 1)
        charList.add(charz)
        offset += Character.charCount(curr)
    }

    return charList
}

fun startGakuService(context: Context, i: Intent)
{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
        context.startForegroundService(i)
    }
    else
    {
        context.startService(i)
    }
}

fun setupGakuDatabasesAndFiles(context: Context)
{
    try {
        val filesAndPaths = hashMapOf(
                JMDICT_DATABASE_NAME to context.filesDir.absolutePath,
                JM_DICT_FURIGANA_DATABASE_NAME to context.filesDir.absolutePath,
                TESS_DATA_NAME to "${context.filesDir.absolutePath}/$TESS_FOLDER_NAME")

        if (shouldResetData(filesAndPaths))
        {
            Log.d(TAG, "Resetting Data")
            for (fileAndPath in filesAndPaths){
                File("${fileAndPath.value}/${fileAndPath.key}").delete()
            }
        }

        copyFilesIfNotExists(context, filesAndPaths)

        var screenshotPath: String = context.filesDir.absolutePath + "/$SCREENSHOT_FOLDER_NAME"
        createDirIfNotExists(screenshotPath)
        deleteScreenshotsOlderThanOneDay(screenshotPath)
    }
    catch (e: Exception)
    {
        Toast.makeText(context, "Unable to setup Gaku database", Toast.LENGTH_LONG).show()
        return
    }
}

fun shouldResetData(filesAndPaths: Map<String, String>) : Boolean
{
    for (fileAndPath in filesAndPaths){
        if (!File("${fileAndPath.value}/${fileAndPath.key}").exists()) return true
    }
    return false
}

fun resetGakuDatabases(context: Context) {
    try {
        val filesAndPaths = hashMapOf(
            JMDICT_DATABASE_NAME to context.filesDir.absolutePath,
            JM_DICT_FURIGANA_DATABASE_NAME to context.filesDir.absolutePath,
            TESS_DATA_NAME to "${context.filesDir.absolutePath}/$TESS_FOLDER_NAME"
        )
        
        for (fileAndPath in filesAndPaths) {
            val file = File("${fileAndPath.value}/${fileAndPath.key}")
            if (file.exists()) {
                file.delete()
            }
        }
        
        copyFilesIfNotExists(context, filesAndPaths)
    } catch (e: Exception) {
        Log.e(TAG, "Error resetting databases", e)
    }
}

fun createDirIfNotExists(path: String)
{
    val dir = File(path)
    if (!dir.exists())
    {
        dir.mkdirs()
    }
}

fun copyFilesIfNotExists(context: Context, filesAndPaths: Map<String, String>)
{
    for (fileAndPath in filesAndPaths)
    {
        val path = fileAndPath.value
        val fileName = fileAndPath.key
        val filePath = "$path/$fileName"

        if (File(filePath).exists())
        {
            continue
        }

        createDirIfNotExists(path)

        try {
            val input = context.assets.open(fileName)
            val output = FileOutputStream(filePath)

            input.copyTo(output);
            output.close()
            Log.d(TAG, "Copied $filePath")
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "Asset $fileName not found, skipping copy.")
        }
    }
}

fun deleteScreenshotsOlderThanOneDay(path: String)
{
    try {
        var dir = File(path)
        if (dir.exists())
        {
            Log.d(TAG, dir.absolutePath)
            var listFileNames = dir.list()
            var purgeTime = System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000
            for (fileName in listFileNames)
            {
                val file = File(fileName)
                if (file.isFile && file.lastModified() < purgeTime)
                {
                    file.delete()
                }
            }
        }
    }
    catch (e: Exception)
    {
        Log.d(TAG, e.toString())
    }
}
