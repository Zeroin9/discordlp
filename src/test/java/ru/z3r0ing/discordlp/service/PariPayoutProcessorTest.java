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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void winnerReceivesDoubleBet() {
        GuildMember member = member(1L, 500L);
        Pari pari = pari(PariStatus.FINISHED, Boolean.TRUE);
        PariBet bet = bet(10L, pari, member, Boolean.TRUE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThat(processor.settleBet(10L)).isTrue();

        assertThat(member.getBalance()).isEqualTo(500L + 600L);
        assertThat(bet.isSettled()).isTrue();
        assertThat(bet.getPayout()).isEqualTo(600L);

        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(600);
        assertThat(captor.getValue().getReason()).isEqualTo(TransactionReason.BET_WIN);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(pari.getId());
    }

    @Test
    void loserReceivesNothing() {
        GuildMember member = member(1L, 500L);
        Pari pari = pari(PariStatus.FINISHED, Boolean.TRUE);
        PariBet bet = bet(11L, pari, member, Boolean.FALSE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(bet));

        assertThat(processor.settleBet(11L)).isTrue();

        assertThat(member.getBalance()).isEqualTo(500L);
        assertThat(bet.isSettled()).isTrue();
        assertThat(bet.getPayout()).isZero();
        verifyNoInteractions(pointsTransactionRepository);
        verify(guildMemberRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void canceledPariRefundsFullBet() {
        GuildMember member = member(1L, 200L);
        Pari pari = pari(PariStatus.CANCELED, null);
        PariBet bet = bet(12L, pari, member, Boolean.TRUE, 300L, false);

        when(pariBetRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(bet));
        when(guildMemberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThat(processor.settleBet(12L)).isTrue();

        assertThat(member.getBalance()).isEqualTo(500L);
        assertThat(bet.getPayout()).isEqualTo(300L);

        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(TransactionReason.BET_REFUND);
    }

    @Test
    void alreadySettledBetIsNotPaidTwice() {
        GuildMember member = member(1L, 1_100L);
        Pari pari = pari(PariStatus.FINISHED, Boolean.TRUE);
        PariBet bet = bet(13L, pari, member, Boolean.TRUE, 300L, true);

        when(pariBetRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(bet));

        assertThat(processor.settleBet(13L)).isFalse();

        assertThat(member.getBalance()).isEqualTo(1_100L);
        verifyNoInteractions(pointsTransactionRepository);
        verify(pariBetRepository, never()).save(any());
    }

    @Test
    void missingBetIsSkipped() {
        when(pariBetRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThat(processor.settleBet(99L)).isFalse();

        verifyNoInteractions(pointsTransactionRepository);
        verifyNoInteractions(guildMemberRepository);
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
