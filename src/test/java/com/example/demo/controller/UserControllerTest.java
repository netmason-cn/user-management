package com.example.demo.controller;

import com.example.demo.dto.CreateUserRequest;
import com.example.demo.dto.UpdateUserRequest;
import com.example.demo.entity.User;
import com.example.demo.service.JwtTokenProvider;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserDetailsService userDetailsService;  // JWT 过滤器需要

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String authToken;  // 在 @BeforeEach 中初始化
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        // 生成真实 JWT token，所有请求将通过 Spring Security 认证
        authToken = "Bearer " + jwtTokenProvider.generateToken(99L, "test-admin@example.com", "ADMIN");

        // Mock UserDetailsService，让 JWT 过滤器认证通过
        org.springframework.security.core.userdetails.UserDetails mockUser =
                org.springframework.security.core.userdetails.User
                        .withUsername("99")
                        .password("password")
                        .authorities("ROLE_ADMIN")
                        .build();
        when(userDetailsService.loadUserByUsername("99")).thenReturn(mockUser);

        user1 = new User("张三", "zhangsan@example.com", 25);
        user1.setId(1L);

        user2 = new User("李四", "lisi@example.com", 30);
        user2.setId(2L);
    }

    // ==================== 请求封装（自动带 JWT token）====================

    private MockHttpServletRequestBuilder authGet(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", authToken);
    }

    private MockHttpServletRequestBuilder authPost(String url, String jsonBody) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private MockHttpServletRequestBuilder authPut(String url, String jsonBody) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private MockHttpServletRequestBuilder authDelete(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", authToken);
    }

    // ==================== GET /api/users ====================

    @Test
    @DisplayName("GET /api/users - 返回用户列表")
    void findAll_ShouldReturnUserList() throws Exception {
        List<User> users = Arrays.asList(user1, user2);
        when(userService.findAll()).thenReturn(users);

        mockMvc.perform(authGet("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("张三"))
                .andExpect(jsonPath("$.data[1].name").value("李四"));

        verify(userService, times(1)).findAll();
    }

    @Test
    @DisplayName("GET /api/users - 空列表")
    void findAll_ShouldReturnEmptyList() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(authGet("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(userService, times(1)).findAll();
    }

    // ==================== GET /api/users/{id} ====================

    @Test
    @DisplayName("GET /api/users/{id} - 返回单个用户")
    void findById_ShouldReturnUser() throws Exception {
        when(userService.findById(1L)).thenReturn(user1);

        mockMvc.perform(authGet("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.email").value("zhangsan@example.com"))
                .andExpect(jsonPath("$.data.age").value(25));

        verify(userService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /api/users/{id} - 用户不存在返回错误")
    void findById_ShouldReturnError_WhenUserNotFound() throws Exception {
        when(userService.findById(999L)).thenThrow(new RuntimeException("用户不存在: 999"));

        mockMvc.perform(authGet("/api/users/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在: 999"));

        verify(userService, times(1)).findById(999L);
    }

    // ==================== POST /api/users ====================

    @Test
    @DisplayName("POST /api/users - 创建用户成功")
    void create_ShouldCreateUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("王五");
        request.setEmail("wangwu@example.com");
        request.setAge(28);
        request.setPassword("123456");
        request.setRole("USER");

        User newUser = new User("王五", "wangwu@example.com", 28);
        newUser.setId(3L);

        when(userService.create(any(CreateUserRequest.class))).thenReturn(newUser);

        mockMvc.perform(authPost("/api/users", objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("创建成功"))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.name").value("王五"));

        verify(userService, times(1)).create(any(CreateUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/users - 缺少必填字段返回校验错误")
    void create_ShouldReturnValidationError_WhenFieldsMissing() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("");
        request.setEmail("not-an-email");
        request.setAge(0);
        request.setPassword("123");

        mockMvc.perform(authPost("/api/users", objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.data.name").value("姓名不能为空"))
                .andExpect(jsonPath("$.data.email").value("邮箱格式不正确"))
                .andExpect(jsonPath("$.data.age").value("年龄必须大于0"));

        verify(userService, never()).create(any(CreateUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/users - 邮箱已存在返回错误")
    void create_ShouldReturnError_WhenEmailExists() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("张三");
        request.setEmail("zhangsan@example.com");
        request.setAge(25);
        request.setPassword("123456");

        when(userService.create(any(CreateUserRequest.class)))
                .thenThrow(new RuntimeException("邮箱已被注册: zhangsan@example.com"));

        mockMvc.perform(authPost("/api/users", objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱已被注册: zhangsan@example.com"));

        verify(userService, times(1)).create(any(CreateUserRequest.class));
    }

    // ==================== PUT /api/users/{id} ====================

    @Test
    @DisplayName("PUT /api/users/{id} - 更新用户成功")
    void update_ShouldUpdateUser() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三改");
        request.setAge(26);

        User updatedUser = new User("张三改", "zhangsan@example.com", 26);
        updatedUser.setId(1L);

        when(userService.update(eq(1L), any(UpdateUserRequest.class))).thenReturn(updatedUser);

        mockMvc.perform(authPut("/api/users/1", objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.name").value("张三改"))
                .andExpect(jsonPath("$.data.age").value(26));

        verify(userService, times(1)).update(eq(1L), any(UpdateUserRequest.class));
    }

    @Test
    @DisplayName("PUT /api/users/{id} - 用户不存在返回错误")
    void update_ShouldReturnError_WhenUserNotFound() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("不存在");
        request.setAge(25);

        when(userService.update(eq(999L), any(UpdateUserRequest.class)))
                .thenThrow(new RuntimeException("用户不存在: 999"));

        mockMvc.perform(authPut("/api/users/999", objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在: 999"));

        verify(userService, times(1)).update(eq(999L), any(UpdateUserRequest.class));
    }

    // ==================== DELETE /api/users/{id} ====================

    @Test
    @DisplayName("DELETE /api/users/{id} - 删除用户成功")
    void delete_ShouldDeleteUser() throws Exception {
        doNothing().when(userService).delete(1L);

        mockMvc.perform(authDelete("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("删除成功"));

        verify(userService, times(1)).delete(1L);
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - 用户不存在返回错误")
    void delete_ShouldReturnError_WhenUserNotFound() throws Exception {
        doThrow(new RuntimeException("用户不存在: 999")).when(userService).delete(999L);

        mockMvc.perform(authDelete("/api/users/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在: 999"));

        verify(userService, times(1)).delete(999L);
    }
}
