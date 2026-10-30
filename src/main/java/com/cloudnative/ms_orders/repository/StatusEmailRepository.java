package com.cloudnative.ms_orders.repository;
import com.cloudnative.ms_orders.model.StatusEmail;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface StatusEmailRepository extends JpaRepository<StatusEmail,String> {
    List<StatusEmail> findTop100ByStateAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(String state, LocalDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StatusEmail e where e.id=:id")
    Optional<StatusEmail> lockById(@Param("id") String id);
}
