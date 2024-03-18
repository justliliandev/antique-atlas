package folk.sisby.antique_atlas.reloader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.BuiltinStructures;
import folk.sisby.antique_atlas.Marker;
import folk.sisby.antique_atlas.TileTexture;
import folk.sisby.antique_atlas.StructureTileProvider;
import folk.sisby.surveyor.landmark.SimplePointLandmark;
import folk.sisby.surveyor.structure.JigsawPieceSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import folk.sisby.surveyor.structure.StructureSummary;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StructureTileProviders extends JsonDataLoader implements IdentifiableResourceReloadListener {
    private static final StructureTileProviders INSTANCE = new StructureTileProviders();

    public static final Identifier ID = AntiqueAtlas.id("structures");

    public static StructureTileProviders getInstance() {
        return INSTANCE;
    }

    private final Map<Identifier, Pair<Identifier, Text>> structureMarkers = new HashMap<>(); // Structure Start
    private final Map<TagKey<Structure>, Pair<Identifier, Text>> structureTagMarkers = new HashMap<>(); // Structure Start

    private final Map<Identifier, StructureTileProvider> pieceTypeProviders = new HashMap<>();
    private final Map<Identifier, StructureTileProvider> singleJigsawProviders = new HashMap<>();

    public StructureTileProviders() {
        super(new Gson(), "atlas/structures");
    }

    public void registerMarker(StructureType<?> structureFeature, Identifier markerType, Text name) {
        structureMarkers.put(Registries.STRUCTURE_TYPE.getId(structureFeature), Pair.of(markerType, name));
    }

    public void registerMarker(TagKey<Structure> structureTag, Identifier markerType, Text name) {
        structureTagMarkers.put(structureTag, Pair.of(markerType, name));
    }

    public Map<ChunkPos, TileTexture> resolve(Map<ChunkPos, TileTexture> tiles, Map<ChunkPos, StructureTileProvider> structureProviders, StructurePieceSummary piece, World world) {
        if (piece instanceof JigsawPieceSummary jigsawPiece) {
            if (singleJigsawProviders.containsKey(jigsawPiece.getId())) {
                StructureTileProvider provider = singleJigsawProviders.get(jigsawPiece.getId());
                provider.getTextures(world, jigsawPiece.getBoundingBox(), jigsawPiece.getJunctions()).forEach((pos, texture) -> {
                    tiles.put(pos, texture);
                    structureProviders.put(pos, provider);
                });
                return tiles;
            }
        }

        Identifier structurePieceId = Registries.STRUCTURE_PIECE.getId(piece.getType());
        if (pieceTypeProviders.containsKey(structurePieceId)) {
            StructureTileProvider provider = pieceTypeProviders.get(structurePieceId);
            provider.getTextures(world, piece.getBoundingBox()).forEach((pos, texture) -> {
                tiles.put(pos, texture);
                structureProviders.put(pos, provider);
            });
        }
        return tiles;
    }

    public void resolve(Map<ChunkPos, TileTexture> outTiles, Map<ChunkPos, StructureTileProvider> structureProviders, Map<RegistryKey<Structure>, Map<ChunkPos, Marker>> outMarkers, World world, RegistryKey<Structure> key, ChunkPos pos, StructureSummary summary, RegistryKey<StructureType<?>> type, Collection<TagKey<Structure>> tags) {
        Pair<Identifier, Text> foundMarker = structureMarkers.get(key.getValue());
        if (foundMarker == null) {
            foundMarker = structureTagMarkers.entrySet().stream().filter(entry -> tags.contains(entry.getKey())).findFirst().map(Map.Entry::getValue).orElse(null);
        }
        if (foundMarker != null) {
            outMarkers.computeIfAbsent(key, k -> new HashMap<>()).put(pos, new Marker(SimplePointLandmark.TYPE, foundMarker.getLeft(), foundMarker.getRight(), pos.getCenterAtY(0), false, null));
        }

        summary.getChildren().forEach(p -> resolve(outTiles, structureProviders, p, world));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        Map<Identifier, TileTexture> textures = TileTextures.getInstance().getTextures();
        Set<TileTexture> unusedTextures = new HashSet<>(textures.values().stream().filter(t -> t.id().getPath().startsWith("structure")).toList());

        pieceTypeProviders.clear();
        BuiltinStructures.reload(textures).forEach((type, provider) -> {
            Identifier id = Registries.STRUCTURE_PIECE.getId(type);
            pieceTypeProviders.put(id, provider);
            unusedTextures.removeAll(provider.allTextures());
        });

        singleJigsawProviders.clear();
        for (Map.Entry<Identifier, JsonElement> fileEntry : prepared.entrySet()) {
            Identifier fileId = fileEntry.getKey();
            Identifier id = new Identifier(fileId.getNamespace(), fileId.getPath().substring("jigsaw/".length()));
            try {
                JsonObject fileJson = fileEntry.getValue().getAsJsonObject();
                StructureTileProvider provider = new StructureTileProvider(
                    id,
                    BiomeTileProviders.resolveTextureJson(textures, fileJson.get("textures"))
                );
                singleJigsawProviders.put(provider.id(), provider);
                unusedTextures.removeAll(provider.allTextures());
            } catch (Exception e) {
                AntiqueAtlas.LOGGER.warn("[Antique Atlas] Error reading jigsaw tile provider from " + fileId + "!", e);
            }
        }

        for (TileTexture texture : unusedTextures) {
            AntiqueAtlas.LOGGER.warn("[Antique Atlas] Tile texture {} isn't referenced by any structure tile provider!", texture.displayId());
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public Collection<Identifier> getFabricDependencies() {
        return List.of(TileTextures.ID);
    }
}
