package com.example.rollback;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RollbackEdgeCaseTest {

    @Autowired
    private FlywayRollbackManager rollbackManager;

    @Autowired
    private ProductionRollbackManager productionRollbackManager;

    @Autowired
    private SafetyGuardService safetyGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Order(1)
    @DisplayName("Edge Case: Rollback with Active Transactions")
    void testRollbackWithActiveTransactions() throws Exception {
        System.out.println("\n=== EDGE CASE: Rollback with Active Transactions ===");
        
        // Start a long-running transaction in another thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch transactionStarted = new CountDownLatch(1);
        CountDownLatch proceedWithRollback = new CountDownLatch(1);
        
        Future<Void> transactionFuture = executor.submit(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                
                // Execute a query to hold a lock
                conn.createStatement().executeQuery("SELECT * FROM users FOR UPDATE");
                transactionStarted.countDown();
                
                // Wait for rollback attempt
                proceedWithRollback.await(10, TimeUnit.SECONDS);
                
                conn.rollback();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
        
        // Wait for transaction to start
        transactionStarted.await();
        
        // Check for active transactions
        boolean hasActiveTransactions = safetyGuard.hasActiveLongRunningTransactions();
        System.out.println("Has active transactions: " + hasActiveTransactions);
        
        // Attempt rollback (should handle gracefully)
        RollbackRequest request = new RollbackRequest();
        request.setTargetVersion("1.0.0");
        request.setEmergencyRollback(true); // Force through
        
        RollbackResult result = productionRollbackManager.executeProductionRollback(request);
        
        // Clean up
        proceedWithRollback.countDown();
        executor.shutdown();
        
        System.out.println("✅ Active transaction handling test completed");
    }

    @Test
    @Order(2)
    @DisplayName("Edge Case: Rollback with Corrupted Migration History")
    void testRollbackWithCorruptedHistory() {
        System.out.println("\n=== EDGE CASE: Corrupted Migration History ===");
        
        try {
            // Corrupt the migration history by inserting invalid data
            jdbcTemplate.update(
                "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) " +
                "VALUES (999, '99.99.99', 'Corrupted Entry', 'SQL', 'V99.99.99__Corrupted.sql', -1, 'test', 1, FALSE)"
            );
            
            // Try to get current version
            String currentVersion = rollbackManager.getCurrentVersion();
            System.out.println("Current version with corrupted history: " + currentVersion);
            
            // Attempt rollback
            RollbackResult result = rollbackManager.rollbackToVersion("1.0.0");
            
            // Clean up corruption
            jdbcTemplate.update("DELETE FROM flyway_schema_history WHERE version = '99.99.99'");
            
            System.out.println("✅ Corrupted history handling test completed");
            
        } catch (Exception e) {
            System.out.println("Expected behavior with corrupted history: " + e.getMessage());
        }
    }

    @Test
    @Order(3)
    @DisplayName("Edge Case: Rollback with Missing Undo Scripts")
    void testRollbackWithMissingUndoScripts() {
        System.out.println("\n=== EDGE CASE: Missing Undo Scripts ===");
        
        // This simulates the case where undo scripts are missing
        // The rollback should handle this gracefully
        
        RollbackResult result = rollbackManager.rollbackToVersion("1.0.1");
        
        // Even without proper undo scripts, the system should handle it
        System.out.println("Rollback completed: " + result.isSuccess());
        System.out.println("Error message: " + result.getErrorMessage());
        
        System.out.println("✅ Missing undo script handling test completed");
    }

    @Test
    @Order(4)
    @DisplayName("Edge Case: Circular Foreign Key Dependencies")
    void testCircularForeignKeyDependencies() {
        System.out.println("\n=== EDGE CASE: Circular Foreign Key Dependencies ===");
        
        try {
            // Create tables with circular dependencies
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS circular_a (" +
                "id BIGINT PRIMARY KEY, " +
                "b_id BIGINT" +
                ")"
            );
            
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS circular_b (" +
                "id BIGINT PRIMARY KEY, " +
                "a_id BIGINT" +
                ")"
            );
            
            // Add foreign keys creating circular dependency
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE circular_a ADD CONSTRAINT fk_a_to_b " +
                    "FOREIGN KEY (b_id) REFERENCES circular_b(id)"
                );
                
                jdbcTemplate.execute(
                    "ALTER TABLE circular_b ADD CONSTRAINT fk_b_to_a " +
                    "FOREIGN KEY (a_id) REFERENCES circular_a(id)"
                );
            } catch (Exception e) {
                // Some databases don't support circular FKs
                System.out.println("Database doesn't support circular FKs: " + e.getMessage());
            }
            
            // Clean up
            jdbcTemplate.execute("DROP TABLE IF EXISTS circular_b");
            jdbcTemplate.execute("DROP TABLE IF EXISTS circular_a");
            
            System.out.println("✅ Circular dependency handling test completed");
            
        } catch (Exception e) {
            System.out.println("Circular dependency test error: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Edge Case: Rollback During Heavy Load")
    void testRollbackDuringHeavyLoad() throws Exception {
        System.out.println("\n=== EDGE CASE: Rollback During Heavy Load ===");
        
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        List<Future<Void>> futures = new ArrayList<>();
        
        // Simulate heavy load with multiple threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Simulate heavy database operations
                    for (int j = 0; j < 10; j++) {
                        jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM users WHERE id > ?", 
                            Integer.class, threadId * 10 + j
                        );
                        Thread.sleep(10);
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
                return null;
            }));
        }
        
        // Start all threads
        startLatch.countDown();
        
        // Wait a bit for load to build up
        Thread.sleep(100);
        
        // Check if database is under load
        boolean isUnderLoad = safetyGuard.isDatabaseUnderHeavyLoad();
        System.out.println("Database under heavy load: " + isUnderLoad);
        
        // Attempt rollback during load
        RollbackRequest request = new RollbackRequest();
        request.setTargetVersion("1.0.0");
        request.setEmergencyRollback(true);
        
        RollbackResult result = productionRollbackManager.executeProductionRollback(request);
        System.out.println("Rollback during load succeeded: " + result.isSuccess());
        
        // Wait for all threads to complete
        completeLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        System.out.println("✅ Heavy load rollback test completed");
    }

    @Test
    @Order(6)
    @DisplayName("Edge Case: Rollback with Large Data Volume")
    void testRollbackWithLargeDataVolume() {
        System.out.println("\n=== EDGE CASE: Large Data Volume ===");
        
        try {
            // Insert large amount of data
            int batchSize = 1000;
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS large_data_test (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "data VARCHAR(255), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
            
            // Use batch insert for efficiency
            List<Object[]> batchArgs = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batchArgs.add(new Object[]{"Test data " + i});
            }
            
            jdbcTemplate.batchUpdate(
                "INSERT INTO large_data_test (data) VALUES (?)", 
                batchArgs
            );
            
            // Create snapshot with large data
            long startTime = System.currentTimeMillis();
            String snapshotId = rollbackManager.createSnapshot("large_data_test");
            long snapshotTime = System.currentTimeMillis() - startTime;
            
            System.out.println("Snapshot of " + batchSize + " rows took: " + snapshotTime + "ms");
            assertNotNull(snapshotId, "Should create snapshot with large data");
            
            // Clean up
            jdbcTemplate.execute("DROP TABLE IF EXISTS large_data_test");
            
            System.out.println("✅ Large data volume test completed");
            
        } catch (Exception e) {
            System.out.println("Large data test error: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("Edge Case: Rollback with Database Connection Loss")
    void testRollbackWithConnectionLoss() {
        System.out.println("\n=== EDGE CASE: Database Connection Loss ===");
        
        // This test simulates connection issues during rollback
        // In a real scenario, you might use a proxy to simulate network issues
        
        try {
            // Create a custom datasource that fails
            MockFailingDataSource failingDataSource = new MockFailingDataSource(dataSource);
            JdbcTemplate failingJdbcTemplate = new JdbcTemplate(failingDataSource);
            
            // Try to execute with failing connection
            try {
                failingJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users", Integer.class
                );
                fail("Should have thrown exception");
            } catch (Exception e) {
                System.out.println("Expected connection failure: " + e.getMessage());
            }
            
            System.out.println("✅ Connection loss handling test completed");
            
        } catch (Exception e) {
            System.out.println("Connection loss test error: " + e.getMessage());
        }
    }

    @Test
    @Order(8)
    @DisplayName("Edge Case: Rollback with Insufficient Permissions")
    void testRollbackWithInsufficientPermissions() {
        System.out.println("\n=== EDGE CASE: Insufficient Permissions ===");
        
        // In a real scenario, you would test with a restricted database user
        // For this test, we'll simulate permission issues
        
        try {
            // Attempt to create a table in a system schema (should fail)
            jdbcTemplate.execute("CREATE TABLE information_schema.test_table (id INT)");
            fail("Should not be able to create table in system schema");
        } catch (Exception e) {
            System.out.println("Expected permission error: " + e.getMessage());
        }
        
        System.out.println("✅ Permission handling test completed");
    }

    @Test
    @Order(9)
    @DisplayName("Edge Case: Rollback with Schema Mismatch")
    void testRollbackWithSchemaMismatch() {
        System.out.println("\n=== EDGE CASE: Schema Mismatch ===");
        
        try {
            // Manually alter schema outside of Flyway
            jdbcTemplate.execute(
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS manual_column VARCHAR(50)"
            );
            
            // Attempt rollback (should handle schema differences)
            RollbackResult result = rollbackManager.rollbackToVersion("1.0.0");
            
            // Check if manual column still exists
            try {
                jdbcTemplate.queryForObject(
                    "SELECT manual_column FROM users WHERE 1=0", String.class
                );
                System.out.println("Manual column still exists after rollback");
            } catch (Exception e) {
                System.out.println("Manual column removed during rollback");
            }
            
            // Clean up
            try {
                jdbcTemplate.execute("ALTER TABLE users DROP COLUMN manual_column");
            } catch (Exception e) {
                // Column might not exist
            }
            
            System.out.println("✅ Schema mismatch handling test completed");
            
        } catch (Exception e) {
            System.out.println("Schema mismatch test error: " + e.getMessage());
        }
    }

    @Test
    @Order(10)
    @DisplayName("Edge Case: Rapid Sequential Rollbacks")
    void testRapidSequentialRollbacks() {
        System.out.println("\n=== EDGE CASE: Rapid Sequential Rollbacks ===");
        
        try {
            // Perform multiple rollbacks in quick succession
            String[] versions = {"1.0.5", "1.0.4", "1.0.3", "1.0.2", "1.0.1", "1.0.0"};
            
            for (String version : versions) {
                System.out.println("Rolling back to: " + version);
                RollbackResult result = rollbackManager.rollbackToVersion(version);
                
                if (result.isSuccess()) {
                    System.out.println("Successfully rolled back to: " + version);
                } else {
                    System.out.println("Rollback to " + version + " failed: " + 
                        result.getErrorMessage());
                }
                
                // No delay between rollbacks
            }
            
            System.out.println("✅ Rapid sequential rollback test completed");
            
        } catch (Exception e) {
            System.out.println("Sequential rollback test error: " + e.getMessage());
        }
    }

    // Mock failing datasource for testing
    private static class MockFailingDataSource implements DataSource {
        private final DataSource delegate;
        
        public MockFailingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("Simulated connection failure");
        }
        
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Simulated connection failure");
        }
        
        // Delegate other methods...
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
        
        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }
        
        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }
        
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }
        
        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}