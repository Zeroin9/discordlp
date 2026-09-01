package ru.z3r0ing.discordlp.entity;

public enum TransactionReason {
    VOICE_STANDARD,
    VOICE_STREAMER,
    VOICE_VIEWER,
    ADMIN_MANUAL,
    ADMIN_REMOVE,
    USER_KICK,
    USER_MUTE,
    /** Блокировка ставки в момент принятия участия в пари. */
    BET_HOLD,
    /** Выплата выигрыша по пари: доля призового фонда, пропорциональная ставке. */
    BET_WIN,
    /** Возврат ставки при отмене пари. */
    BET_REFUND
}
