package folk.sisby.antique_atlas.client;

import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.client.texture.ITexture;
import folk.sisby.antique_atlas.core.scanning.TileHeightType;
import folk.sisby.antique_atlas.util.Log;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.tag.BiomeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;

import java.util.*;
import java.util.Map.Entry;

/**
 * Maps biome IDs (or pseudo IDs) to textures. <i>Not thread-safe!</i>
 * <p>If several textures are set for one ID, one will be chosen at random when
 * putting tile into Atlas.</p>
 *
 * @author Hunternif
 */
public class TileTextureMap {
    private static final TileTextureMap INSTANCE = new TileTextureMap();

    public static final Identifier DEFAULT_TEXTURE = AntiqueAtlas.id("test");

    public static TileTextureMap instance() {
        return INSTANCE;
    }

    /**
     * This map stores the pseudo biome texture mappings, any biome with ID <0 is assumed to be a pseudo biome
     */
    private final Map<Identifier, TextureSet> textureMap = new HashMap<>();

    /**
     * Assign texture set to pseudo biome
     */
    public void setTexture(Identifier tileId, TextureSet textureSet) {
        if (tileId == null) return;

        if (textureSet == null) {
            if (textureMap.remove(tileId) != null) {
                Log.warn("Removing old texture for %d", tileId);
            }
            return;
        }

        textureMap.put(tileId, textureSet);
    }

    /**
     * Assign the same texture set to all height variations of the tileId
     */
    public void setAllTextures(Identifier tileId, TextureSet textureSet) {
        setTexture(tileId, textureSet);

        for (TileHeightType layer : TileHeightType.values()) {
            setTexture(Identifier.tryParse(tileId + "_" + layer), textureSet);
        }
    }

    public TextureSet getDefaultTexture() {
        return TextureSetMap.instance().getByName(DEFAULT_TEXTURE);
    }

    /**
     * Find the most appropriate standard texture set depending on
     * BiomeDictionary types.
     */
    public void autoRegister(Identifier id, RegistryKey<Biome> biome) {
        if (biome == null || id == null) {
            Log.error("Given biome is null. Cannot autodetect a suitable texture set for that.");
            return;
        }

        Optional<Identifier> texture_set = guessFittingTextureSet(biome);

        if (texture_set.isPresent()) {
            setAllTextures(id, TextureSetMap.instance().getByName(texture_set.get()));
            Log.info("Auto-registered standard texture set for biome %s: %s", id, texture_set.get());
        } else {
            Log.error("Failed to auto-register a standard texture set for the biome '%s'. This is most likely caused by errors in the TextureSet configurations, check your resource packs first before reporting it as an issue!", id.toString());
            setAllTextures(id, getDefaultTexture());
        }
    }

    static private Optional<Identifier> guessFittingTextureSet(RegistryKey<Biome> biome) {
        if (MinecraftClient.getInstance().world == null)
            return Optional.empty();

        RegistryEntry<Biome> biomeTag = MinecraftClient.getInstance().world.getRegistryManager().get(Registry.BIOME_KEY).entryOf(biome);

        if (biomeIsVoid(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("end_void"));
        }

        if (biomeTag.isIn(BiomeTags.IS_END) || biomeIsEnd(biomeTag)) {
            if (biomeHasVegetation(biomeTag)) {
                return Optional.of(AntiqueAtlas.id("end_island_plants"));
            } else {
                return Optional.of(AntiqueAtlas.id("end_island"));
            }
        }

        if (biomeTag.isIn(BiomeTags.IS_NETHER) || biomeIsNether(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("soul_sand_valley"));
        }

        if (biomeIsSwamp(biomeTag)) {
            if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("swamp_hills"));
            } else {
                return Optional.of(AntiqueAtlas.id("swamp"));
            }
        }

        if (biomeTag.isIn(BiomeTags.IS_OCEAN)
            || biomeTag.isIn(BiomeTags.IS_DEEP_OCEAN)
            || biomeTag.isIn(BiomeTags.IS_RIVER)
            || biomeIsWater(biomeTag)) {
            if (biomeIsIcy(biomeTag))
                return Optional.of(AntiqueAtlas.id("ice"));

            return Optional.of(AntiqueAtlas.id("water"));
        }

        if (biomeTag.isIn(BiomeTags.IS_BEACH) || biomeIsShore(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("shore"));
        }

        if (biomeTag.isIn(BiomeTags.IS_JUNGLE) || biomeIsJungle(biomeTag)) {
            if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("jungle_hills"));
            } else {
                return Optional.of(AntiqueAtlas.id("jungle"));
            }
        }

        if (biomeTag.isIn(BiomeTags.IS_SAVANNA) || biomeIsSavanna(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("savana"));
        }

        if (biomeTag.isIn(BiomeTags.IS_BADLANDS) || biomeIsBadlands(biomeTag)) {
            if (biomeIsPlateau(biomeTag)) { // Is this still valid? Does height checking supersede this?
                return Optional.of(AntiqueAtlas.id("plateau_mesa"));
            } else {
                return Optional.of(AntiqueAtlas.id("mesa"));
            }
        }

        if (biomeTag.isIn(BiomeTags.IS_FOREST) || biomeIsForest(biomeTag)) {
            if (biomeIsIcy(biomeTag) || biomeIsSnowy(biomeTag)) {
                if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("snow_pines_hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("snow_pines"));
                }
            } else {
                if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("forest_hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("forest"));
                }
            }
        }

        if (biomeIsPlains(biomeTag)) {
            if (biomeIsIcy(biomeTag) || biomeIsSnowy(biomeTag)) {
                if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("snow_hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("snow"));
                }
            } else {
                if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("plains"));
                }
            }
        }

        if (biomeIsIcy(biomeTag)) {
            if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("mountains_snow_caps"));
            } else {
                return Optional.of(AntiqueAtlas.id("ice_spikes"));
            }
        }

        if (biomeIsDesert(biomeTag)) {
            if (biomeTag.isIn(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("desert_hills"));
            } else {
                return Optional.of(AntiqueAtlas.id("desert"));
            }
        }

        if (biomeTag.isIn(BiomeTags.IS_TAIGA) || biomeIsTaiga(biomeTag)) { // should this be any snowy biome as a fallback?
            return Optional.of(AntiqueAtlas.id("snow"));
        }

        if (biomeIsExtremeHills(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("hills"));
        }

        if (biomeIsPeak(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("mountains_snow_caps"));
        }

        if (biomeTag.isIn(BiomeTags.IS_MOUNTAIN) || biomeIsMountain(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("mountains"));
        }

        if (biomeIsMushroom(biomeTag)) {
            return Optional.of(AntiqueAtlas.id("mushroom"));
        }

        if (biomeTag.isIn(BiomeTags.IS_HILL)) {
            return Optional.of(AntiqueAtlas.id("hills"));
        }

        if (biomeIsUnderground(biomeTag)) {
            AntiqueAtlas.LOG.warn("Underground biomes aren't supported yet.");
        }

        return Optional.empty();
    }

    public static boolean biomeIsVoid(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.VOID);
    }

    public static boolean biomeIsEnd(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.IN_THE_END) || biomeTag.isIn(ConventionalBiomeTags.END_ISLANDS);
    }

    public static boolean biomeHasVegetation(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.VEGETATION_DENSE) || biomeTag.isIn(ConventionalBiomeTags.VEGETATION_SPARSE);
    }

    public static boolean biomeIsNether(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.IN_NETHER);
    }

    public static boolean biomeIsSwamp(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.SWAMP);
    }

    public static boolean biomeIsWater(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.AQUATIC);
    }

    public static boolean biomeIsIcy(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.ICY);
    }

    public static boolean biomeIsShore(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.BEACH);
    }

    public static boolean biomeIsJungle(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.JUNGLE) || biomeTag.isIn(ConventionalBiomeTags.TREE_JUNGLE);
    }

    public static boolean biomeIsSavanna(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.SAVANNA) || biomeTag.isIn(ConventionalBiomeTags.TREE_SAVANNA);
    }

    public static boolean biomeIsBadlands(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn((ConventionalBiomeTags.BADLANDS)) || biomeTag.isIn((ConventionalBiomeTags.MESA));
    }

    public static boolean biomeIsPlateau(RegistryEntry<Biome> biomeTag) {
        return false; // None
    }

    public static boolean biomeIsForest(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.TREE_DECIDUOUS);
    }

    public static boolean biomeIsSnowy(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.SNOWY);
    }

    public static boolean biomeIsPlains(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.PLAINS) || biomeTag.isIn(ConventionalBiomeTags.SNOWY_PLAINS);
    }

    public static boolean biomeIsDesert(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.DESERT);
    }

    public static boolean biomeIsTaiga(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.TAIGA);
    }

    public static boolean biomeIsExtremeHills(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.EXTREME_HILLS);
    }

    public static boolean biomeIsPeak(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.MOUNTAIN_PEAK);
    }

    public static boolean biomeIsMountain(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.MOUNTAIN) || biomeTag.isIn(ConventionalBiomeTags.MOUNTAIN_SLOPE);
    }

    public static boolean biomeIsMushroom(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.MUSHROOM);
    }

    public static boolean biomeIsUnderground(RegistryEntry<Biome> biomeTag) {
        return biomeTag.isIn(ConventionalBiomeTags.UNDERGROUND);
    }

    public boolean isRegistered(Identifier id) {
        return textureMap.containsKey(id);
    }

    /**
     * If unknown biome, auto-registers a texture set. If null, returns default set.
     */
    public TextureSet getTextureSet(Identifier tile) {
        if (tile == null) {
            return getDefaultTexture();
        }

        return textureMap.getOrDefault(tile, getDefaultTexture());
    }

    public ITexture getTexture(SubTile subTile) {
        return getTextureSet(subTile.tile).getTexture(subTile.variationNumber);
    }

    public List<Identifier> getAllTextures() {
        List<Identifier> list = new ArrayList<>();

        for (Entry<Identifier, TextureSet> entry : textureMap.entrySet()) {
            Arrays.stream(entry.getValue().textures).forEach(iTexture -> list.add(iTexture.getTexture()));
        }

        return list;
    }
}