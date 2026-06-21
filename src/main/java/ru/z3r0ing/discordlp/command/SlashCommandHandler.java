package ru.z3r0ing.discordlp.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface SlashCommandHandler {

    String getCommandName();

    boolean requiresAdmin();

    void handle(SlashCommandInteractionEvent event);
}
