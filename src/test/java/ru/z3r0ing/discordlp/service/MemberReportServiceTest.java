package ru.z3r0ing.discordlp.service;

import net.dv8tion.jda.api.entities.Message;
import org.junit.jupiter.api.Test;
import ru.z3r0ing.discordlp.entity.GuildMember;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberReportServiceTest {

    private final MemberReportService service = new MemberReportService();

    @Test
    void emptyTableIsReplacedByAPlainMessage() {
        List<String> messages = service.render("Заголовок", List.of(),
                MemberReportService.TABLE_COLUMNS, "Никого нет.");

        assertThat(messages).containsExactly("Заголовок\nНикого нет.");
    }

    @Test
    void tableKeepsTheOrderOfRowsAndNumbersThem() {
        List<String> messages = service.render("Заголовок",
                List.of(row("Первый", 300L, 35, 60), row("Второй", 100L, 0, 5)),
                MemberReportService.TABLE_COLUMNS, "Никого нет.");

        assertThat(messages).hasSize(1);
        List<String> lines = messages.getFirst().lines().toList();
        assertThat(lines.getFirst()).isEqualTo("Заголовок");
        assertThat(lines.get(1)).isEqualTo("```");
        assertThat(lines.get(2)).contains("#", "Имя пользователя", "Баланс LP", "Время в конфе", "Со стримом", "Без стрима");
        assertThat(lines.get(4)).contains("1", "Первый", "300", "1 ч 35 мин", "35 мин", "1 ч 0 мин");
        assertThat(lines.get(5)).contains("2", "Второй", "100", "5 мин");
        assertThat(lines.getLast()).isEqualTo("```");
    }

    @Test
    void columnsAreAlignedToTheWidestCell() {
        List<String> lines = service.render("Заголовок",
                        List.of(row("Коротко", 1L, 0, 5), row("Очень длинное имя", 1_000_000L, 0, 5)),
                        MemberReportService.TABLE_COLUMNS, "Никого нет.")
                .getFirst().lines().toList();

        // Шапка, разделитель и обе строки данных в моноширинном блоке имеют одну ширину
        List<Integer> widths = new ArrayList<>();
        lines.subList(2, lines.size() - 1).forEach(line -> widths.add(line.length()));
        assertThat(widths).allMatch(width -> width.equals(widths.getFirst()));
    }

    @Test
    void activityReportLeavesOutTheBalanceColumn() {
        String message = service.render("Заголовок", List.of(row("Первый", 300L, 35, 60)),
                MemberReportService.ACTIVITY_COLUMNS, "Никого нет.").getFirst();

        assertThat(message).contains("Время в конфе", "Со стримом", "Без стрима");
        assertThat(message).doesNotContain("Баланс LP");
    }

    @Test
    void longNamesAreTruncatedSoLinesDoNotRunAway() {
        String message = service.render("Заголовок",
                List.of(row("ОченьДлинноеИмяУчастникаКотороеНеПоместится", 1L, 0, 5)),
                MemberReportService.TABLE_COLUMNS, "Никого нет.").getFirst();

        assertThat(message).contains("ОченьДлинноеИмяУч…");
        assertThat(message).doesNotContain("ОченьДлинноеИмяУчастникаКотороеНеПоместится");
    }

    @Test
    void aLongTableIsSplitIntoSeveralMessagesWithARepeatedHeader() {
        List<DashboardMemberView> rows = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            rows.add(row("Участник" + i, 1_000_000L + i, 100, 200));
        }

        List<String> messages = service.render("Заголовок", rows,
                MemberReportService.TABLE_COLUMNS, "Никого нет.");

        assertThat(messages).hasSizeGreaterThan(1);
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.length()).isLessThanOrEqualTo(Message.MAX_CONTENT_LENGTH);
            assertThat(message).contains("Имя пользователя");
            assertThat(message).endsWith("```");
        });
        // Ни одна строка не потерялась при нарезке
        long dataLines = messages.stream()
                .flatMap(String::lines)
                .filter(line -> line.contains("Участник"))
                .count();
        assertThat(dataLines).isEqualTo(rows.size());
    }

    private static DashboardMemberView row(String name, long balance, int streamMinutes, int noStreamMinutes) {
        GuildMember member = new GuildMember();
        member.setId(1L);
        member.setGuildId("guild-1");
        member.setUserId("user-1");
        member.setUserName(name);
        member.setGuildName("Guild");
        member.setBalance(balance);
        return DashboardMemberView.of(member, new VoiceTimeBreakdown(
                Duration.ofMinutes(streamMinutes), Duration.ofMinutes(noStreamMinutes)));
    }
}
