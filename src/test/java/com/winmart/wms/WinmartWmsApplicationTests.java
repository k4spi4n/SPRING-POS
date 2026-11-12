package com.winmart.wms;

import com.winmart.wms.config.DataInitializer;
import com.winmart.wms.repository.UserRepository;
import com.winmart.wms.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "DB_URL=jdbc:h2:mem:deployment_test;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "DB_USERNAME=sa",
        "DB_PASSWORD=test-password",
        "JWT_SECRET=deployment-test-secret-with-at-least-32-bytes",
        "CORS_ALLOWED_ORIGINS=https://pos.example.com",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.path=target/test-logs"
})
@ActiveProfiles("mysql")
@AutoConfigureMockMvc
class WinmartWmsApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploymentDoesNotLoadDemoDataOrH2Console() throws Exception {
        assertThat(context.getBeansOfType(DataInitializer.class)).isEmpty();
        assertThat(context.containsBean("h2Console")).isFalse();
        assertThat(userRepository.count()).isZero();
        assertThat(warehouseRepository.count()).isZero();
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void deploymentCorsOnlyAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/products")
                        .header("Origin", "https://pos.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://pos.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mockMvc.perform(options("/api/v1/products")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
