package ru.sberbank.sbp.lab2.transfer_service.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.AccountServiceCommand;

@Component
@RequiredArgsConstructor
@Slf4j
public class JmsSender {

    private final JmsTemplate jmsTemplate; // Spring Boot автоматически создает и конфигурирует JmsTemplate

    public void sendAccountCommand(AccountServiceCommand command) {
        try {
            // Используем настроенный MessageConverter для отправки JSON
            log.info("Sending command to queue [{}]: Type: {}, CorrelationId: {}",
                     JmsConfig.ACCOUNT_COMMAND_QUEUE, command.getClass().getSimpleName(), command.getCorrelationId());
            jmsTemplate.convertAndSend(JmsConfig.ACCOUNT_COMMAND_QUEUE, command);
            log.debug("Command with CorrelationId [{}] successfully sent.", command.getCorrelationId());
        } catch (Exception e) {
            log.error("Error sending command with CorrelationId [{}] to queue [{}]: {}",
                      command.getCorrelationId(), JmsConfig.ACCOUNT_COMMAND_QUEUE, e.getMessage(), e);
            // TODO: Обработка ошибок отправки (например, механизм повторных попыток, Dead Letter Queue)
            // Пока просто логируем. ВАЖНО: если отправка не удалась, транзакция должна откатиться.
            throw new RuntimeException("Failed to send JMS command for CorrelationId: " + command.getCorrelationId(), e);
        }
    }
}