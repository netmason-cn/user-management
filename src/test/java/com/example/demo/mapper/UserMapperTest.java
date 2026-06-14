package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;  // 用于清空表

    private User savedUser;

    @BeforeEach
    void setUp() {
        // 每次测试前清空 users 表，避免唯一索引冲突
        jdbcTemplate.execute("DELETE FROM users");

        // 插入测试数据
        User user = new User("张三", "zhangsan@example.com", 25);
        user.setPassword("hashed-password");
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        savedUser = user;
    }

    // ==================== selectByEmail ====================

    @Test
    @DisplayName("selectByEmail - 找到用户")
    void selectByEmail_ShouldReturnUser() {
        User result = userMapper.selectByEmail("zhangsan@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
    }

    @Test
    @DisplayName("selectByEmail - 未找到返回null")
    void selectByEmail_ShouldReturnNull_WhenNotFound() {
        User result = userMapper.selectByEmail("notfound@example.com");

        assertThat(result).isNull();
    }

    // ==================== selectByNameContaining ====================

    @Test
    @DisplayName("selectByNameContaining - 模糊查询匹配")
    void selectByNameContaining_ShouldReturnMatchingUsers() {
        User u1 = new User("张三丰", "zhangsanfeng@example.com", 80);
        u1.setPassword("hashed-zsf");
        u1.setRole("USER");
        u1.setCreatedAt(LocalDateTime.now());
        u1.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u1);

        User u2 = new User("李四", "lisi@example.com", 30);
        u2.setPassword("hashed-lisi");
        u2.setRole("USER");
        u2.setCreatedAt(LocalDateTime.now());
        u2.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u2);

        List<User> result = userMapper.selectByNameContaining("张");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getName)
                .containsExactlyInAnyOrder("张三", "张三丰");
    }

    @Test
    @DisplayName("selectByNameContaining - 无匹配返回空列表")
    void selectByNameContaining_ShouldReturnEmpty_WhenNoMatch() {
        List<User> result = userMapper.selectByNameContaining("王");

        assertThat(result).isEmpty();
    }

    // ==================== 基础 CRUD ====================

    @Test
    @DisplayName("insert - 创建用户并回填ID")
    void insert_ShouldSetIdAndTimestamps() {
        User newUser = new User("李四", "lisi@example.com", 30);
        LocalDateTime now = LocalDateTime.now();
        newUser.setPassword("hashed-lisi");
        newUser.setRole("USER");
        newUser.setCreatedAt(now);
        newUser.setUpdatedAt(now);

        userMapper.insert(newUser);

        assertThat(newUser.getId()).isNotNull();
        assertThat(newUser.getCreatedAt()).isNotNull();
        assertThat(newUser.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("selectById - 根据ID查询")
    void selectById_ShouldReturnUser() {
        User result = userMapper.selectById(savedUser.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("deleteById - 删除用户")
    void deleteById_ShouldRemoveUser() {
        userMapper.deleteById(savedUser.getId());

        User result = userMapper.selectById(savedUser.getId());
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("countById - 检查用户存在")
    void countById_ShouldReturnCount() {
        assertThat(userMapper.countById(savedUser.getId())).isEqualTo(1);
        assertThat(userMapper.countById(999L)).isEqualTo(0);
    }
}
