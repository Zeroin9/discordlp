package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyPointsServiceTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private JDA jda;
    @Mock
    private GuildMemberRepository guildMemberRepository;
    @Mock
    private PointsTransactionRepository pointsTransactionRepository;
    @Mock
    private MutedMemberRepository mutedMemberRepository;

    @InjectMocks
    private LoyaltyPointsService loyaltyPointsService;

    private Guild guild;

    @BeforeEach
    void setUp() {
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(mutedMemberRepository.findByMutedAtBefore(any())).thenReturn(List.of());
        when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(i -> i.getArgument(0));

        guild = Mockito.mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Guild");
        when(guild.getAfkChannel()).thenReturn(null);
        when(jda.getGuilds()).thenReturn(List.of(guild));
    }

    // --- начисление ---

    @Test
    void doesNothingWhileJdaIsNotConnected() {
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTING_TO_WEBSOCKET);

        loyaltyPointsService.processLoyaltyPoints();

        verifyNoInteractions(guildMemberRepository);
        verifyNoInteractions(pointsTransactionRepository);
    }

    @Test
    void ordinaryMemberEarnsHundred() {
        GuildMember stored = storedMember("user-1", 0L, null);
        givenChannelWith(voiceMember("user-1", false, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(stored.getBalance()).isEqualTo(100L);
        assertThat(savedTransaction().getReason()).isEqualTo(TransactionReason.VOICE_STANDARD);
    }

    @Test
    void viewerOfAStreamEarnsHundredFifty() {
        GuildMember stored = storedMember("user-1", 0L, null);
        storedMember("streamer", 0L, null);
        givenChannelWith(
                voiceMember("user-1", false, false, false),
                voiceMember("streamer", true, false, false)
        );

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(stored.getBalance()).isEqualTo(150L);
    }

    @Test
    void streamerWithAudienceEarnsTwoHundred() {
        GuildMember streamer = storedMember("streamer", 0L, null);
        storedMember("user-1", 0L, null);
        givenChannelWith(
                voiceMember("streamer", true, false, false),
                voiceMember("user-1", false, false, false)
        );

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(streamer.getBalance()).isEqualTo(200L);
    }

    @Test
    void streamerAloneEarnsNothing() {
        GuildMember streamer = storedMember("streamer", 0L, null);
        givenChannelWith(voiceMember("streamer", true, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(streamer.getBalance()).isZero();
        assertThat(streamer.getLastVoiceCheckAt()).isNotNull();
        verify(pointsTransactionRepository, never()).save(any());
    }

    @Test
    void mutedAndDeafenedMembersAreSkipped() {
        storedMember("muted", 0L, null);
        storedMember("deafened", 0L, null);
        givenChannelWith(
                voiceMember("muted", false, true, false),
                voiceMember("deafened", false, false, true)
        );

        loyaltyPointsService.processLoyaltyPoints();

        verify(guildMemberRepository, never()).save(any());
    }

    @Test
    void afkChannelIsExcluded() {
        VoiceChannel afk = Mockito.mock(VoiceChannel.class);
        when(afk.getId()).thenReturn("afk");
        when(guild.getAfkChannel()).thenReturn(afk);

        Member member = voiceMember("user-1", false, false, false);
        VoiceChannel channel = Mockito.mock(VoiceChannel.class);
        when(channel.getId()).thenReturn("afk");
        when(channel.getMembers()).thenReturn(List.of(member));
        when(guild.getVoiceChannels()).thenReturn(List.of(channel));

        loyaltyPointsService.processLoyaltyPoints();

        verify(guildMemberRepository, never()).save(any());
    }

    @Test
    void pointsAreNotAwardedTwiceWithinTheInterval() {
        GuildMember stored = storedMember("user-1", 1_000L, Instant.now());
        givenChannelWith(voiceMember("user-1", false, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(stored.getBalance()).isEqualTo(1_000L);
        verify(pointsTransactionRepository, never()).save(any());
    }

    @Test
    void pointsAreAwardedAgainAfterTheInterval() {
        GuildMember stored = storedMember("user-1", 1_000L, Instant.now().minusSeconds(600));
        givenChannelWith(voiceMember("user-1", false, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(stored.getBalance()).isEqualTo(1_100L);
    }

    @Test
    void memberIsCreatedOnFirstVoiceCheck() {
        when(guildMemberRepository.findByGuildIdAndUserId(GUILD_ID, "newcomer")).thenReturn(Optional.empty());
        givenChannelWith(voiceMember("newcomer", false, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        ArgumentCaptor<GuildMember> captor = ArgumentCaptor.forClass(GuildMember.class);
        verify(guildMemberRepository, Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getUserId()).isEqualTo("newcomer");
    }

    @Test
    void staleNamesAreRefreshed() {
        GuildMember stored = storedMember("user-1", 0L, null);
        stored.setUserName("old name");
        stored.setGuildName("old guild");
        givenChannelWith(voiceMember("user-1", false, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(stored.getUserName()).isEqualTo("user-1");
        assertThat(stored.getGuildName()).isEqualTo("Guild");
    }

    @Test
    void guildFailureDoesNotBreakOtherGuilds() {
        Guild broken = Mockito.mock(Guild.class);
        when(broken.getName()).thenReturn("Broken");
        when(broken.getVoiceChannels()).thenThrow(new IllegalStateException("cache miss"));
        when(jda.getGuilds()).thenReturn(List.of(broken, guild));

        GuildMember stored = storedMember("user-1", 0L, null);
        givenChannelWith(voiceMember("user-1", false, false, false));

        loyaltyPointsService.processLoyaltyPoints();

        assertThat(stored.getBalance()).isEqualTo(100L);
    }

    // --- снятие мьюта ---

    @Test
    void expiredMuteIsLiftedAndRecordRemoved() {
        MutedMember muted = mutedRecord();
        when(mutedMemberRepository.findByMutedAtBefore(any())).thenReturn(List.of(muted));
        when(guild.getVoiceChannels()).thenReturn(List.of());
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        Member member = voiceMember("muted-user", false, true, false);
        when(guild.getMemberById("muted-user")).thenReturn(member);

        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> action = Mockito.mock(AuditableRestAction.class);
        when(member.mute(false)).thenReturn(action);

        loyaltyPointsService.processLoyaltyPoints();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Void>> success = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(success.capture(), any());
        success.getValue().accept(null);

        verify(mutedMemberRepository).delete(muted);
    }

    @Test
    void muteRecordSurvivesWhenGuildIsUnavailable() {
        MutedMember muted = mutedRecord();
        when(mutedMemberRepository.findByMutedAtBefore(any())).thenReturn(List.of(muted));
        when(guild.getVoiceChannels()).thenReturn(List.of());
        when(jda.getGuildById(GUILD_ID)).thenReturn(null);

        loyaltyPointsService.processLoyaltyPoints();

        verify(mutedMemberRepository, never()).delete(any());
    }

    @Test
    void muteIsNotLiftedForMemberOutsideVoice() {
        MutedMember muted = mutedRecord();
        when(mutedMemberRepository.findByMutedAtBefore(any())).thenReturn(List.of(muted));
        when(guild.getVoiceChannels()).thenReturn(List.of());
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        Member member = Mockito.mock(Member.class);
        when(member.getVoiceState()).thenReturn(null);
        when(guild.getMemberById("muted-user")).thenReturn(member);

        loyaltyPointsService.processLoyaltyPoints();

        verify(member, never()).mute(false);
        verify(mutedMemberRepository, never()).delete(any());
    }

    // --- фикстуры ---

    private void givenChannelWith(Member... members) {
        VoiceChannel channel = Mockito.mock(VoiceChannel.class);
        when(channel.getId()).thenReturn("voice-1");
        when(channel.getMembers()).thenReturn(List.of(members));
        when(guild.getVoiceChannels()).thenReturn(List.of(channel));
    }

    private Member voiceMember(String id, boolean streaming, boolean muted, boolean deafened) {
        Member member = Mockito.mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getEffectiveName()).thenReturn(id);

        GuildVoiceState state = Mockito.mock(GuildVoiceState.class);
        when(state.getChannel()).thenReturn(Mockito.mock(AudioChannelUnion.class));
        when(state.inAudioChannel()).thenReturn(true);
        when(state.isStream()).thenReturn(streaming);
        when(state.isMuted()).thenReturn(muted);
        when(state.isDeafened()).thenReturn(deafened);
        when(member.getVoiceState()).thenReturn(state);
        return member;
    }

    private GuildMember storedMember(String userId, long balance, Instant lastCheck) {
        GuildMember member = new GuildMember();
        member.setId((long) userId.hashCode());
        member.setGuildId(GUILD_ID);
        member.setUserId(userId);
        member.setUserName(userId);
        member.setGuildName("Guild");
        member.setBalance(balance);
        member.setLastVoiceCheckAt(lastCheck);
        when(guildMemberRepository.findByGuildIdAndUserId(GUILD_ID, userId)).thenReturn(Optional.of(member));
        return member;
    }

    private static MutedMember mutedRecord() {
        MutedMember muted = new MutedMember();
        muted.setId(1L);
        muted.setGuildId(GUILD_ID);
        muted.setUserId("muted-user");
        muted.setMutedAt(Instant.now().minusSeconds(600));
        return muted;
    }

    private PointsTransaction savedTransaction() {
        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
