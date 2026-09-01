package ru.z3r0ing.discordlp.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.MutedMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.MutedMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.service.GuildMemberService;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.event;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.guild;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.guildMember;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.member;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.reply;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.user;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.withMemberOption;

/**
 * Платные модерационные команды: /lpkick и /lpmute.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationCommandsTest {

    private static final int KICK_COST = 10_000;
    private static final int MUTE_COST = 50_000;

    @Mock
    private GuildMemberService guildMemberService;
    @Mock
    private GuildMemberRepository guildMemberRepository;
    @Mock
    private PointsTransactionRepository pointsTransactionRepository;
    @Mock
    private MutedMemberRepository mutedMemberRepository;

    private Guild guild;
    private User caller;

    @BeforeEach
    void setUp() {
        guild = guild();
        caller = user("caller");
    }

    @Nested
    class LpKick {

        private LpKickCommand command;

        @BeforeEach
        void createCommand() {
            command = new LpKickCommand(guildMemberService, guildMemberRepository, pointsTransactionRepository);
        }

        @Test
        void isAvailableToEveryone() {
            assertThat(command.getCommandName()).isEqualTo("lpkick");
            assertThat(command.requiresAdmin()).isFalse();
        }

        @Test
        void chargesCallerAndDisconnectsTarget() {
            SlashCommandInteractionEvent event = event(guild, caller);
            Member target = member("target", true, false);
            withMemberOption(event, "user", target);
            GuildMember callerMember = guildMember("caller", 25_000L);
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(callerMember);

            @SuppressWarnings("unchecked")
            RestAction<Void> action = Mockito.mock(RestAction.class);
            when(guild.kickVoiceMember(target)).thenReturn(action);

            command.handle(event);

            assertThat(callerMember.getBalance()).isEqualTo(15_000L);
            PointsTransaction tx = savedTransaction();
            assertThat(tx.getAmount()).isEqualTo(-KICK_COST);
            assertThat(tx.getReason()).isEqualTo(TransactionReason.USER_KICK);

            assertThat(runSuccessCallback(action, event)).contains("target").contains(String.valueOf(KICK_COST));
        }

        @Test
        void rejectsUnknownMember() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", null);

            command.handle(event);

            assertThat(reply(event)).contains("Участник не найден");
            verify(guildMemberRepository, never()).save(any());
        }

        @Test
        void rejectsSelfTarget() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("caller", true, false));

            command.handle(event);

            assertThat(reply(event)).contains("самого себя");
        }

        @Test
        void rejectsTargetOutsideVoice() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("target", false, false));

            command.handle(event);

            assertThat(reply(event)).contains("не подключен к голосовому каналу");
        }

        @Test
        void rejectsPoorCaller() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("target", true, false));
            GuildMember callerMember = guildMember("caller", 10L);
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(callerMember);

            command.handle(event);

            assertThat(callerMember.getBalance()).isEqualTo(10L);
            assertThat(reply(event)).contains("Недостаточно поинтов");
            verify(pointsTransactionRepository, never()).save(any());
        }

        @Test
        void reportsDiscordFailureToCaller() {
            SlashCommandInteractionEvent event = event(guild, caller);
            Member target = member("target", true, false);
            withMemberOption(event, "user", target);
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(guildMember("caller", 25_000L));

            @SuppressWarnings("unchecked")
            RestAction<Void> action = Mockito.mock(RestAction.class);
            when(guild.kickVoiceMember(target)).thenReturn(action);

            command.handle(event);

            assertThat(runFailureCallback(action, event, "нет прав")).contains("Не удалось отключить пользователя");
        }
    }

    @Nested
    class LpMute {

        private LpMuteCommand command;

        @BeforeEach
        void createCommand() {
            command = new LpMuteCommand(guildMemberService, guildMemberRepository,
                    pointsTransactionRepository, mutedMemberRepository);
        }

        @Test
        void isAvailableToEveryone() {
            assertThat(command.getCommandName()).isEqualTo("lpmute");
            assertThat(command.requiresAdmin()).isFalse();
        }

        @Test
        void chargesCallerMutesTargetAndSchedulesUnmute() {
            SlashCommandInteractionEvent event = event(guild, caller);
            Member target = member("target", true, false);
            withMemberOption(event, "user", target);
            GuildMember callerMember = guildMember("caller", 60_000L);
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(callerMember);

            @SuppressWarnings("unchecked")
            AuditableRestAction<Void> action = Mockito.mock(AuditableRestAction.class);
            when(target.mute(true)).thenReturn(action);

            command.handle(event);

            assertThat(callerMember.getBalance()).isEqualTo(10_000L);

            PointsTransaction tx = savedTransaction();
            assertThat(tx.getAmount()).isEqualTo(-MUTE_COST);
            assertThat(tx.getReason()).isEqualTo(TransactionReason.USER_MUTE);
            assertThat(tx.getInitiatedBy()).isEqualTo("caller");

            ArgumentCaptor<MutedMember> muted = ArgumentCaptor.forClass(MutedMember.class);
            verify(mutedMemberRepository).save(muted.capture());
            assertThat(muted.getValue().getUserId()).isEqualTo("target");
            assertThat(muted.getValue().getMutedAt()).isNotNull();

            assertThat(runSuccessCallback(action, event)).contains("замьючен");
        }

        @Test
        void rejectsAlreadyMutedTarget() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("target", true, true));

            command.handle(event);

            assertThat(reply(event)).contains("уже замьючен");
            verify(mutedMemberRepository, never()).save(any());
        }

        @Test
        void rejectsSelfTarget() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("caller", true, false));

            command.handle(event);

            assertThat(reply(event)).contains("самого себя");
        }

        @Test
        void rejectsPoorCaller() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("target", true, false));
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(guildMember("caller", 100L));

            command.handle(event);

            assertThat(reply(event)).contains("Недостаточно поинтов");
            verify(mutedMemberRepository, never()).save(any());
        }

        @Test
        void rejectsUnknownMember() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", null);

            command.handle(event);

            assertThat(reply(event)).contains("Участник не найден");
        }

        @Test
        void rejectsTargetOutsideVoice() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withMemberOption(event, "user", member("target", false, false));

            command.handle(event);

            assertThat(reply(event)).contains("не подключен к голосовому каналу");
        }

        @Test
        void reportsDiscordFailureToCaller() {
            SlashCommandInteractionEvent event = event(guild, caller);
            Member target = member("target", true, false);
            withMemberOption(event, "user", target);
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(guildMember("caller", 60_000L));

            @SuppressWarnings("unchecked")
            AuditableRestAction<Void> action = Mockito.mock(AuditableRestAction.class);
            when(target.mute(true)).thenReturn(action);

            command.handle(event);

            assertThat(runFailureCallback(action, event, "нет прав")).contains("Не удалось замьютить пользователя");
        }
    }

    /** Выполняет failure-колбэк RestAction и возвращает текст ответа, отправленного командой. */
    private static String runFailureCallback(RestAction<Void> action, SlashCommandInteractionEvent event, String error) {
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(any(), failure.capture());
        failure.getValue().accept(new IllegalStateException(error));
        return reply(event);
    }

    /** Выполняет success-колбэк RestAction и возвращает текст ответа, отправленного командой. */
    private static String runSuccessCallback(RestAction<Void> action, SlashCommandInteractionEvent event) {
        ArgumentCaptor<Consumer<Void>> success = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(success.capture(), any());
        success.getValue().accept(null);
        return reply(event);
    }

    private PointsTransaction savedTransaction() {
        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
