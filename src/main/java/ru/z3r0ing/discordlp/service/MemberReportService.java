package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.entities.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка текстового отчета по участникам для отправки прямо в канал Discord.
 *
 * <p>Таблица рисуется моноширинным блоком кода, колонки выравниваются по самой длинной
 * ячейке. Discord не принимает сообщения длиннее {@link Message#MAX_CONTENT_LENGTH},
 * поэтому длинная таблица режется на несколько сообщений, и в каждом повторяется шапка.
 */
@Service
public class MemberReportService {

    /** Колонки полного отчета по таблице участников. */
    public static final List<DashboardSort> TABLE_COLUMNS = List.of(
            DashboardSort.USER_NAME,
            DashboardSort.BALANCE,
            DashboardSort.TOTAL_TIME,
            DashboardSort.STREAM_TIME,
            DashboardSort.NO_STREAM_TIME);

    /** Колонки сводки за период: баланс в ней не нужен, речь только о времени. */
    public static final List<DashboardSort> ACTIVITY_COLUMNS = List.of(
            DashboardSort.USER_NAME,
            DashboardSort.TOTAL_TIME,
            DashboardSort.STREAM_TIME,
            DashboardSort.NO_STREAM_TIME);

    /** Ограничение на длину имени в таблице, чтобы строка не расползалась по ширине. */
    private static final int MAX_NAME_WIDTH = 18;

    private static final String FENCE = "```";
    private static final String NUMBER_HEADER = "#";

    /**
     * Рисует таблицу и режет ее на сообщения, готовые к отправке.
     *
     * @param title     заголовок перед таблицей; уходит только в первое сообщение
     * @param rows      строки таблицы в том порядке, в котором их нужно вывести
     * @param columns   колонки отчета
     * @param emptyText что написать вместо таблицы, если строк нет
     * @return список сообщений, каждое не длиннее лимита Discord
     */
    public List<String> render(String title, List<DashboardMemberView> rows,
                               List<DashboardSort> columns, String emptyText) {
        if (rows.isEmpty()) {
            return List.of(title + "\n" + emptyText);
        }

        List<List<String>> cells = cells(rows, columns);
        int[] widths = widths(cells, columns);

        String header = line(headerCells(columns), widths, columns);
        String separator = separator(widths);
        List<String> rowLines = cells.stream().map(row -> line(row, widths, columns)).toList();

        return split(title, header, separator, rowLines);
    }

    /** Значения всех ячеек, включая порядковый номер строки. */
    private static List<List<String>> cells(List<DashboardMemberView> rows, List<DashboardSort> columns) {
        List<List<String>> cells = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = new ArrayList<>(columns.size() + 1);
            row.add(String.valueOf(i + 1));
            for (DashboardSort column : columns) {
                String value = rows.get(i).text(column);
                row.add(column == DashboardSort.USER_NAME ? truncate(value) : value);
            }
            cells.add(row);
        }
        return cells;
    }

    private static List<String> headerCells(List<DashboardSort> columns) {
        List<String> header = new ArrayList<>(columns.size() + 1);
        header.add(NUMBER_HEADER);
        columns.forEach(column -> header.add(column.getTitle()));
        return header;
    }

    /** Ширина каждой колонки — по самой длинной ячейке, включая заголовок. */
    private static int[] widths(List<List<String>> cells, List<DashboardSort> columns) {
        List<String> header = headerCells(columns);
        int[] widths = new int[header.size()];
        for (int i = 0; i < header.size(); i++) {
            widths[i] = header.get(i).length();
        }
        for (List<String> row : cells) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        return widths;
    }

    /** Порядковый номер и числовые колонки выравниваются по правому краю, остальные — по левому. */
    private static String line(List<String> values, int[] widths, List<DashboardSort> columns) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append("  ");
            }
            boolean rightAligned = i == 0 || columns.get(i - 1).isNumeric();
            line.append(pad(values.get(i), widths[i], rightAligned));
        }
        return line.toString().stripTrailing();
    }

    private static String separator(int[] widths) {
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                separator.append("  ");
            }
            separator.append("-".repeat(widths[i]));
        }
        return separator.toString();
    }

    private static String pad(String value, int width, boolean rightAligned) {
        String padding = " ".repeat(Math.max(0, width - value.length()));
        return rightAligned ? padding + value : value + padding;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_NAME_WIDTH ? value : value.substring(0, MAX_NAME_WIDTH - 1) + "…";
    }

    /**
     * Режет строки таблицы на сообщения, укладывающиеся в лимит Discord.
     * Шапка повторяется в каждом сообщении, чтобы продолжение таблицы читалось само по себе.
     */
    private static List<String> split(String title, String header, String separator, List<String> rowLines) {
        String tableHead = FENCE + "\n" + header + "\n" + separator + "\n";
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder(title + "\n" + tableHead);

        for (String row : rowLines) {
            // Место под саму строку и под закрывающие кавычки блока кода
            if (current.length() + row.length() + 1 + FENCE.length() > Message.MAX_CONTENT_LENGTH) {
                messages.add(current.append(FENCE).toString());
                current = new StringBuilder(tableHead);
            }
            current.append(row).append('\n');
        }

        messages.add(current.append(FENCE).toString());
        return messages;
    }
}
