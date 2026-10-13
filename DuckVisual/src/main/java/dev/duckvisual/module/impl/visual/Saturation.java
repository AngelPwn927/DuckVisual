package dev.duckvisual.module.impl.visual;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

/**
 * Colour grading for the world: after the world (and hand) is drawn, the frame is copied and
 * drawn back through the saturation shader. The HUD and menus are drawn later and stay as they are.
 */
public class Saturation extends Module {
    private static final ShaderProgramKey SHADER = new ShaderProgramKey(
            Identifier.of("duckvisual", "core/saturation"), VertexFormats.POSITION_TEXTURE, Defines.EMPTY);

    private final ModeSetting preset = add(new ModeSetting("Preset", "Vivid", "Vivid", "Neon", "Cinema", "Soft", "Custom"));
    private final NumberSetting saturation = add(new NumberSetting("Saturation", 2.0, 0.0, 4.0, 0.05).visibleWhen(() -> preset.is("Custom")));
    private final NumberSetting vibrance = add(new NumberSetting("Vibrance", 0.4, 0.0, 1.5, 0.05).visibleWhen(() -> preset.is("Custom")));
    private final NumberSetting contrast = add(new NumberSetting("Contrast", 1.1, 0.5, 2.0, 0.05).visibleWhen(() -> preset.is("Custom")));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.5, 1.5, 0.05).visibleWhen(() -> preset.is("Custom")));

    private SimpleFramebuffer copy;
    private boolean failed;

    public Saturation() {
        super("Saturation", "Makes the world colours vivid", Category.VISUALS);
    }

    /** saturation, vibrance, contrast, brightness for the chosen preset */
    private float[] values() {
        return switch (preset.get()) {
            case "Neon" -> new float[]{3.0f, 0.8f, 1.2f, 1.05f};
            case "Cinema" -> new float[]{1.35f, 0.25f, 1.25f, 0.95f};
            case "Soft" -> new float[]{1.35f, 0.5f, 1.0f, 1.0f};
            case "Custom" -> new float[]{saturation.getFloat(), vibrance.getFloat(), contrast.getFloat(), brightness.getFloat()};
            default -> new float[]{2.0f, 0.4f, 1.1f, 1.0f};
        };
    }

    @Override
    public String getSuffix() {
        return preset.is("Custom") ? Math.round(saturation.get() * 100) + "%" : preset.get();
    }

    /** Called from GameRendererMixin right after the world was rendered. */
    public void apply() {
        if (failed) return;
        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (copy == null) {
            copy = new SimpleFramebuffer(w, h, false);
        } else if (copy.textureWidth != w || copy.textureHeight != h) {
            copy.resize(w, h);
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.beginWrite(false);

        ShaderProgram program = RenderSystem.setShader(SHADER);
        if (program == null) {
            // the shader could not be compiled (very old driver): switch off instead of breaking the screen
            failed = true;
            DuckVisual.LOGGER.warn("Saturation shader unavailable, module disabled");
            setEnabled(false);
            return;
        }
        float[] v = values();
        set(program, "Saturation", v[0]);
        set(program, "Vibrance", v[1]);
        set(program, "Contrast", v[2]);
        set(program, "Brightness", v[3]);

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, copy.getColorAttachment());
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        b.vertex(-1, -1, 0).texture(0, 0);
        b.vertex(1, -1, 0).texture(1, 0);
        b.vertex(1, 1, 0).texture(1, 1);
        b.vertex(-1, 1, 0).texture(0, 1);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popMatrix();
    }

    private static void set(ShaderProgram program, String name, float value) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(value);
    }

    @Override
    public void onEnable() {
        failed = false;
    }
}
