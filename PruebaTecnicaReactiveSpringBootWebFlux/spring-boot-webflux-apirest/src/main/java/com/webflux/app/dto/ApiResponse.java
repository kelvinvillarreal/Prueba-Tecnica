package com.webflux.app.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO unificado para todas las respuestas de la API
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private boolean success;
    
    /**
     * Constructor para respuesta exitosa
     */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
        this.success = code >= 200 && code < 300;
    }
    
    /**
     * Constructor para respuesta con error
     */
    public ApiResponse(int code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.success = false;
    }
    
    /**
     * Crea una respuesta exitosa (200)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Operación exitosa", data);
    }
    
    /**
     * Crea una respuesta exitosa con mensaje personalizado
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }
    
    /**
     * Crea una respuesta exitosa para creación (201)
     */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "Recurso creado exitosamente", data);
    }
    
    /**
     * Crea una respuesta sin contenido (204)
     */
    public static <T> ApiResponse<T> noContent() {
        return new ApiResponse<>(204, "Operación completada sin contenido");
    }
    
    /**
     * Crea una respuesta de error (400)
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(400, message);
    }
    
    /**
     * Crea una respuesta de no encontrado (404)
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(404, message);
    }
    
    /**
     * Crea una respuesta de error interno (500)
     */
    public static <T> ApiResponse<T> internalError(String message) {
        return new ApiResponse<>(500, message);
    }
    
    // Getters y Setters
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    @Override
    public String toString() {
        return "ApiResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", success=" + success +
                ", timestamp=" + timestamp +
                '}';
    }
}
