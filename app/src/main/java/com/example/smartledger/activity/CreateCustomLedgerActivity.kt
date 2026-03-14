package com.example.smartledger.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.R
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.CustomField
import com.example.smartledger.data.CustomLedger
import com.example.smartledger.data.DateMode
import com.example.smartledger.data.FieldType
import com.example.smartledger.data.LedgerType
import com.example.smartledger.data.PricingConfig
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateCustomLedgerActivity : AppCompatActivity() {

    private lateinit var containerFields: LinearLayout
    private lateinit var etLedgerName: TextInputEditText
    private lateinit var ivSelectedIcon: ImageView
    private var selectedIconResId: Int = R.drawable.ic_star
    private lateinit var flipper: ViewFlipper
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnNext: TextView
    private lateinit var btnBack: TextView
    private var currentStep = 0
    private var applyToAll: Boolean = true
    private var committedLedgerType: String = "simple"
    private val dynamicViewsMap = mutableMapOf<String, TextInputEditText>()
    private val keyboardHandler = Handler(Looper.getMainLooper())
    private var pendingKeyboardRunnable: Runnable? = null

    // Helper data class for pricing
    data class FieldPricing(
        val fieldName: String,
        var isFixed: Boolean = false,
        var price: Double = 0.0
    ) : java.io.Serializable
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_custom_ledger)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }
        initViews()
        setupNavigationButtons()
        setupPhotoSettings()

        if (savedInstanceState == null) {
            committedLedgerType = "simple"
            addNewFieldRow(autoFocus = false)
            currentStep = 0
            updateWizardUI()
            etLedgerName.requestFocus()
            showKeyboard(etLedgerName)
        } else {
            currentStep = savedInstanceState.getInt("saved_step", 0)
            selectedIconResId = savedInstanceState.getInt("saved_icon", R.drawable.ic_star)
            ivSelectedIcon.setImageResource(selectedIconResId)
            applyToAll = savedInstanceState.getBoolean("apply_to_all", true)
            committedLedgerType = savedInstanceState.getString("committed_type", "simple") ?: "simple"


            val savedFieldNames = savedInstanceState.getStringArrayList("field_names") ?: arrayListOf()
            val savedFieldTypes = savedInstanceState.getStringArrayList("field_types") ?: arrayListOf()
            val savedPricing = savedInstanceState.getSerializable("saved_pricing") as? ArrayList<FieldPricing>

            findViewById<View>(R.id.rgLedgerType).post {
                containerFields.removeAllViews()
                dynamicViewsMap.clear()

                if (savedFieldNames.isEmpty()) {
                    addNewFieldRow(autoFocus = false)
                } else {
                    for (i in savedFieldNames.indices) {
                        addNewFieldRow(savedFieldNames[i], savedFieldTypes.getOrElse(i) { "Text" }, autoFocus = false)
                    }
                }

                if (currentStep == 2 && findViewById<RadioButton>(R.id.rbTypeDaily).isChecked) {
                    val fields = collectCustomFields()
                    buildSmartPricingUI(fields, savedPricing)
                }

                updateWizardUI()

                val savedLimit = savedInstanceState.getString("saved_photo_limit", "3")
                    .ifEmpty { "3" }
                val savedPhotosEnabled = savedInstanceState.getBoolean("saved_photos_enabled", true)
                val spinnerLimit = findViewById<AutoCompleteTextView>(R.id.spinnerPhotoLimit)
                val switchPhotos = findViewById<MaterialSwitch>(R.id.switchPhotos)

                val limits = listOf("1", "2", "3", "4", "5")
                spinnerLimit.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, limits))
                spinnerLimit.setText(savedLimit, false)
                switchPhotos.isChecked = savedPhotosEnabled
                findViewById<View>(R.id.containerPhotoLimit).visibility =
                    if (savedPhotosEnabled) View.VISIBLE else View.GONE

                updateAddFieldButtonVisibility()
                updateRemoveButtonsVisibility()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("saved_step", currentStep)
        outState.putInt("saved_icon", selectedIconResId)
        outState.putBoolean("apply_to_all", applyToAll)

        val spinnerLimit = findViewById<AutoCompleteTextView>(R.id.spinnerPhotoLimit)
        outState.putString("saved_photo_limit", spinnerLimit.text.toString())
        outState.putBoolean("saved_photos_enabled", findViewById<MaterialSwitch>(R.id.switchPhotos).isChecked)

        val currentPricing = arrayListOf<FieldPricing>()
        val container = findViewById<LinearLayout>(R.id.containerPricingRows)
        val startIndex = if (container.childCount > 0 && container.getChildAt(0) is MaterialSwitch) 1 else 0

        for (i in startIndex until container.childCount) {
            val row = container.getChildAt(i)
            val label = row.findViewById<TextView>(R.id.tvFieldLabel)?.text.toString()
            val fixed = row.findViewById<MaterialSwitch>(R.id.switchFixed)?.isChecked ?: false
            val price = row.findViewById<TextInputEditText>(R.id.etPrice)?.text.toString().toDoubleOrNull() ?: 0.0

            currentPricing.add(FieldPricing(label, fixed, price))
        }
        outState.putSerializable("saved_pricing", currentPricing)

        val names = arrayListOf<String>()
        val types = arrayListOf<String>()
        for (i in 0 until containerFields.childCount) {
            val row = containerFields.getChildAt(i)
            val nameText = row.findViewById<TextInputEditText>(R.id.etFieldName).text.toString()
            val typeText = row.findViewById<AutoCompleteTextView>(R.id.spinnerFieldType).text.toString()
            names.add(nameText)
            types.add(typeText)
        }
        outState.putStringArrayList("field_names", names)
        outState.putStringArrayList("field_types", types)
    }

    private fun setupNavigationButtons() {
        btnNext.setOnClickListener {
            val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked

            if (validateCurrentStep()) {
                flipper.setInAnimation(this, R.anim.slide_in_right)
                flipper.setOutAnimation(this, R.anim.slide_out_left)

                when (currentStep) {
                    0 -> {
                        hideKeyboard()
                        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked
                        val currentType = if (isDailyLog) "daily" else "simple"

                        if (currentType != committedLedgerType) {
                            containerFields.removeAllViews()
                            dynamicViewsMap.clear()
                            addNewFieldRow(autoFocus = false)
                            committedLedgerType = currentType
                        }

                        currentStep = 1
                        updateWizardUI()
                    }
                    1 -> {
                        if (isDailyLog && containerFields.childCount == 0) {
                            addNewFieldRow(autoFocus = false)
                        }

                        currentStep = 2
                        updateWizardUI()

                        if (isDailyLog) {
                            hideKeyboard()
                            val fields = collectCustomFields()
                            buildSmartPricingUI(fields)
                        }
                    }
                    else -> validateAndSave()
                }
            }
        }

        btnBack.setOnClickListener {
            if (currentStep > 0) {
                flipper.setInAnimation(this, android.R.anim.slide_in_left)
                flipper.setOutAnimation(this, android.R.anim.slide_out_right)
                hideKeyboard()
                currentStep--
                updateWizardUI()
                if (currentStep == 0) {
                    etLedgerName.requestFocus()
                    showKeyboard(etLedgerName, delayMs = 300)
                }
            }
        }
    }

    private fun updateWizardUI() {
        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked

        val flipperIndex = when (currentStep) {
            0 -> 0
            1 -> if (isDailyLog) 2 else 1
            2 -> if (isDailyLog) 3 else 2
            else -> 0
        }
        flipper.displayedChild = flipperIndex

        val isFieldsScreen = (isDailyLog && currentStep == 1) || (!isDailyLog && currentStep == 2)
        val isPricingScreen = isDailyLog && currentStep == 2

        pendingKeyboardRunnable?.let { keyboardHandler.removeCallbacks(it) }
        pendingKeyboardRunnable = null

        when {
            isPricingScreen -> {
                keyboardHandler.postDelayed({
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    val view = currentFocus ?: window.decorView
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    view.clearFocus()
                    findViewById<LinearLayout>(R.id.containerPricingRows)?.clearFocus()
                    window.decorView.clearFocus()
                }, 400)
            }
            isFieldsScreen -> {
                val firstRow = containerFields.getChildAt(0)
                val et = firstRow?.findViewById<TextInputEditText>(R.id.etFieldName)
                if (et != null) {
                    keyboardHandler.postDelayed({
                        et.requestFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }, 350)
                    pendingKeyboardRunnable = null
                }
            }
        }

        val tvFieldsSubtitle = findViewById<TextView>(R.id.tvFieldsSubtitle)
        if (isDailyLog) {
            tvFieldsSubtitle?.text = "Add columns to track ."
        } else {
            tvFieldsSubtitle?.text = "Date and Amount are already included. Add extra fields if needed, or leave empty."
        }

        val progressValue = when (currentStep) {
            0 -> 33
            1 -> 66
            else -> 100
        }
        progressBar.setProgress(progressValue, true)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val stepTitle = if (isDailyLog) "Setup Monthly Log" else "Setup Records"
        toolbar.title = "$stepTitle (Step ${currentStep + 1}/3)"

        btnBack.visibility = if (currentStep == 0) View.GONE else View.VISIBLE

        if (currentStep == 2) {
            btnNext.text = "Create Ledger"
        } else {
            btnNext.text = "Next"
        }
    }

    private fun buildSmartPricingUI(fields: List<CustomField>, savedData: List<FieldPricing>? = null) {
        val container = findViewById<LinearLayout>(R.id.containerPricingRows)
        container.removeAllViews()

        if (fields.size > 1) {
            val applyToAllSwitch = MaterialSwitch(this).apply {
                text = "Apply same pricing to all fields"
                isChecked = applyToAll // Restore boolean
                setPadding(0, 20, 0, 20)
            }
            container.addView(applyToAllSwitch)
            applyToAllSwitch.setOnCheckedChangeListener { _, isChecked ->
                applyToAll = isChecked
                buildPricingList(fields, isChecked, null)
            }
        }
        buildPricingList(fields, fields.size <= 1 || applyToAll, savedData)
    }

    private fun buildPricingList(fields: List<CustomField>, isUnified: Boolean, savedData: List<FieldPricing>?) {
        val container = findViewById<LinearLayout>(R.id.containerPricingRows)
        while (container.childCount > 1) container.removeViewAt(1)
        if (fields.size <= 1) container.removeAllViews()

        if (isUnified) {
            val data = savedData?.firstOrNull()
            container.addView(createPricingRow("All Fields", true, data))
        } else {
            fields.forEach { field ->
                val data = savedData?.find { it.fieldName == field.fieldName }
                container.addView(createPricingRow(field.fieldName, false, data))
            }
        }
    }

    private fun createPricingRow(label: String, isUnified: Boolean, data: FieldPricing?): View {
        val row = layoutInflater.inflate(R.layout.item_smart_pricing_row, null)
        val tvLabel = row.findViewById<TextView>(R.id.tvFieldLabel)
        val switchFixed = row.findViewById<MaterialSwitch>(R.id.switchFixed)
        val tilPrice = row.findViewById<TextInputLayout>(R.id.tilPrice)
        val etPrice = row.findViewById<TextInputEditText>(R.id.etPrice)

        tvLabel.text = if (isUnified) "Global Pricing" else label
        etPrice.hint = ""
        tilPrice.hint = if (isUnified) "Rate" else "Price"

        if (data != null) {
            switchFixed.isChecked = data.isFixed
            if (data.isFixed) {
                tilPrice.isEnabled = true
                tilPrice.alpha = 1.0f
                if (data.price > 0) etPrice.setText(data.price.toString())
            } else {
                tilPrice.isEnabled = false
                tilPrice.alpha = 0.5f
            }
        } else {
            switchFixed.isChecked = false
            tilPrice.isEnabled = false
            tilPrice.alpha = 0.5f
        }

        switchFixed.setOnCheckedChangeListener { _, isChecked ->
            tilPrice.isEnabled = isChecked
            tilPrice.alpha = if (isChecked) 1.0f else 0.5f

            if (isChecked) {
                etPrice.requestFocus()
                showKeyboard(etPrice)
            } else {
                etPrice.setText("")
                hideKeyboard()
                etPrice.clearFocus()
            }
        }
        return row
    }

    private fun validateCurrentStep(): Boolean {
        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked

        return when (currentStep) {
            0 -> {
                val name = etLedgerName.text.toString().trim()
                if (name.isEmpty()) {
                    etLedgerName.error = "Name required"
                    false
                } else true
            }
            1 -> {
                if (isDailyLog) {
                    val fields = collectCustomFields()
                    if (fields.isEmpty()) {
                        Toast.makeText(this, "Add at least one field name", Toast.LENGTH_SHORT).show()
                        val firstRow = containerFields.getChildAt(0)
                        val et = firstRow?.findViewById<TextInputEditText>(R.id.etFieldName)
                        et?.requestFocus()
                        et?.let { showKeyboard(it) }
                        false
                    } else true
                } else true
            }
            else -> true
        }
    }


    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { handleBackAction() }

        flipper = findViewById(R.id.viewFlipper)
        progressBar = findViewById(R.id.stepProgress)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)
        containerFields = findViewById(R.id.containerFields)
        etLedgerName = findViewById(R.id.etLedgerName)
        ivSelectedIcon = findViewById(R.id.ivSelectedIcon)

        findViewById<View>(R.id.btnSelectIcon).setOnClickListener { showIconPicker() }
        findViewById<Button>(R.id.btnAddField).setOnClickListener { addNewFieldRow() }

        val rgLedgerType = findViewById<RadioGroup>(R.id.rgLedgerType)
        rgLedgerType.setOnCheckedChangeListener { _, checkedId ->
            val isDaily = checkedId == R.id.rbTypeDaily
            for (i in 0 until containerFields.childCount) {
                val row = containerFields.getChildAt(i)
                row.findViewById<View>(R.id.tilFieldType)?.visibility = if (isDaily) View.GONE else View.VISIBLE
            }
            updateRemoveButtonsVisibility()
            updateAddFieldButtonVisibility()
        }
    }

    private fun collectCustomFields(): List<CustomField> {
        val fields = mutableListOf<CustomField>()
        for (i in 0 until containerFields.childCount) {
            val row = containerFields.getChildAt(i)
            val fName = row.findViewById<TextInputEditText>(R.id.etFieldName).text.toString().trim()
            val typeStr = row.findViewById<AutoCompleteTextView>(R.id.spinnerFieldType).text.toString()

            if (fName.isNotEmpty()) {
                val type = when (typeStr) {
                    "Number" -> FieldType.NUMBER
                    "Decimal" -> FieldType.DECIMAL
                    else -> FieldType.TEXT
                }
                fields.add(CustomField(fName, type))
            }
        }
        return fields
    }

    private fun getPhotoLimitValue(): Int {
        val switchPhotos = findViewById<MaterialSwitch>(R.id.switchPhotos)
        if (!switchPhotos.isChecked) return 0
        val limitStr = findViewById<AutoCompleteTextView>(R.id.spinnerPhotoLimit).text.toString()
        return limitStr.toIntOrNull() ?: 3
    }
    private fun setupPhotoSettings() {
        val switchPhotos = findViewById<MaterialSwitch>(R.id.switchPhotos)
        val containerLimit = findViewById<View>(R.id.containerPhotoLimit)
        val spinnerLimit = findViewById<AutoCompleteTextView>(R.id.spinnerPhotoLimit)

        val limits = listOf("1", "2", "3", "4", "5")

        spinnerLimit.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, limits))
        spinnerLimit.setText("3", false)
        containerLimit.visibility = if (switchPhotos.isChecked) View.VISIBLE else View.GONE
        switchPhotos.setOnCheckedChangeListener { _, isChecked ->
            containerLimit.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) hideKeyboard()
        }
    }

    private fun addNewFieldRow(initialName: String = "", initialType: String = "Text", autoFocus: Boolean = true) {
        val row = layoutInflater.inflate(R.layout.item_custom_field_builder, containerFields, false)
        val etNewFieldName = row.findViewById<TextInputEditText>(R.id.etFieldName)
        val spinner = row.findViewById<AutoCompleteTextView>(R.id.spinnerFieldType)
        val tilType = row.findViewById<TextInputLayout>(R.id.tilFieldType)
        val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveField)

        val uniqueRowId = System.currentTimeMillis().toString() + (0..1000).random()
        etNewFieldName.tag = uniqueRowId
        dynamicViewsMap[uniqueRowId] = etNewFieldName

        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked
        tilType.visibility = if (isDailyLog) View.GONE else View.VISIBLE

        etNewFieldName.setText(initialName)
        spinner.setText(initialType, false)

        val types = listOf("Text", "Number", "Decimal")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, types)
        spinner.setAdapter(adapter)
        spinner.setText(initialType, false)
        etNewFieldName.setText(initialName)

        btnRemove.setOnClickListener {
            containerFields.removeView(row)
            dynamicViewsMap.remove(uniqueRowId)
            updateRemoveButtonsVisibility()
            updateAddFieldButtonVisibility()
        }

        containerFields.addView(row)
        updateRemoveButtonsVisibility()
        updateAddFieldButtonVisibility()

        if (autoFocus && initialName.isEmpty()) {
            etNewFieldName.requestFocus()
            showKeyboard(etNewFieldName)
        }
    }

    private fun showKeyboard(view: View, delayMs: Long = 200) {
        pendingKeyboardRunnable = Runnable {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        keyboardHandler.postDelayed(pendingKeyboardRunnable!!, delayMs)
    }

    private fun hideKeyboard() {
        pendingKeyboardRunnable?.let { keyboardHandler.removeCallbacks(it) }
        pendingKeyboardRunnable = null

        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun showIconPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_icon_picker, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val rvIcons = dialogView.findViewById<RecyclerView>(R.id.rvIcons)
        val adapter = IconAdapter(categorizedIcons) { selectedResId ->
            findViewById<ImageView>(R.id.ivSelectedIcon).setImageResource(selectedResId)
            selectedIconResId = selectedResId
            dialog.dismiss()
        }

        val layoutManager = GridLayoutManager(this, 4)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == 0) 4 else 1
            }
        }

        rvIcons.layoutManager = layoutManager
        rvIcons.adapter = adapter

        dialog.show()
    }

    private fun validateAndSave() {
        val name = etLedgerName.text.toString().trim()
        val iconName = resources.getResourceEntryName(selectedIconResId)
        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked

        if (name.isEmpty()) {
            currentStep = 0
            updateWizardUI()
            etLedgerName.error = "Enter a name"
            return
        }

        val selectedType = if (isDailyLog) LedgerType.DAILY_LOG else LedgerType.RECORD
        val selectedDateMode = if (isDailyLog) DateMode.SINGLE
        else when {
            findViewById<RadioButton>(R.id.rbDateRange).isChecked -> DateMode.RANGE
            findViewById<RadioButton>(R.id.rbDateMonth).isChecked -> DateMode.MONTH
            else -> DateMode.SINGLE
        }

        val customFields = collectCustomFields()

        if (selectedType == LedgerType.DAILY_LOG) {
            if (customFields.isEmpty()) {
                Toast.makeText(this, "Please add at least one tracking column", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val pricingMap = mutableMapOf<String, PricingConfig>()
        if (isDailyLog) {
            val pricingContainer = findViewById<LinearLayout>(R.id.containerPricingRows)
            for (i in 0 until pricingContainer.childCount) {
                val row = pricingContainer.getChildAt(i)
                val tvLabel = row.findViewById<TextView>(R.id.tvFieldLabel) ?: continue
                val switchFixed = row.findViewById<MaterialSwitch>(R.id.switchFixed)
                val etPrice = row.findViewById<TextInputEditText>(R.id.etPrice)
                val labelText = tvLabel.text.toString()
                val isFixed = switchFixed.isChecked
                val price = if (isFixed) etPrice.text.toString().toDoubleOrNull() ?: 0.0 else 0.0
                pricingMap[if (applyToAll) "MASTER_GLOBAL_PRICE" else labelText] = PricingConfig(isFixed, price)
            }
        }

        val hasPhotos = if (isDailyLog) false else findViewById<MaterialSwitch>(R.id.switchPhotos).isChecked
        val photoLimit = if (hasPhotos) getPhotoLimitValue() else 0
        val newLedger = CustomLedger(
            id = 0,
            name = name,
            iconName = iconName,
            fields = customFields,
            hasPhotos = hasPhotos,
            photoLimit = photoLimit,
            dateMode = selectedDateMode,
            ledgerType = selectedType,
            unitLabel = if (isDailyLog) Gson().toJson(pricingMap) else null,
            pricePerUnit = if (isDailyLog && pricingMap.values.any { !it.isFixed }) -1.0 else 0.0,
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.customLedgerDao().insertLedger(newLedger)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CreateCustomLedgerActivity, "Ledger '${newLedger.name}' Created!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    private fun showDiscardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<View>(R.id.containerDetails).visibility = View.GONE
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Discard Changes?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "Are you sure you want to stop creating this ledger?"
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)
        btnConfirm.text = "Discard"
        btnConfirm.setOnClickListener { dialog.dismiss(); finish() }
        dialogView.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateRemoveButtonsVisibility() {
        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked
        val count = containerFields.childCount

        for (i in 0 until count) {
            val row = containerFields.getChildAt(i)
            val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveField)
            val shouldShowDelete = !(isDailyLog && count <= 1)
            btnRemove.visibility = if (shouldShowDelete) View.VISIBLE else View.GONE
        }
    }

    private fun updateAddFieldButtonVisibility() {
        val isDailyLog = findViewById<RadioButton>(R.id.rbTypeDaily).isChecked
        val btnAddField = findViewById<Button>(R.id.btnAddField)
        val tvMaxFields = findViewById<TextView>(R.id.tvMaxFieldsNote)

        if (isDailyLog) {
            val count = containerFields.childCount
            if (count >= 3) {
                btnAddField.visibility = View.GONE
                tvMaxFields.visibility = View.VISIBLE
            } else {
                btnAddField.visibility = View.VISIBLE
                tvMaxFields.visibility = View.GONE
            }
        } else {
            btnAddField.visibility = View.VISIBLE
            tvMaxFields.visibility = View.GONE
        }
    }

    private fun handleBackAction() {
        if (isProgressMade()) {
            showDiscardDialog()
        } else {
            finish()
        }
    }

    private fun isProgressMade(): Boolean {
        val iconChanged = selectedIconResId != R.drawable.ic_star
        return etLedgerName.text?.isNotEmpty() == true || containerFields.childCount > 1 || iconChanged
    }
}

sealed class IconItem {
    data class Header(val title: String) : IconItem()
    data class Icon(val resId: Int) : IconItem()
}

private val categorizedIcons = listOf(
    // Utilities
    IconItem.Header("Utilities"),
    IconItem.Icon(R.drawable.ic_bolt), IconItem.Icon(R.drawable.ic_fire),
    IconItem.Icon(R.drawable.ic_water_drop), IconItem.Icon(R.drawable.ic_wifi),
    IconItem.Icon(R.drawable.ic_trash), IconItem.Icon(R.drawable.ic_phone),

    // Transport
    IconItem.Header("Transport"),
    IconItem.Icon(R.drawable.ic_car), IconItem.Icon(R.drawable.ic_bike), IconItem.Icon(R.drawable.ic_bus),

    // Home & Food
    IconItem.Header("Home & Food"),
    IconItem.Icon(R.drawable.ic_home), IconItem.Icon(R.drawable.ic_grass),
    IconItem.Icon(R.drawable.ic_mop), IconItem.Icon(R.drawable.ic_security),
    IconItem.Icon(R.drawable.ic_food), IconItem.Icon(R.drawable.ic_shopping_cart),

    // Leisure & Travel
    IconItem.Header("Leisure & Travel"),
    IconItem.Icon(R.drawable.ic_entertainment), IconItem.Icon(R.drawable.ic_flight),

    // Work & Tools
    IconItem.Header("Work & Tools"),
    IconItem.Icon(R.drawable.ic_laptop), IconItem.Icon(R.drawable.ic_construction),

    // Personal & Health
    IconItem.Header("Personal & Health"),
    IconItem.Icon(R.drawable.ic_gym), IconItem.Icon(R.drawable.ic_medical),
    IconItem.Icon(R.drawable.ic_school), IconItem.Icon(R.drawable.ic_pets),
    IconItem.Icon(R.drawable.ic_family),

    // Admin & Finance
    IconItem.Header("Admin & Finance"),
    IconItem.Icon(R.drawable.ic_attach_money), IconItem.Icon(R.drawable.ic_inventory),
    IconItem.Icon(R.drawable.ic_receipt), IconItem.Icon(R.drawable.ic_star)
)
class IconAdapter(
    private val items: List<IconItem>,
    private val onIconSelected: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ICON = 1
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is IconItem.Header -> TYPE_HEADER
        is IconItem.Icon -> TYPE_ICON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val v = inflater.inflate(R.layout.item_icon_header, parent, false)
            HeaderViewHolder(v)
        } else {
            val v = inflater.inflate(R.layout.item_icon_picker, parent, false)
            IconViewHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is IconItem.Header) {
            holder.tvTitle.text = item.title
        } else if (holder is IconViewHolder && item is IconItem.Icon) {
            holder.ivIcon.setImageResource(item.resId)
            holder.itemView.setOnClickListener { onIconSelected(item.resId) }
        }
    }

    override fun getItemCount() = items.size

    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvIconCategory)
    }

    class IconViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivIcon: ImageView = v.findViewById(R.id.ivIcon)
    }
}