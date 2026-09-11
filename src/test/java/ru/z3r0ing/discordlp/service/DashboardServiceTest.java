package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.repository.VoicePointsSum;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private PointsTransactionRepository pointsTransactionRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void appliesRequestedSortAndPaging() {
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        dashboardService.getGuildMembersPage(2, 25, "userName,asc");

        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().getOrderFor("userName"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void descendingIsUsedForAnyNonAscDirection() {
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        dashboardService.getGuildMembersPage(0, 50, "balance,desc");

        assertThat(capturePageable().getSort().getOrderFor("balance").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void fallsBackToBalanceDescWhenSortIsBlank() {
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        dashboardService.getGuildMembersPage(0, 50, "");

        assertThat(capturePageable().getSort().getOrderFor("balance").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void fallsBackToBalanceDescWhenSortIsMalformed() {
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        dashboardService.getGuildMembersPage(0, 50, "userName");

        assertThat(capturePageable().getSort().getOrderFor("balance").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void fallsBackToBalanceDescForAnUnknownColumn() {
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        dashboardService.getGuildMembersPage(0, 50, "lastVoiceCheckAt,asc");

        assertThat(capturePageable().getSort().getOrderFor("balance").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void keepsPagingMetadataOfRepositoryPage() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(1L, "A", 100L)), PageRequest.of(1, 1), 5));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        Page<DashboardMemberView> page = dashboardService.getGuildMembersPage(1, 1, null);

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(5);
        assertThat(page.getNumber()).isEqualTo(1);
    }

    @Test
    void doesNotQueryTransactionsForAnEmptyPage() {
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        assertThat(dashboardService.getGuildMembersPage(0, 50, null)).isEmpty();

        verifyNoInteractions(pointsTransactionRepository);
    }

    @Test
    void splitsVoiceTimeIntoStreamAndNoStream() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(1L, "A", 100L))));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of(
                        // 6 интервалов по 100 LP = 30 минут без стрима
                        new VoicePointsSum(1L, TransactionReason.VOICE_STANDARD, 600L),
                        // 2 интервала по 150 LP = 10 минут со стримом
                        new VoicePointsSum(1L, TransactionReason.VOICE_VIEWER, 300L),
                        // 12 интервалов по 200 LP = 60 минут со стримом
                        new VoicePointsSum(1L, TransactionReason.VOICE_STREAMER, 2400L)
                ));

        DashboardMemberView row = dashboardService.getGuildMembersPage(0, 50, null).getContent().getFirst();

        assertThat(row.totalTime()).isEqualTo(Duration.ofMinutes(100));
        assertThat(row.streamTime()).isEqualTo(Duration.ofMinutes(70));
        assertThat(row.noStreamTime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(row.totalTimeText()).isEqualTo("1 ч 40 мин");
        assertThat(row.streamTimeText()).isEqualTo("1 ч 10 мин");
        assertThat(row.noStreamTimeText()).isEqualTo("30 мин");
    }

    @Test
    void reportsZeroVoiceTimeWhenMemberHasNoVoiceTransactions() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(1L, "A", 100L), member(2L, "B", 100L))));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of(new VoicePointsSum(1L, TransactionReason.VOICE_STANDARD, 100L)));

        List<DashboardMemberView> rows = dashboardService.getGuildMembersPage(0, 50, null).getContent();

        assertThat(rows.get(0).totalTime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(rows.get(1).voiceTime()).isEqualTo(VoiceTimeBreakdown.ZERO);
        assertThat(rows.get(1).totalTimeText()).isEqualTo("0 мин");
    }

    @Test
    void asksOnlyForVoiceReasonsOfThePageMembers() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(7L, "A", 100L), member(8L, "B", 100L))));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        dashboardService.getGuildMembersPage(0, 50, null);

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
        ArgumentCaptor<Collection<TransactionReason>> reasons = ArgumentCaptor.captor();
        verify(pointsTransactionRepository).sumPointsByMemberAndReason(ids.capture(), reasons.capture());

        assertThat(ids.getValue()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(reasons.getValue()).containsExactlyInAnyOrder(
                TransactionReason.VOICE_STANDARD,
                TransactionReason.VOICE_VIEWER,
                TransactionReason.VOICE_STREAMER);
    }

    @Test
    void sortsByComputedStreamTimeAcrossAllMembers() {
        when(guildMemberRepository.findAll())
                .thenReturn(List.of(member(1L, "Тихоня", 100L), member(2L, "Стример", 100L)));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of(
                        new VoicePointsSum(1L, TransactionReason.VOICE_STANDARD, 1_000L),
                        new VoicePointsSum(2L, TransactionReason.VOICE_STREAMER, 400L)));

        List<DashboardMemberView> desc =
                dashboardService.getGuildMembersPage(0, 50, "streamTime,desc").getContent();
        List<DashboardMemberView> asc =
                dashboardService.getGuildMembersPage(0, 50, "streamTime,asc").getContent();

        assertThat(desc).extracting(row -> row.member().getUserName()).containsExactly("Стример", "Тихоня");
        assertThat(asc).extracting(row -> row.member().getUserName()).containsExactly("Тихоня", "Стример");
    }

    @Test
    void pagesTheListItselfWhenSortingByAComputedColumn() {
        when(guildMemberRepository.findAll())
                .thenReturn(List.of(member(1L, "A", 100L), member(2L, "B", 100L), member(3L, "C", 100L)));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of(
                        new VoicePointsSum(1L, TransactionReason.VOICE_STANDARD, 300L),
                        new VoicePointsSum(2L, TransactionReason.VOICE_STANDARD, 200L),
                        new VoicePointsSum(3L, TransactionReason.VOICE_STANDARD, 100L)));

        Page<DashboardMemberView> second = dashboardService.getGuildMembersPage(1, 2, "voiceTime,desc");

        assertThat(second.getTotalElements()).isEqualTo(3);
        assertThat(second.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(row -> row.member().getUserName()).containsExactly("C");
    }

    @Test
    void returnsEmptyContentForAPageBeyondTheEndOfTheList() {
        when(guildMemberRepository.findAll()).thenReturn(List.of(member(1L, "A", 100L)));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        Page<DashboardMemberView> page = dashboardService.getGuildMembersPage(5, 50, "voiceTime,desc");

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void ordersMembersWithEqualValuesByName() {
        when(guildMemberRepository.findAll())
                .thenReturn(List.of(member(1L, "Яна", 100L), member(2L, "Антон", 100L)));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<DashboardMemberView> rows =
                dashboardService.getGuildMembersPage(0, 50, "voiceTime,desc").getContent();

        assertThat(rows).extracting(row -> row.member().getUserName()).containsExactly("Антон", "Яна");
    }

    @Test
    void guildReportTakesOnlyMembersOfThatGuild() {
        when(guildMemberRepository.findByGuildId("guild-1")).thenReturn(List.of(member(1L, "A", 300L)));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<DashboardMemberView> rows = dashboardService.getGuildMembers("guild-1", null, "balance,desc");

        assertThat(rows).hasSize(1);
        verify(guildMemberRepository).findByGuildId("guild-1");
    }

    @Test
    void guildReportForAPeriodAsksForTransactionsSinceThatMoment() {
        Instant since = Instant.parse("2026-09-01T00:00:00Z");
        when(guildMemberRepository.findByGuildId("guild-1")).thenReturn(List.of(member(1L, "A", 0L)));
        when(pointsTransactionRepository.sumPointsByMemberAndReasonSince(anyCollection(), anyCollection(), eq(since)))
                .thenReturn(List.of(new VoicePointsSum(1L, TransactionReason.VOICE_VIEWER, 150L)));

        List<DashboardMemberView> rows = dashboardService.getGuildMembers("guild-1", since, "voiceTime,desc");

        assertThat(rows.getFirst().streamTime()).isEqualTo(Duration.ofMinutes(5));
        verify(pointsTransactionRepository, never()).sumPointsByMemberAndReason(anyCollection(), anyCollection());
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(guildMemberRepository).findAll(captor.capture());
        return captor.getValue();
    }

    private static GuildMember member(Long id, String userName, long balance) {
        GuildMember member = new GuildMember();
        member.setId(id);
        member.setGuildId("guild-1");
        member.setUserId("user-" + id);
        member.setUserName(userName);
        member.setGuildName("Guild");
        member.setBalance(balance);
        return member;
    }

    private static Page<GuildMember> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
