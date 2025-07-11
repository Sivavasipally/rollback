# End-to-End Flow Diagrams for Flyway Rollback Framework

## 1. Application Startup Flow

```mermaid
flowchart TD
    Start([Application Start]) --> LoadConfig[Load application.yml]
    LoadConfig --> CheckProfile{Check Active Profile}
    
    CheckProfile -->|local/default| H2Config[Configure H2 Database]
    CheckProfile -->|mysql| MySQLConfig[Configure MySQL]
    CheckProfile -->|test| TestConfig[Configure Test H2]
    
    H2Config --> InitDS[Initialize DataSource]
    MySQLConfig --> InitDS
    TestConfig --> InitDS
    
    InitDS --> InitFlyway[Initialize Flyway]
    InitFlyway --> CheckRollback{Rollback Enabled?}
    
    CheckRollback -->|Yes| CreateSnapshot[Create Pre-Migration Snapshot]
    CheckRollback -->|No| RunMigrations
    
    CreateSnapshot --> SnapshotSuccess{Snapshot Success?}
    SnapshotSuccess -->|Yes| RunMigrations[Run Flyway Migrations]
    SnapshotSuccess -->|No| LogWarning[Log Warning]
    LogWarning --> RunMigrations
    
    RunMigrations --> MigrationResult{Migration Success?}
    MigrationResult -->|Yes| StartApp[Start Spring Boot App]
    MigrationResult -->|No| CheckAutoRollback{Auto-Rollback Enabled?}
    
    CheckAutoRollback -->|Yes| AutoRollback[Execute Auto Rollback]
    CheckAutoRollback -->|No| ThrowError[Throw Exception]
    
    StartApp --> Ready([Application Ready])
    
    style Start fill:#90EE90
    style Ready fill:#90EE90
    style ThrowError fill:#FFB6C1
```

## 2. Migration Execution Flow

```mermaid
flowchart TD
    MigrationStart([Migration Process Start]) --> LoadScripts[Load Migration Scripts]
    LoadScripts --> SortScripts[Sort by Version Number]
    
    SortScripts --> GetCurrent[Get Current Version from DB]
    GetCurrent --> FilterPending[Filter Pending Migrations]
    
    FilterPending --> HasPending{Has Pending Migrations?}
    HasPending -->|No| MigrationComplete([No Migrations Needed])
    HasPending -->|Yes| BeginTransaction[Begin Transaction]
    
    BeginTransaction --> ExecuteScript[Execute Migration Script]
    ExecuteScript --> CheckResult{Execution Success?}
    
    CheckResult -->|Yes| UpdateHistory[Update flyway_schema_history]
    CheckResult -->|No| DBSupportsRollback{DB Supports DDL Rollback?}
    
    DBSupportsRollback -->|PostgreSQL/H2| RollbackTransaction[Rollback Transaction]
    DBSupportsRollback -->|MySQL/MariaDB| PartialState[Database in Partial State]
    
    UpdateHistory --> MoreScripts{More Scripts?}
    MoreScripts -->|Yes| ExecuteScript
    MoreScripts -->|No| CommitTransaction[Commit Transaction]
    
    CommitTransaction --> MigrationSuccess([Migration Complete])
    RollbackTransaction --> MigrationFailed([Migration Failed - Rolled Back])
    PartialState --> RequiresManualFix([Requires Manual Intervention])
    
    style MigrationStart fill:#87CEEB
    style MigrationComplete fill:#90EE90
    style MigrationSuccess fill:#90EE90
    style MigrationFailed fill:#FFB6C1
    style RequiresManualFix fill:#FF6347
```

## 3. Rollback Request Flow (REST API)

```mermaid
flowchart TD
    APIRequest([POST /api/flyway/rollback/execute]) --> ValidateRequest[Validate Request]
    ValidateRequest --> CheckAuth{Authenticated?}
    
    CheckAuth -->|No| Return401[Return 401 Unauthorized]
    CheckAuth -->|Yes| CheckApproval{Approval Required?}
    
    CheckApproval -->|Yes & Not Approved| CreateApprovalRequest[Create Approval Request]
    CheckApproval -->|No or Approved| ValidateVersion[Validate Target Version]
    
    CreateApprovalRequest --> Return202[Return 202 Accepted]
    
    ValidateVersion --> VersionExists{Version Exists?}
    VersionExists -->|No| Return400[Return 400 Bad Request]
    VersionExists -->|Yes| CheckDryRun{Dry Run Mode?}
    
    CheckDryRun -->|Yes| SimulateRollback[Simulate Rollback]
    CheckDryRun -->|No| CreatePreSnapshot[Create Pre-Rollback Snapshot]
    
    SimulateRollback --> ReturnDryRunResult[Return Dry Run Result]
    
    CreatePreSnapshot --> GetRollbackPlan[Generate Rollback Plan]
    GetRollbackPlan --> ExecuteRollback[Execute Rollback Operations]
    
    ExecuteRollback --> RollbackSuccess{Success?}
    RollbackSuccess -->|Yes| UpdateSchemaHistory[Update Schema History]
    RollbackSuccess -->|No| RestoreSnapshot[Restore from Snapshot]
    
    UpdateSchemaHistory --> AuditLog[Write Audit Log]
    RestoreSnapshot --> AuditLog
    
    AuditLog --> ReturnResult[Return Result]
    
    style APIRequest fill:#87CEEB
    style Return401 fill:#FFB6C1
    style Return400 fill:#FFB6C1
    style Return202 fill:#FFD700
    style ReturnResult fill:#90EE90
```

## 4. Snapshot Creation Flow

```mermaid
flowchart TD
    SnapshotRequest([Create Snapshot Request]) --> GenerateID[Generate Snapshot ID]
    GenerateID --> CreateDirectory[Create Snapshot Directory]
    
    CreateDirectory --> GetTables[Get All Tables List]
    GetTables --> FilterTables[Filter System Tables]
    
    FilterTables --> ForEachTable[For Each Table]
    ForEachTable --> CheckDBType{Database Type?}
    
    CheckDBType -->|H2| ExportCSV[Export to CSV]
    CheckDBType -->|MySQL| CreateBackupTable[Create Backup Table]
    
    ExportCSV --> SaveMetadata
    CreateBackupTable --> SaveMetadata[Save Table Metadata]
    
    SaveMetadata --> MoreTables{More Tables?}
    MoreTables -->|Yes| ForEachTable
    MoreTables -->|No| CreateManifest[Create Snapshot Manifest]
    
    CreateManifest --> CompressOption{Compression Enabled?}
    CompressOption -->|Yes| CompressSnapshot[Compress Snapshot Files]
    CompressOption -->|No| SaveComplete
    
    CompressSnapshot --> SaveComplete[Save Complete]
    SaveComplete --> ReturnSnapshotID([Return Snapshot ID])
    
    style SnapshotRequest fill:#87CEEB
    style ReturnSnapshotID fill:#90EE90
```

## 5. Rollback Execution Flow

```mermaid
flowchart TD
    RollbackStart([Rollback Execution Start]) --> LoadCurrentVersion[Load Current Version]
    LoadCurrentVersion --> LoadTargetVersion[Load Target Version]
    
    LoadTargetVersion --> CalculateVersions[Calculate Versions to Rollback]
    CalculateVersions --> LoadRollbackScripts[Load U*.sql Scripts]
    
    LoadRollbackScripts --> ScriptsExist{All Scripts Exist?}
    ScriptsExist -->|No| RollbackError[Error: Missing Scripts]
    ScriptsExist -->|Yes| DisableConstraints[Disable Foreign Key Constraints]
    
    DisableConstraints --> ExecuteRollbacks[Execute Rollback Scripts]
    ExecuteRollbacks --> RollbackType{Script Type?}
    
    RollbackType -->|DDL| ExecuteDDL[Execute DDL Rollback]
    RollbackType -->|DML| ExecuteDML[Execute DML Rollback]
    
    ExecuteDDL --> CheckDDLResult{Success?}
    ExecuteDML --> CheckDMLResult{Success?}
    
    CheckDDLResult -->|Yes| NextScript
    CheckDDLResult -->|No| DDLError[Log DDL Error]
    
    CheckDMLResult -->|Yes| NextScript
    CheckDMLResult -->|No| DMLError[Log DML Error]
    
    NextScript{More Scripts?} -->|Yes| ExecuteRollbacks
    NextScript -->|No| EnableConstraints[Enable Foreign Key Constraints]
    
    EnableConstraints --> UpdateHistory[Update Schema History]
    UpdateHistory --> VerifyState[Verify Database State]
    
    VerifyState --> StateValid{State Valid?}
    StateValid -->|Yes| RollbackSuccess([Rollback Successful])
    StateValid -->|No| PartialRollback([Partial Rollback - Manual Fix Needed])
    
    DDLError --> HandleError
    DMLError --> HandleError[Handle Error]
    HandleError --> RestoreFromSnapshot{Restore from Snapshot?}
    
    RestoreFromSnapshot -->|Yes| RestoreSnapshot[Restore Snapshot]
    RestoreFromSnapshot -->|No| PartialRollback
    
    style RollbackStart fill:#87CEEB
    style RollbackSuccess fill:#90EE90
    style RollbackError fill:#FFB6C1
    style PartialRollback fill:#FFD700
```

## 6. Database-Specific Operations Flow

```mermaid
flowchart TD
    DBOperation([Database Operation]) --> DetectDB[Detect Database Type]
    DetectDB --> CheckURL{Check Connection URL}
    
    CheckURL -->|Contains 'h2'| H2Operations[H2 Operations]
    CheckURL -->|Contains 'mysql'| MySQLOperations[MySQL Operations]
    
    H2Operations --> H2Snapshot{Snapshot Operation?}
    H2Snapshot -->|Create| H2CSV[Export to CSV Files]
    H2Snapshot -->|Restore| H2Import[Import from CSV Files]
    
    MySQLOperations --> MySQLSnapshot{Snapshot Operation?}
    MySQLSnapshot -->|Create| MySQLBackup[CREATE TABLE AS SELECT]
    MySQLSnapshot -->|Restore| MySQLRestore[RENAME TABLE Operations]
    
    H2CSV --> StoreMetadata[Store Metadata JSON]
    H2Import --> ReadMetadata[Read Metadata JSON]
    MySQLBackup --> StoreMetadata
    MySQLRestore --> ReadMetadata
    
    StoreMetadata --> OperationComplete([Operation Complete])
    ReadMetadata --> ApplyData[Apply Data]
    ApplyData --> OperationComplete
    
    style DBOperation fill:#87CEEB
    style OperationComplete fill:#90EE90
```

## 7. API Request Lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant RollbackManager
    participant SnapshotService
    participant Database
    participant AuditService
    
    Client->>Controller: POST /api/flyway/rollback/execute
    Controller->>Controller: Validate Request
    
    alt Authentication Failed
        Controller-->>Client: 401 Unauthorized
    else Authentication Success
        Controller->>RollbackManager: rollbackToVersion(targetVersion)
        RollbackManager->>Database: getCurrentVersion()
        Database-->>RollbackManager: currentVersion
        
        alt Snapshot Enabled
            RollbackManager->>SnapshotService: createSnapshot()
            SnapshotService->>Database: Export Tables
            Database-->>SnapshotService: Table Data
            SnapshotService-->>RollbackManager: snapshotId
        end
        
        RollbackManager->>Database: Execute Rollback Scripts
        
        alt Rollback Success
            Database-->>RollbackManager: Success
            RollbackManager->>Database: Update Schema History
            RollbackManager->>AuditService: Log Success
            RollbackManager-->>Controller: RollbackResult(success=true)
            Controller-->>Client: 200 OK
        else Rollback Failed
            Database-->>RollbackManager: Error
            RollbackManager->>SnapshotService: restoreSnapshot()
            RollbackManager->>AuditService: Log Failure
            RollbackManager-->>Controller: RollbackResult(success=false)
            Controller-->>Client: 500 Internal Server Error
        end
    end
```

## 8. Multi-Environment Configuration Flow

```mermaid
flowchart TD
    AppStart([Application Start]) --> CheckEnvVar{Check SPRING_PROFILES_ACTIVE}
    
    CheckEnvVar -->|Not Set| DefaultProfile[Use 'local' Profile]
    CheckEnvVar -->|Set| UseEnvProfile[Use Environment Profile]
    
    DefaultProfile --> LocalConfig[Load Local Configuration]
    UseEnvProfile --> CheckProfile{Which Profile?}
    
    CheckProfile -->|local| LocalConfig
    CheckProfile -->|mysql| MySQLConfig[Load MySQL Configuration]
    CheckProfile -->|test| TestConfig[Load Test Configuration]
    CheckProfile -->|production| ProdConfig[Load Production Configuration]
    
    LocalConfig --> H2Settings[H2 In-Memory Settings]
    MySQLConfig --> MySQLSettings[MySQL Connection Settings]
    TestConfig --> TestH2Settings[Test H2 Settings]
    ProdConfig --> ProdSettings[Production Settings]
    
    H2Settings --> ConfigComplete
    MySQLSettings --> ConfigComplete
    TestH2Settings --> ConfigComplete
    ProdSettings --> ConfigComplete[Configuration Complete]
    
    ConfigComplete --> InitializeApp[Initialize Application]
    
    style AppStart fill:#87CEEB
    style ConfigComplete fill:#90EE90
```

## 9. Error Handling and Recovery Flow

```mermaid
flowchart TD
    Error([Error Occurred]) --> ErrorType{Error Type?}
    
    ErrorType -->|Migration Failed| MigrationError[Migration Error Handler]
    ErrorType -->|Rollback Failed| RollbackError[Rollback Error Handler]
    ErrorType -->|Connection Lost| ConnectionError[Connection Error Handler]
    
    MigrationError --> CheckAutoRollback{Auto-Rollback Enabled?}
    CheckAutoRollback -->|Yes| ExecuteAutoRollback[Execute Auto Rollback]
    CheckAutoRollback -->|No| LogError[Log Error Details]
    
    RollbackError --> SnapshotAvailable{Snapshot Available?}
    SnapshotAvailable -->|Yes| RestoreSnapshot[Restore from Snapshot]
    SnapshotAvailable -->|No| ManualIntervention[Require Manual Intervention]
    
    ConnectionError --> RetryConnection[Retry Connection]
    RetryConnection --> RetrySuccess{Success?}
    RetrySuccess -->|Yes| ResumeOperation[Resume Operation]
    RetrySuccess -->|No| FailOperation[Fail Operation]
    
    ExecuteAutoRollback --> RollbackResult{Rollback Success?}
    RollbackResult -->|Yes| NotifySuccess[Notify Success]
    RollbackResult -->|No| NotifyFailure[Notify Failure]
    
    RestoreSnapshot --> RestoreResult{Restore Success?}
    RestoreResult -->|Yes| NotifyRestore[Notify Restore Complete]
    RestoreResult -->|No| CriticalError[Critical Error State]
    
    style Error fill:#FFB6C1
    style NotifySuccess fill:#90EE90
    style CriticalError fill:#FF6347
```

## 10. Complete System Architecture Flow

```mermaid
graph TB
    subgraph "Client Layer"
        WebUI[Web UI]
        CLI[Command Line]
        API[REST API Client]
    end
    
    subgraph "Spring Boot Application"
        subgraph "Web Layer"
            Controllers[REST Controllers]
            Security[Security Filter]
        end
        
        subgraph "Service Layer"
            RollbackManager[Rollback Manager]
            SnapshotService[Snapshot Service]
            AuditService[Audit Service]
        end
        
        subgraph "Data Layer"
            JPA[JPA Repositories]
            JDBC[JDBC Template]
            Flyway[Flyway Core]
        end
    end
    
    subgraph "Database Layer"
        H2[(H2 Database)]
        MySQL[(MySQL Database)]
    end
    
    subgraph "Storage Layer"
        FileSystem[File System Snapshots]
        CloudStorage[Cloud Storage]
    end
    
    WebUI --> Controllers
    CLI --> Controllers
    API --> Controllers
    
    Controllers --> Security
    Security --> RollbackManager
    Security --> JPA
    
    RollbackManager --> SnapshotService
    RollbackManager --> AuditService
    RollbackManager --> JDBC
    RollbackManager --> Flyway
    
    SnapshotService --> FileSystem
    SnapshotService --> CloudStorage
    
    JPA --> H2
    JPA --> MySQL
    JDBC --> H2
    JDBC --> MySQL
    Flyway --> H2
    Flyway --> MySQL
    
    style Controllers fill:#87CEEB
    style RollbackManager fill:#98FB98
    style H2 fill:#FFE4B5
    style MySQL fill:#FFE4B5
```

## Flow Summary

These diagrams illustrate:

1. **Startup Flow**: How the application initializes with different profiles
2. **Migration Flow**: How Flyway executes migrations with transaction support
3. **API Flow**: Complete REST API request handling for rollbacks
4. **Snapshot Flow**: How snapshots are created for different databases
5. **Rollback Flow**: Detailed rollback execution process
6. **Database Operations**: Database-specific handling
7. **Request Lifecycle**: Sequence of operations for API requests
8. **Configuration Flow**: Multi-environment configuration handling
9. **Error Handling**: Comprehensive error recovery mechanisms
10. **Architecture**: Overall system component interactions

Each flow shows decision points, error handling, and the complete path from request to response, making it easy to understand how the rollback framework operates in different scenarios.