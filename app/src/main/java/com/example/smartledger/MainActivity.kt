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
import com.example.smartledger.activity.*
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.RestoreResult
import com.example.smartledger.util.BackupManager
import com.example.smartledger.util.MilkNotificationHandler
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
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
private const val KEY_USER_NAME = "user_name"
private const val KEY_LAST_BACKUP = "last_backup"
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable
    private var isRestoreCancelled = false
    private var isBackupCancelled = false

    // 1. For Local Backup (SAF Create)
    private val localBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { startBackupProcess(it) }
    }

    // 2. For Local Restore (SAF Open)
    private val localRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startRestoreProcess(uri) }
    }

    // 3. For Drive Restore (Targets the Drive App Result)
    private val driveRestoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> startRestoreProcess(uri) }
        }
    }

    // Launcher to detect when user returns from the Drive app
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
        setupTiles()
        setupGestures()
        startClock()
        setupHeader()

        lifecycleScope.launch(Dispatchers.IO) {
            val fifteenDaysAgo = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(15)

            // Clean all 3 tables
            db.expenseDao().deleteExpiredTrash(fifteenDaysAgo)
            db.electricityDao().deleteExpiredTrash(fifteenDaysAgo)
            db.milkDao().deleteExpiredTrash(fifteenDaysAgo)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted
                    MilkNotificationHandler.scheduleMorningNotification(this)
                }
                else -> {
                    // Request it
                    requestPermissionLauncher.launch(permission)
                }
            }
        } else {
            // Below Android 13, permission is granted at install time
            MilkNotificationHandler.scheduleMorningNotification(this)
        }
    }

    private fun setupHeader() {
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navView.getHeaderView(0)
        val tvName = headerView.findViewById<TextView>(R.id.headerName)
        val tvBackup = headerView.findViewById<TextView>(R.id.tvLastBackup)

        val prefs = getSharedPreferences("SmartLedgerPrefs", MODE_PRIVATE)

        // Load Name & Backup Date
        tvName.text = prefs.getString("user_name", "Enter your name")
        tvBackup.text = "Last backup: ${prefs.getString("last_backup", "Never")}"

        // Click to edit (Dashboard only)
        if (this is MainActivity) {
            tvName.setOnClickListener {
                showEditNameDialog(tvName)
            }
        }
    }

    fun showEditNameDialog(nameTextView: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNameInput)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnDialogCancel)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnDialogConfirm)

        // Pre-fill existing name if available
        val current = nameTextView.text.toString()
        if (current != "Enter your name") etName.setText(current)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isNotEmpty()) {
                val prefs = getSharedPreferences("SmartLedgerPrefs", MODE_PRIVATE)
                val isFirstTime = prefs.getString("user_name", null) == null

                // Save to memory
                prefs.edit().putString("user_name", newName).apply()

                // Refresh the Drawer UI immediately
                setupHeader()

                // Dynamic Toast based on first-time entry or update
                val toastMsg = if (isFirstTime) "Username Added" else "Username Updated"
                Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()

                dialog.dismiss()
            } else {
                etName.error = "Please enter a name"
            }
        }

        dialog.show()

        // Force keyboard to open
        etName.requestFocus()
        Handler(Looper.getMainLooper()).postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // 1. Determine if this is a Destination (Activity) or an Action (Dialog)
        val isAction = item.itemId == R.id.nav_backup ||
                item.itemId == R.id.nav_restore ||
                item.itemId == R.id.nav_wipe_data

        // 2. Close the drawer immediately
        drawerLayout.closeDrawer(GravityCompat.START)

        // 3. Handle the logic
        if (isAction) {
            // Actions happen immediately (No delay needed because they open Dialogs)
            when (item.itemId) {
                R.id.nav_backup -> {
                    showChoiceDialog("Backup Storage", "Choose your backup destination:") { isDrive ->
                        if (isDrive) performDriveBackup() else performLocalBackup()
                    }
                    return false
                }
                R.id.nav_restore -> {
                    showChoiceDialog("Restore Source", "Select where your backup is stored:") { isDrive ->
                        if (isDrive) performDriveRestore() else performLocalRestore()
                    }
                    return false
                }
                R.id.nav_wipe_data -> handleWipeData()
            }
            return false // Do not move the selection "pill" to these actions
        } else {
            // Destinations: Navigate with a slight delay for smooth drawer closing
            handler.postDelayed({
                val intent = when (item.itemId) {
                    R.id.nav_electricity -> Intent(this, ElectricityActivity::class.java)
                    R.id.nav_milk -> Intent(this, MilkActivity::class.java)
                    R.id.nav_expenses -> Intent(this, ExpenseActivity::class.java)
                    R.id.nav_calculator -> Intent(this, CalculatorActivity::class.java)
                    R.id.nav_trash -> Intent(this, TrashBinActivity::class.java)
                    R.id.nav_analytics -> Intent(this, AnalyticsActivity::class.java)
                    else -> null
                }
                intent?.let {
                    it.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(it)
                }
            }, 250)

            return item.itemId != R.id.nav_dashboard // Return true to show the pill (except on dashboard if you want)
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
        containerDetails.visibility = View.GONE // Ensure details are hidden

        // Customize button text for the choice
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

    // WIPE DATA LOGIC ---
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

        // Hide details section since we are wiping everything
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
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun setupTiles() {
        findViewById<MaterialCardView>(R.id.cardElectricity).setOnClickListener {
            startActivity(Intent(this, ElectricityActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardMilk).setOnClickListener {
            startActivity(Intent(this, MilkActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardExpenses).setOnClickListener {
            startActivity(Intent(this, ExpenseActivity::class.java))
        }
        findViewById<ExtendedFloatingActionButton>(R.id.btnAnalytics).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
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
        val sdf = SimpleDateFormat("dd-MMM-yyyy_HHmm", Locale.getDefault())
        val fileName = "SmartLedger_Drive_${sdf.format(Date())}.zip"
        val tempFile = File(cacheDir, fileName)
        val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)

        showLedgerDialog("Drive Backup", "Packing records...") { dialog, views ->
            views.progress.visibility = View.VISIBLE
            views.btnCancel.visibility = View.VISIBLE
            isBackupCancelled = false

            views.btnCancel.setOnClickListener {
                isBackupCancelled = true
                dialog.dismiss()
            }

            lifecycleScope.launch {
                try {
                    // Creation Phase
                    val res = BackupManager.createZipBackup(this@MainActivity, contentUri, db) { isBackupCancelled }

                    withContext(Dispatchers.Main) {
                        // Update UI for Phase 2: Ready to Send
                        views.progress.visibility = View.GONE
                        views.title.text = "Backup Ready"
                        views.message.text = "Active records packed. Send to Drive to upload."

                        // Hide any leftovers from the XML (like that Rs 500 placeholder)
                        views.detailAmount.visibility = View.GONE
                        views.details.visibility = View.VISIBLE

                        views.detailTitle.text = """
                        • Electricity: ${res.elecAdded} Records
                        • Milk Records: ${res.milkAdded} Records
                        • Expenses: ${res.expenseAdded} Records
                    """.trimIndent()

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
                                // Use the launcher so we get the Toast when they come back
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
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // --- LOCAL RESTORE (Phone Storage) ---
    private fun performLocalRestore() {
        val mimeTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
        localRestoreLauncher.launch(mimeTypes)
    }

    // --- DRIVE RESTORE (Direct Drive App Picker) ---
    private fun performDriveRestore() {
        val driveIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            setPackage("com.google.android.apps.docs") // Jumps straight to Drive
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)
        }
        try {
            driveRestoreLauncher.launch(driveIntent)
        } catch (e: Exception) {
            performLocalRestore() // Fallback if Drive app is missing
            Toast.makeText(this, "Drive app not found, using system picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackupProcess(uri: Uri) {
        showLedgerDialog("Backup", "Saving data...") { dialog, views ->
            views.progress.visibility = View.VISIBLE
            views.btnCancel.visibility = View.VISIBLE
            isBackupCancelled = false

            // FIX: Make the cancel button work
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
                        // This calls showSummary which handles the "Done" state
                        showSummary(views, "Backup Done", "File saved successfully", res)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun startRestoreProcess(uri: Uri) {
        showLedgerDialog("Restore", "Importing data...") { dialog, views ->
            views.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val res = BackupManager.restoreFromZip(this@MainActivity, uri, db) { isRestoreCancelled }
                showSummary(views, "Restore Done", "Data integrated successfully", res, true)
            }
        }
    }


    private fun showSummary(views: DialogViews, title: String, msg: String, res: RestoreResult, isRestore: Boolean = false) {
        views.progress.visibility = View.GONE
        views.title.text = title
        views.message.text = msg
        views.details.visibility = View.VISIBLE
        views.btnCancel.visibility = View.GONE
        views.btnConfirm.visibility = View.VISIBLE
        views.btnConfirm.text = if (isRestore) "Finish" else "Done"

        // FIX: Hide the "Rs 500" placeholder
        views.detailAmount.visibility = if (isRestore) View.VISIBLE else View.GONE

        views.detailTitle.text = """
        • Electricity: ${res.elecAdded} Records
        • Milk Records: ${res.milkAdded} Records
        • Expenses: ${res.expenseAdded} Records
    """.trimIndent()

        if (isRestore) {
            val skipped = res.elecSkipped + res.milkSkipped + res.expenseSkipped
            if (skipped > 0) {
                views.detailAmount.visibility = View.VISIBLE
                views.detailAmount.text = "Note: $skipped duplicates were ignored."
            } else {
                views.detailAmount.visibility = View.GONE
            }
        }

        views.btnConfirm.setOnClickListener {
            if (isRestore) recreate() else (views.btnConfirm.context as? Activity)?.recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        setupHeader()
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_dashboard)
    }
}