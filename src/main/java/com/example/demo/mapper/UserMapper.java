package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {

    int insert(User user);

    int update(User user);

    int deleteById(Long id);

    User selectById(Long id);

    List<User> selectAll();

    User selectByEmail(String email);

    List<User> selectByNameContaining(String keyword);

    int countById(Long id);
}
