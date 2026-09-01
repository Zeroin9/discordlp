package ru.z3r0ing.discordlp.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import ru.z3r0ing.discordlp.entity.GuildMember;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Общие заглушки JDA для тестов slash-команд.
 */
final class CommandTestSupport {

    static final String GUILD_ID = "guild-1";

    private CommandTestSupport() {
    }

    static SlashCommandInteractionEvent event(Guild guild, User caller) {
        SlashCommandInteractionEvent event = Mockito.mock(SlashCommandInteractionEvent.class);
        lenient().when(event.getGuild()).thenReturn(guild);
        lenient().when(event.getUser()).thenReturn(caller);

        ReplyCallbackAction action = Mockito.mock(ReplyCallbackAction.class);
        lenient().when(action.setEphemeral(anyBoolean())).thenReturn(action);
        lenient().when(event.reply(anyString())).thenReturn(action);
        return event;
    }

    /** Текст первого ответа команды пользователю. */
    static String reply(SlashCommandInteractionEvent event) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        return captor.getValue();
    }

    static void withUserOption(SlashCommandInteractionEvent event, String name, User user) {
        OptionMapping option = Mockito.mock(OptionMapping.class);
        lenient().when(option.getAsUser()).thenReturn(user);
        lenient().when(event.getOption(name)).thenReturn(option);
    }

    static void withMemberOption(SlashCommandInteractionEvent event, String name, Member member) {
        OptionMapping option = Mockito.mock(OptionMapping.class);
        lenient().when(option.getAsMember()).thenReturn(member);
        lenient().when(event.getOption(name)).thenReturn(option);
    }

    static void withIntOption(SlashCommandInteractionEvent event, String name, int value) {
        OptionMapping option = Mockito.mock(OptionMapping.class);
        lenient().when(option.getAsInt()).thenReturn(value);
        lenient().when(event.getOption(name)).thenReturn(option);
    }

    static void withStringOption(SlashCommandInteractionEvent event, String name, String value) {
        OptionMapping option = Mockito.mock(OptionMapping.class);
        lenient().when(option.getAsString()).thenReturn(value);
        lenient().when(event.getOption(name)).thenReturn(option);
    }

    static Guild guild() {
        Guild guild = Mockito.mock(Guild.class);
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(guild.getName()).thenReturn("Guild");
        return guild;
    }

    static User user(String id) {
        User user = Mockito.mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getName()).thenReturn(id);
        return user;
    }

    /**
     * Участник сервера с голосовым состоянием: {@code inVoice} управляет подключением к каналу.
     */
    static Member member(String id, boolean inVoice, boolean muted) {
        Member member = Mockito.mock(Member.class);
        lenient().when(member.getId()).thenReturn(id);
        lenient().when(member.getEffectiveName()).thenReturn(id);

        GuildVoiceState state = Mockito.mock(GuildVoiceState.class);
        lenient().when(state.inAudioChannel()).thenReturn(inVoice);
        lenient().when(state.isMuted()).thenReturn(muted);
        lenient().when(member.getVoiceState()).thenReturn(state);
        return member;
    }

    static GuildMember guildMember(String userId, long balance) {
        GuildMember member = new GuildMember();
        member.setId(1L);
        member.setGuildId(GUILD_ID);
        member.setUserId(userId);
        member.setUserName(userId);
        member.setGuildName("Guild");
        member.setBalance(balance);
        return member;
    }
}
