package ru.z3r0ing.discordlp.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.MutedMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.MutedMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.service.GuildMemberService;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class LpMuteCommand implements SlashCommandHandler {

    private static final int MUTE_COST = 50_000;

    private final GuildMemberService guildMemberService;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final MutedMemberRepository mutedMemberRepository;

    @Override
    public @NotNull String getCommandName() {
        return "lpmute";
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var guild = Objects.requireNonNull(event.getGuild());
        Member targetMember = Objects.requireNonNull(event.getOption("user")).getAsMember();
        User caller = event.getUser();

        if (targetMember == null) {
            event.reply("Участник не найден на сервере.").setEphemeral(true).queue();
            return;
        }

        if (targetMember.getId().equals(caller.getId())) {
            event.reply("Вы не можете замьютить самого себя.").setEphemeral(true).queue();
            return;
        }

        if (targetMember.getVoiceState() == null || !targetMember.getVoiceState().inAudioChannel()) {
            event.reply("Пользователь не подключен к голосовому каналу.").setEphemeral(true).queue();
            return;
        }

        if (targetMember.getVoiceState().isMuted()) {
            event.reply("Пользователь уже замьючен на сервере.").setEphemeral(true).queue();
            return;
        }

        GuildMember callerMember = guildMemberService.getOrCreateMember(guild, caller);

        if (callerMember.getBalance() < MUTE_COST) {
            event.reply("Недостаточно поинтов. Требуется **" + MUTE_COST + "** LP, у вас: **" + callerMember.getBalance() + "** LP.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        callerMember.setBalance(callerMember.getBalance() - MUTE_COST);
        guildMemberRepository.save(callerMember);

        PointsTransaction tx = new PointsTransaction();
        tx.setMember(callerMember);
        tx.setAmount((long) -MUTE_COST);
        tx.setReason(TransactionReason.USER_MUTE);
        tx.setInitiatedBy(caller.getId());
        tx.setCreatedAt(Instant.now());
        pointsTransactionRepository.save(tx);

        MutedMember mutedMember = new MutedMember();
        mutedMember.setGuildId(guild.getId());
        mutedMember.setUserId(targetMember.getId());
        mutedMember.setMutedAt(Instant.now());
        mutedMemberRepository.save(mutedMember);

        targetMember.mute(true).queue(
                success -> event.reply("Пользователь **" + targetMember.getEffectiveName() + "** замьючен. Списано **" + MUTE_COST + "** LP.")
                        .setEphemeral(true)
                        .queue(),
                failure -> {
                    log.error("Не удалось замьютить пользователя {} в гильдии {}", targetMember.getEffectiveName(), guild.getName(), failure);
                    event.reply("Не удалось замьютить пользователя: " + failure.getMessage())
                            .setEphemeral(true)
                            .queue();
                }
        );
    }
}
