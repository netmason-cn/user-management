package com.example.demo.service;

import com.example.demo.dto.CreateUserRequest;
import com.example.demo.dto.UpdateUserRequest;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user1;
    private User user2;
    private final BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        user1 = new User("张三", "zhangsan@example.com", 25);
        user1.setId(1L);
        user1.setPassword(realEncoder.encode("password-1"));
        user1.setRole("USER");

        user2 = new User("李四", "lisi@example.com", 30);
        user2.setId(2L);
        user2.setPassword(realEncoder.encode("password-2"));
        user2.setRole("USER");
    }

    // ==================== findAll ====================

    @Test
    @DisplayName("查询所有用户 - 成功")
    void findAll_ShouldReturnAllUsers() {
        when(userMapper.selectAll()).thenReturn(Arrays.asList(user1, user2));

        List<User> result = userService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("张三");
        assertThat(result.get(1).getName()).isEqualTo("李四");
        verify(userMapper, times(1)).selectAll();
    }

    @Test
    @DisplayName("查询所有用户 - 空列表")
    void findAll_ShouldReturnEmptyList() {
        when(userMapper.selectAll()).thenReturn(List.of());

        List<User> result = userService.findAll();

        assertThat(result).isEmpty();
        verify(userMapper, times(1)).selectAll();
    }

    // ==================== findById ====================

    @Test
    @DisplayName("根据ID查询用户 - 成功")
    void findById_ShouldReturnUser() {
        when(userMapper.selectById(1L)).thenReturn(user1);

        User result = userService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        verify(userMapper, times(1)).selectById(1L);
    }

    @Test
    @DisplayName("根据ID查询用户 - 用户不存在抛出异常")
    void findById_ShouldThrowException_WhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在: 999");
        verify(userMapper, times(1)).selectById(999L);
    }

    // ==================== create ====================

    @Test
    @DisplayName("创建用户 - 成功")
    void create_ShouldSaveAndReturnUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("王五");
        request.setEmail("wangwu@example.com");
        request.setAge(28);
        request.setPassword("123456");
        request.setRole("USER");

        when(userMapper.selectByEmail("wangwu@example.com")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("hashed-123456");

        User result = userService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull(); // insert 后 ID 由 DB 回填，mock 下为 null
        assertThat(result.getName()).isEqualTo("王五");
        assertThat(result.getEmail()).isEqualTo("wangwu@example.com");
        assertThat(result.getAge()).isEqualTo(28);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(userMapper, times(1)).selectByEmail("wangwu@example.com");
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    @DisplayName("创建用户 - 邮箱已存在抛出异常")
    void create_ShouldThrowException_WhenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("张三");
        request.setEmail("zhangsan@example.com");
        request.setAge(25);

        when(userMapper.selectByEmail("zhangsan@example.com")).thenReturn(user1);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("邮箱已被注册: zhangsan@example.com");
        verify(userMapper, times(1)).selectByEmail("zhangsan@example.com");
        verify(userMapper, never()).insert(any(User.class));
    }

    // ==================== update ====================

    @Test
    @DisplayName("更新用户 - 全部字段更新成功")
    void update_ShouldUpdateAllFields() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三改");
        request.setEmail("zhangsan_new@example.com");
        request.setAge(26);

        when(userMapper.selectById(1L)).thenReturn(user1);
        when(userMapper.selectByEmail("zhangsan_new@example.com")).thenReturn(null);

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("张三改");
        assertThat(result.getEmail()).isEqualTo("zhangsan_new@example.com");
        assertThat(result.getAge()).isEqualTo(26);
        verify(userMapper, times(1)).selectById(1L);
        verify(userMapper, times(1)).update(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 部分字段更新")
    void update_ShouldUpdatePartialFields() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三更新");
        // email 和 age 不设置，应该保持不变

        when(userMapper.selectById(1L)).thenReturn(user1);

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("张三更新");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getAge()).isEqualTo(25);
        verify(userMapper, times(1)).update(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 用户不存在抛出异常")
    void update_ShouldThrowException_WhenUserNotFound() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("不存在的用户");

        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.update(999L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在: 999");
        verify(userMapper, times(1)).selectById(999L);
        verify(userMapper, never()).update(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 邮箱被其他用户占用抛出异常")
    void update_ShouldThrowException_WhenEmailUsedByOtherUser() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("lisi@example.com");

        when(userMapper.selectById(1L)).thenReturn(user1);
        when(userMapper.selectByEmail("lisi@example.com")).thenReturn(user2);

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("邮箱已被其他用户使用: lisi@example.com");
        verify(userMapper, times(1)).selectById(1L);
        verify(userMapper, times(1)).selectByEmail("lisi@example.com");
        verify(userMapper, never()).update(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 邮箱不变允许更新")
    void update_ShouldAllowSameEmail() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("张三新名");
        request.setEmail("zhangsan@example.com"); // 和原来一样的邮箱

        when(userMapper.selectById(1L)).thenReturn(user1);
        when(userMapper.selectByEmail("zhangsan@example.com")).thenReturn(user1);

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("张三新名");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        verify(userMapper, times(1)).update(any(User.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("删除用户 - 成功")
    void delete_ShouldDeleteUser() {
        when(userMapper.countById(1L)).thenReturn(1);
        when(userMapper.deleteById(1L)).thenReturn(1);

        userService.delete(1L);

        verify(userMapper, times(1)).countById(1L);
        verify(userMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除用户 - 用户不存在抛出异常")
    void delete_ShouldThrowException_WhenUserNotFound() {
        when(userMapper.countById(999L)).thenReturn(0);

        assertThatThrownBy(() -> userService.delete(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在: 999");
        verify(userMapper, times(1)).countById(999L);
        verify(userMapper, never()).deleteById(anyLong());
    }
}
