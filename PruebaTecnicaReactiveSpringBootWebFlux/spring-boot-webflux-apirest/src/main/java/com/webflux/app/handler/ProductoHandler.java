package com.webflux.app.handler;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

@Component //Es el controlador, pero no antonar con @Controller
public class ProductoHandler {

	@Autowired
	private IProductoService iProductoService;
	
	@Value("${config.uploads.path}")
	private String path;
	
	@Autowired
	private Validator validator;

	public Mono<ServerResponse> listar(ServerRequest request) {
		return ServerResponse.ok()
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.body(iProductoService.findAll(),Producto.class);
	}
	
	
	public Mono<ServerResponse> ver (ServerRequest request){
		String id=request.pathVariable("id");
		
		return iProductoService.findById(id) //
				.flatMap(p->ServerResponse //Conver el serverResonse a un Mono<ServerResponse>
						.ok() //200
						.contentType(MediaType.APPLICATION_JSON_UTF8) //opcional
						.body(BodyInserters.fromObject(p)))//Recibe un publishe del tipo mono o flux. P como no es un tipo reactivo  usamos inserters
				.switchIfEmpty(ServerResponse.notFound().build());	//build retorna un Mono<ServerResponse>										
	}
	
//	public Mono<ServerResponse> crear(ServerRequest request){
//		Mono<Producto> producto=request.bodyToMono(Producto.class);//Convertir los datos del request en un Mono 
//		
//		return producto.flatMap(p->{ //Para emitir el producto
//			if(p.getCreateAt()==null) {
//				p.setCreateAt(new Date());
//			}
//			
//			return iProductoService.save(p);//Guardar en base de datos
//		}).flatMap(p->ServerResponse  //Una vez guardado se tiene que convertir al tipo Mono<ServerResponse>
//				.created(URI.create("/api/v2/productos/".concat(p.getId())))
//				.contentType(MediaType.APPLICATION_JSON_UTF8)
//				.body(BodyInserters.fromObject(p)));	
//	}
	
	public Mono<ServerResponse> crear(ServerRequest request){
		Mono<Producto> producto = request.bodyToMono(Producto.class);
		
		return producto.flatMap(p -> {
			
			Errors errors = new BeanPropertyBindingResult(p, Producto.class.getName());
			validator.validate(p, errors);
			
			if(errors.hasErrors()) {
				return Flux.fromIterable(errors.getFieldErrors())
						.map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
						.collectList()
						.flatMap(list -> ServerResponse.badRequest().body(BodyInserters.fromObject(list)));
			} else {
				if(p.getCreateAt() ==null) {
					p.setCreateAt(new Date());
				}
				return iProductoService.save(p).flatMap(pdb -> ServerResponse
						.created(URI.create("/api/v2/productos/".concat(pdb.getId())))
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.body(BodyInserters.fromObject(pdb)));
			}
			
		});
	}
	
	public Mono<ServerResponse> editar(ServerRequest request){
		Mono<Producto> producto = request.bodyToMono(Producto.class);//Convertir los datos del request en un Mono
		String id=request.pathVariable("id");
		
		Mono<Producto> productoDb=iProductoService.findById(id);
		
		return productoDb.zipWith(producto, (db, req) ->{ //Combina ambos flujos. Para set y get de objetos
			db.setNombre(req.getNombre());
			db.setPrecio(req.getPrecio());
			db.setCategoria(req.getCategoria());
			
			return db; //Retorn un Mono<Producto>
			
		}).flatMap(p->ServerResponse //Convertir al tipo Mono<ServerResponse>
				.created(URI.create("api/v2/productos/".concat(p.getId())))
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.body(iProductoService.save(p), Producto.class))
				.switchIfEmpty(ServerResponse.notFound().build());//Validar en caso de que el producto no exista 						
	}
	
	public Mono<ServerResponse> eliminar(ServerRequest request){
		String id=request.pathVariable("id");		
		Mono<Producto> productoDb=iProductoService.findById(id);
		
		return productoDb.flatMap(p->iProductoService.delete(p)//Delete siempre sera vacio
				.then(ServerResponse.noContent().build()))//Entonces convertir el void a un Mono<ServerResponse>
				.switchIfEmpty(ServerResponse.notFound().build());//Para manejar el error si no existe.
	}		

	public Mono<ServerResponse> crearConFoto(ServerRequest request){
		//Obtener un Mono<Producto> a partir multipartData
		Mono<Producto> producto=request.multipartData().map(multipart->{
			FormFieldPart nombre=(FormFieldPart)multipart.toSingleValueMap().get("nombre");
			FormFieldPart precio=(FormFieldPart)multipart.toSingleValueMap().get("precio");
			FormFieldPart categoriaId=(FormFieldPart)multipart.toSingleValueMap().get("categoria.id");
			FormFieldPart categoriaNombre=(FormFieldPart)multipart.toSingleValueMap().get("categoria.nombre");
			
			Categoria categoria=new Categoria(categoriaNombre.value());
			categoria.setId(categoriaId.value());
			return new Producto(nombre.value(), Double.parseDouble(precio.value()), categoria);
		});
		
		return request.multipartData() //Retorna un Observable (Mono) contiene el File
				.map(multipart -> multipart.toSingleValueMap().get("file")) //Obtenemos el file part
				.cast(FilePart.class) //Convertir al Part a FilePart
				//Sube la imagen y actualiza el producto
				.flatMap(file -> producto //Transforma el flujo a un Mono<Producto>. Se emite el file
						.flatMap(p -> {	//Se emite el producto						
							p.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
							.replace(" ", "-")
							.replace(":", "")
							.replace("\\", ""));
							
						return file.transferTo(new File(path + p.getFoto())) //subir la imagen
								.then(iProductoService.save(p));//una ves subido la imagen, actualizamos la imagen en mongo. Entonces ejecutamos otro Mono
				//Emitir un Mono<ServerResponse>
				})).flatMap(p -> ServerResponse //Se emite el producto
						.created(URI.create("/api/v2/productos/".concat(p.getId()))) 
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.body(BodyInserters.fromObject(p)))
				.switchIfEmpty(ServerResponse.notFound().build());//Cuando no existe el producto.
	}
	
	public Mono<ServerResponse> upload(ServerRequest request){
		String id = request.pathVariable("id");
		
		return request.multipartData() //Retorna un Observable (Mono) contiene el File
				.map(multipart -> multipart.toSingleValueMap().get("file")) //Obtenemos el file part
				.cast(FilePart.class) //Convertir al Part a FilePart
				//Sube la imagen y actualiza el producto
				.flatMap(file -> iProductoService.findById(id) //Transforma el flujo a un Mono<Producto>. Se emite el file
						.flatMap(p -> {	//Se emite el producto						
							p.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
							.replace(" ", "-")
							.replace(":", "")
							.replace("\\", ""));
							
						return file.transferTo(new File(path + p.getFoto())) //subir la imagen
								.then(iProductoService.save(p));//una ves subido la imagen, actualizamos la imagen en mongo. Entonces ejecutamos otro Mono
				//Emitir un Mono<ServerResponse>
				})).flatMap(p -> ServerResponse //Se emite el producto
						.created(URI.create("/api/v2/productos/".concat(p.getId()))) 
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.body(BodyInserters.fromObject(p)))
				.switchIfEmpty(ServerResponse.notFound().build());//Cuando no existe el producto.
	}
	
	
}
