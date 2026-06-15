package ru.z3r0ing.discordlp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.service.DashboardService;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Отображает дашборд с таблицей участников гильдии.
     *
     * @param page Номер страницы (по умолчанию 0)
     * @param size Размер страницы (по умолчанию 50)
     * @param sort Параметр сортировки (по умолчанию "balance,desc")
     * @param model Модель для передачи данных в шаблон
     * @return Имя шаблона dashboard.html
     */
    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "balance,desc") String sort,
            Model model) {

        Page<GuildMember> guildMemberPage = dashboardService.getGuildMembersPage(page, size, sort);

        model.addAttribute("guildMembers", guildMemberPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", guildMemberPage.getTotalPages());
        model.addAttribute("pageSize", size);
        model.addAttribute("sort", sort);

        return "dashboard";
    }
}