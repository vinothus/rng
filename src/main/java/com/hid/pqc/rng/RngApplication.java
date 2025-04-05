package com.hid.pqc.rng;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//@EnableScheduling
public class RngApplication {

	public static void main(String[] args) {
		SpringApplication.run(RngApplication.class, args);
	}

}
