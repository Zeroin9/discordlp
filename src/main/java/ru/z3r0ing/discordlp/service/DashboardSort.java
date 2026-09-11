package ru.z3r0ing.discordlp.service;

import org.springframework.data.domain.Sort;
import ru.z3r0ing.discordlp.entity.GuildMember;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * Колонки таблицы участников, по которым разрешена сортировка.
 *
 * <p>Колонки делятся на два вида. Поля участника ({@link #getEntityProperty()} не пустое)
 * сортируются самой БД вместе с пагинацией. Колонки времени в конференции в БД не хранятся —
 * они восстанавливаются по журналу начислений, поэтому сортируются уже в приложении
 * через {@link #comparator(Sort.Direction)}.
 */
public enum DashboardSort {

    USER_NAME("userName", "Имя пользователя", "userName", Sort.Direction.ASC, false,
            Comparator.comparing(row -> row.member().getUserName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))),

    GUILD_NAME("guildName", "Название гильдии", "guildName", Sort.Direction.ASC, false,
            Comparator.comparing(row -> row.member().getGuildName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))),

    BALANCE("balance", "Баланс LP", "balance", Sort.Direction.DESC, true,
            Comparator.comparing(row -> Optional.ofNullable(row.member().getBalance()).orElse(0L))),

    TOTAL_TIME("voiceTime", "Время в конфе", null, Sort.Direction.DESC, true,
            Comparator.comparing(DashboardMemberView::totalTime)),

    STREAM_TIME("streamTime", "Со стримом", null, Sort.Direction.DESC, true,
            Comparator.comparing(DashboardMemberView::streamTime)),

    NO_STREAM_TIME("noStreamTime", "Без стрима", null, Sort.Direction.DESC, true,
            Comparator.comparing(DashboardMemberView::noStreamTime));

    /** Ключ колонки в параметре {@code sort} и в ссылках заголовков. */
    private final String key;

    /** Заголовок колонки. */
    private final String title;

    /** Поле сущности {@link GuildMember} для сортировки в БД либо {@code null} для вычисляемых колонок. */
    private final String entityProperty;

    /** Направление, с которого начинается сортировка при первом клике по заголовку. */
    private final Sort.Direction preferredDirection;

    /** Числовая ли колонка: такие выравниваются по правому краю и в HTML, и в текстовом отчете. */
    private final boolean numeric;

    private final Comparator<DashboardMemberView> ascending;

    DashboardSort(String key, String title, String entityProperty, Sort.Direction preferredDirection,
                  boolean numeric, Comparator<DashboardMemberView> ascending) {
        this.key = key;
        this.title = title;
        this.entityProperty = entityProperty;
        this.preferredDirection = preferredDirection;
        this.numeric = numeric;
        this.ascending = ascending;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getEntityProperty() {
        return entityProperty;
    }

    public Sort.Direction getPreferredDirection() {
        return preferredDirection;
    }

    public boolean isNumeric() {
        return numeric;
    }

    /** Умеет ли БД отсортировать эту колонку сама. */
    public boolean isEntityProperty() {
        return entityProperty != null;
    }

    /**
     * Компаратор строк таблицы по этой колонке. Имя участника добавляется вторым ключом,
     * чтобы участники с одинаковым значением всегда шли в одном и том же порядке
     * и не «прыгали» между страницами.
     */
    public Comparator<DashboardMemberView> comparator(Sort.Direction direction) {
        Comparator<DashboardMemberView> byColumn = direction == Sort.Direction.ASC ? ascending : ascending.reversed();
        return byColumn.thenComparing(row -> row.member().getUserName(),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    /** Ищет колонку по ключу из параметра {@code sort}. */
    public static Optional<DashboardSort> byKey(String key) {
        return Arrays.stream(values()).filter(column -> column.key.equalsIgnoreCase(key)).findFirst();
    }
}
