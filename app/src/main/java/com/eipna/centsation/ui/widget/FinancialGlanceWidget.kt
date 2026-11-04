package com.eipna.centsation.ui.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.eipna.centsation.data.Database
import com.eipna.centsation.ui.activities.CreateActivity
import com.eipna.centsation.ui.activities.HistoryActivity
import com.eipna.centsation.ui.activities.MainActivity
import com.eipna.centsation.util.FormatUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class FinancialWidgetData(
    val totalBalance: Double,
    val monthlyExpenses: Double,
    val activeAccountsCount: Int,
    val totalDebtOwed: Double,
    val totalDebtLent: Double
)

class FinancialGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_COMPACT = DpSize(140.dp, 100.dp)
        private val SIZE_WIDE = DpSize(260.dp, 120.dp)
        private val SIZE_EXPANDED = DpSize(340.dp, 180.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_WIDE, SIZE_EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) {
            fetchFinancialData(context)
        }

        provideContent {
            GlanceTheme {
                FinancialWidgetRoot(context = context, data = data)
            }
        }
    }

    private fun fetchFinancialData(context: Context): FinancialWidgetData {
        var totalBalance = 0.0
        var activeAccounts = 0
        var monthlyExpenses = 0.0
        var totalDebtOwed = 0.0
        var totalDebtLent = 0.0

        try {
            val dbHelper = Database.getInstance(context)
            val db = dbHelper.readableDatabase

            // 1. Total balance from active savings
            val savingCursor = db.rawQuery(
                "SELECT ${Database.COLUMN_SAVING_CURRENT_SAVING} FROM ${Database.TABLE_SAVING} WHERE ${Database.COLUMN_SAVING_IS_ARCHIVED} = 0",
                null
            )
            savingCursor.use {
                while (it.moveToNext()) {
                    totalBalance += it.getDouble(0)
                    activeAccounts++
                }
            }

            // 2. Monthly expenses for current month
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfMonth = cal.timeInMillis

            val txCursor = db.rawQuery(
                "SELECT SUM(${Database.COLUMN_TRANSACTION_AMOUNT}) FROM ${Database.TABLE_TRANSACTION} WHERE ${Database.COLUMN_TRANSACTION_TYPE} = 'Withdraw' AND ${Database.COLUMN_TRANSACTION_DATE} >= ?",
                arrayOf(startOfMonth.toString())
            )
            txCursor.use {
                if (it.moveToFirst()) {
                    monthlyExpenses = it.getDouble(0)
                }
            }

            // 3. Debts
            val debtCursor = db.rawQuery(
                "SELECT ${Database.COLUMN_DEBT_TOTAL_AMOUNT}, ${Database.COLUMN_DEBT_AMOUNT_PAID}, ${Database.COLUMN_DEBT_TYPE} FROM ${Database.TABLE_DEBT} WHERE ${Database.COLUMN_DEBT_IS_ARCHIVED} = 0",
                null
            )
            debtCursor.use {
                while (it.moveToNext()) {
                    val total = it.getDouble(0)
                    val paid = it.getDouble(1)
                    val remaining = (total - paid).coerceAtLeast(0.0)
                    val type = it.getString(2) ?: "debt"
                    if ("loan".equals(type, ignoreCase = true)) {
                        totalDebtLent += remaining
                    } else {
                        totalDebtOwed += remaining
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return FinancialWidgetData(
            totalBalance = totalBalance,
            monthlyExpenses = monthlyExpenses,
            activeAccountsCount = activeAccounts,
            totalDebtOwed = totalDebtOwed,
            totalDebtLent = totalDebtLent
        )
    }

    @androidx.compose.runtime.Composable
    private fun FinancialWidgetRoot(context: Context, data: FinancialWidgetData) {
        val size = LocalSize.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
        ) {
            if (size.width < 220.dp || size.height < 110.dp) {
                CompactFinancialLayout(context = context, data = data)
            } else if (size.width >= 320.dp && size.height >= 160.dp) {
                ExpandedFinancialLayout(context = context, data = data)
            } else {
                WideFinancialLayout(context = context, data = data)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CompactFinancialLayout(context: Context, data: FinancialWidgetData) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💳 Balance",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "${data.activeAccountsCount} accts",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.outline)
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = FormatUtil.formatAmount(data.totalBalance, context),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = GlanceTheme.colors.primary
                )
            )

            Text(
                text = "Spent: -${FormatUtil.formatAmount(data.monthlyExpenses, context)}",
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.error)
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Button(
                text = "+ Log Spend",
                onClick = actionStartActivity<CreateActivity>(),
                modifier = GlanceModifier.fillMaxWidth().height(30.dp)
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun WideFinancialLayout(context: Context, data: FinancialWidgetData) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💳 SuperCenter Finance",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "${data.activeAccountsCount} Accounts",
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.outline)
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Balance & Debts Row
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = FormatUtil.formatAmount(data.totalBalance, context),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    Text(
                        text = "This month: -${FormatUtil.formatAmount(data.monthlyExpenses, context)}",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.error)
                    )
                }

                if (data.totalDebtOwed > 0 || data.totalDebtLent > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (data.totalDebtOwed > 0) {
                            Text(
                                text = "Owe: ${FormatUtil.formatAmount(data.totalDebtOwed, context)}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GlanceTheme.colors.error
                                )
                            )
                        }
                        if (data.totalDebtLent > 0) {
                            Text(
                                text = "Owed: ${FormatUtil.formatAmount(data.totalDebtLent, context)}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GlanceTheme.colors.primary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action Buttons Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "+ Log Spend",
                    onClick = actionStartActivity<CreateActivity>(),
                    modifier = GlanceModifier.height(32.dp).width(96.dp)
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                Button(
                    text = "History ❯",
                    onClick = actionStartActivity<HistoryActivity>(),
                    modifier = GlanceModifier.height(32.dp).width(86.dp)
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(
                    text = "Open App ❯",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ExpandedFinancialLayout(context: Context, data: FinancialWidgetData) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "💳 SuperCenter Wealth & Debts",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        text = "Real-time ledger overview • ${data.activeAccountsCount} Active Accounts",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.outline)
                    )
                }

                Button(
                    text = "Open Hub ❯",
                    onClick = actionStartActivity<MainActivity>(),
                    modifier = GlanceModifier.height(30.dp)
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Main Balance Section
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Total Net Balance",
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.outline)
                    )
                    Text(
                        text = FormatUtil.formatAmount(data.totalBalance, context),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Month Spent: -${FormatUtil.formatAmount(data.monthlyExpenses, context)}",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.error
                        )
                    )
                    if (data.totalDebtOwed > 0) {
                        Text(
                            text = "Debts You Owe: ${FormatUtil.formatAmount(data.totalDebtOwed, context)}",
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.error)
                        )
                    }
                    if (data.totalDebtLent > 0) {
                        Text(
                            text = "Loans Owed to You: ${FormatUtil.formatAmount(data.totalDebtLent, context)}",
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.primary)
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Quick Actions
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "+ Log Transaction",
                    onClick = actionStartActivity<CreateActivity>(),
                    modifier = GlanceModifier.height(34.dp).width(136.dp)
                )

                Spacer(modifier = GlanceModifier.width(10.dp))

                Button(
                    text = "Transaction History",
                    onClick = actionStartActivity<HistoryActivity>(),
                    modifier = GlanceModifier.height(34.dp).width(140.dp)
                )
            }
        }
    }
}

class FinancialGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinancialGlanceWidget()
}

fun updateAllFinancialWidgets(context: Context) {
    try {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val glanceComponent = android.content.ComponentName(context, FinancialGlanceReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(glanceComponent)
        if (widgetIds == null || widgetIds.isEmpty()) return
    } catch (e: Exception) {
        // Fallback
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            FinancialGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            // Widget might not be placed
        }
    }
}
