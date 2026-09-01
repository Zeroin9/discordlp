package ru.z3r0ing.discordlp;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.z3r0ing.discordlp.command.SlashCommandHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поднимает полный контекст приложения на настоящем PostgreSQL: проверяет, что миграции
 * применяются, Hibernate валидирует схему и все бины связываются между собой.
 * Подключение к Discord подменяется моком, чтобы тест не требовал реального токена.
 */
@SpringBootTest(properties = "discord.bot.token=test-token")
class DiscordlpApplicationTests extends PostgresContainerTest {

	// Глубокие заглушки нужны, чтобы регистрация slash-команд на ApplicationReadyEvent
	// прошла по цепочке updateCommands().addCommands(...).queue(...) без реального Discord.
	@MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
	private JDA jda;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private List<SlashCommandHandler> commandHandlers;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	void everyCommandHandlerIsRegistered() {
		assertThat(commandHandlers).extracting(SlashCommandHandler::getCommandName)
				.containsExactlyInAnyOrder("lp", "lpuser", "lpadd", "lpremove", "lpkick", "lpmute", "lp-pari");
	}
}
