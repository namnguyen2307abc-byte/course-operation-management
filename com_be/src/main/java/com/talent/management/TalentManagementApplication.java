package com.talent.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class TalentManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalentManagementApplication.class, args);
    }

}
