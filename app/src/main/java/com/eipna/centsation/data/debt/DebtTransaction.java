package com.eipna.centsation.data.debt;

public class DebtTransaction {

    private long id;
    private String debtId;
    private double amount;
    private long date;
    private String notes;

    public DebtTransaction() {
        this.id = 0;
        this.debtId = null;
        this.amount = 0.0;
        this.date = System.currentTimeMillis();
        this.notes = null;
    }

    public DebtTransaction(long id, String debtId, double amount, long date, String notes) {
        this.id = id;
        this.debtId = debtId;
        this.amount = amount;
        this.date = date;
        this.notes = notes;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDebtId() {
        return debtId;
    }

    public void setDebtId(String debtId) {
        this.debtId = debtId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
