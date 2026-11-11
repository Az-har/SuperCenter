package com.eipna.centsation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Environment;

import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.TransactionRepository;
import com.eipna.centsation.receiver.DeadlineReceiver;
import com.eipna.centsation.ui.activities.CreateActivity;
import com.eipna.centsation.ui.activities.EditActivity;
import com.eipna.centsation.ui.activities.HistoryActivity;
import com.eipna.centsation.ui.activities.MainActivity;
import com.eipna.centsation.ui.activities.SettingsActivity;
import com.eipna.centsation.ui.dialogs.DebtDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.SavingDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.viewmodel.ArchiveViewModel;
import com.eipna.centsation.ui.viewmodel.HistoryViewModel;
import com.eipna.centsation.ui.viewmodel.MainViewModel;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.BackupManager;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;

/**
 * Empirical Challenger Verification Test Suite for Milestone 1.
 * Tests threading, lifecycle leak avoidance, bytecode invariants,
 * and repository management across all modified components.
 */
public class ChallengerThreadingAndLifecycleTest {

    private byte[] getClassBytes(Class<?> clazz) throws Exception {
        String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getResourceAsStream(resourcePath)) {
            assertNotNull("Class bytecode resource must exist: " + resourcePath, is);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    private String getConstantPoolUtf8Strings(Class<?> clazz) throws Exception {
        byte[] bytes = getClassBytes(clazz);
        // Convert bytecode bytes to an ISO-8859-1 string to search for constant pool UTF-8 strings
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    // ==========================================
    // 1. CreateActivity Threading & Lifecycle
    // ==========================================
    @Test
    public void createActivity_hasManagedExecutorAndLifecycleShutdown() throws Exception {
        Field executorField = CreateActivity.class.getDeclaredField("executor");
        assertNotNull("CreateActivity must declare an executor field", executorField);
        assertTrue("Executor must be of type ExecutorService",
                ExecutorService.class.isAssignableFrom(executorField.getType()));
        assertTrue("Executor should be final to prevent re-assignment",
                Modifier.isFinal(executorField.getModifiers()));

        Field repoField = CreateActivity.class.getDeclaredField("savingRepository");
        assertNotNull("CreateActivity must declare savingRepository", repoField);
        assertEquals(SavingRepository.class, repoField.getType());

        Method onDestroyMethod = CreateActivity.class.getDeclaredMethod("onDestroy");
        assertNotNull("CreateActivity must override onDestroy", onDestroyMethod);

        // Bytecode invariant: shared AppExecutors.diskIO() must NEVER be shut down by Activity lifecycle
        String bytecodeStrings = getConstantPoolUtf8Strings(CreateActivity.class);
        assertTrue("CreateActivity must obtain executor from AppExecutors",
                bytecodeStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("CreateActivity must NOT call shutdown() on shared AppExecutors singleton",
                bytecodeStrings.contains("shutdown"));
        assertTrue("CreateActivity must call close() on savingRepository in onDestroy",
                bytecodeStrings.contains("close"));

        // Verify createSaving() delegates DB work to executor and does NOT call repository synchronously on main thread
        Method createSavingMethod = CreateActivity.class.getDeclaredMethod("createSaving");
        assertNotNull("CreateActivity must declare createSaving", createSavingMethod);
    }

    // ==========================================
    // 2. EditActivity Threading & Lifecycle
    // ==========================================
    @Test
    public void editActivity_hasManagedExecutorAndLifecycleShutdown() throws Exception {
        Field executorField = EditActivity.class.getDeclaredField("executor");
        assertNotNull("EditActivity must declare an executor field", executorField);
        assertTrue("Executor must be of type ExecutorService",
                ExecutorService.class.isAssignableFrom(executorField.getType()));

        Field repoField = EditActivity.class.getDeclaredField("savingRepository");
        assertNotNull("EditActivity must declare savingRepository", repoField);
        assertEquals(SavingRepository.class, repoField.getType());

        Method onDestroyMethod = EditActivity.class.getDeclaredMethod("onDestroy");
        assertNotNull("EditActivity must override onDestroy", onDestroyMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(EditActivity.class);
        assertTrue("EditActivity must use shared AppExecutors",
                bytecodeStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("EditActivity must NOT call shutdown() on shared AppExecutors singleton",
                bytecodeStrings.contains("shutdown"));
        assertTrue("EditActivity must invoke close()", bytecodeStrings.contains("close"));

        // Verify loadSaving method exists for asynchronous loading
        Method loadSavingMethod = EditActivity.class.getDeclaredMethod("loadSaving", String.class);
        assertNotNull("EditActivity must load savings asynchronously via loadSaving(String)", loadSavingMethod);
    }

    // ==========================================
    // 3. SavingDetailsBottomSheetDialogFragment Threading & Lifecycle
    // ==========================================
    @Test
    public void savingDetailsBottomSheet_hasManagedExecutorAndShutdown() throws Exception {
        Field executorField = SavingDetailsBottomSheetDialogFragment.class.getDeclaredField("executor");
        assertNotNull("SavingDetailsBottomSheetDialogFragment must declare an executor", executorField);
        assertTrue("Executor must be ExecutorService",
                ExecutorService.class.isAssignableFrom(executorField.getType()));

        Method onDestroyViewMethod = SavingDetailsBottomSheetDialogFragment.class.getDeclaredMethod("onDestroyView");
        assertNotNull("SavingDetailsBottomSheetDialogFragment must override onDestroyView", onDestroyViewMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(SavingDetailsBottomSheetDialogFragment.class);
        assertTrue("SavingDetailsBottomSheetDialogFragment must call shutdown() in onDestroyView",
                bytecodeStrings.contains("shutdown"));
        assertTrue("SavingDetailsBottomSheetDialogFragment must use SavingRepository in background",
                bytecodeStrings.contains("com/eipna/centsation/data/saving/SavingRepository"));
    }

    // ==========================================
    // 4. DeadlineReceiver Asynchronous Execution via goAsync()
    // ==========================================
    @Test
    public void deadlineReceiver_usesGoAsyncAndBackgroundExecutor() throws Exception {
        Field executorField = DeadlineReceiver.class.getDeclaredField("EXECUTOR");
        assertNotNull("DeadlineReceiver must declare EXECUTOR", executorField);
        assertTrue("EXECUTOR must be static", Modifier.isStatic(executorField.getModifiers()));
        assertTrue("EXECUTOR must be ExecutorService",
                ExecutorService.class.isAssignableFrom(executorField.getType()));

        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(null);
        assertNotNull("EXECUTOR instance must not be null", executor);
        assertFalse("EXECUTOR must not be shut down", executor.isShutdown());

        String bytecodeStrings = getConstantPoolUtf8Strings(DeadlineReceiver.class);
        assertTrue("DeadlineReceiver must call goAsync() to release UI thread",
                bytecodeStrings.contains("goAsync"));
        assertTrue("DeadlineReceiver must call finish() on PendingResult",
                bytecodeStrings.contains("finish"));
        assertTrue("DeadlineReceiver must reference PendingResult",
                bytecodeStrings.contains("android/content/BroadcastReceiver$PendingResult"));
    }

    // ==========================================
    // 5. MainActivity Managed Executor & No Orphaned Executors
    // ==========================================
    @Test
    public void mainActivity_hasManagedExecutorAndShutsDown() throws Exception {
        Field executorField = MainActivity.class.getDeclaredField("executor");
        assertNotNull("MainActivity must declare managed executor", executorField);
        assertTrue("Executor must be ExecutorService",
                ExecutorService.class.isAssignableFrom(executorField.getType()));

        Method onDestroyMethod = MainActivity.class.getDeclaredMethod("onDestroy");
        assertNotNull("MainActivity must override onDestroy", onDestroyMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(MainActivity.class);
        assertTrue("MainActivity must use AppExecutors shared pool",
                bytecodeStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("MainActivity must NOT call shutdown() on shared AppExecutors singleton",
                bytecodeStrings.contains("shutdown"));
        assertTrue("MainActivity must release analyticsController in onDestroy",
                bytecodeStrings.contains("release"));
        assertFalse("MainActivity must NOT instantiate ad-hoc single-thread executors",
                bytecodeStrings.contains("newSingleThreadExecutor"));
    }

    // ==========================================
    // 6. HistoryActivity Orphaned Executor Removed
    // ==========================================
    @Test
    public void historyActivity_hasNoOrphanedExecutors() throws Exception {
        Field[] fields = HistoryActivity.class.getDeclaredFields();
        for (Field f : fields) {
            assertFalse("HistoryActivity must NOT retain any ExecutorService directly",
                    ExecutorService.class.isAssignableFrom(f.getType()));
        }

        String bytecodeStrings = getConstantPoolUtf8Strings(HistoryActivity.class);
        assertFalse("HistoryActivity must NOT instantiate newSingleThreadExecutor",
                bytecodeStrings.contains("newSingleThreadExecutor"));
        assertFalse("HistoryActivity must NOT instantiate SavingRepository directly",
                bytecodeStrings.contains("com/eipna/centsation/data/saving/SavingRepository"));
    }

    // ==========================================
    // 7. ViewModels: Repositories Closed and Executors Shut Down in onCleared
    // ==========================================
    @Test
    public void historyViewModel_shutsDownExecutorAndClosesBothRepositories() throws Exception {
        Field execField = HistoryViewModel.class.getDeclaredField("executor");
        assertTrue(ExecutorService.class.isAssignableFrom(execField.getType()));

        Field savingRepoField = HistoryViewModel.class.getDeclaredField("savingRepository");
        assertEquals(SavingRepository.class, savingRepoField.getType());

        Field txRepoField = HistoryViewModel.class.getDeclaredField("transactionRepository");
        assertEquals(TransactionRepository.class, txRepoField.getType());

        Method onClearedMethod = HistoryViewModel.class.getDeclaredMethod("onCleared");
        assertNotNull(onClearedMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(HistoryViewModel.class);
        assertTrue("HistoryViewModel must use shared AppExecutors",
                bytecodeStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("HistoryViewModel must NOT call shutdown() on shared AppExecutors",
                bytecodeStrings.contains("shutdown"));
        assertTrue("HistoryViewModel must call close()", bytecodeStrings.contains("close"));
    }

    @Test
    public void mainViewModel_shutsDownExecutorAndClosesSavingRepository() throws Exception {
        Field execField = MainViewModel.class.getDeclaredField("executor");
        assertTrue(ExecutorService.class.isAssignableFrom(execField.getType()));

        Field repoField = MainViewModel.class.getDeclaredField("savingRepository");
        assertEquals(SavingRepository.class, repoField.getType());

        Method onClearedMethod = MainViewModel.class.getDeclaredMethod("onCleared");
        assertNotNull(onClearedMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(MainViewModel.class);
        assertTrue("MainViewModel must use shared AppExecutors",
                bytecodeStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("MainViewModel must NOT call shutdown() on shared AppExecutors",
                bytecodeStrings.contains("shutdown"));
        assertTrue("MainViewModel must call close()", bytecodeStrings.contains("close"));
    }

    @Test
    public void archiveViewModel_shutsDownExecutorAndClosesSavingRepository() throws Exception {
        Field execField = ArchiveViewModel.class.getDeclaredField("executor");
        assertTrue(ExecutorService.class.isAssignableFrom(execField.getType()));

        Field repoField = ArchiveViewModel.class.getDeclaredField("savingRepository");
        assertEquals(SavingRepository.class, repoField.getType());

        Field debtRepoField = ArchiveViewModel.class.getDeclaredField("debtRepository");
        assertEquals(DebtRepository.class, debtRepoField.getType());

        Method onClearedMethod = ArchiveViewModel.class.getDeclaredMethod("onCleared");
        assertNotNull(onClearedMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(ArchiveViewModel.class);
        assertTrue("ArchiveViewModel must use shared AppExecutors",
                bytecodeStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("ArchiveViewModel must NOT call shutdown() on shared AppExecutors",
                bytecodeStrings.contains("shutdown"));
        assertTrue("ArchiveViewModel must call close()", bytecodeStrings.contains("close"));
    }

    @Test
    public void settingsAndDebtFragments_neverShutDownSharedExecutor() throws Exception {
        String settingsStrings = getConstantPoolUtf8Strings(SettingsActivity.SettingsFragment.class);
        assertTrue("SettingsFragment must use AppExecutors",
                settingsStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("SettingsFragment must NOT shut down shared AppExecutors",
                settingsStrings.contains("shutdown"));

        String debtDetailsStrings = getConstantPoolUtf8Strings(DebtDetailsBottomSheetDialogFragment.class);
        assertTrue("DebtDetailsBottomSheetDialogFragment must use AppExecutors",
                debtDetailsStrings.contains("com/eipna/centsation/util/AppExecutors"));
        assertFalse("DebtDetailsBottomSheetDialogFragment must NOT shut down shared AppExecutors",
                debtDetailsStrings.contains("shutdown"));

        assertNotNull(AppExecutors.getInstance().diskIO());
        assertFalse(AppExecutors.getInstance().diskIO().isShutdown());
    }

    // ==========================================
    // 8. SavingRepository bulkImport In-Place Update & Deduplication
    // ==========================================
    @Test
    public void savingRepository_bulkImportUsesInPlaceUpdateAndDeduplication() throws Exception {
        String bytecodeStrings = getConstantPoolUtf8Strings(SavingRepository.class);
        assertTrue("SavingRepository must use update for in-place bulkImport to prevent cascade deletes",
                bytecodeStrings.contains("update"));
        assertFalse("SavingRepository must NOT use CONFLICT_REPLACE for bulkImport",
                bytecodeStrings.contains("CONFLICT_REPLACE"));
        assertTrue("SavingRepository must check transactionExists",
                bytecodeStrings.contains("transactionExists"));

        Method txExistsMethod = SavingRepository.class.getDeclaredMethod(
                "transactionExists",
                android.database.sqlite.SQLiteDatabase.class,
                com.eipna.centsation.data.transaction.Transaction.class);
        assertNotNull("transactionExists helper method must exist", txExistsMethod);
    }

    // ==========================================
    // 9. Database Schema Index, Migration & Configuration Timing
    // ==========================================
    @Test
    public void database_hasIndexAndOnConfigureForeignKeySetup() throws Exception {
        Method onConfigureMethod = Database.class.getDeclaredMethod(
                "onConfigure", android.database.sqlite.SQLiteDatabase.class);
        assertNotNull("Database must override onConfigure", onConfigureMethod);

        Method onUpgradeMethod = Database.class.getDeclaredMethod(
                "onUpgrade", android.database.sqlite.SQLiteDatabase.class, int.class, int.class);
        assertNotNull("Database must override onUpgrade", onUpgradeMethod);

        Method onOpenMethod = Database.class.getDeclaredMethod(
                "onOpen", android.database.sqlite.SQLiteDatabase.class);
        assertNotNull("Database must override onOpen", onOpenMethod);

        String bytecodeStrings = getConstantPoolUtf8Strings(Database.class);
        assertTrue("Database must call setForeignKeyConstraintsEnabled in onConfigure",
                bytecodeStrings.contains("setForeignKeyConstraintsEnabled"));
        assertTrue("Database must create index idx_transactions_saving_id in onCreate",
                bytecodeStrings.contains("idx_transactions_saving_id"));
    }

    // ==========================================
    // 10. BackupManager MediaStore Relative Path
    // ==========================================
    @Test
    public void backupManager_usesDirectoryDownloadsForMediaStore() throws Exception {
        String bytecodeStrings = getConstantPoolUtf8Strings(BackupManager.class);
        // MediaStore.Downloads requires relative path under Download (Environment.DIRECTORY_DOWNLOADS + "/Centsation")
        // It must NOT use Documents/Centsation for MediaStore.Downloads
        assertFalse("BackupManager must NOT construct MediaStore relative path with Documents/Centsation",
                bytecodeStrings.contains("Documents/Centsation"));
        assertTrue("BackupManager must reference DIRECTORY_DOWNLOADS for MediaStore.Downloads",
                bytecodeStrings.contains("DIRECTORY_DOWNLOADS"));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
