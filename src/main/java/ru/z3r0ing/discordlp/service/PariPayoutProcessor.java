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

        Pari pari = bet.getPari();
        long payout;
        TransactionReason reason;
        switch (pari.getStatus()) {
            case CANCELED -> {
                payout = bet.getAmount();
                reason = TransactionReason.BET_REFUND;
            }
            case FINISHED -> {
                boolean won = Objects.equals(pari.getWinningOption(), bet.getOption());
                payout = won ? bet.getAmount() * PariService.WIN_MULTIPLIER : 0L;
                reason = TransactionReason.BET_WIN;
            }
            default -> throw new IllegalStateException(
                    "Расчет по пари " + pari.getId() + " в статусе " + pari.getStatus() + " невозможен");
        }

        Instant now = Instant.now();

        if (payout > 0) {
            GuildMember member = guildMemberRepository.findByIdForUpdate(bet.getMember().getId())
                    .orElseThrow(() -> new IllegalStateException("Участник ставки " + betId + " не найден"));
            member.setBalance(member.getBalance() + payout);
            guildMemberRepository.save(member);

            PointsTransaction tx = new PointsTransaction();
            tx.setMember(member);
            tx.setAmount(Math.toIntExact(payout));
            tx.setReason(reason);
            tx.setInitiatedBy(pari.getAuthorId());
            tx.setReferenceId(pari.getId());
            tx.setCreatedAt(now);
            pointsTransactionRepository.save(tx);
        }

        bet.setSettled(true);
        bet.setPayout(payout);
        bet.setSettledAt(now);
        pariBetRepository.save(bet);
        return true;
    }
}
