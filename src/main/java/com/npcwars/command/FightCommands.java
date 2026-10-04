package com.npcwars.command;

import com.npcwars.combat.FightManager;
import com.npcwars.config.Messages;
import com.npcwars.util.Completions;
import com.npcwars.util.TimeParser;
import java.util.List;

/** {@code /npc fight}, {@code /npc timefight <time>} and {@code /npc stopfight}. */
final class FightCommands {

    private static final long MAX_DELAY_SECONDS = 30L * 86_400L;

    private FightCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(fight()).register(timeFight()).register(stopFight());
    }

    private static SubCommand fight() {
        return SubCommand.of("fight", "npcplugin.fight", "fight", "Start the fight right now", ctx -> {
            FightManager.StartResult result = ctx.plugin().fights().startNow();
            report(ctx, result, 0);
        });
    }

    private static SubCommand timeFight() {
        return SubCommand.of("timefight", "npcplugin.fight", "timefight <time>",
                "Start the fight after a delay such as 30s, 5m or 1h", ctx -> {
                    long seconds;
                    try {
                        seconds = TimeParser.parseSeconds(ctx.arg(0));
                    } catch (IllegalArgumentException ex) {
                        throw new CommandException("fight.invalid-time", Messages.var("input", ctx.arg(0)));
                    }
                    if (seconds <= 0 || seconds > MAX_DELAY_SECONDS) {
                        throw new CommandException("fight.time-range", Messages.var("input", ctx.arg(0)));
                    }
                    report(ctx, ctx.plugin().fights().schedule(seconds), seconds);
                }).minArgs(1).complete(ctx -> ctx.size() == 1
                ? Completions.filter(List.of("10s", "30s", "1m", "5m", "10m", "1h"), ctx.last()) : List.of());
    }

    private static SubCommand stopFight() {
        return SubCommand.of("stopfight", "npcplugin.fight", "stopfight", "End the running fight or cancel a scheduled one", ctx -> {
            if (!ctx.plugin().fights().stop()) {
                throw new CommandException("fight.nothing-to-stop");
            }
        }).aliases("endfight", "cancelfight");
    }

    private static void report(CommandContext ctx, FightManager.StartResult result, long seconds) throws CommandException {
        switch (result) {
            case STARTED -> ctx.send("fight.start-ok");
            case SCHEDULED -> ctx.send("fight.schedule-ok", Messages.var("time", TimeParser.format(seconds)));
            case ALREADY_RUNNING -> throw new CommandException("fight.already-running");
            case NO_NPCS -> throw new CommandException("fight.no-npcs");
            case NOT_ENOUGH_SIDES -> throw new CommandException("fight.not-enough-sides");
        }
    }
}
