package com.eipna.centsation.data.debt;

import java.util.Comparator;

public class Debt {

    private String id;
    private String name;
    private double totalAmount;
    private double amountPaid;
    private String notes;
    private String category;
    private int isArchived;
    private long deadline;
    private long createdAt;

    public static final int IS_ARCHIVE = 1;
    public static final int NOT_ARCHIVE = 0;

    public Debt() {
        this.id = null;
        this.name = null;
        this.totalAmount = 0.0;
        this.amountPaid = 0.0;
        this.notes = null;
        this.category = DebtCategory.GENERAL.getId();
        this.isArchived = NOT_ARCHIVE;
        this.deadline = 0;
        this.createdAt = System.currentTimeMillis();
    }

    public Debt(Debt other) {
        this.id = other.id;
        this.name = other.name;
        this.totalAmount = other.totalAmount;
        this.amountPaid = other.amountPaid;
        this.notes = other.notes;
        this.category = other.category != null ? other.category : DebtCategory.GENERAL.getId();
        this.isArchived = other.isArchived;
        this.deadline = other.deadline;
        this.createdAt = other.createdAt;
    }

    public Debt(String id, String name, double totalAmount, double amountPaid, String category, int isArchived, long deadline, long createdAt) {
        this.id = id;
        this.name = name;
        this.totalAmount = totalAmount;
        this.amountPaid = amountPaid;
        this.notes = "";
        this.category = category != null ? category : DebtCategory.GENERAL.getId();
        this.isArchived = isArchived;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

    public static final Comparator<Debt> SORT_NAME =
            Comparator.comparing(d -> d.getName() != null ? d.getName().toLowerCase() : "");

    public static final Comparator<Debt> SORT_REMAINING =
            Comparator.comparingDouble(Debt::getRemainingDebt);

    public static final Comparator<Debt> SORT_TOTAL =
            Comparator.comparingDouble(Debt::getTotalAmount);

    public static final Comparator<Debt> SORT_DEADLINE =
            Comparator.comparingLong(Debt::getDeadline);

    public double getRemainingDebt() {
        return Math.max(0.0, totalAmount - amountPaid);
    }

    public boolean isPaidOff() {
        return totalAmount > 0 && amountPaid >= totalAmount;
    }

    public int getProgressPercent() {
        if (totalAmount <= 0) return 100;
        double pct = (amountPaid / totalAmount) * 100.0;
        return (int) Math.min(100, Math.max(0, Math.round(pct)));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(double amountPaid) {
        this.amountPaid = amountPaid;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCategory() {
        return category != null ? category : DebtCategory.GENERAL.getId();
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(int isArchived) {
        this.isArchived = isArchived;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
