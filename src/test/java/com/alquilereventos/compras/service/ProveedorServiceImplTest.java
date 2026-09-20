package com.alquilereventos.compras.service;

import com.alquilereventos.common.dto.PageProveedor;
import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.mapper.ProveedorMapper;
import com.alquilereventos.compras.repository.ProveedorBusquedaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la logica de negocio de proveedores (con Mockito).
 */
class ProveedorServiceImplTest {

    private ProveedorBusquedaRepository proveedorRepository;
    private ProveedorServiceImpl service;

    @BeforeEach
    void setUp() {
        proveedorRepository = mock(ProveedorBusquedaRepository.class);
        service = new ProveedorServiceImpl(proveedorRepository, new ProveedorMapper());
    }

    private ProveedorCrearRequest request(String nombre) {
        ProveedorCrearRequest request = new ProveedorCrearRequest();
        request.setNombre(nombre);
        request.setContacto("Ana Lopez");
        request.setTelefono("555-1234");
        request.setEmail("ana@sony.com");
        request.setDireccion("Av. Reforma 100");
        return request;
    }

    private com.alquilereventos.common.entity.Proveedor entidad(int id, String nombre, boolean activo) {
        com.alquilereventos.common.entity.Proveedor entity =
                new com.alquilereventos.common.entity.Proveedor();
        entity.setId(id);
        entity.setActivo(activo);
        entity.setNombre(nombre);
        return entity;
    }

    @Test
    @DisplayName("crear: guarda el proveedor como activo y devuelve el DTO")
    void crearMarcaActivo() {
        when(proveedorRepository.save(any())).thenAnswer(inv -> {
            com.alquilereventos.common.entity.Proveedor p = inv.getArgument(0);
            p.setId(1);
            return p;
        });

        Proveedor dto = service.crear(request("Sony"));

        assertEquals(1, dto.getId());
        assertEquals("Sony", dto.getNombre());
        assertEquals(Boolean.TRUE, dto.getActivo());
        verify(proveedorRepository).save(any());
    }

    @Test
    @DisplayName("crear: nombre vacio lanza ReglaNegocioException y no toca la BD")
    void crearNombreVacio() {
        assertThrows(ReglaNegocioException.class, () -> service.crear(request("  ")));
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: modifica los campos del proveedor existente")
    void actualizarCampos() {
        com.alquilereventos.common.entity.Proveedor entity = entidad(1, "Sony", true);
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(entity));
        when(proveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Proveedor dto = service.actualizar(1, request("Sony MX"));

        assertEquals("Sony MX", dto.getNombre());
        assertEquals(Boolean.TRUE, dto.getActivo());
        verify(proveedorRepository).save(any());
    }

    @Test
    @DisplayName("actualizar: id inexistente lanza RecursoNoEncontradoException (404)")
    void actualizarNoEncontrado() {
        when(proveedorRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(99, request("X")));
    }

    @Test
    @DisplayName("actualizar: proveedor con borrado logico se trata como inexistente")
    void actualizarInactivoEs404() {
        when(proveedorRepository.findById(2))
                .thenReturn(Optional.of(entidad(2, "Baja", false)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(2, request("X")));
    }

    @Test
    @DisplayName("obtenerPorId: devuelve el DTO cuando existe y esta activo")
    void obtenerPorId() {
        when(proveedorRepository.findById(1))
                .thenReturn(Optional.of(entidad(1, "Sony", true)));
        assertEquals("Sony", service.obtenerPorId(1).getNombre());
    }

    @Test
    @DisplayName("obtenerPorId: inexistente lanza RecursoNoEncontradoException")
    void obtenerPorIdNoEncontrado() {
        when(proveedorRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.obtenerPorId(99));
    }

    @Test
    @DisplayName("listar: devuelve pagina de proveedores activos")
    void listarPagina() {
        Pageable pageable = PageRequest.of(0, 10);
        when(proveedorRepository.buscar("", pageable))
                .thenReturn(new PageImpl<>(List.of(entidad(1, "Sony", true)), pageable, 1));

        PageProveedor pagina = service.listar(0, 10);

        assertEquals(1, pagina.getContenido().size());
        assertEquals(1L, pagina.getTotalElementos());
        assertEquals(0, pagina.getNumeroPagina());
        assertEquals(10, pagina.getTamanoPagina());
    }

    @Test
    @DisplayName("buscar: pasa el termino en minusculas- LIKE al repositorio")
    void buscarPorNombre() {
        when(proveedorRepository.buscar("sony", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(entidad(1, "Sony", true))));

        PageProveedor pagina = service.buscar("sony", 0, 10);

        assertEquals(1, pagina.getContenido().size());
        assertTrue(pagina.getContenido().get(0).getNombre().contains("Sony"));
    }

    @Test
    @DisplayName("buscar: termino null se trata como cadena vacia (listar todo)")
    void buscarTerminoNull() {
        when(proveedorRepository.buscar("", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));
        PageProveedor pagina = service.buscar(null, 0, 10);
        assertEquals(0, pagina.getContenido().size());
    }

    @Test
    @DisplayName("eliminar: borrado logico (activo=false), nunca delete fisico")
    void eliminarBorradoLogico() {
        com.alquilereventos.common.entity.Proveedor entity = entidad(1, "Sony", true);
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(entity));
        when(proveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Proveedor dto = service.eliminar(1);

        assertFalse(dto.getActivo());
        verify(proveedorRepository).save(any());
        verify(proveedorRepository, never()).delete(any());
    }
}
