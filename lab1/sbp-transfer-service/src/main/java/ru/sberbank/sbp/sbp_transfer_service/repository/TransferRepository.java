package ru.sberbank.sbp.sbp_transfer_service.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    List<Transfer> findBySenderPhoneNumberOrderByCreatedAtDesc(String phoneNumber, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t WHERE t.senderPhoneNumber = :phoneNumber " +
           "AND t.createdAt >= :startDate AND t.status = 'SUCCESSFUL'")
    BigDecimal sumTransferAmountsByPhoneNumberAndDate(@Param("phoneNumber") String phoneNumber,
                                                     @Param("startDate") LocalDateTime startDate);

    @Query("SELECT t FROM Transfer t WHERE t.status = 'PROCESSING' " +
           "AND t.createdAt < :timeout")
    List<Transfer> findStuckTransfers(@Param("timeout") LocalDateTime timeout);
}