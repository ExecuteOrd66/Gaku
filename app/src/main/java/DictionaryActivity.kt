package ca.fuwafuwa.gaku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.fuwafuwa.gaku.data.AppDatabase
import ca.fuwafuwa.gaku.data.dao.DictionarySummary
import ca.fuwafuwa.gaku.data.importer.YomitanImporter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

class DictionaryActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: DictionaryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private val scope = CoroutineScope(Dispatchers.Main)

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importDictionary(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        db = AppDatabase.getDatabase(this)
        progressBar = findViewById(R.id.import_progress)
        statusText = findViewById(R.id.import_status)

        val recycler = findViewById<RecyclerView>(R.id.dict_list)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = DictionaryAdapter { dict -> deleteDictionary(dict.dictionary.id, dict.dictionary.name) }
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.btn_add_dict).setOnClickListener {
            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }

        loadDictionaries()
    }

    private fun loadDictionaries() {
        scope.launch(Dispatchers.IO) {
            val dicts = db.dictionaryDao().getDictionariesWithStats()
            withContext(Dispatchers.Main) {
                adapter.submitList(dicts)
            }
        }
    }

    private fun importDictionary(uri: Uri) {
        val stream = contentResolver.openInputStream(uri) ?: return
        
        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        statusText.text = "Preparing import..."
        progressBar.isIndeterminate = false
        
        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var lastUpdate = 0L

            try {
                val importer = YomitanImporter(db)
                importer.importDictionary(stream) { progress ->
                    val now = System.currentTimeMillis()
                    // Update UI every 100ms max to prevent stutter
                    if (now - lastUpdate > 100 || progress.currentFileIndex == progress.totalFiles) {
                        lastUpdate = now
                        val processed = progress.currentFileIndex
                        val total = progress.totalFiles
                        
                        val elapsedTime = now - startTime
                        val eta = if (processed > 0) {
                            val timePerFile = elapsedTime / processed.toDouble()
                            val remainingFiles = total - processed
                            (timePerFile * remainingFiles).toLong()
                        } else 0L

                        val etaSec = eta / 1000
                        val etaStr = if (etaSec > 60) "${etaSec / 60}m ${etaSec % 60}s" else "${etaSec}s"

                        runOnUiThread {
                            progressBar.max = total
                            progressBar.progress = processed
                            statusText.text = "Importing: ${progress.fileName}\n(${processed}/${total}) - ETA: $etaStr"
                        }
                    }
                }
                loadDictionaries()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DictionaryActivity, "Import Successful", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DictionaryActivity, "Import Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.visibility = View.GONE
                }
            }
        }
    }

    private fun deleteDictionary(id: Long, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Dictionary")
            .setMessage("Are you sure you want to delete '$name'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    db.dictionaryDao().deleteDictionary(id)
                    loadDictionaries()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class DictionaryAdapter(
    private val onDelete: (DictionarySummary) -> Unit
) : RecyclerView.Adapter<DictionaryAdapter.ViewHolder>() {

    private var list = listOf<DictionarySummary>()

    fun submitList(newList: List<DictionarySummary>) {
        list = newList
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.dict_name)
        val meta: TextView = view.findViewById(R.id.dict_meta)
        val delete: ImageButton = view.findViewById(R.id.btn_delete_dict)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dictionary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.name.text = item.dictionary.name
        
        val numberFormat = NumberFormat.getInstance()
        val terms = numberFormat.format(item.termCount)
        val kanji = numberFormat.format(item.kanjiCount)
        
        holder.meta.text = "Rev: ${item.dictionary.revision} | $terms Terms | $kanji Kanji"
        holder.delete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = list.size
}