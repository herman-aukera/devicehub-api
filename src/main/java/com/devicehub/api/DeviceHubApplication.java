package com.devicehub.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.SpringVersion;

@SpringBootApplication
@Slf4j
public class DeviceHubApplication {

    public static void main(String[] args) {
        log.info("DeviceHub API starting with Java {} and Spring Boot {}",
                System.getProperty("java.version"),
                SpringVersion.getVersion());
        SpringApplication.run(DeviceHubApplication.class, args);
        log.info("DeviceHub API started successfully");
    }
}
