package com.kisia.staybooking;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:mysql-profile-check;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MysqlProfileConfigurationTests {
    @Autowired private DataSource dataSource;

    @Test
    void profileAcceptsExternalDatasourceSettings() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertFalse(connection.isClosed());
        }
    }
}
