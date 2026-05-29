package com.weiki.prismbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiki.prismbackend.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
