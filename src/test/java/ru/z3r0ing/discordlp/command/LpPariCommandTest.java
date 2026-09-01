package ru.z3r0ing.discordlp.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.service.PariException;
import ru.z3r0ing.discordlp.service.PariMessageService;
import ru.z3r0ing.discordlp.service.PariService;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.event;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.guild;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.reply;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.user;
import static ru.z3r0ing.discordlp.command.CommandTestSupport.withStringOption;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LpPariCommandTest {

    @Mock
    private PariService pariService;
    @Mock
    private PariMessageService pariMessageService;

    private LpPariCommand command;
    private Guild guild;
    private User author;

    @BeforeEach
    void setUp() {
        command = new LpPariCommand(pariService, pariMessageService);
        guild = guild();
        author = user("author");
    }

    @Test
    void isAvailableToEveryone() {
        assertThat(command.getCommandName()).isEqualTo("lp-pari");
        assertThat(command.requiresAdmin()).isFalse();
    }

    @Test
    void publishesPollAndRemembersMessageCoordinates() {
        SlashCommandInteractionEvent event = event(guild, author);
        withStringOption(event, "title", "Победит ли команда?");

        Pari pari = pari();
        when(pariService.createPari(guild, author, "Победит ли команда?")).thenReturn(pari);
        when(pariMessageService.buildCreateData(pari)).thenReturn(pollMessage());

        ReplyCallbackAction replyAction = Mockito.mock(ReplyCallbackAction.class);
        when(event.reply(any(MessageCreateData.class))).thenReturn(replyAction);

        command.handle(event);

        // разыгрываем колбэк отправки сообщения
        ArgumentCaptor<Consumer<InteractionHook>> onSent = ArgumentCaptor.forClass(Consumer.class);
        verify(replyAction).queue(onSent.capture(), any());

        InteractionHook hook = Mockito.mock(InteractionHook.class);
        @SuppressWarnings("unchecked")
        RestAction<Message> retrieve = Mockito.mock(RestAction.class);
        when(hook.retrieveOriginal()).thenReturn(retrieve);
        onSent.getValue().accept(hook);

        ArgumentCaptor<Consumer<Message>> onRetrieved = ArgumentCaptor.forClass(Consumer.class);
        verify(retrieve).queue(onRetrieved.capture(), any());

        Message message = Mockito.mock(Message.class);
        MessageChannelUnion channel = Mockito.mock(MessageChannelUnion.class);
        when(channel.getId()).thenReturn("channel-1");
        when(message.getChannel()).thenReturn(channel);
        when(message.getId()).thenReturn("message-1");
        onRetrieved.getValue().accept(message);

        verify(pariService).attachMessage(1L, "channel-1", "message-1");
    }

    @Test
    void survivesPublishAndRetrieveFailures() {
        SlashCommandInteractionEvent event = event(guild, author);
        withStringOption(event, "title", "Пари");

        Pari pari = pari();
        when(pariService.createPari(guild, author, "Пари")).thenReturn(pari);
        when(pariMessageService.buildCreateData(pari)).thenReturn(pollMessage());

        ReplyCallbackAction replyAction = Mockito.mock(ReplyCallbackAction.class);
        when(event.reply(any(MessageCreateData.class))).thenReturn(replyAction);

        command.handle(event);

        ArgumentCaptor<Consumer<InteractionHook>> onSent = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> onSendFailed = ArgumentCaptor.forClass(Consumer.class);
        verify(replyAction).queue(onSent.capture(), onSendFailed.capture());

        // сообщение не ушло — координаты не сохраняются, исключение наружу не летит
        onSendFailed.getValue().accept(new IllegalStateException("discord down"));

        InteractionHook hook = Mockito.mock(InteractionHook.class);
        @SuppressWarnings("unchecked")
        RestAction<Message> retrieve = Mockito.mock(RestAction.class);
        when(hook.retrieveOriginal()).thenReturn(retrieve);
        onSent.getValue().accept(hook);

        ArgumentCaptor<Consumer<Throwable>> onRetrieveFailed = ArgumentCaptor.forClass(Consumer.class);
        verify(retrieve).queue(any(), onRetrieveFailed.capture());
        onRetrieveFailed.getValue().accept(new IllegalStateException("no message"));

        verify(pariService, never()).attachMessage(any(), any(), any());
    }

    @Test
    void rejectsMissingTitleOption() {
        SlashCommandInteractionEvent event = event(guild, author);
        when(event.getOption("title")).thenReturn(null);

        command.handle(event);

        assertThat(reply(event)).contains("Укажите название пари");
        verify(pariService, never()).createPari(any(), any(), any());
    }

    @Test
    void reportsBusinessRuleViolationToAuthor() {
        SlashCommandInteractionEvent event = event(guild, author);
        withStringOption(event, "title", "   ");
        when(pariService.createPari(guild, author, "   "))
                .thenThrow(new PariException("Название пари не может быть пустым."));

        command.handle(event);

        assertThat(reply(event)).contains("не может быть пустым");
    }

    private static MessageCreateData pollMessage() {
        return new MessageCreateBuilder().setContent("опрос").build();
    }

    private static Pari pari() {
        Pari pari = new Pari();
        pari.setId(1L);
        pari.setGuildId(CommandTestSupport.GUILD_ID);
        pari.setAuthorId("author");
        pari.setTitle("Победит ли команда?");
        pari.setStatus(PariStatus.OPEN);
        pari.setCreatedAt(Instant.now());
        return pari;
    }
}
