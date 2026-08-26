package com.mudasir.smartledger.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.MainActivity
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.DailyRecordAdapter
import com.mudasir.smartledger.adapter.GenericAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.CustomDailyEntry
import com.mudasir.smartledger.data.CustomDailyRecord
import com.mudasir.smartledger.data.CustomEntry
import com.mudasir.smartledger.data.CustomLedger
import com.mudasir.smartledger.data.PricingConfig
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.mudasir.smartledger.util.DialogHelper
import com.mudasir.smartledger.util.DrawerNavigationHelper
import com.mudasir.smartledger.util.SelectionActionModeHelper
import com.mudasir.smartledger.util.applySystemBarPadding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class GenericLedgerActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var gestureDetector: GestureDetector
    private lateinit var navigationView: NavigationView
    private lateinit var ledger: CustomLedger
    private lateinit var adapter: GenericAdapter
    private lateinit var dailyAdapter: DailyRecordAdapter
    private var actionMode: ActionMode? = null
    private var loadJob: Job? = null
    private val db by lazy { AppDatabase.getDatabase(this) }
    enum class SortType { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_generic_ledger)

        val template = intent.getSerializableExtra("ledger_template")
        if (template is CustomLedger) {
            ledger = template
        } else {
            finish()
            return
        }

        setupWindowInsets()
        setupUI()
        setupGestures()
        DrawerNavigationHelper.observeCustomLedgers(this, navigationView, selectedCustomLedgerId = ledger.id)
        loadRecords(SortType.DATE_DESC)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    navigateToDashboard()
                }
            }
        })
    }

    private fun setupWindowInsets() {
        findViewById<View>(R.id.main_content).applySystemBarPadding()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.generic_ledger_menu, menu)
        return true
    }

    private fun updateActionModeTitle(mode: ActionMode? = actionMode) {
        val count = getActiveSelectionCount()
        mode?.title = "$count Selected"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_mode -> {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                    if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) {
                        dailyAdapter.startSelectionMode(null)
                    } else {
                        adapter.startSelectionMode(null)
                    }
                }
                true
            }

            R.id.sort_date_desc -> {
                item.isChecked = true
                loadRecords(SortType.DATE_DESC)
                Toast.makeText(this, "Sorting: Date (Newest)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.sort_date_asc -> {
                item.isChecked = true
                loadRecords(SortType.DATE_ASC)
                Toast.makeText(this, "Sorting: Date (Oldest)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.sort_amount_desc -> {
                item.isChecked = true
                loadRecords(SortType.AMOUNT_DESC)
                Toast.makeText(this, "Sorting: Amount (High to Low)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.sort_amount_asc -> {
                item.isChecked = true
                loadRecords(SortType.AMOUNT_ASC)
                Toast.makeText(this, "Sorting: Amount (Low to High)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_delete_ledger -> {
                showDeleteLedgerDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadRecords(sortType: SortType) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) {
                val flow = when (sortType) {
                    SortType.DATE_DESC -> db.customLedgerDao().getDailyRecordsByDateDesc(ledger.id)
                    SortType.DATE_ASC -> db.customLedgerDao().getDailyRecordsByDateAsc(ledger.id)
                    SortType.AMOUNT_DESC -> db.customLedgerDao().getDailyRecordsByAmountDesc(ledger.id)
                    SortType.AMOUNT_ASC -> db.customLedgerDao().getDailyRecordsByAmountAsc(ledger.id)
                }

                flow.collect { list ->
                    dailyAdapter.submitList(list) {
                        findViewById<RecyclerView>(R.id.rvGenericRecords).scrollToPosition(0)
                    }
                    findViewById<View>(R.id.tvEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    supportActionBar?.subtitle = "${list.size} Records"
                }
            } else {
                val flow = when (sortType) {
                    SortType.DATE_DESC -> db.customLedgerDao().getEntriesByDateDesc(ledger.id)
                    SortType.DATE_ASC -> db.customLedgerDao().getEntriesByDateAsc(ledger.id)
                    SortType.AMOUNT_DESC -> db.customLedgerDao().getEntriesByAmountDesc(ledger.id)
                    SortType.AMOUNT_ASC -> db.customLedgerDao().getEntriesByAmountAsc(ledger.id)
                }

                flow.collect { list ->
                    adapter.submitList(list) {
                        findViewById<RecyclerView>(R.id.rvGenericRecords).scrollToPosition(0)
                    }
                    findViewById<View>(R.id.tvEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    supportActionBar?.subtitle = "${list.size} Records"
                }
            }
        }
    }

    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigationView)
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)

        setSupportActionBar(toolbar)
        supportActionBar?.title = ledger.name

        toolbar.setNavigationOnClickListener {
            navigateToDashboard()
        }
        navigationView.setNavigationItemSelectedListener(this)

        val rv = findViewById<RecyclerView>(R.id.rvGenericRecords)
        rv.layoutManager = LinearLayoutManager(this)
        if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) {
            dailyAdapter = DailyRecordAdapter(
                ledger = ledger,
                onItemClick = { record ->
                    val intent = Intent(this, CustomDailyActivity::class.java)
                    intent.putExtra("ledger_template", ledger)
                    intent.putExtra("daily_record", record)
                    startActivity(intent)
                },
                onLongClick = {
                    if (actionMode == null) {
                        actionMode = startSupportActionMode(actionModeCallback)
                    }
                },
                onSelectionChange = { count ->
                    actionMode?.title = "$count Selected"
                    updateSelectAllIcon()
                }
            )
            rv.adapter = dailyAdapter
        } else {
            adapter = GenericAdapter(
                ledger = ledger,
                onNormalClick = { entry ->
                    val intent = Intent(this, GenericViewActivity::class.java)
                    intent.putExtra("entry_data", entry)
                    intent.putExtra("ledger_template", ledger)
                    startActivity(intent)
                },
                onLongClick = {
                    if (actionMode == null) {
                        actionMode = startSupportActionMode(actionModeCallback)
                    }
                },
                onSelectionChange = { count ->
                    actionMode?.title = "$count Selected"
                    updateSelectAllIcon()
                }
            )
            rv.adapter = adapter
        }

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && fabAdd.isShown) {
                    fabAdd.hide()
                }
                else if (dy < 0 && !fabAdd.isShown && actionMode == null) {
                    fabAdd.show()
                }
            }
        })

        fabAdd.setOnClickListener {
            if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) {
                showAddDailyLogDialog()
            } else {
                val intent = Intent(this, GenericAddEditActivity::class.java)
                intent.putExtra("ledger_template", ledger)
                startActivity(intent)
            }
        }
    }

    private val actionModeCallback by lazy {
        SelectionActionModeHelper.setupActionMode(
            activity = this,
            drawerLayout = drawerLayout,
            fabAdd = findViewById(R.id.fabAdd),
            menuResId = R.menu.contextual_menu,
            onActionClicked = { mode, item ->
                val isDaily = ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG
                when (item.itemId) {
                    R.id.action_select_all -> {
                        if (isDaily) {
                            if (dailyAdapter.isAllSelected()) dailyAdapter.deselectAll() else dailyAdapter.selectAll()
                        } else {
                            if (adapter.isAllSelected()) adapter.deselectAll() else adapter.selectAll()
                        }
                        updateActionModeTitle(mode)
                        updateSelectAllIcon()
                        true
                    }
                    R.id.action_delete_selection -> {
                        val selected = if (isDaily) dailyAdapter.getSelectedItems() else adapter.getSelectedItems()
                        if (selected.isNotEmpty()) {
                            showDeleteConfirmationDialog(selected, mode)
                        } else {
                            Toast.makeText(this@GenericLedgerActivity, "Select item(s) to delete", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    else -> false
                }
            },
            onDestroy = {
                if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) dailyAdapter.endSelectionMode()
                else adapter.endSelectionMode()
                actionMode = null
            }
        )
    }

    private fun updateSelectAllIcon() {
        val item = actionMode?.menu?.findItem(R.id.action_select_all)
        if (isAllSelectedActive()) {
            item?.setIcon(R.drawable.ic_select_all_active)
        } else {
            item?.setIcon(R.drawable.ic_select_all)
        }
        item?.icon?.setTint(android.graphics.Color.WHITE)
    }

    private fun navigateToDashboard() {
        val intent = Intent(this@GenericLedgerActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun setupGestures() {
        gestureDetector = DrawerNavigationHelper.attachSwipeToOpenDrawer(this, drawerLayout) { actionMode != null }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && actionMode == null) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return DrawerNavigationHelper.handleNavigation(this, drawerLayout, item, currentCustomLedgerId = ledger.id.toLong())
    }



    private fun showAddDailyLogDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_log, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val spinnerMonth = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerMonth)
        val etYear = dialogView.findViewById<TextInputEditText>(R.id.etYear)
        val layoutFeb = dialogView.findViewById<TextInputLayout>(R.id.layoutFebDays)
        val etFebDays = dialogView.findViewById<TextInputEditText>(R.id.etFebDays)
        val containerPricing = dialogView.findViewById<LinearLayout>(R.id.containerDynamicPricing)

        val ledgerPricing: Map<String, PricingConfig> = try {
            Gson().fromJson(ledger.unitLabel, object : TypeToken<Map<String, PricingConfig>>() {}.type)
        } catch (e: Exception) { emptyMap() }

        val isMasterGlobal = ledgerPricing.containsKey("MASTER_GLOBAL_PRICE")
        val masterConfig = ledgerPricing["MASTER_GLOBAL_PRICE"]

        val variableInputs = mutableMapOf<String, TextInputEditText>()

        if (isMasterGlobal) {
            val til = TextInputLayout(
                this, null, com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 24 }
                hint = "Rate (applies to all fields)"
                setBoxStrokeColor(getColor(R.color.teal_main))
                setHintTextColor(android.content.res.ColorStateList.valueOf(getColor(R.color.teal_main)))
            }
            val et = TextInputEditText(til.context).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            if (masterConfig?.isFixed == true) {
                // Fixed — pre-fill and lock
                et.setText(masterConfig.price.toInt().toString())
                et.isEnabled = false
                et.alpha = 0.6f
            } else {
                variableInputs["MASTER_GLOBAL_PRICE"] = et
            }

            til.addView(et)
            containerPricing.addView(til)

        } else {
            ledger.fields.forEach { field ->
                val config = ledgerPricing[field.fieldName]

                val til = TextInputLayout(
                    this, null, com.google.android.material.R.attr.textInputOutlinedStyle
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 24 }
                    hint = "Rate for ${field.fieldName}"
                    setBoxStrokeColor(getColor(R.color.teal_main))
                    setHintTextColor(android.content.res.ColorStateList.valueOf(getColor(R.color.teal_main)))
                }
                val et = TextInputEditText(til.context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                }

                if (config?.isFixed == true) {
                    et.setText(config.price.toInt().toString())
                    et.isEnabled = false
                    et.alpha = 0.6f
                } else {
                    variableInputs[field.fieldName] = et
                }

                til.addView(et)
                containerPricing.addView(til)
            }
        }

        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        spinnerMonth.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, months))
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        spinnerMonth.setText(months[currentMonth], false)
        etYear.setText(currentYear.toString())
        spinnerMonth.setOnItemClickListener { _, _, position, _ ->
            layoutFeb.visibility = if (months[position] == "February") View.VISIBLE else View.GONE
        }
        if (currentMonth == 1) layoutFeb.visibility = View.VISIBLE

        dialogView.findViewById<TextView>(R.id.btnAdd).setOnClickListener {
            var hasError = false
            variableInputs.forEach { (_, et) ->
                if (et.text.isNullOrBlank()) {
                    et.error = "Required"
                    hasError = true
                }
            }
            if (hasError) {
                Toast.makeText(this, "Please fill in all prices", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val monthName = spinnerMonth.text.toString()
            val monthIndex = months.indexOf(monthName)
            val year = etYear.text.toString().toIntOrNull() ?: currentYear
            val daysInMonth = when (monthIndex) {
                1 -> etFebDays.text.toString().toIntOrNull() ?: 28
                3, 5, 8, 10 -> 30
                else -> 31
            }

            val effectivePricing = mutableMapOf<String, Double>()

            if (isMasterGlobal) {
                val price = if (masterConfig?.isFixed == true) {
                    masterConfig.price
                } else {
                    variableInputs["MASTER_GLOBAL_PRICE"]?.text?.toString()?.toDoubleOrNull() ?: 0.0
                }
                effectivePricing["MASTER_GLOBAL_PRICE"] = price
            } else {
                ledger.fields.forEach { field ->
                    val config = ledgerPricing[field.fieldName]
                    val price = if (config?.isFixed == true) {
                        config.price
                    } else {
                        variableInputs[field.fieldName]?.text?.toString()?.toDoubleOrNull() ?: 0.0
                    }
                    effectivePricing[field.fieldName] = price
                }
            }

            val newEntries = (1..daysInMonth).map { CustomDailyEntry(it) }
            val newRecord = CustomDailyRecord(
                ledgerId = ledger.id,
                monthIndex = monthIndex,
                year = year,
                monthName = "$monthName $year",
                dailyEntries = newEntries,
                pricingJson = Gson().toJson(effectivePricing)
            )

            lifecycleScope.launch {
                val insertedId = withContext(Dispatchers.IO) {
                    db.customLedgerDao().insertDailyRecord(newRecord)
                }
                val savedRecord = newRecord.copy(id = insertedId.toInt())
                dialog.dismiss()
                Toast.makeText(this@GenericLedgerActivity, "Month Added", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@GenericLedgerActivity, CustomDailyActivity::class.java)
                intent.putExtra("ledger_template", ledger)
                intent.putExtra("daily_record", savedRecord)
                startActivity(intent)
            }
        }

        dialogView.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    private fun showDeleteConfirmationDialog(items: List<Any>, mode: ActionMode) {
        val dialog = DialogHelper.createConfirmationDialog(this) { views ->
            views.btnConfirm.text = "Delete"

            if (items.size == 1) {
                views.details.visibility = View.VISIBLE
                when (val item = items[0]) {
                    is CustomEntry -> {
                        views.title.text = "Delete Record"
                        views.message.text = "Are you sure you want to delete this record?"
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        val dataMap: Map<String, String>? = try { Gson().fromJson(item.dataJson, type) } catch (e: Exception) { null }
                        val userFields = dataMap?.filterKeys { it != "SYS_END_DATE" }
                        val firstNonEmpty = userFields?.values?.firstOrNull { it.trim().isNotEmpty() }
                        views.detailTitle.text = firstNonEmpty ?: ledger.name
                        views.detailAmount.text = "Rs ${item.amount ?: 0.0}"
                    }
                    is CustomDailyRecord -> {
                        views.title.text = "Delete Record"
                        views.message.text = "Are you sure you want to delete this record?"
                        views.detailTitle.text = item.monthName
                        views.detailAmount.text = "Rs ${item.totalAmount}"
                    }
                }
            } else {
                views.title.text = "Delete Items"
                views.message.text = "Are you sure you want to delete these ${items.size} items?"
                views.details.visibility = View.GONE
            }

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                views.dialog.dismiss()
                val itemType = items[0]
                val count = items.size

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        if (itemType is CustomEntry) {
                            val ids = items.filterIsInstance<CustomEntry>().map { it.id }
                            db.customLedgerDao().softDeleteEntries(ids, System.currentTimeMillis())
                        } else if (itemType is CustomDailyRecord) {
                            val ids = items.filterIsInstance<CustomDailyRecord>().map { it.id }
                            db.customLedgerDao().softDeleteDailyRecords(ids, System.currentTimeMillis())
                        }
                    }
                    mode.finish()
                    val message = if (count == 1) "Item moved to Trash" else "$count items moved to Trash"
                    Toast.makeText(this@GenericLedgerActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteLedgerDialog() {
        val dialog = DialogHelper.createConfirmationDialog(this) { views ->
            views.details.visibility = View.GONE
            views.title.text = "Delete Ledger"
            views.title.setTextColor(resources.getColor(R.color.teal_main, theme))
            views.message.text = "Are you sure you want to delete '${ledger.name}'? This will move all associated records to the Trash Bin."
            views.btnConfirm.text = "Delete"
            views.btnConfirm.setBackgroundResource(R.drawable.bg_btn_pill_red)

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val deleteTime = System.currentTimeMillis()
                    db.customLedgerDao().softDeleteLedger(ledger.id, deleteTime)
                    db.customLedgerDao().softDeleteOnlyActiveEntriesByLedger(ledger.id, deleteTime)
                    db.customLedgerDao().softDeleteActiveDailyRecordsByLedger(ledger.id, deleteTime)
                    withContext(Dispatchers.Main) {
                        views.dialog.dismiss()
                        Toast.makeText(this@GenericLedgerActivity, "${ledger.name} moved to Trash", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        DrawerNavigationHelper.updateHeaderLastBackup(this, navigationView)
        navigationView.setCheckedItem(ledger.id + 1000)
        findViewById<FloatingActionButton>(R.id.fabAdd).show()
    }

    private fun getActiveSelectionCount(): Int {
        return if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) {
            dailyAdapter.getSelectedItems().size
        } else {
            adapter.getSelectedItems().size
        }
    }

    private fun isAllSelectedActive(): Boolean {
        return if (ledger.ledgerType == com.mudasir.smartledger.data.LedgerType.DAILY_LOG) {
            dailyAdapter.isAllSelected()
        } else {
            adapter.isAllSelected()
        }
    }

}