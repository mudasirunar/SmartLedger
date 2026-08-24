package com.mudasir.smartledger.data

data class DashboardTile(
    val id: Int,
    val name: String,
    val iconRes: Int?,
    val iconName: String?,
    val isCustom: Boolean = false,
    val isAddTile: Boolean = false,
    val ledgerTemplate: CustomLedger? = null
)