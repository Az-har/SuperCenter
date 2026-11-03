package com.eipna.centsation.debt;

import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.data.debt.DebtTransaction;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DebtCalculationTest {

    @Test
    public void testDebtInitialRemainingAndProgress() {
        Debt debt = new Debt("d1", "Car Loan", 25000.0, 0.0, "general", 0, 0, System.currentTimeMillis());

        Assert.assertEquals(25000.0, debt.getRemainingDebt(), 0.001);
        Assert.assertEquals(0, debt.getProgressPercent());
        Assert.assertFalse(debt.isPaidOff());
    }

    @Test
    public void testDebtPartialPayment() {
        Debt debt = new Debt("d2", "Student Loan", 50000.0, 15000.0, "student", 0, 0, System.currentTimeMillis());

        Assert.assertEquals(35000.0, debt.getRemainingDebt(), 0.001);
        Assert.assertEquals(30, debt.getProgressPercent());
        Assert.assertFalse(debt.isPaidOff());
    }

    @Test
    public void testDebtFullPayoff() {
        Debt debt = new Debt("d3", "Credit Card", 5000.0, 5000.0, "credit_card", 0, 0, System.currentTimeMillis());

        Assert.assertEquals(0.0, debt.getRemainingDebt(), 0.001);
        Assert.assertEquals(100, debt.getProgressPercent());
        Assert.assertTrue(debt.isPaidOff());
    }

    @Test
    public void testDebtOverpaymentCappedAt100Percent() {
        Debt debt = new Debt("d4", "Small Loan", 1000.0, 1200.0, "personal", 0, 0, System.currentTimeMillis());

        Assert.assertEquals(0.0, debt.getRemainingDebt(), 0.001);
        Assert.assertEquals(100, debt.getProgressPercent());
        Assert.assertTrue(debt.isPaidOff());
    }

    @Test
    public void testDebtZeroTotalHandledGracefully() {
        Debt debt = new Debt("d5", "Zero Loan", 0.0, 0.0, "general", 0, 0, System.currentTimeMillis());

        Assert.assertEquals(0.0, debt.getRemainingDebt(), 0.001);
        Assert.assertEquals(100, debt.getProgressPercent());
    }

    @Test
    public void testCombinedTotalDebtAnalytics() {
        List<Debt> debts = new ArrayList<>();
        debts.add(new Debt("1", "Loan A", 10000.0, 4000.0, "personal", 0, 0, 1000L));
        debts.add(new Debt("2", "Loan B", 20000.0, 6000.0, "student", 0, 0, 2000L));
        debts.add(new Debt("3", "Loan C", 5000.0, 5000.0, "credit_card", 0, 0, 3000L));

        double totalBorrowed = 0.0;
        double totalPaid = 0.0;
        double totalRemaining = 0.0;
        int activeCount = 0;
        int paidOffCount = 0;

        for (Debt d : debts) {
            totalBorrowed += d.getTotalAmount();
            totalPaid += d.getAmountPaid();
            totalRemaining += d.getRemainingDebt();
            if (d.isPaidOff()) {
                paidOffCount++;
            } else {
                activeCount++;
            }
        }

        Assert.assertEquals(35000.0, totalBorrowed, 0.001);
        Assert.assertEquals(15000.0, totalPaid, 0.001);
        Assert.assertEquals(20000.0, totalRemaining, 0.001);
        Assert.assertEquals(2, activeCount);
        Assert.assertEquals(1, paidOffCount);

        int overallProgress = totalBorrowed > 0 ? (int) Math.round((totalPaid / totalBorrowed) * 100.0) : 0;
        Assert.assertEquals(43, overallProgress);
    }

    @Test
    public void testNetFinancialPositionCalculation() {
        double totalSavings = 75000.0;
        double totalDebt = 50000.0;

        double netWorth = totalSavings - totalDebt;
        Assert.assertEquals(25000.0, netWorth, 0.001);
        Assert.assertTrue(netWorth > 0);

        // When debt exceeds savings
        double largeDebt = 120000.0;
        double negativeNetWorth = totalSavings - largeDebt;
        Assert.assertEquals(-45000.0, negativeNetWorth, 0.001);
        Assert.assertTrue(negativeNetWorth < 0);
    }

    @Test
    public void testPayoffPaceAdvisor() {
        double remainingDebt = 30000.0;
        long now = System.currentTimeMillis();
        long thirtyDaysLater = now + (30L * 24L * 60L * 60L * 1000L);

        long daysRemaining = (thirtyDaysLater - now) / (1000L * 60L * 60L * 24L);
        Assert.assertTrue(daysRemaining >= 29 && daysRemaining <= 30);

        double dailyRate = remainingDebt / Math.max(1, daysRemaining);
        Assert.assertTrue(dailyRate >= 1000.0 && dailyRate <= 1035.0);

        double monthlyRate = dailyRate * 30.4375;
        Assert.assertTrue(monthlyRate >= 30000.0 && monthlyRate <= 31500.0);
    }

    @Test
    public void testDebtCategoryLookups() {
        DebtCategory cc = DebtCategory.fromId("credit_card");
        Assert.assertEquals(DebtCategory.CREDIT_CARD, cc);
        Assert.assertEquals("💳", cc.getEmoji());

        DebtCategory auto = DebtCategory.fromId("car_loan");
        Assert.assertEquals(DebtCategory.CAR_LOAN, auto);
        Assert.assertEquals("🚗", auto.getEmoji());

        DebtCategory unknown = DebtCategory.fromId("non_existent_category");
        Assert.assertEquals(DebtCategory.GENERAL, unknown);
        Assert.assertEquals("💳", unknown.getEmoji());
    }

    @Test
    public void testDebtTransactionModel() {
        DebtTransaction tx = new DebtTransaction();
        tx.setId(101L);
        tx.setDebtId("debt_abc");
        tx.setAmount(1500.50);
        tx.setDate(1700000000000L);
        tx.setNotes("EMI installment #3");

        Assert.assertEquals(101L, tx.getId());
        Assert.assertEquals("debt_abc", tx.getDebtId());
        Assert.assertEquals(1500.50, tx.getAmount(), 0.001);
        Assert.assertEquals(1700000000000L, tx.getDate());
        Assert.assertEquals("EMI installment #3", tx.getNotes());
    }
}
