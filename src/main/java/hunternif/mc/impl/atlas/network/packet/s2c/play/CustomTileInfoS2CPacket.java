package hunternif.mc.impl.atlas.network.packet.s2c.play;

import hunternif.mc.impl.atlas.AntiqueAtlasMod;
import hunternif.mc.impl.atlas.ext.ExtBiomeData;
import hunternif.mc.impl.atlas.network.packet.s2c.S2CPacket;
import hunternif.mc.impl.atlas.util.ShortVec2;
import net.fabricmc.fabric.api.network.PacketContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Map;

/**
 * Used to sync custom tiles from server to client.
 * @author Hunternif
 * @author Haven King
 */
public class CustomTileInfoS2CPacket extends S2CPacket {
	public static final Identifier ID = AntiqueAtlasMod.id("packet", "s2c", "custom_tile", "info");

	public CustomTileInfoS2CPacket(RegistryKey<World> world, Map<ShortVec2, Integer> biomes) {
		this.writeIdentifier(world.getValue());
		this.writeVarInt(biomes.size());

		for (Map.Entry<ShortVec2, Integer> entry : biomes.entrySet()) {
			this.writeShort(entry.getKey().x);
			this.writeShort(entry.getKey().y);
			this.writeInt(entry.getValue());
		}
	}

	public CustomTileInfoS2CPacket(RegistryKey<World> world, int chunkX, int chunkZ, int biomeId) {
		this.writeIdentifier(world.getValue());
		this.writeVarInt(1);
		this.writeShort(chunkX);
		this.writeShort(chunkZ);
		this.writeInt(biomeId);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	public static void apply(PacketContext context, PacketByteBuf buf) {
		RegistryKey<World> world = RegistryKey.of(Registry.DIMENSION, buf.readIdentifier());
		int tileCount = buf.readVarInt();

		ExtBiomeData data = AntiqueAtlasMod.extBiomeData.getData();
		for (int i = 0; i < tileCount; ++i) {
			data.setBiomeAt(world, buf.readShort(), buf.readShort(), buf.readInt());
		}
	}
}
