package com.campus.reservation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.campus.reservation.mapper")
public class CampusReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusReservationApplication.class, args);
    }
}
