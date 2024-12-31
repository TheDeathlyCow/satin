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

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import org.apiguardian.api.API;
import org.joml.Matrix4f;
import org.ladysnake.satin.Satin;
import org.ladysnake.satin.api.managed.ManagedFramebuffer;
import org.ladysnake.satin.api.managed.ManagedShaderEffect;
import org.ladysnake.satin.api.managed.ShaderEffectManager;
import org.ladysnake.satin.api.managed.uniform.SamplerUniformV2;
import org.ladysnake.satin.mixin.client.AccessiblePassesShaderEffect;
import org.ladysnake.satin.mixin.client.render.GameRendererAccessor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.apiguardian.api.API.Status.INTERNAL;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

/**
 * A {@link ManagedShaderEffect} that can be {@link #setup} several times in its lifetime,
 * triggering an initialization callback.
 *
 * @see ReloadableShaderEffectManager
 * @see ManagedShaderEffect
 * @since 1.0.0
 */
public final class ResettableManagedShaderEffect extends ResettableManagedShaderBase<PostEffectProcessor> implements ManagedShaderEffect {

    /**Callback to run once each time the shader effect is initialized*/
    private final Consumer<ManagedShaderEffect> initCallback;
    private final Map<String, FramebufferWrapper> managedTargets;
    private final Map<String, ManagedPassSamplerUniform> managedSamplers = new HashMap<>();

    /**
     * Creates a new shader effect. <br>
     * <b>Users should obtain instanced of this class through {@link ShaderEffectManager}</b>
     *
     * @param location         the location of a shader effect JSON definition file
     * @param initCallback code to run in {@link #setup()}
     * @see ReloadableShaderEffectManager#manage(Identifier)
     * @see ReloadableShaderEffectManager#manage(Identifier, Consumer)
     */
    @API(status = INTERNAL)
    public ResettableManagedShaderEffect(Identifier location, Consumer<ManagedShaderEffect> initCallback) {
        super(location);
        this.initCallback = initCallback;
        this.managedTargets = new HashMap<>();
    }

    @Nullable
    @Override
    public PostEffectProcessor getShaderEffect() {
        return getShaderOrLog();
    }

    @Override
    public void initialize() throws IOException {
        super.initialize(MinecraftClient.getInstance().getResourceManager());
    }

    @Override
    protected PostEffectProcessor parseShader(ResourceFactory resourceFactory, MinecraftClient mc, Identifier location) throws IOException {
        return mc.getShaderLoader().loadPostEffect(location, DefaultFramebufferSet.MAIN_ONLY);
    }

    @Override
    public void setup() {
        Preconditions.checkNotNull(shader);

        for (ManagedUniformBase uniform : this.getManagedUniforms()) {
            setupUniform(uniform, shader);
        }

        for (FramebufferWrapper buf : this.managedTargets.values()) {
            buf.findTarget(this.shader);
        }

        this.initCallback.accept(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(float tickDelta) {
        PostEffectProcessor sg = this.getShaderEffect();
        if (sg != null) {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            MinecraftClient client = MinecraftClient.getInstance();
            sg.render(client.getFramebuffer(), ((GameRendererAccessor) client.gameRenderer).getPool());
            client.getFramebuffer().beginWrite(true);
            RenderSystem.disableBlend();
            RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // restore blending
            RenderSystem.enableDepthTest();
        }
    }

    @Override
    public ManagedFramebuffer getTarget(String name) {
        return this.managedTargets.computeIfAbsent(name, n -> {
            FramebufferWrapper ret = new FramebufferWrapper(n);
            if (this.shader != null) {
                ret.findTarget(this.shader);
            }
            return ret;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, int value) {
        this.findUniform1i(uniformName).set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, int value0, int value1) {
        this.findUniform2i(uniformName).set(value0, value1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, int value0, int value1, int value2) {
        this.findUniform3i(uniformName).set(value0, value1, value2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, int value0, int value1, int value2, int value3) {
        this.findUniform4i(uniformName).set(value0, value1, value2, value3);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, float value) {
        this.findUniform1f(uniformName).set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, float value0, float value1) {
        this.findUniform2f(uniformName).set(value0, value1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, float value0, float value1, float value2) {
        this.findUniform3f(uniformName).set(value0, value1, value2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, float value0, float value1, float value2, float value3) {
        this.findUniform4f(uniformName).set(value0, value1, value2, value3);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUniformValue(String uniformName, Matrix4f value) {
        this.findUniformMat4(uniformName).set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSamplerUniform(String samplerName, AbstractTexture texture) {
        this.findSampler(samplerName).set(texture);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSamplerUniform(String samplerName, Framebuffer textureFbo) {
        this.findSampler(samplerName).set(textureFbo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSamplerUniform(String samplerName, int textureName) {
        this.findSampler(samplerName).set(textureName);
    }

    @Override
    public SamplerUniformV2 findSampler(String samplerName) {
        return manageUniform(this.managedSamplers, ManagedPassSamplerUniform::new, samplerName, "sampler");
    }

    @Override
    protected boolean setupUniform(ManagedUniformBase uniform, PostEffectProcessor shader) {
        return uniform.findUniformTargets(((AccessiblePassesShaderEffect) shader).getPasses());
    }

    @Override
    protected void logInitError(IOException e) {
        Satin.LOGGER.error("Could not create screen shader {}", this.getLocation(), e);
    }

    private @Nullable PostEffectProcessor getShaderOrLog() {
        if (!this.isInitialized() && !this.isErrored()) {
            this.initializeOrLog(MinecraftClient.getInstance().getResourceManager());
        }
        return this.shader;
    }

    @Override
    protected void doRelease(PostEffectProcessor shader) {

    }
}