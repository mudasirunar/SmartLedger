package com.example.smartledger.activity

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
import com.example.smartledger.R
import com.example.smartledger.adapter.PhotoViewAdapter
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.Electricity
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
    private lateinit var photoAdapter: PhotoViewAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            finish() // Only finish the view activity if the record was actually updated
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 1. Enable Edge to Edge
        setContentView(R.layout.activity_view_electricity)

        // 2. Fix the Top Bar Overlap
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

        // Split Dates
        findViewById<TextView>(R.id.tvDetailStartDate).text = sdf.format(record!!.startDate)
        findViewById<TextView>(R.id.tvDetailEndDate).text = sdf.format(record!!.endDate)

        fun fmt(v: Double?): String = if (v == null) "-" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

        // Units & Amount
        findViewById<TextView>(R.id.tvDetailStartUnits).text = fmt(record!!.startUnits)
        findViewById<TextView>(R.id.tvDetailEndUnits).text = fmt(record!!.endUnits)
        findViewById<TextView>(R.id.tvDetailTotalUnits).text = "${fmt(record!!.totalUnits)} Units"
        findViewById<TextView>(R.id.tvDetailAmount).text = "Rs %.2f".format(record!!.amount ?: 0.0)

        // 1. Use the correct ID from your XML (detailRvPhotos)
        // Ensure this ID matches your new XML ID
        val rvPhotos = findViewById<RecyclerView>(R.id.detailRvPhotos)

        rvPhotos?.apply {
            // Optimization: Tell Android the size won't change drastically
            setHasFixedSize(true)

            // Set LayoutManager
            layoutManager = GridLayoutManager(this@ViewElectricityActivity, 2)

            val images = record?.imagePaths ?: emptyList()

            if (images.isEmpty()) {
                findViewById<View>(R.id.detailPhotos)?.visibility = View.GONE
                visibility = View.GONE
            } else {
                findViewById<View>(R.id.detailPhotos)?.visibility = View.VISIBLE
                visibility = View.VISIBLE

                val pAdapter = PhotoViewAdapter(images) { imagePath ->
                    val pos = images.indexOf(imagePath)
                    val intent = Intent(this@ViewElectricityActivity, ImageViewerActivity::class.java)
                    intent.putStringArrayListExtra("image_paths", ArrayList(images))
                    intent.putExtra("position", pos)
                    startActivity(intent)
                }

                adapter = pAdapter
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