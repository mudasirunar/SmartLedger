package com.mudasir.smartledger.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.CustomDailyAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.CustomDailyRecord
import com.mudasir.smartledger.data.CustomLedger
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomDailyActivity : AppCompatActivity() {

    private lateinit var ledger: CustomLedger
    private var record: CustomDailyRecord? = null
    private lateinit var adapter: CustomDailyAdapter
    private lateinit var tvPriceInfo: TextView
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var saveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_daily)

        ledger = intent.getSerializableExtra("ledger_template") as CustomLedger
        record = intent.getSerializableExtra("daily_record") as? CustomDailyRecord

        setupInsets()
        setupUI()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, maxOf(systemBars.bottom, imeInsets.bottom))
            insets
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = record?.monthName ?: ledger.name
        toolbar.setNavigationOnClickListener { finish() }
        tvPriceInfo = findViewById(R.id.tvPriceInfo)
        findViewById<ImageView>(R.id.btnInfoHeader).setOnClickListener {
            val pricing = getEffectivePricing()
            val rateText = "Rate: " + ledger.fields.joinToString(", ") { field ->
                "${getPriceForField(field.fieldName, pricing).toInt()}/${field.fieldName}"
            }
            tvPriceInfo.text = rateText
            tvPriceInfo.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({ tvPriceInfo.visibility = View.GONE }, 3000)
        }

        findViewById<ImageView>(R.id.btnDeleteHeader).setOnClickListener { showDeleteDialog() }

        val headers = listOf(
            findViewById<TextView>(R.id.tvHeader1),
            findViewById(R.id.tvHeader2),
            findViewById(R.id.tvHeader3)
        )
        ledger.fields.forEachIndexed { index, field ->
            if (index < headers.size) {
                headers[index].text = field.fieldName
                headers[index].visibility = View.VISIBLE
            }
        }


        val pricing = getEffectivePricing()
        val simplePricingMap = ledger.fields.associate { field ->
            field.fieldName to getPriceForField(field.fieldName, pricing)
        }

        val rv = findViewById<RecyclerView>(R.id.rvDailyEntries)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = CustomDailyAdapter(ledger, record?.dailyEntries ?: emptyList(), simplePricingMap) {
            onRecordChanged()
        }
        rv.adapter = adapter
        updateTotalSummary()
    }


    private fun showDeleteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Delete Record?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "Move this month's record to Trash?"
        dialogView.findViewById<View>(R.id.containerDetails).visibility = View.VISIBLE
        dialogView.findViewById<TextView>(R.id.tvDetailTitle).text = record?.monthName
        dialogView.findViewById<TextView>(R.id.tvDetailAmount).text = "Rs ${record?.totalAmount?.toInt() ?: 0}"

        dialogView.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnDialogConfirm).setOnClickListener {
            dialog.dismiss()
            record?.let { r ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.customLedgerDao().softDeleteDailyRecords(listOf(r.id), System.currentTimeMillis())
                    }
                    Toast.makeText(this@CustomDailyActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        dialog.show()
    }

    // Replace the helper function
    private fun getEffectivePricing(): Map<String, Double> {
        return try {
            Gson().fromJson(record?.pricingJson, object : TypeToken<Map<String, Double>>() {}.type)
        } catch (e: Exception) { emptyMap() }
    }

    private fun getPriceForField(fieldName: String, pricing: Map<String, Double>): Double {
        return pricing["MASTER_GLOBAL_PRICE"] ?: pricing[fieldName] ?: 0.0
    }

    private fun updateTotalSummary() {
        val pricing = getEffectivePricing()
        var totalQty = 0.0
        var totalAmount = 0.0
        adapter.dailyEntries.forEach { entry ->
            ledger.fields.forEachIndexed { index, field ->
                val qty = entry.values.getOrElse(index) { 0.0 }
                totalQty += qty
                totalAmount += qty * getPriceForField(field.fieldName, pricing)
            }
        }
        findViewById<TextView>(R.id.tvTotalQuantity).text = totalQty.toString()
        findViewById<TextView>(R.id.tvTotalAmount).text = "Rs ${totalAmount.toInt()}"
    }

    private fun calculateOnlyAmount(): Double {
        val pricing = getEffectivePricing()
        var total = 0.0
        adapter.dailyEntries.forEach { entry ->
            ledger.fields.forEachIndexed { index, field ->
                val qty = entry.values.getOrElse(index) { 0.0 }
                total += qty * getPriceForField(field.fieldName, pricing)
            }
        }
        return total
    }

    private fun onRecordChanged() {
        updateTotalSummary()

        val totalAmountForDb = calculateOnlyAmount()

        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(1000)
            record?.let {
                val updatedRecord = it.copy(
                    dailyEntries = adapter.dailyEntries,
                    totalAmount = totalAmountForDb
                )
                withContext(Dispatchers.IO) {
                    db.customLedgerDao().updateDailyRecord(updatedRecord)
                }
                record = updatedRecord
            }
        }
    }

}