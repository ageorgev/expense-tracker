package com.agv.expenses;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExpanalysisApplication {

	public static void main(String[] args) {
		System.out.println(System.getenv("JASYPT_ENCRYPTOR_PASSWORD"));
		SpringApplication.run(ExpanalysisApplication.class, args);
	}

}
