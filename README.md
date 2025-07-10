# Spring Boot Flyway Example with Rollback Support

This project demonstrates Spring Boot integration with Flyway including a comprehensive rollback framework.

## Features

- Spring Boot 3.2.0 with Java 17
- Flyway 10.0.0 for database migrations
- H2 database for local development (default)
- MySQL support for production
- Comprehensive rollback framework
- REST API for rollback operations
- Snapshot management
- Audit logging
- Multi-profile support

## Requirements

- Java 17 or higher
- Maven 3.x
- MySQL 8.x (optional, for MySQL profile)
- Docker (optional, for containerized MySQL)

## Quick Start

### 1. Clone the repository
```bash
git clone https://github.com/callicoder/spring-boot-flyway-example.git
cd spring-boot-flyway-example