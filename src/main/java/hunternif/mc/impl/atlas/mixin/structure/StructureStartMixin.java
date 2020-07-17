package hunternif.mc.impl.atlas.mixin.structure;

import hunternif.mc.impl.atlas.structure.StructurePieceAddedCallback;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(StructureStart.class)
public class StructureStartMixin {
	@Redirect(method = "generateStructure", at = @At(value = "INVOKE", target = "Lnet/minecraft/structure/StructurePiece;generate(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Ljava/util/Random;Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean structurePieceGenerated(StructurePiece structurePiece, ServerWorldAccess serverWorldAccess, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox boundingBox, ChunkPos chunkPos, BlockPos blockPos) {
		World world;

		if (serverWorldAccess instanceof ServerWorld) {
			world = (World) serverWorldAccess;
		} else {
			world = ((ChunkRegion) serverWorldAccess).world;
		}

		StructurePieceAddedCallback.EVENT.invoker().onStructurePieceAdded(world, structurePiece);
		return structurePiece.generate(serverWorldAccess, structureAccessor, chunkGenerator, random, boundingBox, chunkPos, blockPos);
	}
}
