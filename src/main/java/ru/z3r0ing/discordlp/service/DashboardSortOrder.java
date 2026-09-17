package ru.z3r0ing.discordlp.service;

import org.springframework.data.domain.Sort;

import java.util.Comparator;

/**
 * Разобранный параметр сортировки таблицы участников: колонка и направление.
 *
 * <p>Текстовый вид — {@code "<ключ колонки>,<asc|desc>"}, например {@code "streamTime,desc"}.
 * Все, что разобрать не удалось, откатывается к {@link #DEFAULT}, поэтому в SQL и в ссылки
 * попадают только известные колонки.
 *
 * @param column    колонка, по которой идет сортировка
 * @param direction направление сортировки
 */
public record DashboardSortOrder(DashboardSort column, Sort.Direction direction) {

    public static final DashboardSortOrder DEFAULT =
            new DashboardSortOrder(DashboardSort.BALANCE, Sort.Direction.DESC);

    /**
     * Разбирает параметр вида {@code "balance,desc"}.
     * Неизвестная колонка или неверный формат дают {@link #DEFAULT}; направление, отличное
     * от {@code asc}, считается убывающим.
     */
    public static DashboardSortOrder parse(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT;
        }

        String[] parts = sort.split(",");
        if (parts.length != 2) {
            return DEFAULT;
        }

        return DashboardSort.byKey(parts[0].trim())
                .map(column -> new DashboardSortOrder(column, direction(parts[1])))
                .orElse(DEFAULT);
    }

    private static Sort.Direction direction(String value) {
        return "asc".equalsIgnoreCase(value.trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    /** Текстовый вид параметра — то, что уходит в ссылки пагинации и заголовков. */
    public String toParam() {
        return column.getKey() + "," + (direction == Sort.Direction.ASC ? "asc" : "desc");
    }

    /** Сортировка для БД. Вызывается только для колонок, которые БД умеет сортировать сама. */
    public Sort toSpringSort() {
        return Sort.by(direction, column.getEntityProperty());
    }

    /** Компаратор строк таблицы по текущей колонке и направлению. */
    public Comparator<DashboardMemberView> comparator() {
        return column.comparator(direction);
    }

    /**
     * Параметр для ссылки в заголовке колонки: по текущей колонке направление переворачивается,
     * по остальным берется предпочтительное для этой колонки (у чисел и времени — по убыванию,
     * у имен — по возрастанию).
     */
    public String toggleParam(DashboardSort target) {
        Sort.Direction next = target == column
                ? (direction == Sort.Direction.ASC ? Sort.Direction.DESC : Sort.Direction.ASC)
                : target.getPreferredDirection();
        return new DashboardSortOrder(target, next).toParam();
    }

    /** Стрелка направления рядом с заголовком активной колонки; для остальных — пусто. */
    public String indicator(DashboardSort target) {
        if (target != column) {
            return "";
        }
        return direction == Sort.Direction.ASC ? " ▲" : " ▼";
    }
}
