package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.z3r0ing.discordlp.entity.GuildMember;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyActivityServiceTest {

    @Mock
    private JDA jda;

    @Mock
    private DashboardService dashboardService;

    private WeeklyActivityService service;

    @BeforeEach
    void setUp() {
        service = new WeeklyActivityService(jda, dashboardService, new MemberReportService());
        ReflectionTestUtils.setField(service, "days", 7);
        ReflectionTestUtils.setField(service, "zone", "Europe/Moscow");
        ReflectionTestUtils.setField(service, "channelId", "channel-1");
    }

    @Test
    void summaryListsOnlyMembersWhoWereInVoice() {
        when(dashboardService.getGuildMembers(eq("guild-1"), any(Instant.class), anyString()))
                .thenReturn(List.of(
                        row("Активный", 35, 60),
                        row("Заходил", 0, 5),
                        row("Не заходил", 0, 0)));

        String summary = String.join("\n", service.buildSummary("guild-1", "Гильдия"));

        assertThat(summary).contains("Активный", "Заходил");
        assertThat(summary).doesNotContain("Не заходил");
        assertThat(summary).contains("участников: 2");
        // Баланс в сводке про активность не нужен
        assertThat(summary).doesNotContain("Баланс LP");
    }

    @Test
    void summaryAsksForTheLastSevenDaysSortedByTotalTime() {
        when(dashboardService.getGuildMembers(eq("guild-1"), any(Instant.class), anyString()))
                .thenReturn(List.of(row("Активный", 35, 60)));

        Instant before = Instant.now();
        service.buildSummary("guild-1", "Гильдия");

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<String> sort = ArgumentCaptor.forClass(String.class);
        verify(dashboardService).getGuildMembers(eq("guild-1"), since.capture(), sort.capture());

        assertThat(since.getValue()).isBetween(before.minus(Duration.ofDays(8)), before.minus(Duration.ofDays(6)));
        assertThat(sort.getValue()).isEqualTo("voiceTime,desc");
    }

    @Test
    void periodBoundsAreShownInTheHeader() {
        when(dashboardService.getGuildMembers(eq("guild-1"), any(Instant.class), anyString()))
                .thenReturn(List.of(row("Активный", 35, 60)));

        String summary = service.buildSummary("guild-1", "Гильдия",
                Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-08T09:00:00Z")).getFirst();

        assertThat(summary).contains("Гильдия", "01.09.2026", "08.09.2026");
    }

    @Test
    void emptySummarySaysNobodyWasAround() {
        when(dashboardService.getGuildMembers(eq("guild-1"), any(Instant.class), anyString()))
                .thenReturn(List.of(row("Не заходил", 0, 0)));

        assertThat(service.buildSummary("guild-1", "Гильдия").getFirst())
                .contains("За этот период в голосовых каналах никого не было.");
    }

    @Test
    void scheduledPublicationIsSkippedWhenNoChannelIsConfigured() {
        ReflectionTestUtils.setField(service, "channelId", "");

        service.publishWeeklySummary();

        verify(jda, never()).getChannelById(eq(GuildMessageChannel.class), anyString());
        verify(dashboardService, never()).getGuildMembers(anyString(), any(), anyString());
    }

    @Test
    void scheduledPublicationIsSkippedWhileJdaIsNotConnected() {
        when(jda.getStatus()).thenReturn(JDA.Status.RECONNECT_QUEUED);

        service.publishWeeklySummary();

        verify(jda, never()).getChannelById(eq(GuildMessageChannel.class), anyString());
    }

    @Test
    void scheduledPublicationSurvivesAMissingChannel() {
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(jda.getChannelById(GuildMessageChannel.class, "channel-1")).thenReturn(null);

        service.publishWeeklySummary();

        verify(dashboardService, never()).getGuildMembers(anyString(), any(), anyString());
    }

    @Test
    void scheduledPublicationSendsTheSummaryToTheConfiguredChannel() {
        GuildMessageChannel channel = Mockito.mock(GuildMessageChannel.class);
        Guild guild = Mockito.mock(Guild.class);
        lenient().when(guild.getId()).thenReturn("guild-1");
        lenient().when(guild.getName()).thenReturn("Гильдия");
        when(channel.getGuild()).thenReturn(guild);

        MessageCreateAction action = Mockito.mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(action);

        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(jda.getChannelById(GuildMessageChannel.class, "channel-1")).thenReturn(channel);
        when(dashboardService.getGuildMembers(eq("guild-1"), any(Instant.class), anyString()))
                .thenReturn(List.of(row("Активный", 35, 60)));

        service.publishWeeklySummary();

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(channel).sendMessage(sent.capture());
        assertThat(sent.getValue()).contains("Активный", "Гильдия");
        verify(action).queue(any(), any());
    }

    @Test
    void allTimeReportIsNotRequestedForTheWeeklySummary() {
        when(dashboardService.getGuildMembers(eq("guild-1"), any(Instant.class), anyString()))
                .thenReturn(List.of(row("Активный", 35, 60)));

        service.buildSummary("guild-1", "Гильдия");

        verify(dashboardService, never()).getGuildMembers(anyString(), isNull(), anyString());
    }

    private static DashboardMemberView row(String name, int streamMinutes, int noStreamMinutes) {
        GuildMember member = new GuildMember();
        member.setId(1L);
        member.setGuildId("guild-1");
        member.setUserId("user-1");
        member.setUserName(name);
        member.setGuildName("Гильдия");
        member.setBalance(1_000L);
        return DashboardMemberView.of(member, new VoiceTimeBreakdown(
                Duration.ofMinutes(streamMinutes), Duration.ofMinutes(noStreamMinutes)));
    }
}
