package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.PageProveedor;
import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.mapper.ProveedorMapper;
import com.alquilereventos.compras.repository.ProveedorBusquedaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacion de la logica de negocio de proveedores.
 *
 * <p>El nombre simple {@code Proveedor} es el DTO del JAR; la entidad se
 * referencia con su nombre completo para evitar el choque de nombres.</p>
 */
@Service
public class ProveedorServiceImpl implements ProveedorService {

    private static final Logger log = LoggerFactory.getLogger(ProveedorServiceImpl.class);

    private final ProveedorBusquedaRepository proveedorRepository;
    private final ProveedorMapper proveedorMapper;

    public ProveedorServiceImpl(ProveedorBusquedaRepository proveedorRepository,
                                ProveedorMapper proveedorMapper) {
        this.proveedorRepository = proveedorRepository;
        this.proveedorMapper = proveedorMapper;
    }

    @Override
    @Transactional
    public Proveedor crear(ProveedorCrearRequest request) {
        log.info("Creando proveedor: {}", request.getNombre());
        validar(request);
        com.alquilereventos.common.entity.Proveedor entity = proveedorMapper.toEntity(request);
        entity.setActivo(Boolean.TRUE);
        entity = proveedorRepository.save(entity);
        log.debug("Proveedor creado con id {}", entity.getId());
        return proveedorMapper.toDto(entity);
    }

    @Override
    @Transactional
    public Proveedor actualizar(Integer id, ProveedorCrearRequest request) {
        log.info("Actualizando proveedor id {}", id);
        validar(request);
        com.alquilereventos.common.entity.Proveedor entity = obtenerEntidadActiva(id);
        entity.setNombre(request.getNombre());
        entity.setContacto(request.getContacto());
        entity.setTelefono(request.getTelefono());
        entity.setEmail(request.getEmail());
        entity.setDireccion(request.getDireccion());
        return proveedorMapper.toDto(proveedorRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Proveedor obtenerPorId(Integer id) {
        log.debug("Buscando proveedor id {}", id);
        return proveedorMapper.toDto(obtenerEntidadActiva(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageProveedor listar(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        log.debug("Listando proveedores pagina {} tamanio {}", page, size);
        return aPageDto(proveedorRepository.buscar("", pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageProveedor buscar(String nombre, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        String termino = nombre == null ? "" : nombre.trim();
        log.info("Buscando proveedores por nombre='{}'", termino);
        return aPageDto(proveedorRepository.buscar(termino, pageable));
    }

    @Override
    @Transactional
    public Proveedor eliminar(Integer id) {
        log.info("Borrado logico de proveedor id {}", id);
        com.alquilereventos.common.entity.Proveedor entity = obtenerEntidadActiva(id);
        entity.setActivo(Boolean.FALSE);
        entity = proveedorRepository.save(entity);
        log.debug("Proveedor id {} marcado inactivo", id);
        return proveedorMapper.toDto(entity);
    }

    private void validar(ProveedorCrearRequest request) {
        if (request == null || request.getNombre() == null || request.getNombre().isBlank()) {
            throw new ReglaNegocioException("El nombre del proveedor es obligatorio");
        }
    }

    private com.alquilereventos.common.entity.Proveedor obtenerEntidadActiva(Integer id) {
        return proveedorRepository.findById(id)
                .filter(com.alquilereventos.common.entity.Proveedor::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado: " + id));
    }

    private PageProveedor aPageDto(Page<com.alquilereventos.common.entity.Proveedor> pagina) {
        PageProveedor dto = new PageProveedor();
        dto.setContenido(pagina.map(proveedorMapper::toDto).getContent());
        dto.setTotalElementos(pagina.getTotalElements());
        dto.setNumeroPagina(pagina.getNumber());
        dto.setTamanoPagina(pagina.getSize());
        return dto;
    }
}
