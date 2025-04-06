package ru.sberbank.sbp.lab2.transfer_service.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    // Поиск истории переводов пользователя с пагинацией
    List<Transfer> findBySenderPhoneNumberOrderByCreatedAtDesc(String phoneNumber, Pageable pageable);

    // Сумма успешных переводов пользователя за период (для лимитов)
    // ВАЖНО: Убедись, что TransferStatus.SUCCESSFUL используется правильно
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t WHERE t.senderPhoneNumber = :phoneNumber " +
           "AND t.createdAt >= :startDate AND t.status = 'SUCCESSFUL'")
    BigDecimal sumSuccessfulTransferAmountsByPhoneNumberAndDate(
        @Param("phoneNumber") String phoneNumber,
        @Param("startDate") LocalDateTime startDate
    );

    // Поиск "зависших" переводов (для возможной фоновой обработки)
    // ВАЖНО: Убедись, что TransferStatus.PROCESSING используется правильно
    @Query("SELECT t FROM Transfer t WHERE t.status = 'PROCESSING' AND t.createdAt < :timeout")
    List<Transfer> findStuckProcessingTransfers(@Param("timeout") LocalDateTime timeout);

    // Можно добавить другие нужные методы поиска
}