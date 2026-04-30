package com.argus.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.argus.rag.**.mapper")
public class ArgusBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArgusBackendApplication.class, args);
    }
}
