package com.momo.ominousvault.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import java.util.Optional;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

final class VaultRenderLayers {
    static final RenderLayer SEE_THROUGH_LINES = createSeeThroughLines();

    private VaultRenderLayers() {
    }

    private static RenderLayer createSeeThroughLines() {
        RenderPipeline vanillaLines = RenderPipelines.LINES;
        RenderPipeline.Snippet lineSnippet = new RenderPipeline.Snippet(
                Optional.of(vanillaLines.getVertexShader()),
                Optional.of(vanillaLines.getFragmentShader()),
                Optional.of(vanillaLines.getShaderDefines()),
                Optional.of(vanillaLines.getSamplers()),
                Optional.of(vanillaLines.getUniforms()),
                vanillaLines.getBlendFunction(),
                Optional.empty(),
                Optional.of(vanillaLines.getPolygonMode()),
                Optional.of(vanillaLines.isCull()),
                Optional.of(vanillaLines.isWriteColor()),
                Optional.of(vanillaLines.isWriteAlpha()),
                Optional.of(vanillaLines.isWriteDepth()),
                Optional.of(vanillaLines.getColorLogic()),
                Optional.of(vanillaLines.getVertexFormat()),
                Optional.of(vanillaLines.getVertexFormatMode())
        );

        RenderPipeline pipeline = RenderPipeline.builder(lineSnippet)
                .withLocation(Identifier.of("ominous-vault-track", "pipeline/see_through_lines"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .build();
        return RenderLayer.of("ominous_vault_see_through_lines", setup);
    }
}
