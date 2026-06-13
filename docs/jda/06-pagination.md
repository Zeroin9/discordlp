# Пагинация и История (Pagination & History)

### Rate Limit Listener Example

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

An example of a `ListenerAdapter` that manages a queue of message deletion tasks. It adds deletion tasks for messages from a specific user and cancels them if the user is banned from the guild. This demonstrates managing asynchronous tasks in response to Discord events.

```java
public class RateLimitListener extends ListenerAdapter
{
    private final long guildId;
    private final long userId;
    private final Queue<RequestFuture<Void>> tasks = new LinkedList<>();

    public RateLimitListener(Guild guild, User user)
    {
        guildId = guild.getIdLong();
        userId = user.getIdLong();
        // only store IDs as JDA objects can be disposed by cache invalidation
        //when disposed the entity is not usable anymore, since we only need the id this is good enough
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event)
    {
        if (event.getAuthor().getIdLong() != userId)
            return; // ignore other users
        if (event.getGuild().getIdLong() != guildId)
            return; // ignore other guilds
        RequestFuture<Void> task = event.getMessage().delete().submit();
        tasks.add(task); // add task to cancel queue in case user gets banned
        task.thenRun(() -> tasks.remove(task)); // remove once completed
    }

    @Override
    public void onGuildBan(GuildBanEvent event)
    {
        if (event.getUser().getIdLong() != userId)
            return; // ignore other users
        if (event.getGuild().getIdLong() != guildId)
            return; // ignore other guilds

        // stop deleting messages for banned user
        RequestFuture<Void> current;
        while ((current = tasks.poll()) != null)
            current.cancel(true); 
        tasks.clear();

        // remove this as listener, our task has completed!
        event.getJDA().removeEventListener(this);
    }
}
```

--------------------------------

### JDA Javadoc Example Template

Source: https://github.com/discord-jda/jda/wiki/6)-JDA-Structure-Guide

This snippet demonstrates the standard Javadoc template for documenting JDA methods or classes. It includes sections for basic function, additional information, error responses, parameters, exceptions, return values, and other references.

```java
    /**
     * This description should inform the user about the basic function of the method (or class)
     * that is being documented.
     * <br>A line break should be placed at the beginning of the following line.
     *
     * <p>This description is optional and should contain additional / notable information about
     * this method (or class)
     *
     * <p>All additional description paragraphs should start with the paragraph tag
     * at the beginning of the new paragraph and should be separated from the previous
     * paragraph by (at least) one line.
     *
     * <p>The last paragraph should point out what the possible {@link net.dv8tion.jda.core.requests.ErrorResponse ErrorResponses}
     * are. These can occur in RestAction failures.
     * <br>For that the following format should be used:
     * <ul>
     *     <li>{@link net.dv8tion.jda.core.requests.ErrorResponse#UNKNOWN_MESSAGE UNKNOWN_MESSAGE}
     *     <br>The Message did not exist (possibly deleted)</li>
     *
     *     <li>{@link net.dv8tion.jda.core.requests.ErrorResponse#INVALID_PIN INVALID_PIN}
     *     <br>The message specified can not be pinned (possibly a system message)</li>
     *
     *     <li>{@link net.dv8tion.jda.core.requests.ErrorResponse#MISSING_PERMISSIONS MISSING_PERMISSIONS}
     *     <br>This can be caused if we do not hold one of the following Permissions:
     *         <ul>
     *             <li>{@link net.dv8tion.jda.core.Permission#MESSAGE_WRITE MESSAGE_WRITE}
     *             <br>We are unable to send a message to this channel</li>
     *
     *             <li>{@link net.dv8tion.jda.core.Permission#MESSAGE_READ MESSAGE_READ}
     *             <br>We are unable to read messages in this channel</li>
     *         </ul></li>
     * </ul>
     *
     * @param  var0
     *         The Description should be at the same level as the parameter name
     * @param  var1
     *         Multiple parameters are to be documented in one "block"
     *
     * @throws javax.security.auth.login.LoginException
     *         The same goes for descriptions of throwables
     * @throws net.dv8tion.jda.core.exceptions.RateLimitedException
     *         Multiple throwables are to be documented in one "block"
     *
     * @return {@link net.dv8tion.jda.core.requests.RestAction RestAction} - Type: {@link net.dv8tion.jda.core.entities.Role Role}
     *         <br>The response type of the RestAction can be described further here.
     *
     * @see    Void
     * @see    net.dv8tion.jda.core.JDA
     *
     * @since  3.0
     *
     * @serialData
     *         If a tag is not specified here it should be at the bottom of the documentation.
     *         <br>If the tag name is too long to follow the proper indentation formatting
     *         it should start the block in the next line with the correct indentation.
     */
```

--------------------------------

### Handling RestActions with queue()

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Demonstrates how to handle RestActions returned by methods like sendMessage() or delete(). It's recommended to use the async `queue()` method.

```java
channel.sendMessage("hello");
// This returns a RestAction, you need to call queue(), submit(), or complete()
```

```java
message.delete();
// This returns a RestAction, you need to call queue(), submit(), or complete()
```

--------------------------------

### Chaining RestAction Operations with queue()

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Demonstrates chaining multiple asynchronous operations using `queue()` for sequential execution and handling callbacks for each step. This approach is suitable when you need to perform a series of actions and react to their completion or failure.

```java
public void setTestingChannel(TextChannel channel)
{
    channel.getManager().setName("testing-channel").queue( (v) ->
        channel.sendMessage("Update Channel").queue( (m) ->
            m.delete().queueAfter(30, TimeUnit.SECONDS, (t) ->
                logChannel.sendMessage("Deleted Response in %s", channel).queue()
            )
        )
    );
}
```

--------------------------------

### Add Reactions to a Message

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Demonstrates how to add reactions to a message using custom emotes, Unicode escape sequences, or codepoint notation.

```java
message.addReaction("minn:245267426227388416").queue();
// unicode escape
message.addReaction("\uD83D\uDE02").queue();
// codepoint notation
message.addReaction("U+1F602").queue();
```

--------------------------------

### Iterate Messages with Blocking Loop (Not Recommended)

Source: https://github.com/discord-jda/jda/wiki/7)-Paginating-Entities

Demonstrates blocking iteration through message history using the Iterable interface. This method is not recommended due to its blocking nature and potential performance impact.

```java
// Blocking iteration using the Iterable interface (not recommended)
public static List<Message> forEachMessage(int limit, MessageChannel channel, Consumer<Message> action)
{
    MessagePaginationAction paginator = channel.getIterableHistory();
    for (Message message : paginator)
    {
        action.apply(message);
        if (--limit <= 0) break;
    }
    return paginator.getCached();
}
```

--------------------------------

### AuditableRestAction with Reason

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Demonstrates how to use AuditableRestAction to set a reason for Discord API operations like deleting messages or banning users. This is useful for moderation logs.

```java
public class ModerationUtil
{
    public static void deleteMessage(Message message, String reason)
    {
        message.delete().reason(reason).queue();
    }

    public static void ban(Guild guild, User user, String reason)
    {
        guild.ban(user, 7, reason).queue();
    }
}
```

--------------------------------

### Retrieve and Process Messages Asynchronously

Source: https://github.com/discord-jda/jda/wiki/7)-Paginating-Entities

Retrieves up to 1000 messages from a MessageChannel and processes them using a callback. It's recommended to use async methods to avoid blocking operations.

```java
/**
 * Retrieves up to 1000 messages from the provided MessageChannel
 * and then provides them to the callback.
 */
public void get1000(MessageChannel channel, Consumer<List<Message>> callback)
{
    List<Message> messages = new ArrayList<>(1000);
    channel.getIterableHistory().cache(false).forEachAsync((message) ->
    {
        messages.add(message);
        return messages.size() < 1000;
    }).thenRun(() -> callback.accept(messages));
}

get1000(channel, (messages) -> channel.purgeMessages(messages));
```

--------------------------------

### Chaining RestAction Operations with submit() and CompletableFuture

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Shows how to chain asynchronous operations using `submit()` which returns a `CompletableFuture`. This allows for more complex asynchronous workflows and error handling using `thenCompose` and `whenComplete`.

```java
public void setTestingChannel(TextChannel channel)
{
    channel.getManager().setName("testing-channel").submit()                   // CompletableFuture<Void>
           .thenCompose((v) -> channel.sendMessage("Update Channel").submit()) // CompletableFuture<Message>
           .thenCompose((m) -> m.delete().submitAfter(30, TimeUnit.SECONDS))   // CompletableFuture<Void>
           .thenCompose((v) -> logChannel.sendMessage("Deleted Response in %s", channel).submit())
           .whenComplete((s, error) -> {
               // this will be called for every termination (success/failure)
               // if the result is successful the error will be null
               // otherwise you should handle the error here to prevent it from being eaten and never printed
               if (error != null) error.printStackTrace();
           });
}
```

--------------------------------

### Check User Reaction Asynchronously

Source: https://github.com/discord-jda/jda/wiki/7)-Paginating-Entities

Checks if a specific user has reacted to a message and executes a callback if they have. This method uses async iteration to avoid blocking.

```java
// Calls the callback if the user has reacted
public static void ifReacted(MessageReaction reaction, User user, Runnable callback)
{
    reaction.getUsers().cache(false).forEachAsync(u -> {
        if (u.equals(user)) {
            callback.run(); // user has reacted -> call the callback
            return false; // end iteration
        }
        return true; // continue iteration
    });
}
```

--------------------------------

### Set Testing Channel Blocking

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Sets the name of a text channel and sends a message, with the message being deleted after a delay. This version uses blocking operations.

```java
public void setTestingChannelBlocking(TextChannel channel)
{
    channel.getManager().setName("testing-channel").complete();
    Message m = channel.sendMessage("Update Channel").complete();
    m.delete().completeAfter(30, TimeUnit.SECONDS);
    logChannel.sendMessage("Deleted Response in %s", channel).queue();
    // note how we used queue in the end because we don't need it sequenced anymore.
}
```

--------------------------------

### Delete Messages Up To a Specific Time

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Deletes messages from a channel until a specified time is reached. Requires MessageChannel and an OffsetDateTime.

```java
// Delete messages up to the specified time
void deleteUntil(MessageChannel channel, OffsetDateTime time) {
  channel.getIterableHistory()
    .takeUntilAsync(message -> message.getTimeCreated().isBefore(time)) // Collect messages until they pass the time condition
    .thenAccept(channel::purgeMessages); // bulk deletes the messages from the channel (if possible)
}
```

--------------------------------

### Handle RestAction Failure Callback

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Adds a failure callback to a RestAction's queue method to handle exceptions during execution. This is useful for logging or reacting to errors when an operation like deleting a message fails.

```java
public void deleteMessage(Message message) {
    message.delete().queue(null, (exception) -> {
        message.getChannel().sendMessage("There was an error " + exception).queue();
    });
}
```

--------------------------------

### Delete Messages from a Specific Author

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Deletes a specified number of messages from a particular author in a channel. Requires MessageChannel, User, and an integer amount. Consider adding a time condition for efficiency.

```java
// Delete a number of messages for a specific author (this can be abstracted to any condition)
void deleteFromUser(MessageChannel channel, User author, int amount) {
  List<Message> messages = new ArrayList<>(); // First create a list for your messages
  channel.getIterableHistory()
    .forEachAsync(m -> { // Loop over the history and filter messages
      if (m.getAuthor().equals(author)) messages.add(m); // Add these messages to a list (your collector)
      return messages.size() < amount; // keep going until limit is reached (might be smart to also have a time condition here)
    }) // This is also a CompletableFuture<Void> so you can chain a callback
    .thenRun(() -> channel.purgeMessages(messages)); // Run after loop is over, delete the messages in your list
}
```

--------------------------------

### Delete a Number of Messages

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Deletes a specified number of messages from a channel asynchronously. Requires MessageChannel and an integer amount.

```java
// Delete a number of messages
void deleteMessages(MessageChannel channel, int amount) {
  channel.getIterableHistory()
    .takeAsync(amount) // CompletableFuture<List<Message>>
    .thenAccept(channel::purgeMessages); // bulk deletes the messages from the channel (if possible)
}
```