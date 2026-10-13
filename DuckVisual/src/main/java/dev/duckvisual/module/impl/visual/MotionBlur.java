package dev.duckvisual.module.impl.visual;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.NumberSetting;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

/**
 * Frame-accumulation motion blur: after the world is drawn, the previous (already blurred)
 * frame is blended on top, then the result is kept for the next frame. HUD is not blurred.
 */
public class MotionBlur extends Module {
    private final NumberSetting strength = add(new NumberSetting("Strength", 0.55, 0.1, 0.9, 0.05));

    private SimpleFramebuffer previous;
    private boolean hasFrame;

    public MotionBlur() {
        super("MotionBlur", "Smooth motion blur", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return Math.round(strength.get() * 100) + "%";
    }

    /** Called from GameRendererMixin right after the world (and hand) was rendered. */
    public void apply() {
        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (previous == null) {
            previous = new SimpleFramebuffer(w, h, false);
            hasFrame = false;
        } else if (previous.textureWidth != w || previous.textureHeight != h) {
            previous.resize(w, h);
            hasFrame = false;
        }

        if (hasFrame) {
            main.beginWrite(false);
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().identity();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, previous.getColorAttachment());
            int a = (int) (strength.get() * 255);
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(-1, -1, 0).texture(0, 0).color(255, 255, 255, a);
            b.vertex(1, -1, 0).texture(1, 0).color(255, 255, 255, a);
            b.vertex(1, 1, 0).texture(1, 1).color(255, 255, 255, a);
            b.vertex(-1, 1, 0).texture(0, 1).color(255, 255, 255, a);
            BufferRenderer.drawWithGlobalProgram(b.end());
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.getModelViewStack().popMatrix();
        }

        // keep this (blurred) frame for the next one
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.beginWrite(false);
        hasFrame = true;
    }

    @Override
    public void onDisable() {
        hasFrame = false;
    }
}
