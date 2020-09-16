package com.greglturnquist.hackingspringboot.reactive;

import static org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.HypermediaWebTestClientConfigurer;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.hateoas.server.core.TypeReferences.CollectionModelType;
import org.springframework.hateoas.server.core.TypeReferences.EntityModelType;

import reactor.test.StepVerifier;

@SpringBootTest
@EnableHypermediaSupport(type = HAL)
@AutoConfigureWebTestClient
public class ApiItemControllerTest {

	@Autowired
	WebTestClient webTestClient;
	
	@Autowired
	ItemRepository repository;
	
	@Autowired
	HypermediaWebTestClientConfigurer webClientConfigurer;
	
	@BeforeEach
	void setUp() {
		this.webTestClient = this.webTestClient.mutateWith(webClientConfigurer);
	}
	
	@Test
	void noCredentialsFailsAtRoot() {
		this.webTestClient.get().uri("/api")
			.exchange()
			.expectStatus().isUnauthorized();
	}
	
	@Test
	@WithMockUser(username = "ada")
	void credentialsWorkAtRoot() {
		this.webTestClient.get().uri("/api")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("{\"_links\":{\"self\":{\"href\":\"/api\"},\"item\":{\"href\":\"/api/items\"}}}");
	}
	
	@Test
	@WithMockUser(username = "alice", roles = { "SOME_OTHER_ROLE" })
	void addingInventoryWithoutProperRoleFails() {
		this.webTestClient
			.post().uri("/api/items/add")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{" +
					"\"name\": \"iPhone X\", " + 
					"\"description\": \"upgrade\", " + 
					"\"price\": 999.99" + 
					"}")
			.exchange()
			.expectStatus().isForbidden();
	}
	
	@Test
	@WithMockUser(username = "alice", roles = { "INVENTORY" })
	void addingInventoryWithProperRoleSucceeds() {
		this.webTestClient
			.post().uri("/api/items/add")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{" +
					"\"name\": \"iPhone X\", " + 
					"\"description\": \"upgrade\", " + 
					"\"price\": 999.99" + 
					"}")
			.exchange()
			.expectStatus().isCreated();
		
		this.repository.findByName("iPhone X")
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
	void deletingInventoryWithoutProperRoleFails() {
		this.webTestClient.delete().uri("/api/items/delete/some-item")
			.exchange()
			.expectStatus().isForbidden();
	}
	
	@Test
	@WithMockUser(username = "dan", roles = { "INVENTORY" })
	void deletingInventoryWithProperRoleSucceeds() {
		String id = this.repository.findByName("Alf alarm clock")
						.map(Item::getId)
						.block();
		
		this.webTestClient.delete().uri("/api/items/delete/" + id)
			.exchange()
			.expectStatus().isNoContent();
		
		this.repository.findById(id)
			.as(StepVerifier::create)
			.expectNextCount(0)
			.verifyComplete();
		
	}
	
	@Test
	@WithMockUser(username = "alice")
	void navigateToItemWithoutInventoryAuthority() {
		RepresentationModel<?> root = this.webTestClient.get().uri("/api")
			.exchange()
			.expectBody(RepresentationModel.class)
			.returnResult().getResponseBody();
		
		CollectionModel<EntityModel<Item>> items = this.webTestClient.get()
			.uri(root.getRequiredLink(IanaLinkRelations.ITEM).toUri())
			.exchange()
			.expectBody(new CollectionModelType<EntityModel<Item>>() {})
			.returnResult().getResponseBody();
		
		assertThat(items.getLinks()).hasSize(1);
		assertThat(items.hasLink(IanaLinkRelations.SELF)).isTrue();
		
		EntityModel<Item> first = items.getContent().iterator().next();
		
		EntityModel<Item> item = this.webTestClient.get()
				.uri(first.getRequiredLink(IanaLinkRelations.SELF).toUri())
				.exchange()
				.expectBody(new EntityModelType<Item>() {}) //
				.returnResult().getResponseBody();
		
		assertThat(item.getLinks()).hasSize(2);
		assertThat(item.hasLink(IanaLinkRelations.SELF)).isTrue();
		assertThat(item.hasLink(IanaLinkRelations.ITEM)).isTrue();
	}

	@Test
	@WithMockUser(username = "alice", roles={ "INVENTORY" })
	void navigateToItemWithInventoryAuthority() {
		RepresentationModel<?> root = this.webTestClient.get().uri("/api")
			.exchange()
			.expectBody(RepresentationModel.class)
			.returnResult().getResponseBody();
		
		CollectionModel<EntityModel<Item>> items = this.webTestClient.get()
			.uri(root.getRequiredLink(IanaLinkRelations.ITEM).toUri())
			.exchange()
			.expectBody(new CollectionModelType<EntityModel<Item>>() {})
			.returnResult().getResponseBody();
		
		assertThat(items.getLinks()).hasSize(2);
		assertThat(items.hasLink(IanaLinkRelations.SELF)).isTrue();
		assertThat(items.hasLink("add")).isTrue();
		
		EntityModel<Item> first = items.getContent().iterator().next();
		
		EntityModel<Item> item = this.webTestClient.get()
				.uri(first.getRequiredLink(IanaLinkRelations.SELF).toUri())
				.exchange()
				.expectBody(new EntityModelType<Item>() {}) //
				.returnResult().getResponseBody();
		
		assertThat(item.getLinks()).hasSize(3);
		assertThat(item.hasLink(IanaLinkRelations.SELF)).isTrue();
		assertThat(item.hasLink(IanaLinkRelations.ITEM)).isTrue();
		assertThat(item.hasLink("delete")).isTrue();
	}

}
