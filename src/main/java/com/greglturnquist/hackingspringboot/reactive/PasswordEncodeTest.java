package com.greglturnquist.hackingspringboot.reactive;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncodeTest {
	public static void main(String... args) {
		String cryptedPassword = new BCryptPasswordEncoder().encode("password");
		System.out.println(cryptedPassword);
	}
}
