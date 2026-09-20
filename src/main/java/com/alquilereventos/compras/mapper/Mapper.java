package com.alquilereventos.compras.mapper;

/**
 * Interfaz generica de mapeo bidireccional Entity &lt;-&gt; DTO.
 *
 * <p>Jerarquia de clases: todos los mappers del microservicio implementan esta
 * interfaz, garantizando la reutilizacion de codigo transversal.</p>
 *
 * @param <E> tipo de la entidad JPA
 * @param <D> tipo del DTO
 */
public interface Mapper<E, D> {

    /** Convierte una entidad a su DTO. */
    D toDto(E entity);

    /** Convierte un DTO a su entidad. */
    E toEntity(D dto);
}
