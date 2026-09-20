package com.alquilereventos.compras.mapper;

import com.alquilereventos.common.dto.Proveedor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prueba el mapeo bidireccional Entidad &lt;-&gt; DTO de Proveedor.
 */
class ProveedorMapperTest {

    private final ProveedorMapper mapper = new ProveedorMapper();

    @Test
    @DisplayName("toDto: copia todos los campos de la entidad al DTO")
    void toDtoCopiaTodosLosCampos() {
        com.alquilereventos.common.entity.Proveedor entity =
                new com.alquilereventos.common.entity.Proveedor();
        entity.setId(7);
        entity.setActivo(Boolean.TRUE);
        entity.setNombre("Sony");
        entity.setContacto("Ana Lopez");
        entity.setTelefono("555-1234");
        entity.setEmail("ventas@sony.com");
        entity.setDireccion("Av. Reforma 100");

        Proveedor dto = mapper.toDto(entity);

        assertEquals(7, dto.getId());
        assertEquals(Boolean.TRUE, dto.getActivo());
        assertEquals("Sony", dto.getNombre());
        assertEquals("Ana Lopez", dto.getContacto());
        assertEquals("555-1234", dto.getTelefono());
        assertEquals("ventas@sony.com", dto.getEmail());
        assertEquals("Av. Reforma 100", dto.getDireccion());
    }

    @Test
    @DisplayName("toEntity(request): copia los campos del request, sin id ni activo")
    void toEntityDesdeRequest() {
        com.alquilereventos.common.dto.ProveedorCrearRequest request =
                new com.alquilereventos.common.dto.ProveedorCrearRequest();
        request.setNombre("Bose");
        request.setContacto("Luis Paz");
        request.setTelefono("555-9876");
        request.setEmail("contacto@bose.com");
        request.setDireccion("Calle 5 #20");

        com.alquilereventos.common.entity.Proveedor entity = mapper.toEntity(request);

        assertEquals("Bose", entity.getNombre());
        assertEquals("Luis Paz", entity.getContacto());
        assertEquals("555-9876", entity.getTelefono());
        assertEquals("contacto@bose.com", entity.getEmail());
        assertEquals("Calle 5 #20", entity.getDireccion());
    }

    @Test
    @DisplayName("toDto(null) y toEntity(null) devuelven null sin lanzar excepcion")
    void manejaNulos() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity((Proveedor) null));
    }
}
