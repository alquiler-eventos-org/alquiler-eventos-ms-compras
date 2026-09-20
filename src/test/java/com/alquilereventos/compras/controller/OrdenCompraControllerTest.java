package com.alquilereventos.compras.controller;

import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraCrearRequest;
import com.alquilereventos.common.dto.OrdenCompraEstadoRequest;
import com.alquilereventos.common.dto.PageOrdenCompra;
import com.alquilereventos.compras.exception.ApiExceptionHandler;
import com.alquilereventos.compras.exception.RecursoNoEncontradoException;
import com.alquilereventos.compras.exception.ReglaNegocioException;
import com.alquilereventos.compras.service.OrdenCompraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la capa web de ordenes de compra con MockMvc: codigos HTTP,
 * JSON y errores de negocio via ApiError.
 */
class OrdenCompraControllerTest {

    private OrdenCompraService ordenCompraService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ordenCompraService = mock(OrdenCompraService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrdenCompraController(ordenCompraService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private OrdenCompra dto(int id, String estado) {
        OrdenCompra dto = new OrdenCompra(id, 100.0, null);
        dto.setEstado(com.alquilereventos.common.dto.EstadoOrdenCompra.valueOf(estado));
        return dto;
    }

    private final String cuerpoCrear = """
            {
              "proveedorId": 1,
              "usuarioId": 2,
              "detalles": [
                {"equipoId": 10, "cantidad": 2, "costoUnitario": 100.0}
              ]
            }
            """;

    @Test
    @DisplayName("POST /ordenes-compra devuelve 201 con la orden creada")
    void crearDevuelve201() throws Exception {
        when(ordenCompraService.crear(any())).thenReturn(dto(1, "PENDIENTE"));

        mockMvc.perform(post("/ordenes-compra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("POST /ordenes-compra sin detalles devuelve 400 con ApiError")
    void crearSinDetalles400() throws Exception {
        when(ordenCompraService.crear(any()))
                .thenThrow(new ReglaNegocioException("La orden debe tener al menos un detalle"));

        mockMvc.perform(post("/ordenes-compra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":1,\"usuarioId\":2,\"detalles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje").value("La orden debe tener al menos un detalle"));
    }

    @Test
    @DisplayName("PUT /ordenes-compra/{id}/estado cambia el estado (200)")
    void cambiarEstado() throws Exception {
        when(ordenCompraService.cambiarEstado(eq(1), any())).thenReturn(dto(1, "CONFIRMADA"));

        mockMvc.perform(put("/ordenes-compra/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("PUT /ordenes-compra/{id}/estado con transicion invalida devuelve 400")
    void cambiarEstadoInvalido() throws Exception {
        when(ordenCompraService.cambiarEstado(eq(1), any()))
                .thenThrow(new ReglaNegocioException("Transicion de estado no permitida: PENDIENTE -> RECIBIDA"));

        mockMvc.perform(put("/ordenes-compra/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RECIBIDA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    @DisplayName("GET /ordenes-compra/{id} devuelve 200; inexistente 404 con ApiError")
    void obtenerPorId() throws Exception {
        when(ordenCompraService.obtenerPorId(1)).thenReturn(dto(1, "PENDIENTE"));
        when(ordenCompraService.obtenerPorId(99))
                .thenThrow(new RecursoNoEncontradoException("Orden de compra no encontrada: 99"));

        mockMvc.perform(get("/ordenes-compra/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(get("/ordenes-compra/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje").value("Orden de compra no encontrada: 99"));
    }

    @Test
    @DisplayName("GET /ordenes-compra pagina la respuesta")
    void listar() throws Exception {
        PageOrdenCompra pagina = new PageOrdenCompra();
        pagina.setContenido(List.of(dto(1, "PENDIENTE")));
        pagina.setTotalElementos(1L);
        pagina.setNumeroPagina(0);
        pagina.setTamanoPagina(10);
        when(ordenCompraService.listar(0, 10)).thenReturn(pagina);

        // page y size explicitos: en MockMvc standalone no se resuelven los
        // placeholders ${app.pagination...} de los @RequestParam.
        mockMvc.perform(get("/ordenes-compra").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(1))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("DELETE /ordenes-compra/{id} anula (200) y orden inexistente da 404")
    void eliminar() throws Exception {
        when(ordenCompraService.eliminar(1)).thenReturn(dto(1, "ANULADA"));
        when(ordenCompraService.eliminar(99))
                .thenThrow(new RecursoNoEncontradoException("Orden de compra no encontrada: 99"));

        mockMvc.perform(delete("/ordenes-compra/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"));

        mockMvc.perform(delete("/ordenes-compra/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /ordenes-compra/{id} actualiza una orden PENDIENTE (200)")
    void actualizarPendiente() throws Exception {
        when(ordenCompraService.actualizar(eq(1), any())).thenReturn(dto(1, "PENDIENTE"));

        mockMvc.perform(put("/ordenes-compra/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("Estado desconocido en el JSON devuelve 400 (no 500)")
    void estadoDesconocido() throws Exception {
        mockMvc.perform(put("/ordenes-compra/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"NO_EXISTO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }
}
