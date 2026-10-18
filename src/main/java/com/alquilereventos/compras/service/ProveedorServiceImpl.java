package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.PageProveedor;
import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.config.cache.CacheKeyFactory;
import com.alquilereventos.compras.mapper.ProveedorMapper;
import com.alquilereventos.compras.repository.ProveedorBusquedaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
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
    private final CacheManager cacheManager;
    private final CacheKeyFactory cacheKeys;
    private final String proveedoresCacheName;

    @Autowired
    public ProveedorServiceImpl(ProveedorBusquedaRepository proveedorRepository,
                                ProveedorMapper proveedorMapper,
                                CacheManager cacheManager,
                                CacheKeyFactory cacheKeys,
                                @Value("${app.cache.domains.proveedores.name}") String proveedoresCacheName) {
        this.proveedorRepository = proveedorRepository;
        this.proveedorMapper = proveedorMapper;
        this.cacheManager = cacheManager;
        this.cacheKeys = cacheKeys;
        this.proveedoresCacheName = proveedoresCacheName;
    }

    /** Constructor conservado para pruebas unitarias sin infraestructura Redis. */
    public ProveedorServiceImpl(ProveedorBusquedaRepository proveedorRepository,
                                ProveedorMapper proveedorMapper) {
        this(proveedorRepository, proveedorMapper, null, null, null);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = "${app.cache.domains.proveedores.name}", key = "@cacheKeys.proveedor(#result.id)")
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
    @CachePut(cacheNames = "${app.cache.domains.proveedores.name}", key = "@cacheKeys.proveedor(#id)")
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
    @Cacheable(cacheNames = "${app.cache.domains.proveedores.name}", key = "@cacheKeys.proveedor(#id)")
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
    @CacheEvict(cacheNames = "${app.cache.domains.proveedores.name}", key = "@cacheKeys.proveedor(#id)")
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
        var contenido = pagina.map(proveedorMapper::toDto).getContent();
        calentarProveedores(contenido);
        PageProveedor dto = new PageProveedor();
        dto.setContenido(contenido);
        dto.setTotalElementos(pagina.getTotalElements());
        dto.setNumeroPagina(pagina.getNumber());
        dto.setTamanoPagina(pagina.getSize());
        return dto;
    }

    /** No cachea la pagina: solamente sus objetos individuales ya procesados. */
    private void calentarProveedores(java.util.List<Proveedor> proveedores) {
        if (cacheManager == null || cacheKeys == null) {
            return;
        }
        Cache cache = cacheManager.getCache(proveedoresCacheName);
        if (cache != null) {
            proveedores.forEach(proveedor -> cache.put(cacheKeys.proveedor(proveedor.getId()), proveedor));
        }
    }
}
