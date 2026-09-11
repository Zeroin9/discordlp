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
 * Проверяет на настоящем PostgreSQL, что таблица участников восстанавливает время
 * в конференции из журнала начислений: агрегат считается в БД, неголосовые причины
 * в него не попадают, время делится на «со стримом» и «без стрима», а для отчетов
 * за период учитываются только начисления нужной давности.
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
        member(guildId, "silent");

        // 3 начисления по 100 LP = 15 минут без стрима,
        // 2 по 150 LP = 10 минут и 1 по 200 LP = 5 минут — со стримом
        award(active, TransactionReason.VOICE_STANDARD, Instant.now(), 100L, 100L, 100L);
        award(active, TransactionReason.VOICE_VIEWER, Instant.now(), 150L, 150L);
        award(active, TransactionReason.VOICE_STREAMER, Instant.now(), 200L);
        // Баллы не за голос во время не превращаются
        award(active, TransactionReason.ADMIN_MANUAL, Instant.now(), 50_000L);

        List<DashboardMemberView> rows = dashboardService.getGuildMembers(guildId, null, "voiceTime,desc");

        assertThat(rows).extracting(row -> row.member().getUserName()).containsExactly("active", "silent");

        DashboardMemberView activeRow = rows.getFirst();
        assertThat(activeRow.totalTime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(activeRow.streamTime()).isEqualTo(Duration.ofMinutes(15));
        assertThat(activeRow.noStreamTime()).isEqualTo(Duration.ofMinutes(15));
        assertThat(activeRow.totalTimeText()).isEqualTo("30 мин");

        DashboardMemberView silentRow = rows.get(1);
        assertThat(silentRow.totalTime()).isEqualTo(Duration.ZERO);
        assertThat(silentRow.totalTimeText()).isEqualTo("0 мин");
    }

    @Test
    void countsOnlyTransactionsInsideThePeriod() {
        String guildId = UUID.randomUUID().toString();
        GuildMember member = member(guildId, "regular");

        Instant now = Instant.now();
        // Внутри недели: 2 интервала по 100 LP = 10 минут без стрима
        award(member, TransactionReason.VOICE_STANDARD, now.minus(Duration.ofDays(2)), 100L, 100L);
        // Старше недели — в сводку попасть не должно
        award(member, TransactionReason.VOICE_STANDARD, now.minus(Duration.ofDays(30)), 100L, 100L, 100L);
        award(member, TransactionReason.VOICE_STREAMER, now.minus(Duration.ofDays(30)), 200L);

        List<DashboardMemberView> week =
                dashboardService.getGuildMembers(guildId, now.minus(Duration.ofDays(7)), "voiceTime,desc");
        List<DashboardMemberView> allTime = dashboardService.getGuildMembers(guildId, null, "voiceTime,desc");

        assertThat(week.getFirst().totalTime()).isEqualTo(Duration.ofMinutes(10));
        assertThat(week.getFirst().streamTime()).isEqualTo(Duration.ZERO);
        assertThat(allTime.getFirst().totalTime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(allTime.getFirst().streamTime()).isEqualTo(Duration.ofMinutes(5));
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

    private void award(GuildMember member, TransactionReason reason, Instant createdAt, Long... amounts) {
        for (Long amount : amounts) {
            PointsTransaction tx = new PointsTransaction();
            tx.setMember(member);
            tx.setAmount(amount);
            tx.setReason(reason);
            tx.setCreatedAt(createdAt);
            pointsTransactionRepository.save(tx);
        }
    }
}
