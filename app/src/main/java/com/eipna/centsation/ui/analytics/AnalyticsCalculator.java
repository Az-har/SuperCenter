package com.eipna.centsation.ui.analytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.data.debt.DebtTransaction;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.SavingsCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure JVM analytics math for Savings Insights and Debt Insights.
 * Keeps all velocity, calendar-day pacing, and forecast calculations testable without Android Views.
 */
public final class AnalyticsCalculator {

    public static final long SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L;
    public static final long THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L;

    private AnalyticsCalculator() {}

    public static final class SavingsMetrics {
        public final double net7Days;
        public final double net30Days;
        public final int inflowCount;
        public final double avgInflow;
        public final double grandTotalCurrent;
        public final Map<SavingCategory, Double> categoryTotals;
        public final double totalDailyPace;
        public final int paceGoalsCount;
        public final int dueTodayCount;
        public final int overdueGoalsCount;
        public final double totalOverdueRemaining;

        SavingsMetrics(double net7Days, double net30Days, int inflowCount, double avgInflow,
                       double grandTotalCurrent, Map<SavingCategory, Double> categoryTotals,
                       double totalDailyPace, int paceGoalsCount, int dueTodayCount,
                       int overdueGoalsCount, double totalOverdueRemaining) {
            this.net7Days = net7Days;
            this.net30Days = net30Days;
            this.inflowCount = inflowCount;
            this.avgInflow = avgInflow;
            this.grandTotalCurrent = grandTotalCurrent;
            this.categoryTotals = categoryTotals;
            this.totalDailyPace = totalDailyPace;
            this.paceGoalsCount = paceGoalsCount;
            this.dueTodayCount = dueTodayCount;
            this.overdueGoalsCount = overdueGoalsCount;
            this.totalOverdueRemaining = totalOverdueRemaining;
        }
    }

    public static final class DebtSummaryMetrics {
        public final double totalIOweOriginal;
        public final double totalIOwePaid;
        public final double totalIOweRemaining;
        public final int activeIOweCount;

        public final double totalOwedOriginal;
        public final double totalOwedPaid;
        public final double totalOwedRemaining;
        public final int activeOwedCount;

        public final Map<DebtCategory, Double> categoryDebtTotals;
        @Nullable public final Debt snowballCandidate;
        @Nullable public final Debt nextDueCandidate;
        public final double totalDailyPace;
        public final int paceGoalsCount;
        public final int dueTodayLiabilityCount;
        public final int overdueLiabilityCount;

        public final double liability7Days;
        public final double liability30Days;
        public final int liabilityPaymentCount;
        public final double avgLiabilityPayment;

        public final double receivable7Days;
        public final double receivable30Days;
        public final int receivablePaymentCount;
        public final double avgReceivablePayment;

        DebtSummaryMetrics(
                double totalIOweOriginal, double totalIOwePaid, double totalIOweRemaining, int activeIOweCount,
                double totalOwedOriginal, double totalOwedPaid, double totalOwedRemaining, int activeOwedCount,
                Map<DebtCategory, Double> categoryDebtTotals, @Nullable Debt snowballCandidate,
                @Nullable Debt nextDueCandidate, double totalDailyPace, int paceGoalsCount,
                int dueTodayLiabilityCount, int overdueLiabilityCount,
                double liability7Days, double liability30Days, int liabilityPaymentCount, double avgLiabilityPayment,
                double receivable7Days, double receivable30Days, int receivablePaymentCount, double avgReceivablePayment) {
            this.totalIOweOriginal = totalIOweOriginal;
            this.totalIOwePaid = totalIOwePaid;
            this.totalIOweRemaining = totalIOweRemaining;
            this.activeIOweCount = activeIOweCount;
            this.totalOwedOriginal = totalOwedOriginal;
            this.totalOwedPaid = totalOwedPaid;
            this.totalOwedRemaining = totalOwedRemaining;
            this.activeOwedCount = activeOwedCount;
            this.categoryDebtTotals = categoryDebtTotals;
            this.snowballCandidate = snowballCandidate;
            this.nextDueCandidate = nextDueCandidate;
            this.totalDailyPace = totalDailyPace;
            this.paceGoalsCount = paceGoalsCount;
            this.dueTodayLiabilityCount = dueTodayLiabilityCount;
            this.overdueLiabilityCount = overdueLiabilityCount;
            this.liability7Days = liability7Days;
            this.liability30Days = liability30Days;
            this.liabilityPaymentCount = liabilityPaymentCount;
            this.avgLiabilityPayment = avgLiabilityPayment;
            this.receivable7Days = receivable7Days;
            this.receivable30Days = receivable30Days;
            this.receivablePaymentCount = receivablePaymentCount;
            this.avgReceivablePayment = avgReceivablePayment;
        }
    }

    public static final class IndividualDebtMetrics {
        public final long daysSinceCreated;
        public final long daysUntilDeadline;
        public final double period7Days;
        public final double period30Days;
        public final int paymentCount;
        public final double avgPayment;
        public final double dailyPace;
        public final double projectedMonthsRemaining;
        public final List<DebtTransaction> debtTransactions;

        IndividualDebtMetrics(long daysSinceCreated, long daysUntilDeadline, double period7Days,
                              double period30Days, int paymentCount, double avgPayment,
                              double dailyPace, double projectedMonthsRemaining,
                              List<DebtTransaction> debtTransactions) {
            this.daysSinceCreated = daysSinceCreated;
            this.daysUntilDeadline = daysUntilDeadline;
            this.period7Days = period7Days;
            this.period30Days = period30Days;
            this.paymentCount = paymentCount;
            this.avgPayment = avgPayment;
            this.dailyPace = dailyPace;
            this.projectedMonthsRemaining = projectedMonthsRemaining;
            this.debtTransactions = debtTransactions;
        }
    }

    @NonNull
    public static SavingsMetrics computeSavingsMetrics(
            @NonNull List<Saving> activeSavings,
            @NonNull List<Transaction> transactions,
            long nowMillis) {
        long sevenDaysAgo = nowMillis - SEVEN_DAYS_MS;
        long thirtyDaysAgo = nowMillis - THIRTY_DAYS_MS;

        double net7Days = 0.0;
        double net30Days = 0.0;
        double totalInflows = 0.0;
        int inflowCount = 0;

        for (Transaction tx : transactions) {
            if (tx == null) continue;
            double amt = tx.getAmount();
            if (amt <= 0.0) continue;
            String type = tx.getType();
            if (TransactionType.DEPOSIT.VALUE.equalsIgnoreCase(type)
                    || TransactionType.CREATED.VALUE.equalsIgnoreCase(type)) {
                if (tx.getDate() >= sevenDaysAgo) net7Days += amt;
                if (tx.getDate() >= thirtyDaysAgo) net30Days += amt;
                totalInflows += amt;
                inflowCount++;
            } else if (TransactionType.WITHDRAW.VALUE.equalsIgnoreCase(type)) {
                if (tx.getDate() >= sevenDaysAgo) net7Days -= amt;
                if (tx.getDate() >= thirtyDaysAgo) net30Days -= amt;
            }
        }

        double avgInflow = inflowCount > 0 ? (totalInflows / inflowCount) : 0.0;

        Map<SavingCategory, Double> categoryTotals = new HashMap<>();
        double grandTotalCurrent = 0.0;
        double totalDailyPace = 0.0;
        int paceGoalsCount = 0;
        int dueTodayCount = 0;
        int overdueGoalsCount = 0;
        double totalOverdueRemaining = 0.0;

        for (Saving s : activeSavings) {
            if (s == null) continue;
            SavingCategory cat = SavingCategory.fromId(s.getCategory());
            double current = s.getCurrentSaving();
            grandTotalCurrent += current;
            categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + current);

            if (!SavingsCalculator.isCompleted(s) && s.getDeadline() != AlarmSetter.NO_ALARM && s.getDeadline() > 0L) {
                long days = SavingsCalculator.getDaysRemaining(s.getDeadline(), nowMillis);
                double rem = SavingsCalculator.getRemainingAmount(s);
                if (days > 0) {
                    totalDailyPace += (rem / days);
                    paceGoalsCount++;
                } else if (days == 0) {
                    totalDailyPace += rem;
                    paceGoalsCount++;
                    dueTodayCount++;
                } else {
                    overdueGoalsCount++;
                    totalOverdueRemaining += rem;
                }
            }
        }

        return new SavingsMetrics(
                net7Days, net30Days, inflowCount, avgInflow,
                grandTotalCurrent, categoryTotals,
                totalDailyPace, paceGoalsCount, dueTodayCount,
                overdueGoalsCount, totalOverdueRemaining
        );
    }

    @NonNull
    public static DebtSummaryMetrics computeDebtSummary(
            @NonNull List<Debt> activeDebts,
            @NonNull List<DebtTransaction> allTransactions,
            boolean filterIOweOnly,
            boolean filterOwedOnly,
            long nowMillis) {
        double totalIOweOriginal = 0.0;
        double totalIOwePaid = 0.0;
        double totalIOweRemaining = 0.0;
        int activeIOweCount = 0;

        double totalOwedOriginal = 0.0;
        double totalOwedPaid = 0.0;
        double totalOwedRemaining = 0.0;
        int activeOwedCount = 0;

        Map<DebtCategory, Double> categoryDebtTotals = new HashMap<>();
        Set<String> iOweIds = new HashSet<>();
        Set<String> owedToMeIds = new HashSet<>();

        Debt snowballCandidate = null;
        Debt nextDueCandidate = null;
        long nextDueTimestampMin = Long.MAX_VALUE;

        double totalDailyPace = 0.0;
        int paceGoalsCount = 0;
        int dueTodayLiabilityCount = 0;
        int overdueLiabilityCount = 0;

        for (Debt d : activeDebts) {
            if (d == null) continue;
            double orig = d.getTotalAmount();
            double paid = d.getAmountPaid();
            double rem = d.getRemainingDebt();

            if (d.isOwedToMe()) {
                if (d.getId() != null) owedToMeIds.add(d.getId());
                totalOwedOriginal += orig;
                totalOwedPaid += paid;
                totalOwedRemaining += rem;
                if (!d.isPaidOff()) activeOwedCount++;

                if (!filterIOweOnly) {
                    DebtCategory cat = DebtCategory.fromId(d.getCategory());
                    categoryDebtTotals.put(cat, categoryDebtTotals.getOrDefault(cat, 0.0) + rem);
                }
            } else {
                if (d.getId() != null) iOweIds.add(d.getId());
                totalIOweOriginal += orig;
                totalIOwePaid += paid;
                totalIOweRemaining += rem;
                if (!d.isPaidOff()) activeIOweCount++;

                if (!filterOwedOnly) {
                    DebtCategory cat = DebtCategory.fromId(d.getCategory());
                    categoryDebtTotals.put(cat, categoryDebtTotals.getOrDefault(cat, 0.0) + rem);
                }

                if (rem > 0.0) {
                    if (snowballCandidate == null || rem < snowballCandidate.getRemainingDebt()) {
                        snowballCandidate = d;
                    }
                    if (d.getDeadline() > 0L) {
                        long days = SavingsCalculator.getCalendarDaysBetween(nowMillis, d.getDeadline());
                        if (days > 0) {
                            totalDailyPace += (rem / days);
                            paceGoalsCount++;
                        } else if (days == 0) {
                            totalDailyPace += rem;
                            paceGoalsCount++;
                            dueTodayLiabilityCount++;
                        } else {
                            overdueLiabilityCount++;
                        }
                        if (d.getDeadline() < nextDueTimestampMin) {
                            nextDueTimestampMin = d.getDeadline();
                            nextDueCandidate = d;
                        }
                    }
                }
            }
        }

        long sevenDaysAgo = nowMillis - SEVEN_DAYS_MS;
        long thirtyDaysAgo = nowMillis - THIRTY_DAYS_MS;

        double liability7Days = 0.0;
        double liability30Days = 0.0;
        double liabilityTotal = 0.0;
        int liabilityCount = 0;

        double receivable7Days = 0.0;
        double receivable30Days = 0.0;
        double receivableTotal = 0.0;
        int receivableCount = 0;

        for (DebtTransaction dt : allTransactions) {
            if (dt == null || dt.getDebtId() == null) continue;
            double amt = dt.getAmount();
            if (amt <= 0.0) continue;

            if (iOweIds.contains(dt.getDebtId())) {
                if (dt.getDate() >= sevenDaysAgo) liability7Days += amt;
                if (dt.getDate() >= thirtyDaysAgo) liability30Days += amt;
                liabilityTotal += amt;
                liabilityCount++;
            } else if (owedToMeIds.contains(dt.getDebtId())) {
                if (dt.getDate() >= sevenDaysAgo) receivable7Days += amt;
                if (dt.getDate() >= thirtyDaysAgo) receivable30Days += amt;
                receivableTotal += amt;
                receivableCount++;
            }
        }

        double avgLiabilityPayment = liabilityCount > 0 ? (liabilityTotal / liabilityCount) : 0.0;
        double avgReceivablePayment = receivableCount > 0 ? (receivableTotal / receivableCount) : 0.0;

        return new DebtSummaryMetrics(
                totalIOweOriginal, totalIOwePaid, totalIOweRemaining, activeIOweCount,
                totalOwedOriginal, totalOwedPaid, totalOwedRemaining, activeOwedCount,
                categoryDebtTotals, snowballCandidate, nextDueCandidate,
                totalDailyPace, paceGoalsCount, dueTodayLiabilityCount, overdueLiabilityCount,
                liability7Days, liability30Days, liabilityCount, avgLiabilityPayment,
                receivable7Days, receivable30Days, receivableCount, avgReceivablePayment
        );
    }

    @NonNull
    public static IndividualDebtMetrics computeIndividualDebtMetrics(
            @NonNull Debt debt,
            @NonNull List<DebtTransaction> allTransactions,
            long nowMillis) {
        long daysSinceCreated = Math.max(0L, SavingsCalculator.getCalendarDaysBetween(debt.getCreatedAt(), nowMillis));
        long daysUntilDeadline = debt.getDeadline() > 0L
                ? SavingsCalculator.getCalendarDaysBetween(nowMillis, debt.getDeadline())
                : Long.MIN_VALUE;

        long sevenDaysAgo = nowMillis - SEVEN_DAYS_MS;
        long thirtyDaysAgo = nowMillis - THIRTY_DAYS_MS;

        double period7Days = 0.0;
        double period30Days = 0.0;
        double totalPaid = 0.0;
        int paymentCount = 0;
        List<DebtTransaction> matched = new ArrayList<>();

        for (DebtTransaction dt : allTransactions) {
            if (dt == null || debt.getId() == null || !debt.getId().equals(dt.getDebtId())) continue;
            matched.add(dt);
            double amt = dt.getAmount();
            if (amt > 0.0) {
                if (dt.getDate() >= sevenDaysAgo) period7Days += amt;
                if (dt.getDate() >= thirtyDaysAgo) period30Days += amt;
                totalPaid += amt;
                paymentCount++;
            }
        }

        double avgPayment = paymentCount > 0 ? (totalPaid / paymentCount) : 0.0;
        double remaining = debt.getRemainingDebt();
        double dailyPace = 0.0;
        if (debt.getDeadline() > 0L && remaining > 0.0) {
            if (daysUntilDeadline > 0) {
                dailyPace = remaining / daysUntilDeadline;
            } else if (daysUntilDeadline == 0) {
                dailyPace = remaining;
            }
        }
        double projectedMonths = (remaining > 0.0 && period30Days > 0.0) ? (remaining / period30Days) : 0.0;

        return new IndividualDebtMetrics(
                daysSinceCreated, daysUntilDeadline, period7Days, period30Days,
                paymentCount, avgPayment, dailyPace, projectedMonths, matched
        );
    }
}
