package com.cloudnative.ms_orders.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cloudnative.ms_orders.model.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByCustomerId(int customerId);
}
