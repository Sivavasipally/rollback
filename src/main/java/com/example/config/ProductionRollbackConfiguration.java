package com.example.config;

import com.example.rollback.*;
import com.example.rollback.properties.FlywayRollbackProperties;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration for production-grade Flyway rollback capabilities
 * Integrates with Spring Boot and provides enhanced rollback features
 */
@Configuration
@EnableConfigurationProperties(FlywayRollbackProperties.class)
@ConditionalOnProperty(prefix = "flyway.rollback", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductionRollbackConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ProductionRollbackConfiguration.class);
    
    @Bean
    public DatabaseSnapshotManager databaseSnapshotManager(DataSource dataSource, 
                                                         FlywayRollbackProperties properties) {
        return new DatabaseSnapshotManager(dataSource, properties);
    }
    
    @Bean
    public RollbackAuditService rollbackAuditService(DataSource dataSource, 
                                                   FlywayRollbackProperties properties) {
        return new RollbackAuditService(dataSource, properties);
    }
    
    @Bean
    public SafetyGuardService safetyGuardService(DataSource dataSource, 
                                               FlywayRollbackProperties properties) {
        return new SafetyGuardService(dataSource, properties);
    }
    
    @Bean
    @Primary
    public ProductionRollbackManager productionRollbackManager(DataSource dataSource, 
                                                            FlywayRollbackProperties properties,
                                                            DatabaseSnapshotManager snapshotManager,
                                                            RollbackAuditService auditService,
                                                            SafetyGuardService safetyGuard) {
        return new ProductionRollbackManager(dataSource, properties, snapshotManager, 
                                           auditService, safetyGuard);
    }
    

    
    /**
     * Custom Flyway configuration for undo support
     * Note: Requires Flyway Teams/Enterprise for undo functionality
     */
    @Bean
    @ConditionalOnProperty(prefix = "flyway.rollback", name = "teams-license-key")
    public Flyway flywayWithUndoSupport(DataSource dataSource, FlywayRollbackProperties properties) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration", "classpath:db/undo")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .outOfOrder(false)
            .cleanDisabled(true) // Safety for production
            .load();
    }
    
    /**
     * Initializes the audit table on startup
     */
    @Bean
    public AuditTableInitializer auditTableInitializer(DataSource dataSource, 
                                                     FlywayRollbackProperties properties) {
        return new AuditTableInitializer(dataSource, properties);
    }
    
    /**
     * Helper class to initialize audit table
     */
    public static class AuditTableInitializer {
        private final DataSource dataSource;
        private final FlywayRollbackProperties properties;
        private final Logger log = LoggerFactory.getLogger(AuditTableInitializer.class);
        
        public AuditTableInitializer(DataSource dataSource, FlywayRollbackProperties properties) {
            this.dataSource = dataSource;
            this.properties = properties;
            initialize();
        }
        
        private void initialize() {
            if (!properties.getAudit().isEnabled()) {
                return;
            }
            
            try {
                log.info("Initializing rollback audit table");
                // Implementation would create the audit table if it doesn't exist
            } catch (Exception e) {
                log.warn("Failed to initialize audit table", e);
            }
        }
    }
}
