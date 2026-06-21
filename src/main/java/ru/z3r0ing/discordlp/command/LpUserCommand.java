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
public class LpUserCommand implements SlashCommandHandler {

    private final GuildMemberService guildMemberService;

    @Override
    public @NotNull String getCommandName() {
        return "lpuser";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        User targetUser = event.getOption("user").getAsUser();

        GuildMember targetMember = guildMemberService.getOrCreateMember(guild, targetUser);

        event.reply("Баланс пользователя **" + targetMember.getUserName() + "**: **" + targetMember.getBalance() + "** LP.")
                .setEphemeral(true)
                .queue();
    }
}
