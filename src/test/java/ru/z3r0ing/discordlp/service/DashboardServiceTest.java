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
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
    void keepsPagingMetadataOfRepositoryPage() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(1L)), PageRequest.of(1, 1), 5));
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
    void sumsVoiceTimeAcrossAllVoiceReasons() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(1L))));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of(
                        // 6 интервалов по 100 LP = 30 минут
                        new VoicePointsSum(1L, TransactionReason.VOICE_STANDARD, 600L),
                        // 2 интервала по 150 LP = 10 минут
                        new VoicePointsSum(1L, TransactionReason.VOICE_VIEWER, 300L),
                        // 12 интервалов по 200 LP = 60 минут
                        new VoicePointsSum(1L, TransactionReason.VOICE_STREAMER, 2400L)
                ));

        DashboardMemberView row = dashboardService.getGuildMembersPage(0, 50, null).getContent().getFirst();

        assertThat(row.voiceTime()).isEqualTo(Duration.ofMinutes(100));
        assertThat(row.voiceTimeText()).isEqualTo("1 ч 40 мин");
    }

    @Test
    void reportsZeroVoiceTimeWhenMemberHasNoVoiceTransactions() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(1L), member(2L))));
        when(pointsTransactionRepository.sumPointsByMemberAndReason(anyCollection(), anyCollection()))
                .thenReturn(List.of(new VoicePointsSum(1L, TransactionReason.VOICE_STANDARD, 100L)));

        List<DashboardMemberView> rows = dashboardService.getGuildMembersPage(0, 50, null).getContent();

        assertThat(rows.get(0).voiceTime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(rows.get(1).voiceTime()).isEqualTo(Duration.ZERO);
        assertThat(rows.get(1).voiceTimeText()).isEqualTo("0 мин");
    }

    @Test
    void asksOnlyForVoiceReasonsOfThePageMembers() {
        when(guildMemberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member(7L), member(8L))));
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

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(guildMemberRepository).findAll(captor.capture());
        return captor.getValue();
    }

    private static GuildMember member(Long id) {
        GuildMember member = new GuildMember();
        member.setId(id);
        member.setGuildId("guild-1");
        member.setUserId("user-" + id);
        member.setUserName("User " + id);
        member.setGuildName("Guild");
        member.setBalance(1_000L);
        return member;
    }

    private static Page<GuildMember> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
