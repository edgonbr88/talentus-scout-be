package com.talentus.scout.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

public interface FileStorageService {

    /**
     * Almacena un archivo multipart en un subdirectorio específico.
     *
     * @param file Archivo recibido desde el cliente
     * @param subDirectory Subdirectorio relativo (ej. "credentials", "documents")
     * @return Ruta o URL relativa accesible del archivo almacenado
     */
    String storeFile(MultipartFile file, String subDirectory);

    /**
     * Elimina un archivo previamente almacenado.
     *
     * @param fileUrl Ruta o URL relativa del archivo
     */
    void deleteFile(String fileUrl);

    /**
     * Genera una URL prefirmada temporal para el acceso a archivos privados (LOPNNA, credenciales).
     * En almacenamiento local devuelve la URL local directamente.
     *
     * @param fileKeyOrUrl Clave del objeto o URL almacenada
     * @param expiration Duración de validez de la URL prefirmada
     * @return URL accesible o prefirmada
     */
    default String generatePresignedUrl(String fileKeyOrUrl, Duration expiration) {
        return fileKeyOrUrl;
    }
}

