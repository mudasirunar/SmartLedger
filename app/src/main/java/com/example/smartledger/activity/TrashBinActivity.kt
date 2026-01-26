package com.example.smartledger.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
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
import com.example.smartledger.MainActivity
import com.example.smartledger.R
import com.example.smartledger.adapter.TrashAdapter
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.TrashItem
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class TrashBinActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: TrashAdapter
    private lateinit var tvEmpty: View
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trash_bin)

        setupWindowInsets()
        setupUI()
        setupGestures()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (actionMode != null) {
                    actionMode?.finish()
                } else {
                    // Navigate back to Dashboard
                    val intent = Intent(this@TrashBinActivity, MainActivity::class.java) // Change Activity Name accordingly
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
            }
        })

        // Auto Cleanup logic
        lifecycleScope.launch {
            val fifteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(15)
            db.expenseDao().deleteExpiredTrash(fifteenDaysAgo)
            db.electricityDao().deleteExpiredTrash(fifteenDaysAgo)
            db.milkDao().deleteExpiredTrash(fifteenDaysAgo)
        }

        loadTrashItems()
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
        tvEmpty = findViewById(R.id.tvEmpty)

        setSupportActionBar(topAppBar)
        topAppBar.setNavigationOnClickListener { onBackPressed() }

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        navigationView.setNavigationItemSelectedListener(this)

        val recyclerView = findViewById<RecyclerView>(R.id.rvTrash)

        adapter = TrashAdapter(
            onRecoverClick = { item -> showRecoverConfirmationDialog(listOf(item), null) },
            onDeleteClick = { item -> showDeleteConfirmationDialog(listOf(item), null) },
            onLongClick = {
                if (actionMode == null) actionMode = startSupportActionMode(actionModeCallback)
            },
            onSelectionChange = { count ->
                actionMode?.title = "$count Selected"
                updateSelectAllIcon()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadTrashItems() {
        lifecycleScope.launch {
            val expensesFlow = db.expenseDao().getTrashExpenses()
            val electricityFlow = db.electricityDao().getTrashRecords()
            val milkFlow = db.milkDao().getTrashRecords()

            // Combine flows into one List<TrashItem>
            combine(expensesFlow, electricityFlow, milkFlow) { expenses, electrics, milks -> // combine 3 flows
                val list1 = expenses.map { TrashItem.ExpenseItem(it) }
                val list2 = electrics.map { TrashItem.ElectricityItem(it) }
                val list3 = milks.map { TrashItem.MilkItem(it) }
                (list1 + list2 + list3).sortedByDescending { it.deletedAt }
            }.collect { fullList ->
                adapter.submitList(fullList)
                tvEmpty.visibility = if (fullList.isEmpty()) View.VISIBLE else View.GONE
                supportActionBar?.subtitle = "${fullList.size} Items"
            }
        }
    }

    private fun showRecoverConfirmationDialog(items: List<TrashItem>, mode: ActionMode?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<android.view.View>(R.id.containerDetails)
        val tvDetailTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)


        btnConfirm.text = "Recover"

        if (items.size == 1) {
            tvTitle.text = "Recover Item"
            val item = items[0]
            tvMessage.text = "Restore this item?"
            containerDetails.visibility = View.VISIBLE

            // Extract data based on type
            when(item) {
                is TrashItem.ElectricityItem -> {
                    tvDetailTitle.text = "${item.data.totalUnits ?: 0} Units"
                    tvDetailAmount.text = "Rs ${item.data.amount ?: 0.0}"
                }
                is TrashItem.MilkItem -> {
                    tvDetailTitle.text = item.data.monthName
                    tvDetailAmount.text = "Rs ${item.data.totalAmount.toInt()}"
                }
                is TrashItem.ExpenseItem -> {
                    tvDetailTitle.text = item.data.title
                    tvDetailAmount.text = "Rs ${item.data.amount}"
                }
            }
        } else {
            tvTitle.text = "Recover Items"
            tvMessage.text = "Restore these ${items.size} items?"
            containerDetails.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            recoverItems(items, mode) // Call helper function
            dialog.dismiss()
        }
        dialog.show()
    }

    // Helper to perform recovery logic
    private fun recoverItems(items: List<TrashItem>, mode: ActionMode?) {
        lifecycleScope.launch {
            val expenseIds = items.filterIsInstance<TrashItem.ExpenseItem>().map { it.data.id }
            val elecIds = items.filterIsInstance<TrashItem.ElectricityItem>().map { it.data.id }
            val milkIds = items.filterIsInstance<TrashItem.MilkItem>().map { it.data.id }

            withContext(Dispatchers.IO) {
                if (expenseIds.isNotEmpty()) db.expenseDao().restoreExpenses(expenseIds)
                if (elecIds.isNotEmpty()) db.electricityDao().restore(elecIds)
                if (milkIds.isNotEmpty()) db.milkDao().restore(milkIds)
            }

            val toastMessage = if (items.size == 1) {
                "Item Restored"
            } else {
                "${items.size} Items Restored"
            }

            Toast.makeText(this@TrashBinActivity, toastMessage, Toast.LENGTH_SHORT).show()
            mode?.finish()
        }
    }

    private fun showDeleteConfirmationDialog(items: List<TrashItem>, mode: ActionMode?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<android.view.View>(R.id.containerDetails)
        val tvDetailTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<android.widget.TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogConfirm)

        tvTitle.text = "Delete Permanently"
        btnConfirm.text = "Delete"

        if (items.size == 1) {
            val item = items[0]
            tvMessage.text = "Delete this item permanently?"
            containerDetails.visibility = View.VISIBLE

            when(item) {
                is TrashItem.ElectricityItem -> {
                    tvDetailTitle.text = "${item.data.totalUnits ?: 0} Units"
                    tvDetailAmount.text = "Rs ${item.data.amount ?: 0.0}"
                }
                is TrashItem.MilkItem -> {
                    tvDetailTitle.text = item.data.monthName
                    tvDetailAmount.text = "Rs ${item.data.totalAmount.toInt()}"
                }
                is TrashItem.ExpenseItem -> {
                    tvDetailTitle.text = item.data.title
                    tvDetailAmount.text = "Rs ${item.data.amount}"
                }
            }
        } else {
            tvMessage.text = "Delete these ${items.size} items permanently?"
            containerDetails.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            performHardDelete(items, mode)
            dialog.dismiss()
        }
        dialog.show()
    }

    // Helper to perform hard delete logic
    private fun performHardDelete(items: List<TrashItem>, mode: ActionMode?) {
        lifecycleScope.launch {
            val expenseIds = items.filterIsInstance<TrashItem.ExpenseItem>().map { it.data.id }
            val elecIds = items.filterIsInstance<TrashItem.ElectricityItem>().map { it.data.id }
            val milkIds = items.filterIsInstance<TrashItem.MilkItem>().map { it.data.id }

            withContext(Dispatchers.IO) {
                if (expenseIds.isNotEmpty()) db.expenseDao().hardDeleteExpenses(expenseIds)
                if (elecIds.isNotEmpty()) db.electricityDao().hardDelete(elecIds)
                if (milkIds.isNotEmpty()) db.milkDao().hardDelete(milkIds)
            }

            val toastMessage = if (items.size == 1) {
                "Item Deleted Permanently"
            } else {
                "${items.size} Items Deleted Permanently"
            }

            Toast.makeText(this@TrashBinActivity, toastMessage, Toast.LENGTH_SHORT).show()
            mode?.finish()
        }
    }

    private fun showInfoTooltip() {
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

        tvTitle.text = "Trash Info"
        tvMessage.text = "Items in the Trash Bin are stored for 15 days. After that, they are permanently deleted automatically."

        containerDetails.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnConfirm.text = "OK"

        btnConfirm.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- Action Mode ---
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.contextual_trash_menu, menu)
            mode.title = "0 Selected"
            window.statusBarColor = getColor(R.color.teal_dark)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            for(i in 0 until menu.size()) menu.getItem(i).icon?.setTint(getColor(R.color.white))
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = adapter.getSelectedItems()
            return when (item.itemId) {
                R.id.action_select_all -> {
                    if (adapter.isAllSelected()) {
                        adapter.deselectAll()
                        Toast.makeText(this@TrashBinActivity, "Deselected All", Toast.LENGTH_SHORT).show()
                    } else {
                        adapter.selectAll()
                        Toast.makeText(this@TrashBinActivity, "Selected All", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_recover_selection -> {
                    if (selected.isNotEmpty()) {
                        showRecoverConfirmationDialog(selected, mode)
                    } else {
                        Toast.makeText(this@TrashBinActivity, "Select item(s) to recover", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_delete_selection -> {
                    if (selected.isNotEmpty()) {
                        showDeleteConfirmationDialog(selected, mode)
                    } else {
                        Toast.makeText(this@TrashBinActivity, "Select item(s) to delete", Toast.LENGTH_SHORT).show()
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
        }
    }

    private fun updateSelectAllIcon() {
        val item = actionMode?.menu?.findItem(R.id.action_select_all)
        if (adapter.isAllSelected()) item?.setIcon(R.drawable.ic_select_all_active)
        else item?.setIcon(R.drawable.ic_select_all)
        item?.icon?.setTint(getColor(R.color.white))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.trash_menu, menu)
        menu?.findItem(R.id.action_info)?.icon?.setTint(getColor(R.color.white))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_info) {
            showInfoTooltip()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Navigation ---
    override fun onResume() {
        super.onResume()
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_trash)
    }

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
                    finish()
                }
                R.id.nav_milk -> {
                    val intent = Intent(this, MilkActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_expenses -> {
                    val intent = Intent(this, ExpenseActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_analytics -> {
                    val intent = Intent(this, AnalyticsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_trash -> {
                    //Already Here
                }
                R.id.nav_calculator -> {
                    startActivity(Intent(this, CalculatorActivity::class.java))
                }
                R.id.nav_backup,
                R.id.nav_restore,
                R.id.nav_wipe_data -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    Toast.makeText(this, "Manage this setting from Dashboard", Toast.LENGTH_SHORT).show()
                }
            }
        }, 250)
        return true
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = false
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (actionMode != null || e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(vX) > 100) {
                    if (diffX > 0 && !drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}