package com.winmart.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winmart.wms.entity.User;
import com.winmart.wms.entity.enums.RoleName;
import com.winmart.wms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void registrationWithoutTokenIsUnauthorized(RoleName role) throws Exception {
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(role)))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @ParameterizedTest
    @CsvSource({"staff, ROLE_ADMIN", "manager, ROLE_ADMIN", "staff, ROLE_STAFF", "manager, ROLE_STAFF"})
    void nonAdminCannotRegisterAccounts(String username, RoleName role) throws Exception {
        String token = login(username, username + "123");
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(role)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void adminCanRegisterEachValidRole(RoleName role) throws Exception {
        String token = login("admin", "admin123");
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.role").value(role.name()));

        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
        User saved = userRepository.findByUsername("newuser").orElseThrow();
        assertThat(saved.getRole().getName()).isEqualTo(role);
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = login("staff", "staff123");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("staff"));
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private String registration(RoleName role) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "username", "newuser",
                "password", "password123",
                "fullName", "New User",
                "role", role));
    }
}
