package ru.z3r0ing.discordlp.command;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.service.GuildMemberService;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class LpKickCommand implements SlashCommandHandler {

    private static final int KICK_COST = 10_000;

    private final GuildMemberService guildMemberService;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    @Override
    public @NotNull String getCommandName() {
        return "lpkick";
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        User targetUser = event.getOption("user").getAsUser();
        User caller = event.getUser();

        if (targetUser.getId().equals(caller.getId())) {
            event.reply("Вы не можете кикнуть самого себя.").setEphemeral(true).queue();
            return;
        }

        GuildMember callerMember = guildMemberService.getOrCreateMember(guild, caller);

        if (callerMember.getBalance() < KICK_COST) {
            event.reply("Недостаточно поинтов. Требуется **" + KICK_COST + "** LP, у вас: **" + callerMember.getBalance() + "** LP.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        callerMember.setBalance(callerMember.getBalance() - KICK_COST);
        guildMemberRepository.save(callerMember);

        PointsTransaction tx = new PointsTransaction();
        tx.setMember(callerMember);
        tx.setAmount(-KICK_COST);
        tx.setReason(TransactionReason.USER_KICK);
        tx.setCreatedAt(Instant.now());
        pointsTransactionRepository.save(tx);

        guild.kick(targetUser).queue(
                success -> event.reply("Пользователь **" + targetUser.getName() + "** кикнут. Списано **" + KICK_COST + "** LP.")
                        .setEphemeral(true)
                        .queue(),
                failure -> event.reply("Не удалось кикнуть пользователя: " + failure.getMessage())
                        .setEphemeral(true)
                        .queue()
        );
    }
}
