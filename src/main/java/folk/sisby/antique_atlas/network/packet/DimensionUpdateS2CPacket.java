package folk.sisby.antique_atlas.network.packet;

import folk.sisby.antique_atlas.network.S2CPacket;
import folk.sisby.antique_atlas.core.TileInfo;
import folk.sisby.antique_atlas.network.AntiqueAtlasNetworking;
import net.minecraft.network.PacketByteBuf;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record DimensionUpdateS2CPacket(int atlasID, RegistryKey<World> world, Collection<TileInfo> tiles) implements S2CPacket {
    public DimensionUpdateS2CPacket(PacketByteBuf buf) {
        this(buf.readVarInt(), RegistryKey.of(Registry.WORLD_KEY, buf.readIdentifier()), readTiles(buf));
    }

    private static List<TileInfo> readTiles(PacketByteBuf buf) {
        int tileCount = buf.readVarInt();
        List<TileInfo> tiles = new ArrayList<>();
        for (int i = 0; i < tileCount; ++i) {
            tiles.add(new TileInfo(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readIdentifier())
            );
        }
        return tiles;
    }

    @Override
    public void writeBuf(PacketByteBuf buf) {
        buf.writeVarInt(atlasID);
        buf.writeIdentifier(world.getValue());
        buf.writeVarInt(tiles.size());

        for (TileInfo tile : tiles) {
            buf.writeVarInt(tile.x);
            buf.writeVarInt(tile.z);
            buf.writeIdentifier(tile.id);
        }
    }

    @Override
    public Identifier getId() {
        return AntiqueAtlasNetworking.S2C_DIMENSION_UPDATE;
    }
}