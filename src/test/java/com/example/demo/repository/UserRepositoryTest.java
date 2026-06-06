package com.example.demo.repository;

import com.example.demo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        // 清理并插入测试数据
        userRepository.deleteAll();
        User user = new User("张三", "zhangsan@example.com", 25);
        savedUser = userRepository.save(user);
    }

    // ==================== findByEmail ====================

    @Test
    @DisplayName("findByEmail - 找到用户")
    void findByEmail_ShouldReturnUser() {
        Optional<User> result = userRepository.findByEmail("zhangsan@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("张三");
        assertThat(result.get().getEmail()).isEqualTo("zhangsan@example.com");
    }

    @Test
    @DisplayName("findByEmail - 未找到返回空")
    void findByEmail_ShouldReturnEmpty_WhenNotFound() {
        Optional<User> result = userRepository.findByEmail("notfound@example.com");

        assertThat(result).isEmpty();
    }

    // ==================== findByNameContaining ====================

    @Test
    @DisplayName("findByNameContaining - 模糊查询匹配")
    void findByNameContaining_ShouldReturnMatchingUsers() {
        userRepository.save(new User("张三丰", "zhangsanfeng@example.com", 80));
        userRepository.save(new User("李四", "lisi@example.com", 30));

        List<User> result = userRepository.findByNameContaining("张");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getName)
                .containsExactlyInAnyOrder("张三", "张三丰");
    }

    @Test
    @DisplayName("findByNameContaining - 无匹配返回空列表")
    void findByNameContaining_ShouldReturnEmpty_WhenNoMatch() {
        List<User> result = userRepository.findByNameContaining("王");

        assertThat(result).isEmpty();
    }

    // ==================== 基础 CRUD ====================

    @Test
    @DisplayName("save - 创建用户自动填充时间戳")
    void save_ShouldSetTimestamps() {
        User newUser = new User("李四", "lisi@example.com", 30);
        User saved = userRepository.save(newUser);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("findById - 根据ID查询")
    void findById_ShouldReturnUser() {
        Optional<User> result = userRepository.findById(savedUser.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("deleteById - 删除用户")
    void deleteById_ShouldRemoveUser() {
        userRepository.deleteById(savedUser.getId());

        Optional<User> result = userRepository.findById(savedUser.getId());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsById - 检查用户存在")
    void existsById_ShouldReturnTrue_WhenUserExists() {
        assertThat(userRepository.existsById(savedUser.getId())).isTrue();
        assertThat(userRepository.existsById(999L)).isFalse();
    }
}
