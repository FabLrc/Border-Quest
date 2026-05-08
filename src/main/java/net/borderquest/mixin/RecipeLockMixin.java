package net.borderquest.mixin;

import net.borderquest.BorderQuest;
import net.borderquest.BorderQuestManager;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class RecipeLockMixin {

    @Mixin(CraftingScreenHandler.class)
    public static class CraftingTable {

        @Inject(method = "onContentChanged", at = @At("TAIL"))
        private void bq_lockCraftingRecipes(CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            CraftingScreenHandler handler = (CraftingScreenHandler) (Object) this;
            ItemStack result = handler.getSlot(0).getStack();
            if (result.isEmpty()) return;

            String itemId = Registries.ITEM.getId(result.getItem()).toString();
            if (!mgr.isRecipeUnlocked(itemId)) {
                handler.getSlot(0).setStack(ItemStack.EMPTY);
                sendLockMessage(mgr, handler);
            }
        }

        private void sendLockMessage(BorderQuestManager mgr, CraftingScreenHandler handler) {
            for (ServerPlayerEntity player : mgr.getServer().getPlayerManager().getPlayerList()) {
                if (player.currentScreenHandler == handler) {
                    player.sendMessage(
                        Text.literal("Cette recette n'est pas encore debloquee.").formatted(Formatting.RED),
                        false
                    );
                    break;
                }
            }
        }
    }

    @Mixin(PlayerScreenHandler.class)
    public static class PlayerCrafting {

        @Inject(method = "onContentChanged", at = @At("TAIL"))
        private void bq_lockPlayerCrafting(CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            PlayerScreenHandler handler = (PlayerScreenHandler) (Object) this;
            ItemStack result = handler.getSlot(0).getStack();
            if (result.isEmpty()) return;

            String itemId = Registries.ITEM.getId(result.getItem()).toString();
            if (!mgr.isRecipeUnlocked(itemId)) {
                handler.getSlot(0).setStack(ItemStack.EMPTY);
                sendLockMessage(mgr, handler);
            }
        }

        private void sendLockMessage(BorderQuestManager mgr, PlayerScreenHandler handler) {
            for (ServerPlayerEntity player : mgr.getServer().getPlayerManager().getPlayerList()) {
                if (player.currentScreenHandler == handler) {
                    player.sendMessage(
                        Text.literal("Cette recette n'est pas encore debloquee.").formatted(Formatting.RED),
                        false
                    );
                    break;
                }
            }
        }
    }

    @Mixin(AbstractFurnaceBlockEntity.class)
    public static class Furnace {

        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private static void bq_lockFurnaceRecipes(ServerWorld world, BlockPos pos,
                net.minecraft.block.BlockState state, AbstractFurnaceBlockEntity be, CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            ItemStack input = be.getStack(0);
            if (input.isEmpty()) return;

            ServerRecipeManager recipeManager = mgr.getServer().getRecipeManager();
            SingleStackRecipeInput recipeInput = new SingleStackRecipeInput(input);
            @SuppressWarnings("rawtypes")
            RecipeType[] furnaceTypes = {RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING};
            boolean locked = false;
            for (RecipeType type : furnaceTypes) {
                var recipe = recipeManager.getFirstMatch(type, recipeInput, world);
                if (recipe.isEmpty()) continue;

                RecipeEntry<?> entry = (RecipeEntry<?>) recipe.get();
                @SuppressWarnings("rawtypes")
                Recipe rawRecipe = entry.value();
                ItemStack result = rawRecipe.craft(recipeInput, world.getRegistryManager());
                if (result.isEmpty()) continue;

                String itemId = Registries.ITEM.getId(result.getItem()).toString();
                if (!mgr.isRecipeUnlocked(itemId)) {
                    locked = true;
                    break;
                }
            }
            if (locked) {
                ci.cancel();
            }
        }
    }

    @Mixin(ServerPlayNetworkHandler.class)
    public static class RecipeBook {

        @Inject(method = "onCraftRequest", at = @At("HEAD"), cancellable = true)
        private void bq_lockRecipeBook(CraftRequestC2SPacket packet, CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            ServerRecipeManager recipeManager = mgr.getServer().getRecipeManager();
            var serverRecipe = recipeManager.get(packet.recipeId());
            if (serverRecipe == null) return;

            RecipeEntry<?> entry = serverRecipe.parent();
            @SuppressWarnings("rawtypes")
            Recipe rawRecipe = entry.value();
            ItemStack result = rawRecipe.craft(
                CraftingRecipeInput.EMPTY,
                mgr.getServer().getOverworld().getRegistryManager()
            );
            if (result.isEmpty()) return;

            String itemId = Registries.ITEM.getId(result.getItem()).toString();
            if (!mgr.isRecipeUnlocked(itemId)) {
                ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
                handler.player.sendMessage(
                    Text.literal("Cette recette n'est pas encore debloquee.").formatted(Formatting.RED),
                    false
                );
                ci.cancel();
            }
        }
    }
}
