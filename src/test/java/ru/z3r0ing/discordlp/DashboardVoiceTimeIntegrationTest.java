package ru.z3r0ing.discordlp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import ru.z3r0ing.discordlp.config.FlywayConfig;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.service.DashboardMemberView;
import ru.z3r0ing.discordlp.service.DashboardService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет на настоящем PostgreSQL, что дашборд восстанавливает время в конференции
 * из журнала начислений: агрегат считается в БД, неголосовые причины в него не попадают.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayConfig.class, DashboardService.class})
class DashboardVoiceTimeIntegrationTest extends PostgresContainerTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private GuildMemberRepository guildMemberRepository;
    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Test
    void restoresVoiceTimeFromTransactionLog() {
        String guildId = UUID.randomUUID().toString();
        GuildMember active = member(guildId, "active");
        GuildMember silent = member(guildId, "silent");

        // 3 начисления по 100 LP = 15 минут, 2 по 150 LP = 10 минут, 1 по 200 LP = 5 минут
        award(active, TransactionReason.VOICE_STANDARD, 100L, 100L, 100L);
        award(active, TransactionReason.VOICE_VIEWER, 150L, 150L);
        award(active, TransactionReason.VOICE_STREAMER, 200L);
        // Баллы не за голос во время не превращаются
        award(active, TransactionReason.ADMIN_MANUAL, 50_000L);
        award(silent, TransactionReason.BET_WIN, 10_000L);

        // Сортировка по id, desc гарантирует, что только что созданные участники попадут на первую страницу
        List<DashboardMemberView> rows = dashboardService.getGuildMembersPage(0, 50, "id,desc")
                .getContent().stream()
                .filter(row -> guildId.equals(row.member().getGuildId()))
                .toList();

        assertThat(rows).extracting(row -> row.member().getUserName())
                .containsExactly("silent", "active");
        DashboardMemberView activeRow = rows.get(1);
        DashboardMemberView silentRow = rows.get(0);
        assertThat(activeRow.voiceTime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(activeRow.voiceTimeText()).isEqualTo("30 мин");
        assertThat(silentRow.voiceTime()).isEqualTo(Duration.ZERO);
        assertThat(silentRow.voiceTimeText()).isEqualTo("0 мин");
    }

    private GuildMember member(String guildId, String userName) {
        GuildMember member = new GuildMember();
        member.setGuildId(guildId);
        member.setUserId(UUID.randomUUID().toString());
        member.setGuildName("Guild");
        member.setUserName(userName);
        member.setBalance(0L);
        return guildMemberRepository.save(member);
    }

    private void award(GuildMember member, TransactionReason reason, Long... amounts) {
        for (Long amount : amounts) {
            PointsTransaction tx = new PointsTransaction();
            tx.setMember(member);
            tx.setAmount(amount);
            tx.setReason(reason);
            tx.setCreatedAt(Instant.now());
            pointsTransactionRepository.save(tx);
        }
    }
}
