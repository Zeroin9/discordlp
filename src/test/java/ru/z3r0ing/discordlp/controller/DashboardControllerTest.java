package ru.z3r0ing.discordlp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.service.DashboardMemberView;
import ru.z3r0ing.discordlp.service.DashboardService;

import java.time.Duration;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void rendersDashboardWithDefaultParameters() throws Exception {
        when(dashboardService.getGuildMembersPage(anyInt(), anyInt(), anyString())).thenReturn(membersPage());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("pageSize", 50))
                .andExpect(model().attribute("sort", "balance,desc"))
                .andExpect(model().attributeExists("guildMembers", "totalPages"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tester")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Время в конфе")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 ч 35 мин")));

        verify(dashboardService).getGuildMembersPage(0, 50, "balance,desc");
    }

    @Test
    void passesExplicitParametersToService() throws Exception {
        when(dashboardService.getGuildMembersPage(anyInt(), anyInt(), anyString())).thenReturn(membersPage());

        mockMvc.perform(get("/dashboard").param("page", "2").param("size", "10").param("sort", "userName,asc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("pageSize", 10))
                .andExpect(model().attribute("sort", "userName,asc"));

        verify(dashboardService).getGuildMembersPage(2, 10, "userName,asc");
    }

    @Test
    void rendersPlaceholderWhenThereAreNoMembers() throws Exception {
        when(dashboardService.getGuildMembersPage(anyInt(), anyInt(), anyString()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Нет данных для отображения")));
    }

    private static Page<DashboardMemberView> membersPage() {
        GuildMember member = new GuildMember();
        member.setId(1L);
        member.setGuildId("guild-1");
        member.setUserId("user-1");
        member.setUserName("Tester");
        member.setGuildName("Guild");
        member.setBalance(1_000L);
        DashboardMemberView row = DashboardMemberView.of(member, Duration.ofMinutes(95));
        return new PageImpl<>(List.of(row), PageRequest.of(0, 50), 1);
    }
}
