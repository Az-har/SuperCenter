package com.eipna.centsation.data.saving;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.util.AlarmSetter;

import java.util.ArrayList;
import java.util.List;

public class SavingRepository implements AutoCloseable {

    private final Database dbHelper;
    private final Context context;

    public SavingRepository(@Nullable Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        this.dbHelper = Database.getInstance(context);
    }

    public void create(Saving createdSaving) {
        if (dbHelper == null || createdSaving == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(Database.COLUMN_SAVING_ID, createdSaving.getID());
            values.put(Database.COLUMN_SAVING_NAME, createdSaving.getName());
            values.put(Database.COLUMN_SAVING_CURRENT_SAVING, createdSaving.getCurrentSaving());
            values.put(Database.COLUMN_SAVING_GOAL, createdSaving.getGoal());
            values.put(Database.COLUMN_SAVING_NOTES, createdSaving.getNotes());
            values.put(Database.COLUMN_SAVING_CATEGORY, createdSaving.getCategory());
            values.put(Database.COLUMN_SAVING_IS_ARCHIVED, createdSaving.getIsArchived());
            values.put(Database.COLUMN_SAVING_DEADLINE, createdSaving.getDeadline());
            database.insert(Database.TABLE_SAVING, null, values);

            ContentValues txValues = new ContentValues();
            txValues.put(Database.COLUMN_TRANSACTION_SAVING_ID, createdSaving.getID());
            txValues.put(Database.COLUMN_TRANSACTION_AMOUNT, createdSaving.getCurrentSaving());
            txValues.put(Database.COLUMN_TRANSACTION_TYPE, TransactionType.CREATED.VALUE);
            txValues.put(Database.COLUMN_TRANSACTION_DATE, System.currentTimeMillis());
            database.insert(Database.TABLE_TRANSACTION, null, txValues);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        if (context != null) {
            com.eipna.centsation.ui.widget.FinancialGlanceWidgetKt.updateAllFinancialWidgets(context);
        }
    }

    public void edit(Saving editedSaving) {
        if (dbHelper == null || editedSaving == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Database.COLUMN_SAVING_NAME, editedSaving.getName());
        values.put(Database.COLUMN_SAVING_CURRENT_SAVING, editedSaving.getCurrentSaving());
        values.put(Database.COLUMN_SAVING_GOAL, editedSaving.getGoal());
        values.put(Database.COLUMN_SAVING_NOTES, editedSaving.getNotes());
        values.put(Database.COLUMN_SAVING_CATEGORY, editedSaving.getCategory());
        values.put(Database.COLUMN_SAVING_IS_ARCHIVED, editedSaving.getIsArchived());
        values.put(Database.COLUMN_SAVING_DEADLINE, editedSaving.getDeadline());

        database.update(Database.TABLE_SAVING, values, Database.COLUMN_SAVING_ID + " = ?", new String[]{editedSaving.getID()});
        if (context != null) {
            com.eipna.centsation.ui.widget.FinancialGlanceWidgetKt.updateAllFinancialWidgets(context);
        }
    }

    public void delete(String savingID) {
        if (dbHelper == null || savingID == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(Database.TABLE_TRANSACTION, Database.COLUMN_TRANSACTION_SAVING_ID + " = ?", new String[]{savingID});
            database.delete(Database.TABLE_SAVING, Database.COLUMN_SAVING_ID + " = ?", new String[]{savingID});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        if (context != null) {
            com.eipna.centsation.ui.widget.FinancialGlanceWidgetKt.updateAllFinancialWidgets(context);
        }
    }

    public void makeTransaction(Saving updatedSaving, double amount, TransactionType type) {
        if (dbHelper == null || updatedSaving == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(Database.COLUMN_SAVING_CURRENT_SAVING, updatedSaving.getCurrentSaving());
            database.update(Database.TABLE_SAVING, values, Database.COLUMN_SAVING_ID + " = ?", new String[]{updatedSaving.getID()});

            ContentValues txValues = new ContentValues();
            txValues.put(Database.COLUMN_TRANSACTION_SAVING_ID, updatedSaving.getID());
            txValues.put(Database.COLUMN_TRANSACTION_AMOUNT, Math.abs(amount));
            txValues.put(Database.COLUMN_TRANSACTION_TYPE, type.VALUE);
            txValues.put(Database.COLUMN_TRANSACTION_DATE, System.currentTimeMillis());
            database.insert(Database.TABLE_TRANSACTION, null, txValues);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        if (context != null) {
            com.eipna.centsation.ui.widget.FinancialGlanceWidgetKt.updateAllFinancialWidgets(context);
        }
    }

    public ArrayList<Saving> getSavings(int isArchive) {
        ArrayList<Saving> list = new ArrayList<>();
        if (dbHelper == null) return list;
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_SAVING + " WHERE " + Database.COLUMN_SAVING_IS_ARCHIVED + " = ?";
        try (Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(isArchive)})) {
            if (cursor.moveToFirst()) {
                do {
                    Saving queriedSaving = mapSavingFromCursor(cursor);
                    list.add(queriedSaving);
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    public ArrayList<Saving> getAllSavings() {
        ArrayList<Saving> list = new ArrayList<>();
        if (dbHelper == null) return list;
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + Database.TABLE_SAVING, null)) {
            if (cursor.moveToFirst()) {
                do {
                    Saving queriedSaving = mapSavingFromCursor(cursor);
                    list.add(queriedSaving);
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    public Saving getSaving(String savingID) {
        if (dbHelper == null || savingID == null) return null;
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + Database.TABLE_SAVING + " WHERE " + Database.COLUMN_SAVING_ID + " = ?", new String[]{savingID})) {
            if (cursor.moveToFirst()) {
                return mapSavingFromCursor(cursor);
            }
        }
        return null;
    }

    private Saving mapSavingFromCursor(Cursor cursor) {
        Saving queriedSaving = new Saving();
        queriedSaving.setID(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_ID)));
        queriedSaving.setName(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_NAME)));
        queriedSaving.setCurrentSaving(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_CURRENT_SAVING)));
        queriedSaving.setGoal(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_GOAL)));
        queriedSaving.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_NOTES)));
        queriedSaving.setIsArchived(cursor.getInt(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_IS_ARCHIVED)));
        queriedSaving.setDeadline(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_SAVING_DEADLINE)));

        int catIdx = cursor.getColumnIndex(Database.COLUMN_SAVING_CATEGORY);
        if (catIdx != -1 && !cursor.isNull(catIdx)) {
            queriedSaving.setCategory(cursor.getString(catIdx));
        } else {
            queriedSaving.setCategory(SavingCategory.GENERAL.getId());
        }

        return queriedSaving;
    }

    public void bulkImport(@NonNull List<Saving> savings, @NonNull List<Transaction> transactions) {
        if (dbHelper == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            for (Saving saving : savings) {
                if (saving == null || saving.getID() == null) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(Database.COLUMN_SAVING_ID, saving.getID());
                values.put(Database.COLUMN_SAVING_NAME, saving.getName());
                values.put(Database.COLUMN_SAVING_CURRENT_SAVING, saving.getCurrentSaving());
                values.put(Database.COLUMN_SAVING_GOAL, saving.getGoal());
                values.put(Database.COLUMN_SAVING_NOTES, saving.getNotes() != null ? saving.getNotes() : "");
                values.put(Database.COLUMN_SAVING_CATEGORY, saving.getCategory());
                values.put(Database.COLUMN_SAVING_IS_ARCHIVED, saving.getIsArchived());
                values.put(Database.COLUMN_SAVING_DEADLINE, saving.getDeadline());

                int updatedRows = database.update(
                        Database.TABLE_SAVING,
                        values,
                        Database.COLUMN_SAVING_ID + " = ?",
                        new String[]{saving.getID()}
                );
                if (updatedRows == 0) {
                    database.insert(Database.TABLE_SAVING, null, values);
                }
            }

            for (Transaction transaction : transactions) {
                if (transactionExists(database, transaction)) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(Database.COLUMN_TRANSACTION_SAVING_ID, transaction.getSavingID());
                values.put(Database.COLUMN_TRANSACTION_AMOUNT, transaction.getAmount());
                values.put(Database.COLUMN_TRANSACTION_TYPE, transaction.getType());
                values.put(Database.COLUMN_TRANSACTION_DATE, transaction.getDate());
                database.insert(Database.TABLE_TRANSACTION, null, values);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        long now = System.currentTimeMillis();
        for (Saving saving : savings) {
            long deadline = saving.getDeadline();
            if (saving.getIsArchived() == Saving.NOT_ARCHIVE
                    && saving.getCurrentSaving() < saving.getGoal()
                    && deadline != AlarmSetter.NO_ALARM
                    && deadline > now) {
                AlarmSetter.set(context, saving);
            } else {
                AlarmSetter.cancel(context, saving);
            }
        }
    }

    private boolean transactionExists(SQLiteDatabase database, Transaction transaction) {
        if (transaction == null || transaction.getSavingID() == null) {
            return false;
        }
        String selection = Database.COLUMN_TRANSACTION_SAVING_ID + " = ? AND "
                + Database.COLUMN_TRANSACTION_AMOUNT + " = ? AND "
                + Database.COLUMN_TRANSACTION_TYPE + " = ? AND "
                + Database.COLUMN_TRANSACTION_DATE + " = ?";
        String[] args = new String[]{
                transaction.getSavingID(),
                String.valueOf(transaction.getAmount()),
                transaction.getType() != null ? transaction.getType() : "",
                String.valueOf(transaction.getDate())
        };
        try (Cursor cursor = database.query(Database.TABLE_TRANSACTION, new String[]{Database.COLUMN_TRANSACTION_ID}, selection, args, null, null, null, "1")) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    @Override
    public void close() {
        // Shared database helper connection pool is managed globally.
    }
}
