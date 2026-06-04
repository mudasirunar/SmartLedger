package com.example.smartledger.activity

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.Handler
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.MainActivity
import com.example.smartledger.R
import com.example.smartledger.adapter.ExpenseAdapter
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.Expense
import com.example.smartledger.util.AiHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ExpenseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: ExpenseAdapter
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var tvEmpty: View
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Selection & Sort Variables
    private var actionMode: ActionMode? = null
    private var loadExpensesJob: Job? = null

    private val addEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val message = result.data?.getStringExtra("toast_message") ?: "Success"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    enum class SortType { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expense)

        setupWindowInsets()
        setupUI()
        setupGestures()
        observeCustomLedgers()
        loadExpenses(SortType.DATE_DESC)

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
        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)

        adapter = ExpenseAdapter(
            onNormalClick = { expense ->
                val intent = Intent(this, ViewExpenseActivity::class.java)
                intent.putExtra("expense_data", expense)
                startActivity(intent)
            },
            onLongClick = {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
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

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && fabAdd.isShown) fabAdd.hide()
                else if (dy < 0 && !fabAdd.isShown && actionMode == null) fabAdd.show()
            }
        })

        fabAdd.setOnClickListener {
            addEditLauncher.launch(Intent(this, AddEditExpenseActivity::class.java))
        }
    }

    private fun loadExpenses(sortType: SortType) {
        loadExpensesJob?.cancel()
        loadExpensesJob = lifecycleScope.launch {
            val flow = when (sortType) {
                SortType.DATE_DESC -> db.expenseDao().getAllExpensesByDateDesc()
                SortType.DATE_ASC -> db.expenseDao().getAllExpensesByDateAsc()
                SortType.AMOUNT_DESC -> db.expenseDao().getAllExpensesByAmountDesc()
                SortType.AMOUNT_ASC -> db.expenseDao().getAllExpensesByAmountAsc()
            }
            flow.collect { list ->
                adapter.submitList(list) {
                    findViewById<RecyclerView>(R.id.rvExpenses).scrollToPosition(0)
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

            for(i in 0 until menu.size()) {
                menu.getItem(i).icon?.setTint(getColor(R.color.white))
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_select_all -> {
                    if (adapter.isAllSelected()) {
                        adapter.deselectAll()
                        Toast.makeText(this@ExpenseActivity, "Deselected All", Toast.LENGTH_SHORT).show()
                    } else {
                        adapter.selectAll()
                        Toast.makeText(this@ExpenseActivity, "Selected All", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_delete_selection -> {
                    val selectedExpenses = adapter.getSelectedExpenses()

                    if (selectedExpenses.isNotEmpty()) {
                        showDeleteConfirmationDialog(selectedExpenses, mode)
                    } else {
                        Toast.makeText(this@ExpenseActivity, "Select item(s) to delete", Toast.LENGTH_SHORT).show()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.expense_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_analytics -> {
                val mainIntent = Intent(this, MainActivity::class.java)
                mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                val parentIntent = Intent(this, ExpenseActivity::class.java)
                val targetIntent = Intent(this, AnalyticsActivity::class.java)
                startActivities(arrayOf(mainIntent, parentIntent, targetIntent))
                true
            }
            R.id.action_ai_analysis -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val list = db.expenseDao().getAllRaw().filter { !it.isDeleted }
                    if (list.isNotEmpty()) {
                        val summary = AiHelper.summarizeExpenses(list)
                        withContext(Dispatchers.Main) {
                            val intent = Intent(this@ExpenseActivity, AiInsightActivity::class.java).apply {
                                putExtra("DATA_TYPE", "Expense")
                                putExtra("DATA_SUMMARY", summary)
                                putExtra("RECORD_COUNT", list.size)
                            }
                            startActivity(intent)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ExpenseActivity, "No data for analysis", Toast.LENGTH_SHORT).show()
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
                item.isChecked = true
                loadExpenses(SortType.DATE_DESC)
                Toast.makeText(this, "Sorting: Date (Newest)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.sort_date_asc -> {
                item.isChecked = true
                loadExpenses(SortType.DATE_ASC)
                Toast.makeText(this, "Sorting: Date (Oldest)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.sort_amount_desc -> {
                item.isChecked = true
                loadExpenses(SortType.AMOUNT_DESC)
                Toast.makeText(this, "Sorting: Amount (High to Low)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.sort_amount_asc -> {
                item.isChecked = true
                loadExpenses(SortType.AMOUNT_ASC)
                Toast.makeText(this, "Sorting: Amount (Low to High)", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onDown(e: MotionEvent): Boolean {
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (actionMode != null) return false

                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX > 0) {
                        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            drawerLayout.openDrawer(GravityCompat.START)
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        val id = item.itemId

        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                val customLedgers = withContext(Dispatchers.IO) { db.customLedgerDao().getAllLedgersList() }
                val clickedLedger = customLedgers.find { (it.id + 1000) == id }

                if (clickedLedger != null) {
                    val intent = Intent(this@ExpenseActivity, GenericLedgerActivity::class.java)
                    intent.putExtra("ledger_template", clickedLedger)
                    startActivity(intent)
                    finish()
                } else {
                    when (id) {
                        R.id.nav_dashboard -> {
                            val intent = Intent(this@ExpenseActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                        R.id.nav_electricity -> {
                            val mainIntent = Intent(this@ExpenseActivity, MainActivity::class.java)
                            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            val targetIntent = Intent(this@ExpenseActivity, ElectricityActivity::class.java)
                            startActivities(arrayOf(mainIntent, targetIntent))
                        }
                        R.id.nav_milk -> {
                            val mainIntent = Intent(this@ExpenseActivity, MainActivity::class.java)
                            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            val targetIntent = Intent(this@ExpenseActivity, MilkActivity::class.java)
                            startActivities(arrayOf(mainIntent, targetIntent))
                        }
                        R.id.nav_expenses -> { /* Already here */ }
                        R.id.nav_analytics -> {
                            val mainIntent = Intent(this@ExpenseActivity, MainActivity::class.java)
                            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            val targetIntent = Intent(this@ExpenseActivity, AnalyticsActivity::class.java)
                            startActivities(arrayOf(mainIntent, targetIntent))
                        }
                        R.id.nav_trash -> {
                            val intent = Intent(this@ExpenseActivity, TrashBinActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        R.id.nav_calculator -> {
                            startActivity(Intent(this@ExpenseActivity, CalculatorActivity::class.java))
                        }
                        R.id.nav_backup, R.id.nav_restore, R.id.nav_wipe_data -> {
                            val intent = Intent(this@ExpenseActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                            Toast.makeText(this@ExpenseActivity, "Manage these settings from Dashboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }, 250)
        return true
    }

    private fun showDeleteConfirmationDialog(selectedExpenses: List<Expense>, mode: ActionMode) {
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

        btnConfirm.text = "Delete"

        if (selectedExpenses.size == 1) {
            val expense = selectedExpenses[0]
            tvTitle.text = "Delete Record"
            tvMessage.text = "Are you sure you want to delete this record?"
            containerDetails.visibility = View.VISIBLE
            tvDetailTitle.text = expense.title
            tvDetailAmount.text = "Rs ${expense.amount}"

        } else {
            tvTitle.text = "Delete Records"
            tvMessage.text = "Are you sure you want to delete these ${selectedExpenses.size} records?"
            containerDetails.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val idsToDelete = selectedExpenses.map { it.id }
                val currentTimestamp = System.currentTimeMillis()

                withContext(Dispatchers.IO) {
                    db.expenseDao().softDeleteExpenses(idsToDelete, currentTimestamp)
                }

                val message = if (idsToDelete.size == 1) "Item moved to Trash" else "${idsToDelete.size} items moved to Trash"
                Toast.makeText(this@ExpenseActivity, message, Toast.LENGTH_SHORT).show()

                mode.finish()
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        setupHeader()
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_expenses)
    }

    private fun setupHeader() {
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navView.getHeaderView(0)
        val tvBackup = headerView.findViewById<TextView>(R.id.tvLastBackup)
        val prefs = getSharedPreferences("SmartLedgerPrefs", MODE_PRIVATE)
        tvBackup.text = "Last backup: ${prefs.getString("last_backup", "Never")}"
    }
    private fun observeCustomLedgers() {
        lifecycleScope.launch {
            db.customLedgerDao().getAllLedgers().collect { ledgers ->
                val navigationView = findViewById<NavigationView>(R.id.navigationView)
                val menu = navigationView.menu
                val staticIds = setOf(R.id.nav_dashboard, R.id.nav_electricity, R.id.nav_milk, R.id.nav_expenses)
                val toRemove = mutableListOf<Int>()

                for (i in 0 until menu.size()) {
                    val item = menu.getItem(i)
                    if (item.groupId == R.id.group_main && !staticIds.contains(item.itemId)) {
                        toRemove.add(item.itemId)
                    }
                }
                toRemove.forEach { menu.removeItem(it) }

                ledgers.forEachIndexed { index, ledger ->
                    val iconResId = resources.getIdentifier(ledger.iconName, "drawable", packageName)

                    val menuItem = menu.add(
                        R.id.group_main,
                        ledger.id + 1000,
                        10 + index,
                        ledger.name
                    )

                    menuItem.setIcon(if (iconResId != 0) iconResId else R.drawable.ic_star)
                    menuItem.setCheckable(true)
                    menuItem.icon?.setTint(androidx.core.content.ContextCompat.getColor(this@ExpenseActivity, R.color.teal_main))
                }
            }
        }
    }
}