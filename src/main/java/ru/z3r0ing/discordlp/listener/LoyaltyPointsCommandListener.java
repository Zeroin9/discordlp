package ru.z3r0ing.discordlp.listener;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.command.SlashCommandHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyPointsCommandListener extends ListenerAdapter {

    private final List<SlashCommandHandler> handlers;
    private final Map<String, SlashCommandHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (SlashCommandHandler handler : handlers) {
            handlerMap.put(handler.getCommandName(), handler);
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("Эта команда работает только на серверах.").setEphemeral(true).queue();
            return;
        }

        SlashCommandHandler handler = handlerMap.get(event.getName());
        if (handler == null) {
            return;
        }

        if (handler.requiresAdmin()) {
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("У вас нет прав для использования этой команды (требуется ADMINISTRATOR).").setEphemeral(true).queue();
                return;
            }
        }

        handler.handle(event);
    }
}
