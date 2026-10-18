package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraCrearRequest;
import com.alquilereventos.common.dto.OrdenCompraDetalle;
import com.alquilereventos.common.dto.OrdenCompraEstadoRequest;
import com.alquilereventos.common.dto.PageOrdenCompra;
import com.alquilereventos.common.entity.Equipo;
import com.alquilereventos.common.entity.OrdenCompraHistorial;
import com.alquilereventos.common.entity.Usuario;
import com.alquilereventos.common.entity.enums.EstadoOrdenCompra;
import com.alquilereventos.common.repository.EquipoRepository;
import com.alquilereventos.common.repository.OrdenCompraHistorialRepository;
import com.alquilereventos.common.repository.OrdenCompraRepository;
import com.alquilereventos.common.repository.ProveedorRepository;
import com.alquilereventos.common.repository.UsuarioRepository;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.config.cache.CacheKeyFactory;
import com.alquilereventos.compras.mapper.OrdenCompraMapper;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Logica de negocio de ordenes de compra.
 *
 * <p>Reglas: el servidor calcula subtotales y total (el cliente no los manda);
 * estado inicial PENDIENTE; transiciones PENDIENTE-&gt;CONFIRMADA,
 * CONFIRMADA-&gt;RECIBIDA (suma stock) y PENDIENTE-&gt;ANULADA (borrado logico);
 * todo cambio de estado queda en el historial (herencia de HistorialEstado).</p>
 */
@Service
public class OrdenCompraServiceImpl implements OrdenCompraService {

    private static final Logger log = LoggerFactory.getLogger(OrdenCompraServiceImpl.class);

    private final OrdenCompraRepository ordenCompraRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final EquipoRepository equipoRepository;
    private final OrdenCompraHistorialRepository historialRepository;
    private final OrdenCompraMapper ordenCompraMapper;
    private final CacheManager cacheManager;
    private final CacheKeyFactory cacheKeys;
    private final String ordenesCompraCacheName;

    @Autowired
    public OrdenCompraServiceImpl(OrdenCompraRepository ordenCompraRepository,
                                  ProveedorRepository proveedorRepository,
                                  UsuarioRepository usuarioRepository,
                                  EquipoRepository equipoRepository,
                                  OrdenCompraHistorialRepository historialRepository,
                                  OrdenCompraMapper ordenCompraMapper,
                                  CacheManager cacheManager,
                                  CacheKeyFactory cacheKeys,
                                  @Value("${app.cache.domains.ordenes-compra.name}") String ordenesCompraCacheName) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.equipoRepository = equipoRepository;
        this.historialRepository = historialRepository;
        this.ordenCompraMapper = ordenCompraMapper;
        this.cacheManager = cacheManager;
        this.cacheKeys = cacheKeys;
        this.ordenesCompraCacheName = ordenesCompraCacheName;
    }

    /** Constructor conservado para pruebas unitarias sin infraestructura Redis. */
    public OrdenCompraServiceImpl(OrdenCompraRepository ordenCompraRepository,
                                  ProveedorRepository proveedorRepository,
                                  UsuarioRepository usuarioRepository,
                                  EquipoRepository equipoRepository,
                                  OrdenCompraHistorialRepository historialRepository,
                                  OrdenCompraMapper ordenCompraMapper) {
        this(ordenCompraRepository, proveedorRepository, usuarioRepository, equipoRepository,
                historialRepository, ordenCompraMapper, null, null, null);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = "${app.cache.domains.ordenes-compra.name}", key = "@cacheKeys.ordenCompra(#result.id)")
    public OrdenCompra crear(OrdenCompraCrearRequest request, Integer usuarioActorId) {
        validarRequest(request);
        log.info("Creando orden de compra para proveedor {}", request.getProveedorId());
        validarDetalles(request.getDetalles());

        com.alquilereventos.common.entity.Proveedor proveedor =
                proveedorRepository.findById(request.getProveedorId())
                        .filter(com.alquilereventos.common.entity.Proveedor::getActivo)
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Proveedor no encontrado o inactivo: " + request.getProveedorId()));

        Usuario usuario = obtenerUsuarioActivo(usuarioActorId);

        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setProveedor(proveedor);
        orden.setUsuario(usuario);
        orden.setFechaCompra(request.getFechaCompra() != null ? request.getFechaCompra() : LocalDate.now());
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        orden.setTotal(BigDecimal.ZERO);
        orden.setDetalles(new ArrayList<>());

        orden = cargarDetallesYTotal(orden, request.getDetalles());
        orden = ordenCompraRepository.save(orden);
        registrarHistorial(orden, usuario, EstadoOrdenCompra.PENDIENTE, EstadoOrdenCompra.PENDIENTE);
        log.debug("Orden de compra {} creada con total {}", orden.getId(), orden.getTotal());
        return ordenCompraMapper.toDto(orden);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = "${app.cache.domains.ordenes-compra.name}", key = "@cacheKeys.ordenCompra(#id)")
    public OrdenCompra actualizar(Integer id, OrdenCompraCrearRequest request, Integer usuarioActorId) {
        validarRequest(request);
        log.info("Actualizando orden de compra {}", id);
        com.alquilereventos.common.entity.OrdenCompra orden = obtenerEntidad(id);
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            throw new ReglaNegocioException("Solo se puede modificar una orden en estado PENDIENTE");
        }
        obtenerUsuarioActivo(usuarioActorId);
        validarDetalles(request.getDetalles());

        com.alquilereventos.common.entity.Proveedor proveedor =
                proveedorRepository.findById(request.getProveedorId())
                        .filter(com.alquilereventos.common.entity.Proveedor::getActivo)
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Proveedor no encontrado o inactivo: " + request.getProveedorId()));
        orden.setProveedor(proveedor);
        if (request.getFechaCompra() != null) {
            orden.setFechaCompra(request.getFechaCompra());
        }

        // Reemplazo completo de los detalles (cascade ALL + orphanRemoval).
        orden.getDetalles().clear();
        orden = cargarDetallesYTotal(orden, request.getDetalles());
        return ordenCompraMapper.toDto(ordenCompraRepository.save(orden));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "${app.cache.domains.ordenes-compra.name}", key = "@cacheKeys.ordenCompra(#id)")
    public OrdenCompra obtenerPorId(Integer id) {
        log.debug("Buscando orden de compra {}", id);
        return ordenCompraMapper.toDto(obtenerEntidad(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageOrdenCompra listar(Integer page, Integer size) {
        log.info("Listando ordenes de compra pagina {} tamanio {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<com.alquilereventos.common.entity.OrdenCompra> pagina =
                ordenCompraRepository.findAll(pageable);
        PageOrdenCompra dto = new PageOrdenCompra();
        var contenido = pagina.map(ordenCompraMapper::toDto).getContent();
        calentarOrdenes(contenido);
        dto.setContenido(contenido);
        dto.setTotalElementos(pagina.getTotalElements());
        dto.setNumeroPagina(pagina.getNumber());
        dto.setTamanoPagina(pagina.getSize());
        return dto;
    }

    @Override
    @Transactional
    @CachePut(cacheNames = "${app.cache.domains.ordenes-compra.name}", key = "@cacheKeys.ordenCompra(#id)")
    public OrdenCompra cambiarEstado(Integer id, OrdenCompraEstadoRequest request, Integer usuarioActorId) {
        if (request == null || request.getEstado() == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio");
        }
        log.info("Cambiando estado de orden {} a {}", id, request.getEstado());
        Usuario usuarioActor = obtenerUsuarioActivo(usuarioActorId);
        com.alquilereventos.common.entity.OrdenCompra orden = obtenerEntidad(id);
        EstadoOrdenCompra actual = orden.getEstado();
        EstadoOrdenCompra nuevo = EstadoOrdenCompra.valueOf(request.getEstado().name());

        boolean transicionValida =
                (actual == EstadoOrdenCompra.PENDIENTE && nuevo == EstadoOrdenCompra.CONFIRMADA)
                        || (actual == EstadoOrdenCompra.PENDIENTE && nuevo == EstadoOrdenCompra.ANULADA)
                        || (actual == EstadoOrdenCompra.CONFIRMADA && nuevo == EstadoOrdenCompra.RECIBIDA);
        if (!transicionValida) {
            throw new ReglaNegocioException("Transicion de estado no permitida: " + actual + " -> " + nuevo);
        }

        if (nuevo == EstadoOrdenCompra.RECIBIDA) {
            sumarStock(orden);
        }

        orden.setEstado(nuevo);
        orden = ordenCompraRepository.save(orden);
        registrarHistorial(orden, usuarioActor, actual, nuevo);
        return ordenCompraMapper.toDto(orden);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "${app.cache.domains.ordenes-compra.name}", key = "@cacheKeys.ordenCompra(#id)")
    public OrdenCompra eliminar(Integer id, Integer usuarioActorId) {
        log.info("Borrado logico (ANULADA) de orden {}", id);
        Usuario usuarioActor = obtenerUsuarioActivo(usuarioActorId);
        com.alquilereventos.common.entity.OrdenCompra orden = obtenerEntidad(id);
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            throw new ReglaNegocioException("Solo se puede anular una orden en estado PENDIENTE");
        }
        EstadoOrdenCompra actual = orden.getEstado();
        orden.setEstado(EstadoOrdenCompra.ANULADA);
        orden = ordenCompraRepository.save(orden);
        registrarHistorial(orden, usuarioActor, actual, EstadoOrdenCompra.ANULADA);
        return ordenCompraMapper.toDto(orden);
    }

    private void validarRequest(OrdenCompraCrearRequest request) {
        if (request == null || request.getProveedorId() == null) {
            throw new ReglaNegocioException("Proveedor y detalles son obligatorios");
        }
    }

    private void validarDetalles(List<OrdenCompraDetalle> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            throw new ReglaNegocioException("La orden debe tener al menos un detalle");
        }
        for (OrdenCompraDetalle detalle : detalles) {
            if (detalle.getEquipoId() == null) {
                throw new ReglaNegocioException("Cada detalle debe indicar un equipo");
            }
            if (detalle.getCantidad() == null || detalle.getCantidad() < 1) {
                throw new ReglaNegocioException("La cantidad de cada detalle debe ser mayor a cero");
            }
            if (detalle.getCostoUnitario() == null || detalle.getCostoUnitario() <= 0) {
                throw new ReglaNegocioException("El costo unitario de cada detalle debe ser mayor a cero");
            }
        }
    }

    private com.alquilereventos.common.entity.OrdenCompra cargarDetallesYTotal(
            com.alquilereventos.common.entity.OrdenCompra orden,
            List<OrdenCompraDetalle> detallesDto) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrdenCompraDetalle detalleDto : detallesDto) {
            Equipo equipo = equipoRepository.findById(detalleDto.getEquipoId())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "Equipo no encontrado: " + detalleDto.getEquipoId()));
            BigDecimal costoUnitario = BigDecimal.valueOf(detalleDto.getCostoUnitario());
            BigDecimal subtotal = costoUnitario.multiply(BigDecimal.valueOf(detalleDto.getCantidad()));

            com.alquilereventos.common.entity.OrdenCompraDetalle detalle =
                    new com.alquilereventos.common.entity.OrdenCompraDetalle();
            detalle.setOrdenCompra(orden);
            detalle.setEquipo(equipo);
            detalle.setCantidad(detalleDto.getCantidad());
            detalle.setCostoUnitario(costoUnitario);
            detalle.setSubtotal(subtotal);

            orden.getDetalles().add(detalle);
            total = total.add(subtotal);
        }
        orden.setTotal(total);
        return orden;
    }

    private void sumarStock(com.alquilereventos.common.entity.OrdenCompra orden) {
        for (com.alquilereventos.common.entity.OrdenCompraDetalle detalle : orden.getDetalles()) {
            Equipo equipo = detalle.getEquipo();
            equipo.setStock(equipo.getStock() + detalle.getCantidad());
            equipoRepository.save(equipo);
            log.debug("Stock sumado al equipo {}: +{}", equipo.getId(), detalle.getCantidad());
        }
    }

    private void registrarHistorial(com.alquilereventos.common.entity.OrdenCompra orden,
                                    Usuario usuarioActor,
                                    EstadoOrdenCompra estadoAnterior,
                                    EstadoOrdenCompra estadoNuevo) {
        OrdenCompraHistorial registro = new OrdenCompraHistorial();
        registro.setOrdenCompra(orden);
        registro.setUsuario(usuarioActor);
        registro.setEstadoAnterior(estadoAnterior.name());
        registro.setEstadoNuevo(estadoNuevo.name());
        historialRepository.save(registro);
        log.debug("Historial registrado: {} -> {}", registro.getEstadoAnterior(), registro.getEstadoNuevo());
    }

    private com.alquilereventos.common.entity.OrdenCompra obtenerEntidad(Integer id) {
        return ordenCompraRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden de compra no encontrada: " + id));
    }

    private Usuario obtenerUsuarioActivo(Integer usuarioId) {
        if (usuarioId == null) {
            throw new ReglaNegocioException("El usuario autenticado es obligatorio");
        }
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .orElseThrow(() -> new ReglaNegocioException("Usuario no encontrado o inactivo: " + usuarioId));
    }

    /** No se guarda la pagina; se cargan individualmente las ordenes obtenidas. */
    private void calentarOrdenes(List<OrdenCompra> ordenes) {
        if (cacheManager == null || cacheKeys == null) {
            return;
        }
        Cache cache = cacheManager.getCache(ordenesCompraCacheName);
        if (cache != null) {
            ordenes.forEach(orden -> cache.put(cacheKeys.ordenCompra(orden.getId()), orden));
        }
    }
}
