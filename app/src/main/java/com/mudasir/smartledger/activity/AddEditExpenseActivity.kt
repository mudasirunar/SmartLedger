package com.mudasir.smartledger.activity

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.ThumbnailAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.Expense
import com.mudasir.smartledger.util.applySystemBarPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddEditExpenseActivity : AppCompatActivity() {

    private var initialTitle = ""
    private var initialAmount = ""
    private var initialDescription = ""
    private var initialDate: Long = 0
    private val initialImagePaths = mutableListOf<String>()
    private lateinit var etDate: TextInputEditText
    private lateinit var etTitle: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var rvPhotos: RecyclerView
    private lateinit var btnAddPhoto: Button
    private val selectedImagePaths = mutableListOf<String>()
    private lateinit var thumbnailAdapter: ThumbnailAdapter
    private var tempImageUri: Uri? = null
    private var selectedDate: Long = System.currentTimeMillis()
    private var existingExpense: Expense? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    //Launchers
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (selectedImagePaths.size + uris.size > 5) {
                Toast.makeText(this, "You can only add up to 5 photos total.", Toast.LENGTH_LONG).show()
                val spaceLeft = 5 - selectedImagePaths.size
                uris.take(spaceLeft).forEach { processSelectedImage(it) }
            } else {
                uris.forEach { processSelectedImage(it) }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) {
            processSelectedImage(tempImageUri!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_expense)
        setupWindowInsets()
        existingExpense = intent.getSerializableExtra("expense_data") as? Expense

        initViews()
        rvPhotos = findViewById(R.id.rvPhotos)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)

        if (savedInstanceState != null) {
            val savedPaths = savedInstanceState.getStringArrayList("selected_images")
            if (savedPaths != null) {
                selectedImagePaths.addAll(savedPaths)
            }
        }


        thumbnailAdapter = ThumbnailAdapter(selectedImagePaths) { imagePath ->
            val position = selectedImagePaths.indexOf(imagePath)
            if (position != -1) {
                selectedImagePaths.removeAt(position)
                thumbnailAdapter.notifyItemRemoved(position)
                thumbnailAdapter.notifyItemRangeChanged(position, selectedImagePaths.size)
                updatePhotoCount()
            }
        }
        rvPhotos.adapter = thumbnailAdapter

        populateData()
        updatePhotoCount()

        btnAddPhoto.setOnClickListener {
            if (selectedImagePaths.size >= 5) {
                Toast.makeText(this, "Limit of 5 photos reached", Toast.LENGTH_SHORT).show()
            } else {
                showImageSourceDialog()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selected_images", ArrayList(selectedImagePaths))
    }

    private fun updatePhotoCount() {
        btnAddPhoto.isEnabled = selectedImagePaths.size < 5
        btnAddPhoto.text = if(selectedImagePaths.size >= 5) "Limit Reached" else "Add"
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            openCamera()
                        } else {
                            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                    1 -> pickImagesLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun openCamera() {
        try {
            val tmpFile = File.createTempFile("img_", ".jpg", externalCacheDir)
            val authority = "${packageName}.fileprovider"
            tempImageUri = FileProvider.getUriForFile(this, authority, tmpFile)
            takePhotoLauncher.launch(tempImageUri!!)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun processSelectedImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val imagesDir = File(filesDir, "expense_images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val fileName = "EXP_${System.currentTimeMillis()}_${(0..1000).random()}.jpg"
                val file = File(imagesDir, fileName)

                val outputStream = java.io.FileOutputStream(file)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                // Switch back to Main thread to update UI
                withContext(Dispatchers.Main) {
                    selectedImagePaths.add(file.absolutePath)
                    thumbnailAdapter.notifyItemInserted(selectedImagePaths.size - 1)
                    updatePhotoCount()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditExpenseActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupWindowInsets() {
        findViewById<View>(R.id.main).applySystemBarPadding(includeIme = true)
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)

        toolbar.navigationIcon?.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(this, R.color.white),
            PorterDuff.Mode.SRC_ATOP
        )

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.title = if (existingExpense != null) "Edit Record" else "Add Record"

        etDate = findViewById(R.id.etDate)
        etTitle = findViewById(R.id.etTitle)
        etAmount = findViewById(R.id.etAmount)
        etDescription = findViewById(R.id.etDescription)
        btnSave = findViewById(R.id.btnSave)

        updateDateDisplay(selectedDate)

        etDate.setOnClickListener {
            val localCalendar = Calendar.getInstance()
            localCalendar.timeInMillis = selectedDate

            val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            utcCalendar.clear()
            utcCalendar.set(
                localCalendar.get(Calendar.YEAR),
                localCalendar.get(Calendar.MONTH),
                localCalendar.get(Calendar.DAY_OF_MONTH)
            )
            val utcTime = utcCalendar.timeInMillis
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(utcTime)
                .setTheme(R.style.App_MaterialDatePicker)
                .build()

            picker.addOnPositiveButtonClickListener { selection ->

                val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCal.timeInMillis = selection
                val localCal = Calendar.getInstance()
                localCal.set(
                    utcCal.get(Calendar.YEAR),
                    utcCal.get(Calendar.MONTH),
                    utcCal.get(Calendar.DAY_OF_MONTH),
                    0, 0, 0
                )
                selectedDate = localCal.timeInMillis
                updateDateDisplay(selectedDate)
            }
            picker.show(supportFragmentManager, "tag")
        }

        btnSave.setOnClickListener { saveExpense() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackAction()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun populateData() {
        existingExpense?.let {
            // 1. Capture Baseline
            initialTitle = it.title
            initialAmount = it.amount.toString()
            initialDescription = it.description
            initialDate = it.date
            initialImagePaths.clear()
            initialImagePaths.addAll(it.imagePaths)

            // 2. Set UI
            selectedDate = it.date
            updateDateDisplay(selectedDate)
            etTitle.setText(initialTitle)
            etAmount.setText(initialAmount)
            etDescription.setText(initialDescription)
            btnSave.text = "Update Record"

            // 3. Sync Images
            if (selectedImagePaths.isEmpty()) {
                selectedImagePaths.addAll(it.imagePaths)
            }

            thumbnailAdapter.notifyDataSetChanged()
            updatePhotoCount()

        } ?: run {
            initialDate = selectedDate
            etTitle.requestFocus()
            etTitle.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etTitle, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun updateDateDisplay(timestamp: Long) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        etDate.setText(sdf.format(Date(timestamp)))
    }

    private fun saveExpense() {
        val title = etTitle.text.toString().trim()
        val amountStr = etAmount.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (amountStr.isEmpty()) {
            etAmount.error = "Required"
            return
        }
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val finalImagePaths = ArrayList(selectedImagePaths)

        val expense = Expense(
            id = existingExpense?.id ?: 0, // 0 means new
            title = if (title.isEmpty()) "(No Title)" else title,
            amount = amount,
            description = description,
            date = selectedDate,
            imagePaths = finalImagePaths
        )
        val isNew = existingExpense == null
        showSaveConfirmationDialog(expense, isNew)
    }

    private fun showSaveConfirmationDialog(expense: Expense, isNew: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val tvDetailTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)

        // Set Logic
        if (isNew) {
            tvTitle.text = "Add Record"
            tvMessage.text = "Please confirm the details below:"
            btnConfirm.text = "Add"
        } else {
            tvTitle.text = "Update Record"
            tvMessage.text = "Are you sure you want to update this record?"
            btnConfirm.text = "Update"
        }

        tvDetailTitle.text = expense.title
        tvDetailAmount.text = "Rs ${expense.amount}"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isNew) {
                        db.expenseDao().insertExpense(expense)
                    } else {
                        db.expenseDao().updateExpense(expense)
                    }
                }
                val resultIntent = Intent()
                val isEdit = intent.hasExtra("expense_data")

                if (isEdit) {
                    resultIntent.putExtra("toast_message", "Record Updated")
                } else {
                    resultIntent.putExtra("toast_message", "Record Added")
                }

                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }

        dialog.show()
    }
    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = etTitle.text.toString().trim()
        val currentAmount = etAmount.text.toString().trim()
        val currentDesc = etDescription.text.toString().trim()

        if (existingExpense == null) {
            return currentTitle.isNotEmpty() || currentAmount.isNotEmpty() ||
                    currentDesc.isNotEmpty() || selectedImagePaths.isNotEmpty() ||
                    selectedDate != initialDate
        }

        return currentTitle != initialTitle ||
                currentAmount != initialAmount ||
                currentDesc != initialDescription ||
                selectedDate != initialDate ||
                selectedImagePaths != initialImagePaths
    }

    private fun handleBackAction() {
        if (hasUnsavedChanges()) {
            showDiscardDialog()
        } else {
            finish()
        }
    }

    private fun showDiscardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)

        tvTitle.text = "Discard Changes?"
        tvMessage.text = "You have unsaved changes. Are you sure you want to discard them?"
        containerDetails.visibility = View.GONE
        btnConfirm.text = "Discard"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }
}