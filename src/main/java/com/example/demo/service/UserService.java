package com.example.demo.service;

import com.example.demo.dto.CreateUserRequest;
import com.example.demo.dto.UpdateUserRequest;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userMapper.selectAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "user", key = "#id")
    public User findById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + id);
        }
        return user;
    }

    @Transactional
    @CacheEvict(value = "user", allEntries = true)
    public User create(CreateUserRequest request) {
        // 检查邮箱是否已存在
        if (userMapper.selectByEmail(request.getEmail()) != null) {
            throw new RuntimeException("邮箱已被注册: " + request.getEmail());
        }

        User user = new User(request.getName(), request.getEmail(), request.getAge());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        String role = request.getRole() != null ? request.getRole() : "USER";
        user.setRole(role);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }

    @Transactional
    @CachePut(value = "user", key = "#id")
    public User update(Long id, UpdateUserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + id);
        }

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            // 检查新邮箱是否已被其他用户使用
            User existing = userMapper.selectByEmail(request.getEmail());
            if (existing != null && !existing.getId().equals(id)) {
                throw new RuntimeException("邮箱已被其他用户使用: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
        return user;
    }

    @Transactional
    @CacheEvict(value = "user", key = "#id")
    public void delete(Long id) {
        if (userMapper.countById(id) == 0) {
            throw new RuntimeException("用户不存在: " + id);
        }
        userMapper.deleteById(id);
    }
}
