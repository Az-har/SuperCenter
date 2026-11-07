package com.eipna.centsation.data.transaction;

public enum TransactionType {

    CREATED("Created"),
    DEPOSIT("Deposit"),
    WITHDRAW("Withdraw");

    public final String VALUE;

    TransactionType(String value) {
        this.VALUE = value;
    }
}