package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuildMemberServiceTest {

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @InjectMocks
    private GuildMemberService guildMemberService;

    @Test
    void returnsExistingMemberWithoutSaving() {
        Guild guild = guild();
        User user = user();
        GuildMember existing = new GuildMember();
        when(guildMemberRepository.findByGuildIdAndUserId("guild-1", "user-1")).thenReturn(Optional.of(existing));

        assertThat(guildMemberService.getOrCreateMember(guild, user)).isSameAs(existing);

        verify(guildMemberRepository, never()).save(any());
    }

    @Test
    void createsMemberWithZeroBalance() {
        Guild guild = guild();
        User user = user();
        when(guildMemberRepository.findByGuildIdAndUserId("guild-1", "user-1")).thenReturn(Optional.empty());
        when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(i -> i.getArgument(0));

        GuildMember created = guildMemberService.getOrCreateMember(guild, user);

        assertThat(created.getGuildId()).isEqualTo("guild-1");
        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getUserName()).isEqualTo("Tester");
        assertThat(created.getGuildName()).isEqualTo("Guild");
        assertThat(created.getBalance()).isZero();
    }

    private static Guild guild() {
        Guild guild = Mockito.mock(Guild.class);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("Guild");
        return guild;
    }

    private static User user() {
        User user = Mockito.mock(User.class);
        Mockito.lenient().when(user.getId()).thenReturn("user-1");
        Mockito.lenient().when(user.getName()).thenReturn("Tester");
        return user;
    }
}
