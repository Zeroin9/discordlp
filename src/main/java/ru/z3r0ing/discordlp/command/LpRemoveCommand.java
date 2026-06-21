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
public class LpRemoveCommand implements SlashCommandHandler {

    private final GuildMemberService guildMemberService;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    @Override
    public @NotNull String getCommandName() {
        return "lpremove";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        User targetUser = event.getOption("user").getAsUser();
        int amount = event.getOption("amount").getAsInt();

        if (amount <= 0) {
            event.reply("Количество поинтов должно быть больше нуля.").setEphemeral(true).queue();
            return;
        }

        GuildMember targetMember = guildMemberService.getOrCreateMember(guild, targetUser);

        if (targetMember.getBalance() < amount) {
            event.reply("У пользователя недостаточно поинтов для списания.").setEphemeral(true).queue();
            return;
        }

        targetMember.setBalance(targetMember.getBalance() - amount);
        guildMemberRepository.save(targetMember);

        PointsTransaction tx = new PointsTransaction();
        tx.setMember(targetMember);
        tx.setAmount(-amount);
        tx.setReason(TransactionReason.ADMIN_REMOVE);
        tx.setCreatedAt(Instant.now());
        pointsTransactionRepository.save(tx);

        event.reply("Списано **" + amount + "** LP у пользователя **" + targetMember.getUserName() + "**. Новый баланс: **" + targetMember.getBalance() + "** LP.")
                .setEphemeral(true)
                .queue();
    }
}
