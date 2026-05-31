package com.weiki.prismbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    @JsonIgnore
    private String password;

    private String role;

    /** GitHub OAuth 绑定信息 */
    private Long githubId;

    private String githubLogin;

    @JsonIgnore
    private String githubToken;

    private String avatarUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记：0-未删除，1-已删除。
     */
    @JsonIgnore
    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
