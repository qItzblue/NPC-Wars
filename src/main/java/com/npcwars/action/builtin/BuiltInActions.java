package com.npcwars.action.builtin;

import com.npcwars.action.ActionRegistry;
import java.util.List;

/** Registers the actions that ship with the plugin. */
public final class BuiltInActions {

    private BuiltInActions() {
    }

    public static void registerAll(ActionRegistry registry) {
        registry.register(new SwingAction("attack", List.of("hit"), true,
                "Swing and hit the enemy in front (damage can be turned off in config.yml)"));
        registry.register(new SwingAction("swing", List.of(), false, "Play the arm-swing animation only"));
        registry.register(new MoveAction());
        registry.register(new DirectionalMoveAction("walk", List.of("forward"), DirectionalMoveAction.Mode.WALK,
                "Walk in a direction for some blocks or a duration"));
        registry.register(new DirectionalMoveAction("run", List.of("sprint"), DirectionalMoveAction.Mode.RUN,
                "Sprint in a direction for some blocks or a duration"));
        registry.register(new DirectionalMoveAction("swim", List.of(), DirectionalMoveAction.Mode.SWIM,
                "Swim (in swimming pose) in a direction"));
        registry.register(new JumpAction());
        registry.register(new SneakAction(false));
        registry.register(new SneakAction(true));
        registry.register(new LookAction());
        registry.register(new SpinAction());
        registry.register(new FollowAction());
        registry.register(new StopAction());
    }
}
