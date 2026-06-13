package ru.z3r0ing.discordlp.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiscordBotInitializer {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotInitializer.class);

    private final JDA jda;
    private final List<ListenerAdapter> listeners;

    public DiscordBotInitializer(JDA jda, List<ListenerAdapter> listeners) {
        this.jda = jda;
        this.listeners = listeners;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        for (ListenerAdapter listener : listeners) {
            jda.addEventListener(listener);
        }
        log.info("Bot listeners registered successfully.");
    }
}