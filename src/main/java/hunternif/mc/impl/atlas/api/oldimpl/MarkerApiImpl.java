package hunternif.mc.impl.atlas.api.oldimpl;

import hunternif.mc.impl.atlas.AntiqueAtlasMod;
import hunternif.mc.impl.atlas.api.MarkerAPI;
import hunternif.mc.impl.atlas.marker.Marker;
import hunternif.mc.impl.atlas.marker.MarkersData;
import hunternif.mc.impl.atlas.network.packet.c2s.play.DeleteMarkerRequestC2SPacket;
import hunternif.mc.impl.atlas.network.packet.s2c.play.DeleteMarkerResponseS2CPacket;
import hunternif.mc.impl.atlas.network.packet.s2c.play.MarkersS2CPacket;
import hunternif.mc.impl.atlas.registry.MarkerType;
import hunternif.mc.impl.atlas.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Collections;

public class MarkerApiImpl implements MarkerAPI {
	/** Used in place of atlasID to signify that the marker is global. */
	private static final int GLOBAL = -1;

	@Nullable
	@Override
	public Marker putMarker(@Nonnull World world, boolean visibleAhead, int atlasID, MarkerType markerType, String label, int x, int z) {
		return doPutMarker(world, visibleAhead, atlasID, markerType, label, x, z);
	}
	@Nullable
	@Override
	public Marker putGlobalMarker(@Nonnull World world, boolean visibleAhead, MarkerType markerType, String label, int x, int z) {
		return doPutMarker(world, visibleAhead, GLOBAL, markerType, label, x, z);
	}

	private Marker doPutMarker(World world, boolean visibleAhead, int atlasID, MarkerType markerType, String label, int x, int z) {
		Marker marker = null;
		if (!world.isClient && world.getServer() != null) {
			MarkersData data = atlasID == GLOBAL
							? AntiqueAtlasMod.globalMarkersData.getData()
							: AntiqueAtlasMod.markersData.getMarkersData(atlasID, world)
							;

			marker = data.createAndSaveMarker(markerType, world.getRegistryKey(), x, z, visibleAhead, label);
			new MarkersS2CPacket(atlasID, world.getRegistryKey(), Collections.singleton(marker)).send((ServerWorld) world);
		}

		return marker;
	}
	
	@Override
	public void deleteMarker(@Nonnull World world, int atlasID, int markerID) {
		doDeleteMarker(world, atlasID, markerID);
	}

	@Override
	public void deleteGlobalMarker(@Nonnull World world, int markerID) {
		doDeleteMarker(world, GLOBAL, markerID);
	}

	private void doDeleteMarker(World world, int atlasID, int markerID) {
		if (world.isClient) {
			if (atlasID == GLOBAL) {
				Log.warn("Client tried to delete a global marker!");
			} else {
				new DeleteMarkerRequestC2SPacket(atlasID, markerID).send();
			}
		} else {
			MarkersData data = atlasID == GLOBAL ?
					AntiqueAtlasMod.globalMarkersData.getData() :
					AntiqueAtlasMod.markersData.getMarkersData(atlasID, world);
			data.removeMarker(markerID);

			new DeleteMarkerResponseS2CPacket(atlasID, markerID).send(world.getServer());
		}
	}
	
	@Override
	public void registerMarker(Identifier identifier, MarkerType markerType) {
		MarkerType.register(identifier, markerType);
	}

}
