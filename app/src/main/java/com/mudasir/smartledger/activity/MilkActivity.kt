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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.MainActivity
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.MilkMonthAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.DailyEntry
import com.mudasir.smartledger.data.MilkRecord
import com.mudasir.smartledger.util.AiHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.mudasir.smartledger.util.DrawerNavigationHelper
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs

class MilkActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: MilkMonthAdapter
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var tvEmpty: View
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var actionMode: ActionMode? = null
    private var loadJob: Job? = null

    enum class SortType { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, LITERS_DESC, LITERS_ASC }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_milk)

        setupWindowInsets()
        setupUI()
        setupGestures()
        DrawerNavigationHelper.observeCustomLedgers(this, findViewById(R.id.navigationView))

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (actionMode != null) {
                    actionMode?.finish()
                } else {
                    finish()
                }
            }
        })

        loadRecords(SortType.DATE_DESC)
    }

    private fun setupWindowInsets() {
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)

        setSupportActionBar(topAppBar)
        topAppBar.setNavigationOnClickListener { finish() }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        navigationView.setNavigationItemSelectedListener(this)
        fabAdd = findViewById(R.id.fabAdd)
        tvEmpty = findViewById(R.id.tvEmpty)

        val recyclerView = findViewById<RecyclerView>(R.id.rvMilk)

        adapter = MilkMonthAdapter(
            onNormalClick = { record ->
                if (actionMode != null) {
                } else {
                    val intent = Intent(this, ViewMilkActivity::class.java)
                    intent.putExtra("milk_data", record)
                    startActivity(intent)
                }
            },
            onLongClick = {
                if (actionMode == null) actionMode = startSupportActionMode(actionModeCallback)
            },
            onSelectionChange = { count ->
                actionMode?.title = "$count Selected"
                val selectAllItem = actionMode?.menu?.findItem(R.id.action_select_all)
                if (adapter.isAllSelected()) {
                    selectAllItem?.setIcon(R.drawable.ic_select_all_active)
                } else {
                    selectAllItem?.setIcon(R.drawable.ic_select_all)
                }
                selectAllItem?.icon?.setTint(getColor(R.color.white))
            }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && fabAdd.isShown) fabAdd.hide()
                else if (dy < 0 && !fabAdd.isShown && actionMode == null) fabAdd.show()
            }
        })

        fabAdd.setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_milk, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val spinnerMonth = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerMonth)
        val etYear = dialogView.findViewById<TextInputEditText>(R.id.etYear)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)
        val layoutFeb = dialogView.findViewById<TextInputLayout>(R.id.layoutFebDays)
        val etFebDays = dialogView.findViewById<TextInputEditText>(R.id.etFebDays)
        val btnAdd = dialogView.findViewById<TextView>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)

        lifecycleScope.launch(Dispatchers.IO) {
            val records = db.milkDao().getAllRaw().filter { !it.isDeleted }
            val lastPrice = if (records.isEmpty()) {
                ""
            } else {
                val lastRecord = records.maxByOrNull { it.year * 100 + it.monthIndex }
                val price = lastRecord?.pricePerLiter ?: 0.0
                if (price == price.toLong().toDouble()) price.toLong().toString() else price.toString()
            }

            withContext(Dispatchers.Main) {
                etPrice.setText(lastPrice)
            }
        }

        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, months)
        spinnerMonth.setAdapter(adapter)
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        spinnerMonth.setText(months[currentMonth], false)
        val currentYear = calendar.get(Calendar.YEAR)
        etYear.setText(currentYear.toString())

        spinnerMonth.setOnItemClickListener { _, _, position, _ ->
            if (adapter.getItem(position) == "February") layoutFeb.visibility = View.VISIBLE else layoutFeb.visibility = View.GONE
        }

        if (currentMonth == 1) layoutFeb.visibility = View.VISIBLE

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val priceStr = etPrice.text.toString()
            val yearStr = etYear.text.toString()
            if (priceStr.isEmpty()) { etPrice.error = "Required"; return@setOnClickListener }
            if (yearStr.isEmpty() || yearStr.length != 4) { etYear.error = "Invalid Year"; return@setOnClickListener }

            val selectedYear = yearStr.toInt()
            val monthName = spinnerMonth.text.toString()
            val monthIndex = months.indexOf(monthName)
            val daysInMonth = when(monthIndex) {
                1 -> {
                    val d = etFebDays.text.toString().toIntOrNull()
                    if (d == null || (d != 28 && d != 29)) {
                        etFebDays.error = "Enter 28 or 29"
                        return@setOnClickListener
                    }
                    d
                }
                3, 5, 8, 10 -> 30
                else -> 31
            }

            val dailyEntries = (1..daysInMonth).map { DailyEntry(it, null) }
            val record = MilkRecord(
                monthName = "$monthName $selectedYear",
                monthIndex = monthIndex,
                year = selectedYear,
                pricePerLiter = priceStr.toDouble(),
                dailyEntries = dailyEntries
            )

            lifecycleScope.launch {
                val insertedId = withContext(Dispatchers.IO) {
                    db.milkDao().insert(record)
                }
                val recordWithId = record.copy(id = insertedId.toInt())
                Toast.makeText(this@MilkActivity, "Month Added", Toast.LENGTH_SHORT).show()
                dialog.dismiss()

                val intent = Intent(this@MilkActivity, ViewMilkActivity::class.java)
                intent.putExtra("milk_data", recordWithId)
                startActivity(intent)
            }
        }
        dialog.show()
    }

    private fun loadRecords(sortType: SortType) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val flow = when (sortType) {
                SortType.DATE_DESC -> db.milkDao().getAllByDateDesc()
                SortType.DATE_ASC -> db.milkDao().getAllByDateAsc()
                SortType.AMOUNT_DESC -> db.milkDao().getAllByAmountDesc()
                SortType.AMOUNT_ASC -> db.milkDao().getAllByAmountAsc()
                SortType.LITERS_DESC -> db.milkDao().getAllByLitersDesc()
                SortType.LITERS_ASC -> db.milkDao().getAllByLitersAsc()
            }
            flow.collect { list ->
                adapter.submitList(list) {
                    findViewById<RecyclerView>(R.id.rvMilk).scrollToPosition(0)
                }
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                supportActionBar?.subtitle = "${list.size} Records"
            }
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.contextual_menu, menu)
            mode.title = "0 Selected"
            window.statusBarColor = getColor(R.color.teal_dark)
            fabAdd.hide()
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            for(i in 0 until menu.size()) menu.getItem(i).icon?.setTint(getColor(R.color.white))
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_select_all -> {
                    if (adapter.isAllSelected()) {
                        adapter.deselectAll()
                        Toast.makeText(this@MilkActivity, "Deselected All", Toast.LENGTH_SHORT).show()
                    } else {
                        adapter.selectAll()
                        Toast.makeText(this@MilkActivity, "Selected All", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_delete_selection -> {
                    val selected = adapter.getSelectedItems()
                    if (selected.isNotEmpty()) {
                        showDeleteConfirmationDialog(selected, mode)
                    } else {
                        Toast.makeText(this@MilkActivity, "Select item(s) to delete", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.endSelectionMode()
            actionMode = null
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            window.statusBarColor = getColor(R.color.teal_main)
            fabAdd.show()
        }
    }

    private fun showDeleteConfirmationDialog(items: List<MilkRecord>, mode: ActionMode) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)


        btnConfirm.text = "Delete"

        if(items.size == 1) {
            tvTitle.text = "Delete Record"
            val item = items[0]
            tvMessage.text = "Are you sure you want to delete this record?"
            containerDetails.visibility = View.VISIBLE
            val tvDetailTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailTitle)
            val tvDetailAmount = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailAmount)
            tvDetailTitle.text = item.monthName
            tvDetailAmount.text = "Rs ${item.totalAmount.toInt()}"
        } else {
            tvTitle.text = "Delete Records"
            tvMessage.text = "Are you sure you want to delete these ${items.size} records?"
            containerDetails.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val ids = items.map { it.id }

                withContext(Dispatchers.IO) {
                    db.milkDao().softDelete(ids, System.currentTimeMillis())
                }

                val message = if (ids.size == 1) "Item moved to Trash" else "${ids.size} items moved to Trash"
                Toast.makeText(this@MilkActivity, message, Toast.LENGTH_SHORT).show()

                mode.finish()
            }
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.milk_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_analytics -> {
                val mainIntent = Intent(this, MainActivity::class.java)
                mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                val parentIntent = Intent(this, MilkActivity::class.java)
                val targetIntent = Intent(this, AnalyticsActivity::class.java)
                startActivities(arrayOf(mainIntent, parentIntent, targetIntent))
                true
            }
            R.id.action_ai_analysis -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val list = db.milkDao().getAllRaw().filter { !it.isDeleted }
                    if (list.isNotEmpty()) {
                        val summary = AiHelper.summarizeMilk(list)
                        withContext(Dispatchers.Main) {
                            val intent = Intent(this@MilkActivity, AiInsightActivity::class.java).apply {
                                putExtra("DATA_TYPE", "Milk")
                                putExtra("DATA_SUMMARY", summary)
                                putExtra("RECORD_COUNT", list.size)
                            }
                            startActivity(intent)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MilkActivity, "No data for analysis", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
            R.id.action_select_mode -> {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                    adapter.startSelectionMode(null)
                }
                true
            }
            R.id.sort_default -> {
                item.isChecked = true; loadRecords(SortType.DATE_DESC); Toast.makeText(this, "Sorting: Date (Newest)", Toast.LENGTH_SHORT).show(); true
            }
            R.id.sort_date_asc -> {
                item.isChecked = true; loadRecords(SortType.DATE_ASC); Toast.makeText(this, "Sorting: Date (Oldest)", Toast.LENGTH_SHORT).show(); true
            }
            R.id.sort_amount_desc -> {
                item.isChecked = true; loadRecords(SortType.AMOUNT_DESC); Toast.makeText(this, "Sorting: Amount (High)", Toast.LENGTH_SHORT).show(); true
            }
            R.id.sort_amount_asc -> {
                item.isChecked = true; loadRecords(SortType.AMOUNT_ASC); Toast.makeText(this, "Sorting: Amount (Low)", Toast.LENGTH_SHORT).show(); true
            }
            R.id.sort_liters_desc -> {
                item.isChecked = true; loadRecords(SortType.LITERS_DESC); Toast.makeText(this, "Sorting: Liters (High)", Toast.LENGTH_SHORT).show(); true
            }
            R.id.sort_liters_asc -> {
                item.isChecked = true; loadRecords(SortType.LITERS_ASC); Toast.makeText(this, "Sorting: Liters (Low)", Toast.LENGTH_SHORT).show(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return DrawerNavigationHelper.handleNavigation(this, drawerLayout, item)
    }

    private fun setupGestures() {
        gestureDetector = DrawerNavigationHelper.attachSwipeToOpenDrawer(this, drawerLayout) { actionMode != null }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        val navView = findViewById<NavigationView>(R.id.navigationView)
        DrawerNavigationHelper.updateHeaderLastBackup(this, navView)
        navView.setCheckedItem(R.id.nav_milk)
    }
}