package com.webflux.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webflux.app.models.documents.Categoria;
import com.webflux.app.models.documents.Producto;
import com.webflux.app.models.services.IProductoService;

import reactor.core.publisher.Mono;

@AutoConfigureWebTestClient //1.Simulado, sin levantar el servidor Nety
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) //2.Simulado, sin levantar el servidor Nety
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) //Le indica que tome la configuracion principal de la aplicacion. Toma todo el aplication context de spring
class SpringBootWebfluxApirestApplicationTests {

	@Autowired
	private WebTestClient client;
	
	@Autowired
	private IProductoService iProductoService;
	
	@Value("${config.base.endpoint}")
	private String url;
	/*
	@Test
	public void listarTest() {
		client.get() //peticion GET
		.uri("/api/v2/productos")//endpoint
		.accept(MediaType.APPLICATION_JSON_UTF8)//Consumir un mediatype		
		.exchange() //enviar el request al endpoint y consumir el response
		.expectStatus().isOk() //ok=200
		.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)//cabecera
		.expectBodyList(Producto.class)//la respuesta retorna un list de productos
		.hasSize(9);//Nro de elementos de la lista
	}
	*/
	
	@Test
	public void listarTest() {
		client.get() //peticion GET
		.uri(url)//endpoint
		.accept(MediaType.APPLICATION_JSON_UTF8)//Consumir un mediatype		
		.exchange() //enviar el request al endpoint y consumir el response
		.expectStatus().isOk() //ok=200
		.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)//cabecera
		.expectBodyList(Producto.class)//la respuesta retorna un list de productos
		.consumeWith(response -> {
			List<Producto> productos=response.getResponseBody();
			productos.forEach(p->{
				System.out.println("test:"+p.getNombre());
			});
			
			Assertions.assertThat(productos.size()==10).isTrue();			
		});
	}
	
	
	@Test
	public void verTest() {
		//Las pruebas unitarias no se pueden trabajar dentro de un subscribe
		//Es necesario que sean sincronos
		Producto producto = iProductoService.obtenerPorNombre("monitor samsung 24").block();
		System.out.println("Id--->"+producto.getId());
		System.out.println("Nombre--->"+producto.getNombre());
		
		client.get() //peticion GET
		.uri(url+"/{id}",Collections.singletonMap("id", producto.getId()))//endpoint
		.accept(MediaType.APPLICATION_JSON_UTF8)//Consumir un mediatype		
		.exchange() //enviar el request al endpoint y consumir el response
		.expectStatus().isOk() //ok=200
		.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)//cabecera
		.expectBody(Producto.class)
		.consumeWith(response->{
			Producto p=response.getResponseBody();
			//Evaluaciones
			Assertions.assertThat(p.getId()).isNotEmpty();
			Assertions.assertThat(p.getId().length()>0).isTrue();
			Assertions.assertThat(p.getNombre()).isEqualTo("monitor samsung 24");
		});
		
		/*.expectBody()
		.jsonPath("$.id").isNotEmpty()
		.jsonPath("$.nombre").isEqualTo("monitor samsung 24");*/
		
	}
	
	@Test
	public void crearTest() {
		Categoria categoria=iProductoService.findCategoriaByNombre("enseres").block();
		Producto producto=new Producto("Cocina electrica test",100.00, categoria);
		
		client.post()
		.uri(url)
		.contentType(MediaType.APPLICATION_JSON_UTF8) //Tipo de contenido del REQUEST (json)
		.accept(MediaType.APPLICATION_JSON_UTF8)//Tipo de contenido del RESPONSE(json)
		.body(Mono.just(producto), Producto.class)//Acepta un publiser observable del tipo Mono
		.exchange()
		.expectStatus().isCreated()
		.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
		.expectBody()
		.jsonPath("$.producto.id").isNotEmpty()
		.jsonPath("$.producto.nombre").isEqualTo("Cocina electrica test")
		.jsonPath("$.producto.categoria.nombre").isEqualTo("enseres");
	}
	
	@Test
	public void crear2Test() {
		Categoria categoria=iProductoService.findCategoriaByNombre("enseres").block();
		Producto producto=new Producto("Cocina electrica test",100.00, categoria);
		
		client.post()
		.uri(url)
		.contentType(MediaType.APPLICATION_JSON_UTF8) //Tipo de contenido del REQUEST (json)
		.accept(MediaType.APPLICATION_JSON_UTF8)//Tipo de contenido del RESPONSE(json)
		.body(Mono.just(producto), Producto.class)//Acepta un publiser observable del tipo Mono
		.exchange()
		.expectStatus().isCreated()
		.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
		//.expectBody(Producto.class)
		.expectBody(new ParameterizedTypeReference<LinkedHashMap<String, Object>>(){})		//Cambios para el RestController
		.consumeWith(response->{
			//Producto p=response.getResponseBody();		
			Object o=response.getResponseBody().get("producto");		//Cambios para el RestController
			Producto p=new ObjectMapper().convertValue(o, Producto.class);		//Cambios para el RestController
			Assertions.assertThat(p.getId()).isNotEmpty();
			Assertions.assertThat(p.getNombre()).isEqualTo("Cocina electrica test");
			Assertions.assertThat(p.getCategoria().getNombre()).isEqualTo("enseres");
		});
	}
	
	
	@Test
	public void editarTest() {
		Producto producto=iProductoService.findByNombre("monitor lg 24").block();
		Categoria categoria=iProductoService.findCategoriaByNombre("Electronico").block();
		
		Producto productoEditado=new Producto("LG editado",700.00, categoria);
		
		client.put()
		.uri(url+"/{id}",Collections.singletonMap("id", producto.getId()))
		.contentType(MediaType.APPLICATION_JSON_UTF8)
		.accept(MediaType.APPLICATION_JSON_UTF8)
		.body(Mono.just(productoEditado),Producto.class)
		.exchange()
		.expectStatus().isCreated()
		.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
		.expectBody()
		.jsonPath("$.id").isNotEmpty()
		.jsonPath("$.nombre").isEqualTo("LG editado")
		.jsonPath("$.categoria.nombre").isEqualTo("Electronico");				
	}
	
	@Test
	public void eliminarTest() {
		Producto producto=iProductoService.findByNombre("television hisense").block();
		client.delete()
		.uri(url+"/{id}",Collections.singletonMap("id", producto.getId()))
		.exchange()
		.expectStatus().isNoContent()
		.expectBody()
		.isEmpty();
		
		client.get()
		.uri(url+"/{id}",Collections.singletonMap("id", producto.getId()))
		.exchange()
		.expectStatus().isNotFound()
		.expectBody()
		.isEmpty();
	}
}


