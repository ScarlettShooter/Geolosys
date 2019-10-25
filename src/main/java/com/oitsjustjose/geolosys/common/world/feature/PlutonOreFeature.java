package com.oitsjustjose.geolosys.common.world.feature;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

import com.mojang.datafixers.Dynamic;
import com.oitsjustjose.geolosys.Geolosys;
import com.oitsjustjose.geolosys.api.BlockPosDim;
import com.oitsjustjose.geolosys.api.ChunkPosDim;
import com.oitsjustjose.geolosys.api.GeolosysAPI;
import com.oitsjustjose.geolosys.api.PlutonType;
import com.oitsjustjose.geolosys.api.world.DepositBiomeRestricted;
import com.oitsjustjose.geolosys.api.world.DepositMultiOreBiomeRestricted;
import com.oitsjustjose.geolosys.api.world.IDeposit;
import com.oitsjustjose.geolosys.common.blocks.SampleBlock;
import com.oitsjustjose.geolosys.common.config.CommonConfig;
import com.oitsjustjose.geolosys.common.utils.Utils;
import com.oitsjustjose.geolosys.common.world.SampleUtils;
import com.oitsjustjose.geolosys.common.world.capability.IGeolosysCapability;

import net.minecraft.block.BlockState;
import net.minecraft.block.IWaterLoggable;
import net.minecraft.state.IProperty;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IWorld;
import net.minecraft.world.WorldType;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;

public class PlutonOreFeature extends Feature<NoFeatureConfig>
{
    public PlutonOreFeature(Function<Dynamic<?>, ? extends NoFeatureConfig> configFactoryIn)
    {
        super(configFactoryIn, true);
    }

    /**
     * Post-pluton-placement logic to generate samples and more
     *
     * @param world          an IWorld instance
     * @param plutonStartPos The BlockPos from which the pluton originated
     * @param ore            The IDeposit that was generated
     */
    private void postPlacement(IWorld world, BlockPos plutonStartPos, IDeposit ore)
    {
        if (CommonConfig.DEBUG_WORLD_GEN.get())
        {
            Geolosys.getInstance().LOGGER.debug("Generated " + ore.getOre().getBlock().getRegistryName().toString()
                    + " in Chunk " + new ChunkPos(plutonStartPos));
        }

        IGeolosysCapability plutonCapability = world.getWorld().getCapability(GeolosysAPI.GEOLOSYS_WORLD_CAPABILITY)
                .orElse(null);
        if (plutonCapability != null)
        {

            // Random space between plutons as well
            plutonCapability.setOrePlutonGenerated(new ChunkPosDim(plutonStartPos.getX(), plutonStartPos.getZ(),
                    Utils.dimensionToString(world.getDimension())));

        }
        if (world.getWorld().getWorldType() != WorldType.FLAT)
        {
            int sampleLimit = SampleUtils.getSampleCount(ore);
            for (int i = 0; i < sampleLimit; i++)
            {
                BlockPos samplePos = SampleUtils.getSamplePosition(world, new ChunkPos(plutonStartPos), ore.getYMax());
                if (samplePos == null || SampleUtils.inNonWaterFluid(world, samplePos))
                {
                    continue;
                }
                if (world.getBlockState(samplePos) != ore.getSample())
                {
                    boolean isInWater = SampleUtils.isInWater(world, samplePos);
                    if (ore.getSample().getBlock() instanceof SampleBlock)
                    {
                        BlockState sampleState = isInWater ? ore.getSample().with(SampleBlock.WATERLOGGED, Boolean.TRUE)
                                : ore.getSample();
                        world.setBlockState(samplePos, sampleState, 2 | 16);
                    }
                    else
                    {
                        // Place a waterlogged variant of whatever block it ends up being
                        if (isInWater && ore.getSample().getBlock() instanceof IWaterLoggable)
                        {
                            ore.getSample().getProperties().forEach(x -> {
                                if (x.getName().toLowerCase().contains("waterlog")
                                        && x.getAllowedValues().contains(Boolean.TRUE))
                                {
                                    IProperty<Boolean> waterLogged = (IProperty<Boolean>) x;
                                    world.setBlockState(samplePos, ore.getSample().with(waterLogged, Boolean.TRUE),
                                            2 | 16);
                                }
                            });
                        }
                        else
                        {
                            world.setBlockState(samplePos, ore.getSample(), 2 | 16);
                        }
                    }
                }
            }
        }
    }

    private boolean isInChunk(ChunkPos chunkPos, BlockPos pos)
    {
        int blockX = pos.getX();
        int blockZ = pos.getZ();
        return blockX >= chunkPos.getXStart() && blockX <= chunkPos.getXEnd() && blockZ >= chunkPos.getZStart()
                && blockZ <= chunkPos.getZEnd();
    }

    private boolean isInChunk(ChunkPos chunkPos, BlockPosDim pos)
    {
        int blockX = pos.getX();
        int blockZ = pos.getZ();
        return blockX >= chunkPos.getXStart() && blockX <= chunkPos.getXEnd() && blockZ >= chunkPos.getZStart()
                && blockZ <= chunkPos.getZEnd();
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean place(IWorld worldIn, ChunkGenerator<? extends GenerationSettings> generator, Random rand,
            BlockPos pos, NoFeatureConfig config)
    {
        IGeolosysCapability plutonCapability = worldIn.getWorld().getCapability(GeolosysAPI.GEOLOSYS_WORLD_CAPABILITY)
                .orElse(null);
        // Fill in pending Blocks when possible:
        plutonCapability.getPendingBlocks().forEach((pPos, pState) -> {
            if (isInChunk(new ChunkPos(pos), pPos))
            {
                if (CommonConfig.DEBUG_WORLD_GEN.get())
                {
                    Geolosys.getInstance().LOGGER.info(
                            "Generated pending block " + pState.getBlock().getRegistryName().toString() + " at " + pos);
                }
                worldIn.setBlockState(pPos.getPos(), pState, 2 | 16);
                plutonCapability.getPendingBlocks().remove(pPos);
            }
        });

        ChunkPosDim chunkPosDim = new ChunkPosDim(pos, Utils.dimensionToString(worldIn.getDimension()));
        if (plutonCapability.hasOrePlutonGenerated(chunkPosDim))
        {
            return false;
        }
        IDeposit pluton = GeolosysAPI.plutonRegistry.pickPluton(worldIn, pos, rand);
        if (pluton == null)
        {
            return false;
        }
        if (rand.nextInt(100) > pluton.getChance())
        {
            return false;
        }
        // Logic to confirm that this can be placed here
        if (pluton instanceof DepositBiomeRestricted)
        {
            DepositBiomeRestricted restricted = (DepositBiomeRestricted) pluton;
            if (!restricted.canPlaceInBiome(worldIn.getBiome(pos)))
            {
                return false;
            }
        }
        else if (pluton instanceof DepositMultiOreBiomeRestricted)
        {
            DepositMultiOreBiomeRestricted restricted = (DepositMultiOreBiomeRestricted) pluton;
            if (!restricted.canPlaceInBiome(worldIn.getBiome(pos)))
            {
                return false;
            }
        }

        // New way of determining if the dimension is valid for generation
        // Much quicker to use parallel streams than a for-loop, especially if in a large modpack
        List<DimensionType> dimTypes = Arrays.stream(pluton.getDimensionBlacklist()).parallel()
                .map(x -> DimensionType.byName(new ResourceLocation(x))).collect(Collectors.toList());

        if (dimTypes.contains(worldIn.getDimension().getType()))
        {
            return false;
        }

        boolean placed = false;

        if (pluton.getPlutonType() == PlutonType.DENSE)
        {
            placed = generateDense(worldIn, pos, rand, pluton, plutonCapability);
        }
        else if (pluton.getPlutonType() == PlutonType.SPARSE)
        {
            placed = generateSparse(worldIn, pos, rand, pluton, plutonCapability);
        }
        else if (pluton.getPlutonType() == PlutonType.DIKE)
        {
            placed = generateDike(worldIn, pos, rand, pluton, plutonCapability);
        }
        else if (pluton.getPlutonType() == PlutonType.LAYER)
        {
            placed = generateLayer(worldIn, pos, rand, pluton, plutonCapability);
        }
        else
        {
            Geolosys.getInstance().LOGGER.error("Unknown Generation Logic for PlutonType {}", pluton.getPlutonType());
        }

        if (placed)
        {
            this.postPlacement(worldIn, pos, pluton);
        }

        return placed;
    }

    private boolean generateLayer(IWorld worldIn, BlockPos pos, Random rand, IDeposit pluton,
            IGeolosysCapability plutonCapability)
    {
        ChunkPos thisChunk = new ChunkPos(pos);
        boolean placed = false;

        int x = ((thisChunk.getXStart() + thisChunk.getXEnd()) / 2) - rand.nextInt(8) + rand.nextInt(16);
        int y = pluton.getYMin() + rand.nextInt(Math.abs(pluton.getYMax() - pluton.getYMin()));
        int z = ((thisChunk.getZStart() + thisChunk.getZEnd()) / 2) - rand.nextInt(8) + rand.nextInt(16);

        BlockPos basePos = new BlockPos(x, y, z);

        int radius = pluton.getSize() / 2;
        int depth = rand.nextBoolean() ? 1 : 2;

        for (int dX = -radius; dX <= radius; dX++)
        {
            for (int dZ = -radius; dZ <= radius; dZ++)
            {
                for (int dY = 0; dY < depth; dY++)
                {
                    float dist = (dX * dX) + (dZ * dZ);
                    if (dist > radius)
                    {
                        continue;
                    }

                    BlockPos blockpos = basePos.add(dX, dY, dZ);

                    if (isInChunk(thisChunk, blockpos) || worldIn.chunkExists(x >> 4, z >> 4))
                    {
                        float density = Math.min(pluton.getDensity(), 1.0F);

                        if (rand.nextFloat() > density)
                        {
                            continue;
                        }
                        BlockState state = worldIn.getBlockState(blockpos);
                        for (BlockState matcherState : (pluton.getBlockStateMatchers() == null
                                ? Utils.getDefaultMatchers()
                                : pluton.getBlockStateMatchers()))
                        {
                            if (Utils.doStatesMatch(matcherState, state))
                            {
                                worldIn.setBlockState(blockpos, pluton.getOre(), 2 | 16);
                                placed = true;
                                break;
                            }
                        }
                    }
                    else
                    {
                        plutonCapability.putPendingBlock(
                                new BlockPosDim(pos, Utils.dimensionToString(worldIn.getDimension())), pluton.getOre());
                    }
                }
            }
        }

        return placed;

    }

    private boolean generateDike(IWorld worldIn, BlockPos pos, Random rand, IDeposit pluton,
            IGeolosysCapability plutonCapability)
    {
        ChunkPos thisChunk = new ChunkPos(pos);

        boolean placed = false;
        int xStart = pos.getX() + rand.nextInt(16);
        int zStart = pos.getZ() + rand.nextInt(16);
        int yStart = pluton.getYMin();
        int yMod = Math.abs((pluton.getYMax() - pluton.getYMin())) / (2 + rand.nextInt(2));
        int yEnd = yStart + yMod;

        int radius = 4 + rand.nextInt(3);

        int maxItersSinceOffset = 3 + rand.nextInt(3);
        int itersSinceOffset = 0;

        for (int y = yStart; y < yEnd; y++)
        {
            if (itersSinceOffset >= maxItersSinceOffset)
            {
                if (rand.nextBoolean())
                {
                    xStart++;
                }
                else
                {
                    zStart++;
                }
                maxItersSinceOffset = 3 + rand.nextInt(3);
                itersSinceOffset = 0;
                // Decrease the radius incrementally to turn it into more of a spire
                // If the "spire" gets too small we should quit.
                radius--;
                if (radius == 0)
                {
                    return placed;
                }
            }
            for (int x = xStart; x < xStart + radius; x++)
            {
                for (int z = zStart; z < zStart + radius; z++)
                {
                    BlockPos blockpos = new BlockPos(x, y, z);
                    if (isInChunk(thisChunk, blockpos) || worldIn.chunkExists(x >> 4, z >> 4))
                    {
                        float density = Math.min(pluton.getDensity(), 1.0F);

                        if (rand.nextFloat() > density)
                        {
                            continue;
                        }
                        BlockState state = worldIn.getBlockState(blockpos);
                        for (BlockState matcherState : (pluton.getBlockStateMatchers() == null
                                ? Utils.getDefaultMatchers()
                                : pluton.getBlockStateMatchers()))
                        {
                            if (Utils.doStatesMatch(matcherState, state))
                            {
                                worldIn.setBlockState(blockpos, pluton.getOre(), 2 | 16);
                                placed = true;
                                break;
                            }
                        }
                    }
                    else
                    {
                        plutonCapability.putPendingBlock(
                                new BlockPosDim(pos, Utils.dimensionToString(worldIn.getDimension())), pluton.getOre());
                    }
                }
            }
            itersSinceOffset++;
        }

        return placed;
    }

    private boolean generateSparse(IWorld worldIn, BlockPos pos, Random rand, IDeposit pluton,
            IGeolosysCapability plutonCapability)
    {
        ChunkPos thisChunk = new ChunkPos(pos);

        boolean placed = false;

        for (int y = pluton.getYMin(); y < pluton.getYMax(); y++)
        {
            int numAttempts = rand.nextInt(pluton.getSize() / 4);
            for (int attempt = 0; attempt < numAttempts; attempt++)
            {
                int x = pos.getX() + rand.nextInt(16);
                int z = pos.getZ() + rand.nextInt(16);
                BlockPos blockpos = new BlockPos(x, y, z);
                if (isInChunk(thisChunk, blockpos) || worldIn.chunkExists(x >> 4, z >> 4))
                {
                    float density = Math.min(pluton.getDensity(), 1.0F);

                    if (rand.nextFloat() > density)
                    {
                        continue;
                    }
                    BlockState state = worldIn.getBlockState(blockpos);
                    for (BlockState matcherState : (pluton.getBlockStateMatchers() == null ? Utils.getDefaultMatchers()
                            : pluton.getBlockStateMatchers()))
                    {
                        if (Utils.doStatesMatch(matcherState, state))
                        {
                            worldIn.setBlockState(blockpos, pluton.getOre(), 2 | 16);
                            placed = true;
                            break;
                        }
                    }
                }
                else
                {
                    plutonCapability.putPendingBlock(
                            new BlockPosDim(pos, Utils.dimensionToString(worldIn.getDimension())), pluton.getOre());
                }
            }
        }

        return placed;

    }

    private boolean generateDense(IWorld worldIn, BlockPos pos, Random rand, IDeposit pluton,
            IGeolosysCapability plutonCapability)
    {
        // Do this ourselves because by default pos.getY() == 0
        int randY = pluton.getYMin() + rand.nextInt(pluton.getYMax() - pluton.getYMin());

        float f = rand.nextFloat() * (float) Math.PI;
        double d0 = (float) (pos.getX() + 8) + MathHelper.sin(f) * (float) pluton.getSize() / 8.0F;
        double d1 = (float) (pos.getX() + 8) - MathHelper.sin(f) * (float) pluton.getSize() / 8.0F;
        double d2 = (float) (pos.getZ() + 8) + MathHelper.cos(f) * (float) pluton.getSize() / 8.0F;
        double d3 = (float) (pos.getZ() + 8) - MathHelper.cos(f) * (float) pluton.getSize() / 8.0F;
        double d4 = randY + rand.nextInt(3) - 2;
        double d5 = randY + rand.nextInt(3) - 2;

        ChunkPos thisChunk = new ChunkPos(pos);
        boolean placed = false;

        for (int i = 0; i < pluton.getSize(); ++i)
        {
            float f1 = (float) i / (float) pluton.getSize();
            double d6 = d0 + (d1 - d0) * (double) f1;
            double d7 = d4 + (d5 - d4) * (double) f1;
            double d8 = d2 + (d3 - d2) * (double) f1;
            double d9 = rand.nextDouble() * (double) pluton.getSize() / 16.0D;
            double d10 = (double) (MathHelper.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0D;
            double d11 = (double) (MathHelper.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0D;
            int j = MathHelper.floor(d6 - d10 / 2.0D);
            int k = MathHelper.floor(d7 - d11 / 2.0D);
            int l = MathHelper.floor(d8 - d10 / 2.0D);
            int i1 = MathHelper.floor(d6 + d10 / 2.0D);
            int j1 = MathHelper.floor(d7 + d11 / 2.0D);
            int k1 = MathHelper.floor(d8 + d10 / 2.0D);

            for (int l1 = j; l1 <= i1; ++l1)
            {
                double d12 = ((double) l1 + 0.5D - d6) / (d10 / 2.0D);

                if (d12 * d12 < 1.0D)
                {
                    for (int i2 = k; i2 <= j1; ++i2)
                    {
                        double d13 = ((double) i2 + 0.5D - d7) / (d11 / 2.0D);

                        if (d12 * d12 + d13 * d13 < 1.0D)
                        {
                            for (int j2 = l; j2 <= k1; ++j2)
                            {
                                double d14 = ((double) j2 + 0.5D - d8) / (d10 / 2.0D);

                                if (d12 * d12 + d13 * d13 + d14 * d14 < 1.0D)
                                {
                                    BlockPos blockpos = new BlockPos(l1, i2, j2);

                                    if (isInChunk(thisChunk, blockpos) || worldIn.chunkExists(l1 >> 4, j2 >> 4))
                                    {
                                        float density = Math.min(pluton.getDensity(), 1.0F);

                                        if (rand.nextFloat() > density)
                                        {
                                            continue;
                                        }
                                        BlockState state = worldIn.getBlockState(blockpos);
                                        for (BlockState matcherState : (pluton.getBlockStateMatchers() == null
                                                ? Utils.getDefaultMatchers()
                                                : pluton.getBlockStateMatchers()))
                                        {
                                            if (Utils.doStatesMatch(matcherState, state))
                                            {
                                                worldIn.setBlockState(blockpos, pluton.getOre(), 2 | 16);
                                                placed = true;
                                                break;
                                            }
                                        }
                                    }
                                    else
                                    {
                                        plutonCapability.putPendingBlock(
                                                new BlockPosDim(pos, Utils.dimensionToString(worldIn.getDimension())),
                                                pluton.getOre());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return placed;
    }
}