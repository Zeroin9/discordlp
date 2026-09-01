package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
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
import org.springframework.dao.DataIntegrityViolationException;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PariRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PariServiceTest {

    private static final String GUILD_ID = "guild-1";
    private static final String AUTHOR_ID = "author-1";
    private static final String BETTER_ID = "better-1";

    @Mock
    private PariRepository pariRepository;
    @Mock
    private PariBetRepository pariBetRepository;
    @Mock
    private GuildMemberRepository guildMemberRepository;
    @Mock
    private PointsTransactionRepository pointsTransactionRepository;
    @Mock
    private GuildMemberService guildMemberService;

    @InjectMocks
    private PariService pariService;

    private Guild guild;
    private User author;
    private User better;

    @BeforeEach
    void setUp() {
        guild = Mockito.mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Guild");

        author = user(AUTHOR_ID);
        better = user(BETTER_ID);

        when(pariRepository.save(any(Pari.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pariBetRepository.saveAndFlush(any(PariBet.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // --- создание ---

    @Test
    void createPariTrimsTitleAndOpensBetting() {
        Pari pari = pariService.createPari(guild, author, "  Победит ли команда?  ");

        assertThat(pari.getTitle()).isEqualTo("Победит ли команда?");
        assertThat(pari.getStatus()).isEqualTo(PariStatus.OPEN);
        assertThat(pari.getGuildId()).isEqualTo(GUILD_ID);
        assertThat(pari.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(pari.getWinningOption()).isNull();
        assertThat(pari.getCreatedAt()).isNotNull();
    }

    @Test
    void createPariRejectsBlankTitle() {
        assertThatThrownBy(() -> pariService.createPari(guild, author, "   "))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("не может быть пустым");
    }

    @Test
    void createPariTruncatesOverlongTitle() {
        String longTitle = "я".repeat(PariService.MAX_TITLE_LENGTH + 50);

        Pari pari = pariService.createPari(guild, author, longTitle);

        assertThat(pari.getTitle()).hasSize(PariService.MAX_TITLE_LENGTH);
    }

    @Test
    void attachMessageStoresCoordinates() {
        Pari pari = openPari();
        when(pariRepository.findById(1L)).thenReturn(Optional.of(pari));

        pariService.attachMessage(1L, "channel-1", "message-1");

        assertThat(pari.getChannelId()).isEqualTo("channel-1");
        assertThat(pari.getMessageId()).isEqualTo("message-1");
    }

    // --- прием ставок ---

    @Test
    void placeBetDebitsBalanceAndWritesHoldTransaction() {
        Pari pari = openPari();
        GuildMember member = member(1_000L);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));
        when(guildMemberService.getOrCreateMember(guild, better)).thenReturn(member);
        when(guildMemberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(pariBetRepository.findByPariIdAndMemberId(1L, 7L)).thenReturn(Optional.empty());

        PariBet bet = pariService.placeBet(1L, guild, better, true, 300L);

        assertThat(member.getBalance()).isEqualTo(700L);
        assertThat(bet.getAmount()).isEqualTo(300L);
        assertThat(bet.getOption()).isTrue();
        assertThat(bet.isSettled()).isFalse();

        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(-300);
        assertThat(captor.getValue().getReason()).isEqualTo(TransactionReason.BET_HOLD);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(1L);
        assertThat(captor.getValue().getInitiatedBy()).isEqualTo(BETTER_ID);
    }

    @Test
    void placeBetRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, 0L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("больше нуля");

        verifyNoInteractions(pariRepository);
    }

    @Test
    void placeBetRejectsAmountAboveLimit() {
        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, PariService.MAX_BET + 1))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("Максимальная ставка");
    }

    @Test
    void placeBetRejectsUnknownPari() {
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("не найдено");
    }

    @Test
    void placeBetRejectsForeignGuild() {
        Pari pari = openPari();
        pari.setGuildId("other-guild");
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("другом сервере");
    }

    @Test
    void placeBetRejectsClosedPari() {
        Pari pari = openPari();
        pari.setStatus(PariStatus.RESOLVING);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("закрыт");
    }

    @Test
    void placeBetRejectsAuthor() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, author, true, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("Автор не может");
    }

    @Test
    void placeBetRejectsSecondBetFromSameMember() {
        Pari pari = openPari();
        GuildMember member = member(1_000L);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));
        when(guildMemberService.getOrCreateMember(guild, better)).thenReturn(member);
        when(guildMemberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(pariBetRepository.findByPariIdAndMemberId(1L, 7L)).thenReturn(Optional.of(new PariBet()));

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, false, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("Изменить выбор нельзя");

        assertThat(member.getBalance()).isEqualTo(1_000L);
    }

    @Test
    void placeBetRejectsInsufficientBalance() {
        Pari pari = openPari();
        GuildMember member = member(50L);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));
        when(guildMemberService.getOrCreateMember(guild, better)).thenReturn(member);
        when(guildMemberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(pariBetRepository.findByPariIdAndMemberId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("Недостаточно поинтов");

        assertThat(member.getBalance()).isEqualTo(50L);
        verify(pariBetRepository, never()).saveAndFlush(any());
    }

    @Test
    void placeBetTranslatesUniqueViolationIntoUserMessage() {
        Pari pari = openPari();
        GuildMember member = member(1_000L);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));
        when(guildMemberService.getOrCreateMember(guild, better)).thenReturn(member);
        when(guildMemberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(pariBetRepository.findByPariIdAndMemberId(1L, 7L)).thenReturn(Optional.empty());
        when(pariBetRepository.saveAndFlush(any(PariBet.class)))
                .thenThrow(new DataIntegrityViolationException("uk_pari_bet_pari_member"));

        assertThatThrownBy(() -> pariService.placeBet(1L, guild, better, true, 100L))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("Изменить выбор нельзя");
    }

    // --- смена статуса ---

    @Test
    void closeBettingSwitchesToResolving() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThat(pariService.closeBetting(1L, AUTHOR_ID).getStatus()).isEqualTo(PariStatus.RESOLVING);
    }

    @Test
    void closeBettingRejectsNonAuthor() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.closeBetting(1L, BETTER_ID))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("только его автор");
    }

    @Test
    void closeBettingRejectsRepeatedCall() {
        Pari pari = openPari();
        pari.setStatus(PariStatus.RESOLVING);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.closeBetting(1L, AUTHOR_ID))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("уже остановлен");
    }

    @Test
    void finishRecordsWinningOption() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        Pari finished = pariService.finish(1L, AUTHOR_ID, false);

        assertThat(finished.getStatus()).isEqualTo(PariStatus.FINISHED);
        assertThat(finished.getWinningOption()).isFalse();
        assertThat(finished.getClosedAt()).isNotNull();
        assertThat(finished.getSettledAt()).isNull();
    }

    @Test
    void finishRejectsNonAuthor() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.finish(1L, BETTER_ID, true))
                .isInstanceOf(PariException.class);
    }

    @Test
    void finishRejectsTerminalPari() {
        Pari pari = openPari();
        pari.setStatus(PariStatus.CANCELED);
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.finish(1L, AUTHOR_ID, true))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("уже завершено или отменено");
    }

    @Test
    void cancelByAuthorClosesPari() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        Pari canceled = pariService.cancel(1L, AUTHOR_ID);

        assertThat(canceled.getStatus()).isEqualTo(PariStatus.CANCELED);
        assertThat(canceled.getClosedAt()).isNotNull();
    }

    @Test
    void cancelRejectsNonAuthor() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThatThrownBy(() -> pariService.cancel(1L, BETTER_ID))
                .isInstanceOf(PariException.class)
                .hasMessageContaining("только его автор");
    }

    @Test
    void systemCancelSkipsAuthorCheck() {
        Pari pari = openPari();
        when(pariRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pari));

        assertThat(pariService.cancel(1L, null).getStatus()).isEqualTo(PariStatus.CANCELED);
    }

    // --- чтение ---

    @Test
    void getStatsAggregatesBothOptions() {
        when(pariBetRepository.countByPariIdAndOption(1L, Boolean.TRUE)).thenReturn(2L);
        when(pariBetRepository.sumAmountByPariIdAndOption(1L, Boolean.TRUE)).thenReturn(500L);
        when(pariBetRepository.countByPariIdAndOption(1L, Boolean.FALSE)).thenReturn(3L);
        when(pariBetRepository.sumAmountByPariIdAndOption(1L, Boolean.FALSE)).thenReturn(700L);

        PariStats stats = pariService.getStats(1L);

        assertThat(stats.yesCount()).isEqualTo(2L);
        assertThat(stats.noPool()).isEqualTo(700L);
        assertThat(stats.totalCount()).isEqualTo(5L);
        assertThat(stats.totalPool()).isEqualTo(1_200L);
    }

    @Test
    void findBetResolvesMemberByGuildAndUser() {
        GuildMember member = member(0L);
        PariBet bet = new PariBet();
        when(guildMemberRepository.findByGuildIdAndUserId(GUILD_ID, BETTER_ID)).thenReturn(Optional.of(member));
        when(pariBetRepository.findByPariIdAndMemberId(1L, 7L)).thenReturn(Optional.of(bet));

        assertThat(pariService.findBet(1L, GUILD_ID, BETTER_ID)).containsSame(bet);
    }

    @Test
    void findBetReturnsEmptyForUnknownMember() {
        when(guildMemberRepository.findByGuildIdAndUserId(GUILD_ID, BETTER_ID)).thenReturn(Optional.empty());

        assertThat(pariService.findBet(1L, GUILD_ID, BETTER_ID)).isEmpty();
    }

    @Test
    void findExpiredParisCombinesOpenAndResolving() {
        Instant threshold = Instant.now();
        Pari open = openPari();
        Pari resolving = openPari();
        resolving.setStatus(PariStatus.RESOLVING);
        when(pariRepository.findByStatusAndCreatedAtBefore(PariStatus.OPEN, threshold)).thenReturn(List.of(open));
        when(pariRepository.findByStatusAndCreatedAtBefore(PariStatus.RESOLVING, threshold)).thenReturn(List.of(resolving));

        assertThat(pariService.findExpiredParis(threshold)).containsExactly(open, resolving);
    }

    // --- фикстуры ---

    private static User user(String id) {
        User user = Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn(id);
        return user;
    }

    private static Pari openPari() {
        Pari pari = new Pari();
        pari.setId(1L);
        pari.setGuildId(GUILD_ID);
        pari.setAuthorId(AUTHOR_ID);
        pari.setTitle("Пари");
        pari.setStatus(PariStatus.OPEN);
        pari.setCreatedAt(Instant.now());
        return pari;
    }

    private static GuildMember member(long balance) {
        GuildMember member = new GuildMember();
        member.setId(7L);
        member.setGuildId(GUILD_ID);
        member.setUserId(BETTER_ID);
        member.setBalance(balance);
        return member;
    }
}
