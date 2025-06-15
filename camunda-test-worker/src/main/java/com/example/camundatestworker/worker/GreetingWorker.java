package com.example.camundatestworker.worker;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreetingWorker {

  private static final Logger log = LoggerFactory.getLogger(
    GreetingWorker.class
  );

  // URL для подключения к Camunda Engine REST API.
  // По умолчанию localhost:8080, что соответствует нашему Docker-контейнеру Camunda.
  @Value("${camunda.bpm.client.base-url:http://localhost:8080/engine-rest}")
  private String camundaBaseUrl;

  private ExternalTaskClient client;

  @PostConstruct
  public void init() {
    log.info(
      "Initializing Camunda External Task Client for Camunda at: {}",
      camundaBaseUrl
    );

    // Создаем клиент для подключения к Camunda
    client = ExternalTaskClient.create()
      .baseUrl(camundaBaseUrl)
      .asyncResponseTimeout(10000) // Таймаут ожидания ответа от сервера Camunda (в мс)
      .disableBackoffStrategy() // Можно отключить стратегию увеличения интервалов при ошибках, для простоты
      .build();

    // Подписываемся на топик "greeting-topic", который мы указали в BPMN-диаграмме
    TopicSubscriptionBuilder subscription = client
      .subscribe("greeting-topic")
      .lockDuration(5000) // Блокируем задачу на 5 секунд для этого воркера, чтобы другие воркеры не взяли ее
      .handler((externalTask, externalTaskService) -> {
        // Эта лямбда будет вызвана, когда воркер получит задачу из Camunda

        // Получаем переменную 'userName' из контекста процесса Camunda
        // Мы ожидаем, что эта переменная будет установлена при старте процесса
        String userName = externalTask.getVariable("userName");
        if (userName == null) {
          userName = "Anonymous User"; // Значение по умолчанию, если переменная не найдена
        }

        log.info(
          "GreetingWorker: Received task with ID '{}', processing for user: {}",
          externalTask.getId(),
          userName
        );

        // --- Сюда можно вставить твою реальную бизнес-логику ---
        // В нашем примере просто генерируем приветственное сообщение
        String greetingMessage =
          "Hello, " +
          userName +
          "! Welcome to the world of Camunda External Tasks.";
        log.info("GreetingWorker: Generated message: '{}'", greetingMessage);

        // Переменные, которые мы хотим вернуть в процесс Camunda после выполнения задачи
        Map<String, Object> variablesToSet = new HashMap<>();
        variablesToSet.put("greetingResult", greetingMessage); // Сохраняем результат в новую переменную

        try {
          // Имитация какой-то работы (например, вызов другого сервиса)
          Thread.sleep(1000); // Задержка в 1 секунду

          // Завершаем задачу успешно и передаем переменные обратно в Camunda
          externalTaskService.complete(externalTask, variablesToSet);
          log.info(
            "GreetingWorker: Task '{}' completed successfully.",
            externalTask.getId()
          );
        } catch (InterruptedException e) {
          log.error(
            "GreetingWorker: Task processing was interrupted for task ID '{}'",
            externalTask.getId(),
            e
          );
          Thread.currentThread().interrupt(); // Восстанавливаем флаг прерывания
          // Сообщаем Camunda о сбое, чтобы она могла попробовать еще раз или обработать ошибку
          externalTaskService.handleFailure(
            externalTask,
            "Task interrupted: " + e.getMessage(), // Сообщение об ошибке
            e.toString(), // Детали ошибки
            0, // Количество оставшихся попыток (0 = не пытаться больше)
            0L // Таймаут перед следующей попыткой (0 = немедленно, если retries > 0)
          );
        } catch (Exception e) {
          log.error(
            "GreetingWorker: Failed to process task ID '{}'",
            externalTask.getId(),
            e
          );
          // Обработка другой технической ошибки
          externalTaskService.handleFailure(
            externalTask,
            "Processing failed: " + e.getMessage(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            externalTask.getRetries() == null
              ? 2
              : externalTask.getRetries() - 1, // Уменьшаем кол-во попыток (пример)
            5000L // Попробовать снова через 5 секунд
          );
        }
      });

    subscription.open(); // Открываем подписку, чтобы начать получать задачи
    log.info(
      "GreetingWorker: Subscribed to topic 'greeting-topic'. Waiting for external tasks..."
    );
  }
  // Опционально: корректное закрытие клиента при остановке приложения
  // @PreDestroy
  // public void shutdown() {
  //     if (client != null) {
  //         log.info("Shutting down Camunda External Task Client...");
  //         client.stop();
  //     }
  // }
}
