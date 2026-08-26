package com.mudasir.smartledger.activity

import android.app.NotificationManager
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
import androidx.work.WorkManager
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.MilkDailyAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.MilkRecord
import com.mudasir.smartledger.util.DialogHelper
import com.mudasir.smartledger.util.MilkNotificationConstants
import com.mudasir.smartledger.util.applySystemBarPadding
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ViewMilkActivity : AppCompatActivity() {

    private var record: MilkRecord? = null
    private lateinit var adapter: MilkDailyAdapter
    private lateinit var tvTotalLiters: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvPriceInfo: TextView
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_milk)

        findViewById<View>(R.id.main).applySystemBarPadding(includeIme = true)

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
            tvPriceInfo.text = "Current Rate: Rs ${record!!.pricePerLiter} / Liter"
            tvPriceInfo.visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                tvPriceInfo.visibility = View.GONE
            }, 3000)
        }

        btnDelete.setOnClickListener {
            showDeleteDialog()
        }

        tvTotalLiters = findViewById(R.id.tvTotalLiters)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)

        updateSummaryUI()

        val rv = findViewById<RecyclerView>(R.id.rvDailyEntries)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = MilkDailyAdapter(record!!.dailyEntries, record!!.pricePerLiter) {
            onRecordChanged()
        }
        rv.adapter = adapter
    }

    private fun showDeleteDialog() {
        DialogHelper.createConfirmationDialog(
            context = this,
            title = "Delete Record?",
            message = "Move this month's record to Trash?"
        ) { views ->
            views.btnConfirm.text = "Delete"
            views.details.visibility = View.VISIBLE
            views.detailTitle.text = record!!.monthName
            views.detailAmount.text = "Rs ${record!!.totalAmount.toInt()}"

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                views.dialog.dismiss()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.milkDao().softDelete(listOf(record!!.id), System.currentTimeMillis())
                    }
                    Toast.makeText(this@ViewMilkActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.show()
    }

    private fun updateSummaryUI() {
        var l = 0.0
        var amt = 0.0
        record!!.dailyEntries.forEach {
            val qty = it.liters ?: 0.0
            l += qty
            amt += (qty * record!!.pricePerLiter)
        }
        tvTotalLiters.text = String.format("%.1f", l)
        tvTotalAmount.text = "Rs ${amt.toInt()}"
    }

    private fun onRecordChanged() {
        updateSummaryUI()

        var finalLiters = 0.0
        record!!.dailyEntries.forEach { finalLiters += (it.liters ?: 0.0) }
        val finalAmount = finalLiters * record!!.pricePerLiter

        val updatedRecord = record!!.copy(
            totalLiters = finalLiters,
            totalAmount = finalAmount
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.milkDao().update(updatedRecord)
                val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                val todayEntry = updatedRecord.dailyEntries.find { it.day == today }
                if (todayEntry != null && (todayEntry.liters ?: 0.0) > 0.0) {
                    WorkManager.getInstance(applicationContext)
                        .cancelUniqueWork(MilkNotificationConstants.WORK_NAME_MIDNIGHT)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.cancel(MilkNotificationConstants.NOTIFICATION_ID)
                }
            }
            record = updatedRecord
        }
    }
}