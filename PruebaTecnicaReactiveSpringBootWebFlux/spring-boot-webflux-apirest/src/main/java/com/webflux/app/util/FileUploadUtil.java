package com.webflux.app.util;

import java.io.File;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;

import reactor.core.publisher.Mono;

/**
 * Utilidad para manejo de archivos en operaciones reactivas
 */
public class FileUploadUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUploadUtil.class);
    private static final String INVALID_CHARS_REGEX = "[\\\\/:*?\"<>|]"; 
    
    /**
     * Sanitiza el nombre de un archivo eliminando caracteres inválidos
     * @param filename Nombre original del archivo
     * @return Nombre del archivo sanitizado
     */
    public static String sanitizeFileName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del archivo no puede estar vacío");
        }
        
        String sanitized = filename
                .replaceAll(INVALID_CHARS_REGEX, "-")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .toLowerCase();
        
        logger.debug("Archivo sanitizado: {} -> {}", filename, sanitized);
        return sanitized;
    }
    
    /**
     * Genera un nombre único para el archivo
     * @param filename Nombre original del archivo
     * @return Nombre único del archivo
     */
    public static String generateUniqueFileName(String filename) {
        String sanitized = sanitizeFileName(filename);
        String uniqueName = UUID.randomUUID().toString() + "-" + sanitized;
        logger.debug("Nombre único generado: {}", uniqueName);
        return uniqueName;
    }
    
    /**
     * Sube un archivo de forma reactiva
     * @param file Archivo a subir
     * @param uploadPath Ruta donde subir el archivo
     * @param filename Nombre del archivo
     * @return Mono<String> con el nombre del archivo subido
     */
    public static Mono<String> uploadFile(FilePart file, String uploadPath, String filename) {
        logger.debug("Iniciando carga de archivo: {} -> {}", file.filename(), filename);
        
        // Crear directorio si no existe
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
            logger.debug("Directorio creado: {}", uploadPath);
        }
        
        File targetFile = new File(uploadPath + filename);
        
        return file.transferTo(targetFile)
                .doOnSuccess(v -> logger.info("Archivo subido exitosamente: {}", filename))
                .doOnError(e -> logger.error("Error al subir archivo: {}", filename, e))
                .then(Mono.just(filename))
                .onErrorResume(e -> Mono.error(
                        new IllegalArgumentException("Error al subir archivo: " + e.getMessage(), e)
                ));
    }
    
    /**
     * Valida el tipo de archivo permitido
     * @param filename Nombre del archivo
     * @param allowedExtensions Extensiones permitidas
     * @return true si el archivo es válido
     */
    public static boolean isValidFileType(String filename, String... allowedExtensions) {
        String extension = getFileExtension(filename).toLowerCase();
        
        for (String allowed : allowedExtensions) {
            if (extension.equals(allowed.toLowerCase())) {
                return true;
            }
        }
        
        logger.warn("Tipo de archivo no permitido: {}", extension);
        return false;
    }
    
    /**
     * Obtiene la extensión de un archivo
     * @param filename Nombre del archivo
     * @return Extensión del archivo
     */
    public static String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }
}
