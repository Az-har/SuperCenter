package com.eipna.centsation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionRepository;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.BackupManager;

import org.junit.Test;

/**
 * Unit tests verifying persistence, repository contracts, and concurrency-related models.
 */
public class PersistenceAndConcurrencyTest {

    @Test
    public void databaseSchemaConstants_areCorrect() {
        assertEquals("savings", Database.TABLE_SAVING);
        assertEquals("id", Database.COLUMN_SAVING_ID);
        assertEquals("name", Database.COLUMN_SAVING_NAME);
        assertEquals("current_saving", Database.COLUMN_SAVING_CURRENT_SAVING);
        assertEquals("goal", Database.COLUMN_SAVING_GOAL);
        assertEquals("notes", Database.COLUMN_SAVING_NOTES);
        assertEquals("category", Database.COLUMN_SAVING_CATEGORY);
        assertEquals("is_archived", Database.COLUMN_SAVING_IS_ARCHIVED);
        assertEquals("deadline", Database.COLUMN_SAVING_DEADLINE);

        assertEquals("transactions", Database.TABLE_TRANSACTION);
        assertEquals("id", Database.COLUMN_TRANSACTION_ID);
        assertEquals("saving_id", Database.COLUMN_TRANSACTION_SAVING_ID);
        assertEquals("amount", Database.COLUMN_TRANSACTION_AMOUNT);
        assertEquals("type", Database.COLUMN_TRANSACTION_TYPE);
        assertEquals("date", Database.COLUMN_TRANSACTION_DATE);
    }

    @Test
    public void repositories_implementAutoCloseable() {
        assertTrue("SavingRepository must implement AutoCloseable",
                AutoCloseable.class.isAssignableFrom(SavingRepository.class));
        assertTrue("TransactionRepository must implement AutoCloseable",
                AutoCloseable.class.isAssignableFrom(TransactionRepository.class));

        // Test safe close on uninitialized/null-context repositories
        try (SavingRepository repo = new SavingRepository(null)) {
            assertNotNull(repo);
        } catch (Exception e) {
            org.junit.Assert.fail("SavingRepository.close() should not throw: " + e.getMessage());
        }

        try (TransactionRepository repo = new TransactionRepository(null)) {
            assertNotNull(repo);
        } catch (Exception e) {
            org.junit.Assert.fail("TransactionRepository.close() should not throw: " + e.getMessage());
        }
    }

    @Test
    public void backupManagerConstants_areCorrect() {
        assertEquals("SuperCenter", BackupManager.BACKUP_DIR_NAME);
        assertEquals("supercenter_auto_backup.json", BackupManager.AUTO_BACKUP_FILE_NAME);
        assertEquals(2, BackupManager.CURRENT_BACKUP_VERSION);
        assertEquals("Centsation", BackupManager.LEGACY_BACKUP_DIR_NAME);
        assertEquals("centsation_auto_backup.json", BackupManager.LEGACY_AUTO_BACKUP_FILE_NAME);
        assertEquals("trackers", BackupManager.TABLE_TRACKERS);
        assertEquals("tracker_logs", BackupManager.TABLE_TRACKER_LOGS);
    }

    @Test
    public void debtRepository_implementsAutoCloseableAndHandlesNullSafely() {
        assertTrue("DebtRepository must implement AutoCloseable",
                AutoCloseable.class.isAssignableFrom(DebtRepository.class));
        try (DebtRepository repo = new DebtRepository(null)) {
            assertNotNull(repo);
        } catch (Exception e) {
            org.junit.Assert.fail("DebtRepository.close() should not throw: " + e.getMessage());
        }
    }

    @Test
    public void savingEntity_dataIntegrityMaintained() {
        Saving saving = new Saving();
        saving.setID("test-uuid-123");
        saving.setName("Emergency Fund");
        saving.setCurrentSaving(500.0);
        saving.setGoal(1000.0);
        saving.setNotes("High yield savings");
        saving.setCategory("travel");
        saving.setIsArchived(Saving.NOT_ARCHIVE);
        saving.setDeadline(1750000000000L);

        assertEquals("test-uuid-123", saving.getID());
        assertEquals("Emergency Fund", saving.getName());
        assertEquals(500.0, saving.getCurrentSaving(), 0.001);
        assertEquals(1000.0, saving.getGoal(), 0.001);
        assertEquals("High yield savings", saving.getNotes());
        assertEquals("travel", saving.getCategory());
        assertEquals(Saving.NOT_ARCHIVE, saving.getIsArchived());
        assertEquals(1750000000000L, saving.getDeadline());

        // Copy constructor integrity
        Saving copy = new Saving(saving);
        assertEquals(saving.getID(), copy.getID());
        assertEquals(saving.getName(), copy.getName());
        assertEquals(saving.getCurrentSaving(), copy.getCurrentSaving(), 0.001);
        assertEquals(saving.getGoal(), copy.getGoal(), 0.001);
        assertEquals(saving.getNotes(), copy.getNotes());
        assertEquals(saving.getCategory(), copy.getCategory());
        assertEquals(saving.getIsArchived(), copy.getIsArchived());
        assertEquals(saving.getDeadline(), copy.getDeadline());
    }

    @Test
    public void transactionEntity_dataIntegrityMaintained() {
        Transaction tx = new Transaction();
        tx.setID(42);
        tx.setSavingID("test-uuid-123");
        tx.setAmount(150.75);
        tx.setType(TransactionType.DEPOSIT.VALUE);
        tx.setDate(1700000000000L);

        assertEquals(42, tx.getID());
        assertEquals("test-uuid-123", tx.getSavingID());
        assertEquals(150.75, tx.getAmount(), 0.001);
        assertEquals(TransactionType.DEPOSIT.VALUE, tx.getType());
        assertEquals(1700000000000L, tx.getDate());
    }

    @Test
    public void alarmSetterConstants_areCorrect() {
        assertEquals("com.eipna.centsation.ACTION_DEADLINE_ALARM", AlarmSetter.ACTION_DEADLINE_ALARM);
        assertEquals(0L, AlarmSetter.NO_ALARM);
    }
}
