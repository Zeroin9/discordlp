package ru.z3r0ing.discordlp.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariBet;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.service.PariException;
import ru.z3r0ing.discordlp.service.PariMessageService;
import ru.z3r0ing.discordlp.service.PariService;
import ru.z3r0ing.discordlp.service.PariSettlementService;

import java.util.Optional;

/**
 * Обработка кнопок и модальных окон пари.
 * <p>
 * customId компонентов: {@code pari:<действие>:<id пари>[:yes|no]}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PariInteractionListener extends ListenerAdapter {

    private static final String ID_SEPARATOR = ":";

    private final PariService pariService;
    private final PariSettlementService pariSettlementService;
    private final PariMessageService pariMessageService;

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        ComponentId componentId = ComponentId.parse(event.getComponentId());
        if (componentId == null) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("Пари доступны только на серверах.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (componentId.action()) {
                case PariMessageService.ACTION_BET -> openBetModal(event, componentId);
                case PariMessageService.ACTION_STOP -> stopBetting(event, componentId);
                case PariMessageService.ACTION_FINISH -> finishPari(event, componentId);
                case PariMessageService.ACTION_CANCEL -> cancelPari(event, componentId);
                default -> log.warn("Неизвестное действие пари: {}", componentId.action());
            }
        } catch (PariException e) {
            replyError(event, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при обработке кнопки пари {}", event.getComponentId(), e);
            replyError(event, "Не удалось выполнить действие. Попробуйте еще раз.");
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        ComponentId componentId = ComponentId.parse(event.getModalId());
        if (componentId == null || !PariMessageService.ACTION_BET.equals(componentId.action())) {
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Пари доступны только на серверах.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Long amount = parseAmount(event.getValue(PariMessageService.INPUT_AMOUNT));
        if (amount == null) {
            hook.sendMessage("Сумма ставки должна быть целым положительным числом.").queue();
            return;
        }

        boolean option = componentId.option() != null && componentId.option();
        try {
            PariBet bet = pariService.placeBet(componentId.pariId(), guild, event.getUser(), option, amount);
            hook.sendMessage("Ставка принята: **" + bet.getAmount() + "** LP на вариант «"
                    + PariMessageService.optionName(option) + "». Возможный выигрыш: **"
                    + bet.getAmount() * PariService.WIN_MULTIPLIER + "** LP.").queue();
            pariMessageService.refresh(componentId.pariId());
        } catch (PariException e) {
            hook.sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Ошибка при приеме ставки в пари {}", componentId.pariId(), e);
            hook.sendMessage("Не удалось принять ставку. Попробуйте еще раз.").queue();
        }
    }

    private void openBetModal(ButtonInteractionEvent event, ComponentId componentId) {
        Pari pari = pariService.findById(componentId.pariId())
                .orElseThrow(() -> new PariException("Пари не найдено."));

        if (pari.getStatus() != PariStatus.OPEN) {
            throw new PariException("Прием ставок по этому пари уже закрыт.");
        }
        if (pari.getAuthorId().equals(event.getUser().getId())) {
            throw new PariException("Автор не может делать ставки в собственном пари.");
        }

        Optional<PariBet> existingBet = pariService.findBet(pari.getId(), pari.getGuildId(), event.getUser().getId());
        if (existingBet.isPresent()) {
            PariBet bet = existingBet.get();
            throw new PariException("Вы уже поставили **" + bet.getAmount() + "** LP на вариант «"
                    + PariMessageService.optionName(bet.getOption()) + "». Изменить выбор нельзя.");
        }

        boolean option = componentId.option() != null && componentId.option();
        event.replyModal(pariMessageService.buildBetModal(pari, option)).queue();
    }

    private void stopBetting(ButtonInteractionEvent event, ComponentId componentId) {
        event.deferEdit().queue();
        try {
            Pari pari = pariService.closeBetting(componentId.pariId(), event.getUser().getId());
            event.getHook().editOriginal(pariMessageService.buildEditData(pari)).queue();
        } catch (PariException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void finishPari(ButtonInteractionEvent event, ComponentId componentId) {
        boolean winningOption = componentId.option() != null && componentId.option();
        event.deferEdit().queue();
        try {
            Pari pari = pariService.finish(componentId.pariId(), event.getUser().getId(), winningOption);
            // Начисление идемпотентно: незавершенный расчет доведет планировщик.
            pariSettlementService.settle(pari.getId());
            event.getHook().editOriginal(pariMessageService.buildEditData(pari)).queue();
            event.getHook().sendMessage("Пари завершено. Победил вариант «"
                    + PariMessageService.optionName(winningOption) + "», выигрыши начислены.")
                    .setEphemeral(true).queue();
        } catch (PariException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void cancelPari(ButtonInteractionEvent event, ComponentId componentId) {
        event.deferEdit().queue();
        try {
            Pari pari = pariService.cancel(componentId.pariId(), event.getUser().getId());
            pariSettlementService.settle(pari.getId());
            event.getHook().editOriginal(pariMessageService.buildEditData(pari)).queue();
            event.getHook().sendMessage("Пари отменено, ставки возвращены участникам.")
                    .setEphemeral(true).queue();
        } catch (PariException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void replyError(ButtonInteractionEvent event, String message) {
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(message).setEphemeral(true).queue();
        } else {
            event.reply(message).setEphemeral(true).queue();
        }
    }

    private Long parseAmount(ModalMapping mapping) {
        if (mapping == null) {
            return null;
        }
        String raw = mapping.getAsString().trim().replace(" ", "").replace("_", "");
        try {
            long amount = Long.parseLong(raw);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Разобранный customId компонента пари.
     *
     * @param option выбранный вариант или {@code null}, если действие его не требует
     */
    private record ComponentId(String action, Long pariId, Boolean option) {

        static ComponentId parse(String customId) {
            if (customId == null || !customId.startsWith(PariMessageService.ID_PREFIX + ID_SEPARATOR)) {
                return null;
            }

            String[] parts = customId.split(ID_SEPARATOR);
            if (parts.length < 3) {
                return null;
            }

            try {
                Long pariId = Long.parseLong(parts[2]);
                Boolean option = parts.length > 3
                        ? PariMessageService.OPTION_YES.equals(parts[3])
                        : null;
                return new ComponentId(parts[1], pariId, option);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
