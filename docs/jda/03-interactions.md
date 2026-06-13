# Взаимодействия (Interactions & Slash Commands)

### Complex RestAction with Operators

Source: https://github.com/discord-jda/jda/blob/master/README.md

Illustrates a complex RestAction chain involving delays and message editing before final deletion. This example uses specific time units and a scheduler.

```java
public RestAction<Void> selfDestruct(MessageChannel channel, String content) {
    return channel.sendMessage("The following message will destroy itself in 1 minute!")
        .addComponents(ActionRow.of(Button.danger("delete", "Delete now")))
        .delay(10, SECONDS, scheduler)
        .flatMap((it) -> it.editMessage(content))
        .delay(1, MINUTES, scheduler)
        .flatMap(Message::delete);
}
```

--------------------------------

### Create a Slash Command Bot

Source: https://github.com/discord-jda/jda/blob/master/README.md

Initializes a JDA bot using light caching and no intents, suitable for bots relying on interactions. It sets up a listener for slash commands and registers global commands.

```java
public static void main(String[] args) {
  JDA jda = JDABuilder.createLight(token, Collections.emptyList())
      .addEventListeners(new SlashCommandListener())
      .build();

  // Register your commands to make them visible globally on Discord:

  CommandListUpdateAction commands = jda.updateCommands();

  // Add all your commands on this action instance
  commands.addCommands(
    Commands.slash("say", "Makes the bot say what you tell it to")
      .addOption(STRING, "content", "What the bot should say", true), // Accepting a user input
    Commands.slash("leave", "Makes the bot leave the server")
      .setContexts(InteractionContextType.GUILD) // this doesn't make sense in DMs
      .setDefaultPermissions(DefaultMemberPermissions.DISABLED) // only admins should be able to use this command.
  );

  // Then finally send your commands to discord using the API
  commands.queue();
}
```

--------------------------------

### jda-ktx Command Handling with Suspending RestAction

Source: https://github.com/discord-jda/jda/blob/master/README.md

Demonstrates using jda-ktx extensions in Kotlin to handle a slash command, asynchronously wait for a RestAction to complete using await(), and then edit the interaction response.

```kotlin
fun main() {
    val jda = light(BOT_TOKEN)
    
    jda.onCommand("ping") { event ->
        val time = measureTime {
            event.reply("Pong!").await() // suspending
        }.inWholeMilliseconds

        event.hook.editOriginal("Pong: $time ms").queue()
    }
}
```

--------------------------------

### Reply to a Slash Command

Source: https://github.com/discord-jda/jda/wiki/Interactions

Use this snippet to immediately reply to a slash command with the provided content. Ensure the command name and options are correctly accessed.

```java
public class SayCommand extends ListenerAdapter {
  @Override
  public void onSlashCommand(SlashCommandEvent event) {
    if (event.getName().equals("say")) {
      event.reply(event.getOption("content").getAsString()).queue(); // reply immediately
    }
  }
}
```

--------------------------------

### Handling Button Clicks and Creating Buttons

Source: https://github.com/discord-jda/jda/wiki/Interactions

This Java code demonstrates how to create different types of buttons (primary, success, link) and handle their click events. It shows how to use component IDs to identify which button was pressed and how to respond with a new message or by editing the existing message. This snippet is part of a ListenerAdapter for JDA.

```java
public class HelloBot extends ListenerAdapter {
  @Override
  public void onSlashCommand(SlashCommandEvent event) {
      if (event.getName().equals("hello")) {
          event.reply("Click the button to say hello")
              .addActionRow(
                Button.primary("hello", "Click Me"), // Button with only a label
                Button.success("emoji", Emoji.fromMarkdown("<:minn:245267426227388416>"))) // Button with only an emoji
              .queue();
      } else if (event.getName().equals("info")) {
          event.reply("Click the buttons for more info")
              .addActionRow( // link buttons don't send events, they just open a link in the browser when clicked
                  Button.link("https://github.com/DV8FromTheWorld/JDA", "GitHub")
                    .withEmoji(Emoji.fromMarkdown("<:github:849286315580719104>")), // Link Button with label and emoji
                  Button.link("https://ci.dv8tion.net/job/JDA/javadoc/", "Javadocs")) // Link Button with only a label
              .queue();
      }
  }

  @Override
  public void onButtonClick(ButtonClickEvent event) {
      if (event.getComponentId().equals("hello")) {
          event.reply("Hello :)").queue(); // send a message in the channel
      } else if (event.getComponentId().equals("emoji")) {
          event.editMessage("That button didn't say click me").queue(); // update the message
      }
  }
}
```

--------------------------------

### Implement a Slash Command Interaction Listener

Source: https://github.com/discord-jda/jda/blob/master/README.md

An event listener that handles slash command interactions, responding to 'say' commands by echoing user input and 'leave' commands by replying and leaving the guild.

```java
public class SlashCommandListener extends ListenerAdapter {
  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    switch (event.getName()) {
      case "say" -> {
        String content = event.getOption("content", OptionMapping::getAsString);
        event.reply(content).queue();
      }
      case "leave" -> {
        event.reply("I'm leaving the server now!")
          .setEphemeral(true) // this message is only visible to the command user
          .flatMap(m -> event.getGuild().leave()) // append a follow-up action using flatMap
          .queue(); // enqueue both actions to run in sequence (send message -> leave guild)
      }
    }
  }
}
```

--------------------------------

### Defer and Reply to a Slash Command

Source: https://github.com/discord-jda/jda/wiki/Interactions

Use this snippet to defer a slash command response, sending a 'Thinking...' message, and then update it with the actual content later. This is useful for commands that take longer to process.

```java
public class TagCommand extends ListenerAdapter {
  @Override
  public void onSlashCommand(SlashCommandEvent event) {
    if (event.getName().equals("tag")) {
      event.deferReply().queue(); // Tell discord we received the command, send a thinking... message to the user
      String tagName = event.getOption("name").getAsString();
      TagDatabase.fingTag(tagName,
        (tag) -> event.getHook().sendMessage(tag).queue() // delayed response updates our inital "thinking..." message with the tag value
      );
    }
  }
}
```

--------------------------------

### Defer Discord Interaction Reply

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Acknowledges an interaction within the 3-second time limit by deferring the reply. This must be followed by a subsequent reply or edit using InteractionHook.

```java
event.deferReply().queue()
```

```java
event.deferEdit().queue()
```

```java
event.editMessage(...).queue()
```

```java
event.reply(...).queue()
```