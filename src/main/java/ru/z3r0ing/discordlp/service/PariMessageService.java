package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.springframework.stereotype.Service;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Отрисовка сообщения-опроса пари и разбор идентификаторов его компонентов.
 * <p>
 * Формат customId: {@code pari:<действие>:<id пари>[:<вариант>]}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PariMessageService {

    public static final String ID_PREFIX = "pari";
    public static final String ACTION_BET = "bet";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_FINISH = "finish";
    public static final String ACTION_CANCEL = "cancel";
    /** customId поля ввода суммы внутри модального окна. */
    public static final String INPUT_AMOUNT = "amount";

    public static final String OPTION_YES = "yes";
    public static final String OPTION_NO = "no";

    private static final Color COLOR_OPEN = new Color(0x5865F2);
    private static final Color COLOR_RESOLVING = new Color(0xFAA61A);
    private static final Color COLOR_FINISHED = new Color(0x57F287);
    private static final Color COLOR_CANCELED = new Color(0x99AAB5);

    private final JDA jda;
    private final PariService pariService;

    public static String customId(String action, Long pariId) {
        return ID_PREFIX + ":" + action + ":" + pariId;
    }

    public static String customId(String action, Long pariId, boolean option) {
        return customId(action, pariId) + ":" + (option ? OPTION_YES : OPTION_NO);
    }

    public MessageCreateData buildCreateData(Pari pari) {
        return new MessageCreateBuilder()
                .addEmbeds(buildEmbed(pari))
                .addComponents(buildComponents(pari))
                .build();
    }

    public MessageEditData buildEditData(Pari pari) {
        return new MessageEditBuilder()
                .setEmbeds(buildEmbed(pari))
                .setComponents(buildComponents(pari))
                .build();
    }

    /** Модальное окно ввода суммы ставки. */
    public Modal buildBetModal(Pari pari, boolean option) {
        TextInput amountInput = TextInput.create(INPUT_AMOUNT, TextInputStyle.SHORT)
                .setPlaceholder("Например: 500")
                .setRequired(true)
                .setMaxLength(String.valueOf(PariService.MAX_BET).length())
                .build();

        return Modal.create(customId(ACTION_BET, pari.getId(), option), truncate(pari.getTitle(), 45))
                .addComponents(Label.of("Ставка на вариант «" + optionName(option) + "» (LP)", amountInput))
                .build();
    }

    public MessageEmbed buildEmbed(Pari pari) {
        PariStats stats = pariService.getStats(pari.getId());
        PariOdds odds = PariOdds.of(stats, pari.getCommissionRate());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 " + pari.getTitle())
                .setDescription("Автор: <@" + pari.getAuthorId() + ">")
                .setColor(statusColor(pari.getStatus()))
                .addField("✅ Да", optionField(stats.yesCount(), stats.yesPool(), odds.yesCoefficient()), true)
                .addField("❌ Нет", optionField(stats.noCount(), stats.noPool(), odds.noCoefficient()), true)
                .addField("Итого", totalField(stats, odds, pari), true)
                .setFooter(statusText(pari));

        if (pari.getCreatedAt() != null) {
            embed.setTimestamp(pari.getCreatedAt());
        }
        return embed.build();
    }

    private String optionField(long count, long pool, BigDecimal coefficient) {
        return count + " ставок\nПул: " + pool + " LP\nКоэф.: " + formatCoefficient(coefficient);
    }

    private String totalField(PariStats stats, PariOdds odds, Pari pari) {
        StringBuilder text = new StringBuilder()
                .append(stats.totalCount()).append(" участников\n")
                .append("Пул: ").append(stats.totalPool()).append(" LP");
        if (odds.commission() > 0) {
            text.append("\nКомиссия ").append(formatPercent(pari.getCommissionRate()))
                    .append(": ").append(odds.commission()).append(" LP");
        }
        return text.toString();
    }

    /** Коэффициент в виде «1.85»; прочерк, если на вариант еще никто не поставил. */
    public static String formatCoefficient(BigDecimal coefficient) {
        return coefficient == null ? "—" : coefficient.setScale(2, RoundingMode.DOWN).toPlainString();
    }

    private static String formatPercent(BigDecimal rate) {
        BigDecimal percent = (rate == null ? BigDecimal.ZERO : rate).multiply(BigDecimal.valueOf(100));
        return percent.stripTrailingZeros().toPlainString() + "%";
    }

    public List<MessageTopLevelComponent> buildComponents(Pari pari) {
        List<MessageTopLevelComponent> rows = new ArrayList<>();
        boolean acceptingBets = pari.getStatus() == PariStatus.OPEN;

        rows.add(ActionRow.of(
                Button.success(customId(ACTION_BET, pari.getId(), true), "Да").withDisabled(!acceptingBets),
                Button.danger(customId(ACTION_BET, pari.getId(), false), "Нет").withDisabled(!acceptingBets)
        ));

        // Кнопки управления показываются, пока пари не в терминальном статусе.
        // Права проверяются при нажатии: управлять может только автор.
        if (pari.getStatus() == PariStatus.OPEN || pari.getStatus() == PariStatus.RESOLVING) {
            rows.add(ActionRow.of(
                    Button.secondary(customId(ACTION_STOP, pari.getId()), "⏸ Остановить ставки")
                            .withDisabled(!acceptingBets),
                    Button.success(customId(ACTION_FINISH, pari.getId(), true), "Завершить: ДА"),
                    Button.danger(customId(ACTION_FINISH, pari.getId(), false), "Завершить: НЕТ"),
                    Button.secondary(customId(ACTION_CANCEL, pari.getId()), "🚫 Отменить")
            ));
        }

        return rows;
    }

    /**
     * Перерисовывает сообщение пари по сохраненным координатам канала и сообщения.
     * Ошибки только логируются: расчет средств от доступности сообщения не зависит.
     */
    public void refresh(Long pariId) {
        Pari pari = pariService.findById(pariId).orElse(null);
        if (pari == null || pari.getChannelId() == null || pari.getMessageId() == null) {
            return;
        }

        try {
            MessageChannel channel = jda.getChannelById(MessageChannel.class, pari.getChannelId());
            if (channel == null) {
                log.warn("Канал {} для пари {} недоступен, сообщение не обновлено", pari.getChannelId(), pariId);
                return;
            }
            channel.editMessageById(pari.getMessageId(), buildEditData(pari)).queue(
                    success -> log.debug("Сообщение пари {} обновлено", pariId),
                    failure -> log.warn("Не удалось обновить сообщение пари {}: {}", pariId, failure.getMessage())
            );
        } catch (Exception e) {
            log.error("Ошибка при обновлении сообщения пари {}", pariId, e);
        }
    }

    public static String optionName(boolean option) {
        return option ? "Да" : "Нет";
    }

    private String statusText(Pari pari) {
        return switch (pari.getStatus()) {
            case OPEN -> "Идет прием ставок · коэффициенты меняются с каждой ставкой";
            case RESOLVING -> "Прием ставок остановлен, ожидаем итогов";
            case FINISHED -> finishedStatusText(pari);
            case CANCELED -> "Отменено · ставки возвращены участникам";
        };
    }

    private String finishedStatusText(Pari pari) {
        String winner = "Завершено · победил вариант «"
                + optionName(Boolean.TRUE.equals(pari.getWinningOption())) + "»";
        if (pari.getWinningSum() != null && pari.getWinningSum() == 0) {
            return winner + " · ставок на него не было, средства возвращены";
        }
        return winner + " · коэффициент " + formatCoefficient(pari.getWinningCoefficient());
    }

    private Color statusColor(PariStatus status) {
        return switch (status) {
            case OPEN -> COLOR_OPEN;
            case RESOLVING -> COLOR_RESOLVING;
            case FINISHED -> COLOR_FINISHED;
            case CANCELED -> COLOR_CANCELED;
        };
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
