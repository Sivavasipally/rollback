package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class SimpleApplicationTest {

    @Test
    public void contextLoads() {
        // This test will pass if the application context loads successfully
    }
}
