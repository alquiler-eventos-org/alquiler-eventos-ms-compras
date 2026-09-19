package com.alquilereventos.compras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de compras del sistema de alquiler de equipos para eventos.
 *
 * <p>Gestiona proveedores y ordenes de compra, usando las entidades,
 * repositorios y DTOs del JAR compartido {@code alquiler-eventos-common}
 * (entrega 1), sin redefinirlos.</p>
 */
@SpringBootApplication
public class ComprasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComprasApplication.class, args);
    }
}
