package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.PageProveedor;
import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;

/**
 * Logica de negocio de proveedores.
 */
public interface ProveedorService {

    Proveedor crear(ProveedorCrearRequest request);

    Proveedor actualizar(Integer id, ProveedorCrearRequest request);

    Proveedor obtenerPorId(Integer id);

    PageProveedor listar(Integer page, Integer size);

    PageProveedor buscar(String nombre, Integer page, Integer size);

    Proveedor eliminar(Integer id);
}
