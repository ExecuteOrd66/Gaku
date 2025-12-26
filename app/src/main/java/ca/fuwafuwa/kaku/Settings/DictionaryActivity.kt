package ca.fuwafuwa.gaku.Settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.fuwafuwa.gaku.Database.Yomitan.YomitanDatabaseHelper
import ca.fuwafuwa.gaku.Database.Yomitan.YomitanImporter
import ca.fuwafuwa.gaku.R
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.concurrent.thread
import ca.fuwafuwa.gaku.Database.Yomitan.YomitanDictionary

class DictionaryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        recyclerView = findViewById(R.id.dict_list)
        progressBar = findViewById(R.id.import_progress)
        val btnAdd = findViewById<FloatingActionButton>(R.id.btn_add_dict)

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        btnAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/zip"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, 1001)
        }

        refreshList()
    }

    private fun refreshList() {
        val db = YomitanDatabaseHelper.getInstance(this)
        val dicts = db.dictionaryDao.queryForAll()
        recyclerView.adapter = DictionaryAdapter(dicts) { dict ->
            // Optional: Handle delete
            Toast.makeText(this, "Long press to delete (not implemented)", Toast.LENGTH_SHORT).show()
        }
    }

    class DictionaryAdapter(private val dicts: List<YomitanDictionary>, private val onClick: (YomitanDictionary) -> Unit) :
        RecyclerView.Adapter<DictionaryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(android.R.id.text1)
            val infoText: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val dict = dicts[position]
            holder.titleText.text = dict.title
            holder.infoText.text = "Author: ${dict.author} | Rev: ${dict.revision}"
            holder.itemView.setOnClickListener { onClick(dict) }
        }

        override fun getItemCount() = dicts.size
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            startImport(uri)
        }
    }

    private fun startImport(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        
        thread {
            try {
                YomitanImporter(this).importFromZip(uri) { msg, progress ->
                    runOnUiThread {
                        progressBar.progress = progress
                        if (progress % 10 == 0) {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Import Complete!", Toast.LENGTH_LONG).show()
                    refreshList()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Import Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
