package com.example.demo.service;

import com.example.demo.dto.CreateUserRequest;
import com.example.demo.dto.UpdateUserRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = new User("张三", "zhangsan@example.com", 25);
        user1.setId(1L);

        user2 = new User("李四", "lisi@example.com", 30);
        user2.setId(2L);
    }

    // ==================== findAll ====================

    @Test
    @DisplayName("查询所有用户 - 成功")
    void findAll_ShouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2));

        List<User> result = userService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("张三");
        assertThat(result.get(1).getName()).isEqualTo("李四");
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("查询所有用户 - 空列表")
    void findAll_ShouldReturnEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<User> result = userService.findAll();

        assertThat(result).isEmpty();
        verify(userRepository, times(1)).findAll();
    }

    // ==================== findById ====================

    @Test
    @DisplayName("根据ID查询用户 - 成功")
    void findById_ShouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        User result = userService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("根据ID查询用户 - 用户不存在抛出异常")
    void findById_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在: 999");
        verify(userRepository, times(1)).findById(999L);
    }

    // ==================== create ====================

    @Test
    @DisplayName("创建用户 - 成功")
    void create_ShouldSaveAndReturnUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("王五");
        request.setEmail("wangwu@example.com");
        request.setAge(28);

        when(userRepository.findByEmail("wangwu@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(3L);
            return u;
        });

        User result = userService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getName()).isEqualTo("王五");
        assertThat(result.getEmail()).isEqualTo("wangwu@example.com");
        assertThat(result.getAge()).isEqualTo(28);
        verify(userRepository, times(1)).findByEmail("wangwu@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("创建用户 - 邮箱已存在抛出异常")
    void create_ShouldThrowException_WhenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("张三");
        request.setEmail("zhangsan@example.com");
        request.setAge(25);

        when(userRepository.findByEmail("zhangsan@example.com")).thenReturn(Optional.of(user1));

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("邮箱已被注册: zhangsan@example.com");
        verify(userRepository, times(1)).findByEmail("zhangsan@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== update ====================

    @Test
    @DisplayName("更新用户 - 全部字段更新成功")
    void update_ShouldUpdateAllFields() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三改");
        request.setEmail("zhangsan_new@example.com");
        request.setAge(26);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findByEmail("zhangsan_new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user1);

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("张三改");
        assertThat(result.getEmail()).isEqualTo("zhangsan_new@example.com");
        assertThat(result.getAge()).isEqualTo(26);
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 部分字段更新")
    void update_ShouldUpdatePartialFields() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三更新");
        // email 和 age 不设置，应该保持不变

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenReturn(user1);

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("张三更新");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getAge()).isEqualTo(25);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 用户不存在抛出异常")
    void update_ShouldThrowException_WhenUserNotFound() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("不存在的用户");

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(999L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在: 999");
        verify(userRepository, times(1)).findById(999L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 邮箱被其他用户占用抛出异常")
    void update_ShouldThrowException_WhenEmailUsedByOtherUser() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("lisi@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findByEmail("lisi@example.com")).thenReturn(Optional.of(user2));

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("邮箱已被其他用户使用: lisi@example.com");
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByEmail("lisi@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 邮箱不变允许更新")
    void update_ShouldAllowSameEmail() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三新名");
        request.setEmail("zhangsan@example.com"); // 和原来一样的邮箱

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findByEmail("zhangsan@example.com")).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenReturn(user1);

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("张三新名");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("删除用户 - 成功")
    void delete_ShouldDeleteUser() {
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);

        userService.delete(1L);

        verify(userRepository, times(1)).existsById(1L);
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除用户 - 用户不存在抛出异常")
    void delete_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> userService.delete(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在: 999");
        verify(userRepository, times(1)).existsById(999L);
        verify(userRepository, never()).deleteById(anyLong());
    }
}
