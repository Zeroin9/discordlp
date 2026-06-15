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

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final GuildMemberRepository guildMemberRepository;

    /**
     * Получает страницу участников гильдии с пагинацией и сортировкой.
     *
     * @param page Номер страницы (начиная с 0)
     * @param size Размер страницы
     * @param sort Параметр сортировки (например, "balance,desc" или "userName,asc")
     * @return Страница с участниками гильдии
     */
    public Page<GuildMember> getGuildMembersPage(int page, int size, String sort) {
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
        return guildMemberRepository.findAll(pageable);
    }
}