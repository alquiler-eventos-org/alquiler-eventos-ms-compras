package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraCrearRequest;
import com.alquilereventos.common.dto.OrdenCompraEstadoRequest;
import com.alquilereventos.common.dto.PageOrdenCompra;

/**
 * Logica de negocio de ordenes de compra (detalles, estados, historial y stock).
 */
public interface OrdenCompraService {

    OrdenCompra crear(OrdenCompraCrearRequest request);

    OrdenCompra actualizar(Integer id, OrdenCompraCrearRequest request);

    OrdenCompra obtenerPorId(Integer id);

    PageOrdenCompra listar(Integer page, Integer size);

    OrdenCompra cambiarEstado(Integer id, OrdenCompraEstadoRequest request);

    OrdenCompra eliminar(Integer id);
}
