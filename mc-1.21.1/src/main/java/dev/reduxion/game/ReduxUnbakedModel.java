package dev.reduxion.game;

import dev.reduxion.core.Redux;
import dev.reduxion.model.CubeModel;
import dev.reduxion.model.Mesh;
import dev.reduxion.pack.PackCompiler;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Vector3f;

/** Bakes a REDUX mesh directly into quads, so 1.21.1 is not limited by the vanilla JSON model rules. */
public final class ReduxUnbakedModel implements UnbakedModel {
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private final CubeModel model;

    public ReduxUnbakedModel(CubeModel model) { this.model = model; }

    @Override public Collection<ResourceLocation> getDependencies() { return List.of(); }
    @Override public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {}

    @Override public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        List<TextureAtlasSprite> tex = new ArrayList<>();
        for (CubeModel.Tex t : model.textures) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Redux.NS, "item/t_" + PackCompiler.hash(t.png));
            tex.add(sprites.apply(new Material(InventoryMenu.BLOCK_ATLAS, id)));
        }
        Mesh mesh = Mesh.build(model);
        List<BakedQuad> quads = new ArrayList<>(mesh.quads.size());
        for (Mesh.Quad q : mesh.quads) {
            TextureAtlasSprite sprite = tex.get(Math.max(0, Math.min(q.texture, tex.size() - 1)));
            int[] data = new int[32];
            int nx = Math.round(q.normal[0] * 127) & 0xFF, ny = Math.round(q.normal[1] * 127) & 0xFF, nz = Math.round(q.normal[2] * 127) & 0xFF;
            int normal = nx | (ny << 8) | (nz << 16);
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                data[o] = Float.floatToRawIntBits(q.pos[i][0] / 16f);
                data[o + 1] = Float.floatToRawIntBits(q.pos[i][1] / 16f);
                data[o + 2] = Float.floatToRawIntBits(q.pos[i][2] / 16f);
                data[o + 3] = -1;
                data[o + 4] = Float.floatToRawIntBits(sprite.getU(clamp01(q.uv[i][0])));
                data[o + 5] = Float.floatToRawIntBits(sprite.getV(clamp01(q.uv[i][1])));
                data[o + 6] = 0;
                data[o + 7] = normal;
            }
            quads.add(new BakedQuad(data, q.tint, DIRS[q.dir], sprite, q.shade));
        }
        Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) culled.put(d, List.of());
        ItemTransform right3 = tr("thirdperson_righthand"), right1 = tr("firstperson_righthand");
        ItemTransforms transforms = new ItemTransforms(
            model.display.containsKey("thirdperson_lefthand") ? tr("thirdperson_lefthand") : right3, right3,
            model.display.containsKey("firstperson_lefthand") ? tr("firstperson_lefthand") : right1, right1,
            tr("head"), tr("gui"), tr("ground"), tr("fixed"));
        return new SimpleBakedModel(quads, culled, false, model.guiLight3d, true, tex.get(0), transforms, ItemOverrides.EMPTY);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private ItemTransform tr(String slot) {
        float[] d = model.display.get(slot);
        if (d == null) return ItemTransform.NO_TRANSFORM;
        return new ItemTransform(new Vector3f(d[0], d[1], d[2]),
            new Vector3f(c(d[3] / 16f, 5), c(d[4] / 16f, 5), c(d[5] / 16f, 5)),
            new Vector3f(c(d[6], 4), c(d[7], 4), c(d[8], 4)));
    }

    private static float c(float v, float lim) { return Math.max(-lim, Math.min(lim, v)); }
}
