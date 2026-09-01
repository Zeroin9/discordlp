package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PariStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PariMessageServiceTest {

    @Mock
    private JDA jda;
    @Mock
    private PariService pariService;

    @InjectMocks
    private PariMessageService pariMessageService;

    @BeforeEach
    void setUp() {
        when(pariService.getStats(any())).thenReturn(new PariStats(2L, 500L, 1L, 500L));
    }

    // --- идентификаторы компонентов ---

    @Test
    void customIdEncodesActionAndPari() {
        assertThat(PariMessageService.customId(PariMessageService.ACTION_CANCEL, 42L)).isEqualTo("pari:cancel:42");
    }

    @Test
    void customIdEncodesOption() {
        assertThat(PariMessageService.customId(PariMessageService.ACTION_BET, 42L, true)).isEqualTo("pari:bet:42:yes");
        assertThat(PariMessageService.customId(PariMessageService.ACTION_FINISH, 7L, false)).isEqualTo("pari:finish:7:no");
    }

    @Test
    void optionNameIsHumanReadable() {
        assertThat(PariMessageService.optionName(true)).isEqualTo("Да");
        assertThat(PariMessageService.optionName(false)).isEqualTo("Нет");
    }

    // --- эмбед ---

    @Test
    void embedShowsTitleAuthorPoolsAndCoefficients() {
        MessageEmbed embed = pariMessageService.buildEmbed(pari(PariStatus.OPEN, null));

        assertThat(embed.getTitle()).contains("Победит ли команда?");
        assertThat(embed.getDescription()).contains("<@author-1>");
        assertThat(embed.getFields()).hasSize(3);
        // пул 1000, комиссия 5% → призовой фонд 950, на каждый вариант по 500 → коэффициент 1.90
        assertThat(embed.getFields().get(0).getValue())
                .contains("2 ставок").contains("500 LP").contains("Коэф.: 1.90");
        assertThat(embed.getFields().get(1).getValue())
                .contains("1 ставок").contains("Коэф.: 1.90");
        assertThat(embed.getFields().get(2).getValue())
                .contains("3 участников").contains("1000 LP").contains("Комиссия 5%").contains("50 LP");
        assertThat(embed.getFooter().getText()).contains("Идет прием ставок");
    }

    @Test
    void optionWithoutBetsShowsDashInsteadOfCoefficient() {
        when(pariService.getStats(any())).thenReturn(new PariStats(1L, 300L, 0L, 0L));

        MessageEmbed embed = pariMessageService.buildEmbed(pari(PariStatus.OPEN, null));

        assertThat(embed.getFields().get(1).getValue()).contains("Коэф.: —");
    }

    @Test
    void commissionLineIsHiddenWhenThereIsNoCommission() {
        Pari pari = pari(PariStatus.OPEN, null);
        pari.setCommissionRate(BigDecimal.ZERO);

        MessageEmbed embed = pariMessageService.buildEmbed(pari);

        assertThat(embed.getFields().get(2).getValue()).doesNotContain("Комиссия");
        assertThat(embed.getFields().get(0).getValue()).contains("Коэф.: 2.00");
    }

    @Test
    void formatCoefficientTruncatesToTwoDecimals() {
        // усечение, а не округление: обещанный коэффициент не должен оказаться выше реального
        assertThat(PariMessageService.formatCoefficient(new BigDecimal("3.1666"))).isEqualTo("3.16");
        assertThat(PariMessageService.formatCoefficient(new BigDecimal("1.9000"))).isEqualTo("1.90");
        assertThat(PariMessageService.formatCoefficient(null)).isEqualTo("—");
    }

    @Test
    void embedFooterReflectsStatus() {
        assertThat(pariMessageService.buildEmbed(pari(PariStatus.RESOLVING, null)).getFooter().getText())
                .contains("Прием ставок остановлен");
        assertThat(pariMessageService.buildEmbed(pari(PariStatus.FINISHED, Boolean.TRUE)).getFooter().getText())
                .contains("победил вариант «Да»");
        assertThat(pariMessageService.buildEmbed(pari(PariStatus.FINISHED, Boolean.FALSE)).getFooter().getText())
                .contains("победил вариант «Нет»");
        assertThat(pariMessageService.buildEmbed(pari(PariStatus.CANCELED, null)).getFooter().getText())
                .contains("ставки возвращены");
    }

    @Test
    void finishedFooterShowsTheFinalCoefficient() {
        Pari pari = pari(PariStatus.FINISHED, Boolean.TRUE);
        pari.setWinningSum(500L);
        pari.setWinningCoefficient(new BigDecimal("1.9000"));

        assertThat(pariMessageService.buildEmbed(pari).getFooter().getText())
                .contains("победил вариант «Да»").contains("коэффициент 1.90");
    }

    @Test
    void finishedFooterExplainsRefundWhenNobodyWon() {
        Pari pari = pari(PariStatus.FINISHED, Boolean.TRUE);
        pari.setWinningSum(0L);

        assertThat(pariMessageService.buildEmbed(pari).getFooter().getText())
                .contains("ставок на него не было").contains("возвращены");
    }

    @Test
    void embedColorDependsOnStatus() {
        assertThat(pariMessageService.buildEmbed(pari(PariStatus.OPEN, null)).getColorRaw())
                .isNotEqualTo(pariMessageService.buildEmbed(pari(PariStatus.FINISHED, Boolean.TRUE)).getColorRaw());
    }

    // --- кнопки ---

    @Test
    void openPariHasEnabledBetButtonsAndControls() {
        List<MessageTopLevelComponent> rows = pariMessageService.buildComponents(pari(PariStatus.OPEN, null));

        assertThat(rows).hasSize(2);
        List<Button> betButtons = buttons(rows.get(0));
        assertThat(betButtons).extracting(Button::getCustomId)
                .containsExactly("pari:bet:1:yes", "pari:bet:1:no");
        assertThat(betButtons).noneMatch(Button::isDisabled);

        assertThat(buttons(rows.get(1))).extracting(Button::getCustomId)
                .containsExactly("pari:stop:1", "pari:finish:1:yes", "pari:finish:1:no", "pari:cancel:1");
    }

    @Test
    void resolvingPariDisablesBettingButKeepsControls() {
        List<MessageTopLevelComponent> rows = pariMessageService.buildComponents(pari(PariStatus.RESOLVING, null));

        assertThat(rows).hasSize(2);
        assertThat(buttons(rows.get(0))).allMatch(Button::isDisabled);
        List<Button> controls = buttons(rows.get(1));
        assertThat(controls.get(0).isDisabled()).as("повторная остановка ставок").isTrue();
        assertThat(controls.get(1).isDisabled()).isFalse();
    }

    @Test
    void terminalPariKeepsOnlyDisabledBetRow() {
        for (PariStatus status : List.of(PariStatus.FINISHED, PariStatus.CANCELED)) {
            List<MessageTopLevelComponent> rows = pariMessageService.buildComponents(pari(status, Boolean.TRUE));

            assertThat(rows).as("статус %s", status).hasSize(1);
            assertThat(buttons(rows.get(0))).allMatch(Button::isDisabled);
        }
    }

    @Test
    void createAndEditDataCarryEmbedAndComponents() {
        Pari pari = pari(PariStatus.OPEN, null);

        assertThat(pariMessageService.buildCreateData(pari).getEmbeds()).hasSize(1);
        assertThat(pariMessageService.buildCreateData(pari).getComponents()).hasSize(2);
        assertThat(pariMessageService.buildEditData(pari).getEmbeds()).hasSize(1);
        assertThat(pariMessageService.buildEditData(pari).getComponents()).hasSize(2);
    }

    // --- модальное окно ---

    @Test
    void betModalCarriesPariAndOption() {
        Modal modal = pariMessageService.buildBetModal(pari(PariStatus.OPEN, null), true);

        assertThat(modal.getId()).isEqualTo("pari:bet:1:yes");
        assertThat(modal.getTitle()).isEqualTo("Победит ли команда?");
        assertThat(modal.getComponents()).hasSize(1);
    }

    @Test
    void betModalTitleIsTruncatedToDiscordLimit() {
        Pari pari = pari(PariStatus.OPEN, null);
        pari.setTitle("я".repeat(PariService.MAX_TITLE_LENGTH));

        assertThat(pariMessageService.buildBetModal(pari, false).getTitle()).hasSize(45);
    }

    // --- обновление сообщения ---

    @Test
    void refreshEditsStoredMessage() {
        Pari pari = pari(PariStatus.OPEN, null);
        pari.setChannelId("channel-1");
        pari.setMessageId("message-1");
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));

        MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);
        MessageEditAction editAction = org.mockito.Mockito.mock(MessageEditAction.class);
        when(jda.getChannelById(eq(MessageChannel.class), anyString())).thenReturn(channel);
        when(channel.editMessageById(eq("message-1"), any(net.dv8tion.jda.api.utils.messages.MessageEditData.class)))
                .thenReturn(editAction);

        pariMessageService.refresh(1L);

        verify(editAction).queue(any(), any());
    }

    @Test
    void refreshIsNoOpWhenMessageWasNeverStored() {
        when(pariService.findById(1L)).thenReturn(Optional.of(pari(PariStatus.OPEN, null)));

        pariMessageService.refresh(1L);

        verify(jda, never()).getChannelById(eq(MessageChannel.class), anyString());
    }

    @Test
    void refreshIsNoOpForUnknownPari() {
        when(pariService.findById(99L)).thenReturn(Optional.empty());

        pariMessageService.refresh(99L);

        verify(jda, never()).getChannelById(eq(MessageChannel.class), anyString());
    }

    @Test
    void refreshSurvivesMissingChannel() {
        Pari pari = pari(PariStatus.OPEN, null);
        pari.setChannelId("channel-1");
        pari.setMessageId("message-1");
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));
        when(jda.getChannelById(eq(MessageChannel.class), anyString())).thenReturn(null);

        pariMessageService.refresh(1L);
    }

    // --- сводка выплат ---

    @Test
    void resultsEmbedShowsMentionsBetsAndPayouts() {
        Pari pari = finishedPari();
        List<PariBet> bets = List.of(bet("user-1", true, 300L, 570L), bet("user-2", false, 500L, 0L));

        MessageEmbed embed = pariMessageService.buildResultsEmbed(pari, bets);

        assertThat(embed.getTitle()).contains("Итоги пари").contains("Победит ли команда?");
        assertThat(embed.getDescription())
                .contains("Победил вариант «Да»").contains("1.90")
                .contains("950").contains("1000").contains("комиссия").contains("50 LP");
        assertThat(embed.getFields()).hasSize(2);
        assertThat(embed.getFields().get(0).getName()).contains("Победители").contains("(1)");
        assertThat(embed.getFields().get(0).getValue())
                .contains("<@user-1>").contains("300 LP").contains("570").contains("+270");
        assertThat(embed.getFields().get(1).getName()).contains("Проигравшие").contains("(1)");
        assertThat(embed.getFields().get(1).getValue())
                .contains("<@user-2>").contains("500 LP").contains("−500");
        assertThat(embed.getFooter().getText()).contains("2 участников").contains("800 LP");
    }

    @Test
    void resultsEmbedSortsWinnersByPayout() {
        Pari pari = finishedPari();
        List<PariBet> bets = List.of(bet("small", true, 100L, 190L), bet("big", true, 400L, 760L));

        String winners = pariMessageService.buildResultsEmbed(pari, bets).getFields().get(0).getValue();

        assertThat(winners.indexOf("<@big>")).isLessThan(winners.indexOf("<@small>"));
    }

    @Test
    void canceledPariShowsRefundsOnly() {
        Pari pari = pari(PariStatus.CANCELED, null);
        pari.setSettledAt(Instant.now());
        List<PariBet> bets = List.of(bet("user-1", true, 300L, 300L), bet("user-2", false, 500L, 500L));

        MessageEmbed embed = pariMessageService.buildResultsEmbed(pari, bets);

        assertThat(embed.getDescription()).contains("отменено").contains("800");
        assertThat(embed.getFields()).hasSize(1);
        assertThat(embed.getFields().get(0).getName()).contains("Возврат");
        assertThat(embed.getFields().get(0).getValue())
                .contains("<@user-1>").contains("<@user-2>").contains("возврат");
    }

    @Test
    void refundIsShownWhenNobodyBetOnTheWinningOption() {
        Pari pari = finishedPari();
        pari.setWinningSum(0L);
        pari.setWinningCoefficient(null);

        MessageEmbed embed = pariMessageService.buildResultsEmbed(pari, List.of(bet("user-1", false, 300L, 300L)));

        assertThat(embed.getDescription()).contains("ставок на него не было");
        assertThat(embed.getFields().get(0).getName()).contains("Возврат");
    }

    @Test
    void longParticipantListIsFoldedIntoCounter() {
        Pari pari = finishedPari();
        List<PariBet> bets = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bets.add(bet("user-" + i, true, 100L, 190L));
        }

        MessageEmbed.Field winners = pariMessageService.buildResultsEmbed(pari, bets).getFields().get(0);

        assertThat(winners.getValue()).hasSizeLessThanOrEqualTo(1024);
        assertThat(winners.getValue()).contains("и ещё");
    }

    // --- публикация сводки ---

    @Test
    void publishResultsRepliesToPariMessage() {
        Pari pari = settledPariWithMessage();
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));
        when(pariService.getBets(1L)).thenReturn(List.of(bet("user-1", true, 300L, 570L)));
        when(pariService.claimResultsPublication(1L)).thenReturn(true);

        MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);
        MessageCreateAction action = org.mockito.Mockito.mock(MessageCreateAction.class);
        when(jda.getChannelById(eq(MessageChannel.class), anyString())).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class), any(MessageEmbed[].class))).thenReturn(action);
        when(action.setMessageReference(anyString())).thenReturn(action);
        when(action.failOnInvalidReply(anyBoolean())).thenReturn(action);
        when(action.mentionRepliedUser(anyBoolean())).thenReturn(action);

        pariMessageService.publishResults(1L);

        verify(action).setMessageReference("message-1");
        verify(action).queue(any(), any());
    }

    @Test
    void publishResultsIsSkippedWhenAlreadyPosted() {
        Pari pari = settledPariWithMessage();
        pari.setResultsPostedAt(Instant.now());
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));

        pariMessageService.publishResults(1L);

        verify(pariService, never()).claimResultsPublication(anyLong());
        verify(jda, never()).getChannelById(eq(MessageChannel.class), anyString());
    }

    @Test
    void publishResultsWaitsUntilSettlementIsComplete() {
        Pari pari = settledPariWithMessage();
        pari.setSettledAt(null);
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));

        pariMessageService.publishResults(1L);

        verify(pariService, never()).claimResultsPublication(anyLong());
    }

    @Test
    void publishResultsIsSkippedWhenNobodyBet() {
        Pari pari = settledPariWithMessage();
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));
        when(pariService.getBets(1L)).thenReturn(List.of());

        pariMessageService.publishResults(1L);

        verify(pariService, never()).claimResultsPublication(anyLong());
    }

    @Test
    void publishResultsDoesNotSendWhenClaimIsLost() {
        Pari pari = settledPariWithMessage();
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));
        when(pariService.getBets(1L)).thenReturn(List.of(bet("user-1", true, 300L, 570L)));
        when(pariService.claimResultsPublication(1L)).thenReturn(false);

        MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);
        when(jda.getChannelById(eq(MessageChannel.class), anyString())).thenReturn(channel);

        pariMessageService.publishResults(1L);

        verify(channel, never()).sendMessageEmbeds(any(MessageEmbed.class), any(MessageEmbed[].class));
    }

    @Test
    void publishResultsSurvivesMissingChannel() {
        Pari pari = settledPariWithMessage();
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));
        when(pariService.getBets(1L)).thenReturn(List.of(bet("user-1", true, 300L, 570L)));
        when(jda.getChannelById(eq(MessageChannel.class), anyString())).thenReturn(null);

        pariMessageService.publishResults(1L);

        // Право на публикацию не тратится: сводку еще можно будет отправить позже
        verify(pariService, never()).claimResultsPublication(anyLong());
    }

    private static Pari finishedPari() {
        Pari pari = pari(PariStatus.FINISHED, Boolean.TRUE);
        pari.setTotalPool(1000L);
        pari.setPrizePool(950L);
        pari.setWinningSum(500L);
        pari.setWinningCoefficient(new BigDecimal("1.9000"));
        pari.setSettledAt(Instant.now());
        return pari;
    }

    private static Pari settledPariWithMessage() {
        Pari pari = finishedPari();
        pari.setChannelId("channel-1");
        pari.setMessageId("message-1");
        return pari;
    }

    private static PariBet bet(String userId, boolean option, long amount, long payout) {
        GuildMember member = new GuildMember();
        member.setId(1L);
        member.setGuildId("guild-1");
        member.setUserId(userId);

        PariBet bet = new PariBet();
        bet.setMember(member);
        bet.setOption(option);
        bet.setAmount(amount);
        bet.setPayout(payout);
        bet.setSettled(true);
        return bet;
    }

    private static List<Button> buttons(MessageTopLevelComponent row) {
        return ((ActionRow) row).getComponents().stream()
                .map(component -> (Button) component)
                .toList();
    }

    private static Pari pari(PariStatus status, Boolean winningOption) {
        Pari pari = new Pari();
        pari.setId(1L);
        pari.setGuildId("guild-1");
        pari.setAuthorId("author-1");
        pari.setTitle("Победит ли команда?");
        pari.setStatus(status);
        pari.setWinningOption(winningOption);
        pari.setCommissionRate(new BigDecimal("0.05"));
        pari.setCreatedAt(Instant.now());
        return pari;
    }
}
