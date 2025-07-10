# Flyway Rollback Framework

A comprehensive, production-grade Spring Boot framework for managing database rollbacks with Flyway. This framework provides enterprise-level safety mechanisms, complete audit trails, and supports real-time rollbacks in production environments.

## 🏗️ Architecture Overview

### Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    Flyway Rollback Framework                    │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ ProductionRollback│  │ FlywayRollback  │  │ DatabaseSnapshot│ │
│  │    Manager      │  │    Manager      │  │    Manager      │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│           │                     │                     │        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ SafetyGuard     │  │ RollbackAudit   │  │ Configuration   │ │
│  │   Service       │  │   Service       │  │   Management    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Key Features |
|-----------|---------|--------------|
| **ProductionRollbackManager** | Orchestrates production rollbacks | Safety guards, async execution, audit integration |
| **FlywayRollbackManager** | Core rollback engine | SQL script execution, version management, snapshot integration |
| **DatabaseSnapshotManager** | Data preservation | Parallel CSV export, metadata capture, restoration capabilities |
| **SafetyGuardService** | Production safety | Environment detection, data loss prevention, integrity checks |
| **RollbackAuditService** | Compliance & tracking | Complete audit trails, notifications, compliance reporting |

## 🔄 Code Flow Architecture

### 1. Rollback Initiation Flow

```
Rollback Request → ProductionRollbackManager → Safety Guard Validation
       ↓
Production Environment Check → Enhanced Safety Checks / Basic Validation
       ↓
Create Snapshot → Execute Rollback → Verify Integrity → Update Audit Trail → Send Notifications
```

### 2. SQL Execution Flow

```
Rollback Script → Parse SQL Statements → Execute DDL Operations → Update Schema History → Verify Execution → Log Results
```

### 3. Safety Guard Flow

```
Safety Check Request → Environment Detection → Database Type Detection → Business Hours Check
       ↓
Data Loss Analysis → Connection Pool Check → Integrity Verification → Safety Decision
```

## 📦 Installation & Setup

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Spring Boot 3.2+
- Database (MySQL, H2, PostgreSQL, etc.)

### Step 1: Add Dependencies

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Flyway -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Configuration Setup

Create `application.yml`:

```yaml
# Database Configuration
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database
    username: ${DB_USERNAME:your_username}
    password: ${DB_PASSWORD:your_password}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect

# Flyway Configuration
flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: true
  validate-on-migrate: true

# Flyway Rollback Framework Configuration
flyway:
  rollback:
    enabled: true
    snapshot-enabled: true
    production-mode: false
    max-rollback-versions: 10
    snapshot-retention-days: 30
    parallel-snapshot-threads: 5
    safety-guards:
      check-business-hours: true
      require-approval: true
      max-connection-threshold: 80
    notifications:
      enabled: true
      webhook-url: ${ROLLBACK_WEBHOOK_URL:}
      teams-license-key: ${TEAMS_LICENSE_KEY:}

# Logging
logging:
  level:
    com.example.rollback: DEBUG
    org.flywaydb: INFO
```

### Step 3: Directory Structure Setup

Create the following directory structure:

```
src/
├── main/
│   ├── java/
│   │   └── com/example/
│   │       ├── FlywayDemoApplication.java
│   │       ├── config/
│   │       │   ├── FlywayRollbackConfiguration.java
│   │       │   └── ProductionRollbackConfiguration.java
│   │       └── rollback/
│   │           ├── ProductionRollbackManager.java
│   │           ├── FlywayRollbackManager.java
│   │           ├── DatabaseSnapshotManager.java
│   │           ├── SafetyGuardService.java
│   │           ├── RollbackAuditService.java
│   │           └── properties/
│   │               └── FlywayRollbackProperties.java
│   └── resources/
│       ├── application.yml
│       └── db/
│           └── migration/
│               ├── V1.0.0__Create_initial_schema.sql
│               ├── V1.0.1__Add_user_profile_table.sql
│               ├── V1.0.2__Add_audit_log_table.sql
│               ├── U1.0.0__Create_initial_schema_undo.sql
│               ├── U1.0.1__Add_user_profile_table_undo.sql
│               └── U1.0.2__Add_audit_log_table_undo.sql
└── test/
    ├── java/
    │   └── com/example/
    │       ├── BasicConfigurationTest.java
    │       ├── FlywayRollbackTest.java
    │       ├── SimpleApplicationTest.java
    │       └── rollback/
    │           └── FlywayRollbackIntegrationTest.java
    └── resources/
        └── application-test.yml
```

### Step 4: Migration Scripts

Create forward migration scripts in `src/main/resources/db/migration/`:

**V1.0.0__Create_initial_schema.sql:**
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE user_roles (
    user_id BIGINT,
    role_id BIGINT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);
```

Create corresponding rollback scripts with `U` prefix:

**U1.0.0__Create_initial_schema_undo.sql:**
```sql
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
```

### Step 5: Enable Framework

Add to your main application class:

```java
@SpringBootApplication
@EnableJpaRepositories
public class FlywayDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlywayDemoApplication.class, args);
    }
}
```

## 🚀 Usage Guide

### Basic Rollback Operations

```java
@RestController
@RequestMapping("/api/rollback")
public class RollbackController {

    @Autowired
    private ProductionRollbackManager rollbackManager;

    @Autowired
    private FlywayRollbackManager basicRollbackManager;

    // Basic rollback
    @PostMapping("/basic/{version}")
    public ResponseEntity<String> basicRollback(@PathVariable String version) {
        try {
            String rollbackId = basicRollbackManager.rollbackToVersion(version);
            return ResponseEntity.ok("Rollback completed: " + rollbackId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Rollback failed: " + e.getMessage());
        }
    }

    // Production rollback with safety checks
    @PostMapping("/production/{version}")
    public ResponseEntity<String> productionRollback(
            @PathVariable String version,
            @RequestParam String reason,
            @RequestParam(required = false) String ticketNumber) {
        try {
            String rollbackId = rollbackManager.rollbackToVersion(version, reason, ticketNumber);
            return ResponseEntity.ok("Production rollback completed: " + rollbackId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Production rollback failed: " + e.getMessage());
        }
    }

    // Dry run rollback
    @PostMapping("/dry-run/{version}")
    public ResponseEntity<String> dryRunRollback(@PathVariable String version) {
        try {
            String rollbackId = rollbackManager.dryRunRollback(version, "Dry run test");
            return ResponseEntity.ok("Dry run completed: " + rollbackId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Dry run failed: " + e.getMessage());
        }
    }
}
```

### Snapshot Management

```java
@Service
public class BackupService {

    @Autowired
    private DatabaseSnapshotManager snapshotManager;

    public String createBackup(String snapshotName) {
        return snapshotManager.createSnapshot(snapshotName);
    }

    public List<String> listSnapshots() {
        return snapshotManager.listSnapshots();
    }

    public boolean restoreSnapshot(String snapshotName) {
        return snapshotManager.restoreSnapshot(snapshotName);
    }
}
```

### Safety Guard Configuration

```java
@Component
public class CustomSafetyGuards {

    @Autowired
    private SafetyGuardService safetyGuard;

    public boolean isRollbackSafe(String fromVersion, String toVersion) {
        // Custom business logic
        if (isBusinessCriticalHours()) {
            return false;
        }

        // Check data loss potential
        return !safetyGuard.wouldCauseDataLoss(fromVersion, toVersion);
    }

    private boolean isBusinessCriticalHours() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(17, 0));
    }
}
```

## 🧪 Testing Approaches

### Test Environment Setup

The framework includes comprehensive testing with H2 database:

**application-test.yml:**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

flyway:
  enabled: true
  locations: classpath:db/migration
  rollback:
    enabled: true
    snapshot-enabled: true
    production-mode: false
    parallel-snapshot-threads: 3

logging:
  level:
    com.example.rollback: DEBUG
    org.flywaydb: DEBUG
```

### Test Categories

#### 1. Unit Tests

**Basic Configuration Test:**
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "flyway.rollback.enabled=false",
    "logging.level.org.springframework=INFO"
})
class BasicConfigurationTest {

    @Test
    void contextLoads() {
        // Verifies Spring context loads when rollback framework is disabled
        assertThat(true).isTrue();
    }
}
```

**Core Rollback Test:**
```java
@SpringBootTest
@TestPropertySource(properties = {
    "flyway.rollback.enabled=true",
    "flyway.rollback.snapshot-enabled=true"
})
class FlywayRollbackTest {

    @Autowired
    private FlywayRollbackManager rollbackManager;

    @Test
    void testSnapshotCreation() {
        String snapshotId = rollbackManager.createSnapshot("test_snapshot");
        assertThat(snapshotId).isNotNull();
    }

    @Test
    void testCompleteRollback() {
        String rollbackId = rollbackManager.rollbackToVersion("1");
        assertThat(rollbackId).isNotNull();
    }
}
```

#### 2. Integration Tests

**Comprehensive Integration Test:**
```java
@SpringBootTest
@TestPropertySource(properties = {
    "flyway.rollback.enabled=true",
    "flyway.rollback.production-mode=false"
})
class FlywayRollbackIntegrationTest {

    @Test
    void testInitialDatabaseState() {
        // Verify database is properly migrated to latest version
    }

    @Test
    void testCreateSnapshot() {
        // Test snapshot creation with H2 system table handling
    }

    @Test
    void testBasicRollback() {
        // Test single version rollback (1.0.2 → 1.0.1)
    }

    @Test
    void testProductionRollback() {
        // Test production rollback with safety guards (1.0.1 → 1.0.0)
    }

    @Test
    void testDryRunRollback() {
        // Test dry run functionality
    }

    @Test
    void testSafetyGuardValidations() {
        // Test all safety guard mechanisms
    }

    @Test
    void testRollbackAudit() {
        // Test audit trail and notifications
    }
}
```

#### 3. Performance Tests

```java
@SpringBootTest
class PerformanceTest {

    @Test
    void testParallelSnapshotCreation() {
        // Test concurrent snapshot creation performance
        long startTime = System.currentTimeMillis();
        String snapshotId = snapshotManager.createSnapshot("perf_test");
        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(10000); // Should complete within 10 seconds
    }

    @Test
    void testLargeDatasetRollback() {
        // Test rollback performance with large datasets
    }
}
```

### Running Tests

#### Complete Test Suite
```bash
# Run all tests
mvn test

# Run with detailed logging
mvn test -Dlogging.level.com.example.rollback=DEBUG

# Run specific test class
mvn test -Dtest=FlywayRollbackIntegrationTest

# Run with coverage
mvn test jacoco:report
```

#### Test Categories
```bash
# Unit tests only
mvn test -Dtest="*Test"

# Integration tests only
mvn test -Dtest="*IntegrationTest"

# Performance tests only
mvn test -Dtest="*PerformanceTest"
```

### Test Data Management

#### Test Migration Scripts

Create test-specific migrations in `src/test/resources/db/migration/`:

**V1.0.3__Add_test_data.sql:**
```sql
INSERT INTO users (username, email) VALUES
('testuser1', 'test1@example.com'),
('testuser2', 'test2@example.com');

INSERT INTO roles (name, description) VALUES
('ADMIN', 'Administrator role'),
('USER', 'Regular user role');
```

**U1.0.3__Remove_test_data_undo.sql:**
```sql
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'testuser%');
DELETE FROM users WHERE username LIKE 'testuser%';
DELETE FROM roles WHERE name IN ('ADMIN', 'USER');
```

#### Test Utilities

```java
@TestComponent
public class TestDataHelper {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertTestData() {
        jdbcTemplate.execute("INSERT INTO users (username, email) VALUES ('test', 'test@example.com')");
    }

    public void cleanupTestData() {
        jdbcTemplate.execute("DELETE FROM users WHERE username = 'test'");
    }

    public int getUserCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }
}
```

## 🔧 Advanced Features

### Custom Safety Guards

Implement custom safety guards for specific business requirements:

```java
@Component
public class CustomBusinessSafetyGuard implements SafetyGuardValidator {

    @Override
    public boolean validateRollback(String fromVersion, String toVersion, RollbackContext context) {
        // Custom validation logic
        if (isBlackoutPeriod()) {
            throw new RollbackException("Rollback not allowed during blackout period");
        }

        if (hasActiveTransactions()) {
            throw new RollbackException("Active transactions detected");
        }

        return true;
    }

    private boolean isBlackoutPeriod() {
        // Check if current time is in maintenance blackout period
        LocalDateTime now = LocalDateTime.now();
        return now.getHour() >= 2 && now.getHour() <= 4; // 2 AM - 4 AM
    }

    private boolean hasActiveTransactions() {
        // Check for long-running transactions
        return false; // Implementation depends on database type
    }
}
```

### Custom Notification Handlers

```java
@Component
public class SlackNotificationHandler implements NotificationHandler {

    @Value("${slack.webhook.url}")
    private String slackWebhookUrl;

    @Override
    public void sendNotification(RollbackEvent event) {
        SlackMessage message = SlackMessage.builder()
            .text("🚨 Database Rollback Alert")
            .attachment(SlackAttachment.builder()
                .color(event.isSuccess() ? "good" : "danger")
                .title("Rollback Details")
                .field("Version", event.getTargetVersion(), true)
                .field("Reason", event.getReason(), true)
                .field("Status", event.getStatus(), true)
                .field("Timestamp", event.getTimestamp().toString(), true)
                .build())
            .build();

        // Send to Slack
        restTemplate.postForEntity(slackWebhookUrl, message, String.class);
    }
}
```

### Database-Specific Implementations

#### MySQL-Specific Features

```java
@Component
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "com.mysql.cj.jdbc.Driver")
public class MySQLSafetyGuard extends SafetyGuardService {

    @Override
    public boolean hasLongRunningTransactions() {
        String sql = """
            SELECT COUNT(*) FROM information_schema.PROCESSLIST
            WHERE COMMAND != 'Sleep'
            AND TIME > ?
            AND USER != 'system user'
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 300); // 5 minutes
        return count != null && count > 0;
    }

    @Override
    public List<String> getActiveConnections() {
        String sql = """
            SELECT CONCAT(USER, '@', HOST, ' - ', DB, ' (', TIME, 's)') as connection_info
            FROM information_schema.PROCESSLIST
            WHERE COMMAND != 'Sleep'
            """;

        return jdbcTemplate.queryForList(sql, String.class);
    }
}
```

#### PostgreSQL-Specific Features

```java
@Component
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.postgresql.Driver")
public class PostgreSQLSafetyGuard extends SafetyGuardService {

    @Override
    public boolean hasLongRunningTransactions() {
        String sql = """
            SELECT COUNT(*) FROM pg_stat_activity
            WHERE state = 'active'
            AND query_start < NOW() - INTERVAL '5 minutes'
            AND usename != 'postgres'
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null && count > 0;
    }
}
```

## 📊 Monitoring & Observability

### Metrics Integration

```java
@Component
public class RollbackMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter rollbackCounter;
    private final Timer rollbackTimer;

    public RollbackMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.rollbackCounter = Counter.builder("rollback.executions")
            .description("Number of rollback executions")
            .tag("type", "production")
            .register(meterRegistry);
        this.rollbackTimer = Timer.builder("rollback.duration")
            .description("Rollback execution time")
            .register(meterRegistry);
    }

    public void recordRollback(String version, boolean success, Duration duration) {
        rollbackCounter.increment(
            Tags.of(
                "version", version,
                "success", String.valueOf(success)
            )
        );
        rollbackTimer.record(duration);
    }
}
```

### Health Checks

```java
@Component
public class RollbackHealthIndicator implements HealthIndicator {

    @Autowired
    private FlywayRollbackManager rollbackManager;

    @Override
    public Health health() {
        try {
            boolean isHealthy = rollbackManager.isSystemHealthy();

            if (isHealthy) {
                return Health.up()
                    .withDetail("rollback-framework", "operational")
                    .withDetail("last-check", Instant.now())
                    .build();
            } else {
                return Health.down()
                    .withDetail("rollback-framework", "degraded")
                    .withDetail("issue", "System health check failed")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("rollback-framework", "error")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## 🚨 Troubleshooting

### Common Issues

#### 1. Rollback Script Not Found

**Error:** `RollbackScriptNotFoundException: Rollback script not found for version 1.0.1`

**Solution:**
- Ensure rollback script exists: `U1.0.1__Description_undo.sql`
- Check file naming convention matches forward migration
- Verify script is in correct location: `src/main/resources/db/migration/`

#### 2. H2 System Table Warnings

**Warning:** `Failed to export table to CSV: INFORMATION_SCHEMA`

**Solution:**
This is expected behavior. H2 system tables cannot be exported and are safely ignored.

#### 3. Production Safety Guard Failures

**Error:** `RollbackException: Production rollback blocked by safety guards`

**Solution:**
- Check business hours configuration
- Verify no long-running transactions
- Ensure proper approval workflow
- Review data loss analysis results

#### 4. Snapshot Creation Failures

**Error:** `SnapshotException: Failed to create database snapshot`

**Solution:**
- Check disk space availability
- Verify write permissions to snapshot directory
- Review database connection pool settings
- Check for table locks

### Debug Configuration

Enable detailed logging for troubleshooting:

```yaml
logging:
  level:
    com.example.rollback: DEBUG
    org.flywaydb: DEBUG
    org.springframework.jdbc: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Performance Tuning

#### Snapshot Performance

```yaml
flyway:
  rollback:
    parallel-snapshot-threads: 10  # Increase for better performance
    snapshot-batch-size: 1000      # Larger batches for big tables
    snapshot-timeout-minutes: 30   # Increase for large databases
```

#### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

## 📋 Best Practices

### 1. Rollback Script Guidelines

- **Always test rollback scripts** in development environment
- **Use IF EXISTS clauses** for DROP statements
- **Maintain referential integrity** during rollbacks
- **Document complex rollback logic** with comments
- **Version rollback scripts** alongside forward migrations

### 2. Production Rollback Checklist

- [ ] Verify rollback scripts exist and are tested
- [ ] Create database snapshot before rollback
- [ ] Check for active transactions
- [ ] Notify stakeholders of planned rollback
- [ ] Have rollback approval from authorized personnel
- [ ] Monitor system during and after rollback
- [ ] Verify application functionality post-rollback

### 3. Safety Guard Configuration

- **Enable all safety guards** in production
- **Configure business hours** appropriately
- **Set connection thresholds** based on normal load
- **Implement custom validators** for business-specific rules
- **Test safety guards** regularly

### 4. Monitoring & Alerting

- **Set up alerts** for rollback executions
- **Monitor rollback success rates**
- **Track rollback duration** trends
- **Alert on safety guard failures**
- **Monitor snapshot creation** performance

## 🤝 Contributing

### Development Setup

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Make changes and add tests
4. Run test suite: `mvn test`
5. Commit changes: `git commit -m 'Add amazing feature'`
6. Push to branch: `git push origin feature/amazing-feature`
7. Open Pull Request

### Code Standards

- Follow Spring Boot conventions
- Maintain test coverage above 90%
- Add JavaDoc for public APIs
- Use meaningful commit messages
- Update documentation for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Spring Boot team for excellent framework
- Flyway team for database migration tools
- H2 Database for testing capabilities
- Community contributors and feedback

---

**Built with ❤️ for production-grade database rollback management**