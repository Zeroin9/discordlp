package ru.z3r0ing.discordlp.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordBotConfig {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotConfig.class);

    private final String token;

    public DiscordBotConfig(@Value("${discord.bot.token}") String token) {
        this.token = token;
    }

    @Bean
    public JDA jda() {
        try {
            log.info("Initializing JDA...");
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_VOICE_STATES,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .build();
            log.info("JDA initialized successfully.");
            return jda;
        } catch (Exception e) {
            log.error("Failed to initialize JDA", e);
            throw new RuntimeException("Failed to initialize JDA", e);
        }
    }
}