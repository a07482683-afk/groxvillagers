package com.example.realvillagers.client;

import com.example.realvillagers.RealVillagers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class RealVillagersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(RealVillagers.REAL_VILLAGER, RealVillagerRenderer::new);
    }
}
