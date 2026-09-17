package ru.z3r0ing.discordlp.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.service.DashboardMemberView;
import ru.z3r0ing.discordlp.service.DashboardService;
import ru.z3r0ing.discordlp.service.MemberReportService;
import ru.z3r0ing.discordlp.service.VoiceTimeBreakdown;
import ru.z3r0ing.discordlp.service.WeeklyActivityService;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Команды, печатающие отчеты в чат: {@code /lptable} и {@code /lpweek}.
 */
@ExtendWith(MockitoExtension.class)
class ReportCommandsTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private WeeklyActivityService weeklyActivityService;

    private LpTableCommand tableCommand;
    private LpWeekCommand weekCommand;

    private Guild guild;
    private SlashCommandInteractionEvent event;
    private InteractionHook hook;
    private WebhookMessageCreateAction<Message> sendAction;

    @BeforeEach
    void setUp() {
        tableCommand = new LpTableCommand(dashboardService, new MemberReportService());
        weekCommand = new LpWeekCommand(weeklyActivityService);

        guild = CommandTestSupport.guild();
        event = CommandTestSupport.event(guild, CommandTestSupport.user("caller"));

        ReplyCallbackAction deferAction = Mockito.mock(ReplyCallbackAction.class);
        lenient().when(event.deferReply()).thenReturn(deferAction);

        hook = Mockito.mock(InteractionHook.class);
        lenient().when(event.getHook()).thenReturn(hook);

        @SuppressWarnings("unchecked")
        WebhookMessageCreateAction<Message> action = Mockito.mock(WebhookMessageCreateAction.class);
        sendAction = action;
        lenient().when(sendAction.setEphemeral(Mockito.anyBoolean())).thenReturn(sendAction);
        // flatMap объявлен как RestAction<Object>, поэтому заглушка ставится через doReturn
        Mockito.lenient().doReturn(sendAction).when(sendAction).flatMap(any());
        lenient().when(hook.sendMessage(anyString())).thenReturn(sendAction);
    }

    @Test
    void tableCommandIsOpenToEveryone() {
        assertThat(tableCommand.getCommandName()).isEqualTo("lptable");
        assertThat(tableCommand.requiresAdmin()).isFalse();
        assertThat(weekCommand.getCommandName()).isEqualTo("lpweek");
        assertThat(weekCommand.requiresAdmin()).isFalse();
    }

    @Test
    void tableCommandPrintsEveryMemberOfTheGuild() {
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenReturn(List.of(row("Первый", 300L, 35, 60), row("Второй", 100L, 0, 5)));

        tableCommand.handle(event);

        verify(event).deferReply();
        String message = firstSentMessage();
        assertThat(message).contains("Таблица участников", "Первый", "Второй", "1 ч 35 мин");
        assertThat(message).contains("участников: 2");
    }

    @Test
    void tableCommandSortsByBalanceDescendingByDefault() {
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenReturn(List.of());

        tableCommand.handle(event);

        verify(dashboardService).getGuildMembers(CommandTestSupport.GUILD_ID, null, "balance,desc");
    }

    @Test
    void tableCommandTakesTheColumnFromTheOption() {
        CommandTestSupport.withStringOption(event, LpTableCommand.OPTION_SORT, "streamTime");
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenReturn(List.of());

        tableCommand.handle(event);

        // Направление не указано — берется предпочтительное для колонки времени
        verify(dashboardService).getGuildMembers(CommandTestSupport.GUILD_ID, null, "streamTime,desc");
    }

    @Test
    void tableCommandTakesTheDirectionFromTheOption() {
        CommandTestSupport.withStringOption(event, LpTableCommand.OPTION_SORT, "userName");
        CommandTestSupport.withStringOption(event, LpTableCommand.OPTION_ORDER, LpTableCommand.ORDER_ASC);
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenReturn(List.of());

        tableCommand.handle(event);

        verify(dashboardService).getGuildMembers(CommandTestSupport.GUILD_ID, null, "userName,asc");
    }

    @Test
    void tableCommandFallsBackToTheDefaultColumnForGarbage() {
        CommandTestSupport.withStringOption(event, LpTableCommand.OPTION_SORT, "неизвестная колонка");
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenReturn(List.of());

        tableCommand.handle(event);

        verify(dashboardService).getGuildMembers(CommandTestSupport.GUILD_ID, null, "balance,desc");
    }

    @Test
    void tableCommandSaysSoWhenThereIsNothingToShow() {
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenReturn(List.of());

        tableCommand.handle(event);

        assertThat(firstSentMessage()).contains("Пока никто не набрал ни одного балла.");
    }

    @Test
    void tableCommandReportsAFailureInsteadOfThrowing() {
        when(dashboardService.getGuildMembers(eq(CommandTestSupport.GUILD_ID), isNull(), anyString()))
                .thenThrow(new IllegalStateException("БД недоступна"));

        tableCommand.handle(event);

        assertThat(firstSentMessage()).contains("Не удалось построить отчет");
        verify(sendAction).setEphemeral(true);
    }

    @Test
    void weekCommandSendsTheSummaryOfTheCurrentGuild() {
        when(weeklyActivityService.buildSummary(CommandTestSupport.GUILD_ID, "Guild"))
                .thenReturn(List.of("Сводка за неделю"));

        weekCommand.handle(event);

        verify(event).deferReply();
        assertThat(firstSentMessage()).isEqualTo("Сводка за неделю");
    }

    @Test
    void weekCommandSendsEveryPartInOrder() {
        when(weeklyActivityService.buildSummary(CommandTestSupport.GUILD_ID, "Guild"))
                .thenReturn(List.of("Часть 1", "Часть 2"));

        weekCommand.handle(event);

        verify(hook).sendMessage("Часть 1");
        // Продолжение уходит только после успешной отправки первой части
        verify(sendAction).flatMap(any());
    }

    @Test
    void weekCommandReportsAFailureInsteadOfThrowing() {
        when(weeklyActivityService.buildSummary(anyString(), anyString()))
                .thenThrow(new IllegalStateException("БД недоступна"));

        weekCommand.handle(event);

        assertThat(firstSentMessage()).contains("Не удалось построить сводку");
    }

    private String firstSentMessage() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hook, Mockito.atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().getFirst();
    }

    private static DashboardMemberView row(String name, long balance, int streamMinutes, int noStreamMinutes) {
        GuildMember member = new GuildMember();
        member.setId(1L);
        member.setGuildId(CommandTestSupport.GUILD_ID);
        member.setUserId("user-1");
        member.setUserName(name);
        member.setGuildName("Guild");
        member.setBalance(balance);
        return DashboardMemberView.of(member, new VoiceTimeBreakdown(
                Duration.ofMinutes(streamMinutes), Duration.ofMinutes(noStreamMinutes)));
    }
}
