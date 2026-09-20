package com.alquilereventos.compras.mapper;

import com.alquilereventos.common.dto.EstadoOrdenCompra;
import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraDetalle;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte la entidad OrdenCompra del JAR common &lt;-&gt; los DTOs del JAR.
 *
 * <p>El nombre simple {@code OrdenCompra} es el DTO; las entidades se
 * referencian con su nombre completo para evitar el choque de nombres.
 * Los campos solo-lectura del DTO (id, total, createdAt / id, subtotal)
 * se asignan por constructor.</p>
 */
@Component
public class OrdenCompraMapper
        implements Mapper<com.alquilereventos.common.entity.OrdenCompra, OrdenCompra> {

    @Override
    public OrdenCompra toDto(com.alquilereventos.common.entity.OrdenCompra entity) {
        if (entity == null) {
            return null;
        }
        OffsetDateTime createdAt = entity.getCreatedAt() != null
                ? entity.getCreatedAt().atOffset(OffsetDateTime.now().getOffset())
                : null;
        Double total = entity.getTotal() != null ? entity.getTotal().doubleValue() : null;

        OrdenCompra dto = new OrdenCompra(entity.getId(), total, createdAt);
        if (entity.getProveedor() != null) {
            dto.setProveedorId(entity.getProveedor().getId());
        }
        if (entity.getUsuario() != null) {
            dto.setUsuarioId(entity.getUsuario().getId());
        }
        dto.setFechaCompra(entity.getFechaCompra());
        if (entity.getEstado() != null) {
            dto.setEstado(EstadoOrdenCompra.valueOf(entity.getEstado().name()));
        }
        if (entity.getDetalles() != null) {
            List<OrdenCompraDetalle> detallesDto = new ArrayList<>();
            for (com.alquilereventos.common.entity.OrdenCompraDetalle detalle : entity.getDetalles()) {
                detallesDto.add(toDetalleDto(detalle));
            }
            dto.setDetalles(detallesDto);
        }
        return dto;
    }

    @Override
    public com.alquilereventos.common.entity.OrdenCompra toEntity(OrdenCompra dto) {
        if (dto == null) {
            return null;
        }
        // Mapea los campos escalares; las referencias (proveedor, usuario, equipos)
        // las resuelve OrdenCompraService porque requiere validarlas en la BD.
        com.alquilereventos.common.entity.OrdenCompra entity =
                new com.alquilereventos.common.entity.OrdenCompra();
        entity.setFechaCompra(dto.getFechaCompra());
        if (dto.getEstado() != null) {
            entity.setEstado(com.alquilereventos.common.entity.enums.EstadoOrdenCompra
                    .valueOf(dto.getEstado().name()));
        }
        return entity;
    }

    public OrdenCompraDetalle toDetalleDto(com.alquilereventos.common.entity.OrdenCompraDetalle detalle) {
        Double subtotal = detalle.getSubtotal() != null ? detalle.getSubtotal().doubleValue() : null;
        OrdenCompraDetalle dto = new OrdenCompraDetalle(detalle.getId(), subtotal);
        if (detalle.getEquipo() != null) {
            dto.setEquipoId(detalle.getEquipo().getId());
        }
        dto.setCantidad(detalle.getCantidad());
        if (detalle.getCostoUnitario() != null) {
            dto.setCostoUnitario(detalle.getCostoUnitario().doubleValue());
        }
        return dto;
    }
}
