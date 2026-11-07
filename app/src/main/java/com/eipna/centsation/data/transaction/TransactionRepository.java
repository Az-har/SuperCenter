package com.eipna.centsation.data.transaction;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.eipna.centsation.data.Database;

import java.util.ArrayList;

public class TransactionRepository implements AutoCloseable {

    private final Database dbHelper;

    public TransactionRepository(@Nullable Context context) {
        this.dbHelper = Database.getInstance(context);
    }

    public void create(Transaction createdTransaction) {
        if (dbHelper == null) return;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Database.COLUMN_TRANSACTION_SAVING_ID, createdTransaction.getSavingID());
        values.put(Database.COLUMN_TRANSACTION_AMOUNT, createdTransaction.getAmount());
        values.put(Database.COLUMN_TRANSACTION_TYPE, createdTransaction.getType());
        values.put(Database.COLUMN_TRANSACTION_DATE, createdTransaction.getDate() > 0 ? createdTransaction.getDate() : System.currentTimeMillis());
        database.insert(Database.TABLE_TRANSACTION, null, values);
    }

    public ArrayList<Transaction> get(String savingID) {
        ArrayList<Transaction> list = new ArrayList<>();
        if (dbHelper == null || savingID == null) return list;
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + Database.TABLE_TRANSACTION + " WHERE " + Database.COLUMN_TRANSACTION_SAVING_ID + " = ? ORDER BY " + Database.COLUMN_TRANSACTION_DATE + " DESC";
        try (Cursor cursor = database.rawQuery(query, new String[]{savingID})) {
            if (cursor.moveToFirst()) {
                do {
                    Transaction queriedTransaction = new Transaction();
                    queriedTransaction.setID(cursor.getInt(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_ID)));
                    queriedTransaction.setSavingID(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_SAVING_ID)));
                    queriedTransaction.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_AMOUNT)));
                    queriedTransaction.setType(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_TYPE)));
                    queriedTransaction.setDate(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_DATE)));
                    list.add(queriedTransaction);
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    public ArrayList<Transaction> getAll() {
        ArrayList<Transaction> list = new ArrayList<>();
        if (dbHelper == null) return list;
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + Database.TABLE_TRANSACTION + " ORDER BY " + Database.COLUMN_TRANSACTION_DATE + " DESC", null)) {
            if (cursor.moveToFirst()) {
                do {
                    Transaction queriedTransaction = new Transaction();
                    queriedTransaction.setID(cursor.getInt(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_ID)));
                    queriedTransaction.setSavingID(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_SAVING_ID)));
                    queriedTransaction.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_AMOUNT)));
                    queriedTransaction.setType(cursor.getString(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_TYPE)));
                    queriedTransaction.setDate(cursor.getLong(cursor.getColumnIndexOrThrow(Database.COLUMN_TRANSACTION_DATE)));
                    list.add(queriedTransaction);
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    @Override
    public void close() {
        // Shared database helper connection pool is managed globally.
    }
}
