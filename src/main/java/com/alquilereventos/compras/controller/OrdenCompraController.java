package com.alquilereventos.compras.controller;

import com.alquilereventos.common.dto.OrdenCompra;
import com.alquilereventos.common.dto.OrdenCompraCrearRequest;
import com.alquilereventos.common.dto.OrdenCompraEstadoRequest;
import com.alquilereventos.common.dto.PageOrdenCompra;
import com.alquilereventos.compras.service.OrdenCompraService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de ordenes de compra. Solo delega en el service (capas estrictas).
 */
@RestController
@RequestMapping("/ordenes-compra")
@PreAuthorize("hasAnyRole('ADMIN', 'ENCARGADO_COMPRAS')")
public class OrdenCompraController {

    private static final Logger log = LoggerFactory.getLogger(OrdenCompraController.class);

    private final OrdenCompraService ordenCompraService;

    public OrdenCompraController(OrdenCompraService ordenCompraService) {
        this.ordenCompraService = ordenCompraService;
    }

    @PostMapping
    public ResponseEntity<OrdenCompra> crear(@Valid @RequestBody OrdenCompraCrearRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        log.info("POST /ordenes-compra");
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenCompraService.crear(request, usuarioId(jwt)));
    }

    @PutMapping("/{id}")
    public OrdenCompra actualizar(@PathVariable Integer id,
                                  @Valid @RequestBody OrdenCompraCrearRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        log.info("PUT /ordenes-compra/{}", id);
        return ordenCompraService.actualizar(id, request, usuarioId(jwt));
    }

    @GetMapping("/{id}")
    public OrdenCompra obtenerPorId(@PathVariable Integer id) {
        log.info("GET /ordenes-compra/{}", id);
        return ordenCompraService.obtenerPorId(id);
    }

    @GetMapping
    public PageOrdenCompra listar(@RequestParam(required = false,
                                                defaultValue = "${app.pagination.default-page:0}") Integer page,
                                  @RequestParam(required = false,
                                                defaultValue = "${app.pagination.default-page-size:10}") Integer size) {
        log.info("GET /ordenes-compra page={} size={}", page, size);
        return ordenCompraService.listar(page, size);
    }

    @PutMapping("/{id}/estado")
    public OrdenCompra cambiarEstado(@PathVariable Integer id,
                                     @Valid @RequestBody OrdenCompraEstadoRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("PUT /ordenes-compra/{}/estado", id);
        return ordenCompraService.cambiarEstado(id, request, usuarioId(jwt));
    }

    @DeleteMapping("/{id}")
    public OrdenCompra eliminar(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        log.info("DELETE /ordenes-compra/{}", id);
        return ordenCompraService.eliminar(id, usuarioId(jwt));
    }

    private Integer usuarioId(Jwt jwt) {
        if (jwt == null || jwt.getClaimAsString("uid") == null) {
            return null;
        }
        try {
            return Integer.valueOf(jwt.getClaimAsString("uid"));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
