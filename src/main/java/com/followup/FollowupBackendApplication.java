package com.followup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FollowupBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FollowupBackendApplication.class, args);
    }

}
