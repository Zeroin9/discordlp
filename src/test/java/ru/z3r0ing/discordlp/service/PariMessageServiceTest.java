package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        when(pariService.getStats(any())).thenReturn(new PariStats(2L, 500L, 1L, 200L));
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
    void embedShowsTitleAuthorAndPools() {
        MessageEmbed embed = pariMessageService.buildEmbed(pari(PariStatus.OPEN, null));

        assertThat(embed.getTitle()).contains("Победит ли команда?");
        assertThat(embed.getDescription()).contains("<@author-1>");
        assertThat(embed.getFields()).hasSize(3);
        assertThat(embed.getFields().get(0).getValue()).contains("2 ставок").contains("500 LP");
        assertThat(embed.getFields().get(1).getValue()).contains("1 ставок").contains("200 LP");
        assertThat(embed.getFields().get(2).getValue()).contains("3 участников").contains("700 LP");
        assertThat(embed.getFooter().getText()).contains("Идет прием ставок");
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
        pari.setCreatedAt(Instant.now());
        return pari;
    }
}
