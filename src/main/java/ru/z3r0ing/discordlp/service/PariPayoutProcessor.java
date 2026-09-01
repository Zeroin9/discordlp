package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;

import java.time.Instant;
import java.util.Objects;

/**
 * Расчет по одной ставке. Вынесен в отдельный бин, чтобы каждая ставка обрабатывалась
 * в собственной транзакции: сбой на одном участнике не откатывает выплаты остальным.
 * <p>
 * Выплата берется из итогов, зафиксированных в пари при объявлении исхода
 * ({@code prize_pool} и {@code winning_sum}), и никогда не пересчитывается по текущему
 * состоянию пула. Благодаря этому результат не зависит ни от момента запуска расчета,
 * ни от порядка обработки ставок.
 * <p>
 * Идемпотентность: строка ставки блокируется через SELECT ... FOR UPDATE, после чего
 * проверяется флаг {@code settled}. Начисление и установка флага коммитятся вместе,
 * поэтому повторный запуск расчета (в том числе после рестарта сервиса) не выплатит дважды.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PariPayoutProcessor {

    private final PariBetRepository pariBetRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    /**
     * @return {@code true}, если расчет по ставке был выполнен именно этим вызовом
     */
    @Transactional
    public boolean settleBet(Long betId) {
        PariBet bet = pariBetRepository.findByIdForUpdate(betId).orElse(null);
        if (bet == null) {
            log.warn("Ставка {} не найдена при расчете", betId);
            return false;
        }
        if (bet.isSettled()) {
            // Уже рассчитана — повторное начисление исключено.
            return false;
        }

        Payout payout = resolvePayout(bet);
        Instant now = Instant.now();

        if (payout.amount() > 0) {
            GuildMember member = guildMemberRepository.findByIdForUpdate(bet.getMember().getId())
                    .orElseThrow(() -> new IllegalStateException("Участник ставки " + betId + " не найден"));
            member.setBalance(member.getBalance() + payout.amount());
            guildMemberRepository.save(member);

            PointsTransaction tx = new PointsTransaction();
            tx.setMember(member);
            tx.setAmount(payout.amount());
            tx.setReason(payout.reason());
            tx.setInitiatedBy(bet.getPari().getAuthorId());
            tx.setReferenceId(bet.getPari().getId());
            tx.setCreatedAt(now);
            pointsTransactionRepository.save(tx);
        }

        bet.setSettled(true);
        bet.setPayout(payout.amount());
        bet.setSettledAt(now);
        pariBetRepository.save(bet);
        return true;
    }

    private Payout resolvePayout(PariBet bet) {
        Pari pari = bet.getPari();
        return switch (pari.getStatus()) {
            case CANCELED -> new Payout(bet.getAmount(), TransactionReason.BET_REFUND);
            case FINISHED -> resolveFinishedPayout(bet, pari);
            default -> throw new IllegalStateException(
                    "Расчет по пари " + pari.getId() + " в статусе " + pari.getStatus() + " невозможен");
        };
    }

    private Payout resolveFinishedPayout(PariBet bet, Pari pari) {
        long winningSum = pari.getWinningSum() == null ? 0L : pari.getWinningSum();
        if (winningSum <= 0) {
            // На победивший вариант никто не поставил: делить призовой фонд не между кем,
            // поэтому ставки возвращаются всем участникам.
            return new Payout(bet.getAmount(), TransactionReason.BET_REFUND);
        }

        if (!Objects.equals(pari.getWinningOption(), bet.getOption())) {
            return new Payout(0L, TransactionReason.BET_WIN);
        }

        long prizePool = pari.getPrizePool() == null ? 0L : pari.getPrizePool();
        return new Payout(PariPayoutCalculator.payout(bet.getAmount(), prizePool, winningSum),
                TransactionReason.BET_WIN);
    }

    private record Payout(long amount, TransactionReason reason) {
    }
}
