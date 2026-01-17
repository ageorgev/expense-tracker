package com.util.encryptor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.util.encryptor.util.Utilities;

@SpringBootApplication
public class EncryptorApplication {

	public static void main(String[] args) {
		String myPassword="I am not giving my password to anyone!";
		System.out.println("ENC(" + Utilities.encrypt(myPassword) + ")");
		//SpringApplication.run(EncryptorApplication.class, args);
	}

}
