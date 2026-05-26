package com.webflux.app.util;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.Errors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Utilidad para construcción de respuestas HTTP reactivas
 */
public class ResponseUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);
    
    /**
     * Crea una respuesta de error con mensajes de validación
     * @param errors Errores de validación
     * @return Mono<ServerResponse> con estado 400 y mensajes de error
     */
    public static Mono<ServerResponse> validationErrorResponse(Errors errors) {
        logger.debug("Construyendo respuesta de validación con {} errores", errors.getErrorCount());
        
        List<String> errorMessages = errors.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        
        return Flux.fromIterable(errorMessages)
                .collectList()
                .flatMap(list -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ErrorResponseDto("Error de validación", list)));
    }
    
    /**
     * Crea una respuesta de error genérica
     * @param status Estado HTTP
     * @param message Mensaje de error
     * @param detail Detalles adicionales
     * @return Mono<ServerResponse> con el estado y mensaje especificado
     */
    public static Mono<ServerResponse> errorResponse(HttpStatus status, String message, String detail) {
        logger.debug("Construyendo respuesta de error: {} - {}", status, message);
        
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponseDto(message, detail));
    }
    
    /**
     * Crea una respuesta de error con excepción
     * @param status Estado HTTP
     * @param message Mensaje de error
     * @param exception Excepción capturada
     * @return Mono<ServerResponse> con el estado y detalles de la excepción
     */
    public static Mono<ServerResponse> errorResponse(HttpStatus status, String message, Exception exception) {
        logger.error("Error en respuesta: {}", message, exception);
        
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponseDto(message, exception.getMessage()));
    }
    
    /**
     * DTO para respuestas de error
     */
    public static class ErrorResponseDto {
        private String message;
        private Object details;
        
        public ErrorResponseDto(String message, Object details) {
            this.message = message;
            this.details = details;
        }
        
        public String getMessage() { return message; }
        public Object getDetails() { return details; }
    }
}
