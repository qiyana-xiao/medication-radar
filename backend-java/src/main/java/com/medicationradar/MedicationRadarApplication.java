package com.medicationradar;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@MapperScan("com.medicationradar.mapper")
@SpringBootApplication
public class MedicationRadarApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicationRadarApplication.class, args);
    }
}
