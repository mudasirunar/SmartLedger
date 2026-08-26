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
import com.mudasir.smartledger.adapter.TrashAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.TrashItem
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

import com.mudasir.smartledger.util.DrawerNavigationHelper
import com.mudasir.smartledger.util.applySystemBarPadding

class TrashBinActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: TrashAdapter
    private lateinit var tvEmpty: View
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Selection variables
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
                    val intent = Intent(this@TrashBinActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
            }
        })

        // Auto Cleanup logic
        lifecycleScope.launch(Dispatchers.IO) {
            val fifteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(15)

            db.expenseDao().deleteExpiredTrash(fifteenDaysAgo)
            db.electricityDao().deleteExpiredTrash(fifteenDaysAgo)
            db.milkDao().deleteExpiredTrash(fifteenDaysAgo)
            db.customLedgerDao().deleteExpiredTrash(fifteenDaysAgo)
            db.customLedgerDao().autoCleanExpiredLedgers(fifteenDaysAgo)
        }

        loadTrashItems()
    }

    private fun setupWindowInsets() {
        findViewById<View>(R.id.main_content).applySystemBarPadding()
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
        DrawerNavigationHelper.observeCustomLedgers(this, navigationView)

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
            val trashedEntriesFlow = db.customLedgerDao().getAllTrashEntries()
            val trashedLedgersFlow = db.customLedgerDao().getTrashedLedgers()
            val trashedDailyRecordsFlow = db.customLedgerDao().getTrashedDailyRecords()

            combine(expensesFlow, electricityFlow, milkFlow, trashedEntriesFlow, trashedLedgersFlow, trashedDailyRecordsFlow) {
                    arrays ->
                val exp = arrays[0] as List<*>
                val elec = arrays[1] as List<*>
                val milk = arrays[2] as List<*>
                val entries = arrays[3] as List<*>
                val ledgers = arrays[4] as List<*>
                val dailyRecords = arrays[5] as List<*>
                val trashedLedgerIds = (ledgers.filterIsInstance<com.mudasir.smartledger.data.CustomLedger>()).map { it.id }.toSet()
                val list1 = exp.filterIsInstance<com.mudasir.smartledger.data.Expense>().map { TrashItem.ExpenseItem(it) }
                val list2 = elec.filterIsInstance<com.mudasir.smartledger.data.Electricity>().map { TrashItem.ElectricityItem(it) }
                val list3 = milk.filterIsInstance<com.mudasir.smartledger.data.MilkRecord>().map { TrashItem.MilkItem(it) }
                val list4 = entries.filterIsInstance<com.mudasir.smartledger.data.CustomEntry>()
                    .filter { it.ledgerId !in trashedLedgerIds }
                    .map { entry ->
                        val ledgerName = db.customLedgerDao().getLedgerById(entry.ledgerId)?.name ?: "Unknown"
                        TrashItem.CustomEntryItem(entry, ledgerName)
                    }
                val list5 = dailyRecords.filterIsInstance<com.mudasir.smartledger.data.CustomDailyRecord>()
                    .filter { it.ledgerId !in trashedLedgerIds }
                    .map { record ->
                        val ledgerName = db.customLedgerDao().getLedgerById(record.ledgerId)?.name ?: "Unknown"
                        TrashItem.CustomDailyRecordItem(record, ledgerName)
                    }

                val list6 = (ledgers.filterIsInstance<com.mudasir.smartledger.data.CustomLedger>()).map { ledger ->
                    val entryCount = if (ledger.deletedAt != null) {
                        val regularCount = db.customLedgerDao().getCountDeletedWithLedger(ledger.id, ledger.deletedAt!!)
                        val dailyCount = db.customLedgerDao().getDailyRecordsCountDeletedWithLedger(ledger.id, ledger.deletedAt!!)
                        regularCount + dailyCount
                    } else 0
                    TrashItem.TrashedLedgerItem(ledger, entryCount)
                }
                (list1 + list2 + list3 + list4 + list5 + list6).sortedByDescending { it.deletedAt }
            }.collect { fullList ->
                adapter.submitList(fullList)
                tvEmpty.visibility = if (fullList.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showRecoverConfirmationDialog(items: List<TrashItem>, mode: ActionMode?) {
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


        btnConfirm.text = "Recover"

        if (items.size == 1) {
            tvTitle.text = "Recover Item"
            val item = items[0]
            tvMessage.text = "Restore this item?"
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
                is TrashItem.TrashedLedgerItem -> {
                    tvDetailTitle.text = item.ledger.name
                    tvDetailAmount.text = "${item.entryCount} Records"
                    tvDetailAmount.setTextColor(getColor(R.color.teal_main))
                }
                is TrashItem.CustomEntryItem -> {
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                    val dataMap: Map<String, String>? = try { Gson().fromJson(item.entry.dataJson, type) } catch (e: Exception) { null }
                    val userFields = dataMap?.filterKeys { it != "SYS_END_DATE" }
                    val firstNonEmpty = userFields?.values?.firstOrNull { it.trim().isNotEmpty() }
                    tvDetailTitle.text = firstNonEmpty ?: item.ledgerName
                    tvDetailAmount.text = "Rs ${item.entry.amount ?: 0.0}"
                }
                is TrashItem.CustomDailyRecordItem -> {
                    tvDetailTitle.text = item.record.monthName
                    tvDetailAmount.text = "Rs ${item.record.totalAmount.toInt()}"
                }
            }
        } else {
            tvTitle.text = "Recover Items"
            tvMessage.text = "Restore these ${items.size} items?"
            containerDetails.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            recoverItems(items, mode)
            dialog.dismiss()
        }
        dialog.show()
    }
    private fun recoverItems(items: List<TrashItem>, mode: ActionMode?) {
        lifecycleScope.launch {
            val expenseIds = items.filterIsInstance<TrashItem.ExpenseItem>().map { it.data.id }
            val elecIds = items.filterIsInstance<TrashItem.ElectricityItem>().map { it.data.id }
            val milkIds = items.filterIsInstance<TrashItem.MilkItem>().map { it.data.id }
            val ledgerItems = items.filterIsInstance<TrashItem.TrashedLedgerItem>()
            val customEntryIds = items.filterIsInstance<TrashItem.CustomEntryItem>().map { it.entry.id }

            withContext(Dispatchers.IO) {
                if (expenseIds.isNotEmpty()) db.expenseDao().restoreExpenses(expenseIds)
                if (elecIds.isNotEmpty()) db.electricityDao().restore(elecIds)
                if (milkIds.isNotEmpty()) db.milkDao().restore(milkIds)
                if (customEntryIds.isNotEmpty()) db.customLedgerDao().restoreEntries(customEntryIds)
                val dailyRecordIds = items.filterIsInstance<TrashItem.CustomDailyRecordItem>().map { it.record.id }
                if (dailyRecordIds.isNotEmpty()) db.customLedgerDao().restoreDailyRecords(dailyRecordIds)

                ledgerItems.forEach {
                    db.customLedgerDao().restoreLedger(it.ledger.id)
                    if (it.ledger.deletedAt != null) {
                        db.customLedgerDao().restoreEntriesByLedgerTimestamp(it.ledger.id, it.ledger.deletedAt!!)
                        db.customLedgerDao().restoreDailyRecordsByLedgerTimestamp(it.ledger.id, it.ledger.deletedAt!!)
                    }
                }
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

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val tvDetailTitle = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailAmount = dialogView.findViewById<TextView>(R.id.tvDetailAmount)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

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
                is TrashItem.TrashedLedgerItem -> {
                    tvDetailTitle.text = item.ledger.name
                    tvDetailAmount.text = "${item.entryCount} Records"
                    tvDetailAmount.setTextColor(getColor(R.color.teal_main))
                }
                is TrashItem.CustomEntryItem -> {
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                    val dataMap: Map<String, String>? = try { Gson().fromJson(item.entry.dataJson, type) } catch (e: Exception) { null }
                    val userFields = dataMap?.filterKeys { it != "SYS_END_DATE" }

                    val firstNonEmpty = userFields?.values?.firstOrNull { it.trim().isNotEmpty() }
                    tvDetailTitle.text = firstNonEmpty ?: item.ledgerName
                    tvDetailAmount.text = "Rs ${item.entry.amount ?: 0.0}"
                }
                is TrashItem.CustomDailyRecordItem -> {
                    tvDetailTitle.text = item.record.monthName
                    tvDetailAmount.text = "Rs ${item.record.totalAmount.toInt()}"
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

    private fun performHardDelete(items: List<TrashItem>, mode: ActionMode?) {
        lifecycleScope.launch {
            val expenseIds = items.filterIsInstance<TrashItem.ExpenseItem>().map { it.data.id }
            val elecIds = items.filterIsInstance<TrashItem.ElectricityItem>().map { it.data.id }
            val milkIds = items.filterIsInstance<TrashItem.MilkItem>().map { it.data.id }
            val ledgerItems = items.filterIsInstance<TrashItem.TrashedLedgerItem>()
            val customEntryIds = items.filterIsInstance<TrashItem.CustomEntryItem>().map { it.entry.id }

            withContext(Dispatchers.IO) {
                if (expenseIds.isNotEmpty()) db.expenseDao().hardDeleteExpenses(expenseIds)
                if (elecIds.isNotEmpty()) db.electricityDao().hardDelete(elecIds)
                if (milkIds.isNotEmpty()) db.milkDao().hardDelete(milkIds)
                if (customEntryIds.isNotEmpty()) db.customLedgerDao().hardDeleteEntries(customEntryIds)
                val dailyRecordIds = items.filterIsInstance<TrashItem.CustomDailyRecordItem>().map { it.record.id }
                if (dailyRecordIds.isNotEmpty()) db.customLedgerDao().hardDeleteDailyRecords(dailyRecordIds)

                ledgerItems.forEach {
                    db.customLedgerDao().permanentlyDeleteLedger(it.ledger.id)
                    db.customLedgerDao().hardDeleteDailyRecordsByLedger(it.ledger.id)
                }            }

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

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

        tvTitle.text = "Trash Info"
        tvMessage.text = "Items in the Trash Bin are stored for 15 days. After that, they are permanently deleted automatically."

        containerDetails.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnConfirm.text = "OK"

        btnConfirm.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

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
        navView.setCheckedItem(R.id.nav_trash)
    }
}