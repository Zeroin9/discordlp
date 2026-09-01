package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Расчет одной ставки. Выплата берется из итогов, зафиксированных в пари при объявлении
 * исхода, поэтому все фикстуры задают {@code prizePool} и {@code winningSum} явно.
 */
@ExtendWith(MockitoExtension.class)
class PariPayoutProcessorTest {

    @Mock
    private PariBetRepository pariBetRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private PointsTransactionRepository pointsTransactionRepository;

    @InjectMocks
    private PariPayoutProcessor processor;

    @Test
    void winnerReceivesShareOfPrizePool() {
        // пул 1000, комиссия 5% → призовой фонд 950; на «Да» поставлено 500, из них 300 — эта ставка
        GuildMember member = member(1L, 500L);
        Pari pari = finishedPari(Boolean.TRUE, 1_000L, 950L, 500L);
        PariBet bet = bet(10L, pari, member, Boolean.TRUE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThat(processor.settleBet(10L)).isTrue();

        assertThat(bet.getPayout()).isEqualTo(570L);
        assertThat(member.getBalance()).isEqualTo(500L + 570L);
        assertThat(bet.isSettled()).isTrue();

        PointsTransaction tx = savedTransaction();
        assertThat(tx.getAmount()).isEqualTo(570L);
        assertThat(tx.getReason()).isEqualTo(TransactionReason.BET_WIN);
        assertThat(tx.getReferenceId()).isEqualTo(pari.getId());
    }

    @Test
    void soleWinnerTakesThePrizePool() {
        GuildMember member = member(1L, 0L);
        Pari pari = finishedPari(Boolean.FALSE, 1_000L, 950L, 200L);
        PariBet bet = bet(11L, pari, member, Boolean.FALSE, 200L, false);

        when(pariBetRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        processor.settleBet(11L);

        assertThat(bet.getPayout()).isEqualTo(950L);
        assertThat(member.getBalance()).isEqualTo(950L);
    }

    @Test
    void payoutIsRoundedDown() {
        // 100 * 950 / 300 = 316.66...
        GuildMember member = member(1L, 0L);
        Pari pari = finishedPari(Boolean.TRUE, 1_000L, 950L, 300L);
        PariBet bet = bet(12L, pari, member, Boolean.TRUE, 100L, false);

        when(pariBetRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        processor.settleBet(12L);

        assertThat(bet.getPayout()).isEqualTo(316L);
    }

    @Test
    void loserReceivesNothing() {
        GuildMember member = member(1L, 500L);
        Pari pari = finishedPari(Boolean.TRUE, 1_000L, 950L, 500L);
        PariBet bet = bet(13L, pari, member, Boolean.FALSE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(bet));

        assertThat(processor.settleBet(13L)).isTrue();

        assertThat(member.getBalance()).isEqualTo(500L);
        assertThat(bet.isSettled()).isTrue();
        assertThat(bet.getPayout()).isZero();
        verifyNoInteractions(pointsTransactionRepository);
        verify(guildMemberRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void betsAreRefundedWhenNobodyPickedTheWinningOption() {
        GuildMember member = member(1L, 0L);
        Pari pari = finishedPari(Boolean.TRUE, 300L, 285L, 0L);
        PariBet bet = bet(14L, pari, member, Boolean.FALSE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThat(processor.settleBet(14L)).isTrue();

        // ставка возвращается целиком, комиссия не удерживается
        assertThat(member.getBalance()).isEqualTo(300L);
        assertThat(bet.getPayout()).isEqualTo(300L);
        assertThat(savedTransaction().getReason()).isEqualTo(TransactionReason.BET_REFUND);
    }

    @Test
    void canceledPariRefundsFullBet() {
        GuildMember member = member(1L, 200L);
        Pari pari = pari(PariStatus.CANCELED, null);
        PariBet bet = bet(15L, pari, member, Boolean.TRUE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThat(processor.settleBet(15L)).isTrue();

        assertThat(member.getBalance()).isEqualTo(500L);
        assertThat(bet.getPayout()).isEqualTo(300L);
        assertThat(savedTransaction().getReason()).isEqualTo(TransactionReason.BET_REFUND);
    }

    @Test
    void alreadySettledBetIsNotPaidTwice() {
        GuildMember member = member(1L, 1_100L);
        Pari pari = finishedPari(Boolean.TRUE, 1_000L, 950L, 500L);
        PariBet bet = bet(16L, pari, member, Boolean.TRUE, 300L, true);

        when(pariBetRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(bet));

        assertThat(processor.settleBet(16L)).isFalse();

        assertThat(member.getBalance()).isEqualTo(1_100L);
        verifyNoInteractions(pointsTransactionRepository);
        verify(pariBetRepository, never()).save(any());
    }

    @Test
    void settlingAnOpenPariIsRefused() {
        GuildMember member = member(1L, 500L);
        Pari pari = pari(PariStatus.OPEN, null);
        PariBet bet = bet(17L, pari, member, Boolean.TRUE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> processor.settleBet(17L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPEN");

        assertThat(bet.isSettled()).isFalse();
        assertThat(member.getBalance()).isEqualTo(500L);
    }

    @Test
    void missingMemberAbortsSettlement() {
        GuildMember member = member(1L, 500L);
        Pari pari = finishedPari(Boolean.TRUE, 1_000L, 950L, 500L);
        PariBet bet = bet(18L, pari, member, Boolean.TRUE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(18L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.settleBet(18L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(bet.isSettled()).isFalse();
        verifyNoInteractions(pointsTransactionRepository);
    }

    @Test
    void missingBetIsSkipped() {
        when(pariBetRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThat(processor.settleBet(99L)).isFalse();

        verifyNoInteractions(pointsTransactionRepository);
        verifyNoInteractions(guildMemberRepository);
    }

    private PointsTransaction savedTransaction() {
        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static GuildMember member(Long id, long balance) {
        GuildMember member = new GuildMember();
        member.setId(id);
        member.setGuildId("guild");
        member.setUserId("user-" + id);
        member.setBalance(balance);
        return member;
    }

    private static Pari pari(PariStatus status, Boolean winningOption) {
        Pari pari = new Pari();
        pari.setId(42L);
        pari.setGuildId("guild");
        pari.setAuthorId("author");
        pari.setTitle("Тестовое пари");
        pari.setStatus(status);
        pari.setWinningOption(winningOption);
        pari.setCreatedAt(Instant.now());
        return pari;
    }

    private static Pari finishedPari(Boolean winningOption, long totalPool, long prizePool, long winningSum) {
        Pari pari = pari(PariStatus.FINISHED, winningOption);
        pari.setTotalPool(totalPool);
        pari.setPrizePool(prizePool);
        pari.setWinningSum(winningSum);
        pari.setWinningCoefficient(PariPayoutCalculator.coefficient(prizePool, winningSum));
        return pari;
    }

    private static PariBet bet(Long id, Pari pari, GuildMember member, Boolean option, long amount, boolean settled) {
        PariBet bet = new PariBet();
        bet.setId(id);
        bet.setPari(pari);
        bet.setMember(member);
        bet.setOption(option);
        bet.setAmount(amount);
        bet.setSettled(settled);
        bet.setCreatedAt(Instant.now());
        return bet;
    }
}
