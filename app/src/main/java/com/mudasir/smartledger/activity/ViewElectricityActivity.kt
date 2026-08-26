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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        val rvPhotos = findViewById<RecyclerView>(R.id.detailRvPhotos)
        val tvPhotoHeader = findViewById<View>(R.id.detailPhotos)
        val images = record?.imagePaths ?: emptyList()

        if (images.isEmpty()) {
            tvPhotoHeader.visibility = View.GONE
            rvPhotos.visibility = View.GONE
        } else {
            tvPhotoHeader.visibility = View.VISIBLE
            rvPhotos.visibility = View.VISIBLE

            rvPhotos.apply {
                setHasFixedSize(true)
                layoutManager = GridLayoutManager(this@ViewElectricityActivity, 2)
                adapter = PhotoViewAdapter(images) { imagePath ->
                    val pos = images.indexOf(imagePath)
                    val intent = Intent(this@ViewElectricityActivity, ImageViewerActivity::class.java)
                    intent.putStringArrayListExtra("image_paths", ArrayList(images))
                    intent.putExtra("position", pos)
                    startActivity(intent)
                }
            }
        }
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
        tvMessage.text = "Move this to Trash?"
        btnConfirm.text = "Delete"

        containerDetails.visibility = View.VISIBLE
        tvDetailTitle.text = "${record!!.totalUnits ?: 0} Units"
        tvDetailAmount.text = "Rs ${record!!.amount ?: 0.0}"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.electricityDao().softDelete(listOf(record!!.id), System.currentTimeMillis())
                }
                Toast.makeText(this@ViewElectricityActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        dialog.show()
    }
}