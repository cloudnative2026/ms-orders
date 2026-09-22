package com.cloudnative.ms_orders.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloudnative.ms_orders.client.CatalogClient;
import com.cloudnative.ms_orders.dto.OrderItemResponseDTO;
import com.cloudnative.ms_orders.dto.OrderResponseDTO;
import com.cloudnative.ms_orders.dto.ProductDTO;
import com.cloudnative.ms_orders.model.Order;
import com.cloudnative.ms_orders.model.OrderStatus;
import com.cloudnative.ms_orders.repository.OrderRepository;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CatalogClient catalogClient;

    public List<OrderResponseDTO> findAll() {
        return orderRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public List<OrderResponseDTO> findByCustomerId(int customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public Optional<OrderResponseDTO> findById(int id) {
        return orderRepository.findById(id)
                .map(this::toResponseDTO);
    }

    public OrderResponseDTO createOrder(Order o) {
        Order saved = orderRepository.save(o);
        return toResponseDTO(saved);
    }

    public OrderResponseDTO updateStatus(int id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido con ID " + id + " no encontrado."));
        order.setStatus(newStatus);
        return toResponseDTO(orderRepository.save(order));
    }

    public void deleteOrder(int id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Pedido con ID " + id + " no encontrado.");
        }
        orderRepository.deleteById(id);
    }

    public OrderResponseDTO toResponseDTO(Order order) {
        List<OrderItemResponseDTO> itemDTOs = order.getItems() != null
                ? order.getItems().stream().map(item -> {
                    ProductDTO product = catalogClient.getProductById(item.getProductId());
                    return new OrderItemResponseDTO(
                            item.getProductId(),
                            item.getQuantity(),
                            item.getUnitPrice(),
                            product
                    );
                }).toList()
                : Collections.emptyList();

        return new OrderResponseDTO(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                itemDTOs,
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
