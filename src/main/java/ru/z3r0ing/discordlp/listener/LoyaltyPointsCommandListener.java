package ru.z3r0ing.discordlp.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyPointsCommandListener extends ListenerAdapter {

    private final GuildMemberRepository guildMemberRepository;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!"lp".equals(event.getName())) {
            return;
        }

        var guild = event.getGuild();
        if (guild == null) {
            event.reply("Эта команда работает только на серверах.").setEphemeral(true).queue();
            return;
        }

        String guildId = guild.getId();
        String userId = event.getUser().getId();

        GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
                .orElseGet(() -> {
                    GuildMember newMember = new GuildMember();
                    newMember.setGuildId(guildId);
                    newMember.setUserId(userId);
                    newMember.setBalance(0L);
                    newMember.setUserName(event.getUser().getName());
                    newMember.setGuildName(guild.getName());
                    return guildMemberRepository.save(newMember);
                });

        event.reply("Ваш текущий баланс: **" + member.getBalance() + "** LP.")
                .setEphemeral(true)
                .queue();
    }
}