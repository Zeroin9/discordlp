CREATE TABLE muted_members (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    muted_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_muted_members_guild_user UNIQUE (guild_id, user_id)
);
