package com.cloudnative.ms_orders.repository;

import com.cloudnative.ms_orders.model.Receipt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    List<Receipt> findTop100ByGeneratedAtIsNullOrderByRequestedAtAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.orderId = :id")
    Optional<Receipt> lockByOrderId(@Param("id") long id);
}
