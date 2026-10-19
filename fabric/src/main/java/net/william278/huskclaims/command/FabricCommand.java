/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskclaims.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.user.CommandUser;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class FabricCommand {

    private final String name;
    private final FabricHuskClaims plugin;

    public FabricCommand(@NotNull String name, @NotNull FabricHuskClaims plugin) {
        this.name = name;
        this.plugin = plugin;
    }

    /**
     * Add this command, its namespaced form and its aliases to the dispatcher.
     *
     * @param dispatcher the server command dispatcher
     * @param template   the command as configured when the tree is built; used for the
     *                   literal names, usage text and default permissions only
     */
    public void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher, @NotNull Command template) {
        final Predicate<CommandSourceStack> visible = source -> plugin.isSessionActive()
                && plugin.findCommand(name).map(command -> canUse(source, command)).orElse(false);

        plugin.getPermissions().put(template.getPermission(), template.isOperatorCommand());
        final Map<String, Boolean> additional = template.getAdditionalPermissions();
        additional.forEach(plugin.getPermissions()::put);

        final LiteralArgumentBuilder<CommandSourceStack> builder = literal(template.getName())
                .requires(visible).executes(this::execute);
        if (!template.getRawUsage().isBlank()) {
            builder.then(argument(template.getRawUsage().replaceAll("[<>\\[\\]]", ""), greedyString())
                    .executes(this::execute)
                    .suggests(this::suggest));
        }
        final LiteralCommandNode<CommandSourceStack> node = dispatcher.register(builder);
        dispatcher.register(literal("huskclaims:" + template.getName())
                .requires(visible).executes(this::execute).redirect(node));
        template.getAliases().forEach(alias -> dispatcher.register(literal(alias)
                .requires(visible).executes(this::execute).redirect(node)));
    }

    private boolean canUse(@NotNull CommandSourceStack source, @NotNull Command command) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true; // console and command blocks
        }
        return Permissions.check(player, command.getPermission(), command.isOperatorCommand() ? 3 : 0);
    }

    private int execute(@NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        plugin.findCommand(name).ifPresent(command -> command.onExecuted(
                resolveExecutor(context.getSource()),
                command.removeFirstArg(context.getInput().split(" "))
        ));
        return 1;
    }

    private CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            @NotNull com.mojang.brigadier.suggestion.SuggestionsBuilder suggestions) {
        final Command command = plugin.findCommand(name).orElse(null);
        if (command == null || !(command instanceof TabCompletable provider)) {
            return com.mojang.brigadier.suggestion.Suggestions.empty();
        }
        final String[] args = command.removeFirstArg(context.getInput().split(" ", -1));
        provider.getSuggestions(resolveExecutor(context.getSource()), args).stream()
                    .map(suggestion -> {
                        final String completedArgs = String.join(" ", args);
                        int lastIndex = completedArgs.lastIndexOf(" ");
                        if (lastIndex == -1) {
                            return suggestion;
                        }
                        return completedArgs.substring(0, lastIndex + 1) + suggestion;
                    })
                    .forEach(suggestions::suggest);
            return suggestions.buildFuture();
    }

    @NotNull
    private CommandUser resolveExecutor(@NotNull CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return plugin.getOnlineUser(player);
        }
        return plugin.getConsole();
    }

}
