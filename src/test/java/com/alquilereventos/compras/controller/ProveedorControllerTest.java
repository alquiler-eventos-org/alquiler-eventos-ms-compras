package com.alquilereventos.compras.controller;

import com.alquilereventos.common.dto.PageProveedor;
import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;
import com.alquilereventos.compras.exception.ApiExceptionHandler;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.service.ProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la capa web de proveedores con MockMvc: codigos HTTP, JSON de
 * respuesta y el manejo centralizado de errores (ApiError).
 */
class ProveedorControllerTest {

    private ProveedorService proveedorService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        proveedorService = mock(ProveedorService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProveedorController(proveedorService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private Proveedor dto(int id, String nombre) {
        Proveedor dto = new Proveedor(id, Boolean.TRUE);
        dto.setNombre(nombre);
        return dto;
    }

    @Test
    @DisplayName("POST /proveedores devuelve 201 con el proveedor creado")
    void crearDevuelve201() throws Exception {
        when(proveedorService.crear(any())).thenReturn(dto(1, "Sony"));

        mockMvc.perform(post("/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sony\",\"email\":\"a@b.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Sony"));
    }

    @Test
    @DisplayName("POST /proveedores sin nombre devuelve 400 con ApiError")
    void crearSinNombre400() throws Exception {
        // La validacion del nombre vive en el service (regla de negocio).
        when(proveedorService.crear(any()))
                .thenThrow(new ReglaNegocioException("El nombre del proveedor es obligatorio"));

        mockMvc.perform(post("/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje").value("El nombre del proveedor es obligatorio"))
                .andExpect(jsonPath("$.ruta").value("/proveedores"));
    }

    @Test
    @DisplayName("GET /proveedores/{id} devuelve 200; inexistente 404 con ApiError")
    void obtenerPorId() throws Exception {
        when(proveedorService.obtenerPorId(1)).thenReturn(dto(1, "Sony"));
        when(proveedorService.obtenerPorId(99))
                .thenThrow(new RecursoNoEncontradoException("Proveedor no encontrado: 99"));

        mockMvc.perform(get("/proveedores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(get("/proveedores/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje").value("Proveedor no encontrado: 99"));
    }

    @Test
    @DisplayName("GET /proveedores pagina la respuesta")
    void listar() throws Exception {
        PageProveedor pagina = new PageProveedor();
        pagina.setContenido(List.of(dto(1, "Sony")));
        pagina.setTotalElementos(1L);
        pagina.setNumeroPagina(0);
        pagina.setTamanoPagina(10);
        when(proveedorService.listar(0, 10)).thenReturn(pagina);

        // page y size explicitos: en MockMvc standalone no se resuelven los
        // placeholders ${app.pagination...} de los @RequestParam.
        mockMvc.perform(get("/proveedores").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].nombre").value("Sony"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("DELETE /proveedores/{id} devuelve 200 con el DTO dado de baja")
    void eliminar() throws Exception {
        when(proveedorService.eliminar(1)).thenReturn(dto(1, "Sony"));

        mockMvc.perform(delete("/proveedores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("ReglaNegocioException se traduce a 400 con ApiError")
    void reglaNegocio400() throws Exception {
        when(proveedorService.eliminar(5))
                .thenThrow(new ReglaNegocioException("Operacion no permitida"));

        mockMvc.perform(delete("/proveedores/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje").value("Operacion no permitida"));
    }

    @Test
    @DisplayName("JSON malformado devuelve 400, no 500")
    void jsonMalformado() throws Exception {
        mockMvc.perform(post("/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no-es-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }
}
