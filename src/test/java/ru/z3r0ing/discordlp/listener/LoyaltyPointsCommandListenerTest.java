package ru.z3r0ing.discordlp.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.command.SlashCommandHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyPointsCommandListenerTest {

    @Test
    void routesCommandToMatchingHandler() {
        SlashCommandHandler handler = handler("lp", false);
        SlashCommandInteractionEvent event = event("lp", true, false);

        listenerFor(handler).onSlashCommandInteraction(event);

        verify(handler).handle(event);
    }

    @Test
    void ignoresUnknownCommand() {
        SlashCommandHandler handler = handler("lp", false);
        SlashCommandInteractionEvent event = event("unrelated", true, false);

        listenerFor(handler).onSlashCommandInteraction(event);

        verify(handler, never()).handle(event);
    }

    @Test
    void rejectsDirectMessages() {
        SlashCommandHandler handler = handler("lp", false);
        SlashCommandInteractionEvent event = event("lp", false, false);

        listenerFor(handler).onSlashCommandInteraction(event);

        assertThat(reply(event)).contains("только на серверах");
        verify(handler, never()).handle(event);
    }

    @Test
    void allowsAdminCommandForAdministrator() {
        SlashCommandHandler handler = handler("lpadd", true);
        SlashCommandInteractionEvent event = event("lpadd", true, true);

        listenerFor(handler).onSlashCommandInteraction(event);

        verify(handler).handle(event);
    }

    @Test
    void blocksAdminCommandForOrdinaryMember() {
        SlashCommandHandler handler = handler("lpadd", true);
        SlashCommandInteractionEvent event = event("lpadd", true, false);

        listenerFor(handler).onSlashCommandInteraction(event);

        assertThat(reply(event)).contains("нет прав");
        verify(handler, never()).handle(event);
    }

    @Test
    void blocksAdminCommandWhenMemberIsUnavailable() {
        SlashCommandHandler handler = handler("lpadd", true);
        SlashCommandInteractionEvent event = event("lpadd", true, false);
        when(event.getMember()).thenReturn(null);

        listenerFor(handler).onSlashCommandInteraction(event);

        verify(handler, never()).handle(event);
    }

    private static LoyaltyPointsCommandListener listenerFor(SlashCommandHandler... handlers) {
        LoyaltyPointsCommandListener listener = new LoyaltyPointsCommandListener(List.of(handlers));
        listener.init();
        return listener;
    }

    private static SlashCommandHandler handler(String name, boolean requiresAdmin) {
        SlashCommandHandler handler = Mockito.mock(SlashCommandHandler.class);
        when(handler.getCommandName()).thenReturn(name);
        when(handler.requiresAdmin()).thenReturn(requiresAdmin);
        return handler;
    }

    private static SlashCommandInteractionEvent event(String name, boolean inGuild, boolean admin) {
        SlashCommandInteractionEvent event = Mockito.mock(SlashCommandInteractionEvent.class);
        lenient().when(event.getName()).thenReturn(name);
        lenient().when(event.getGuild()).thenReturn(inGuild ? Mockito.mock(Guild.class) : null);

        Member member = Mockito.mock(Member.class);
        lenient().when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(admin);
        lenient().when(event.getMember()).thenReturn(member);

        ReplyCallbackAction action = Mockito.mock(ReplyCallbackAction.class);
        lenient().when(action.setEphemeral(anyBoolean())).thenReturn(action);
        lenient().when(event.reply(anyString())).thenReturn(action);
        return event;
    }

    private static String reply(SlashCommandInteractionEvent event) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        return captor.getValue();
    }
}
