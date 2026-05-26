package com.webflux.app.handler;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.webflux.app.models.documents.Categoria;
import com.webflux.app.models.documents.Producto;
import com.webflux.app.models.services.IProductoService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ProductoHandler {

	private static final Logger logger = LoggerFactory.getLogger(ProductoHandler.class);

	@Autowired
	private IProductoService iProductoService;
	
	@Value("${config.uploads.path}")
	private String path;
	
	@Autowired
	private Validator validator;

	/**
	 * Lista todos los productos disponibles
	 * @param request ServerRequest del cliente
	 * @return Mono<ServerResponse> con lista de productos
	 */
	public Mono<ServerResponse> listar(ServerRequest request) {
		logger.debug("Iniciando listado de productos");
		
		return ServerResponse.ok()
			.contentType(MediaType.APPLICATION_JSON)
			.body(iProductoService.findAll()
				.doOnNext(p -> logger.debug("Producto cargado: {}", p.getId()))
				.doOnError(e -> logger.error("Error al listar productos", e))
				.onErrorResume(e -> {
					logger.error("Error fatal en listado de productos: {}", e.getMessage());
					return Mono.empty();
				}),
				Producto.class)
		.onErrorResume(e -> {
			logger.error("Error al construir respuesta de listado", e);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new ErrorResponse("Error al listar productos", e.getMessage()));
		});
	}
	
	/**
	 * Obtiene un producto por ID
	 * @param request ServerRequest con el ID del producto
	 * @return Mono<ServerResponse> con el producto encontrado
	 */
	public Mono<ServerResponse> ver(ServerRequest request){
		String id = request.pathVariable("id");
		logger.debug("Buscando producto con ID: {}", id);
		
		return iProductoService.findById(id)
			.doOnSuccess(p -> logger.info("Producto encontrado: {} - {}", id, p.getNombre()))
			.flatMap(p -> ServerResponse.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromValue(p)))
			.switchIfEmpty(Mono.defer(() -> {
				logger.warn("Producto no encontrado con ID: {}", id);
				return ServerResponse.notFound().build();
			}))
			.onErrorResume(e -> {
				logger.error("Error al buscar producto con ID: {}", id, e);
				return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(new ErrorResponse("Error al obtener producto", e.getMessage()));
			});
	}
	
	/**
	 * Crea un nuevo producto con validación
	 * @param request ServerRequest con datos del producto
	 * @return Mono<ServerResponse> con el producto creado
	 */
	public Mono<ServerResponse> crear(ServerRequest request){
		logger.debug("Iniciando creación de nuevo producto");
		
		Mono<Producto> producto = request.bodyToMono(Producto.class)
			.doOnError(e -> logger.error("Error al parsear cuerpo del request", e))
			.onErrorResume(e -> Mono.error(new IllegalArgumentException("Formato de producto inválido", e)));
		
		return producto.flatMap(p -> {
			logger.debug("Validando producto: {}", p.getNombre());
			
			Errors errors = new BeanPropertyBindingResult(p, Producto.class.getName());
			validator.validate(p, errors);
			
			if(errors.hasErrors()) {
				logger.warn("Errores de validación en producto: {}", errors.getErrorCount());
				return Flux.fromIterable(errors.getFieldErrors())
					.map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
					.doOnNext(msg -> logger.debug("Error de validación: {}", msg))
					.collectList()
					.flatMap(list -> ServerResponse.badRequest()
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(new ErrorResponse("Errores de validación", list)));
			} else {
				if(p.getCreateAt() == null) {
					p.setCreateAt(new Date());
				}
				
				logger.info("Guardando nuevo producto: {}", p.getNombre());
				return iProductoService.save(p)
					.doOnSuccess(prod -> logger.info("Producto guardado exitosamente con ID: {}", prod.getId()))
					.flatMap(pdb -> ServerResponse.created(URI.create("/api/v2/productos/" + pdb.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.body(BodyInserters.fromValue(pdb)))
					.doOnError(e -> logger.error("Error al guardar producto", e))
					.onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(new ErrorResponse("Error al crear producto", e.getMessage())));
			}
		})
		.onErrorResume(e -> {
			logger.error("Error fatal en creación de producto", e);
			return ServerResponse.status(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new ErrorResponse("Error al procesar solicitud", e.getMessage()));
		});
	}
	
	/**
	 * Edita un producto existente
	 * @param request ServerRequest con ID y datos del producto
	 * @return Mono<ServerResponse> con el producto actualizado
	 */
	public Mono<ServerResponse> editar(ServerRequest request){
		String id = request.pathVariable("id");
		logger.debug("Iniciando edición de producto con ID: {}", id);
		
		Mono<Producto> producto = request.bodyToMono(Producto.class)
			.doOnError(e -> logger.error("Error al parsear cuerpo de actualización", e))
			.onErrorResume(e -> Mono.error(new IllegalArgumentException("Formato de producto inválido", e)));
		
		Mono<Producto> productoDb = iProductoService.findById(id)
			.doOnError(e -> logger.error("Error al buscar producto en BD", e));
		
		return productoDb.zipWith(producto, (db, req) -> {
			logger.debug("Actualizando campos del producto {}", id);
			db.setNombre(req.getNombre());
			db.setPrecio(req.getPrecio());
			db.setCategoria(req.getCategoria());
			return db;
		})
		.flatMap(p -> {
			logger.info("Guardando producto actualizado: {}", p.getId());
			return ServerResponse.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(iProductoService.save(p)
					.doOnSuccess(prod -> logger.info("Producto actualizado exitosamente: {}", prod.getId()))
					.doOnError(e -> logger.error("Error al actualizar producto", e))
					.onErrorResume(e -> Mono.error(e)),
					Producto.class);
		})
		.switchIfEmpty(Mono.defer(() -> {
			logger.warn("Producto no encontrado para actualizar, ID: {}", id);
			return ServerResponse.notFound().build();
		}))
		.onErrorResume(e -> {
			logger.error("Error en edición de producto {}", id, e);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new ErrorResponse("Error al actualizar producto", e.getMessage()));
		});
	}
	
	/**
	 * Elimina un producto por ID
	 * @param request ServerRequest con el ID del producto
	 * @return Mono<ServerResponse> confirmando la eliminación
	 */
	public Mono<ServerResponse> eliminar(ServerRequest request){
		String id = request.pathVariable("id");
		logger.debug("Iniciando eliminación de producto con ID: {}", id);
		
		Mono<Producto> productoDb = iProductoService.findById(id)
			.doOnError(e -> logger.error("Error al buscar producto para eliminación", e));
		
		return productoDb.flatMap(p -> {
			logger.info("Eliminando producto: {} - {}", id, p.getNombre());
			return iProductoService.delete(p)
				.then(ServerResponse.noContent().build())
				.doOnSuccess(v -> logger.info("Producto eliminado exitosamente: {}", id))
				.doOnError(e -> logger.error("Error al eliminar producto {}", id, e))
				.onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(new ErrorResponse("Error al eliminar producto", e.getMessage())));
		})
		.switchIfEmpty(Mono.defer(() -> {
			logger.warn("Producto no encontrado para eliminar, ID: {}", id);
			return ServerResponse.notFound().build();
		}))
		.onErrorResume(e -> {
			logger.error("Error fatal en eliminación de producto {}", id, e);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new ErrorResponse("Error al procesar solicitud", e.getMessage()));
		});
	}

	/**
	 * Crea un producto con foto
	 * @param request ServerRequest con datos multipart del producto y foto
	 * @return Mono<ServerResponse> con el producto creado
	 */
	public Mono<ServerResponse> crearConFoto(ServerRequest request){
		logger.debug("Iniciando creación de producto con foto");
		
		Mono<Producto> producto = request.multipartData()
			.doOnError(e -> logger.error("Error al parsear datos multipart", e))
			.map(multipart -> {
				logger.debug("Extrayendo datos del formulario");
				FormFieldPart nombre = (FormFieldPart) multipart.toSingleValueMap().get("nombre");
				FormFieldPart precio = (FormFieldPart) multipart.toSingleValueMap().get("precio");
				FormFieldPart categoriaId = (FormFieldPart) multipart.toSingleValueMap().get("categoria.id");
				FormFieldPart categoriaNombre = (FormFieldPart) multipart.toSingleValueMap().get("categoria.nombre");
				
				if(nombre == null || precio == null) {
					logger.error("Campos requeridos faltantes en formulario");
					throw new IllegalArgumentException("Los campos nombre y precio son obligatorios");
				}
				
				Categoria categoria = new Categoria(categoriaNombre != null ? categoriaNombre.value() : "Sin categoría");
				if(categoriaId != null) {
					categoria.setId(categoriaId.value());
				}
				return new Producto(nombre.value(), Double.parseDouble(precio.value()), categoria);
			})
			.onErrorResume(e -> {
				logger.error("Error al procesar datos de formulario", e);
				return Mono.error(e);
			});
		
		return request.multipartData()
			.doOnError(e -> logger.error("Error al obtener multipart data", e))
			.map(multipart -> multipart.toSingleValueMap().get("file"))
			.cast(FilePart.class)
			.flatMap(file -> {
				logger.debug("Procesando archivo: {}", file.filename());
				return producto.flatMap(p -> uploadFileInternal(file, p))
					.doOnError(e -> logger.error("Error al subir archivo", e));
			})
			.onErrorResume(e -> {
				logger.error("Error en creación con foto", e);
				return ServerResponse.status(HttpStatus.BAD_REQUEST)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(new ErrorResponse("Error al crear producto con foto", e.getMessage()));
			});
	}
	
	/**
	 * Sube una foto a un producto existente
	 * @param request ServerRequest con ID del producto y archivo
	 * @return Mono<ServerResponse> con el producto actualizado
	 */
	public Mono<ServerResponse> upload(ServerRequest request){
		String id = request.pathVariable("id");
		logger.debug("Iniciando carga de foto para producto: {}", id);
		
		return request.multipartData()
			.doOnError(e -> logger.error("Error al obtener archivo", e))
			.map(multipart -> multipart.toSingleValueMap().get("file"))
			.cast(FilePart.class)
			.flatMap(file -> {
				logger.debug("Buscando producto para subir foto: {}", id);
				return iProductoService.findById(id)
					.flatMap(p -> uploadFileInternal(file, p))
					.doOnError(e -> logger.error("Error al procesar foto para producto {}", id, e));
			})
			.switchIfEmpty(Mono.defer(() -> {
				logger.warn("Producto no encontrado para carga de foto, ID: {}", id);
				return ServerResponse.notFound().build();
			}))
			.onErrorResume(e -> {
				logger.error("Error fatal en carga de foto para producto {}", id, e);
				return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(new ErrorResponse("Error al subir foto", e.getMessage()));
			});
	}
	
	/**
	 * Método privado para manejar la lógica interna de carga de archivos
	 * @param file Archivo a subir
	 * @param p Producto a actualizar
	 * @return Mono<ServerResponse> con el producto actualizado
	 */
	private Mono<ServerResponse> uploadFileInternal(FilePart file, Producto p) {
		String fileName = sanitizeFileName(file.filename());
		String finalFileName = UUID.randomUUID().toString() + "-" + fileName;
		
		logger.debug("Guardando archivo: {} -> {}", file.filename(), finalFileName);
		p.setFoto(finalFileName);
		
		return file.transferTo(new File(path + finalFileName))
			.doOnSuccess(v -> logger.info("Archivo guardado exitosamente: {}", finalFileName))
			.then(iProductoService.save(p))
			.doOnSuccess(prod -> logger.info("Producto actualizado con foto: {}", prod.getId()))
			.flatMap(prod -> ServerResponse.created(URI.create("/api/v2/productos/" + prod.getId()))
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromValue(prod)))
			.doOnError(e -> logger.error("Error al procesar archivo", e))
			.onErrorResume(e -> Mono.error(e));
	}
	
	/**
	 * Limpia y valida el nombre del archivo
	 * @param filename Nombre original del archivo
	 * @return Nombre del archivo sanitizado
	 */
	private String sanitizeFileName(String filename) {
		return filename.replace(" ", "-")
				.replace(":", "")
				.replace("\\", "")
				.replace("/", "-")
				.toLowerCase();
	}
	
	/**
	 * Clase interna para respuestas de error consistentes
	 */
	public static class ErrorResponse {
		private String message;
		private Object details;
		
		public ErrorResponse(String message, Object details) {
			this.message = message;
			this.details = details;
		}
		
		public String getMessage() {
			return message;
		}
		
		public Object getDetails() {
			return details;
		}
	}
}
