package com.eipna.centsation.data.debt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.data.Database;

import java.util.ArrayList;
import java.util.List;

public class DebtRepository implements AutoCloseable {

    private final Database dbHelper;
    private final Context context;

    public DebtRepository(@Nullable Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        this.dbHelper = Database.getInstance(context);
    }

    public void create(@NonNull Debt debt) {
        if (dbHelper == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(Database.COLUMN_DEBT_ID, debt.getId());
            values.put(Database.COLUMN_DEBT_NAME, debt.getName());
            values.put(Database.COLUMN_DEBT_TOTAL_AMOUNT, debt.getTotalAmount());
            values.put(Database.COLUMN_DEBT_AMOUNT_PAID, debt.getAmountPaid());
            values.put(Database.COLUMN_DEBT_NOTES, debt.getNotes());
            values.put(Database.COLUMN_DEBT_CATEGORY, debt.getCategory());
            values.put(Database.COLUMN_DEBT_IS_ARCHIVED, debt.getIsArchived());
            values.put(Database.COLUMN_DEBT_DEADLINE, debt.getDeadline());
            values.put(Database.COLUMN_DEBT_CREATED_AT, debt.getCreatedAt());
            values.put(Database.COLUMN_DEBT_TYPE, debt.getDebtType());
            database.insert(Database.TABLE_DEBT, null, values);

            if (debt.getAmountPaid() > 0) {
                ContentValues txValues = new ContentValues();
                txValues.put(Database.COLUMN_DEBT_TRANSACTION_DEBT_ID, debt.getId());
                txValues.put(Database.COLUMN_DEBT_TRANSACTION_AMOUNT, debt.getAmountPaid());
                txValues.put(Database.COLUMN_DEBT_TRANSACTION_DATE, debt.getCreatedAt());
                txValues.put(Database.COLUMN_DEBT_TRANSACTION_NOTES, "Initial payment recorded");
                database.insert(Database.TABLE_DEBT_TRANSACTION, null, txValues);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void edit(@NonNull Debt debt) {
        if (dbHelper == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Database.COLUMN_DEBT_NAME, debt.getName());
        values.put(Database.COLUMN_DEBT_TOTAL_AMOUNT, debt.getTotalAmount());
        values.put(Database.COLUMN_DEBT_AMOUNT_PAID, debt.getAmountPaid());
        values.put(Database.COLUMN_DEBT_NOTES, debt.getNotes());
        values.put(Database.COLUMN_DEBT_CATEGORY, debt.getCategory());
        values.put(Database.COLUMN_DEBT_IS_ARCHIVED, debt.getIsArchived());
        values.put(Database.COLUMN_DEBT_DEADLINE, debt.getDeadline());
        values.put(Database.COLUMN_DEBT_TYPE, debt.getDebtType());

        database.update(Database.TABLE_DEBT, values, Database.COLUMN_DEBT_ID + " = ?", new String[]{debt.getId()});
    }

    public void delete(String debtId) {
        if (dbHelper == null || debtId == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(Database.TABLE_DEBT_TRANSACTION, Database.COLUMN_DEBT_TRANSACTION_DEBT_ID + " = ?", new String[]{debtId});
            database.delete(Database.TABLE_DEBT, Database.COLUMN_DEBT_ID + " = ?", new String[]{debtId});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void makePayment(@NonNull Debt debt, double paymentAmount, @Nullable String notes) {
        if (dbHelper == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            double newAmountPaid = debt.getAmountPaid() + paymentAmount;
            debt.setAmountPaid(newAmountPaid);

            ContentValues values = new ContentValues();
            values.put(Database.COLUMN_DEBT_AMOUNT_PAID, newAmountPaid);
            database.update(Database.TABLE_DEBT, values, Database.COLUMN_DEBT_ID + " = ?", new String[]{debt.getId()});

            ContentValues txValues = new ContentValues();
            txValues.put(Database.COLUMN_DEBT_TRANSACTION_DEBT_ID, debt.getId());
            txValues.put(Database.COLUMN_DEBT_TRANSACTION_AMOUNT, Math.abs(paymentAmount));
            txValues.put(Database.COLUMN_DEBT_TRANSACTION_DATE, System.currentTimeMillis());
            txValues.put(Database.COLUMN_DEBT_TRANSACTION_NOTES, notes);
            database.insert(Database.TABLE_DEBT_TRANSACTION, null, txValues);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public List<Debt> getDebts(int isArchived) {
        List<Debt> list = new ArrayList<>();
        if (dbHelper == null) return list;

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_DEBT + " WHERE " + Database.COLUMN_DEBT_IS_ARCHIVED + " = ? ORDER BY " + Database.COLUMN_DEBT_CREATED_AT + " DESC;";
        try (Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(isArchived)})) {
            while (cursor != null && cursor.moveToNext()) {
                list.add(cursorToDebt(cursor));
            }
        }
        return list;
    }

    public List<Debt> getAllDebts() {
        List<Debt> list = new ArrayList<>();
        if (dbHelper == null) return list;

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_DEBT + " ORDER BY " + Database.COLUMN_DEBT_CREATED_AT + " DESC;";
        try (Cursor cursor = database.rawQuery(query, null)) {
            while (cursor != null && cursor.moveToNext()) {
                list.add(cursorToDebt(cursor));
            }
        }
        return list;
    }

    @Nullable
    public Debt getDebt(String id) {
        if (dbHelper == null || id == null) return null;
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_DEBT + " WHERE " + Database.COLUMN_DEBT_ID + " = ? LIMIT 1;";
        try (Cursor cursor = database.rawQuery(query, new String[]{id})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToDebt(cursor);
            }
        }
        return null;
    }

    public List<DebtTransaction> getTransactions(String debtId) {
        List<DebtTransaction> list = new ArrayList<>();
        if (dbHelper == null || debtId == null) return list;

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_DEBT_TRANSACTION + " WHERE " + Database.COLUMN_DEBT_TRANSACTION_DEBT_ID + " = ? ORDER BY " + Database.COLUMN_DEBT_TRANSACTION_DATE + " DESC;";
        try (Cursor cursor = database.rawQuery(query, new String[]{debtId})) {
            while (cursor != null && cursor.moveToNext()) {
                list.add(cursorToTransaction(cursor));
            }
        }
        return list;
    }

    public List<DebtTransaction> getAllTransactions() {
        List<DebtTransaction> list = new ArrayList<>();
        if (dbHelper == null) return list;

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_DEBT_TRANSACTION + " ORDER BY " + Database.COLUMN_DEBT_TRANSACTION_DATE + " DESC;";
        try (Cursor cursor = database.rawQuery(query, null)) {
            while (cursor != null && cursor.moveToNext()) {
                list.add(cursorToTransaction(cursor));
            }
        }
        return list;
    }

    private Debt cursorToDebt(Cursor cursor) {
        Debt debt = new Debt();
        debt.setId(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_ID)));
        debt.setName(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_NAME)));
        debt.setTotalAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_TOTAL_AMOUNT)));
        debt.setAmountPaid(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_AMOUNT_PAID)));
        debt.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_NOTES)));
        debt.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_CATEGORY)));
        debt.setIsArchived(cursor.getInt(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_IS_ARCHIVED)));
        debt.setDeadline(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_DEADLINE)));
        debt.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_CREATED_AT)));
        int typeIdx = cursor.getColumnIndex(Database.COLUMN_DEBT_TYPE);
        if (typeIdx != -1) {
            debt.setDebtType(cursor.getInt(typeIdx));
        } else {
            debt.setDebtType(Debt.TYPE_I_OWE);
        }
        return debt;
    }

    private DebtTransaction cursorToTransaction(Cursor cursor) {
        DebtTransaction tx = new DebtTransaction();
        tx.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_TRANSACTION_ID)));
        tx.setDebtId(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_TRANSACTION_DEBT_ID)));
        tx.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_TRANSACTION_AMOUNT)));
        tx.setDate(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_TRANSACTION_DATE)));
        tx.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_DEBT_TRANSACTION_NOTES)));
        return tx;
    }

    public void bulkImport(@NonNull List<Debt> debts, @NonNull List<DebtTransaction> transactions) {
        if (dbHelper == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            for (Debt debt : debts) {
                if (debt == null || debt.getId() == null) continue;
                ContentValues values = new ContentValues();
                values.put(Database.COLUMN_DEBT_ID, debt.getId());
                values.put(Database.COLUMN_DEBT_NAME, debt.getName());
                values.put(Database.COLUMN_DEBT_TOTAL_AMOUNT, debt.getTotalAmount());
                values.put(Database.COLUMN_DEBT_AMOUNT_PAID, debt.getAmountPaid());
                values.put(Database.COLUMN_DEBT_NOTES, debt.getNotes() != null ? debt.getNotes() : "");
                values.put(Database.COLUMN_DEBT_CATEGORY, debt.getCategory());
                values.put(Database.COLUMN_DEBT_IS_ARCHIVED, debt.getIsArchived());
                values.put(Database.COLUMN_DEBT_DEADLINE, debt.getDeadline());
                values.put(Database.COLUMN_DEBT_CREATED_AT, debt.getCreatedAt());
                values.put(Database.COLUMN_DEBT_TYPE, debt.getDebtType());

                int updated = database.update(
                        Database.TABLE_DEBT,
                        values,
                        Database.COLUMN_DEBT_ID + " = ?",
                        new String[]{debt.getId()}
                );
                if (updated == 0) {
                    database.insert(Database.TABLE_DEBT, null, values);
                }
            }

            for (DebtTransaction tx : transactions) {
                if (debtTransactionExists(database, tx)) continue;
                ContentValues values = new ContentValues();
                values.put(Database.COLUMN_DEBT_TRANSACTION_DEBT_ID, tx.getDebtId());
                values.put(Database.COLUMN_DEBT_TRANSACTION_AMOUNT, tx.getAmount());
                values.put(Database.COLUMN_DEBT_TRANSACTION_DATE, tx.getDate());
                values.put(Database.COLUMN_DEBT_TRANSACTION_NOTES, tx.getNotes() != null ? tx.getNotes() : "");
                database.insert(Database.TABLE_DEBT_TRANSACTION, null, values);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private boolean debtTransactionExists(SQLiteDatabase database, DebtTransaction tx) {
        if (tx == null || tx.getDebtId() == null) return false;
        String selection = Database.COLUMN_DEBT_TRANSACTION_DEBT_ID + " = ? AND "
                + Database.COLUMN_DEBT_TRANSACTION_AMOUNT + " = ? AND "
                + Database.COLUMN_DEBT_TRANSACTION_DATE + " = ?";
        String[] args = new String[]{
                tx.getDebtId(),
                String.valueOf(tx.getAmount()),
                String.valueOf(tx.getDate())
        };
        try (Cursor cursor = database.query(Database.TABLE_DEBT_TRANSACTION, new String[]{Database.COLUMN_DEBT_TRANSACTION_ID}, selection, args, null, null, null, "1")) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    @Override
    public void close() {
        // Database helper managed as singleton
    }
}
