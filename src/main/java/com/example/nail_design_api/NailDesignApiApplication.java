package com.example.nail_design_api;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;

@SpringBootApplication(
		exclude = {
				SecurityAutoConfiguration.class,
				ReactiveSecurityAutoConfiguration.class
		}
)
public class NailDesignApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(NailDesignApiApplication.class, args);
	}
}