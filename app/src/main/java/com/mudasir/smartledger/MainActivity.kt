package com.mudasir.smartledger

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
import com.mudasir.smartledger.activity.AnalyticsActivity
import com.mudasir.smartledger.activity.CalculatorActivity
import com.mudasir.smartledger.activity.CreateCustomLedgerActivity
import com.mudasir.smartledger.activity.ElectricityActivity
import com.mudasir.smartledger.activity.ExpenseActivity
import com.mudasir.smartledger.activity.GenericLedgerActivity
import com.mudasir.smartledger.activity.MilkActivity
import com.mudasir.smartledger.activity.TrashBinActivity
import com.mudasir.smartledger.adapter.DashboardAdapter
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.DashboardTile
import com.mudasir.smartledger.data.RestoreResult
import com.mudasir.smartledger.util.BackupManager
import com.mudasir.smartledger.util.MilkNotificationHandler
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


import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mudasir.smartledger.util.DialogHelper
import com.mudasir.smartledger.util.DrawerNavigationHelper
import com.mudasir.smartledger.util.applySystemBarPadding

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
        installSplashScreen()
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
        DrawerNavigationHelper.observeCustomLedgers(this, navigationView)

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
        return DrawerNavigationHelper.handleNavigation(this, drawerLayout, item) { id ->
            if (id == R.id.nav_backup || id == R.id.nav_restore || id == R.id.nav_wipe_data) {
                handleActionItems(id)
                true
            } else false
        }
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
                }
            }
        }
        dialog.show()
    }

    private fun setupWindowInsets() {
        findViewById<View>(R.id.main_content).applySystemBarPadding()
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
        gestureDetector = DrawerNavigationHelper.attachSwipeToOpenDrawer(this, drawerLayout)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun showLedgerDialog(
        title: String,
        message: String,
        isCancelable: Boolean = false,
        onAction: (dialog: androidx.appcompat.app.AlertDialog, views: DialogHelper.DialogViews) -> Unit
    ) {
        val dialog = DialogHelper.createConfirmationDialog(this, title, message, isCancelable) { views ->
            views.details.visibility = View.GONE
            views.btnConfirm.visibility = View.GONE
            onAction(views.dialog, views)
        }
        dialog.show()
    }

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


    private fun showSummary(views: DialogHelper.DialogViews, title: String, msg: String, res: RestoreResult, isRestore: Boolean = false) {
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
        if (res.milkAdded > 0) summaryBuilder.append("• Milk: ${res.milkAdded} Records\n")
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
            views.dialog.dismiss()
            if (isRestore) {
                Toast.makeText(this, "Restore complete.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Backup saved successfully.", Toast.LENGTH_SHORT).show()
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
                    val intent = Intent(this, GenericLedgerActivity::class.java).apply {
                        putExtra("ledger_template", tile.ledgerTemplate)
                        putExtra("ledger_id", tile.ledgerTemplate?.id ?: 0)
                    }
                    startActivity(intent)
                }
                else -> navigateToStaticLedger(tile.id)
            }
        }

        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = dashboardAdapter
        rv.isNestedScrollingEnabled = false
        rv.alpha = 0f

        lifecycleScope.launch {
            db.customLedgerDao().getAllLedgers().collect { customLedgers ->
                val tiles = mutableListOf<DashboardTile>()

                tiles.add(DashboardTile(1, "Electricity", R.drawable.ic_bolt, null))
                tiles.add(DashboardTile(2, "Milk", R.drawable.ic_water_drop, null))
                tiles.add(DashboardTile(3, "Expenses", R.drawable.ic_attach_money, null))
                customLedgers.forEach { ledger ->
                    tiles.add(DashboardTile(ledger.id + 100, ledger.name, null, ledger.iconName, true, false, ledger))
                }

                tiles.add(DashboardTile(-1, "Add Ledger", null, "ic_add", false, true))

                dashboardAdapter.submitList(tiles) {
                    if (rv.alpha == 0f) {
                        rv.animate().alpha(1f).setDuration(180).start()
                    }
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
        DrawerNavigationHelper.updateHeaderLastBackup(this, navigationView)
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_dashboard)
    }
}