package com.eipna.centsation.ui.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.data.debt.DebtTransaction;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.util.AlarmSetter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AnalyticsCalculatorTest {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    @Test
    public void computeSavingsMetrics_calculatesNetFlowsCategoriesAndDeadlines() {
        long now = 1_794_380_000_000L;

        Saving tech = new Saving();
        tech.setID("s1");
        tech.setCategory(SavingCategory.TECH.getId());
        tech.setCurrentSaving(300.0);
        tech.setGoal(700.0);
        tech.setDeadline(now + 4L * DAY_MS); // 400 / 4 = 100/day

        Saving dueToday = new Saving();
        dueToday.setID("s2");
        dueToday.setCategory(SavingCategory.TECH.getId());
        dueToday.setCurrentSaving(200.0);
        dueToday.setGoal(350.0);
        dueToday.setDeadline(now); // due today: 150 remaining

        Saving overdue = new Saving();
        overdue.setID("s3");
        overdue.setCategory(SavingCategory.EMERGENCY.getId());
        overdue.setCurrentSaving(100.0);
        overdue.setGoal(500.0);
        overdue.setDeadline(now - 3L * DAY_MS); // overdue: 400 remaining

        Saving done = new Saving();
        done.setID("s4");
        done.setCategory(SavingCategory.EMERGENCY.getId());
        done.setCurrentSaving(500.0);
        done.setGoal(500.0);
        done.setDeadline(AlarmSetter.NO_ALARM);

        Transaction depositRecent = new Transaction();
        depositRecent.setSavingID("s1");
        depositRecent.setType(TransactionType.DEPOSIT.VALUE);
        depositRecent.setAmount(300.0);
        depositRecent.setDate(now - 2L * DAY_MS);

        Transaction withdrawRecent = new Transaction();
        withdrawRecent.setSavingID("s1");
        withdrawRecent.setType(TransactionType.WITHDRAW.VALUE);
        withdrawRecent.setAmount(50.0);
        withdrawRecent.setDate(now - 1L * DAY_MS);

        Transaction deposit20DaysAgo = new Transaction();
        deposit20DaysAgo.setSavingID("s2");
        deposit20DaysAgo.setType(TransactionType.CREATED.VALUE);
        deposit20DaysAgo.setAmount(200.0);
        deposit20DaysAgo.setDate(now - 20L * DAY_MS);

        Transaction depositOld = new Transaction();
        depositOld.setSavingID("s3");
        depositOld.setType(TransactionType.DEPOSIT.VALUE);
        depositOld.setAmount(100.0);
        depositOld.setDate(now - 60L * DAY_MS);

        AnalyticsCalculator.SavingsMetrics metrics = AnalyticsCalculator.computeSavingsMetrics(
                Arrays.asList(tech, dueToday, overdue, done),
                Arrays.asList(depositRecent, withdrawRecent, deposit20DaysAgo, depositOld),
                now
        );

        assertEquals(250.0, metrics.net7Days, 0.001);
        assertEquals(450.0, metrics.net30Days, 0.001);
        assertEquals(3, metrics.inflowCount); // 300, 200, 100
        assertEquals(200.0, metrics.avgInflow, 0.001);
        assertEquals(1100.0, metrics.grandTotalCurrent, 0.001);
        assertEquals(500.0, metrics.categoryTotals.get(SavingCategory.TECH), 0.001);
        assertEquals(600.0, metrics.categoryTotals.get(SavingCategory.EMERGENCY), 0.001);
        assertEquals(250.0, metrics.totalDailyPace, 0.001);
        assertEquals(2, metrics.paceGoalsCount);
        assertEquals(1, metrics.dueTodayCount);
        assertEquals(1, metrics.overdueGoalsCount);
        assertEquals(400.0, metrics.totalOverdueRemaining, 0.001);
    }

    @Test
    public void computeDebtSummary_separatesLiabilitiesReceivablesSnowballAndFilters() {
        long now = 1_794_380_000_000L;

        Debt card = new Debt("d1", "Credit Card", 1000.0, 400.0, "credit_card", Debt.NOT_ARCHIVE, now + 10L * DAY_MS, now - 30L * DAY_MS, Debt.TYPE_I_OWE);
        Debt smallPersonal = new Debt("d2", "Friend", 300.0, 200.0, "personal", Debt.NOT_ARCHIVE, now + 5L * DAY_MS, now - 15L * DAY_MS, Debt.TYPE_I_OWE);
        Debt overdueLoan = new Debt("d3", "Old Loan", 500.0, 100.0, "personal", Debt.NOT_ARCHIVE, now - 2L * DAY_MS, now - 40L * DAY_MS, Debt.TYPE_I_OWE);
        Debt owedToMe = new Debt("d4", "Client Invoice", 2000.0, 500.0, "client_invoice", Debt.NOT_ARCHIVE, now + 15L * DAY_MS, now - 10L * DAY_MS, Debt.TYPE_OWED_TO_ME);

        List<Debt> debts = Arrays.asList(card, smallPersonal, overdueLoan, owedToMe);
        List<DebtTransaction> txs = Arrays.asList(
                new DebtTransaction(1L, "d1", 400.0, now - 2L * DAY_MS, "card"),
                new DebtTransaction(2L, "d2", 200.0, now - 12L * DAY_MS, "friend"),
                new DebtTransaction(3L, "d4", 500.0, now - 5L * DAY_MS, "invoice")
        );

        AnalyticsCalculator.DebtSummaryMetrics all = AnalyticsCalculator.computeDebtSummary(debts, txs, false, false, now);
        assertEquals(1800.0, all.totalIOweOriginal, 0.001);
        assertEquals(700.0, all.totalIOwePaid, 0.001);
        assertEquals(1100.0, all.totalIOweRemaining, 0.001);
        assertEquals(3, all.activeIOweCount);
        assertEquals(2000.0, all.totalOwedOriginal, 0.001);
        assertEquals(1500.0, all.totalOwedRemaining, 0.001);
        assertEquals(1, all.activeOwedCount);
        assertNotNull(all.snowballCandidate);
        assertEquals("d2", all.snowballCandidate.getId()); // 100 remaining is smallest liability
        assertNotNull(all.nextDueCandidate);
        assertEquals("d3", all.nextDueCandidate.getId()); // earliest deadline
        assertEquals(1, all.overdueLiabilityCount);
        assertEquals(400.0, all.liability7Days, 0.001);
        assertEquals(600.0, all.liability30Days, 0.001);
        assertEquals(300.0, all.avgLiabilityPayment, 0.001);
        assertEquals(500.0, all.receivable7Days, 0.001);
        assertEquals(1500.0, all.categoryDebtTotals.get(DebtCategory.CLIENT_INVOICE), 0.001);

        // When filtering I Owe only, Client Invoice receivable category should be excluded
        AnalyticsCalculator.DebtSummaryMetrics iOweOnly = AnalyticsCalculator.computeDebtSummary(debts, txs, true, false, now);
        assertNull(iOweOnly.categoryDebtTotals.get(DebtCategory.CLIENT_INVOICE));
        assertEquals(600.0, iOweOnly.categoryDebtTotals.get(DebtCategory.CREDIT_CARD), 0.001);
    }

    @Test
    public void computeIndividualDebtMetrics_filtersToTargetDebtAndComputesPace() {
        long now = 1_794_380_000_000L;
        Debt debt = new Debt("d1", "Car Loan", 1200.0, 200.0, "car_loan", Debt.NOT_ARCHIVE, now + 10L * DAY_MS, now - 5L * DAY_MS, Debt.TYPE_I_OWE);

        List<DebtTransaction> txs = Arrays.asList(
                new DebtTransaction(1L, "d1", 200.0, now - 3L * DAY_MS, "p1"),
                new DebtTransaction(2L, "other", 999.0, now - 1L * DAY_MS, "ignored")
        );

        AnalyticsCalculator.IndividualDebtMetrics m = AnalyticsCalculator.computeIndividualDebtMetrics(debt, txs, now);
        assertEquals(5L, m.daysSinceCreated);
        assertEquals(10L, m.daysUntilDeadline);
        assertEquals(200.0, m.period7Days, 0.001);
        assertEquals(200.0, m.period30Days, 0.001);
        assertEquals(1, m.paymentCount);
        assertEquals(200.0, m.avgPayment, 0.001);
        assertEquals(100.0, m.dailyPace, 0.001);
        assertEquals(5.0, m.projectedMonthsRemaining, 0.001); // 1000 remaining / 200 per 30d
        assertEquals(1, m.debtTransactions.size());
    }
}
