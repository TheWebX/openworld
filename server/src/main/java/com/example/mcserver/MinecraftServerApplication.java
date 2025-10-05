package com.example.mcserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MinecraftServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerApplication.class, args);
    }
}
