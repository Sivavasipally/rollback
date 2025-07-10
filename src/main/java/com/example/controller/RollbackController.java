// src/main/java/com/example/controller/RollbackController.java
package com.example.controller;

import com.example.rollback.FlywayRollbackManager;
import com.example.rollback.RollbackRequest;
import com.example.rollback.RollbackResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flyway/rollback")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flyway.rollback.enabled", havingValue = "true", matchIfMissing = true)
public class RollbackController {

    private static final Logger log = LoggerFactory.getLogger(RollbackController.class);
    private final FlywayRollbackManager rollbackManager;
    
    @PostMapping("/execute")
    public ResponseEntity<RollbackResult> executeRollback(@RequestBody RollbackRequest request) {
        log.info("Rollback request received: {}", request);
        
        RollbackResult result = rollbackManager.rollbackToVersion(request.getTargetVersion());
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    @PostMapping("/snapshot")
    public ResponseEntity<String> createSnapshot() {
        String snapshotId = rollbackManager.createSnapshot("manual");
        return ResponseEntity.ok(snapshotId);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rollback service is running");
    }
}

