// src/test/java/com/example/FlywayRollbackTest.java
package com.example;

import com.example.rollback.FlywayRollbackManager;
import com.example.rollback.RollbackResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayRollbackTest {
    
    @Autowired
    private FlywayRollbackManager rollbackManager;
    
    @Test
    void testCreateSnapshot() {
        String snapshotId = rollbackManager.createSnapshot("test");
        assertThat(snapshotId).isNotNull();
        assertThat(snapshotId).startsWith("test_");
    }
    
    @Test
    void testRollback() {
        // Create snapshot first
        String snapshotId = rollbackManager.createSnapshot("test_before_rollback");
        assertThat(snapshotId).isNotNull();
        
        // Test rollback
        RollbackResult result = rollbackManager.rollbackToVersion("1");
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTargetVersion()).isEqualTo("1");
    }
}