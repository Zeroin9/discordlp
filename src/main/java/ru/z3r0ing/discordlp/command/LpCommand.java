package ru.z3r0ing.discordlp.command;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.service.GuildMemberService;

@Component
@RequiredArgsConstructor
public class LpCommand implements SlashCommandHandler {

    private final GuildMemberService guildMemberService;

    @Override
    public @NotNull String getCommandName() {
        return "lp";
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        User user = event.getUser();

        GuildMember member = guildMemberService.getOrCreateMember(guild, user);

        event.reply("Ваш текущий баланс: **" + member.getBalance() + "** LP.")
                .setEphemeral(true)
                .queue();
    }
}
