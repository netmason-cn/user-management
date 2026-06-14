package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("POST /api/auth/register - 注册成功")
    void register_ShouldSucceed() throws Exception {
        when(userMapper.selectByEmail("new@example.com")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        String body = """
                {"name":"新用户","email":"new@example.com","password":"123456","age":20}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/auth/register - 邮箱已存在返回400")
    void register_ShouldReturn400_WhenEmailExists() throws Exception {
        User existing = new User("张三", "exists@example.com", 25);
        when(userMapper.selectByEmail("exists@example.com")).thenReturn(existing);

        String body = """
                {"name":"张三","email":"exists@example.com","password":"123456"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login - 登录成功返回token")
    void login_ShouldReturnToken() throws Exception {
        User user = new User("张三", "zhangsan@example.com", 25);
        user.setId(1L);
        user.setPassword(encoder.encode("123456"));
        user.setRole("USER");

        when(userMapper.selectByEmail("zhangsan@example.com")).thenReturn(user);
        when(jwtTokenProvider.generateToken(1L, "zhangsan@example.com", "USER")).thenReturn("mock-jwt-token");

        String body = """
                {"email":"zhangsan@example.com","password":"123456"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 密码错误返回401")
    void login_ShouldReturn401_WhenPasswordWrong() throws Exception {
        User user = new User("张三", "zhangsan@example.com", 25);
        user.setPassword(encoder.encode("correct-password"));

        when(userMapper.selectByEmail("zhangsan@example.com")).thenReturn(user);

        String body = """
                {"email":"zhangsan@example.com","password":"wrong-password"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login - 用户不存在返回401")
    void login_ShouldReturn401_WhenUserNotFound() throws Exception {
        when(userMapper.selectByEmail("notfound@example.com")).thenReturn(null);

        String body = """
                {"email":"notfound@example.com","password":"123456"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
