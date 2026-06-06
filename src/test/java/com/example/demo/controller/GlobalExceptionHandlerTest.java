package com.example.demo.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== MethodArgumentNotValidException ====================

    @Test
    @DisplayName("校验失败 - 返回 400 且 data 包含字段级错误信息")
    void handleValidation_ShouldReturnFieldErrors() throws Exception {
        String body = "{\"name\":\"\",\"age\":0}";

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.name").value("姓名不能为空"))
                .andExpect(jsonPath("$.data.age").value("必须大于0"));
    }

    @Test
    @DisplayName("校验失败 - 多字段同时校验失败时全部列出")
    void handleValidation_ShouldListAllErrors() throws Exception {
        String body = "{\"name\":null,\"age\":null}";

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").isNotEmpty())
                .andExpect(jsonPath("$.data.age").isNotEmpty());
    }

    // ==================== RuntimeException ====================

    @Test
    @DisplayName("RuntimeException - 返回 400 并透传异常消息")
    void handleRuntime_ShouldReturn400WithMessage() throws Exception {
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("业务异常"));
    }

    @Test
    @DisplayName("RuntimeException - 不同异常消息正确透传")
    void handleRuntime_ShouldPassThroughDifferentMessages() throws Exception {
        mockMvc.perform(get("/test/runtime-custom?msg=用户不存在: 999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在: 999"));
    }

    // ==================== 兜底 Exception ====================

    @Test
    @DisplayName("Exception - 返回 500 并隐藏内部错误详情")
    void handleAll_ShouldReturn500AndHideDetails() throws Exception {
        mockMvc.perform(get("/test/exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    // ==============================================================
    // 内部测试用的 Controller 和 DTO
    // ==============================================================

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/runtime")
        void throwRuntime() {
            throw new RuntimeException("业务异常");
        }

        @GetMapping("/runtime-custom")
        void throwRuntimeCustom(@RequestParam String msg) {
            throw new RuntimeException(msg);
        }

        @GetMapping("/exception")
        void throwException() throws Exception {
            throw new Exception("内部错误");
        }

        @PostMapping("/validation")
        void validate(@Valid @RequestBody TestRequest req) {
        }
    }

    static class TestRequest {
        @NotBlank(message = "姓名不能为空")
        private String name;

        @NotNull(message = "年龄不能为空")
        @Min(value = 1, message = "必须大于0")
        private Integer age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }
}
