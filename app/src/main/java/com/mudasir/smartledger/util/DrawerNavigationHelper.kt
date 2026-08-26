package com.mudasir.smartledger.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.mudasir.smartledger.MainActivity
import com.mudasir.smartledger.R
import com.mudasir.smartledger.activity.AnalyticsActivity
import com.mudasir.smartledger.activity.CalculatorActivity
import com.mudasir.smartledger.activity.ElectricityActivity
import com.mudasir.smartledger.activity.ExpenseActivity
import com.mudasir.smartledger.activity.GenericLedgerActivity
import com.mudasir.smartledger.activity.MilkActivity
import com.mudasir.smartledger.activity.TrashBinActivity
import com.mudasir.smartledger.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable utility helper for Navigation Drawer operations, dynamic custom ledger menu items,
 * and unified drawer navigation across all activities.
 */
object DrawerNavigationHelper {

    fun handleNavigation(
        activity: AppCompatActivity,
        drawerLayout: DrawerLayout,
        item: MenuItem,
        currentCustomLedgerId: Long? = null,
        onLocalAction: ((Int) -> Boolean)? = null
    ): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        val id = item.itemId

        Handler(Looper.getMainLooper()).postDelayed({
            activity.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(activity)
                val customLedgers = withContext(Dispatchers.IO) { db.customLedgerDao().getAllLedgersList() }
                val clickedLedger = customLedgers.find { (it.id + 1000) == id }

                if (clickedLedger != null) {
                    if (activity is GenericLedgerActivity && currentCustomLedgerId?.toInt() == clickedLedger.id) {
                        return@launch
                    }
                    val intent = Intent(activity, GenericLedgerActivity::class.java)
                    intent.putExtra("ledger_template", clickedLedger)
                    activity.startActivity(intent)
                    if (activity !is MainActivity) activity.finish()
                } else {
                    if (onLocalAction != null && onLocalAction(id)) {
                        return@launch
                    }

                    when (id) {
                        R.id.nav_dashboard -> {
                            if (activity !is MainActivity) {
                                val intent = Intent(activity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                activity.startActivity(intent)
                                activity.finish()
                            }
                        }
                        R.id.nav_electricity -> {
                            if (activity !is ElectricityActivity) {
                                navigateToSection(activity, ElectricityActivity::class.java)
                            }
                        }
                        R.id.nav_milk -> {
                            if (activity !is MilkActivity) {
                                navigateToSection(activity, MilkActivity::class.java)
                            }
                        }
                        R.id.nav_expenses -> {
                            if (activity !is ExpenseActivity) {
                                navigateToSection(activity, ExpenseActivity::class.java)
                            }
                        }
                        R.id.nav_analytics -> {
                            if (activity !is AnalyticsActivity) {
                                navigateToSection(activity, AnalyticsActivity::class.java)
                            }
                        }
                        R.id.nav_trash -> {
                            if (activity !is TrashBinActivity) {
                                val intent = Intent(activity, TrashBinActivity::class.java)
                                activity.startActivity(intent)
                                if (activity !is MainActivity) activity.finish()
                            }
                        }
                        R.id.nav_calculator -> {
                            activity.startActivity(Intent(activity, CalculatorActivity::class.java))
                        }
                        R.id.nav_backup, R.id.nav_restore, R.id.nav_wipe_data -> {
                            if (activity !is MainActivity) {
                                val intent = Intent(activity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                activity.startActivity(intent)
                                activity.finish()
                                Toast.makeText(activity, "Manage these settings from Dashboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }, 250)
        return true
    }

    private fun navigateToSection(activity: AppCompatActivity, targetClass: Class<*>) {
        val mainIntent = Intent(activity, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val targetIntent = Intent(activity, targetClass)
        activity.startActivities(arrayOf(mainIntent, targetIntent))
        if (activity !is MainActivity) activity.finish()
    }

    fun observeCustomLedgers(
        activity: AppCompatActivity,
        navigationView: NavigationView,
        selectedCustomLedgerId: Int? = null
    ) {
        val db = AppDatabase.getDatabase(activity)
        activity.lifecycleScope.launch {
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
                    val iconResId = activity.resources.getIdentifier(ledger.iconName, "drawable", activity.packageName)
                    val menuItem = menu.add(
                        R.id.group_main,
                        ledger.id + 1000,
                        10 + index,
                        ledger.name
                    )
                    menuItem.setIcon(if (iconResId != 0) iconResId else R.drawable.ic_star)
                    menuItem.setCheckable(true)
                    menuItem.icon?.setTint(ContextCompat.getColor(activity, R.color.teal_main))
                    if (selectedCustomLedgerId != null && ledger.id == selectedCustomLedgerId) {
                        menuItem.isChecked = true
                        navigationView.setCheckedItem(menuItem.itemId)
                    }
                }
            }
        }
    }

    fun updateHeaderLastBackup(activity: AppCompatActivity, navigationView: NavigationView) {
        val headerView = navigationView.getHeaderView(0) ?: return
        val tvBackup = headerView.findViewById<TextView>(R.id.tvLastBackup) ?: return
        val prefs = activity.getSharedPreferences("SmartLedgerPrefs", Context.MODE_PRIVATE)
        tvBackup.text = "Last backup: ${prefs.getString("last_backup", "Never")}"
    }
}
