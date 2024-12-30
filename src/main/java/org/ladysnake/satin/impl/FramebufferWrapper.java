/*
 * Satin
 * Copyright (C) 2019-2024 Ladysnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; If not, see <https://www.gnu.org/licenses>.
 */
package org.ladysnake.satin.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.Window;
import org.ladysnake.satin.Satin;
import org.ladysnake.satin.api.managed.ManagedFramebuffer;
import org.ladysnake.satin.mixin.client.AccessiblePassesShaderEffect;

import javax.annotation.Nullable;

public final class FramebufferWrapper implements ManagedFramebuffer {
    private final RenderLayerSupplier renderLayerSupplier;
    private final String name;
    @Nullable
    private Framebuffer wrapped;

    FramebufferWrapper(String name) {
        this.name = name;
        this.renderLayerSupplier = RenderLayerSupplier.framebuffer(
                this.name + System.identityHashCode(this),
                () -> this.beginWrite(false),
                () -> MinecraftClient.getInstance().getFramebuffer().beginWrite(false)
        );
    }

    void findTarget(@Nullable PostEffectProcessor shaderEffect) {
        if (shaderEffect == null) {
            this.wrapped = null;
        } else {
            // FIXME create the target instead and add it to a FramebufferSet
            this.wrapped = null; ((AccessiblePassesShaderEffect) shaderEffect).getInternalTargets().get(this.name);
            if (this.wrapped == null) {
                Satin.LOGGER.warn("No target framebuffer found with name {} in shader", this.name);
            }
        }
    }

    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public Framebuffer getFramebuffer() {
        return wrapped;
    }

    @Override
    public void copyDepthFrom(Framebuffer buffer) {
        if (this.wrapped != null) {
            this.wrapped.copyDepthFrom(buffer);
        }
    }

    @Override
    public void beginWrite(boolean updateViewport) {
        if (this.wrapped != null) {
            this.wrapped.beginWrite(updateViewport);
        }
    }

    @Override
    public void draw() {
        Window window = MinecraftClient.getInstance().getWindow();
        this.draw(window.getFramebufferWidth(), window.getFramebufferHeight(), true);
    }

    @Override
    public void draw(int width, int height, boolean disableBlend) {
        if (this.wrapped != null) {
            this.wrapped.draw(width, height);
        }
    }

    @Override
    public void clear() {
        if (this.wrapped != null) {
            this.wrapped.clear();
        }
    }

    @Override
    public RenderLayer getRenderLayer(RenderLayer baseLayer) {
        return this.renderLayerSupplier.getRenderLayer(baseLayer);
    }
}
