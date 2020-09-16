package com.greglturnquist.hackingspringboot.reactive;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {
	static final String USER = "USER";
	static final String INVENTORY = "INVENTORY";
	
	static String role(String auth) {
		return "ROLE_" + auth;
	}
	
	@Bean
	public PasswordEncoder passwordEncoder( ) {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public ReactiveUserDetailsService userDetailsService(UserRepository repository) {
		return username -> repository.findByName(username)
			.map(user -> User
				.withUsername(user.getName())
				.password(user.getPassword())
				.authorities(user.getRoles().toArray(new String[0]))
				.build());
	}
	
	@Bean
	CommandLineRunner userloader(MongoOperations operations) {
		return args -> {
			operations.save(new com.greglturnquist.hackingspringboot.reactive.User(
					"greg", "$2a$10$GcwdeDauPhOycNF7nliuRu5KisfNeln0kG4v6ZrMGuxTzJXSp.yle", Arrays.asList(role(USER))));
			
			operations.save(new com.greglturnquist.hackingspringboot.reactive.User(
					"manager", "$2a$10$GcwdeDauPhOycNF7nliuRu5KisfNeln0kG4v6ZrMGuxTzJXSp.yle", Arrays.asList(role(USER), role(INVENTORY))));
		};
	}
	
	@Bean
	SecurityWebFilterChain myCustomSecurityPolicy(ServerHttpSecurity http) {
		return http
			.authorizeExchange(exchanges -> exchanges
				.pathMatchers(HttpMethod.POST, "/item").hasRole(INVENTORY)
				.pathMatchers(HttpMethod.DELETE, "/item/**").hasRole(INVENTORY)
				.anyExchange().authenticated()
				.and()
				.httpBasic()
				.and()
				.formLogin())
			.csrf().disable()
			.build();
	}
}
