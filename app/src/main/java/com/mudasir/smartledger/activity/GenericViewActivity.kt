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
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.CustomEntry
import com.mudasir.smartledger.data.CustomLedger
import com.mudasir.smartledger.data.DateMode
import com.mudasir.smartledger.util.DialogHelper
import com.mudasir.smartledger.util.PhotoGridHelper
import com.mudasir.smartledger.util.applySystemBarPadding
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
            val message = result.data?.getStringExtra("toast_message") ?: "Record Updated"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            entry?.id?.let { id ->
                lifecycleScope.launch {
                    val updated = withContext(Dispatchers.IO) { db.customLedgerDao().getEntryById(id) }
                    if (updated != null) {
                        entry = updated
                        displayRecordDetails()
                    }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_generic_view)

        findViewById<View>(R.id.main).applySystemBarPadding(includeIme = true)

        entry = intent.getSerializableExtraCompat<CustomEntry>("entry_data")
        ledger = intent.getSerializableExtraCompat<CustomLedger>("ledger_template")

        if (entry == null) {
            finish()
            return
        }

        if (ledger == null) {
            val lId = intent.getIntExtra("ledger_id", entry?.ledgerId ?: 0)
            if (lId != 0) {
                lifecycleScope.launch {
                    ledger = withContext(Dispatchers.IO) { db.customLedgerDao().getLedgerById(lId) }
                    if (ledger == null) {
                        finish()
                    } else {
                        initUI()
                        displayRecordDetails()
                    }
                }
            } else {
                finish()
            }
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
            val intent = Intent(this, GenericAddEditActivity::class.java).apply {
                putExtra("ledger_template", ledger)
                putExtra("ledger_id", ledger?.id ?: 0)
                putExtra("existing_entry", entry)
            }
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

        PhotoGridHelper.setupPhotoGrid(
            context = this,
            recyclerView = rvPhotos,
            headerView = tvPhotoLabel,
            images = entry?.imagePaths ?: emptyList()
        )
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
        val type = object : TypeToken<Map<String, String>>() {}.type
        val dataMap: Map<String, String>? = try {
            Gson().fromJson(entry!!.dataJson, type)
        } catch (e: Exception) { null }

        val firstFieldValue = dataMap?.values?.firstOrNull()
        val displayTitle = if (!firstFieldValue.isNullOrBlank()) firstFieldValue else ledger?.name ?: "Record"

        DialogHelper.createConfirmationDialog(
            context = this,
            title = "Delete Record?",
            message = "Move this to Trash?"
        ) { views ->
            views.btnConfirm.text = "Delete"
            views.details.visibility = View.VISIBLE
            views.detailTitle.text = displayTitle
            views.detailAmount.text = "Rs ${entry!!.amount ?: 0.0}"

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                views.dialog.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    db.customLedgerDao().softDeleteEntries(listOf(entry!!.id), System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@GenericViewActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }.show()
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private inline fun <reified T : java.io.Serializable> Intent.getSerializableExtraCompat(key: String): T? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(key, T::class.java)
        } else {
            getSerializableExtra(key) as? T
        }
    }
}