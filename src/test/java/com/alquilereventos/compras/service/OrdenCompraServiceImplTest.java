package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraCrearRequest;
import com.alquilereventos.common.dto.OrdenCompraDetalle;
import com.alquilereventos.common.dto.OrdenCompraEstadoRequest;
import com.alquilereventos.common.dto.PageOrdenCompra;
import com.alquilereventos.common.entity.enums.EstadoOrdenCompra;
import com.alquilereventos.common.repository.EquipoRepository;
import com.alquilereventos.common.repository.OrdenCompraHistorialRepository;
import com.alquilereventos.common.repository.OrdenCompraRepository;
import com.alquilereventos.common.repository.ProveedorRepository;
import com.alquilereventos.common.repository.UsuarioRepository;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.mapper.OrdenCompraMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la logica de ordenes de compra: calculo de totales,
 * transiciones de estado, historial y suma de stock.
 */
class OrdenCompraServiceImplTest {

    private OrdenCompraRepository ordenCompraRepository;
    private ProveedorRepository proveedorRepository;
    private UsuarioRepository usuarioRepository;
    private EquipoRepository equipoRepository;
    private OrdenCompraHistorialRepository historialRepository;
    private OrdenCompraServiceImpl service;

    @BeforeEach
    void setUp() {
        ordenCompraRepository = mock(OrdenCompraRepository.class);
        proveedorRepository = mock(ProveedorRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        equipoRepository = mock(EquipoRepository.class);
        historialRepository = mock(OrdenCompraHistorialRepository.class);
        service = new OrdenCompraServiceImpl(ordenCompraRepository, proveedorRepository,
                usuarioRepository, equipoRepository, historialRepository, new OrdenCompraMapper());
    }

    // ---------- helpers ----------

    private com.alquilereventos.common.entity.Proveedor proveedor(int id, boolean activo) {
        com.alquilereventos.common.entity.Proveedor p =
                new com.alquilereventos.common.entity.Proveedor();
        p.setId(id);
        p.setActivo(activo);
        p.setNombre("Sony");
        return p;
    }

    private com.alquilereventos.common.entity.Usuario usuario(int id) {
        com.alquilereventos.common.entity.Usuario u =
                new com.alquilereventos.common.entity.Usuario();
        u.setId(id);
        return u;
    }

    private com.alquilereventos.common.entity.Equipo equipo(int id, int stock) {
        com.alquilereventos.common.entity.Equipo e =
                new com.alquilereventos.common.entity.Equipo();
        e.setId(id);
        e.setStock(stock);
        return e;
    }

    private OrdenCompraDetalle detalleDto(Integer equipoId, int cantidad, Double costo) {
        OrdenCompraDetalle detalle = new OrdenCompraDetalle();
        detalle.setEquipoId(equipoId);
        detalle.setCantidad(cantidad);
        detalle.setCostoUnitario(costo);
        return detalle;
    }

    private OrdenCompraCrearRequest request(Integer proveedorId, Integer usuarioId,
                                            List<OrdenCompraDetalle> detalles) {
        OrdenCompraCrearRequest request = new OrdenCompraCrearRequest();
        request.setProveedorId(proveedorId);
        request.setUsuarioId(usuarioId);
        request.setDetalles(detalles);
        return request;
    }

    private void stubsBasicosOk() {
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor(1, true)));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(usuario(2)));
        when(equipoRepository.findById(10)).thenReturn(Optional.of(equipo(10, 5)));
        when(equipoRepository.findById(11)).thenReturn(Optional.of(equipo(11, 0)));
        when(ordenCompraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- crear ----------

    @Test
    @DisplayName("crear: calcula subtotales y total en el servidor y queda PENDIENTE")
    void crearCalculaTotal() {
        stubsBasicosOk();
        OrdenCompraCrearRequest request = request(1, 2, List.of(
                detalleDto(10, 2, 100.50),
                detalleDto(11, 1, 50.00)));

        OrdenCompra dto = service.crear(request);

        assertEquals("PENDIENTE", dto.getEstado().name());
        assertEquals(2, dto.getDetalles().size());
        assertEquals(100.50 * 2, dto.getDetalles().get(0).getSubtotal());
        assertEquals(251.00, dto.getTotal());
        verify(ordenCompraRepository).save(any());
        verify(historialRepository).save(any());
    }

    @Test
    @DisplayName("crear: sin detalles lanza ReglaNegocioException")
    void crearSinDetalles() {
        assertThrows(ReglaNegocioException.class, () -> service.crear(request(1, 2, List.of())));
        verify(ordenCompraRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: cantidad menor a 1 lanza ReglaNegocioException")
    void crearCantidadInvalida() {
        assertThrows(ReglaNegocioException.class, () -> service.crear(
                request(1, 2, List.of(detalleDto(10, 0, 100.0)))));
    }

    @Test
    @DisplayName("crear: costo unitario menor o igual a cero lanza ReglaNegocioException")
    void crearCostoInvalido() {
        assertThrows(ReglaNegocioException.class, () -> service.crear(
                request(1, 2, List.of(detalleDto(10, 1, 0.0)))));
    }

    @Test
    @DisplayName("crear: sin proveedorId lanza ReglaNegocioException")
    void crearSinProveedor() {
        assertThrows(ReglaNegocioException.class,
                () -> service.crear(request(null, 2, List.of(detalleDto(10, 1, 10.0)))));
    }

    @Test
    @DisplayName("crear: proveedor inactivo lanza ReglaNegocioException")
    void crearProveedorInactivo() {
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor(1, false)));
        assertThrows(ReglaNegocioException.class, () -> service.crear(
                request(1, 2, List.of(detalleDto(10, 1, 10.0)))));
    }

    @Test
    @DisplayName("crear: usuario inexistente lanza ReglaNegocioException")
    void crearUsuarioInexistente() {
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor(1, true)));
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ReglaNegocioException.class, () -> service.crear(
                request(1, 99, List.of(detalleDto(10, 1, 10.0)))));
    }

    @Test
    @DisplayName("crear: equipo inexistente lanza ReglaNegocioException")
    void crearEquipoInexistente() {
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor(1, true)));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(usuario(2)));
        when(equipoRepository.findById(999)).thenReturn(Optional.empty());
        assertThrows(ReglaNegocioException.class, () -> service.crear(
                request(1, 2, List.of(detalleDto(999, 1, 10.0)))));
    }

    // ---------- actualizar ----------

    @Test
    @DisplayName("actualizar: orden PENDIENTE reemplaza detalles y recalcula el total")
    void actualizarPendiente() {
        stubsBasicosOk();
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        orden.setDetalles(new ArrayList<>());
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        OrdenCompra dto = service.actualizar(5, request(1, 2, List.of(detalleDto(10, 3, 20.0))));

        assertEquals(60.00, dto.getTotal());
        assertEquals(1, dto.getDetalles().size());
    }

    @Test
    @DisplayName("actualizar: orden CONFIRMADA no se puede modificar (400)")
    void actualizarNoPendiente() {
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.CONFIRMADA);
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(5, request(1, 2, List.of(detalleDto(10, 1, 20.0)))));
    }

    @Test
    @DisplayName("actualizar: id inexistente lanza RecursoNoEncontradoException")
    void actualizarNoEncontrada() {
        when(ordenCompraRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(99, request(1, 2, List.of(detalleDto(10, 1, 20.0)))));
    }

    // ---------- obtener / listar ----------

    @Test
    @DisplayName("obtenerPorId: existe -> DTO; no existe -> 404")
    void obtenerPorId() {
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));
        when(ordenCompraRepository.findById(99)).thenReturn(Optional.empty());

        assertEquals(5, service.obtenerPorId(5).getId());
        assertThrows(RecursoNoEncontradoException.class, () -> service.obtenerPorId(99));
    }

    @Test
    @DisplayName("listar: devuelve la pagina mapeada a DTO")
    void listarPagina() {
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        // El service ordena por id descendente: el stub debe usar el mismo Pageable.
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));
        when(ordenCompraRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(orden), pageable, 1));

        PageOrdenCompra pagina = service.listar(0, 10);

        assertEquals(1, pagina.getContenido().size());
        assertEquals(1L, pagina.getTotalElementos());
    }

    // ---------- cambiarEstado ----------

    @Test
    @DisplayName("cambiarEstado: PENDIENTE -> CONFIRMADA valida y registra historial")
    void pendienteAConfirmada() {
        stubsBasicosOk();
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        orden.setDetalles(new ArrayList<>());
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        OrdenCompraEstadoRequest estadoRequest = new OrdenCompraEstadoRequest();
        estadoRequest.setEstado(com.alquilereventos.common.dto.EstadoOrdenCompra.CONFIRMADA);

        OrdenCompra dto = service.cambiarEstado(5, estadoRequest);

        assertEquals("CONFIRMADA", dto.getEstado().name());
        verify(historialRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("cambiarEstado: CONFIRMADA -> RECIBIDA suma stock a cada equipo")
    void confirmadaARecibidaSumaStock() {
        stubsBasicosOk();
        com.alquilereventos.common.entity.Equipo equipo10 = equipo(10, 5);
        com.alquilereventos.common.entity.Equipo equipo11 = equipo(11, 0);

        com.alquilereventos.common.entity.OrdenCompraDetalle d1 =
                new com.alquilereventos.common.entity.OrdenCompraDetalle();
        d1.setEquipo(equipo10);
        d1.setCantidad(3);
        com.alquilereventos.common.entity.OrdenCompraDetalle d2 =
                new com.alquilereventos.common.entity.OrdenCompraDetalle();
        d2.setEquipo(equipo11);
        d2.setCantidad(2);

        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.CONFIRMADA);
        orden.setDetalles(new ArrayList<>(List.of(d1, d2)));
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        OrdenCompraEstadoRequest estadoRequest = new OrdenCompraEstadoRequest();
        estadoRequest.setEstado(com.alquilereventos.common.dto.EstadoOrdenCompra.RECIBIDA);

        OrdenCompra dto = service.cambiarEstado(5, estadoRequest);

        assertEquals("RECIBIDA", dto.getEstado().name());
        assertEquals(8, equipo10.getStock());
        assertEquals(2, equipo11.getStock());
        verify(equipoRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("cambiarEstado: PENDIENTE -> RECIBIDA no permitida (400)")
    void transicionInvalida() {
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        OrdenCompraEstadoRequest estadoRequest = new OrdenCompraEstadoRequest();
        estadoRequest.setEstado(com.alquilereventos.common.dto.EstadoOrdenCompra.RECIBIDA);

        assertThrows(ReglaNegocioException.class, () -> service.cambiarEstado(5, estadoRequest));
        verify(equipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado: RECIBIDA es estado terminal (400)")
    void recibidaEsTerminal() {
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.RECIBIDA);
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        OrdenCompraEstadoRequest estadoRequest = new OrdenCompraEstadoRequest();
        estadoRequest.setEstado(com.alquilereventos.common.dto.EstadoOrdenCompra.ANULADA);

        assertThrows(ReglaNegocioException.class, () -> service.cambiarEstado(5, estadoRequest));
    }

    @Test
    @DisplayName("cambiarEstado: sin estado destino lanza ReglaNegocioException")
    void estadoNull() {
        assertThrows(ReglaNegocioException.class,
                () -> service.cambiarEstado(5, new OrdenCompraEstadoRequest()));
    }

    // ---------- eliminar (ANULADA) ----------

    @Test
    @DisplayName("eliminar: PENDIENTE -> ANULADA (borrado logico) con historial")
    void eliminarPendiente() {
        stubsBasicosOk();
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);
        orden.setDetalles(new ArrayList<>());
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        OrdenCompra dto = service.eliminar(5);

        assertEquals("ANULADA", dto.getEstado().name());
        verify(ordenCompraRepository, never()).delete(any());
        verify(historialRepository).save(any());
    }

    @Test
    @DisplayName("eliminar: orden CONFIRMADA no se puede anular (400)")
    void eliminarNoPendiente() {
        com.alquilereventos.common.entity.OrdenCompra orden =
                new com.alquilereventos.common.entity.OrdenCompra();
        orden.setId(5);
        orden.setEstado(EstadoOrdenCompra.CONFIRMADA);
        when(ordenCompraRepository.findById(5)).thenReturn(Optional.of(orden));

        assertThrows(ReglaNegocioException.class, () -> service.eliminar(5));
    }
}
