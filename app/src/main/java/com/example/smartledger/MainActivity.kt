package com.example.smartledger

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.activity.AnalyticsActivity
import com.example.smartledger.activity.CalculatorActivity
import com.example.smartledger.activity.CreateCustomLedgerActivity
import com.example.smartledger.activity.ElectricityActivity
import com.example.smartledger.activity.ExpenseActivity
import com.example.smartledger.activity.GenericLedgerActivity
import com.example.smartledger.activity.MilkActivity
import com.example.smartledger.activity.TrashBinActivity
import com.example.smartledger.adapter.DashboardAdapter
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.DashboardTile
import com.example.smartledger.data.RestoreResult
import com.example.smartledger.util.BackupManager
import com.example.smartledger.util.MilkNotificationHandler
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs


private const val PREFS_NAME = "SmartLedgerPrefs"
private const val KEY_LAST_BACKUP = "last_backup"
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable
    private var isRestoreCancelled = false
    private var isBackupCancelled = false

    // 1. For Local Backup
    private val localBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { startBackupProcess(it) }
    }

    // 2. For Local Restore
    private val localRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startRestoreProcess(uri) }
    }

    // 3. For Drive Restore
    private val driveRestoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> startRestoreProcess(uri) }
        }
    }

    private val driveBackupIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Toast.makeText(this, "Backup was sent to Google Drive", Toast.LENGTH_SHORT).show()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            MilkNotificationHandler.scheduleMorningNotification(this)
        } else {
            Toast.makeText(this, "Enable notifications to record milk daily", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        MilkNotificationHandler.scheduleMorningNotification(this)
        checkNotificationPermission()
        setupWindowInsets()
        setupNavigation()
        setupDynamicDashboard()
        setupGestures()
        startClock()
        setupHeader()
        observeCustomLedgers()

        val fabAnalytics = findViewById<ExtendedFloatingActionButton>(R.id.btnAnalytics)

        fabAnalytics.setOnClickListener {
            val intent = Intent(this@MainActivity, AnalyticsActivity::class.java)
            startActivity(intent)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val fifteenDaysAgo = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(15)

            db.expenseDao().deleteExpiredTrash(fifteenDaysAgo)
            db.electricityDao().deleteExpiredTrash(fifteenDaysAgo)
            db.milkDao().deleteExpiredTrash(fifteenDaysAgo)
            db.customLedgerDao().deleteExpiredTrash(fifteenDaysAgo)
            db.customLedgerDao().autoCleanExpiredLedgers(fifteenDaysAgo)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    MilkNotificationHandler.scheduleMorningNotification(this)
                }
                else -> {
                    requestPermissionLauncher.launch(permission)
                }
            }
        } else {
            MilkNotificationHandler.scheduleMorningNotification(this)
        }
    }

    private fun setupHeader() {
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navView.getHeaderView(0)
        val tvBackup = headerView.findViewById<TextView>(R.id.tvLastBackup)
        val prefs = getSharedPreferences("SmartLedgerPrefs", MODE_PRIVATE)
        tvBackup.text = "Last backup: ${prefs.getString("last_backup", "Never")}"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        drawerLayout.closeDrawer(GravityCompat.START)

        val isAction = id == R.id.nav_backup || id == R.id.nav_restore || id == R.id.nav_wipe_data
        if (isAction) {
            handleActionItems(id)
            return false
        }

        handler.postDelayed({
            lifecycleScope.launch {
                val customLedgers = withContext(Dispatchers.IO) { db.customLedgerDao().getAllLedgersList() }
                val clickedLedger = customLedgers.find { (it.id + 1000) == id }

                if (clickedLedger != null) {
                    val intent = Intent(this@MainActivity, GenericLedgerActivity::class.java)
                    intent.putExtra("ledger_template", clickedLedger)
                    startActivity(intent)
                } else {
                    val intent = when (id) {
                        R.id.nav_electricity -> Intent(this@MainActivity, ElectricityActivity::class.java)
                        R.id.nav_milk -> Intent(this@MainActivity, MilkActivity::class.java)
                        R.id.nav_expenses -> Intent(this@MainActivity, ExpenseActivity::class.java)
                        R.id.nav_calculator -> Intent(this@MainActivity, CalculatorActivity::class.java)
                        R.id.nav_trash -> Intent(this@MainActivity, TrashBinActivity::class.java)
                        R.id.nav_analytics -> Intent(this@MainActivity, AnalyticsActivity::class.java)
                        else -> null
                    }
                    intent?.let {
                        it.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        startActivity(it)
                    }
                }
            }
        }, 250)

        return id != R.id.nav_dashboard
    }

    private fun handleActionItems(id: Int) {
        when (id) {
            R.id.nav_backup -> showChoiceDialog("Backup Storage", "Choose destination:") { isDrive ->
                if (isDrive) performDriveBackup() else performLocalBackup()
            }
            R.id.nav_restore -> showChoiceDialog("Restore Source", "Select source:") { isDrive ->
                if (isDrive) performDriveRestore() else performLocalRestore()
            }
            R.id.nav_wipe_data -> handleWipeData()
        }
    }

    private fun showChoiceDialog(title: String, message: String, onChoice: (isDrive: Boolean) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val containerDetails = dialogView.findViewById<View>(R.id.containerDetails)
        val btnLocal = dialogView.findViewById<TextView>(R.id.btnDialogCancel)
        val btnDrive = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

        tvTitle.text = title
        tvMsg.text = message
        containerDetails.visibility = View.GONE

        btnLocal.text = "Local Device"
        btnDrive.text = "Google Drive"

        btnLocal.setOnClickListener {
            dialog.dismiss()
            onChoice(false)
        }
        btnDrive.setOnClickListener {
            dialog.dismiss()
            onChoice(true)
        }
        dialog.show()
    }

    private fun handleWipeData() {
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

        tvTitle.text = "Reset Application"
        tvMessage.text = "This will permanently delete all your local records.\n\nAre you sure?"
        btnConfirm.text = "Delete Everything"

        containerDetails.visibility = View.GONE

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                db.clearAllTables()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "App Reset Successfully", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
        }
        dialog.show()
    }

    private fun setupWindowInsets() {
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        ViewCompat.setOnApplyWindowInsetsListener(topAppBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
    }

    private fun startClock() {
        val txtTime = findViewById<TextView>(R.id.txtCurrentTime)
        val txtDate = findViewById<TextView>(R.id.txtCurrentDate)
        timeRunnable = object : Runnable {
            override fun run() {
                txtTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                txtDate.text = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date())
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeRunnable)
    }

    private fun setupNavigation() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigationView)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                if (diffX > 100 && abs(velocityX) > 100) {
                    if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
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

    private fun showLedgerDialog(
        title: String,
        message: String,
        isCancelable: Boolean = false,
        onAction: (dialog: androidx.appcompat.app.AlertDialog, views: DialogViews) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirmation, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(isCancelable)

        val views = DialogViews(
            title = dialogView.findViewById(R.id.tvDialogTitle),
            message = dialogView.findViewById(R.id.tvDialogMessage),
            progress = dialogView.findViewById(R.id.dialogProgressBar),
            details = dialogView.findViewById(R.id.containerDetails),
            detailTitle = dialogView.findViewById(R.id.tvDetailTitle),
            detailAmount = dialogView.findViewById(R.id.tvDetailAmount),
            btnCancel = dialogView.findViewById(R.id.btnDialogCancel),
            btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm)
        )

        views.title.text = title
        views.message.text = message
        views.details.visibility = View.GONE
        views.btnConfirm.visibility = View.GONE

        onAction(dialog, views)
        dialog.show()
    }

    data class DialogViews(
        val title: TextView, val message: TextView, val progress: LinearProgressIndicator,
        val details: View, val detailTitle: TextView, val detailAmount: TextView,
        val btnCancel: View, val btnConfirm: TextView
    )

    // --- BACKUP & RESTORE PROCESSES ---
    private fun performLocalBackup() {
        val sdf = SimpleDateFormat("dd-MMM-yyyy_HHmm", Locale.getDefault())
        val fileName = "SmartLedger_Local_${sdf.format(Date())}.zip"
        localBackupLauncher.launch(fileName)
    }

    private fun performDriveBackup() {
        cacheDir.listFiles()?.forEach { if (it.name.startsWith("SmartLedger_Drive_")) it.delete() }
        val sdf = SimpleDateFormat("dd-MMM-yyyy_HHmm", Locale.getDefault())
        val fileName = "SmartLedger_Drive_${sdf.format(Date())}.zip"
        val tempFile = File(cacheDir, fileName)

        showLedgerDialog("Drive Backup", "Packing records...") { dialog, views ->
            views.progress.visibility = View.VISIBLE
            views.btnCancel.visibility = View.VISIBLE
            isBackupCancelled = false

            views.btnCancel.setOnClickListener {
                isBackupCancelled = true
                dialog.dismiss()
                Toast.makeText(this@MainActivity, "Backup cancelled.", Toast.LENGTH_SHORT).show()
            }

            lifecycleScope.launch {
                try {
                    if (!tempFile.exists()) tempFile.createNewFile()

                    val contentUri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        tempFile
                    )

                    val res = BackupManager.createZipBackup(this@MainActivity, contentUri, db) { isBackupCancelled }

                    withContext(Dispatchers.Main) {
                        views.progress.visibility = View.GONE
                        views.title.text = "Backup Ready"
                        views.message.text = "Active records packed. Send to Drive to upload."
                        views.detailAmount.visibility = View.GONE
                        views.details.visibility = View.VISIBLE

                        val summary = StringBuilder()
                        if (res.elecAdded > 0) summary.append("• Electricity: ${res.elecAdded}\n")
                        if (res.milkAdded > 0) summary.append("• Milk: ${res.milkAdded}\n")
                        if (res.expenseAdded > 0) summary.append("• Expenses: ${res.expenseAdded}\n")
                        res.customCounts.forEach { (name, count) ->
                            if (count > 0) summary.append("• $name: $count\n")
                        }
                        views.detailTitle.text = summary.toString().trim()

                        views.btnConfirm.visibility = View.VISIBLE
                        views.btnConfirm.text = "Send to Drive"

                        views.btnConfirm.setOnClickListener {
                            dialog.dismiss()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                setPackage("com.google.android.apps.docs")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            try {
                                driveBackupIntentLauncher.launch(Intent.createChooser(shareIntent, "Save to Drive"))

                                val now = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_LAST_BACKUP, now).apply()
                                setupHeader()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Drive app not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        if (e.message != "Backup Stopped") {
                            Toast.makeText(this@MainActivity, "Backup failed. Please try again.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Backup cancelled.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // --- LOCAL RESTORE  ---
    private fun performLocalRestore() {
        val mimeTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
        localRestoreLauncher.launch(mimeTypes)
    }

    // --- DRIVE RESTORE  ---
    private fun performDriveRestore() {
        val driveIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            setPackage("com.google.android.apps.docs")
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)
        }
        try {
            driveRestoreLauncher.launch(driveIntent)
        } catch (e: Exception) {
            performLocalRestore()
            Toast.makeText(this, "Drive app not found, using system picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackupProcess(uri: Uri) {
        showLedgerDialog("Backup", "Saving data...") { dialog, views ->
            views.progress.visibility = View.VISIBLE
            views.btnCancel.visibility = View.VISIBLE
            isBackupCancelled = false

            views.btnCancel.setOnClickListener {
                isBackupCancelled = true
                dialog.dismiss()
            }

            lifecycleScope.launch {
                try {
                    val res = BackupManager.createZipBackup(this@MainActivity, uri, db) { isBackupCancelled }

                    val now = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_LAST_BACKUP, now).apply()

                    withContext(Dispatchers.Main) {
                        setupHeader()
                        showSummary(views, "Backup Done", "File saved successfully", res)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "Backup failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startRestoreProcess(uri: Uri) {
        showLedgerDialog("Restore", "Importing data...") { dialog, views ->
            views.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    val res = BackupManager.restoreFromZip(this@MainActivity, uri, db) { isRestoreCancelled }
                    showSummary(views, "Restore Done", "Data integrated successfully", res, true)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "Restore failed. The file may be corrupted or incompatible.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


    private fun showSummary(views: DialogViews, title: String, msg: String, res: RestoreResult, isRestore: Boolean = false) {
        views.progress.visibility = View.GONE
        views.details.visibility = View.VISIBLE
        views.btnCancel.visibility = View.GONE
        views.btnConfirm.visibility = View.VISIBLE
        views.detailTitle.text = ""
        views.detailAmount.text = ""
        views.detailAmount.visibility = View.GONE
        views.title.text = title
        views.message.text = msg
        views.btnConfirm.text = if (isRestore) "Finish" else "Done"

        val summaryBuilder = StringBuilder()

        if (res.elecAdded > 0) summaryBuilder.append("• Electricity: ${res.elecAdded} Records\n")
        if (res.milkAdded > 0) summaryBuilder.append("• Milk Records: ${res.milkAdded} Records\n")
        if (res.expenseAdded > 0) summaryBuilder.append("• Expenses: ${res.expenseAdded} Records\n")

        res.customCounts.forEach { (name, count) ->
            if (count > 0) {
                summaryBuilder.append("• $name: $count Records\n")
            }
        }

        val totalSkipped = res.elecSkipped + res.milkSkipped + res.expenseSkipped + res.customSkipped

        if (summaryBuilder.isEmpty()) {
            if (totalSkipped > 0) {
                views.detailTitle.text = "No new records to add."
                views.detailAmount.visibility = View.VISIBLE
                views.detailAmount.text = "Note: $totalSkipped records already exist and were skipped."
            } else {
                views.detailTitle.text = "All records were already up to date."
            }
        } else {
            views.detailTitle.text = summaryBuilder.toString().trim()

            if (isRestore && totalSkipped > 0) {
                views.detailAmount.visibility = View.VISIBLE
                views.detailAmount.text = "Note: $totalSkipped duplicates were ignored."
            }
        }

        views.btnConfirm.setOnClickListener {
            if (isRestore) {
                Toast.makeText(this, "Restore complete.", Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                Toast.makeText(this, "Backup saved successfully.", Toast.LENGTH_SHORT).show()
                (it.context as? Activity)?.recreate()
            }
        }
    }

    private fun setupDynamicDashboard() {
        val rv = findViewById<RecyclerView>(R.id.rvDashboard)
        val nestedScroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollMain)
        val fabAnalytics = findViewById<ExtendedFloatingActionButton>(R.id.btnAnalytics)

        nestedScroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY + 20) {
                if (fabAnalytics.isExtended) {
                    fabAnalytics.shrink()
                }
            } else if (scrollY < oldScrollY - 20) {
                if (!fabAnalytics.isExtended) {
                    fabAnalytics.extend()
                }
            }
        }
        val dashboardAdapter = DashboardAdapter { tile ->
            when {
                tile.isAddTile -> startActivity(Intent(this, CreateCustomLedgerActivity::class.java))
                tile.isCustom -> {
                    val intent = Intent(this, GenericLedgerActivity::class.java)
                    intent.putExtra("ledger_template", tile.ledgerTemplate)
                    startActivity(intent)
                }
                else -> navigateToStaticLedger(tile.id)
            }
        }

        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = dashboardAdapter
        rv.isNestedScrollingEnabled = false

        lifecycleScope.launch {
            db.customLedgerDao().getAllLedgers().collect { customLedgers ->
                val tiles = mutableListOf<DashboardTile>()

                tiles.add(DashboardTile(1, "Electricity", R.drawable.ic_bolt, null))
                tiles.add(DashboardTile(2, "Milk Records", R.drawable.ic_water_drop, null))
                tiles.add(DashboardTile(3, "Expenses", R.drawable.ic_attach_money, null))
                customLedgers.forEach { ledger ->
                    tiles.add(DashboardTile(ledger.id + 100, ledger.name, null, ledger.iconName, true, false, ledger))
                }

                tiles.add(DashboardTile(-1, "Add Ledger", null, "ic_add", false, true))

                dashboardAdapter.submitList(tiles)
            }
        }
    }

    private fun observeCustomLedgers() {
        lifecycleScope.launch {
            db.customLedgerDao().getAllLedgers().collect { ledgers ->
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
                    menuItem.icon?.setTint(ContextCompat.getColor(this@MainActivity, R.color.teal_main))
                }
            }
        }
    }

    private fun navigateToStaticLedger(id: Int) {
        val intent = when (id) {
            1 -> Intent(this, ElectricityActivity::class.java)
            2 -> Intent(this, MilkActivity::class.java)
            3 -> Intent(this, ExpenseActivity::class.java)
            else -> null
        }
        intent?.let { startActivity(it) }
    }

    override fun onResume() {
        super.onResume()
        setupHeader()
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_dashboard)
    }
}