package com.agv.expenses;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExpanalysisApplicationTests {

	@Test
	void contextLoads() {
		String input = "Nov 05, 2025"; 
		// 1. Trim the input to remove hidden PDF artifacts
		input = input.trim(); 

		// 2. Adjust regex to handle potential multiple spaces or hidden tabs
		String regex = "^[A-Z][a-z]{2}\\s+\\d{2},\\s+\\d{4}$";

		assertTrue (input.matches(regex));
	}

}
