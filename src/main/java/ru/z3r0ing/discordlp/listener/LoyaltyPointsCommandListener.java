package ru.z3r0ing.discordlp.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyPointsCommandListener extends ListenerAdapter {

    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) {
            event.reply("Эта команда работает только на серверах.").setEphemeral(true).queue();
            return;
        }

        if ("lpuser".equals(event.getName()) || "lpadd".equals(event.getName()) || "lpremove".equals(event.getName())) {
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("У вас нет прав для использования этой команды (требуется ADMINISTRATOR).").setEphemeral(true).queue();
                return;
            }
        }

        if ("lpuser".equals(event.getName())) {
            User targetUser = event.getOption("user").getAsUser();
            GuildMember targetMember = getOrCreateMember(guild, targetUser);

            event.reply("Баланс пользователя **" + targetMember.getUserName() + "**: **" + targetMember.getBalance() + "** LP.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("lpremove".equals(event.getName())) {
            User targetUser = event.getOption("user").getAsUser();
            int amount = event.getOption("amount").getAsInt();

            if (amount <= 0) {
                event.reply("Количество поинтов должно быть больше нуля.").setEphemeral(true).queue();
                return;
            }

            GuildMember targetMember = getOrCreateMember(guild, targetUser);

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
            return;
        }

        if ("lpadd".equals(event.getName())) {
            User targetUser = event.getOption("user").getAsUser();
            int amount = event.getOption("amount").getAsInt();

            if (amount <= 0) {
                event.reply("Количество поинтов должно быть больше нуля.").setEphemeral(true).queue();
                return;
            }

            GuildMember targetMember = getOrCreateMember(guild, targetUser);

            targetMember.setBalance(targetMember.getBalance() + amount);
            guildMemberRepository.save(targetMember);

            PointsTransaction tx = new PointsTransaction();
            tx.setMember(targetMember);
            tx.setAmount(amount);
            tx.setReason(TransactionReason.ADMIN_MANUAL);
            tx.setCreatedAt(Instant.now());
            pointsTransactionRepository.save(tx);

            event.reply("Начислено **" + amount + "** LP пользователю **" + targetMember.getUserName() + "**. Новый баланс: **" + targetMember.getBalance() + "** LP.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("lp".equals(event.getName())) {
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

    private GuildMember getOrCreateMember(net.dv8tion.jda.api.entities.Guild guild, User user) {
        String guildId = guild.getId();
        String targetUserId = user.getId();

        return guildMemberRepository.findByGuildIdAndUserId(guildId, targetUserId)
                .orElseGet(() -> {
                    GuildMember newMember = new GuildMember();
                    newMember.setGuildId(guildId);
                    newMember.setUserId(targetUserId);
                    newMember.setBalance(0L);
                    newMember.setUserName(user.getName());
                    newMember.setGuildName(guild.getName());
                    return guildMemberRepository.save(newMember);
                });
    }
}