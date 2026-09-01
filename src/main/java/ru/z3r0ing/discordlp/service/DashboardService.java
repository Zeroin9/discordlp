package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;
import ru.z3r0ing.discordlp.repository.PointsTransactionRepository;
import ru.z3r0ing.discordlp.repository.VoicePointsSum;

import java.time.Duration;
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
     * голосовым баллам (см. {@link VoiceTime}).
     *
     * @param page Номер страницы (начиная с 0)
     * @param size Размер страницы
     * @param sort Параметр сортировки (например, "balance,desc" или "userName,asc")
     * @return Страница со строками дашборда
     */
    public Page<DashboardMemberView> getGuildMembersPage(int page, int size, String sort) {
        // Создаем объект Sort. По умолчанию сортировка по balance, desc.
        Sort sortOrder = Sort.by("balance").descending();
        if (sort != null && !sort.isEmpty()) {
            try {
                String[] sortParams = sort.split(",");
                if (sortParams.length == 2) {
                    String sortBy = sortParams[0];
                    String sortDirection = sortParams[1];
                    sortOrder = sortDirection.equalsIgnoreCase("asc") ? 
                        Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
                }
            } catch (Exception e) {
                log.warn("Ошибка при парсинге параметра сортировки: {}. Используется сортировка по умолчанию.", sort, e);
            }
        }

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Page<GuildMember> members = guildMemberRepository.findAll(pageable);

        Map<Long, Duration> voiceTimes = voiceTimeByMember(members.getContent());
        return members.map(member ->
                DashboardMemberView.of(member, voiceTimes.getOrDefault(member.getId(), Duration.ZERO)));
    }

    /**
     * Считает время в голосовых каналах для участников страницы одним запросом:
     * суммы начислений в разрезе причины переводятся в длительность.
     */
    private Map<Long, Duration> voiceTimeByMember(List<GuildMember> members) {
        List<Long> memberIds = members.stream().map(GuildMember::getId).filter(Objects::nonNull).toList();
        if (memberIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Duration> voiceTimes = new HashMap<>();
        for (VoicePointsSum sum : pointsTransactionRepository.sumPointsByMemberAndReason(memberIds, VoiceTime.VOICE_REASONS)) {
            Duration reasonTime = VoiceTime.of(sum.reason(), sum.points() == null ? 0L : sum.points());
            voiceTimes.merge(sum.memberId(), reasonTime, Duration::plus);
        }
        return voiceTimes;
    }
}
