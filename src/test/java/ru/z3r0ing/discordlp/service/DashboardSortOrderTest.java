package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSortOrderTest {

    @Test
    void parsesColumnAndDirection() {
        DashboardSortOrder order = DashboardSortOrder.parse("streamTime,asc");

        assertThat(order.column()).isEqualTo(DashboardSort.STREAM_TIME);
        assertThat(order.direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(order.toParam()).isEqualTo("streamTime,asc");
    }

    @Test
    void everythingUnparseableFallsBackToTheDefault() {
        assertThat(DashboardSortOrder.parse(null)).isEqualTo(DashboardSortOrder.DEFAULT);
        assertThat(DashboardSortOrder.parse("")).isEqualTo(DashboardSortOrder.DEFAULT);
        assertThat(DashboardSortOrder.parse("balance")).isEqualTo(DashboardSortOrder.DEFAULT);
        assertThat(DashboardSortOrder.parse("balance,desc,extra")).isEqualTo(DashboardSortOrder.DEFAULT);
        // Поле сущности, не открытое как колонка таблицы, в сортировку не попадает
        assertThat(DashboardSortOrder.parse("lastVoiceCheckAt,asc")).isEqualTo(DashboardSortOrder.DEFAULT);
    }

    @Test
    void anyDirectionOtherThanAscIsDescending() {
        assertThat(DashboardSortOrder.parse("balance,whatever").direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(DashboardSortOrder.parse("balance,ASC").direction()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void onlyEntityColumnsAreSortedByTheDatabase() {
        assertThat(DashboardSort.BALANCE.isEntityProperty()).isTrue();
        assertThat(DashboardSortOrder.parse("balance,asc").toSpringSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "balance"));

        assertThat(DashboardSort.TOTAL_TIME.isEntityProperty()).isFalse();
        assertThat(DashboardSort.STREAM_TIME.isEntityProperty()).isFalse();
        assertThat(DashboardSort.NO_STREAM_TIME.isEntityProperty()).isFalse();
    }

    @Test
    void clickOnTheActiveColumnFlipsTheDirection() {
        DashboardSortOrder order = DashboardSortOrder.parse("balance,desc");

        assertThat(order.toggleParam(DashboardSort.BALANCE)).isEqualTo("balance,asc");
        assertThat(DashboardSortOrder.parse("balance,asc").toggleParam(DashboardSort.BALANCE))
                .isEqualTo("balance,desc");
    }

    @Test
    void clickOnAnotherColumnStartsWithItsPreferredDirection() {
        DashboardSortOrder order = DashboardSortOrder.parse("balance,asc");

        assertThat(order.toggleParam(DashboardSort.STREAM_TIME)).isEqualTo("streamTime,desc");
        assertThat(order.toggleParam(DashboardSort.USER_NAME)).isEqualTo("userName,asc");
    }

    @Test
    void arrowIsShownOnlyForTheActiveColumn() {
        DashboardSortOrder order = DashboardSortOrder.parse("voiceTime,desc");

        assertThat(order.indicator(DashboardSort.TOTAL_TIME)).isEqualTo(" ▼");
        assertThat(DashboardSortOrder.parse("voiceTime,asc").indicator(DashboardSort.TOTAL_TIME)).isEqualTo(" ▲");
        assertThat(order.indicator(DashboardSort.BALANCE)).isEmpty();
    }

    @Test
    void numericColumnsAreMarkedForRightAlignment() {
        assertThat(DashboardSort.BALANCE.isNumeric()).isTrue();
        assertThat(DashboardSort.TOTAL_TIME.isNumeric()).isTrue();
        assertThat(DashboardSort.USER_NAME.isNumeric()).isFalse();
        assertThat(DashboardSort.GUILD_NAME.isNumeric()).isFalse();
    }
}
