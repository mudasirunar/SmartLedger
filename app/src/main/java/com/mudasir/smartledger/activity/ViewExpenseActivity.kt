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

        findViewById<View>(R.id.main).applySystemBarPadding(includeIme = true)

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

        PhotoGridHelper.setupPhotoGrid(
            context = this,
            recyclerView = findViewById(R.id.detailRvPhotos),
            headerView = findViewById(R.id.detailPhotos),
            images = expense?.imagePaths ?: emptyList()
        )
    }

    private fun showDeleteConfirmationDialog(expense: Expense) {
        DialogHelper.createConfirmationDialog(
            context = this,
            title = "Delete Record?",
            message = "Are you sure you want to delete this record?"
        ) { views ->
            views.btnConfirm.text = "Delete"
            views.details.visibility = View.VISIBLE
            views.detailTitle.text = expense.title
            views.detailAmount.text = "Rs ${expense.amount}"

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                views.dialog.dismiss()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.expenseDao().softDeleteExpenses(listOf(expense.id), System.currentTimeMillis())
                    }
                    Toast.makeText(this@ViewExpenseActivity, "Item moved to Trash", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.show()
    }
}