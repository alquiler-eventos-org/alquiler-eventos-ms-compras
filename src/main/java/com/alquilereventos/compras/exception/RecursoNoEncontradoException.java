package com.alquilereventos.compras.exception;

/**
 * Se lanza cuando un recurso no existe (o fue eliminado logicamente).
 * El handler central la traduce a HTTP 404 con el DTO ApiError.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
