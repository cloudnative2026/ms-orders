package com.cloudnative.ms_orders.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloudnative.ms_orders.model.Order;
import com.cloudnative.ms_orders.model.OrderStatus;
import com.cloudnative.ms_orders.repository.OrderRepository;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Optional<Order> findById(int id) {
        return orderRepository.findById(id);
    }

    public Order createOrder(Order o) {
        return orderRepository.save(o);
    }

    public Order updateStatus(int id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido con ID " + id + " no encontrado."));
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public void deleteOrder(int id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Pedido con ID " + id + " no encontrado.");
        }
        orderRepository.deleteById(id);
    }
}
