package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Streams;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.GuiBlocker;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.util.Comparator;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class DonkeyDupeCommand {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final SimpleCommandExceptionType NO_DONKEY_IN_RANGE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cdonkeydupe.noDonkeyInRange"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cdonkeydupe");

        dispatcher.register(literal("cdonkeydupe")
                .executes(ctx -> dupe(ctx.getSource()))
                .then(argument("time", integer(0))
                        .executes(ctx -> dupe(ctx.getSource(), getInteger(ctx, "time")))));
    }

    private static int dupe(ServerCommandSource source) {
        return dupe(source, 1);
    }

    private static int dupe(ServerCommandSource source, int time) {
        TaskManager.addTask("cdonkeydupe", new DonkeyDupeTask(time));
        return 0;
    }

    private static class DonkeyDupeTask extends SimpleTask {

        private final int time;

        public DonkeyDupeTask(int time) {
            this.time = time;
        }

        @Override
        public boolean condition() {
            return true;
        }

        @Override
        protected void onTick() {
            Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;
            if (cameraEntity == null) {
                _break();
                return;
            }
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            float reachDistance = interactionManager.getReachDistance();

            Optional<DonkeyEntity> optionalDonkey = Streams.stream(client.world.getEntities())
                    .filter(entity -> entity.distanceTo(client.player) <= reachDistance)
                    .filter(entity -> entity instanceof DonkeyEntity)
                    .map(entity -> (DonkeyEntity) entity)
                    .filter(donkeyEntity -> donkeyEntity.isTame() && donkeyEntity.hasChest() && !donkeyEntity.hasPassengers())
                    .min(Comparator.comparingDouble(entity -> entity.distanceTo(client.player)));

            if (optionalDonkey.isEmpty()) {
                _break();
                return;
            }
            DonkeyEntity donkey = optionalDonkey.get();
            GuiBlocker.addBlocker(new GuiBlocker() {
                @Override
                public boolean accept(Screen screen) {
                    if (!(screen instanceof ScreenHandlerProvider)) {
                        return true;
                    }

                    ScreenHandler container = ((ScreenHandlerProvider<?>) screen).getScreenHandler();
                    for (Slot slot : container.slots) {
                        if (slot.inventory instanceof PlayerInventory) {
                            continue;
                        }
                        slot.takeStack(slot.getStack().getCount());
                    }
                    client.player.getInventory().dropAll();
                    return false;
                }
            });
            client.player.setSneaking(true);
            client.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interact(donkey, true, client.player.preferredHand));
        }
    }
}
