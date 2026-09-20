package com.alquilereventos.compras.exception;

/**
 * Se lanza cuando se viola una regla de negocio.
 * El handler central la traduce a HTTP 400 con el DTO ApiError.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
