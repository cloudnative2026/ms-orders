package com.cloudnative.ms_orders.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import com.cloudnative.ms_orders.client.CatalogClient;
import com.cloudnative.ms_orders.dto.*;
import com.cloudnative.ms_orders.model.*;
import com.cloudnative.ms_orders.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orders;
    private final CatalogClient catalog;
    public OrderService(OrderRepository orders, CatalogClient catalog) {
        this.orders = orders;
        this.catalog = catalog;
    }
    private boolean staff(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream().anyMatch(a -> Set.of("ROLE_Admin", "ROLE_Operador").contains(a.getAuthority()));
    }
    private String issuer(JwtAuthenticationToken auth) { return auth.getToken().getIssuer().toString(); }
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findAll(JwtAuthenticationToken auth) {
        var result = staff(auth) ? orders.findAll() :
            orders.findByOwnerIssuerAndOwnerSubject(issuer(auth), auth.getToken().getSubject());
        return result.stream().map(this::response).toList();
    }
    private Order requireOrder(long id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
    }
    private Order visibleOrder(long id, JwtAuthenticationToken auth) {
        var order = requireOrder(id);
        if (!staff(auth) && (!Objects.equals(order.getOwnerIssuer(), issuer(auth))
                || !Objects.equals(order.getOwnerSubject(), auth.getToken().getSubject()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
        }
        return order;
    }
    @Transactional(readOnly = true)
    public OrderResponseDTO findById(long id, JwtAuthenticationToken auth) { return response(visibleOrder(id, auth)); }
    public OrderResponseDTO createOrder(OrderRequestDTO request, JwtAuthenticationToken auth) {
        var priced = priceItems(request, auth);
        var order = new Order();
        order.setCustomerId(request.customerId());
        order.setOwnerIssuer(issuer(auth));
        order.setOwnerSubject(auth.getToken().getSubject());
        order.setItems(priced.items());
        order.setStatus(OrderStatus.CREADO);
        order.setTotalAmount(priced.total());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(order.getCreatedAt());
        return response(orders.saveAndFlush(order));
    }
    public OrderResponseDTO updateOrder(long id, OrderRequestDTO request, JwtAuthenticationToken auth) {
        var order = visibleOrder(id, auth);
        if (order.getStatus() != OrderStatus.CREADO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden editar pedidos en estado CREADO.");
        var replacement = priceItems(request, auth);
        order.setCustomerId(request.customerId());
        order.setItems(replacement.items());
        order.setTotalAmount(replacement.total());
        order.setUpdatedAt(LocalDateTime.now());
        return response(orders.saveAndFlush(order));
    }
    private record PricedItems(List<OrderItem> items, BigDecimal total) {}
    private PricedItems priceItems(OrderRequestDTO request, JwtAuthenticationToken auth) {
        var seen = new HashSet<Integer>();
        var items = new ArrayList<OrderItem>();
        BigDecimal total = BigDecimal.ZERO;
        for (var item : request.items()) {
            if (!seen.add(item.productId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Productos duplicados.");
            var product = catalog.getProductById(item.productId(), auth.getToken().getTokenValue());
            if (!Boolean.TRUE.equals(product.active())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto inactivo.");
            items.add(new OrderItem(item.productId(), item.quantity(), product.price()));
            total = total.add(product.price().multiply(BigDecimal.valueOf(item.quantity())));
        }
        if (total.compareTo(new BigDecimal("99999999999999999.99")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total fuera de rango.");
        return new PricedItems(items, total);
    }
    public OrderResponseDTO updateStatus(long id, OrderStatus target) {
        var order = requireOrder(id);
        var current = order.getStatus();
        if (current == target) return response(order);
        boolean allowed = switch (current) {
            case CREADO -> target == OrderStatus.ACEPTADO || target == OrderStatus.CANCELADO;
            case ACEPTADO -> target == OrderStatus.EN_PREPARACION || target == OrderStatus.CANCELADO;
            case EN_PREPARACION -> target == OrderStatus.DESPACHADO;
            case DESPACHADO -> target == OrderStatus.ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT, "Transicion de estado no permitida.");
        order.setStatus(target);
        order.setUpdatedAt(LocalDateTime.now());
        return response(orders.saveAndFlush(order));
    }
    public void deleteOrder(long id) {
        var order = requireOrder(id);
        orders.delete(order);
        orders.flush();
    }
    private OrderResponseDTO response(Order order) {
        // Historical reads must survive catalog outages and product deletion.
        var items = order.getItems().stream().map(i -> new OrderItemResponseDTO(
            i.getProductId(), i.getQuantity(), i.getUnitPrice(), null)).toList();
        return new OrderResponseDTO(order.getId(), order.getCustomerId(), order.getStatus(), items,
            order.getTotalAmount(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
