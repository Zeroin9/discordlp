package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
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

    private static final long CHECK_INTERVAL_SECONDS = 300L;
    private static final long CHECK_INTERVAL_MS = CHECK_INTERVAL_SECONDS * 1_000L;

    private final JDA jda;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    @Scheduled(fixedRate = CHECK_INTERVAL_MS)
    public void processLoyaltyPoints() {
        if (jda.getStatus() != net.dv8tion.jda.api.JDA.Status.CONNECTED) {
            log.warn("JDA не в статусе CONNECTED (текущий: {}). Пропуск начисления баллов во избежание блокировок кэша.", jda.getStatus());
            return;
        }
        
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
        VoiceChannel afkChannel = guild.getAfkChannel();
        
        for (VoiceChannel channel : voiceChannels) {
            // Исключаем AFK канал
            if (afkChannel != null && channel.getId().equals(afkChannel.getId())) {
                continue;
            }
            
            List<Member> members = channel.getMembers();
            if (members.isEmpty()) {
                continue;
            }

            for (Member member : members) {
                GuildVoiceState voiceState = member.getVoiceState();
                if (voiceState == null || voiceState.getChannel() == null) {
                    continue;
                }

                // Заглушенные участники не получают баллов
                // Также не начисляем баллы, если микрофон выключен
                if (voiceState.isDeafened() || voiceState.isMuted()) {
                    continue;
                }

                // Определяем количество баллов и причину на основе статуса участника
                PointsInfo pointsInfo = determinePointsAndReason(voiceState, members, member);
                
                // Получаем или создаем участника гильдии
                GuildMember guildMember = getOrCreateGuildMember(guild, member);
                
                // Актуализация имен при изменении
                updateMemberNames(guildMember, guild, member);
                
                // Начисляем баллы, если прошло достаточно времени
                awardPointsToMember(guildMember, pointsInfo);
            }
        }
    }
    
    /**
     * Определяет количество баллов и причину для участника на основе его состояния
     */
    private PointsInfo determinePointsAndReason(GuildVoiceState voiceState, List<Member> members, Member member) {
        boolean isStreaming = voiceState.isStream();
        int pointsToAdd = 0;
        TransactionReason reason = null;

        if (isStreaming) {
            // Стример получает баллы только если в канале есть другие участники (зрители)
            if (members.size() > 1) {
                pointsToAdd = 200;
                reason = TransactionReason.VOICE_STREAMER;
            }
        } else {
            // Проверяем, есть ли в канале хотя бы один стример
            boolean hasStreamer = members.stream()
                    .anyMatch(m -> {
                        var mvs = m.getVoiceState();
                        return mvs != null && mvs.isStream() && !m.getId().equals(member.getId());
                    });
            
            if (hasStreamer) {
                pointsToAdd = 150;
                reason = TransactionReason.VOICE_VIEWER;
            } else {
                pointsToAdd = 100;
                reason = TransactionReason.VOICE_STANDARD;
            }
        }
        
        return new PointsInfo(pointsToAdd, reason);
    }
    
    /**
     * Получает существующего участника гильдии или создает нового
     */
    private GuildMember getOrCreateGuildMember(Guild guild, Member member) {
        return guildMemberRepository
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
    }
    
    /**
     * Актуализирует имена участника при изменении
     */
    private void updateMemberNames(GuildMember guildMember, Guild guild, Member member) {
        boolean userNameChanged = guildMember.getUserName() == null || !guildMember.getUserName().equals(member.getEffectiveName());
        boolean guildNameChanged = guildMember.getGuildName() == null || !guildMember.getGuildName().equals(guild.getName());
        
        if (userNameChanged) {
            guildMember.setUserName(member.getEffectiveName());
        }
        if (guildNameChanged) {
            guildMember.setGuildName(guild.getName());
        }
    }
    
    /**
     * Начисляет баллы участнику, если прошло достаточно времени с последнего начисления
     */
    private void awardPointsToMember(GuildMember guildMember, PointsInfo pointsInfo) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant threshold = now.minusSeconds(CHECK_INTERVAL_SECONDS);

        if (guildMember.getLastVoiceCheckAt() == null || guildMember.getLastVoiceCheckAt().isBefore(threshold)) {
            guildMember.setBalance(guildMember.getBalance() + pointsInfo.pointsToAdd);
            guildMember.setLastVoiceCheckAt(now);
            guildMemberRepository.save(guildMember);

            if (pointsInfo.reason != null) {
                PointsTransaction tx = new PointsTransaction();
                tx.setMember(guildMember);
                tx.setAmount(pointsInfo.pointsToAdd);
                tx.setReason(pointsInfo.reason);
                tx.setCreatedAt(now);
                pointsTransactionRepository.save(tx);
            }

            log.debug("Начислено {} LP пользователю {} ({}) в гильдии {} по причине {}", 
                    pointsInfo.pointsToAdd, guildMember.getUserName(), guildMember.getUserId(), guildMember.getGuildName(), pointsInfo.reason);
        }
    }

    /**
         * Вспомогательный класс для хранения информации о баллах
         */
        private record PointsInfo(int pointsToAdd, TransactionReason reason) {
    }
}