package com.example.smartledger.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.R
import com.example.smartledger.adapter.MilkDailyAdapter
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.MilkRecord
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewMilkActivity : AppCompatActivity() {

    private var record: MilkRecord? = null
    private lateinit var adapter: MilkDailyAdapter
    private lateinit var tvTotalLiters: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvPriceInfo: TextView
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Debounce Job (Waiting for user to stop typing)
    private var saveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_milk)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        record = intent.getSerializableExtra("milk_data") as? MilkRecord
        if (record == null) { finish(); return }

        setupUI()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = record!!.monthName
        toolbar.setNavigationOnClickListener { finish() }

        val btnInfo = findViewById<ImageView>(R.id.btnInfoHeader)
        val btnDelete = findViewById<ImageView>(R.id.btnDeleteHeader)
        tvPriceInfo = findViewById(R.id.tvPriceInfo)
        btnInfo.setOnClickListener {
            // Show the Price
            tvPriceInfo.text = "Current Rate: Rs ${record!!.pricePerLiter} / Liter"
            tvPriceInfo.visibility = View.VISIBLE

            // Auto-hide after 3 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                tvPriceInfo.visibility = View.GONE
            }, 3000)
        }

        // 2. Delete Button Click
        btnDelete.setOnClickListener {
            showDeleteDialog()
        }

        tvTotalLiters = findViewById(R.id.tvTotalLiters)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)

        updateSummaryUI() // Initial calculation

        val rv = findViewById<RecyclerView>(R.id.rvDailyEntries)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = MilkDailyAdapter(record!!.dailyEntries, record!!.pricePerLiter) {
            // This block runs every time the user types a number
            onRecordChanged()
        }
        rv.adapter = adapter
    }

    private fun showDeleteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val tvDetailTitle = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

        tvTitle.text = "Delete Record?"
        tvMessage.text = "Move this month's record to Trash?"
        btnConfirm.text = "Delete"

        containerDetails.visibility = View.VISIBLE
        tvDetailTitle.text = record!!.monthName
        tvDetailAmount.text = "Rs ${record!!.totalAmount.toInt()}"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                // Delete logic in background
                withContext(Dispatchers.IO) {
                    db.milkDao().softDelete(listOf(record!!.id), System.currentTimeMillis())
                }
                Toast.makeText(this@ViewMilkActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                finish() // Close screen
            }
        }
        dialog.show()
    }

    private fun updateSummaryUI() {
        var l = 0.0
        var amt = 0.0
        record!!.dailyEntries.forEach {
            l += it.liters
            amt += (it.liters * record!!.pricePerLiter)
        }
        tvTotalLiters.text = String.format("%.1f", l)
        tvTotalAmount.text = "Rs ${amt.toInt()}"
    }

    private fun onRecordChanged() {
        // 1. Immediate UI Update (Totals)
        updateSummaryUI()

        // 3. Cancel previous save job if user is still typing
        saveJob?.cancel()

        // 4. Start new save job (Wait 1 second)
        saveJob = lifecycleScope.launch {
            delay(1000) // Wait for 1 second of inactivity

            // Recalculate final data
            var finalLiters = 0.0
            record!!.dailyEntries.forEach { finalLiters += it.liters }
            val finalAmount = finalLiters * record!!.pricePerLiter

            // Create updated object
            val updatedRecord = record!!.copy(
                totalLiters = finalLiters,
                totalAmount = finalAmount
            )

            // A. Save Local (Fast)
            withContext(Dispatchers.IO) {
                db.milkDao().update(updatedRecord)
            }
            record = updatedRecord // Update memory reference
        }
    }
}