package ru.z3r0ing.discordlp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.z3r0ing.discordlp.config.FlywayConfig;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PariRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.service.GuildMemberService;
import ru.z3r0ing.discordlp.service.PariException;
import ru.z3r0ing.discordlp.service.PariPayoutProcessor;
import ru.z3r0ing.discordlp.service.PariService;
import ru.z3r0ing.discordlp.service.PariStats;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты пари на настоящем PostgreSQL: проверяют схему из миграций,
 * блокировки при приеме ставок и идемпотентность выплат.
 * <p>
 * Тестовые транзакции отключены (NOT_SUPPORTED), чтобы каждый вызов сервиса открывал
 * собственную транзакцию — иначе пессимистичные блокировки и расчет в отдельных
 * транзакциях проверить нельзя.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayConfig.class, PariService.class, GuildMemberService.class, PariPayoutProcessor.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PariFlowIntegrationTest extends PostgresContainerTest {

    @Autowired
    private PariService pariService;
    @Autowired
    private PariPayoutProcessor payoutProcessor;
    @Autowired
    private PariRepository pariRepository;
    @Autowired
    private PariBetRepository pariBetRepository;
    @Autowired
    private GuildMemberRepository guildMemberRepository;
    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    private final String guildId = "guild-" + UUID.randomUUID();

    @Test
    void winnersArePaidTwiceTheirBetAndLosersGetNothing() {
        Guild guild = guild();
        User author = user("author");
        User winner = user("winner");
        User loser = user("loser");
        giveBalance(guild, winner, 1_000L);
        giveBalance(guild, loser, 1_000L);

        Pari pari = pariService.createPari(guild, author, "Победит ли команда?");

        pariService.placeBet(pari.getId(), guild, winner, true, 300L);
        pariService.placeBet(pari.getId(), guild, loser, false, 200L);

        // ставка списывается сразу
        assertThat(balanceOf(winner)).isEqualTo(700L);
        assertThat(balanceOf(loser)).isEqualTo(800L);

        PariStats stats = pariService.getStats(pari.getId());
        assertThat(stats.yesCount()).isEqualTo(1);
        assertThat(stats.yesPool()).isEqualTo(300L);
        assertThat(stats.totalPool()).isEqualTo(500L);

        pariService.finish(pari.getId(), author.getId(), true);
        settleAll(pari.getId());

        assertThat(balanceOf(winner)).isEqualTo(1_300L);
        assertThat(balanceOf(loser)).isEqualTo(800L);
        assertThat(pariRepository.findById(pari.getId()).orElseThrow().getStatus())
                .isEqualTo(PariStatus.FINISHED);
    }

    @Test
    void repeatedSettlementDoesNotPayTwice() {
        Guild guild = guild();
        User author = user("author");
        User winner = user("winner");
        giveBalance(guild, winner, 500L);

        Pari pari = pariService.createPari(guild, author, "Идемпотентность");
        pariService.placeBet(pari.getId(), guild, winner, true, 500L);
        pariService.finish(pari.getId(), author.getId(), true);

        settleAll(pari.getId());
        settleAll(pari.getId());
        settleAll(pari.getId());

        assertThat(balanceOf(winner)).isEqualTo(1_000L);
        assertThat(transactionsOf(winner, TransactionReason.BET_WIN)).hasSize(1);
    }

    @Test
    void cancelRefundsEveryBet() {
        Guild guild = guild();
        User author = user("author");
        User better = user("better");
        giveBalance(guild, better, 500L);

        Pari pari = pariService.createPari(guild, author, "Отменяемое пари");
        pariService.placeBet(pari.getId(), guild, better, true, 500L);
        assertThat(balanceOf(better)).isZero();

        pariService.cancel(pari.getId(), author.getId());
        settleAll(pari.getId());

        assertThat(balanceOf(better)).isEqualTo(500L);
        assertThat(transactionsOf(better, TransactionReason.BET_REFUND)).hasSize(1);
    }

    @Test
    void betIsRecordedWithHoldTransaction() {
        Guild guild = guild();
        User author = user("author");
        User better = user("better");
        giveBalance(guild, better, 500L);

        Pari pari = pariService.createPari(guild, author, "Лог транзакций");
        pariService.placeBet(pari.getId(), guild, better, false, 120L);

        List<PointsTransaction> holds = transactionsOf(better, TransactionReason.BET_HOLD);
        assertThat(holds).hasSize(1);
        assertThat(holds.get(0).getAmount()).isEqualTo(-120);
        assertThat(holds.get(0).getReferenceId()).isEqualTo(pari.getId());
    }

    @Test
    void concurrentClicksProduceExactlyOneBet() throws Exception {
        Guild guild = guild();
        User author = user("author");
        User spammer = user("spammer");
        giveBalance(guild, spammer, 1_000L);

        Pari pari = pariService.createPari(guild, author, "Спам-клики");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    pariService.placeBet(pari.getId(), guild, spammer, true, 900L);
                    accepted.incrementAndGet();
                } catch (Exception expected) {
                    // принята должна быть ровно одна ставка, остальные попытки отклоняются
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(balanceOf(spammer)).isEqualTo(100L);
    }

    @Test
    void databaseRejectsNegativeBalance() {
        Guild guild = guild();
        User user = user("poor");
        GuildMember member = giveBalance(guild, user, 10L);
        member.setBalance(-1L);

        assertThatThrownBy(() -> guildMemberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bettingIsClosedAfterPariIsFinished() {
        Guild guild = guild();
        User author = user("author");
        User late = user("late");
        giveBalance(guild, late, 500L);

        Pari pari = pariService.createPari(guild, author, "Поздняя ставка");
        pariService.finish(pari.getId(), author.getId(), false);

        assertThatThrownBy(() -> pariService.placeBet(pari.getId(), guild, late, true, 100L))
                .isInstanceOf(PariException.class);
        assertThat(balanceOf(late)).isEqualTo(500L);
    }

    private void settleAll(Long pariId) {
        pariBetRepository.findUnsettledBetIds(pariId, PageRequest.of(0, 100))
                .forEach(payoutProcessor::settleBet);
    }

    private GuildMember giveBalance(Guild guild, User user, long balance) {
        GuildMember member = new GuildMember();
        member.setGuildId(guild.getId());
        member.setUserId(user.getId());
        member.setUserName(user.getName());
        member.setGuildName(guild.getName());
        member.setBalance(balance);
        return guildMemberRepository.save(member);
    }

    private long balanceOf(User user) {
        return guildMemberRepository.findByGuildIdAndUserId(guildId, user.getId()).orElseThrow().getBalance();
    }

    private List<PointsTransaction> transactionsOf(User user, TransactionReason reason) {
        GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, user.getId()).orElseThrow();
        return pointsTransactionRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(tx -> tx.getReason() == reason)
                .toList();
    }

    private Guild guild() {
        Guild guild = Mockito.mock(Guild.class);
        Mockito.lenient().when(guild.getId()).thenReturn(guildId);
        Mockito.lenient().when(guild.getName()).thenReturn("Test Guild");
        return guild;
    }

    private static User user(String name) {
        String id = name + "-" + UUID.randomUUID();
        User user = Mockito.mock(User.class);
        Mockito.lenient().when(user.getId()).thenReturn(id);
        Mockito.lenient().when(user.getName()).thenReturn(name);
        return user;
    }
}
