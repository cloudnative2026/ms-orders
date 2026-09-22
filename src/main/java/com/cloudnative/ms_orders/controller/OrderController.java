package com.cloudnative.ms_orders.controller;

import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloudnative.ms_orders.dto.OrderResponseDTO;
import com.cloudnative.ms_orders.model.Order;
import com.cloudnative.ms_orders.model.OrderStatus;
import com.cloudnative.ms_orders.service.OrderService;

@RestController
@RequestMapping("api/v1/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    @Autowired
    private OrderService orderService;

    private int deriveCustomerId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                return Math.abs(sub.hashCode() % 900) + 101;
            }
        }
        return 101;
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> getAllOrders(Authentication authentication) {
        List<OrderResponseDTO> orders;
        if (isAdmin(authentication)) {
            orders = orderService.findAll();
        } else {
            int customerId = deriveCustomerId(authentication);
            orders = orderService.findByCustomerId(customerId);
        }
        if (!orders.isEmpty()) {
            return new ResponseEntity<>(orders, HttpStatus.OK);
        }
        return new ResponseEntity<>(orders, HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable int id, Authentication authentication) {
        Optional<OrderResponseDTO> optional = orderService.findById(id);
        if (optional.isPresent()) {
            OrderResponseDTO order = optional.get();
            if (!isAdmin(authentication)) {
                int customerId = deriveCustomerId(authentication);
                if (order.customerId() != customerId) {
                    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
                }
            }
            return new ResponseEntity<>(order, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Order o, Authentication authentication) {
        try {
            if (!isAdmin(authentication)) {
                o.setCustomerId(deriveCustomerId(authentication));
            }
            OrderResponseDTO savedOrder = orderService.createOrder(o);
            return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> updateStatus(@PathVariable int id, @RequestParam OrderStatus status) {
        try {
            OrderResponseDTO updated = orderService.updateStatus(id, status);
            return new ResponseEntity<>(updated, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> deleteOrder(@PathVariable int id) {
        try {
            orderService.deleteOrder(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

}
