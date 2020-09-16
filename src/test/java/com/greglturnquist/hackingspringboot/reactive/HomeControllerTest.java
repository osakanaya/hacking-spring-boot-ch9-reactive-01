/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.greglturnquist.hackingspringboot.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import reactor.test.StepVerifier;

@SpringBootTest
@AutoConfigureWebTestClient
public class HomeControllerTest {

	@Autowired
	private WebTestClient webTestClient;
	
	@Autowired
	private ItemRepository repository;

	@Test
	void verifyLoginPageBlocksAccess() {
		this.webTestClient.get().uri("/") //
				.exchange() //
				.expectStatus().isUnauthorized();
	}

	@Test
	@WithMockUser(username = "ada")
	void verifyLoginPageWorks() {
		this.webTestClient.get().uri("/") //
				.exchange() //
				.expectStatus().isOk();
	}
	
	@Test
	@WithMockUser(username = "alice", roles = { "SOME_OTHER_ROLE" })
	void addingInventoryWithoutProperRoleFails() {
		this.webTestClient.post().uri("/item")
			.exchange()
			.expectStatus().isForbidden();
	}
	
	@Test
	@WithMockUser(username = "bob", roles = { "INVENTORY" })
	void addingInventoryWithProperRoleSucceeds() {
		
		final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("name", "iPhone11");
		formData.add("description", "upgrade");
		formData.add("price", "999.99");
		
		this.webTestClient.post().uri("/item")
			.body(BodyInserters.fromFormData(formData))
			.exchange()
			.expectStatus().isSeeOther();
		
		this.repository.findByName("iPhone11")
			.as(StepVerifier::create)
			.expectNextMatches(item -> {
				assertThat(item.getDescription()).isEqualTo("upgrade");
				assertThat(item.getPrice()).isEqualTo(999.99);

				return true;
			})
			.verifyComplete();
	}
	
	
	@Test
	@WithMockUser(username = "carol", roles = { "SOME_OTHER_ROLE" })
	void deletingInventoryWithoutProperRoleFail() {
		this.webTestClient.delete().uri("/item/some-item")
			.exchange()
			.expectStatus().isForbidden();
	}
	
	@Test
	@WithMockUser(username = "dan", roles = { "INVENTORY" })
	void deletingInventoryWithProperRoleSucceeds() {
		String id = this.repository.findByName("Alf alarm clock").map(Item::getId).block();
		
		this.webTestClient
			.delete().uri("/item/" + id)
			.exchange()
			.expectStatus().isSeeOther();
		
		this.repository.findByName("Alf alarm clock")
			.as(StepVerifier::create)
			.expectNextCount(0)
			.verifyComplete();
	}
}
