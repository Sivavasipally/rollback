package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
    "spring.jpa.hibernate.ddl-auto=none",
    "flyway.rollback.enabled=false",
    "logging.level.org.springframework=DEBUG"
})
public class BasicConfigurationTest {

    @Test
    public void contextLoads() {
        System.out.println("✅ Basic Spring context loaded successfully!");
    }
}
