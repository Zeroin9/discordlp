package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyPointsService {

    private static final long CHECK_INTERVAL_SECONDS = 30L;
    private static final long CHECK_INTERVAL_MS = CHECK_INTERVAL_SECONDS * 1_000L;

    private final JDA jda;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    @Scheduled(fixedRate = CHECK_INTERVAL_MS)
    public void processLoyaltyPoints() {
        log.debug("Запуск периодической проверки начисления баллов лояльности...");
        try {
            List<Guild> guilds = jda.getGuilds();
            for (Guild guild : guilds) {
                try {
                    processGuild(guild);
                } catch (Exception e) {
                    log.error("Ошибка при обработке гильдии: {}", guild.getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("Критическая ошибка в процессе начисления баллов лояльности", e);
        }
    }

    private void processGuild(Guild guild) {
        List<VoiceChannel> voiceChannels = guild.getVoiceChannels();
        for (VoiceChannel channel : voiceChannels) {
            List<Member> members = channel.getMembers();
            if (members.isEmpty()) {
                continue;
            }

            // Проверяем, есть ли в канале хотя бы один стример
            boolean hasStreamer = members.stream()
                    .anyMatch(member -> {
                        var vs = member.getVoiceState();
                        return vs != null && vs.isStream();
                    });

            for (Member member : members) {
                var vs = member.getVoiceState();
                if (vs == null || vs.getChannel() == null) {
                    continue;
                }

                boolean isStreaming = vs.isStream();
                int pointsToAdd = 0; // По умолчанию 0 баллов
                TransactionReason reason = null; // По умолчанию без причины

                // Заглушенные участники не получают баллов
                if (!vs.isDeafened()) {
                    // Определяем количество баллов и причину на основе статуса участника
                    if (isStreaming) {
                        pointsToAdd = 200;
                        reason = TransactionReason.VOICE_STREAMER;
                    } else if (hasStreamer) {
                        pointsToAdd = 150;
                        reason = TransactionReason.VOICE_VIEWER;
                    } else {
                        pointsToAdd = 100;
                        reason = TransactionReason.VOICE_STANDARD;
                    }
                }

                Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                Instant threshold = now.minusSeconds(CHECK_INTERVAL_SECONDS);

                GuildMember guildMember = guildMemberRepository
                        .findByGuildIdAndUserId(guild.getId(), member.getId())
                        .orElseGet(() -> {
                            GuildMember newMember = new GuildMember();
                            newMember.setGuildId(guild.getId());
                            newMember.setGuildName(guild.getName());
                            newMember.setUserId(member.getId());
                            newMember.setUserName(member.getEffectiveName());
                            newMember.setBalance(0L);
                            return guildMemberRepository.save(newMember);
                        });

                // Актуализация имен при изменении
                if (guildMember.getUserName() == null || !guildMember.getUserName().equals(member.getEffectiveName())) {
                    guildMember.setUserName(member.getEffectiveName());
                }
                if (guildMember.getGuildName() == null || !guildMember.getGuildName().equals(guild.getName())) {
                    guildMember.setGuildName(guild.getName());
                }

                if (guildMember.getLastVoiceCheckAt() == null || guildMember.getLastVoiceCheckAt().isBefore(threshold)) {
                    guildMember.setBalance(guildMember.getBalance() + pointsToAdd);
                    guildMember.setLastVoiceCheckAt(now);
                    guildMemberRepository.save(guildMember);

                    if (reason != null) {
                        PointsTransaction tx = new PointsTransaction();
                        tx.setMember(guildMember);
                        tx.setAmount(pointsToAdd);
                        tx.setReason(reason);
                        tx.setCreatedAt(now);
                        pointsTransactionRepository.save(tx);
                    }

                    log.debug("Начислено {} LP пользователю {} ({}) в гильдии {} по причине {}", 
                            pointsToAdd, member.getUser().getName(), member.getId(), guild.getName(), reason);
                }
            }
        }
    }
}