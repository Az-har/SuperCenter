package com.eipna.centsation.ui.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.eipna.centsation.data.saving.SavingRepository
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

            // 2. Monthly expenses (Withdraw) for current month
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
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
        ) {
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
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.outline
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Balance
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = FormatUtil.formatAmount(data.totalBalance, context),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = GlanceTheme.colors.primary
                            )
                        )
                        Text(
                            text = "This month: -${FormatUtil.formatAmount(data.monthlyExpenses, context)}",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.error
                            )
                        )
                    }

                    if (data.totalDebtOwed > 0 || data.totalDebtLent > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (data.totalDebtOwed > 0) {
                                Text(
                                    text = "Owe: ${FormatUtil.formatAmount(data.totalDebtOwed, context)}",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.error
                                    )
                                )
                            }
                            if (data.totalDebtLent > 0) {
                                Text(
                                    text = "Owed: ${FormatUtil.formatAmount(data.totalDebtLent, context)}",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.primary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Quick Action Buttons Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        text = "+ Log",
                        onClick = actionStartActivity<CreateActivity>(),
                        modifier = GlanceModifier.height(34.dp).width(72.dp)
                    )

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Button(
                        text = "History ❯",
                        onClick = actionStartActivity<HistoryActivity>(),
                        modifier = GlanceModifier.height(34.dp).width(86.dp)
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
