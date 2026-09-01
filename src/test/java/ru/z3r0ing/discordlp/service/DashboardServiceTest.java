package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private GuildMemberRepository guildMemberRepository;

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
    void returnsRepositoryPage() {
        Page<GuildMember> page = new PageImpl<>(List.of(new GuildMember()));
        when(guildMemberRepository.findAll(any(Pageable.class))).thenReturn(page);

        assertThat(dashboardService.getGuildMembersPage(0, 50, null)).isSameAs(page);
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(guildMemberRepository).findAll(captor.capture());
        return captor.getValue();
    }

    private static Page<GuildMember> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
