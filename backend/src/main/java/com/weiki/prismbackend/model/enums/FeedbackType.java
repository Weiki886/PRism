package com.weiki.prismbackend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum FeedbackType {

    FALSE_POSITIVE("FALSE_POSITIVE", "误报"),
    CONFIRMED("CONFIRMED", "确认");

    @EnumValue
    @JsonValue
    private final String value;

    private final String desc;

    FeedbackType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
