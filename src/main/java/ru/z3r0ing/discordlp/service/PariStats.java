package ru.z3r0ing.discordlp.service;

/**
 * Агрегированная статистика по ставкам пари.
 */
public record PariStats(long yesCount, long yesPool, long noCount, long noPool) {

    public long totalCount() {
        return yesCount + noCount;
    }

    public long totalPool() {
        return yesPool + noPool;
    }
}
