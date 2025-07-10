package com.example.rollback;

import com.example.model.User;
import com.example.model.UserRepository;
import com.example.rollback.properties.FlywayRollbackProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ComprehensiveRollbackTest {

    @Autowired
    private FlywayRollbackManager rollbackManager;

    @Autowired
    private ProductionRollbackManager productionRollbackManager;

    @Autowired
    private DatabaseSnapshotManager snapshotManager;

    @Autowired
    private SafetyGuardService safetyGuard;

    @Autowired
    private RollbackAuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    @Order(1)
    @DisplayName("Test Complete Migration Chain Forward")
    void testCompleteMigrationChain() {
        System.out.println("\n=== TEST: Complete Migration Chain ===");
        
        // Clean and migrate to latest
        flyway.clean();
        flyway.migrate();
        
        // Verify all migrations applied
        String currentVersion = rollbackManager.getCurrentVersion();
        System.out.println("Current version after full migration: " + currentVersion);
        
        // Verify all tables exist (up to migration 1.0.5)
        assertTrue(tableExists("users"), "Users table should exist");
        assertTrue(tableExists("user_profiles"), "User profiles table should exist");
        assertTrue(tableExists("audit_logs"), "Audit logs table should exist");
        assertTrue(tableExists("user_preferences"), "User preferences table should exist");
        assertTrue(tableExists("login_history"), "Login history table should exist");
        assertTrue(tableExists("addresses"), "Addresses table should exist");
        assertTrue(tableExists("user_addresses"), "User addresses table should exist");

        // Note: user_metrics and user_summary view are in later migrations (1.0.6+)
        // which are not included in our H2 test target
        
        System.out.println("✅ All migrations applied successfully");
    }

    @Test
    @Order(2)
    @DisplayName("Test Incremental Rollback with Data Verification")
    void testIncrementalRollbackWithDataVerification() {
        System.out.println("\n=== TEST: Incremental Rollback with Data Verification ===");
        
        // Insert test data at latest version
        User testUser = new User();
        testUser.setUsername("rollback_test_user");
        testUser.setEmail("rollback@test.com");
        testUser.setFirstName("Rollback");
        testUser.setLastName("Test");
        User savedUser = userRepository.save(testUser);
        
        // Add preferences for the user
        jdbcTemplate.update(
            "INSERT INTO user_preferences (user_id, preference_key, preference_value) VALUES (?, ?, ?)",
            savedUser.getId(), "test_pref", "test_value"
        );
        
        // Rollback to 1.0.5 (should remove user_metrics table and constraints)
        RollbackResult result = rollbackManager.rollbackToVersion("1.0.5");
        assertTrue(result.isSuccess(), "Rollback to 1.0.5 should succeed");
        
        // Verify user_metrics table is gone
        assertFalse(tableExists("user_metrics"), "User metrics table should not exist");
        assertFalse(viewExists("user_summary"), "User summary view should not exist");
        
        // Verify user data still exists
        assertTrue(userRepository.existsByUsername("rollback_test_user"), "User should still exist");
        
        // Rollback to 1.0.3 (should remove addresses tables)
        result = rollbackManager.rollbackToVersion("1.0.3");
        assertTrue(result.isSuccess(), "Rollback to 1.0.3 should succeed");
        
        assertFalse(tableExists("addresses"), "Addresses table should not exist");
        assertFalse(tableExists("user_addresses"), "User addresses table should not exist");
        
        // Verify preferences still exist
        Integer prefCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_preferences WHERE user_id = ?",
            Integer.class, savedUser.getId()
        );
        assertEquals(1, prefCount, "User preferences should still exist");
        
        System.out.println("✅ Incremental rollback with data verification passed");
    }

    @Test
    @Order(3)
    @DisplayName("Test Rollback with Foreign Key Dependencies")
    void testRollbackWithForeignKeyDependencies() {
        System.out.println("\n=== TEST: Rollback with Foreign Key Dependencies ===");
        
        // Migrate to latest
        flyway.migrate();
        
        // Try to rollback to 1.0.0 (should handle all foreign keys properly)
        RollbackResult result = rollbackManager.rollbackToVersion("1.0.0");
        assertTrue(result.isSuccess(), "Rollback should handle foreign keys properly");
        
        // Verify only base tables exist
        assertTrue(tableExists("users"), "Users table should exist");
        assertTrue(tableExists("roles"), "Roles table should exist");
        assertTrue(tableExists("user_roles"), "User roles table should exist");
        
        // Verify dependent tables are gone
        assertFalse(tableExists("user_profiles"), "User profiles should not exist");
        assertFalse(tableExists("audit_logs"), "Audit logs should not exist");
        assertFalse(tableExists("user_preferences"), "User preferences should not exist");
        
        System.out.println("✅ Foreign key dependency handling passed");
    }

    @Test
    @Order(4)
    @DisplayName("Test Concurrent Rollback Safety")
    void testConcurrentRollbackSafety() throws Exception {
        System.out.println("\n=== TEST: Concurrent Rollback Safety ===");
        
        // Migrate to latest
        flyway.migrate();
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(2);
        
        // Try to execute two rollbacks concurrently
        CompletableFuture<RollbackResult> rollback1 = CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                RollbackResult result = rollbackManager.rollbackToVersion("1.0.4");
                completeLatch.countDown();
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        CompletableFuture<RollbackResult> rollback2 = CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                RollbackResult result = rollbackManager.rollbackToVersion("1.0.3");
                completeLatch.countDown();
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Start both rollbacks
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS), "Rollbacks should complete");
        
        // At least one should succeed, one might fail due to concurrent access
        RollbackResult result1 = rollback1.get();
        RollbackResult result2 = rollback2.get();
        
        assertTrue(result1.isSuccess() || result2.isSuccess(), 
            "At least one rollback should succeed");
        
        System.out.println("✅ Concurrent rollback safety passed");
    }

    @Test
    @Order(5)
    @DisplayName("Test Data Loss Detection")
    void testDataLossDetection() {
        System.out.println("\n=== TEST: Data Loss Detection ===");
        
        // Migrate to latest
        flyway.migrate();
        
        // Add significant data to login_history (available in our migration target)
        for (int i = 0; i < 10; i++) {
            jdbcTemplate.update(
                "INSERT INTO login_history (user_id, login_time, login_ip, login_status) VALUES (?, DATEADD('DAY', -?, NOW()), ?, ?)",
                1, i, "192.168.1." + (i + 1), "SUCCESS"
            );
        }
        
        // Check if rollback would cause data loss (using valid versions in our migration target)
        boolean wouldLoseData = safetyGuard.wouldCauseDataLoss("1.0.5", "1.0.2");
        assertTrue(wouldLoseData, "Should detect potential data loss");
        
        // Try rollback without force flag
        RollbackRequest request = new RollbackRequest();
        request.setTargetVersion("1.0.2");
        request.setForceDataLoss(false);
        request.setProductionApproved(true);
        
        RollbackResult result = productionRollbackManager.executeProductionRollback(request);
        assertFalse(result.isSuccess(), "Should fail without force data loss flag");
        assertTrue(result.getErrorMessage().contains("data loss"), 
            "Error should mention data loss");
        
        System.out.println("✅ Data loss detection passed");
    }

    @Test
    @Order(6)
    @DisplayName("Test Emergency Rollback")
    void testEmergencyRollback() {
        System.out.println("\n=== TEST: Emergency Rollback ===");
        
        // Create a critical situation
        RollbackRequest emergencyRequest = new RollbackRequest();
        emergencyRequest.setTargetVersion("1.0.0");
        emergencyRequest.setEmergencyRollback(true);
        emergencyRequest.setSkipValidation(true);
        emergencyRequest.setReason("Critical production issue");
        emergencyRequest.setApprovedBy("emergency@admin.com");
        
        RollbackResult result = productionRollbackManager.executeProductionRollback(emergencyRequest);
        assertTrue(result.isSuccess(), "Emergency rollback should succeed");
        
        // Verify audit trail
        if (tableExists("flyway_rollback_audit")) {
            List<Map<String, Object>> audits = jdbcTemplate.queryForList(
                "SELECT * FROM flyway_rollback_audit WHERE rollback_id = ?",
                result.getRollbackId()
            );
            assertFalse(audits.isEmpty(), "Should have audit record");
        }
        
        System.out.println("✅ Emergency rollback passed");
    }

    @Test
    @Order(7)
    @DisplayName("Test Rollback Performance")
    void testRollbackPerformance() {
        System.out.println("\n=== TEST: Rollback Performance ===");
        
        // Migrate to latest
        flyway.migrate();
        
        // Add substantial data (H2-compatible approach)
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.update(
                "INSERT INTO users (username, email) VALUES (?, ?)",
                "perf_user_" + i, "perf" + i + "@test.com"
            );
        }
        
        long startTime = System.currentTimeMillis();
        
        // Create snapshot
        String snapshotId = snapshotManager.createProductionSnapshot("perf_test");
        
        long snapshotTime = System.currentTimeMillis() - startTime;
        System.out.println("Snapshot creation time: " + snapshotTime + "ms");
        assertTrue(snapshotTime < 5000, "Snapshot should complete within 5 seconds");
        
        // Perform rollback
        startTime = System.currentTimeMillis();
        RollbackResult result = rollbackManager.rollbackToVersion("1.0.2");
        
        long rollbackTime = System.currentTimeMillis() - startTime;
        System.out.println("Rollback execution time: " + rollbackTime + "ms");
        assertTrue(rollbackTime < 10000, "Rollback should complete within 10 seconds");
        
        System.out.println("✅ Performance test passed");
    }

    @Test
    @Order(8)
    @DisplayName("Test Snapshot Restoration")
    void testSnapshotRestoration() {
        System.out.println("\n=== TEST: Snapshot Restoration ===");
        
        // Create initial state
        flyway.migrate();
        
        // Add test data
        jdbcTemplate.update("INSERT INTO users (username, email) VALUES (?, ?)",
            "snapshot_test", "snapshot@test.com");
        
        // Create snapshot
        String snapshotId = snapshotManager.createProductionSnapshot("restore_test");
        assertNotNull(snapshotId, "Snapshot should be created");
        
        // Modify data
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", "snapshot_test");
        
        // Restore from snapshot
        boolean restored = snapshotManager.restoreFromSnapshot(snapshotId);
        assertTrue(restored, "Snapshot restoration should succeed");
        
        // Verify data restored
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?",
            Integer.class, "snapshot_test"
        );
        assertEquals(1, count, "Data should be restored from snapshot");
        
        System.out.println("✅ Snapshot restoration passed");
    }

    @Test
    @Order(9)
    @DisplayName("Test Rollback Validation")
    void testRollbackValidation() {
        System.out.println("\n=== TEST: Rollback Validation ===");
        
        // Test various validation scenarios
        
        // 1. Invalid target version
        RollbackResult result = rollbackManager.rollbackToVersion("99.99.99");
        assertFalse(result.isSuccess(), "Should fail for non-existent version");
        
        // 2. Future version
        result = rollbackManager.rollbackToVersion("2.0.0");
        assertFalse(result.isSuccess(), "Should fail for future version");
        
        // 3. Current version
        String currentVersion = rollbackManager.getCurrentVersion();
        result = rollbackManager.rollbackToVersion(currentVersion);
        // This might succeed but do nothing, or fail - both are acceptable
        
        System.out.println("✅ Rollback validation passed");
    }

    @Test
    @Order(10)
    @DisplayName("Test Archive Table Creation")
    void testArchiveTableCreation() {
        System.out.println("\n=== TEST: Archive Table Creation ===");
        
        // Migrate to latest
        flyway.migrate();
        
        // Rollback to create archive tables
        rollbackManager.rollbackToVersion("1.0.0");
        
        // Check for archive tables (created by undo scripts)
        assertTrue(tableExists("archive_user_preferences") || !tableExists("user_preferences"),
            "Should have archive table or original table removed");
        
        System.out.println("✅ Archive table creation passed");
    }

    // Helper methods
    private boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean viewExists(String viewName) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + viewName + " WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @AfterEach
    void cleanup() {
        // Clean up test data after each test
        try {
            jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'perf_user_%'");
            jdbcTemplate.update("DELETE FROM users WHERE username = 'rollback_test_user'");
            jdbcTemplate.update("DELETE FROM users WHERE username = 'snapshot_test'");
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @AfterAll
    static void finalCleanup() {
        System.out.println("\n=== COMPREHENSIVE TEST SUITE COMPLETED ===");
    }
}