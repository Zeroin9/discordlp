package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.repository.VoicePointsSum;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final GuildMemberRepository guildMemberRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    /**
     * Получает страницу участников гильдии с пагинацией и сортировкой.
     * К каждому участнику добавляется время в конференции, рассчитанное по начисленным
     * голосовым баллам и разложенное на время со стримом и без него (см. {@link VoiceTime}).
     *
     * <p>Сортировка по полям участника выполняется в БД вместе с пагинацией. Колонки времени
     * в БД не хранятся, поэтому при сортировке по ним участники выбираются целиком, время
     * считается в приложении, и только потом отрезается нужная страница.
     *
     * @param page Номер страницы (начиная с 0)
     * @param size Размер страницы
     * @param sort Параметр сортировки (например, "balance,desc" или "streamTime,asc")
     * @return Страница со строками дашборда
     */
    public Page<DashboardMemberView> getGuildMembersPage(int page, int size, String sort) {
        DashboardSortOrder order = DashboardSortOrder.parse(sort);

        if (order.column().isEntityProperty()) {
            Pageable pageable = PageRequest.of(page, size, order.toSpringSort());
            Page<GuildMember> members = guildMemberRepository.findAll(pageable);
            Map<Long, VoiceTimeBreakdown> voiceTimes = voiceTimeByMember(members.getContent(), null);
            return members.map(member -> toView(member, voiceTimes));
        }

        List<DashboardMemberView> rows = sortedRows(guildMemberRepository.findAll(), null, order);
        return pageOf(rows, page, size);
    }

    /**
     * Все участники одной гильдии со временем в конференции — для отчетов в Discord.
     *
     * @param guildId идентификатор гильдии Discord
     * @param since   начало периода, за который считается время; {@code null} — за все время
     * @param sort    параметр сортировки в том же виде, что и у дашборда
     * @return отсортированный список строк таблицы
     */
    public List<DashboardMemberView> getGuildMembers(String guildId, Instant since, String sort) {
        List<GuildMember> members = guildMemberRepository.findByGuildId(guildId);
        return sortedRows(members, since, DashboardSortOrder.parse(sort));
    }

    private List<DashboardMemberView> sortedRows(List<GuildMember> members, Instant since, DashboardSortOrder order) {
        Map<Long, VoiceTimeBreakdown> voiceTimes = voiceTimeByMember(members, since);
        List<DashboardMemberView> rows = new ArrayList<>(members.size());
        for (GuildMember member : members) {
            rows.add(toView(member, voiceTimes));
        }
        rows.sort(order.comparator());
        return rows;
    }

    private static DashboardMemberView toView(GuildMember member, Map<Long, VoiceTimeBreakdown> voiceTimes) {
        return DashboardMemberView.of(member, voiceTimes.getOrDefault(member.getId(), VoiceTimeBreakdown.ZERO));
    }

    /** Отрезает страницу от уже отсортированного списка строк. */
    private static Page<DashboardMemberView> pageOf(List<DashboardMemberView> rows, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        int from = Math.min((int) pageable.getOffset(), rows.size());
        int to = Math.min(from + size, rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }

    /**
     * Считает время в голосовых каналах для указанных участников одним запросом:
     * суммы начислений в разрезе причины переводятся в длительность и раскладываются
     * на время со стримом и без него.
     *
     * @param since начало периода либо {@code null}, если период не ограничен
     */
    private Map<Long, VoiceTimeBreakdown> voiceTimeByMember(List<GuildMember> members, Instant since) {
        List<Long> memberIds = members.stream().map(GuildMember::getId).filter(Objects::nonNull).toList();
        if (memberIds.isEmpty()) {
            return Map.of();
        }

        List<VoicePointsSum> sums = since == null
                ? pointsTransactionRepository.sumPointsByMemberAndReason(memberIds, VoiceTime.VOICE_REASONS)
                : pointsTransactionRepository.sumPointsByMemberAndReasonSince(memberIds, VoiceTime.VOICE_REASONS, since);

        Map<Long, VoiceTimeBreakdown> voiceTimes = new HashMap<>();
        for (VoicePointsSum sum : sums) {
            long points = sum.points() == null ? 0L : sum.points();
            voiceTimes.merge(sum.memberId(),
                    VoiceTimeBreakdown.ZERO.plus(sum.reason(), points),
                    VoiceTimeBreakdown::plus);
        }
        return voiceTimes;
    }
}
