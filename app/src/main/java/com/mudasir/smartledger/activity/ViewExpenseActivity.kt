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
import com.mudasir.smartledger.data.Expense
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ViewExpenseActivity : AppCompatActivity() {

    private var expense: Expense? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val message = result.data?.getStringExtra("toast_message") ?: "Record Updated"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            expense?.id?.let { id ->
                lifecycleScope.launch {
                    val updated = withContext(Dispatchers.IO) { db.expenseDao().getById(id) }
                    if (updated != null) {
                        expense = updated
                        setupUI()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_expense)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        expense = intent.getSerializableExtra("expense_data") as? Expense
        if (expense == null) {
            finish()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)

        toolbar.setNavigationOnClickListener {
            finish()
        }
        toolbar.navigationIcon?.setTint(getColor(R.color.white))

        val btnDelete = findViewById<ImageView>(R.id.btnDeleteHeader)
        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog(expense!!)
        }

        findViewById<Button>(R.id.btnModify).setOnClickListener {
            val intent = Intent(this, AddEditExpenseActivity::class.java)
            intent.putExtra("expense_data", expense)
            editLauncher.launch(intent)
        }

        findViewById<TextView>(R.id.detailTvTitle).text = expense!!.title
        findViewById<TextView>(R.id.detailTvAmount).text = "Rs %.2f".format(expense!!.amount)
        findViewById<TextView>(R.id.detailTvDate).text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(expense!!.date)
        val desc = expense!!.description
        if (desc.isNullOrEmpty()) {
            findViewById<View>(R.id.detailDescription).visibility = View.GONE
            findViewById<View>(R.id.detailTvDescription).visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.detailTvDescription).text = desc
        }

        val rvPhotos = findViewById<RecyclerView>(R.id.detailRvPhotos)
        val tvPhotoHeader = findViewById<View>(R.id.detailPhotos)
        val images = expense?.imagePaths ?: emptyList()

        if (images.isEmpty()) {
            tvPhotoHeader.visibility = View.GONE
            rvPhotos.visibility = View.GONE
        } else {
            tvPhotoHeader.visibility = View.VISIBLE
            rvPhotos.visibility = View.VISIBLE

            rvPhotos.apply {
                setHasFixedSize(true)
                layoutManager = GridLayoutManager(this@ViewExpenseActivity, 2)
                adapter = PhotoViewAdapter(images) { imagePath ->
                    val position = images.indexOf(imagePath)
                    val intent = Intent(this@ViewExpenseActivity, ImageViewerActivity::class.java)
                    intent.putStringArrayListExtra("image_paths", ArrayList(images))
                    intent.putExtra("position", position)
                    startActivity(intent)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(expense: Expense) {
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

        tvTitle.text = "Delete Expense?"
        tvMessage.text = "Move this item to the Trash Bin?"
        btnConfirm.text = "Delete"

        containerDetails.visibility = View.VISIBLE
        tvDetailTitle.text = expense.title
        tvDetailAmount.text = "Rs ${expense.amount}"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val currentTimestamp = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    db.expenseDao().softDeleteExpenses(listOf(expense.id), currentTimestamp)
                }
                Toast.makeText(this@ViewExpenseActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        dialog.show()
    }
}