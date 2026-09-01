package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PariRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Командная часть механики пари: создание, прием ставок и смена статуса.
 * <p>
 * Прием ставки выполняется в единой транзакции под пессимистичными блокировками
 * строки пари и строки баланса участника, что исключает двойные траты при спам-кликах.
 * Начисление выигрышей вынесено в {@link PariSettlementService}.
 * <p>
 * Выплата определяется тотализатором: при объявлении исхода фиксируются общий пул,
 * призовой фонд и сумма ставок на победивший вариант, а выигрыш каждой ставки — ее доля
 * призового фонда (см. {@link PariPayoutCalculator}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PariService {

    public static final long MIN_BET = 1L;
    /** Верхняя граница одной ставки — защита от опечаток вроде лишних нулей. */
    public static final long MAX_BET = 100_000_000L;

    public static final int MAX_TITLE_LENGTH = 200;

    private final PariRepository pariRepository;
    private final PariBetRepository pariBetRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final GuildMemberService guildMemberService;

    /** Доля комиссии организатора; фиксируется в пари при создании. */
    @Value("${pari.commission-rate:0.05}")
    private BigDecimal commissionRate;

    @Transactional
    public Pari createPari(Guild guild, User author, String title) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty()) {
            throw new PariException("Название пари не может быть пустым.");
        }
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            normalizedTitle = normalizedTitle.substring(0, MAX_TITLE_LENGTH);
        }

        Pari pari = new Pari();
        pari.setGuildId(guild.getId());
        pari.setAuthorId(author.getId());
        pari.setAuthorName(author.getName());
        pari.setTitle(normalizedTitle);
        pari.setStatus(PariStatus.OPEN);
        pari.setCommissionRate(normalizedCommissionRate());
        pari.setCreatedAt(Instant.now());
        return pariRepository.save(pari);
    }

    /** Запоминает координаты сообщения-опроса, чтобы бот мог обновлять его позже. */
    @Transactional
    public void attachMessage(Long pariId, String channelId, String messageId) {
        pariRepository.findById(pariId).ifPresent(pari -> {
            pari.setChannelId(channelId);
            pari.setMessageId(messageId);
            pariRepository.save(pari);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Pari> findById(Long pariId) {
        return pariRepository.findById(pariId);
    }

    @Transactional(readOnly = true)
    public PariStats getStats(Long pariId) {
        return new PariStats(
                pariBetRepository.countByPariIdAndOption(pariId, Boolean.TRUE),
                pariBetRepository.sumAmountByPariIdAndOption(pariId, Boolean.TRUE),
                pariBetRepository.countByPariIdAndOption(pariId, Boolean.FALSE),
                pariBetRepository.sumAmountByPariIdAndOption(pariId, Boolean.FALSE)
        );
    }

    @Transactional(readOnly = true)
    public Optional<PariBet> findBet(Long pariId, String guildId, String userId) {
        return guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
                .flatMap(member -> pariBetRepository.findByPariIdAndMemberId(pariId, member.getId()));
    }

    /**
     * Принимает ставку участника: списывает сумму с баланса и создает запись ставки.
     * Проверка баланса, списание и запись ставки выполняются в единой транзакции
     * под блокировкой строки баланса (SELECT ... FOR UPDATE).
     */
    @Transactional
    public PariBet placeBet(Long pariId, Guild guild, User user, boolean option, long amount) {
        if (amount < MIN_BET) {
            throw new PariException("Ставка должна быть больше нуля.");
        }
        if (amount > MAX_BET) {
            throw new PariException("Максимальная ставка — **" + MAX_BET + "** LP.");
        }

        // Блокируем пари: пока принимается ставка, статус не сможет измениться.
        Pari pari = pariRepository.findByIdForUpdate(pariId)
                .orElseThrow(() -> new PariException("Пари не найдено."));

        if (!pari.getGuildId().equals(guild.getId())) {
            throw new PariException("Это пари создано на другом сервере.");
        }
        if (pari.getStatus() != PariStatus.OPEN) {
            throw new PariException("Прием ставок по этому пари уже закрыт.");
        }
        if (pari.getAuthorId().equals(user.getId())) {
            throw new PariException("Автор не может делать ставки в собственном пари.");
        }

        GuildMember member = guildMemberService.getOrCreateMember(guild, user);
        // Повторное чтение под блокировкой: с этого момента баланс не изменит никто другой.
        GuildMember lockedMember = guildMemberRepository.findByIdForUpdate(member.getId())
                .orElseThrow(() -> new PariException("Участник не найден."));

        // Проверку существующей ставки делаем уже под блокировкой баланса, иначе два
        // одновременных клика могли бы оба увидеть, что ставки еще нет.
        if (pariBetRepository.findByPariIdAndMemberId(pariId, lockedMember.getId()).isPresent()) {
            throw new PariException("Вы уже сделали ставку в этом пари. Изменить выбор нельзя.");
        }

        if (lockedMember.getBalance() < amount) {
            throw new PariException("Недостаточно поинтов. Нужно **" + amount + "** LP, у вас: **"
                    + lockedMember.getBalance() + "** LP.");
        }

        lockedMember.setBalance(lockedMember.getBalance() - amount);
        guildMemberRepository.save(lockedMember);

        Instant now = Instant.now();

        PariBet bet = new PariBet();
        bet.setPari(pari);
        bet.setMember(lockedMember);
        bet.setOption(option);
        bet.setAmount(amount);
        bet.setSettled(false);
        bet.setCreatedAt(now);
        try {
            // Уникальный индекс (pari_id, member_id) — последний рубеж против гонки.
            bet = pariBetRepository.saveAndFlush(bet);
        } catch (DataIntegrityViolationException e) {
            throw new PariException("Вы уже сделали ставку в этом пари. Изменить выбор нельзя.");
        }

        PointsTransaction tx = new PointsTransaction();
        tx.setMember(lockedMember);
        tx.setAmount(-amount);
        tx.setReason(TransactionReason.BET_HOLD);
        tx.setInitiatedBy(user.getId());
        tx.setReferenceId(pari.getId());
        tx.setCreatedAt(now);
        pointsTransactionRepository.save(tx);

        log.debug("Принята ставка {} LP на вариант {} в пари {} от пользователя {}",
                amount, option, pariId, user.getId());
        return bet;
    }

    /**
     * Останавливает прием ставок без объявления исхода (статус RESOLVING).
     */
    @Transactional
    public Pari closeBetting(Long pariId, String actorId) {
        Pari pari = lockForAuthor(pariId, actorId);
        if (pari.getStatus() != PariStatus.OPEN) {
            throw new PariException("Прием ставок уже остановлен.");
        }
        pari.setStatus(PariStatus.RESOLVING);
        return pariRepository.save(pari);
    }

    /**
     * Объявляет победивший вариант и фиксирует итоги розыгрыша: общий пул, призовой фонд,
     * сумму ставок на победивший вариант и коэффициент.
     * <p>
     * Итоги считаются ровно один раз — здесь, под блокировкой строки пари, когда новые ставки
     * уже невозможны. Начисление выполняется отдельно и идемпотентно в
     * {@link PariSettlementService}: расчет каждой ставки опирается только на сохраненные
     * итоги, поэтому повторный запуск дает тот же результат.
     */
    @Transactional
    public Pari finish(Long pariId, String actorId, boolean winningOption) {
        Pari pari = lockForAuthor(pariId, actorId);
        requireActive(pari);

        PariStats stats = getStats(pariId);
        long totalPool = stats.totalPool();
        long winningSum = winningOption ? stats.yesPool() : stats.noPool();
        long prizePool = PariPayoutCalculator.prizePool(totalPool, pari.getCommissionRate());

        pari.setStatus(PariStatus.FINISHED);
        pari.setWinningOption(winningOption);
        pari.setTotalPool(totalPool);
        pari.setPrizePool(prizePool);
        pari.setWinningSum(winningSum);
        pari.setWinningCoefficient(PariPayoutCalculator.coefficient(prizePool, winningSum));
        pari.setClosedAt(Instant.now());

        if (winningSum == 0) {
            // Ставок на победивший вариант нет — делить призовой фонд не между кем,
            // поэтому расчет вернет всем участникам их ставки.
            log.info("В пари {} нет ставок на победивший вариант, средства будут возвращены", pariId);
        }
        return pariRepository.save(pari);
    }

    /**
     * Отменяет пари. Возврат заблокированных ставок выполняется отдельным расчетом.
     *
     * @param actorId id автора; {@code null} — системная отмена по тайм-ауту
     */
    @Transactional
    public Pari cancel(Long pariId, String actorId) {
        Pari pari = actorId == null
                ? pariRepository.findByIdForUpdate(pariId)
                        .orElseThrow(() -> new PariException("Пари не найдено."))
                : lockForAuthor(pariId, actorId);
        requireActive(pari);
        pari.setStatus(PariStatus.CANCELED);
        pari.setClosedAt(Instant.now());
        return pariRepository.save(pari);
    }

    /** Пари, которые слишком долго висят открытыми и подлежат отмене по тайм-ауту. */
    @Transactional(readOnly = true)
    public List<Pari> findExpiredParis(Instant threshold) {
        List<Pari> expired = new ArrayList<>(pariRepository.findByStatusAndCreatedAtBefore(PariStatus.OPEN, threshold));
        expired.addAll(pariRepository.findByStatusAndCreatedAtBefore(PariStatus.RESOLVING, threshold));
        return expired;
    }

    private Pari lockForAuthor(Long pariId, String actorId) {
        Pari pari = pariRepository.findByIdForUpdate(pariId)
                .orElseThrow(() -> new PariException("Пари не найдено."));
        if (!pari.getAuthorId().equals(actorId)) {
            throw new PariException("Управлять пари может только его автор.");
        }
        return pari;
    }

    /** Текущие коэффициенты вариантов при действующей ставке комиссии. */
    @Transactional(readOnly = true)
    public PariOdds getOdds(Pari pari) {
        return PariOdds.of(getStats(pari.getId()), pari.getCommissionRate());
    }

    private BigDecimal normalizedCommissionRate() {
        if (commissionRate == null || commissionRate.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (commissionRate.compareTo(BigDecimal.ONE) >= 0) {
            log.warn("Комиссия {} вне диапазона [0, 1), используется 0", commissionRate);
            return BigDecimal.ZERO;
        }
        return commissionRate;
    }

    private void requireActive(Pari pari) {
        if (pari.getStatus() != PariStatus.OPEN && pari.getStatus() != PariStatus.RESOLVING) {
            throw new PariException("Пари уже завершено или отменено.");
        }
    }
}
