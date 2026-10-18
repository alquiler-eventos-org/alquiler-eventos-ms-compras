package com.alquilereventos.compras.config.security;

import com.alquilereventos.common.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

/** Respuestas uniformes y sin detalles internos para JWT ausente/invalido y permisos insuficientes. */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        escribir(response, request, HttpStatus.UNAUTHORIZED, "Autenticacion requerida o token invalido");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        escribir(response, request, HttpStatus.FORBIDDEN, "No posee permisos para este recurso");
    }

    private void escribir(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                          String mensaje) throws IOException {
        ApiError error = new ApiError();
        error.setCodigo(status.value());
        error.setMensaje(mensaje);
        error.setRuta(request.getRequestURI());
        error.setFecha(OffsetDateTime.now());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
