package com.alquilereventos.compras.repository;

import com.alquilereventos.common.entity.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de apoyo del microservicio para la busqueda por nombre (la
 * "lupa" del listado). Siempre excluye los proveedores con borrado logico
 * (activo = false).
 *
 * <p>{@code nombre} nunca llega null: el service pasa cadena vacia cuando no
 * hay termino de busqueda (listar = buscar sin termino). Se usa un repositorio
 * local porque la busqueda del JAR common falla en PostgreSQL cuando un filtro
 * llega null (LOWER(bytea)).</p>
 */
public interface ProveedorBusquedaRepository extends JpaRepository<Proveedor, Integer> {

    @Query("""
            SELECT p FROM Proveedor p
            WHERE p.activo = true
              AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
            """)
    Page<Proveedor> buscar(@Param("nombre") String nombre, Pageable pageable);
}