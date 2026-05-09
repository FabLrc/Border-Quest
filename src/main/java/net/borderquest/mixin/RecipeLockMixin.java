package net.borderquest.mixin;

import net.borderquest.BorderQuest;
import net.borderquest.BorderQuestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class RecipeLockMixin {

    @Mixin(CraftingMenu.class)
    public static class CraftingTable {

        @Inject(method = "slotsChanged", at = @At("TAIL"))
        private void bq_lockCraftingRecipes(CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            CraftingMenu handler = (CraftingMenu) (Object) this;
            ItemStack result = handler.getSlot(0).getItem();
            if (result.isEmpty()) return;

            String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            if (!mgr.isRecipeUnlocked(itemId)) {
                handler.getSlot(0).set(ItemStack.EMPTY);
                sendLockMessage(mgr, handler);
            }
        }

        private void sendLockMessage(BorderQuestManager mgr, CraftingMenu handler) {
            for (ServerPlayer player : mgr.getServer().getPlayerList().getPlayers()) {
                if (player.containerMenu == handler) {
                    player.sendSystemMessage(
                        Component.literal("Cette recette n'est pas encore debloquee.").withStyle(ChatFormatting.RED)
                    );
                    break;
                }
            }
        }
    }

    @Mixin(InventoryMenu.class)
    public static class PlayerCrafting {

        @Inject(method = "slotsChanged", at = @At("TAIL"))
        private void bq_lockPlayerCrafting(CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            InventoryMenu handler = (InventoryMenu) (Object) this;
            ItemStack result = handler.getSlot(0).getItem();
            if (result.isEmpty()) return;

            String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            if (!mgr.isRecipeUnlocked(itemId)) {
                handler.getSlot(0).set(ItemStack.EMPTY);
                sendLockMessage(mgr, handler);
            }
        }

        private void sendLockMessage(BorderQuestManager mgr, InventoryMenu handler) {
            for (ServerPlayer player : mgr.getServer().getPlayerList().getPlayers()) {
                if (player.containerMenu == handler) {
                    player.sendSystemMessage(
                        Component.literal("Cette recette n'est pas encore debloquee.").withStyle(ChatFormatting.RED)
                    );
                    break;
                }
            }
        }
    }

    @Mixin(AbstractFurnaceBlockEntity.class)
    public static class Furnace {

        @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
        private static void bq_lockFurnaceRecipes(ServerLevel level, BlockPos pos,
                BlockState state, AbstractFurnaceBlockEntity be, CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            ItemStack input = be.getItem(0);
            if (input.isEmpty()) return;

            RecipeManager recipeManager = mgr.getServer().getRecipeManager();
            SingleRecipeInput recipeInput = new SingleRecipeInput(input);
            @SuppressWarnings("rawtypes")
            RecipeType[] furnaceTypes = {RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING};
            boolean locked = false;
            for (RecipeType type : furnaceTypes) {
                var recipe = recipeManager.getRecipeFor(type, recipeInput, level);
                if (recipe.isEmpty()) continue;

                RecipeHolder<?> entry = (RecipeHolder<?>) recipe.get();
                @SuppressWarnings("rawtypes")
                Recipe rawRecipe = entry.value();
                ItemStack result = rawRecipe.assemble(recipeInput);
                if (result.isEmpty()) continue;

                String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
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

    @Mixin(ServerGamePacketListenerImpl.class)
    public static class RecipeBook {

        @Inject(method = "handlePlaceRecipe", at = @At("TAIL"))
        private void bq_lockRecipeBook(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
            BorderQuestManager mgr = BorderQuest.manager;
            if (mgr == null) return;

            ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;

            if (handler.player.containerMenu instanceof CraftingMenu menu) {
                ItemStack result = menu.getSlot(0).getItem();
                if (result.isEmpty()) return;

                String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
                if (!mgr.isRecipeUnlocked(itemId)) {
                    menu.getSlot(0).set(ItemStack.EMPTY);
                    handler.player.sendSystemMessage(
                        Component.literal("Cette recette n'est pas encore debloquee.").withStyle(ChatFormatting.RED)
                    );
                }
            }
        }
    }
}
