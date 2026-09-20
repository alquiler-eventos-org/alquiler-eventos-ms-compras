package com.alquilereventos.compras.mapper;

import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraDetalle;
import com.alquilereventos.common.entity.enums.EstadoOrdenCompra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prueba el mapeo Entidad &lt;-&gt; DTO de OrdenCompra y sus detalles.
 */
class OrdenCompraMapperTest {

    private final OrdenCompraMapper mapper = new OrdenCompraMapper();

    @Test
    @DisplayName("toDto: mapea orden con detalles, total y estado")
    void toDtoConDetalles() {
        com.alquilereventos.common.entity.Proveedor proveedor =
                new com.alquilereventos.common.entity.Proveedor();
        proveedor.setId(3);

        com.alquilereventos.common.entity.Usuario usuario =
                new com.alquilereventos.common.entity.Usuario();
        usuario.setId(5);

        com.alquilereventos.common.entity.Equipo equipo =
                new com.alquilereventos.common.entity.Equipo();
        equipo.setId(11);

        com.alquilereventos.common.entity.OrdenCompraDetalle detalle =
                new com.alquilereventos.common.entity.OrdenCompraDetalle();
        detalle.setId(9);
        detalle.setEquipo(equipo);
        detalle.setCantidad(4);
        detalle.setCostoUnitario(new BigDecimal("150.50"));
        detalle.setSubtotal(new BigDecimal("602.00"));

        com.alquilereventos.common.entity.OrdenCompra entity =
                new com.alquilereventos.common.entity.OrdenCompra();
        entity.setId(2);
        entity.setProveedor(proveedor);
        entity.setUsuario(usuario);
        entity.setFechaCompra(LocalDate.of(2026, 9, 20));
        entity.setEstado(EstadoOrdenCompra.PENDIENTE);
        entity.setTotal(new BigDecimal("602.00"));
        entity.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 30));
        entity.setDetalles(List.of(detalle));

        OrdenCompra dto = mapper.toDto(entity);

        assertEquals(2, dto.getId());
        assertEquals(3, dto.getProveedorId());
        assertEquals(5, dto.getUsuarioId());
        assertEquals(LocalDate.of(2026, 9, 20), dto.getFechaCompra());
        assertEquals(com.alquilereventos.common.dto.EstadoOrdenCompra.PENDIENTE, dto.getEstado());
        assertEquals(602.00, dto.getTotal());
        assertEquals(1, dto.getDetalles().size());

        OrdenCompraDetalle detalleDto = dto.getDetalles().get(0);
        assertEquals(9, detalleDto.getId());
        assertEquals(11, detalleDto.getEquipoId());
        assertEquals(4, detalleDto.getCantidad());
        assertEquals(150.50, detalleDto.getCostoUnitario());
        assertEquals(602.00, detalleDto.getSubtotal());
    }

    @Test
    @DisplayName("toEntity: mapea fecha y estado, sin referencias (las resuelve el service)")
    void toEntityCamposEscalares() {
        OrdenCompra dto = new OrdenCompra();
        dto.setFechaCompra(LocalDate.of(2026, 9, 1));
        dto.setEstado(com.alquilereventos.common.dto.EstadoOrdenCompra.CONFIRMADA);

        com.alquilereventos.common.entity.OrdenCompra entity = mapper.toEntity(dto);

        assertEquals(LocalDate.of(2026, 9, 1), entity.getFechaCompra());
        assertEquals(EstadoOrdenCompra.CONFIRMADA, entity.getEstado());
    }

    @Test
    @DisplayName("toDto(null) devuelve null sin lanzar excepcion")
    void manejaNulos() {
        assertNull(mapper.toDto(null));
    }
}
