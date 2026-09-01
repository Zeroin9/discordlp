package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PariRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PariSettlementServiceTest {

    @Mock
    private PariRepository pariRepository;
    @Mock
    private PariBetRepository pariBetRepository;
    @Mock
    private PariPayoutProcessor pariPayoutProcessor;
    @Mock
    private PariService pariService;
    @Mock
    private PariMessageService pariMessageService;

    @InjectMocks
    private PariSettlementService settlementService;

    // --- расчет ---

    @Test
    void settleProcessesEveryBatchAndMarksPariSettled() {
        Pari pari = pari(PariStatus.FINISHED);
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(10L, 11L), List.of(12L), List.of(), List.of());
        when(pariPayoutProcessor.settleBet(anyLong())).thenReturn(true);
        when(pariRepository.findById(1L)).thenReturn(Optional.of(pari));

        assertThat(settlementService.settle(1L)).isEqualTo(3);

        verify(pariPayoutProcessor).settleBet(10L);
        verify(pariPayoutProcessor).settleBet(11L);
        verify(pariPayoutProcessor).settleBet(12L);
        assertThat(pari.getSettledAt()).isNotNull();
    }

    @Test
    void settleKeepsGoingWhenOneBetFails() {
        Pari pari = pari(PariStatus.FINISHED);
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(10L, 11L), List.of(), List.of());
        when(pariPayoutProcessor.settleBet(10L)).thenThrow(new IllegalStateException("boom"));
        when(pariPayoutProcessor.settleBet(11L)).thenReturn(true);
        when(pariRepository.findById(1L)).thenReturn(Optional.of(pari));

        assertThat(settlementService.settle(1L)).isEqualTo(1);
        verify(pariPayoutProcessor).settleBet(11L);
    }

    @Test
    void settleStopsWithoutMarkingWhenNoProgressIsMade() {
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(10L));
        when(pariPayoutProcessor.settleBet(10L)).thenThrow(new IllegalStateException("boom"));

        assertThat(settlementService.settle(1L)).isZero();

        verify(pariRepository, never()).save(any());
    }

    @Test
    void markSettledSkipsPariWithOutstandingBets() {
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class))).thenReturn(List.of(10L));

        settlementService.markSettled(1L);

        verify(pariRepository, never()).findById(anyLong());
    }

    @Test
    void markSettledIsIdempotent() {
        Pari pari = pari(PariStatus.FINISHED);
        Instant alreadySettled = Instant.parse("2024-01-01T00:00:00Z");
        pari.setSettledAt(alreadySettled);
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class))).thenReturn(List.of());
        when(pariRepository.findById(1L)).thenReturn(Optional.of(pari));

        settlementService.markSettled(1L);

        assertThat(pari.getSettledAt()).isEqualTo(alreadySettled);
        verify(pariRepository, never()).save(any());
    }

    // --- восстановление после сбоя ---

    @Test
    void recoveryFinishesInterruptedSettlements() {
        Pari pending = pari(PariStatus.FINISHED);
        when(pariRepository.findByStatusInAndSettledAtIsNull(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(10L), List.of(), List.of());
        when(pariPayoutProcessor.settleBet(10L)).thenReturn(true);
        when(pariRepository.findById(1L)).thenReturn(Optional.of(pending));

        settlementService.recoverPendingSettlements();

        verify(pariPayoutProcessor).settleBet(10L);
        verify(pariMessageService).refresh(1L);
    }

    @Test
    void recoverySurvivesRepositoryFailure() {
        when(pariRepository.findByStatusInAndSettledAtIsNull(anyCollection(), any(Pageable.class)))
                .thenThrow(new IllegalStateException("db down"));

        settlementService.recoverPendingSettlements();

        verify(pariPayoutProcessor, never()).settleBet(anyLong());
    }

    // --- тайм-аут ---

    @Test
    void timeoutCancelsStalePariAndRefundsBets() {
        ReflectionTestUtils.setField(settlementService, "timeoutHours", 24L);
        Pari stale = pari(PariStatus.OPEN);
        when(pariService.findExpiredParis(any(Instant.class))).thenReturn(List.of(stale));
        when(pariBetRepository.findUnsettledBetIds(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(10L), List.of(), List.of());
        when(pariPayoutProcessor.settleBet(10L)).thenReturn(true);
        when(pariRepository.findById(1L)).thenReturn(Optional.of(stale));

        settlementService.cancelTimedOutParis();

        verify(pariService).cancel(1L, null);
        verify(pariPayoutProcessor).settleBet(10L);
        verify(pariMessageService).refresh(1L);
    }

    @Test
    void timeoutIsDisabledWhenConfiguredToZero() {
        ReflectionTestUtils.setField(settlementService, "timeoutHours", 0L);

        settlementService.cancelTimedOutParis();

        verify(pariService, never()).findExpiredParis(any());
    }

    @Test
    void timeoutIgnoresPariClosedInTheMeantime() {
        ReflectionTestUtils.setField(settlementService, "timeoutHours", 24L);
        Pari stale = pari(PariStatus.OPEN);
        when(pariService.findExpiredParis(any(Instant.class))).thenReturn(List.of(stale));
        when(pariService.cancel(1L, null)).thenThrow(new PariException("Пари уже завершено или отменено."));

        settlementService.cancelTimedOutParis();

        verify(pariPayoutProcessor, never()).settleBet(anyLong());
    }

    @Test
    void timeoutSurvivesRepositoryFailure() {
        ReflectionTestUtils.setField(settlementService, "timeoutHours", 24L);
        when(pariService.findExpiredParis(any(Instant.class))).thenThrow(new IllegalStateException("db down"));

        settlementService.cancelTimedOutParis();

        verify(pariService, never()).cancel(anyLong(), any());
    }

    private static Pari pari(PariStatus status) {
        Pari pari = new Pari();
        pari.setId(1L);
        pari.setGuildId("guild-1");
        pari.setAuthorId("author-1");
        pari.setTitle("Пари");
        pari.setStatus(status);
        pari.setCreatedAt(Instant.now());
        return pari;
    }
}
