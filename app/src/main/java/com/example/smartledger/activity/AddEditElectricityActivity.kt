package com.example.smartledger.activity

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.smartledger.R
import com.example.smartledger.adapter.ThumbnailAdapter
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.Electricity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddEditElectricityActivity : AppCompatActivity() {

    private lateinit var etStartDate: TextInputEditText
    private lateinit var etEndDate: TextInputEditText
    private lateinit var etStartUnits: TextInputEditText
    private lateinit var etEndUnits: TextInputEditText
    private lateinit var etTotalUnits: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var rvPhotos: RecyclerView
    private lateinit var btnAddPhoto: Button

    private var initialStartUnits = ""
    private var initialEndUnits = ""
    private var initialAmount = ""
    private var initialStartDate: Long? = null
    private var initialEndDate: Long? = null
    private val initialImagePaths = mutableListOf<String>()

    private var startDate: Long? = null
    private var endDate: Long? = null
    private val selectedImagePaths = mutableListOf<String>()
    private var existingRecord: Electricity? = null
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var thumbnailAdapter: ThumbnailAdapter
    private var tempImageUri: Uri? = null

    // --- LAUNCHERS ---
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (selectedImagePaths.size + uris.size > 3) {
                Toast.makeText(this, "Limit of 3 photos reached.", Toast.LENGTH_LONG).show()
                val spaceLeft = 3 - selectedImagePaths.size
                uris.take(spaceLeft).forEach { processSelectedImage(it) }
            } else {
                uris.forEach { processSelectedImage(it) }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) openCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) processSelectedImage(tempImageUri!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_electricity)
        setupWindowInsets()

        initViews()
        rvPhotos = findViewById(R.id.rvPhotos)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)

        if (savedInstanceState != null) {
            val savedPaths = savedInstanceState.getStringArrayList("selected_images")
            if (savedPaths != null) selectedImagePaths.addAll(savedPaths)
        }

        existingRecord = intent.getSerializableExtra("electricity_data") as? Electricity

        setupPhotoLogic()
        populateData()
        updatePhotoCount()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackAction() }
        })
    }

    private fun handleBackAction() {
        if (hasUnsavedChanges()) {
            showDiscardDialog()
        } else {
            finish()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.navigationIcon?.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_ATOP)
        toolbar.title = if(existingRecord != null) "Edit Record" else "Add Record"

        etStartDate = findViewById(R.id.etStartDate)
        etEndDate = findViewById(R.id.etEndDate)
        etStartUnits = findViewById(R.id.etStartUnits)
        etEndUnits = findViewById(R.id.etEndUnits)
        etTotalUnits = findViewById(R.id.etTotalUnits)
        etAmount = findViewById(R.id.etAmount)
        btnSave = findViewById(R.id.btnSave)
        rvPhotos = findViewById(R.id.rvPhotos)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)

        // Date Logic
        etStartDate.setOnClickListener { showDatePicker { date -> startDate = date; updateDateText(etStartDate, date) } }
        etEndDate.setOnClickListener { showDatePicker { date -> endDate = date; updateDateText(etEndDate, date) } }

        // Auto-Calculate Total Units AND Clear Errors
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                calculateTotalUnits()
                // FIX: Remove warning icons immediately when user types
                etStartUnits.error = null
                etEndUnits.error = null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etStartUnits.addTextChangedListener(textWatcher)
        etEndUnits.addTextChangedListener(textWatcher)

        btnAddPhoto.setOnClickListener {
            if (selectedImagePaths.size >= 3) {
                Toast.makeText(this, "Limit of 3 photos reached", Toast.LENGTH_SHORT).show()
            } else {
                showImageSourceDialog()
            }
        }

        btnSave.setOnClickListener { validateAndSave() }
    }

    private fun calculateTotalUnits() {
        val s = etStartUnits.text.toString().toDoubleOrNull()
        val e = etEndUnits.text.toString().toDoubleOrNull()
        if (s != null && e != null) {
            val total = e - s
            // SMART FORMAT FOR AUTO-CALCULATION
            if (total % 1.0 == 0.0) {
                etTotalUnits.setText(total.toInt().toString())
            } else {
                etTotalUnits.setText(String.format("%.1f", total))
            }
        } else {
            etTotalUnits.setText("")
        }
    }

    private fun updateDateText(view: TextInputEditText, date: Long) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        view.setText(sdf.format(Date(date)))
        view.error = null
    }

    private fun validateAndSave() {
        if (startDate == null) { etStartDate.error = "Required"; return }
        if (endDate == null) { etEndDate.error = "Required"; return }

        if (endDate!! < startDate!!) {
            Toast.makeText(this, "End Date cannot be before Start Date", Toast.LENGTH_LONG).show()
            return
        }

        val sUnits = etStartUnits.text.toString().toDoubleOrNull()
        val eUnits = etEndUnits.text.toString().toDoubleOrNull()

        if (sUnits != null && eUnits != null && eUnits < sUnits) {
            etEndUnits.error = "Cannot be less than Start"
            return
        }

        val total = if(sUnits!=null && eUnits!=null) eUnits - sUnits else null
        val amount = etAmount.text.toString().toDoubleOrNull()

        val record = Electricity(
            id = existingRecord?.id ?: 0,
            startDate = startDate!!,
            endDate = endDate!!,
            startUnits = sUnits,
            endUnits = eUnits,
            totalUnits = total,
            amount = amount,
            description = "",
            imagePaths = ArrayList(selectedImagePaths)
        )

        showConfirmationDialog(record, existingRecord == null)
    }

    private fun showConfirmationDialog(record: Electricity, isNew: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val tvDetailTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)

        tvTitle.text = if(isNew) "Add Record" else "Update Record"
        tvMessage.text = "Confirm details?"
        btnConfirm.text = if(isNew) "Add" else "Update"

        val totalVal = record.totalUnits ?: 0.0
        val unitText = if (totalVal % 1.0 == 0.0) "${totalVal.toInt()} Units" else "$totalVal Units"

        tvDetailTitle.text = unitText
        tvDetailAmount.text = "Rs ${record.amount ?: 0.0}"

        btnCancel.setOnClickListener { dialog.dismiss() }

        // --- FIXED LOGIC HERE ---
        btnConfirm.setOnClickListener {
            dialog.dismiss()

            // Launch coroutine to save to DB
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if(isNew) {
                        db.electricityDao().insert(record)
                    } else {
                        db.electricityDao().update(record)
                    }
                }
                // Back on Main Thread
                Toast.makeText(this@AddEditElectricityActivity, "Record Added", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }

        dialog.show()
    }

    // --- HELPERS ---
    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setTheme(R.style.App_MaterialDatePicker)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCal.timeInMillis = selection
            val localCal = Calendar.getInstance()
            localCal.set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
            onDateSelected(localCal.timeInMillis)
        }
        picker.show(supportFragmentManager, "tag")
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
            Toast.makeText(this, "Error opening camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSelectedImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val imagesDir = File(filesDir, "elec_images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val fileName = "ELEC_${System.currentTimeMillis()}_${(0..1000).random()}.jpg"
                val file = File(imagesDir, fileName)

                inputStream?.use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    selectedImagePaths.add(file.absolutePath)
                    thumbnailAdapter.notifyItemInserted(selectedImagePaths.size - 1)
                    updatePhotoCount()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditElectricityActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupPhotoLogic() {
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
        updatePhotoCount()
    }

    private fun updatePhotoCount() {
        btnAddPhoto.isEnabled = selectedImagePaths.size < 3
        btnAddPhoto.text = if(selectedImagePaths.size >= 3) "Limit Reached" else "Add"
    }

    private fun populateData() {
        existingRecord?.let {
            // Save Initial State for comparison later
            initialStartDate = it.startDate
            initialEndDate = it.endDate
            initialStartUnits = it.startUnits?.toString() ?: ""
            initialEndUnits = it.endUnits?.toString() ?: ""
            initialAmount = it.amount?.toString() ?: ""
            initialImagePaths.clear()
            initialImagePaths.addAll(it.imagePaths)

            // Set UI values
            startDate = it.startDate
            endDate = it.endDate
            updateDateText(etStartDate, it.startDate)
            updateDateText(etEndDate, it.endDate)
            etStartUnits.setText(initialStartUnits)
            etEndUnits.setText(initialEndUnits)
            etAmount.setText(initialAmount)
            calculateTotalUnits()
            btnSave.text = "Update Record"

            // Sync Images
            if (selectedImagePaths.isEmpty()) {
                selectedImagePaths.addAll(it.imagePaths)
            }
            thumbnailAdapter.notifyDataSetChanged()
            updatePhotoCount()
        } ?: run {
            initialStartDate = startDate
            initialEndDate = endDate
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        // Current UI values
        val currentStartUnits = etStartUnits.text.toString()
        val currentEndUnits = etEndUnits.text.toString()
        val currentAmount = etAmount.text.toString()

        // Compare against the "Initial State" we captured in populateData
        val isDateChanged = startDate != initialStartDate || endDate != initialEndDate
        val isUnitsChanged = currentStartUnits != initialStartUnits || currentEndUnits != initialEndUnits
        val isAmountChanged = currentAmount != initialAmount
        val isPhotosChanged = selectedImagePaths != initialImagePaths

        // If it's a NEW record, just check if any field is NOT empty
        if (existingRecord == null) {
            return startDate != null || endDate != null ||
                    currentStartUnits.isNotEmpty() || currentEndUnits.isNotEmpty() ||
                    currentAmount.isNotEmpty() || selectedImagePaths.isNotEmpty()
        }

        // If it's an EXISTING record, check if anything differs from the original
        return isDateChanged || isUnitsChanged || isAmountChanged || isPhotosChanged
    }

    private fun showDiscardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<android.view.View>(R.id.containerDetails)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)

        tvTitle.text = "Discard Changes?"
        tvMessage.text = "You have unsaved changes. Are you sure you want to discard them and go back?"
        containerDetails.visibility = View.GONE // Hide the details box for discard dialog

        btnConfirm.text = "Discard"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            finish() // Go back to the previous screen (ElectricityActivity or ViewElectricityActivity)
        }

        dialog.show()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackAction()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selected_images", ArrayList(selectedImagePaths))
    }
}