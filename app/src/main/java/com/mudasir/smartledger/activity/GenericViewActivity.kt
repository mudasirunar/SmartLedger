package com.mudasir.smartledger.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.data.CustomEntry
import com.mudasir.smartledger.data.CustomLedger
import kotlin.getValue
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.DateMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenericViewActivity : AppCompatActivity() {

    private lateinit var containerDetails: LinearLayout
    private lateinit var tvAmount: TextView
    private lateinit var rvPhotos: RecyclerView
    private lateinit var tvPhotoLabel: TextView
    private var entry: CustomEntry? = null
    private var ledger: CustomLedger? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_generic_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        entry = intent.getSerializableExtra("entry_data") as? CustomEntry
        ledger = intent.getSerializableExtra("ledger_template") as? CustomLedger

        if (entry == null || ledger == null) {
            finish()
            return
        }

        initUI()
        displayRecordDetails()
    }

    private fun initUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val ledgerName = ledger?.name ?: "Record"
        toolbar.title = "$ledgerName Details"
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon?.setTint(getColor(R.color.white))

        findViewById<ImageView>(R.id.btnDeleteHeader).setOnClickListener { showDeleteDialog() }

        containerDetails = findViewById(R.id.containerDetails)
        tvAmount = findViewById(R.id.tvViewAmount)
        rvPhotos = findViewById(R.id.rvPhotos)
        tvPhotoLabel = findViewById(R.id.tvPhotoLabel)

        findViewById<Button>(R.id.btnEdit).setOnClickListener {
            val intent = Intent(this, GenericAddEditActivity::class.java)
            intent.putExtra("ledger_template", ledger)
            intent.putExtra("existing_entry", entry)
            editLauncher.launch(intent)
        }
    }

    private fun displayRecordDetails() {
        val sdfSingle = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val containerDateSection = findViewById<LinearLayout>(R.id.containerDateSection)
        containerDateSection.removeAllViews()

        tvAmount.text = "Rs %.2f".format(entry!!.amount ?: 0.0)

        when (ledger?.dateMode) {
            DateMode.RANGE -> {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val dataMap: Map<String, String> = Gson().fromJson(entry!!.dataJson, type)
                val endStr = dataMap["SYS_END_DATE"]?.toLongOrNull()?.let { sdfSingle.format(Date(it)) } ?: "-"

                containerDateSection.addView(createDateColumn("Start Date", sdfSingle.format(Date(entry!!.date))))
                containerDateSection.addView(createDateColumn("End Date", endStr))
            }
            DateMode.MONTH -> {
                containerDateSection.addView(createDateColumn("Month", sdfMonth.format(Date(entry!!.date))))
            }
            else -> {
                containerDateSection.addView(createDateColumn("Date", sdfSingle.format(Date(entry!!.date))))
            }
        }

        val type = object : TypeToken<Map<String, String>>() {}.type
        val dataMap: Map<String, String> = Gson().fromJson(entry!!.dataJson, type)

        containerDetails.removeAllViews()
        ledger?.fields?.forEach { field ->
            val value = dataMap[field.fieldName] ?: "-"
            addStyledDetail(field.fieldName, value)
        }

        val images = entry?.imagePaths ?: emptyList()
        if (images.isEmpty()) {
            tvPhotoLabel.visibility = View.GONE
            rvPhotos.visibility = View.GONE
        } else {
            tvPhotoLabel.visibility = View.VISIBLE
            rvPhotos.visibility = View.VISIBLE
            rvPhotos.layoutManager = GridLayoutManager(this, 2)
            rvPhotos.adapter = com.mudasir.smartledger.adapter.PhotoViewAdapter(images) { path ->
                val intent = Intent(this, ImageViewerActivity::class.java)
                intent.putStringArrayListExtra("image_paths", ArrayList(images))
                intent.putExtra("position", images.indexOf(path))
                startActivity(intent)
            }
        }
    }

    private fun createDateColumn(label: String, value: String): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val header = TextView(this).apply {
            text = label
            setTextAppearance(R.style.DetailHeader)
        }
        val valText = TextView(this).apply {
            text = value
            setTextAppearance(R.style.DetailValue)
        }
        layout.addView(header)
        layout.addView(valText)
        return layout
    }

    private fun addStyledDetail(label: String, value: String) {
        val labelTv = TextView(this).apply {
            text = label
            setTextAppearance(R.style.DetailHeader)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 16.dpToPx() }
        }
        val valueTv = TextView(this).apply {
            text = value
            setTextAppearance(R.style.DetailValue)
        }
        containerDetails.addView(labelTv)
        containerDetails.addView(valueTv)
    }

    private fun showDeleteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val type = object : TypeToken<Map<String, String>>() {}.type
        val dataMap: Map<String, String>? = try {
            Gson().fromJson(entry!!.dataJson, type)
        } catch (e: Exception) { null }

        val firstFieldValue = dataMap?.values?.firstOrNull()
        val displayTitle = if (!firstFieldValue.isNullOrBlank()) firstFieldValue else ledger?.name ?: "Record"

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Delete Record?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "Move this to Trash?"
        dialogView.findViewById<View>(R.id.containerDetails).visibility = View.VISIBLE

        dialogView.findViewById<TextView>(R.id.tvDetailTitle).text = displayTitle
        dialogView.findViewById<TextView>(R.id.tvDetailAmount).text = "Rs ${entry!!.amount ?: 0.0}"

        dialogView.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnDialogConfirm).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                db.customLedgerDao().softDeleteEntries(listOf(entry!!.id), System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GenericViewActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        dialog.show()
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()
}