package com.alquilereventos.compras.config.security;

import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.compras.controller.OrdenCompraController;
import com.alquilereventos.compras.controller.ProveedorController;
import com.alquilereventos.compras.exception.ApiExceptionHandler;
import com.alquilereventos.compras.service.OrdenCompraService;
import com.alquilereventos.compras.service.ProveedorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la cadena real de Spring Security (401/403/200) que no montan los
 * tests de controller con standaloneSetup. El secreto es solo de prueba.
 *
 * <p>Se fijan las clases del slice con {@code @ContextConfiguration} para NO
 * arrastrar el {@code @EnableJpaRepositories} de {@code ComprasApplication}.</p>
 */
@WebMvcTest
@ContextConfiguration(classes = {
        ProveedorController.class,
        OrdenCompraController.class,
        ComprasSecurityConfig.class,
        SecurityErrorHandler.class,
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.jwt.issuer=alquiler-eventos-seguridad"
})
class ComprasSecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProveedorService proveedorService;

    @MockitoBean
    private OrdenCompraService ordenCompraService;

    @Test
    @DisplayName("Sin token -> 401 con ApiError")
    void sinToken() throws Exception {
        mockMvc.perform(get("/proveedores/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(401));
    }

    @Test
    @DisplayName("Rol ENCARGADO_DEPOSITO sobre compras -> 403 con ApiError")
    void rolIncorrecto() throws Exception {
        mockMvc.perform(get("/proveedores/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ENCARGADO_DEPOSITO"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    @DisplayName("Rol ENCARGADO_COMPRAS -> 200")
    void rolCorrecto() throws Exception {
        when(proveedorService.obtenerPorId(1)).thenReturn(new Proveedor());

        mockMvc.perform(get("/proveedores/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ENCARGADO_COMPRAS"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token valido sin claim uid al crear orden -> 401 (no 400 ni 500)")
    void tokenSinUid() throws Exception {
        mockMvc.perform(post("/ordenes-compra")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ENCARGADO_COMPRAS")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":1,\"fechaCompra\":\"2026-10-20\",\"detalles\":"
                                + "[{\"equipoId\":1,\"cantidad\":1,\"costoUnitario\":10}]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(401));
    }
}
