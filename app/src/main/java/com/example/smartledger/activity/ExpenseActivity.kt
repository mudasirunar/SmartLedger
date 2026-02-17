package com.example.smartledger.activity

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.Handler
import android.view.GestureDetector // Import added
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent // Import added
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs // Import added

class ExpenseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: ExpenseAdapter
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var tvEmpty: View

    // Gesture Detector for Swipe
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
        setupGestures() // Initialize Swipe Logic
        loadExpenses(SortType.DATE_DESC)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (actionMode != null) {
                    actionMode?.finish()
                } else {
                    // Navigate back to Dashboard
                    val intent = Intent(this@ExpenseActivity, MainActivity::class.java) // Change Activity Name accordingly
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
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
        // 1. Navigation & Toolbar
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)

        setSupportActionBar(topAppBar)
        topAppBar.setNavigationOnClickListener { finish() }

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        navigationView.setNavigationItemSelectedListener(this)

        // 2. List & FAB
        fabAdd = findViewById(R.id.fabAdd)
        tvEmpty = findViewById(R.id.tvEmpty)
        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)

        adapter = ExpenseAdapter(
            onNormalClick = { expense ->
                // If standard click, just view details
                val intent = Intent(this, ViewExpenseActivity::class.java)
                intent.putExtra("expense_data", expense)
                startActivity(intent)
            },
            onLongClick = {
                // If Long Click, start selection mode
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
            },
            onSelectionChange = { count ->
                // Update title
                actionMode?.title = "$count Selected"

                // --- NEW: Update Icon based on state ---
                val selectAllItem = actionMode?.menu?.findItem(R.id.action_select_all)
                if (adapter.isAllSelected()) {
                    // Show "Active" icon (Filled Circle)
                    selectAllItem?.setIcon(R.drawable.ic_select_all_active)
                } else {
                    // Show "Normal" icon
                    selectAllItem?.setIcon(R.drawable.ic_select_all)
                }

                // Force tint to white again (just in case)
                selectAllItem?.icon?.setTint(getColor(R.color.white))
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Hide FAB on Scroll
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

    // --- Action Mode (Selection Logic) ---
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.contextual_expense_menu, menu)
            mode.title = "0 Selected"

            // UI Adjustments
            window.statusBarColor = getColor(R.color.teal_dark)
            fabAdd.hide()
            // DISABLE DRAWER SWIPE while selecting
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

            // Force icons to be white
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
                        // Icon updates automatically via onSelectionChange
                        Toast.makeText(this@ExpenseActivity, "Deselected All", Toast.LENGTH_SHORT).show()
                    } else {
                        adapter.selectAll()
                        // Icon updates automatically via onSelectionChange
                        Toast.makeText(this@ExpenseActivity, "Selected All", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_delete_selection -> {
                    // CHANGED: Get actual objects instead of just IDs
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

            // RE-ENABLE DRAWER SWIPE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            window.statusBarColor = getColor(R.color.teal_main)
            fabAdd.show()
        }
    }

    // --- Menu Handling ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.expense_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

    // --- Gesture / Swipe Logic ---
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
                // If Selection Mode is ON, disable swipe
                if (actionMode != null) return false

                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX > 0) { // Left to Right Swipe
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

    // --- Navigation Drawer Handling ---
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        Handler(Looper.getMainLooper()).postDelayed({
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                R.id.nav_electricity -> {
                    val intent = Intent(this, ElectricityActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish() // Close Expense to save memory/stack
                }
                R.id.nav_milk -> {
                    val intent = Intent(this, MilkActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_expenses -> {
                    //Already here
                }
                R.id.nav_analytics -> {
                    val intent = Intent(this, AnalyticsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_trash -> {
                    startActivity(Intent(this, TrashBinActivity::class.java))
                    finish()
                }
                R.id.nav_calculator -> {
                    startActivity(Intent(this, CalculatorActivity::class.java))
                }
                R.id.nav_backup, R.id.nav_restore, R.id.nav_wipe_data -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    Toast.makeText(this, "Manage these settings from Dashboard", Toast.LENGTH_SHORT).show()
                }
            }
        }, 250)
        return true
    }

    // Helper function to show Delete Dialog
    private fun showDeleteConfirmationDialog(selectedExpenses: List<Expense>, mode: ActionMode) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Bind Views
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<android.view.View>(R.id.containerDetails)
        val tvDetailTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)

        // Default Button Text
        btnConfirm.text = "Delete"

        // --- LOGIC TO SHOW DETAILS ---
        if (selectedExpenses.size == 1) {
            // SINGLE ITEM: Show Title and Amount
            val expense = selectedExpenses[0]

            tvTitle.text = "Delete Record"
            tvMessage.text = "Are you sure you want to delete this record?"

            containerDetails.visibility = View.VISIBLE
            tvDetailTitle.text = expense.title
            tvDetailAmount.text = "Rs ${expense.amount}"

        } else {
            // MULTIPLE ITEMS: Hide Details, Show Count
            tvTitle.text = "Delete Records"
            tvMessage.text = "Are you sure you want to delete these ${selectedExpenses.size} records?"

            // Hide the details box because we can't show multiple titles here
            containerDetails.visibility = View.GONE
        }

        // Button Actions
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val idsToDelete = selectedExpenses.map { it.id }
                val currentTimestamp = System.currentTimeMillis()

                withContext(Dispatchers.IO) {
                    db.expenseDao().softDeleteExpenses(idsToDelete, currentTimestamp)
                }

                // Logic for singular/plural Toast
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

        // Use findViewById on the headerView, not the activity
        val tvName = headerView.findViewById<TextView>(R.id.headerName)
        val tvBackup = headerView.findViewById<TextView>(R.id.tvLastBackup)

        val prefs = getSharedPreferences("SmartLedgerPrefs", MODE_PRIVATE)

        // 1. Load the data
        tvName.text = prefs.getString("user_name", "Enter your name")
        tvBackup.text = "Last backup: ${prefs.getString("last_backup", "Never")}"

        // 2. The Safety Check: Only allow editing if we are in MainActivity
        if (this is MainActivity) {
            tvName.setOnClickListener {
                // This call is now safe because 'this' is confirmed as MainActivity
                this.showEditNameDialog(tvName)
            }
        } else {
            // In other activities, remove the edit icon/arrow if you added one
            tvName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvName.setOnClickListener(null)
        }
    }
}