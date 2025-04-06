package ru.sberbank.sbp.lab2.account_service.jms;

public final class JmsConfig { // Используем final класс без конструктора

    private JmsConfig() { // Приватный конструктор, чтобы нельзя было создать экземпляр
    }

    // Имя очереди для команд, которые должен обработать account-service
    public static final String ACCOUNT_COMMAND_QUEUE = "account.command.queue";

    // Можно добавить очередь для ответов, если понадобится
    // public static final String ACCOUNT_REPLY_QUEUE = "account.reply.queue";
}