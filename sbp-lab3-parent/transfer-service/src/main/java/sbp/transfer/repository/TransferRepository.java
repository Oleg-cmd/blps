package sbp.transfer.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sbp.dto.enums.TransferStatus;
import sbp.transfer.entity.Transfer;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {
  Optional<Transfer> findByCorrelationId(UUID correlationId);

  List<Transfer> findByStatusAndCreatedAtBefore(
    TransferStatus status,
    LocalDateTime timeoutTime
  );

  @Query(
    "SELECT t FROM Transfer t WHERE t.status IN :statuses AND t.updatedAt < :cutOffTime"
  )
  List<Transfer> findByStatusInAndUpdatedAtBefore(
    @Param("statuses") List<TransferStatus> statuses,
    @Param("cutOffTime") LocalDateTime cutOffTime
  );

  @Query(
    "SELECT t FROM Transfer t WHERE t.status IN :statuses AND t.createdAt < :cutOffTime"
  )
  List<Transfer> findByStatusInAndCreatedAtBefore(
    @Param("statuses") List<TransferStatus> statuses,
    @Param("cutOffTime") LocalDateTime cutOffTime
  );
}
