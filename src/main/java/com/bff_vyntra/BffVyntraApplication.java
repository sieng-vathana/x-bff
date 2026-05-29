package com.bff_vyntra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class
})
public class BffVyntraApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffVyntraApplication.class, args);
	}
}
