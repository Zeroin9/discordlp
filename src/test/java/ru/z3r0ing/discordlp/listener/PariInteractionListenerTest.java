package ru.z3r0ing.discordlp.listener;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.service.PariException;
import ru.z3r0ing.discordlp.service.PariMessageService;
import ru.z3r0ing.discordlp.service.PariOdds;
import ru.z3r0ing.discordlp.service.PariService;
import ru.z3r0ing.discordlp.service.PariSettlementService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PariInteractionListenerTest {

    private static final String GUILD_ID = "guild-1";
    private static final String AUTHOR_ID = "author-1";
    private static final String BETTER_ID = "better-1";

    @Mock
    private PariService pariService;
    @Mock
    private PariSettlementService pariSettlementService;
    @Mock
    private PariMessageService pariMessageService;

    private PariInteractionListener listener;
    private Guild guild;

    @BeforeEach
    void setUp() {
        listener = new PariInteractionListener(pariService, pariSettlementService, pariMessageService);
        guild = Mockito.mock(Guild.class);
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(pariMessageService.buildEditData(any())).thenReturn(editData());
    }

    // --- маршрутизация ---

    @Test
    void ignoresForeignComponents() {
        ButtonInteractionEvent event = button("other:action:1", BETTER_ID);

        listener.onButtonInteraction(event);

        verifyNoInteractions(pariService);
    }

    @Test
    void ignoresMalformedPariComponentId() {
        ButtonInteractionEvent event = button("pari:bet", BETTER_ID);

        listener.onButtonInteraction(event);

        verifyNoInteractions(pariService);
    }

    @Test
    void ignoresNonNumericPariId() {
        ButtonInteractionEvent event = button("pari:bet:abc:yes", BETTER_ID);

        listener.onButtonInteraction(event);

        verifyNoInteractions(pariService);
    }

    @Test
    void rejectsButtonOutsideGuild() {
        ButtonInteractionEvent event = button("pari:bet:1:yes", BETTER_ID);
        when(event.getGuild()).thenReturn(null);

        listener.onButtonInteraction(event);

        assertThat(reply(event)).contains("только на серверах");
    }

    @Test
    void ignoresUnknownPariAction() {
        ButtonInteractionEvent event = button("pari:unknown:1", BETTER_ID);

        listener.onButtonInteraction(event);

        verifyNoInteractions(pariService);
        verify(event, never()).deferEdit();
    }

    @Test
    void rejectsModalOutsideGuild() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, "100");
        when(event.getGuild()).thenReturn(null);

        listener.onModalInteraction(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        assertThat(captor.getValue()).contains("только на серверах");
        verify(pariService, never()).placeBet(anyLong(), any(), any(), anyBoolean(), anyLong());
    }

    @Test
    void modalWithoutAmountFieldIsRejected() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, "100");
        when(event.getValue(PariMessageService.INPUT_AMOUNT)).thenReturn(null);

        listener.onModalInteraction(event);

        assertThat(hookMessage(event)).contains("целым положительным числом");
    }

    // --- ставки ---

    @Test
    void betButtonOpensAmountModal() {
        ButtonInteractionEvent event = button("pari:bet:1:yes", BETTER_ID);
        Pari pari = pari(PariStatus.OPEN);
        when(pariService.findById(1L)).thenReturn(Optional.of(pari));
        when(pariService.findBet(1L, GUILD_ID, BETTER_ID)).thenReturn(Optional.empty());

        Modal modal = Mockito.mock(Modal.class);
        when(pariMessageService.buildBetModal(pari, true)).thenReturn(modal);
        ModalCallbackAction modalAction = Mockito.mock(ModalCallbackAction.class);
        when(event.replyModal(modal)).thenReturn(modalAction);

        listener.onButtonInteraction(event);

        verify(modalAction).queue();
    }

    @Test
    void betButtonRefusesAuthor() {
        ButtonInteractionEvent event = button("pari:bet:1:no", AUTHOR_ID);
        when(pariService.findById(1L)).thenReturn(Optional.of(pari(PariStatus.OPEN)));

        listener.onButtonInteraction(event);

        assertThat(reply(event)).contains("Автор не может");
        verify(event, never()).replyModal(any());
    }

    @Test
    void betButtonRefusesClosedPari() {
        ButtonInteractionEvent event = button("pari:bet:1:yes", BETTER_ID);
        when(pariService.findById(1L)).thenReturn(Optional.of(pari(PariStatus.RESOLVING)));

        listener.onButtonInteraction(event);

        assertThat(reply(event)).contains("закрыт");
    }

    @Test
    void betButtonRefusesUnknownPari() {
        ButtonInteractionEvent event = button("pari:bet:1:yes", BETTER_ID);
        when(pariService.findById(1L)).thenReturn(Optional.empty());

        listener.onButtonInteraction(event);

        assertThat(reply(event)).contains("не найдено");
    }

    @Test
    void betButtonRefusesSecondBetAndShowsExistingOne() {
        ButtonInteractionEvent event = button("pari:bet:1:yes", BETTER_ID);
        when(pariService.findById(1L)).thenReturn(Optional.of(pari(PariStatus.OPEN)));
        when(pariService.findBet(1L, GUILD_ID, BETTER_ID)).thenReturn(Optional.of(bet(250L, true)));

        listener.onButtonInteraction(event);

        assertThat(reply(event)).contains("250").contains("Да").contains("Изменить выбор нельзя");
    }

    @Test
    void modalSubmissionPlacesBetAndRefreshesPoll() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, "300");
        PariBet placed = bet(300L, true);
        when(pariService.placeBet(eq(1L), eq(guild), any(User.class), eq(true), eq(300L))).thenReturn(placed);
        when(pariService.getOdds(placed.getPari()))
                .thenReturn(new PariOdds(1_000L, 950L, new BigDecimal("1.9000"), new BigDecimal("1.9000")));

        listener.onModalInteraction(event);

        assertThat(hookMessage(event)).contains("Ставка принята").contains("300").contains("1.90");
        verify(pariMessageService).refresh(1L);
    }

    @Test
    void modalSubmissionRejectsNonNumericAmount() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, "много");

        listener.onModalInteraction(event);

        assertThat(hookMessage(event)).contains("целым положительным числом");
        verify(pariService, never()).placeBet(anyLong(), any(), any(), anyBoolean(), anyLong());
    }

    @Test
    void modalSubmissionRejectsNegativeAmount() {
        ModalInteractionEvent event = modal("pari:bet:1:no", BETTER_ID, "-100");

        listener.onModalInteraction(event);

        assertThat(hookMessage(event)).contains("целым положительным числом");
    }

    @Test
    void modalSubmissionAcceptsGroupedDigits() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, " 1 000 ");
        when(pariService.placeBet(eq(1L), eq(guild), any(User.class), eq(true), eq(1_000L)))
                .thenReturn(bet(1_000L, true));

        listener.onModalInteraction(event);

        verify(pariService).placeBet(eq(1L), eq(guild), any(User.class), eq(true), eq(1_000L));
    }

    @Test
    void modalSubmissionReportsBusinessRuleViolation() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, "300");
        when(pariService.placeBet(anyLong(), any(), any(), anyBoolean(), anyLong()))
                .thenThrow(new PariException("Недостаточно поинтов."));

        listener.onModalInteraction(event);

        assertThat(hookMessage(event)).contains("Недостаточно поинтов");
        verify(pariMessageService, never()).refresh(anyLong());
    }

    @Test
    void modalSubmissionSurvivesUnexpectedFailure() {
        ModalInteractionEvent event = modal("pari:bet:1:yes", BETTER_ID, "300");
        when(pariService.placeBet(anyLong(), any(), any(), anyBoolean(), anyLong()))
                .thenThrow(new IllegalStateException("db down"));

        listener.onModalInteraction(event);

        assertThat(hookMessage(event)).contains("Не удалось принять ставку");
    }

    @Test
    void ignoresForeignModal() {
        ModalInteractionEvent event = modal("other:modal:1", BETTER_ID, "300");

        listener.onModalInteraction(event);

        verifyNoInteractions(pariService);
    }

    // --- управление пари ---

    @Test
    void stopButtonClosesBetting() {
        ButtonInteractionEvent event = button("pari:stop:1", AUTHOR_ID);
        when(pariService.closeBetting(1L, AUTHOR_ID)).thenReturn(pari(PariStatus.RESOLVING));

        listener.onButtonInteraction(event);

        verify(pariService).closeBetting(1L, AUTHOR_ID);
        verify(event.getHook()).editOriginal(any(MessageEditData.class));
    }

    @Test
    void finishButtonSettlesAndAnnouncesWinner() {
        ButtonInteractionEvent event = button("pari:finish:1:yes", AUTHOR_ID);
        Pari finished = pari(PariStatus.FINISHED);
        finished.setWinningSum(500L);
        finished.setWinningCoefficient(new BigDecimal("1.9000"));
        when(pariService.finish(1L, AUTHOR_ID, true)).thenReturn(finished);

        listener.onButtonInteraction(event);

        verify(pariSettlementService).settle(1L);
        verify(event.getHook()).editOriginal(any(MessageEditData.class));
        assertThat(hookMessage(event)).contains("Пари завершено").contains("Да").contains("1.90");
    }

    @Test
    void finishButtonReportsRefundWhenNobodyPickedTheWinner() {
        ButtonInteractionEvent event = button("pari:finish:1:yes", AUTHOR_ID);
        Pari pari = pari(PariStatus.FINISHED);
        pari.setWinningSum(0L);
        when(pariService.finish(1L, AUTHOR_ID, true)).thenReturn(pari);

        listener.onButtonInteraction(event);

        verify(pariSettlementService).settle(1L);
        assertThat(hookMessage(event)).contains("ставок на него не было").contains("возвращены");
    }

    @Test
    void finishButtonRefusesNonAuthor() {
        ButtonInteractionEvent event = button("pari:finish:1:no", BETTER_ID);
        when(pariService.finish(1L, BETTER_ID, false))
                .thenThrow(new PariException("Управлять пари может только его автор."));

        listener.onButtonInteraction(event);

        assertThat(hookMessage(event)).contains("только его автор");
        verify(pariSettlementService, never()).settle(anyLong());
    }

    @Test
    void cancelButtonRefundsBets() {
        ButtonInteractionEvent event = button("pari:cancel:1", AUTHOR_ID);
        when(pariService.cancel(1L, AUTHOR_ID)).thenReturn(pari(PariStatus.CANCELED));

        listener.onButtonInteraction(event);

        verify(pariSettlementService).settle(1L);
        assertThat(hookMessage(event)).contains("ставки возвращены");
    }

    @Test
    void cancelButtonRefusesNonAuthor() {
        ButtonInteractionEvent event = button("pari:cancel:1", BETTER_ID);
        when(pariService.cancel(1L, BETTER_ID))
                .thenThrow(new PariException("Управлять пари может только его автор."));

        listener.onButtonInteraction(event);

        assertThat(hookMessage(event)).contains("только его автор");
        verify(pariSettlementService, never()).settle(anyLong());
    }

    @Test
    void unexpectedFailureIsReportedToUser() {
        ButtonInteractionEvent event = button("pari:bet:1:yes", BETTER_ID);
        when(pariService.findById(1L)).thenThrow(new IllegalStateException("db down"));

        listener.onButtonInteraction(event);

        assertThat(reply(event)).contains("Не удалось выполнить действие");
    }

    // --- фикстуры ---

    private ButtonInteractionEvent button(String componentId, String userId) {
        User user = user(userId);
        InteractionHook hook = hook();

        ButtonInteractionEvent event = Mockito.mock(ButtonInteractionEvent.class);
        lenient().when(event.getComponentId()).thenReturn(componentId);
        lenient().when(event.getGuild()).thenReturn(guild);
        lenient().when(event.getUser()).thenReturn(user);
        lenient().when(event.isAcknowledged()).thenReturn(false);

        ReplyCallbackAction replyAction = Mockito.mock(ReplyCallbackAction.class);
        lenient().when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        lenient().when(event.reply(anyString())).thenReturn(replyAction);

        MessageEditCallbackAction deferAction = Mockito.mock(MessageEditCallbackAction.class);
        lenient().when(event.deferEdit()).thenReturn(deferAction);
        lenient().when(event.getHook()).thenReturn(hook);
        return event;
    }

    private ModalInteractionEvent modal(String modalId, String userId, String amount) {
        User user = user(userId);
        InteractionHook hook = hook();

        ModalInteractionEvent event = Mockito.mock(ModalInteractionEvent.class);
        lenient().when(event.getModalId()).thenReturn(modalId);
        lenient().when(event.getGuild()).thenReturn(guild);
        lenient().when(event.getUser()).thenReturn(user);

        ReplyCallbackAction deferAction = Mockito.mock(ReplyCallbackAction.class);
        lenient().when(deferAction.setEphemeral(anyBoolean())).thenReturn(deferAction);
        lenient().when(event.deferReply(anyBoolean())).thenReturn(deferAction);
        lenient().when(event.reply(anyString())).thenReturn(deferAction);
        lenient().when(event.getHook()).thenReturn(hook);

        ModalMapping mapping = Mockito.mock(ModalMapping.class);
        lenient().when(mapping.getAsString()).thenReturn(amount);
        lenient().when(event.getValue(PariMessageService.INPUT_AMOUNT)).thenReturn(mapping);
        return event;
    }

    @SuppressWarnings("unchecked")
    private static InteractionHook hook() {
        InteractionHook hook = Mockito.mock(InteractionHook.class);

        WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message> createAction =
                Mockito.mock(WebhookMessageCreateAction.class);
        lenient().when(createAction.setEphemeral(anyBoolean())).thenReturn(createAction);
        lenient().when(hook.sendMessage(anyString())).thenReturn(createAction);

        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> editAction =
                Mockito.mock(WebhookMessageEditAction.class);
        lenient().when(hook.editOriginal(any(MessageEditData.class))).thenReturn(editAction);
        return hook;
    }

    private static String reply(ButtonInteractionEvent event) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        return captor.getValue();
    }

    private static String hookMessage(ButtonInteractionEvent event) {
        return hookMessage(event.getHook());
    }

    private static String hookMessage(ModalInteractionEvent event) {
        return hookMessage(event.getHook());
    }

    private static String hookMessage(InteractionHook hook) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(captor.capture());
        return captor.getValue();
    }

    private static User user(String id) {
        User user = Mockito.mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getName()).thenReturn(id);
        return user;
    }

    private static Pari pari(PariStatus status) {
        Pari pari = new Pari();
        pari.setId(1L);
        pari.setGuildId(GUILD_ID);
        pari.setAuthorId(AUTHOR_ID);
        pari.setTitle("Пари");
        pari.setStatus(status);
        pari.setCreatedAt(Instant.now());
        return pari;
    }

    private static PariBet bet(long amount, boolean option) {
        GuildMember member = new GuildMember();
        member.setId(7L);
        member.setUserId(BETTER_ID);

        PariBet bet = new PariBet();
        bet.setId(10L);
        bet.setPari(pari(PariStatus.OPEN));
        bet.setMember(member);
        bet.setAmount(amount);
        bet.setOption(option);
        bet.setCreatedAt(Instant.now());
        return bet;
    }

    private static MessageEditData editData() {
        return new MessageEditBuilder().setContent("опрос").build();
    }
}
