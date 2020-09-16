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

import static com.greglturnquist.hackingspringboot.reactive.SecurityConfig.INVENTORY;
import static org.springframework.hateoas.mediatype.alps.Alps.alps;
import static org.springframework.hateoas.mediatype.alps.Alps.descriptor;
import static org.springframework.hateoas.server.reactive.WebFluxLinkBuilder.linkTo;
import static org.springframework.hateoas.server.reactive.WebFluxLinkBuilder.methodOn;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.alps.Alps;
import org.springframework.hateoas.mediatype.alps.Type;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class ApiItemController {

	private static final SimpleGrantedAuthority ROLE_INVENTORY = 
			new SimpleGrantedAuthority("ROLE_" + INVENTORY);
	
	private final ItemRepository repository;

	public ApiItemController(ItemRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/api")
	Mono<RepresentationModel<?>> root() {
		ApiItemController controller = methodOn(ApiItemController.class);

		Mono<Link> selfLink = linkTo(controller.root()).withSelfRel()
				.toMono();

		Mono<Link> itemsAggregateLink = linkTo(controller.findAll(null))
				.withRel(IanaLinkRelations.ITEM)
				.toMono();

		return Mono.zip(selfLink, itemsAggregateLink)
				.map(links -> Links.of(links.getT1(), links.getT2()))
				.map(links -> new RepresentationModel<>(links.toList()));
	}

	@GetMapping("/api/items")
	Mono<CollectionModel<EntityModel<Item>>> findAll(Authentication auth) {
		ApiItemController controller = methodOn(ApiItemController.class);

		Mono<Link> selfLink = linkTo(controller.findAll(auth)).withSelfRel().toMono();

		Mono<Links> allLinks;
		
		if (auth.getAuthorities().contains(ROLE_INVENTORY)) {
			Mono<Link> addNewLink = linkTo(controller.addNewItem(null, auth)).withRel("add").toMono();
			
			allLinks = Mono.zip(selfLink, addNewLink) //
					.map(links -> Links.of(links.getT1(), links.getT2()));
		} else {
			allLinks = selfLink.map(link -> Links.of(link));
		}

		return allLinks
				.flatMap(links -> this.repository.findAll()
						.flatMap(item -> findOne(item.getId(), auth))
						.collectList()
						.map(entityModels -> CollectionModel.of(entityModels, links)));
	}

	@GetMapping("/api/items/{id}")
	Mono<EntityModel<Item>> findOne(@PathVariable String id, Authentication auth) {
		ApiItemController controller = methodOn(ApiItemController.class);

		Mono<Link> selfLink = linkTo(controller.findOne(id, auth)).withSelfRel()
				.toMono();

		Mono<Link> aggregateLink = linkTo(controller.findAll(auth))
				.withRel(IanaLinkRelations.ITEM).toMono();

		Mono<Links> allLinks;
		
		if (auth.getAuthorities().contains(ROLE_INVENTORY)) {
			Mono<Link> deleteLink = linkTo(controller.deleteItem(id)).withRel("delete")
					.toMono();
			allLinks = Mono.zip(selfLink, aggregateLink, deleteLink)
					.map(links -> Links.of(links.getT1(), links.getT2(), links.getT3()));
			
		} else {
			allLinks = Mono.zip(selfLink, aggregateLink)
					.map(links -> Links.of(links.getT1(), links.getT2()));
		}

		return this.repository.findById(id)
				.zipWith(allLinks)
				.map(o -> EntityModel.of(o.getT1(), o.getT2()));
	}

	@PreAuthorize("hasRole('" + INVENTORY + "')")
	@PostMapping("/api/items/add")
	Mono<ResponseEntity<?>> addNewItem(@RequestBody Item item, Authentication auth) {
		return this.repository.save(item)
				.map(Item::getId)
				.flatMap(id -> findOne(id, auth))
				.map(newModel -> ResponseEntity.created(newModel
						.getRequiredLink(IanaLinkRelations.SELF)
						.toUri()).build());
	}
	
	@PreAuthorize("hasRole('" + INVENTORY + "')")
	@DeleteMapping("/api/items/delete/{id}")
	Mono<ResponseEntity<?>> deleteItem(@PathVariable String id) {
		return this.repository.deleteById(id)
				.thenReturn(ResponseEntity.noContent().build());
	}

	@PutMapping("/api/items/{id}")
	public Mono<ResponseEntity<?>> updateItem(@RequestBody Mono<EntityModel<Item>> item,
			@PathVariable String id, Authentication auth) {
		return item
				.map(EntityModel::getContent)
				.map(content -> new Item(id, content.getName(),
						content.getDescription(), content.getPrice()))
				.flatMap(this.repository::save)
				.then(findOne(id, auth))
				.map(model -> ResponseEntity.noContent()
						.location(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).build());
	}

	@GetMapping(value = "/api/items/profile"/*, produces = MediaTypes.ALPS_JSON_VALUE*/)
	public Alps profile() {
		return alps()
				.descriptor(Collections.singletonList(descriptor()
						.id(Item.class.getSimpleName() + "-representation")
						.descriptor(Arrays.stream(Item.class.getDeclaredFields())
								.map(field -> descriptor()
										.name(field.getName())
										.type(Type.SEMANTIC)
										.build())
								.collect(Collectors.toList()))
						.build()))
				.build();
	}
}
