package com.alquilereventos.compras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Microservicio de compras del sistema de alquiler de equipos para eventos.
 *
 * <p>Gestiona proveedores y ordenes de compra, usando las entidades y los
 * repositorios del JAR compartido {@code alquiler-eventos-common} (entrega 1),
 * sin redefinirlos. El repositorio de apoyo {@code ProveedorBusquedaRepository}
 * (busqueda por nombre) vive en este microservicio.</p>
 */
@SpringBootApplication
@EntityScan(basePackages = "com.alquilereventos.common.entity")
@EnableJpaRepositories(basePackages = {
        "com.alquilereventos.common.repository",
        "com.alquilereventos.compras.repository"
})
public class ComprasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComprasApplication.class, args);
    }
}
