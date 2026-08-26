package com.mudasir.smartledger.activity

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
import com.mudasir.smartledger.MainActivity
import com.mudasir.smartledger.R
import com.mudasir.smartledger.adapter.ExpenseAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.Expense
import com.mudasir.smartledger.util.AiHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.mudasir.smartledger.util.DialogHelper
import com.mudasir.smartledger.util.DrawerNavigationHelper
import com.mudasir.smartledger.util.SelectionActionModeHelper
import com.mudasir.smartledger.util.applySystemBarPadding
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
        DrawerNavigationHelper.observeCustomLedgers(this, findViewById<NavigationView>(R.id.navigationView))
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
        findViewById<View>(R.id.main_content).applySystemBarPadding()
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

        val rv = findViewById<RecyclerView>(R.id.rvExpenses)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Render from RAM cache instantly (0ms) if available
        com.mudasir.smartledger.data.DataCache.cachedExpenses?.let { cached ->
            adapter.submitList(cached)
            rv.alpha = 1f
            tvEmpty.visibility = if (cached.isEmpty()) View.VISIBLE else View.GONE
            supportActionBar?.subtitle = "${cached.size} Records"
        } ?: run {
            rv.alpha = 0f
        }

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                com.mudasir.smartledger.data.DataCache.cachedExpenses = list
                val rv = findViewById<RecyclerView>(R.id.rvExpenses)
                adapter.submitList(list) {
                    if (rv.alpha == 0f) {
                        rv.animate().alpha(1f).setDuration(180).start()
                    }
                    rv.scrollToPosition(0)
                }
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                supportActionBar?.subtitle = "${list.size} Records"
            }
        }
    }

    private val actionModeCallback by lazy {
        SelectionActionModeHelper.setupActionMode(
            activity = this,
            drawerLayout = drawerLayout,
            fabAdd = fabAdd,
            menuResId = R.menu.contextual_menu,
            onActionClicked = { mode, item ->
                when (item.itemId) {
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
            },
            onDestroy = {
                adapter.endSelectionMode()
                actionMode = null
            }
        )
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
        gestureDetector = DrawerNavigationHelper.attachSwipeToOpenDrawer(this, drawerLayout) { actionMode != null }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return DrawerNavigationHelper.handleNavigation(this, drawerLayout, item)
    }

    private fun showDeleteConfirmationDialog(selectedExpenses: List<Expense>, mode: ActionMode) {
        val dialog = DialogHelper.createConfirmationDialog(this) { views ->
            views.btnConfirm.text = "Delete"
            if (selectedExpenses.size == 1) {
                val expense = selectedExpenses[0]
                views.title.text = "Delete Record"
                views.message.text = "Are you sure you want to delete this record?"
                views.details.visibility = View.VISIBLE
                views.detailTitle.text = expense.title
                views.detailAmount.text = "Rs ${expense.amount}"
            } else {
                views.title.text = "Delete Records"
                views.message.text = "Are you sure you want to delete these ${selectedExpenses.size} records?"
                views.details.visibility = View.GONE
            }

            views.btnCancel.setOnClickListener { views.dialog.dismiss() }
            views.btnConfirm.setOnClickListener {
                views.dialog.dismiss()
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
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        val navView = findViewById<NavigationView>(R.id.navigationView)
        DrawerNavigationHelper.updateHeaderLastBackup(this, navView)
        navView.setCheckedItem(R.id.nav_expenses)
    }
}