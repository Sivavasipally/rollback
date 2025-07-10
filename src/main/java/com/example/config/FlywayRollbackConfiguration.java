// src/main/java/com/example/config/FlywayRollbackConfiguration.java
package com.example.config;

import com.example.rollback.FlywayRollbackManager;
import com.example.rollback.properties.FlywayRollbackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(FlywayRollbackProperties.class)
public class FlywayRollbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlywayRollbackConfiguration.class);

    @Bean
    public FlywayRollbackManager flywayRollbackManager(
            DataSource dataSource, 
            FlywayRollbackProperties properties) {
        return new FlywayRollbackManager(dataSource, properties);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(FlywayRollbackManager rollbackManager) {
        return flyway -> {
            log.info("Starting Flyway migration with rollback support");
            
            // Create pre-migration snapshot if enabled
            if (rollbackManager.isSnapshotEnabled()) {
                try {
                    String snapshotId = rollbackManager.createPreMigrationSnapshot();
                    log.info("Created pre-migration snapshot: {}", snapshotId);
                } catch (Exception e) {
                    log.warn("Failed to create pre-migration snapshot", e);
                }
            }
            
            // Execute migration
            try {
                flyway.migrate();
                log.info("Flyway migration completed successfully");
            } catch (Exception e) {
                log.error("Flyway migration failed", e);
                
                // Auto-rollback if enabled
                if (rollbackManager.isAutoRollbackEnabled()) {
                    log.info("Attempting automatic rollback");
                    rollbackManager.handleMigrationFailure(e);
                }
                
                throw e;
            }
        };
    }
}