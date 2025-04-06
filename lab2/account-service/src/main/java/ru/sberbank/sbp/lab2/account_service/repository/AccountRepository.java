package ru.sberbank.sbp.lab2.account_service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.sberbank.sbp.lab2.account_service.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
  // Метод для поиска счета по номеру телефона
  Optional<Account> findByPhoneNumber(String phoneNumber);
  // JpaRepository уже предоставляет save, findById, findAll, deleteById и т.д.
}
