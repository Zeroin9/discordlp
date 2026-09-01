package ru.z3r0ing.discordlp.service;

/**
 * Ошибка бизнес-правил пари. Сообщение предназначено для показа пользователю в Discord.
 */
public class PariException extends RuntimeException {

    public PariException(String message) {
        super(message);
    }
}
