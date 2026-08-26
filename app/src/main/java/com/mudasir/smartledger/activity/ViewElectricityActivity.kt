package com.mudasir.smartledger.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.PhotoViewAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.Electricity
import com.mudasir.smartledger.util.DialogHelper
import com.mudasir.smartledger.util.PhotoGridHelper
import com.mudasir.smartledger.util.applySystemBarPadding
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ViewElectricityActivity : AppCompatActivity() {

    private var record: Electricity? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val message = result.data?.getStringExtra("toast_message") ?: "Record Updated"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            record?.id?.let { id ->
                lifecycleScope.launch {
                    val updated = withContext(Dispatchers.IO) { db.electricityDao().getById(id) }
                    if (updated != null) {
                        record = updated
                        setupUI()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_electricity)

        findViewById<View>(R.id.main).applySystemBarPadding(includeIme = true)

        record = intent.getSerializableExtra("electricity_data") as? Electricity
        if (record == null) { finish(); return }

        setupUI()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon?.setTint(getColor(R.color.white))

        val btnDelete = findViewById<ImageView>(R.id.btnDeleteHeader)
        btnDelete.setOnClickListener { showDeleteDialog() }

        findViewById<Button>(R.id.btnModify).setOnClickListener {
            val intent = Intent(this, AddEditElectricityActivity::class.java)
            intent.putExtra("electricity_data", record)
            editLauncher.launch(intent)
        }

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        findViewById<TextView>(R.id.tvDetailStartDate).text = sdf.format(record!!.startDate)
        findViewById<TextView>(R.id.tvDetailEndDate).text = sdf.format(record!!.endDate)

        fun fmt(v: Double?): String = if (v == null) "-" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

        findViewById<TextView>(R.id.tvDetailStartUnits).text = fmt(record!!.startUnits)
        findViewById<TextView>(R.id.tvDetailEndUnits).text = fmt(record!!.endUnits)
        findViewById<TextView>(R.id.tvDetailTotalUnits).text = "${fmt(record!!.totalUnits)} Units"
        findViewById<TextView>(R.id.tvDetailAmount).text = "Rs %.2f".format(record!!.amount ?: 0.0)
        val totalUnits = record!!.totalUnits ?: 0.0
        val amount = record!!.amount ?: 0.0

        val unitPrice = if (totalUnits > 0) amount / totalUnits else 0.0
        findViewById<TextView>(R.id.tvDetailUnitPrice).text = "Rs %.2f / Unit".format(unitPrice)

        PhotoGridHelper.setupPhotoGrid(
            context = this,
            recyclerView = findViewById(R.id.detailRvPhotos),
            headerView = findViewById(R.id.detailPhotos),
            images = record?.imagePaths ?: emptyList()
        )
    }

    private fun showDeleteDialog() {
        val totalVal = record!!.totalUnits ?: 0.0
        val unitText = if (totalVal % 1.0 == 0.0) "${totalVal.toInt()} Units" else "$totalVal Units"

        DialogHelper.createConfirmationDialog(
            context = this,
            title = "Delete Record?",
            message = "Move this to Trash?"
        ) { views ->
            views.btnConfirm.text = "Delete"
            views.details.visibility = View.VISIBLE
            views.detailTitle.text = unitText
            views.detailAmount.text = "Rs ${record!!.amount ?: 0.0}"

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                views.dialog.dismiss()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.electricityDao().softDelete(listOf(record!!.id), System.currentTimeMillis())
                    }
                    Toast.makeText(this@ViewElectricityActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.show()
    }
}