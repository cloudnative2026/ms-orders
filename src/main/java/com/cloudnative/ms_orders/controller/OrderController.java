package com.cloudnative.ms_orders.controller;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import com.cloudnative.ms_orders.dto.*;
import com.cloudnative.ms_orders.model.OrderStatus;
import com.cloudnative.ms_orders.service.OrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }
    @GetMapping
    public List<OrderResponseDTO> getAll(JwtAuthenticationToken auth) { return service.findAll(auth); }
    @GetMapping("/{id}")
    public OrderResponseDTO get(@PathVariable long id, JwtAuthenticationToken auth) { return service.findById(id, auth); }
    @PostMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody OrderRequestDTO body, JwtAuthenticationToken auth) {
        var order = service.createOrder(body, auth);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    public OrderResponseDTO update(@PathVariable long id, @Valid @RequestBody OrderRequestDTO body, JwtAuthenticationToken auth) {
        return service.updateOrder(id, body, auth);
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    public OrderResponseDTO status(@PathVariable long id, @RequestParam OrderStatus status) { return service.updateStatus(id, status); }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
