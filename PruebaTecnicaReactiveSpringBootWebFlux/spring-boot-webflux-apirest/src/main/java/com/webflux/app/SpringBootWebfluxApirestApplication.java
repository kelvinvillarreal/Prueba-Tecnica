package com.webflux.app;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import reactor.core.publisher.Mono;

@EnableEurekaClient
@SpringBootApplication
public class SpringBootWebfluxApirestApplication implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(SpringBootWebfluxApirestApplication.class);

	@Autowired
	private IProductoService iProductoService;
	
	@Autowired
	private ReactiveMongoTemplate reactiveMongoTemplate;
	
	public static void main(String[] args) {
		logger.info("Iniciando aplicación Spring Boot WebFlux");
		SpringApplication.run(SpringBootWebfluxApirestApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		logger.info("=== Iniciando carga de datos iniciales ===");
		
		initializeDatabase()
			.doOnSuccess(v -> logger.info("=== Datos iniciales cargados exitosamente ==="))
			.doOnError(e -> logger.error("Error durante la inicialización de datos", e))
			.subscribe();
	}
	
	/**
	 * Inicializa la base de datos con colecciones y datos de ejemplo
	 * @return Mono<Void> completada cuando termina la inicialización
	 */
	private Mono<Void> initializeDatabase() {
		return dropCollections()
			.then(createCategories())
			.then(createProducts())
			.onErrorResume(e -> {
				logger.error("Error en inicialización de base de datos", e);
				return Mono.empty();
			});
	}
	
	/**
	 * Elimina las colecciones existentes
	 * @return Mono<Void> completada cuando termina la limpieza
	 */
	private Mono<Void> dropCollections() {
		logger.debug("Eliminando colecciones existentes");
		
		return reactiveMongoTemplate.dropCollection("productos")
			.doOnSuccess(v -> logger.debug("Colección 'productos' eliminada"))
			.doOnError(e -> logger.debug("Colección 'productos' no existía: {}", e.getMessage()))
			.onErrorResume(e -> Mono.empty())
			.then(reactiveMongoTemplate.dropCollection("categorias")
				.doOnSuccess(v -> logger.debug("Colección 'categorias' eliminada"))
				.doOnError(e -> logger.debug("Colección 'categorias' no existía: {}", e.getMessage()))
				.onErrorResume(e -> Mono.empty()));
	}
	
	/**
	 * Crea las categorías iniciales
	 * @return Mono<Void> completada cuando se crean todas las categorías
	 */
	private Mono<Void> createCategories() {
		logger.info("Creando categorías iniciales");
		
		Categoria electronico = new Categoria("Electronico");
		Categoria estudio = new Categoria("Estudio");
		Categoria informatica = new Categoria("Informatica");
		Categoria enseres = new Categoria("Enseres");
		
		return Flux.just(electronico, estudio, informatica, enseres)
			.flatMap(categoria -> {
				logger.debug("Guardando categoría: {}", categoria.getNombre());
				return iProductoService.saveCategoria(categoria)
					.doOnSuccess(c -> logger.debug("Categoría creada: {} (ID: {})", c.getNombre(), c.getId()))
					.doOnError(e -> logger.error("Error al guardar categoría: {}", categoria.getNombre(), e));
			})
			.then()
			.onErrorResume(e -> {
				logger.error("Error crítico al crear categorías", e);
				return Mono.empty();
			});
	}
	
	/**
	 * Crea los productos iniciales
	 * @return Mono<Void> completada cuando se crean todos los productos
	 */
	private Mono<Void> createProducts() {
		logger.info("Creando productos iniciales");
		
		return Flux.fromIterable(getProductosIniciales())
			.flatMap(producto -> {
				producto.setCreateAt(new Date());
				logger.debug("Guardando producto: {}", producto.getNombre());
				
				return iProductoService.save(producto)
					.doOnSuccess(p -> logger.debug("Producto creado: {} (ID: {}, Precio: ${})",
						p.getNombre(), p.getId(), p.getPrecio()))
					.doOnError(e -> logger.error("Error al guardar producto: {}", producto.getNombre(), e));
			})
			.then()
			.onErrorResume(e -> {
				logger.error("Error crítico al crear productos", e);
				return Mono.empty();
			});
	}
	
	/**
	 * Obtiene la lista de productos iniciales
	 * @return Lista de productos de ejemplo
	 */
	private java.util.List<Producto> getProductosIniciales() {
		Categoria electronico = new Categoria("Electronico");
		Categoria informatica = new Categoria("Informatica");
		Categoria enseres = new Categoria("Enseres");
		
		return java.util.List.of(
			// Monitores
			new Producto("Monitor Samsung 24", 100.10, electronico),
			new Producto("Monitor LG 24", 200.10, electronico),
			new Producto("Monitor Spectra 24", 300.10, electronico),
			// Portátiles
			new Producto("Lenovo Portátil", 400.10, informatica),
			new Producto("Asus Portátil", 500.10, informatica),
			new Producto("Acer Portátil", 600.10, informatica),
			// Electrodomésticos
			new Producto("Televisión Hisense", 700.10, electronico),
			new Producto("Cocina Eléctrica", 800.10, enseres),
			new Producto("Enfriador", 900.10, enseres)
		);
	}
}
