package com.example.smartledger

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
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
import com.example.smartledger.data.BackupData
import com.example.smartledger.util.MilkNotificationHandler
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, schedule the notification
            MilkNotificationHandler.scheduleMorningNotification(this)
        } else {
            Toast.makeText(this, "Enable notifications to record milk daily", Toast.LENGTH_LONG).show()
        }
    }

    // --- LAUNCHERS ---
    private val createJsonFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) performBackup(uri)
    }

    private val openJsonFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) performRestore(uri)
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
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navView.getHeaderView(0)

        val tvName = headerView.findViewById<TextView>(R.id.headerName)

        toolbar.title = "Smart Ledger" // Static Title
        if (tvName != null) tvName.text = "Smart Ledger"
    }

    // --- NAVIGATION ACTIONS ---
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        Handler(Looper.getMainLooper()).postDelayed({
            when (item.itemId) {
                R.id.nav_dashboard -> {}
                R.id.nav_electricity ->
                    startActivity(Intent(this, ElectricityActivity::class.java))
                R.id.nav_milk ->
                    startActivity(Intent(this, MilkActivity::class.java))
                R.id.nav_expenses ->
                    startActivity(Intent(this, ExpenseActivity::class.java))
                R.id.nav_calculator ->
                    startActivity(Intent(this, CalculatorActivity::class.java))
                R.id.nav_trash ->
                    startActivity(Intent(this, TrashBinActivity::class.java))
                R.id.nav_analytics ->
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                R.id.nav_backup -> {
                    val sdf = SimpleDateFormat("dd-MMM-yyyy_hh-mm-a", Locale.getDefault())
                    val fileName = "SmartLedger_Backup_${sdf.format(Date())}.json"
                    createJsonFileLauncher.launch(fileName)
                }
                R.id.nav_restore ->
                    openJsonFileLauncher.launch(arrayOf("application/json"))
                R.id.nav_wipe_data -> handleWipeData()

            }
        }, 250)
        return when(item.itemId) {
            R.id.nav_backup, R.id.nav_restore, R.id.nav_wipe_data -> false
            else -> true
        }
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

    private fun performBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val expenses = db.expenseDao().getAllRaw()
                val electricity = db.electricityDao().getAllRaw()
                val milk = db.milkDao().getAllRaw()
                val backupData = BackupData(System.currentTimeMillis(), expenses, electricity, milk)
                val jsonString = Gson().toJson(backupData)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
                }
                Toast.makeText(this@MainActivity, "Backup Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRestore(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val sb = StringBuilder()
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use {
                        BufferedReader(InputStreamReader(it)).forEachLine { line -> sb.append(line) }
                    }
                }
                val backup = Gson().fromJson(sb.toString(), BackupData::class.java)
                backup.expenses.forEach { db.expenseDao().insertExpense(it) }
                backup.electricity.forEach { db.electricityDao().insert(it) }
                backup.milkRecords.forEach { db.milkDao().insert(it) }

                Toast.makeText(this@MainActivity, "Restored!", Toast.LENGTH_SHORT).show()
                recreate()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Invalid File", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_dashboard)
    }
}