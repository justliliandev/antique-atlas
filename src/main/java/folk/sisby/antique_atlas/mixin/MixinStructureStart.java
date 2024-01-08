package folk.sisby.antique_atlas.mixin;

import folk.sisby.antique_atlas.structure.StructureHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public abstract class MixinStructureStart {
    @Final @Shadow private StructurePiecesList children;

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/structure/StructurePiece;generate(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/util/math/BlockPos;)V"))
    private void structurePieceGenerated(StructurePiece structurePiece, StructureWorldAccess serverWorldAccess, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, net.minecraft.util.math.random.Random random, BlockBox boundingBox, ChunkPos chunkPos, BlockPos blockPos) {
        ServerWorld world;

        if (serverWorldAccess instanceof ServerWorld) {
            world = (ServerWorld) serverWorldAccess;
        } else {
            world = ((ChunkRegion) serverWorldAccess).world;
        }

        StructureHandler.resolve(structurePiece, world);
        structurePiece.generate(serverWorldAccess, structureAccessor, chunkGenerator, random, boundingBox, chunkPos, blockPos);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void structureGenerated(StructureWorldAccess serverWorldAccess, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, net.minecraft.util.math.random.Random random, BlockBox chunkBox, ChunkPos chunkPos, CallbackInfo ci) {
        ServerWorld world;

        if (serverWorldAccess instanceof ServerWorld) {
            world = (ServerWorld) serverWorldAccess;
        } else {
            world = ((ChunkRegion) serverWorldAccess).world;
        }

        synchronized (this.children) {
            if (this.children.isEmpty()) return;

            StructureHandler.resolve((StructureStart) (Object) this, world);
        }
    }
}