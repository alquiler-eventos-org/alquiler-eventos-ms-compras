package com.alquilereventos.compras.mapper;

import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;
import org.springframework.stereotype.Component;

/**
 * Convierte la entidad Proveedor del JAR common &lt;-&gt; los DTOs del JAR.
 *
 * <p>El nombre simple {@code Proveedor} es el DTO; la entidad se referencia
 * con su nombre completo para evitar el choque de nombres. Los campos
 * solo-lectura del DTO (id, activo) se asignan por constructor.</p>
 */
@Component
public class ProveedorMapper
        implements Mapper<com.alquilereventos.common.entity.Proveedor, Proveedor> {

    @Override
    public Proveedor toDto(com.alquilereventos.common.entity.Proveedor entity) {
        if (entity == null) {
            return null;
        }
        Proveedor dto = new Proveedor(entity.getId(), entity.getActivo());
        dto.setNombre(entity.getNombre());
        dto.setContacto(entity.getContacto());
        dto.setTelefono(entity.getTelefono());
        dto.setEmail(entity.getEmail());
        dto.setDireccion(entity.getDireccion());
        return dto;
    }

    @Override
    public com.alquilereventos.common.entity.Proveedor toEntity(Proveedor dto) {
        if (dto == null) {
            return null;
        }
        return baseEntity(dto.getNombre(), dto.getContacto(), dto.getTelefono(),
                dto.getEmail(), dto.getDireccion());
    }

    /** Sobrecarga: convierte el request de creacion en entidad. */
    public com.alquilereventos.common.entity.Proveedor toEntity(ProveedorCrearRequest request) {
        if (request == null) {
            return null;
        }
        return baseEntity(request.getNombre(), request.getContacto(), request.getTelefono(),
                request.getEmail(), request.getDireccion());
    }

    private com.alquilereventos.common.entity.Proveedor baseEntity(String nombre, String contacto,
                                                                   String telefono, String email,
                                                                   String direccion) {
        com.alquilereventos.common.entity.Proveedor entity =
                new com.alquilereventos.common.entity.Proveedor();
        entity.setNombre(nombre);
        entity.setContacto(contacto);
        entity.setTelefono(telefono);
        entity.setEmail(email);
        entity.setDireccion(direccion);
        return entity;
    }
}
