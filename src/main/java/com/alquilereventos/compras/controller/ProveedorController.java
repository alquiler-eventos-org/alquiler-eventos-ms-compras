package com.alquilereventos.compras.controller;

import com.alquilereventos.common.dto.PageProveedor;
import com.alquilereventos.common.dto.Proveedor;
import com.alquilereventos.common.dto.ProveedorCrearRequest;
import com.alquilereventos.compras.service.ProveedorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Endpoints REST de proveedores. Solo delega en el service (capas estrictas).
 */
@RestController
@RequestMapping("/proveedores")
public class ProveedorController {

    private static final Logger log = LoggerFactory.getLogger(ProveedorController.class);

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @PostMapping
    public ResponseEntity<Proveedor> crear(@Valid @RequestBody ProveedorCrearRequest request) {
        log.info("POST /proveedores");
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedorService.crear(request));
    }

    @PutMapping("/{id}")
    public Proveedor actualizar(@PathVariable Integer id,
                                @Valid @RequestBody ProveedorCrearRequest request) {
        log.info("PUT /proveedores/{}", id);
        return proveedorService.actualizar(id, request);
    }

    @GetMapping("/{id}")
    public Proveedor obtenerPorId(@PathVariable Integer id) {
        log.info("GET /proveedores/{}", id);
        return proveedorService.obtenerPorId(id);
    }

    @GetMapping
    public PageProveedor listar(@RequestParam(required = false,
                                              defaultValue = "${app.pagination.default-page:0}") Integer page,
                                @RequestParam(required = false,
                                              defaultValue = "${app.pagination.default-page-size:10}") Integer size) {
        log.info("GET /proveedores page={} size={}", page, size);
        return proveedorService.listar(page, size);
    }

    @GetMapping("/buscar")
    public PageProveedor buscar(@RequestParam(required = false) String nombre,
                                @RequestParam(required = false,
                                              defaultValue = "${app.pagination.default-page:0}") Integer page,
                                @RequestParam(required = false,
                                              defaultValue = "${app.pagination.default-page-size:10}") Integer size) {
        log.info("GET /proveedores/buscar nombre={} page={} size={}", nombre, page, size);
        return proveedorService.buscar(nombre, page, size);
    }

    @DeleteMapping("/{id}")
    public Proveedor eliminar(@PathVariable Integer id) {
        log.info("DELETE /proveedores/{}", id);
        return proveedorService.eliminar(id);
    }
}
