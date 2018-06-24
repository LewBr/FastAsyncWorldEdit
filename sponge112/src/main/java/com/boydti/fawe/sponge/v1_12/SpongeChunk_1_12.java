package com.boydti.fawe.sponge.v1_12;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.example.CharFaweChunk;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.MathMan;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.DoubleTag;
import com.sk89q.jnbt.FloatTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BitArray;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.BlockStatePaletteRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IBlockStatePalette;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class SpongeChunk_1_12 extends CharFaweChunk<Chunk, SpongeQueue_1_12> {

    public BlockStateContainer[] sectionPalettes;

    public static Map<String, ResourceLocation> entityKeys;

    /**
     * A FaweSections object represents a chunk and the blocks that you wish to change in it.
     *
     * @param parent
     * @param x
     * @param z
     */
    public SpongeChunk_1_12(FaweQueue parent, int x, int z) {
        super(parent, x, z);
    }

    public SpongeChunk_1_12(FaweQueue parent, int x, int z, char[][] ids, short[] count, short[] air, byte[] heightMap) {
        super(parent, x, z, ids, count, air, heightMap);
    }

    @Override
    public CharFaweChunk copy(boolean shallow) {
        SpongeChunk_1_12 copy;
        if (shallow) {
            copy = new SpongeChunk_1_12(getParent(), getX(), getZ(), ids, count, air, heightMap);
            copy.biomes = biomes;
            copy.chunk = chunk;
        } else {
            copy = new SpongeChunk_1_12(getParent(), getX(), getZ(), (char[][]) MainUtil.copyNd(ids), count.clone(), air.clone(), heightMap.clone());
            copy.biomes = biomes;
            copy.chunk = chunk;
            copy.biomes = biomes.clone();
            copy.chunk = chunk;
        }
        if (sectionPalettes != null) {
            copy.sectionPalettes = new BlockStateContainer[16];
            try {
                Field fieldBits = BlockStateContainer.class.getDeclaredField("field_186021_b");
                fieldBits.setAccessible(true);
                Field fieldPalette = BlockStateContainer.class.getDeclaredField("field_186022_c");
                fieldPalette.setAccessible(true);
                Field fieldSize = BlockStateContainer.class.getDeclaredField("field_186024_e");
                fieldSize.setAccessible(true);
                for (int i = 0; i < sectionPalettes.length; i++) {
                    BlockStateContainer current = sectionPalettes[i];
                    if (current == null) {
                        continue;
                    }
                    // Clone palette
                    IBlockStatePalette currentPalette = (IBlockStatePalette) fieldPalette.get(current);
                    if (!(currentPalette instanceof BlockStatePaletteRegistry)) {
                        current.onResize(128, null);
                    }
                    BlockStateContainer paletteBlock = new BlockStateContainer();
                    currentPalette = (IBlockStatePalette) fieldPalette.get(current);
                    if (!(currentPalette instanceof BlockStatePaletteRegistry)) {
                        throw new RuntimeException("Palette must be global!");
                    }
                    fieldPalette.set(paletteBlock, currentPalette);
                    // Clone size
                    fieldSize.set(paletteBlock, fieldSize.get(current));
                    // Clone palette
                    BitArray currentBits = (BitArray) fieldBits.get(current);
                    BitArray newBits = new BitArray(1, 0);
                    for (Field field : BitArray.class.getDeclaredFields()) {
                        field.setAccessible(true);
                        Object currentValue = field.get(currentBits);
                        if (currentValue instanceof long[]) {
                            currentValue = ((long[]) currentValue).clone();
                        }
                        field.set(newBits, currentValue);
                    }
                    fieldBits.set(paletteBlock, newBits);
                    copy.sectionPalettes[i] = paletteBlock;
                }
            } catch (Throwable e) {
                MainUtil.handleError(e);
            }
        }
        return copy;
    }

    @Override
    public Chunk getNewChunk() {
        World world = ((SpongeQueue_1_12) getParent()).getWorld();
        return world.getChunkProvider().provideChunk(getX(), getZ());
    }

    public void optimize() {
        if (sectionPalettes != null) {
            return;
        }
        char[][] arrays = getCombinedIdArrays();
        char lastChar = Character.MAX_VALUE;
        for (int layer = 0; layer < 16; layer++) {
            if (getCount(layer) > 0) {
                if (sectionPalettes == null) {
                    sectionPalettes = new BlockStateContainer[16];
                }
                BlockStateContainer palette = new BlockStateContainer();
                char[] blocks = getIdArray(layer);
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            char combinedId = blocks[FaweCache.CACHE_J[y][z][x]];
                            if (combinedId > 1) {
                                palette.set(x, y, z, Block.getBlockById(combinedId >> 4).getStateFromMeta(combinedId & 0xF));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public SpongeChunk_1_12 call() {
        net.minecraft.world.chunk.Chunk nmsChunk = this.getChunk();
        int bx = this.getX() << 4;
        int bz = this.getZ() << 4;
        nmsChunk.setModified(true);
        net.minecraft.world.World nmsWorld = getParent().getWorld();
        try {
            boolean flag = nmsWorld.provider.hasSkyLight();
            // Sections
            ExtendedBlockStorage[] sections = nmsChunk.getBlockStorageArray();
            Map<BlockPos, TileEntity> tiles = nmsChunk.getTileEntityMap();
            ClassInheritanceMultiMap<Entity>[] entities = nmsChunk.getEntityLists();

            // Set heightmap
            getParent().setHeightMap(this, heightMap);

            // Remove entities
            for (int i = 0; i < 16; i++) {
                int count = this.getCount(i);
                if (count == 0) {
                    continue;
                } else if (count >= 4096) {
                    Collection<Entity> ents = entities[i];
                    if (!ents.isEmpty()) {
                        synchronized (SpongeChunk_1_12.class) {
                            entities[i] = new ClassInheritanceMultiMap<>(Entity.class);
                        }
                    }
                } else if (!getParent().getSettings().EXPERIMENTAL.KEEP_ENTITIES_IN_BLOCKS) {
                    char[] array = this.getIdArray(i);
                    Collection<Entity> ents = new ArrayList<>(entities[i]);
                    synchronized (SpongeChunk_1_12.class) {
                        for (Entity entity : ents) {
                            if (entity instanceof EntityPlayer) {
                                continue;
                            }
                            int x = (MathMan.roundInt(entity.posX) & 15);
                            int z = (MathMan.roundInt(entity.posZ) & 15);
                            int y = MathMan.roundInt(entity.posY);
                            if (array == null) {
                                continue;
                            }
                            if (y < 0 || y > 255 || array[FaweCache.CACHE_J[y][z][x]] != 0) {
                                nmsWorld.removeEntity(entity);
                            }
                        }
                    }
                }
            }
            // Set entities
            Set<UUID> createdEntities = new HashSet<>();
            Set<CompoundTag> entitiesToSpawn = this.getEntities();
            if (!entitiesToSpawn.isEmpty()) {
                synchronized (SpongeChunk_1_12.class) {
                    for (CompoundTag nativeTag : entitiesToSpawn) {
                        Map<String, Tag> entityTagMap = nativeTag.getValue();
                        StringTag idTag = (StringTag) entityTagMap.get("Id");
                        ListTag posTag = (ListTag) entityTagMap.get("Pos");
                        ListTag rotTag = (ListTag) entityTagMap.get("Rotation");
                        if (idTag == null || posTag == null || rotTag == null) {
                            Fawe.debug("Unknown entity tag: " + nativeTag);
                            continue;
                        }
                        List<Tag> value = posTag.getValue();
                        double x = ((DoubleTag) value.get(0)).getValue();
                        double y = ((DoubleTag) value.get(1)).getValue();
                        double z = ((DoubleTag) value.get(2)).getValue();
                        value = rotTag.getValue();
                        float yaw = ((FloatTag) value.get(0)).getValue();
                        float pitch = ((FloatTag) value.get(1)).getValue();
                        String id = idTag.getValue();
                        if (entityKeys == null) {
                            entityKeys = new HashMap<>();
                            for (ResourceLocation key : EntityList.getEntityNameList()) {
                                String currentId = EntityList.getTranslationName(key);
                                entityKeys.put(currentId, key);
                                entityKeys.put(key.getResourcePath(), key);
                            }
                        }
                        ResourceLocation entityKey = entityKeys.get(id);
                        if (entityKey != null) {
                            Entity entity = EntityList.createEntityByIDFromName(entityKey, nmsWorld);
                            if (entity != null) {
                                NBTTagCompound tag = (NBTTagCompound) SpongeQueue_1_12.methodFromNative.invoke(SpongeQueue_1_12.adapter, nativeTag);
                                entity.readFromNBT(tag);
                                tag.removeTag("UUIDMost");
                                tag.removeTag("UUIDLeast");
                                entity.setPositionAndRotation(x, y, z, yaw, pitch);
                                nmsWorld.spawnEntity(entity);
                            }
                        }
                    }
                }
            }
            // Run change task if applicable
            if (getParent().getChangeTask() != null) {
                CharFaweChunk previous = getParent().getPrevious(this, sections, tiles, entities, createdEntities, false);
                getParent().getChangeTask().run(previous, this);
            }
            // Trim tiles
            if (!tiles.isEmpty()) {
                synchronized (SpongeChunk_1_12.class) {
                    List<TileEntity> invalidate = null;
                    Set<Map.Entry<BlockPos, TileEntity>> entryset = tiles.entrySet();
                    Iterator<Map.Entry<BlockPos, TileEntity>> iterator = entryset.iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<BlockPos, TileEntity> tile = iterator.next();
                        BlockPos pos = tile.getKey();
                        int lx = pos.getX() & 15;
                        int ly = pos.getY();
                        int lz = pos.getZ() & 15;
                        int j = FaweCache.CACHE_I[ly][lz][lx];
                        char[] array = this.getIdArray(j);
                        if (array == null) {
                            continue;
                        }
                        int k = FaweCache.CACHE_J[ly][lz][lx];
                        if (array[k] != 0) {
                            iterator.remove();
                            if (invalidate == null) invalidate = new ArrayList<>();
                            invalidate.add(tile.getValue());
                        }
                    }
                    if (invalidate != null) {
                        for (TileEntity tile : invalidate) {
                            tile.invalidate();
                        }
                    }
                }
            }
            HashSet<UUID> entsToRemove = this.getEntityRemoves();
            if (!entsToRemove.isEmpty()) {
                synchronized (SpongeChunk_1_12.class) {
                    for (int i = 0; i < entities.length; i++) {
                        Collection<Entity> ents = new ArrayList<>(entities[i]);
                        for (Entity entity : ents) {
                            if (entsToRemove.contains(entity.getUniqueID())) {
                                nmsWorld.removeEntity(entity);
                            }
                        }
                    }
                }
            }
            // Efficiently merge sections
            for (int j = 0; j < sections.length; j++) {
                int count = this.getCount(j);
                if (count == 0) {
                    continue;
                }
                final char[] array = this.getIdArray(j);
                if (array == null) {
                    continue;
                }
                int countAir = this.getAir(j);
                ExtendedBlockStorage section = sections[j];
                if (section == null) {
                    if (count == countAir) {
                        continue;
                    }
                    if (this.sectionPalettes != null && this.sectionPalettes[j] != null) {
                        section = sections[j] = new ExtendedBlockStorage(j << 4, flag);
                        getParent().setPalette(section, this.sectionPalettes[j]);
                        getParent().setCount(0, count - this.getAir(j), section);
                        continue;
                    } else {
                        sections[j] = section = new ExtendedBlockStorage(j << 4, flag);
                    }
                } else if (count >= 4096) {
                    if (count == countAir) {
                        sections[j] = null;
                        continue;
                    }
                    if (this.sectionPalettes != null && this.sectionPalettes[j] != null) {
                        getParent().setPalette(section, this.sectionPalettes[j]);
                        getParent().setCount(0, count - this.getAir(j), section);
                        continue;
                    } else {
                        sections[j] = section = new ExtendedBlockStorage(j << 4, flag);
                    }
                }
                IBlockState existing;
                int by = j << 4;
                BlockStateContainer nibble = section.getData();
                int nonEmptyBlockCount = 0;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            char combinedId = array[FaweCache.CACHE_J[y][z][x]];
                            switch (combinedId) {
                                case 0:
                                    continue;
                                case 1:
                                    existing = nibble.get(x, y, z);
                                    if (existing != SpongeQueue_1_12.air) {
                                        if (existing.getLightValue() > 0) {
                                            getParent().getRelighter().addLightUpdate(bx + x, by + y, bz + z);
                                        }
                                        nonEmptyBlockCount--;
                                    }
                                    nibble.set(x, y, z, SpongeQueue_1_12.air);
                                    continue;
                                default:
                                    existing = nibble.get(x, y, z);
                                    if (existing != SpongeQueue_1_12.air) {
                                        if (existing.getLightValue() > 0) {
                                            getParent().getRelighter().addLightUpdate(bx + x, by + y, bz + z);
                                        }
                                    } else {
                                        nonEmptyBlockCount++;
                                    }
                                    nibble.set(x, y, z, Block.getBlockById(combinedId >> 4).getStateFromMeta(combinedId & 0xF));
                            }
                        }
                    }
                }
                getParent().setCount(0, getParent().getNonEmptyBlockCount(section) + nonEmptyBlockCount, section);
            }
            // Set biomes
            if (this.biomes != null) {
                byte[] currentBiomes = nmsChunk.getBiomeArray();
                for (int i = 0 ; i < this.biomes.length; i++) {
                    byte biome = this.biomes[i];
                    if (biome != 0) {
                        if (biome == -1) biome = 0;
                        currentBiomes[i] = biome;
                    }
                }
            }
            // Set tiles
            Map<Short, CompoundTag> tilesToSpawn = this.getTiles();

            if (!tilesToSpawn.isEmpty()) {
                synchronized (SpongeChunk_1_12.class) {
                    for (Map.Entry<Short, CompoundTag> entry : tilesToSpawn.entrySet()) {
                        CompoundTag nativeTag = entry.getValue();
                        short blockHash = entry.getKey();
                        int x = (blockHash >> 12 & 0xF) + bx;
                        int y = (blockHash & 0xFF);
                        int z = (blockHash >> 8 & 0xF) + bz;
                        BlockPos pos = new BlockPos(x, y, z); // Set pos
                        TileEntity tileEntity = nmsWorld.getTileEntity(pos);
                        if (tileEntity != null) {
                            NBTTagCompound tag = (NBTTagCompound) SpongeQueue_1_12.methodFromNative.invoke(SpongeQueue_1_12.adapter, nativeTag);
                            tag.setInteger("x", pos.getX());
                            tag.setInteger("y", pos.getY());
                            tag.setInteger("z", pos.getZ());
                            tileEntity.readFromNBT(tag); // ReadTagIntoTile
                        }
                    }
                }
            }
        } catch (Throwable e) {
            MainUtil.handleError(e);
        }
        return this;
    }
}
