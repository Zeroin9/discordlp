package ru.z3r0ing.discordlp.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.service.GuildMemberService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.event;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.guild;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.guildMember;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.reply;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.user;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.withIntOption;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.withUserOption;

/**
 * Команды просмотра и ручной корректировки баланса: /lp, /lpuser, /lpadd, /lpremove.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceCommandsTest {

    @Mock
    private GuildMemberService guildMemberService;
    @Mock
    private GuildMemberRepository guildMemberRepository;
    @Mock
    private PointsTransactionRepository pointsTransactionRepository;

    private Guild guild;
    private User caller;
    private User target;

    @BeforeEach
    void setUp() {
        guild = guild();
        caller = user("caller");
        target = user("target");
    }

    @Nested
    class Lp {

        private LpCommand command;

        @BeforeEach
        void createCommand() {
            command = new LpCommand(guildMemberService);
        }

        @Test
        void isAvailableToEveryone() {
            assertThat(command.getCommandName()).isEqualTo("lp");
            assertThat(command.requiresAdmin()).isFalse();
        }

        @Test
        void showsOwnBalance() {
            SlashCommandInteractionEvent event = event(guild, caller);
            when(guildMemberService.getOrCreateMember(guild, caller)).thenReturn(guildMember("caller", 1_234L));

            command.handle(event);

            assertThat(reply(event)).contains("1234");
        }
    }

    @Nested
    class LpUser {

        private LpUserCommand command;

        @BeforeEach
        void createCommand() {
            command = new LpUserCommand(guildMemberService);
        }

        @Test
        void requiresAdministrator() {
            assertThat(command.getCommandName()).isEqualTo("lpuser");
            assertThat(command.requiresAdmin()).isTrue();
        }

        @Test
        void showsBalanceOfAnotherMember() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withUserOption(event, "user", target);
            when(guildMemberService.getOrCreateMember(guild, target)).thenReturn(guildMember("target", 42L));

            command.handle(event);

            assertThat(reply(event)).contains("target").contains("42");
        }
    }

    @Nested
    class LpAdd {

        private LpAddCommand command;

        @BeforeEach
        void createCommand() {
            command = new LpAddCommand(guildMemberService, guildMemberRepository, pointsTransactionRepository);
        }

        @Test
        void requiresAdministrator() {
            assertThat(command.getCommandName()).isEqualTo("lpadd");
            assertThat(command.requiresAdmin()).isTrue();
        }

        @Test
        void creditsBalanceAndLogsTransaction() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withUserOption(event, "user", target);
            withIntOption(event, "amount", 500);
            GuildMember member = guildMember("target", 100L);
            when(guildMemberService.getOrCreateMember(guild, target)).thenReturn(member);

            command.handle(event);

            assertThat(member.getBalance()).isEqualTo(600L);
            PointsTransaction tx = savedTransaction();
            assertThat(tx.getAmount()).isEqualTo(500);
            assertThat(tx.getReason()).isEqualTo(TransactionReason.ADMIN_MANUAL);
            assertThat(tx.getInitiatedBy()).isEqualTo("caller");
            assertThat(reply(event)).contains("600");
        }

        @Test
        void rejectsNonPositiveAmount() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withUserOption(event, "user", target);
            withIntOption(event, "amount", 0);

            command.handle(event);

            assertThat(reply(event)).contains("больше нуля");
            verify(guildMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    class LpRemove {

        private LpRemoveCommand command;

        @BeforeEach
        void createCommand() {
            command = new LpRemoveCommand(guildMemberService, guildMemberRepository, pointsTransactionRepository);
        }

        @Test
        void requiresAdministrator() {
            assertThat(command.getCommandName()).isEqualTo("lpremove");
            assertThat(command.requiresAdmin()).isTrue();
        }

        @Test
        void debitsBalanceAndLogsTransaction() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withUserOption(event, "user", target);
            withIntOption(event, "amount", 300);
            GuildMember member = guildMember("target", 1_000L);
            when(guildMemberService.getOrCreateMember(guild, target)).thenReturn(member);

            command.handle(event);

            assertThat(member.getBalance()).isEqualTo(700L);
            PointsTransaction tx = savedTransaction();
            assertThat(tx.getAmount()).isEqualTo(-300);
            assertThat(tx.getReason()).isEqualTo(TransactionReason.ADMIN_REMOVE);
        }

        @Test
        void rejectsNonPositiveAmount() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withUserOption(event, "user", target);
            withIntOption(event, "amount", -5);

            command.handle(event);

            assertThat(reply(event)).contains("больше нуля");
        }

        @Test
        void rejectsRemovalBeyondBalance() {
            SlashCommandInteractionEvent event = event(guild, caller);
            withUserOption(event, "user", target);
            withIntOption(event, "amount", 5_000);
            GuildMember member = guildMember("target", 100L);
            when(guildMemberService.getOrCreateMember(guild, target)).thenReturn(member);

            command.handle(event);

            assertThat(member.getBalance()).isEqualTo(100L);
            assertThat(reply(event)).contains("недостаточно поинтов");
            verify(pointsTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }

    private PointsTransaction savedTransaction() {
        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
