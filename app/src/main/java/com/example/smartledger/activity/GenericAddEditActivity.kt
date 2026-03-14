package com.example.smartledger.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.R
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.CustomEntry
import com.example.smartledger.data.CustomLedger
import com.example.smartledger.data.DateMode
import com.example.smartledger.data.FieldType
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenericAddEditActivity : AppCompatActivity() {

    private lateinit var containerDynamicFields: LinearLayout
    private lateinit var etSingleDate: TextInputEditText
    private lateinit var etStartDate: TextInputEditText
    private lateinit var etEndDate: TextInputEditText
    private lateinit var btnSave: Button
    private val dynamicViewsMap = mutableMapOf<String, TextInputEditText>()
    private var selectedLedger: CustomLedger? = null
    private var existingEntry: CustomEntry? = null
    private var startDate: Long = System.currentTimeMillis()
    private var endDate: Long = System.currentTimeMillis()
    private lateinit var etAmount: TextInputEditText
    private lateinit var rvPhotos: RecyclerView
    private lateinit var btnAddPhoto: Button
    private lateinit var tvPhotoCount: TextView
    private lateinit var thumbnailAdapter: com.example.smartledger.adapter.ThumbnailAdapter
    private val selectedImagePaths = mutableListOf<String>()
    private var tempImageUri: Uri? = null
    private var initialStartDate: Long = 0
    private var initialEndDate: Long = 0
    private lateinit var etMonthDate: TextInputEditText
    private var initialDataMap = mutableMapOf<String, String>()
    private var initialImagePaths = mutableListOf<String>()
    private var isInitialStateCaptured = false
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val limit = selectedLedger?.photoLimit ?: 3
        if (uris.isNotEmpty()) {
            if (selectedImagePaths.size + uris.size > limit) {
                Toast.makeText(this, "Limit of $limit photos reached.", Toast.LENGTH_SHORT).show()
                val spaceLeft = limit - selectedImagePaths.size
                uris.take(spaceLeft).forEach { processSelectedImage(it) }
            } else {
                uris.forEach { processSelectedImage(it) }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) processSelectedImage(tempImageUri!!)
    }
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_generic_add_edit)

        selectedLedger = intent.getSerializableExtra("ledger_template") as? CustomLedger
        existingEntry = intent.getSerializableExtra("existing_entry") as? CustomEntry

        if (selectedLedger == null) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackAction() }
        })

        initViews()
        applyTemplateLogic()
        buildDynamicUI()

        if (savedInstanceState != null) {
            val savedPaths = savedInstanceState.getStringArrayList("selected_images")
            if (savedPaths != null) {
                selectedImagePaths.clear()
                selectedImagePaths.addAll(savedPaths)
                thumbnailAdapter.notifyDataSetChanged()
            }
            initialStartDate = savedInstanceState.getLong("init_start_date")
            initialEndDate = savedInstanceState.getLong("init_end_date")
            val savedData = savedInstanceState.getSerializable("init_data_map") as? HashMap<String, String>
            if (savedData != null) initialDataMap = savedData
            initialImagePaths = savedInstanceState.getStringArrayList("init_img_paths") ?: mutableListOf()
            isInitialStateCaptured = true

            updatePhotoUI()
        } else if (existingEntry != null) {
            populateExistingData()
        }

        etAmount.post {
            if (existingEntry == null) {
                showKeyboard(etAmount)
            }
            captureInitialState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selected_images", ArrayList(selectedImagePaths))
        outState.putLong("init_start_date", initialStartDate)
        outState.putLong("init_end_date", initialEndDate)
        outState.putSerializable("init_data_map", HashMap(initialDataMap))
        outState.putStringArrayList("init_img_paths", ArrayList(initialImagePaths))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackAction()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        etAmount = findViewById(R.id.etAmount)
        etSingleDate = findViewById(R.id.etSingleDate)
        etMonthDate = findViewById(R.id.etMonthDate)
        etStartDate = findViewById(R.id.etStartDate)
        etEndDate = findViewById(R.id.etEndDate)
        btnSave = findViewById(R.id.btnSave)

        etSingleDate.setOnClickListener {
            hideKeyboard()
            showDatePicker { date -> startDate = date; updateDateText() }
        }
        etMonthDate.setOnClickListener {
            hideKeyboard()
            showMonthPicker()
        }
        etStartDate.setOnClickListener {
            hideKeyboard()
            showDatePicker { date -> startDate = date; updateDateText() }
        }
        etEndDate.setOnClickListener {
            hideKeyboard()
            showDatePicker { date -> endDate = date; updateDateText() }
        }

        btnSave.setOnClickListener { validateAndSave() }
        updateDateText()
        setupPhotoLogic()
    }


    private fun populateExistingData() {
        existingEntry?.let { entry ->
            startDate = entry.date
            etAmount.setText(entry.amount?.toString() ?: "")

            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            val dataMap: Map<String, String>? = try {
                Gson().fromJson(entry.dataJson, type)
            } catch (e: Exception) {
                null
            }

            if (selectedLedger?.dateMode == DateMode.RANGE) {
                val savedEnd = dataMap?.get("SYS_END_DATE")?.toLongOrNull()
                if (savedEnd != null) {
                    endDate = savedEnd
                }
            }

            updateDateText()

            dataMap?.forEach { (fieldName, value) ->
                if (fieldName != "SYS_END_DATE") {
                    dynamicViewsMap[fieldName]?.setText(value)
                }
            }

            if (selectedImagePaths.isEmpty()) {
                selectedImagePaths.addAll(entry.imagePaths)
                thumbnailAdapter.notifyDataSetChanged()
                updatePhotoUI()
            }

            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
            toolbar.title = "Edit Record"
            btnSave.text = "Update Record"
        }
    }

    private fun applyTemplateLogic() {
        val ledger = selectedLedger!!

        when (ledger.dateMode) {
            DateMode.RANGE -> {
                findViewById<View>(R.id.tilSingleDate).visibility = View.GONE
                findViewById<View>(R.id.tilMonthDate).visibility = View.GONE
                findViewById<View>(R.id.containerDateRange).visibility = View.VISIBLE
            }
            DateMode.MONTH -> {
                findViewById<View>(R.id.tilSingleDate).visibility = View.GONE
                findViewById<View>(R.id.containerDateRange).visibility = View.GONE
                findViewById<View>(R.id.tilMonthDate).visibility = View.VISIBLE
            }
            else -> {
                findViewById<View>(R.id.tilSingleDate).visibility = View.VISIBLE
                findViewById<View>(R.id.tilMonthDate).visibility = View.GONE
                findViewById<View>(R.id.containerDateRange).visibility = View.GONE
            }
        }

        if (ledger.hasPhotos) {
            findViewById<View>(R.id.sectionPhotos).visibility = View.VISIBLE
            tvPhotoCount.text = "Photos (Max ${ledger.photoLimit})"
        }
    }

    private fun buildDynamicUI() {
        containerDynamicFields = findViewById(R.id.containerDynamicFields)
        containerDynamicFields.removeAllViews()

        val fields = selectedLedger?.fields ?: emptyList()

        etAmount.imeOptions = if (fields.isNotEmpty()) android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        else android.view.inputmethod.EditorInfo.IME_ACTION_DONE

        fields.forEachIndexed { index, field ->
            val til = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 16.dpToPx() }
                hint = field.fieldName
            }

            val et = TextInputEditText(til.context).apply {
                inputType = when (field.fieldType) {
                    FieldType.NUMBER -> InputType.TYPE_CLASS_NUMBER
                    FieldType.DECIMAL -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }

                imeOptions = if (index < fields.size - 1) android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                else android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }

            til.addView(et)
            containerDynamicFields.addView(til)
            dynamicViewsMap[field.fieldName] = et
        }
    }
    private fun validateAndSave() {
        val amountText = etAmount.text.toString().trim()
        if (amountText.isEmpty()) {
            etAmount.error = "Amount is required"
            showKeyboard(etAmount)
            return
        }
        val amount = amountText.toDoubleOrNull() ?: 0.0
        val dataMap = mutableMapOf<String, String>()
        for ((name, et) in dynamicViewsMap) {
            dataMap[name] = et.text.toString().trim()
        }

        if (selectedLedger?.dateMode == DateMode.RANGE) {
            dataMap["SYS_END_DATE"] = endDate.toString()
        }

        val json = Gson().toJson(dataMap)

        val entry = CustomEntry(
            id = existingEntry?.id ?: 0,
            ledgerId = selectedLedger!!.id,
            date = startDate,
            amount = amount,
            dataJson = json,
            imagePaths = ArrayList(selectedImagePaths)
        )

        showConfirmationDialog(entry, dataMap, existingEntry == null)
    }

    private fun showConfirmationDialog(entry: CustomEntry, dataMap: Map<String, String>, isNew: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val tvDetailTitle = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

        tvTitle.text = if (isNew) "Add Record" else "Update Record"
        tvMessage.text = "Confirm details?"
        btnConfirm.text = if (isNew) "Add" else "Update"

        val userFields = dataMap.filterKeys { it != "SYS_END_DATE" }
        tvDetailTitle.text = if (userFields.isNotEmpty() && userFields.values.first().isNotEmpty()) {
            userFields.values.first()
        } else {
            selectedLedger?.name
        }

        tvDetailAmount.text = "Rs ${entry.amount ?: 0.0}"
        containerDetails.visibility = View.VISIBLE

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                if (isNew) db.customLedgerDao().insertEntry(entry)
                else db.customLedgerDao().updateEntry(entry)

                withContext(Dispatchers.Main) {
                    val resultIntent = Intent().apply {
                        putExtra("toast_message", if (isNew) "Record Added" else "Record Updated")
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
        dialog.show()
    }

    private fun captureInitialState() {
        initialStartDate = startDate
        initialEndDate = endDate

        initialDataMap.clear()
        initialDataMap["STATIC_AMOUNT_FIELD"] = etAmount.text.toString()

        dynamicViewsMap.forEach { (name, et) ->
            initialDataMap[name] = et.text.toString()
        }

        initialImagePaths = selectedImagePaths.toMutableList()
        isInitialStateCaptured = true
    }

    private fun hasUnsavedChanges(): Boolean {
        if (!isInitialStateCaptured) return false

        val currentAmt = etAmount.text.toString().toDoubleOrNull() ?: 0.0
        val initialAmt = initialDataMap["STATIC_AMOUNT_FIELD"]?.toDoubleOrNull() ?: 0.0
        val amountChanged = currentAmt != initialAmt
        val dateChanged = startDate != initialStartDate ||
                (selectedLedger?.dateMode == DateMode.RANGE && endDate != initialEndDate)

        var fieldsChanged = false
        dynamicViewsMap.forEach { (name, et) ->
            val currentVal = et.text.toString().trim()
            val initialVal = initialDataMap[name]?.trim() ?: ""
            if (currentVal != initialVal) {
                fieldsChanged = true
            }
        }

        val photosChanged = selectedImagePaths != initialImagePaths

        return amountChanged || dateChanged || fieldsChanged || photosChanged
    }

    private fun handleBackAction() {
        if (hasUnsavedChanges()) {
            hideKeyboard()
            showDiscardDialog()
        } else {
            finish()
        }
    }

    private fun showDiscardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

        tvTitle.text = "Discard Changes?"
        tvMessage.text = "You have unsaved changes. Are you sure you want to discard them and go back?"
        containerDetails.visibility = View.GONE

        btnConfirm.text = "Discard"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun setupPhotoLogic() {
        rvPhotos = findViewById(R.id.rvPhotos)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        tvPhotoCount = findViewById(R.id.tvPhotoCount)

        thumbnailAdapter = com.example.smartledger.adapter.ThumbnailAdapter(selectedImagePaths) { imagePath ->
            val position = selectedImagePaths.indexOf(imagePath)
            if (position != -1) {
                selectedImagePaths.removeAt(position)
                thumbnailAdapter.notifyItemRemoved(position)
                updatePhotoUI()
            }
        }
        rvPhotos.adapter = thumbnailAdapter
        rvPhotos.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)

        btnAddPhoto.setOnClickListener {
            hideKeyboard()
            val limit = selectedLedger?.photoLimit ?: 3
            if (selectedImagePaths.size >= limit) {
                Toast.makeText(this, "Limit reached", Toast.LENGTH_SHORT).show()
            } else {
                showImageSourceDialog()
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(options) { _, which ->
                if (which == 0) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        openCamera()
                    } else {
                        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                } else pickImagesLauncher.launch("image/*")
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
                val imagesDir = File(filesDir, "custom_images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val fileName = "CUSTOM_${System.currentTimeMillis()}_${(0..1000).random()}.jpg"
                val file = File(imagesDir, fileName)

                inputStream?.use { input ->
                    java.io.FileOutputStream(file).use { output -> input.copyTo(output) }
                }

                withContext(Dispatchers.Main) {
                    selectedImagePaths.add(file.absolutePath)
                    thumbnailAdapter.notifyItemInserted(selectedImagePaths.size - 1)
                    updatePhotoUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GenericAddEditActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePhotoUI() {
        val limit = selectedLedger?.photoLimit ?: 3
        btnAddPhoto.isEnabled = selectedImagePaths.size < limit
        btnAddPhoto.text = if (selectedImagePaths.size >= limit) "Limit Reached" else "Add"
        tvPhotoCount.text = "Photos (Max $limit)"
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker().setSelection(System.currentTimeMillis()).build()
        picker.addOnPositiveButtonClickListener { onDateSelected(it) }
        picker.show(supportFragmentManager, "DATE")
    }

    private fun updateDateText() {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthSdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        etSingleDate.setText(sdf.format(Date(startDate)))
        etStartDate.setText(sdf.format(Date(startDate)))
        etEndDate.setText(sdf.format(Date(endDate)))
        etMonthDate.setText(monthSdf.format(Date(startDate)))
    }

    private fun showMonthPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_month_picker, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        val spinnerMonth = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerMonth)
        val etYear = dialogView.findViewById<TextInputEditText>(R.id.etYear)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnSelect = dialogView.findViewById<TextView>(R.id.btnSelect)

        fun hideDialogKeyboard() {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(dialogView.windowToken, 0)
            etYear.clearFocus()
        }

        dialog.setOnShowListener {
            hideDialogKeyboard()
        }

        spinnerMonth.setOnTouchListener { v, event ->
            hideDialogKeyboard()
            v.post { spinnerMonth.showDropDown() }
            true
        }

        spinnerMonth.setOnItemClickListener { _, _, position, _ ->
        }

        etYear.setOnEditorActionListener { _, _, _ ->
            hideDialogKeyboard()
            false
        }

        val months = arrayOf("January","February","March","April","May","June",
            "July","August","September","October","November","December")

        spinnerMonth.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, months))

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = startDate }
        spinnerMonth.setText(months[cal.get(java.util.Calendar.MONTH)], false)
        etYear.setText(cal.get(java.util.Calendar.YEAR).toString())

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSelect.setOnClickListener {
            val monthIndex = months.indexOf(spinnerMonth.text.toString())
            val year = etYear.text.toString().toIntOrNull()

            if (monthIndex == -1) {
                Toast.makeText(this, "Select a valid month", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (year == null || year < 2000 || year > 2100) {
                etYear.error = "Enter a valid year"
                return@setOnClickListener
            }

            startDate = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, monthIndex)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            updateDateText()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        view.post {
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()
}