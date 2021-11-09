package com.webflux.app;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import com.webflux.app.models.documents.Categoria;
import com.webflux.app.models.documents.Producto;
import com.webflux.app.models.services.IProductoService;

import reactor.core.publisher.Flux;

@EnableEurekaClient
@SpringBootApplication
public class SpringBootWebfluxApirestApplication implements CommandLineRunner {

	@Autowired
	private IProductoService iProductoService;
	
	@Autowired
	private ReactiveMongoTemplate reactiveMongoTemplate;
	
	public static void main(String[] args) {
		SpringApplication.run(SpringBootWebfluxApirestApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Inicio");
		reactiveMongoTemplate.dropCollection("productos").subscribe();
		reactiveMongoTemplate.dropCollection("categorias").subscribe();				
		System.out.println("Productor borrados");
		
		Categoria electronico=new Categoria("Electronico");
		Categoria estudio=new Categoria("estudio");
		Categoria informatica=new Categoria("informatica");
		Categoria enseres=new Categoria("enseres");
		
		Flux.just(electronico,estudio,informatica,enseres)
		.flatMap(iProductoService::saveCategoria)
		.doOnNext(c->{
			System.out.println("Categoria creada: "+c.getNombre()+", id: "+c.getId());			
		}).thenMany(Flux.just(
				//Una vez que ha terminado el flujo:
				//Then, agregar un flujo del tipo Mono
				//ThenMany agregar otro flujo del tipo Flux
				new Producto("monitor samsung 24", 100.10, electronico),
				new Producto("monitor lg 24", 200.10, electronico),
				new Producto("monitor spectra 24", 300.10, electronico),
				new Producto("lenovo portatil", 400.10, informatica),
				new Producto("asus portatil", 500.10, informatica),
				new Producto("acer portatil", 600.10, informatica),
				new Producto("television hisense", 700.10, electronico),
				new Producto("cocina electrica", 800.10, enseres),
				new Producto("enfriador", 900.10, enseres)
				)
		.flatMap(producto-> {
			producto.setCreateAt(new Date());
			return iProductoService.save(producto);
		}))				
		.subscribe(
				producto->System.out.println("Insert: "+producto.getId()+" "+producto.getNombre()) //doOnNext
				);		
		System.out.println("Productor insertados");
	}

}
