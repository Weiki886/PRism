package com.weiki.prismbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.weiki.prismbackend.mapper")
public class PrismBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrismBackendApplication.class, args);
    }

}
