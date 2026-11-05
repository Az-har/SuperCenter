package com.eipna.centsation.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class Database extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "centsation.db";
    private static final int DATABASE_VERSION = 6;

    public static final String TABLE_SAVING = "savings";
    public static final String COLUMN_SAVING_ID = "id";
    public static final String COLUMN_SAVING_NAME = "name";
    public static final String COLUMN_SAVING_CURRENT_SAVING = "current_saving";
    public static final String COLUMN_SAVING_GOAL = "goal";
    public static final String COLUMN_SAVING_NOTES = "notes";
    public static final String COLUMN_SAVING_CATEGORY = "category";
    public static final String COLUMN_SAVING_IS_ARCHIVED = "is_archived";
    public static final String COLUMN_SAVING_DEADLINE = "deadline";

    public static final String TABLE_TRANSACTION = "transactions";
    public static final String COLUMN_TRANSACTION_ID = "id";
    public static final String COLUMN_TRANSACTION_SAVING_ID = "saving_id";
    public static final String COLUMN_TRANSACTION_AMOUNT = "amount";
    public static final String COLUMN_TRANSACTION_TYPE = "type";
    public static final String COLUMN_TRANSACTION_DATE = "date";

    public static final String TABLE_DEBT = "debts";
    public static final String COLUMN_DEBT_ID = "id";
    public static final String COLUMN_DEBT_NAME = "name";
    public static final String COLUMN_DEBT_TOTAL_AMOUNT = "total_amount";
    public static final String COLUMN_DEBT_AMOUNT_PAID = "amount_paid";
    public static final String COLUMN_DEBT_NOTES = "notes";
    public static final String COLUMN_DEBT_CATEGORY = "category";
    public static final String COLUMN_DEBT_IS_ARCHIVED = "is_archived";
    public static final String COLUMN_DEBT_DEADLINE = "deadline";
    public static final String COLUMN_DEBT_CREATED_AT = "created_at";
    public static final String COLUMN_DEBT_TYPE = "debt_type";

    public static final String TABLE_DEBT_TRANSACTION = "debt_transactions";
    public static final String COLUMN_DEBT_TRANSACTION_ID = "id";
    public static final String COLUMN_DEBT_TRANSACTION_DEBT_ID = "debt_id";
    public static final String COLUMN_DEBT_TRANSACTION_AMOUNT = "amount";
    public static final String COLUMN_DEBT_TRANSACTION_DATE = "date";
    public static final String COLUMN_DEBT_TRANSACTION_NOTES = "notes";

    private static volatile Database sInstance;

    public static synchronized Database getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new Database(context.getApplicationContext());
        }
        return sInstance;
    }

    public Database(@Nullable Context context) {
        super(context != null ? context.getApplicationContext() : null, DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String createSavingTable = "CREATE TABLE IF NOT EXISTS " + TABLE_SAVING + "(" +
                COLUMN_SAVING_ID + " TEXT PRIMARY KEY, " +
                COLUMN_SAVING_NAME + " TEXT NOT NULL, " +
                COLUMN_SAVING_CURRENT_SAVING + " REAL NOT NULL, " +
                COLUMN_SAVING_GOAL + " REAL NOT NULL, " +
                COLUMN_SAVING_NOTES + " TEXT, " +
                COLUMN_SAVING_CATEGORY + " TEXT, " +
                COLUMN_SAVING_IS_ARCHIVED + " INTEGER NOT NULL, " +
                COLUMN_SAVING_DEADLINE + " INTEGER);";

        String createTransactionTable = "CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTION + "(" +
                COLUMN_TRANSACTION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TRANSACTION_SAVING_ID + " TEXT NOT NULL, " +
                COLUMN_TRANSACTION_AMOUNT + " REAL NOT NULL, " +
                COLUMN_TRANSACTION_TYPE + " TEXT NOT NULL," +
                COLUMN_TRANSACTION_DATE + " INTEGER NOT NULL, " +
                "FOREIGN KEY (" + COLUMN_TRANSACTION_SAVING_ID + ") REFERENCES " + TABLE_SAVING + "(" + COLUMN_SAVING_ID + ") ON DELETE CASCADE);";

        String createDebtTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DEBT + "(" +
                COLUMN_DEBT_ID + " TEXT PRIMARY KEY, " +
                COLUMN_DEBT_NAME + " TEXT NOT NULL, " +
                COLUMN_DEBT_TOTAL_AMOUNT + " REAL NOT NULL, " +
                COLUMN_DEBT_AMOUNT_PAID + " REAL NOT NULL, " +
                COLUMN_DEBT_NOTES + " TEXT, " +
                COLUMN_DEBT_CATEGORY + " TEXT, " +
                COLUMN_DEBT_IS_ARCHIVED + " INTEGER NOT NULL, " +
                COLUMN_DEBT_DEADLINE + " INTEGER, " +
                COLUMN_DEBT_CREATED_AT + " INTEGER NOT NULL, " +
                COLUMN_DEBT_TYPE + " INTEGER NOT NULL DEFAULT 0);";

        String createDebtTransactionTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DEBT_TRANSACTION + "(" +
                COLUMN_DEBT_TRANSACTION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_DEBT_TRANSACTION_DEBT_ID + " TEXT NOT NULL, " +
                COLUMN_DEBT_TRANSACTION_AMOUNT + " REAL NOT NULL, " +
                COLUMN_DEBT_TRANSACTION_DATE + " INTEGER NOT NULL, " +
                COLUMN_DEBT_TRANSACTION_NOTES + " TEXT, " +
                "FOREIGN KEY (" + COLUMN_DEBT_TRANSACTION_DEBT_ID + ") REFERENCES " + TABLE_DEBT + "(" + COLUMN_DEBT_ID + ") ON DELETE CASCADE);";

        sqLiteDatabase.execSQL(createSavingTable);
        sqLiteDatabase.execSQL(createTransactionTable);
        sqLiteDatabase.execSQL(createDebtTable);
        sqLiteDatabase.execSQL(createDebtTransactionTable);
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_saving_id ON " + TABLE_TRANSACTION + "(" + COLUMN_TRANSACTION_SAVING_ID + ");");
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_debt_transactions_debt_id ON " + TABLE_DEBT_TRANSACTION + "(" + COLUMN_DEBT_TRANSACTION_DEBT_ID + ");");
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_savings_is_archived ON " + TABLE_SAVING + "(" + COLUMN_SAVING_IS_ARCHIVED + ");");
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_debts_is_archived ON " + TABLE_DEBT + "(" + COLUMN_DEBT_IS_ARCHIVED + ");");
    }

    @Override
    public void onConfigure(SQLiteDatabase sqLiteDatabase) {
        super.onConfigure(sqLiteDatabase);
        sqLiteDatabase.setForeignKeyConstraintsEnabled(true);
        sqLiteDatabase.enableWriteAheadLogging();
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_saving_id ON " + TABLE_TRANSACTION + "(" + COLUMN_TRANSACTION_SAVING_ID + ");");
        }
        if (oldVersion < 3) {
            try {
                sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_SAVING + " ADD COLUMN " + COLUMN_SAVING_CATEGORY + " TEXT;");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 4) {
            createDebtTables(sqLiteDatabase);
        }
        if (oldVersion < 5) {
            try {
                sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_DEBT + " ADD COLUMN " + COLUMN_DEBT_TYPE + " INTEGER NOT NULL DEFAULT 0;");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 6) {
            try {
                sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_savings_is_archived ON " + TABLE_SAVING + "(" + COLUMN_SAVING_IS_ARCHIVED + ");");
                sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_debts_is_archived ON " + TABLE_DEBT + "(" + COLUMN_DEBT_IS_ARCHIVED + ");");
            } catch (Exception ignored) {
            }
        }
    }

    private void createDebtTables(SQLiteDatabase sqLiteDatabase) {
        String createDebtTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DEBT + "(" +
                COLUMN_DEBT_ID + " TEXT PRIMARY KEY, " +
                COLUMN_DEBT_NAME + " TEXT NOT NULL, " +
                COLUMN_DEBT_TOTAL_AMOUNT + " REAL NOT NULL, " +
                COLUMN_DEBT_AMOUNT_PAID + " REAL NOT NULL, " +
                COLUMN_DEBT_NOTES + " TEXT, " +
                COLUMN_DEBT_CATEGORY + " TEXT, " +
                COLUMN_DEBT_IS_ARCHIVED + " INTEGER NOT NULL, " +
                COLUMN_DEBT_DEADLINE + " INTEGER, " +
                COLUMN_DEBT_CREATED_AT + " INTEGER NOT NULL, " +
                COLUMN_DEBT_TYPE + " INTEGER NOT NULL DEFAULT 0);";

        String createDebtTransactionTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DEBT_TRANSACTION + "(" +
                COLUMN_DEBT_TRANSACTION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_DEBT_TRANSACTION_DEBT_ID + " TEXT NOT NULL, " +
                COLUMN_DEBT_TRANSACTION_AMOUNT + " REAL NOT NULL, " +
                COLUMN_DEBT_TRANSACTION_DATE + " INTEGER NOT NULL, " +
                COLUMN_DEBT_TRANSACTION_NOTES + " TEXT, " +
                "FOREIGN KEY (" + COLUMN_DEBT_TRANSACTION_DEBT_ID + ") REFERENCES " + TABLE_DEBT + "(" + COLUMN_DEBT_ID + ") ON DELETE CASCADE);";

        sqLiteDatabase.execSQL(createDebtTable);
        sqLiteDatabase.execSQL(createDebtTransactionTable);
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_debt_transactions_debt_id ON " + TABLE_DEBT_TRANSACTION + "(" + COLUMN_DEBT_TRANSACTION_DEBT_ID + ");");
    }

    @Override
    public void onOpen(SQLiteDatabase sqLiteDatabase) {
        super.onOpen(sqLiteDatabase);
        // Foreign keys and indices are configured in onConfigure, onCreate, and onUpgrade.
        // Avoid redundant DDL execution on every connection to conserve battery and flash I/O.
    }
}
