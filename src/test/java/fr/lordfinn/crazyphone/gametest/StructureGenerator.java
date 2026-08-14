package fr.lordfinn.crazyphone.gametest;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One-off generator for the minimal GameTest platform structure (a 3x1x3 stone floor with 2 blocks of air
 * headroom above), hand-built from vanilla's stable structure-NBT schema (DataVersion/size/entities/blocks/
 * palette) rather than requiring an in-game "/structure save" (not available without a display). Run once
 * via `gradlew test --tests StructureGenerator` after any change, then re-disable - this is a build-time
 * asset generation step, not a real ongoing test.
 *
 * The relative output path below resolves against the test JVM's own working directory (NeoForge's
 * moddev test setup runs it from build/minecraft-junit/, not the project root) - after running, copy
 * build/minecraft-junit/src/main/resources/data/crazyphone/structure/platform.nbt over the real one at
 * src/main/resources/data/crazyphone/structure/platform.nbt.
 */
class StructureGenerator {

    @Test
    @Disabled("one-off generator - re-enable, run once, re-disable after regenerating the structure")
    void generatePlatformStructure() throws IOException {
        CompoundTag root = new CompoundTag();
        //? if <1.21.10 {
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        //? } else {
        /*root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        *///?}
        root.put("size", new IntArrayTag(new int[]{3, 3, 3}));
        root.put("entities", new ListTag());

        ListTag palette = new ListTag();
        CompoundTag stoneEntry = new CompoundTag();
        stoneEntry.putString("Name", "minecraft:stone");
        palette.add(stoneEntry);
        CompoundTag airEntry = new CompoundTag();
        airEntry.putString("Name", "minecraft:air");
        palette.add(airEntry);
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(blockEntry(0, x, 0, z)); // y=0: stone floor
                blocks.add(blockEntry(1, x, 1, z)); // y=1: air (player headroom)
                blocks.add(blockEntry(1, x, 2, z)); // y=2: air (more headroom)
            }
        }
        root.put("blocks", blocks);

        File out = new File("src/main/resources/data/crazyphone/structure/platform.nbt");
        out.getParentFile().mkdirs();
        NbtIo.writeCompressed(root, out.toPath());

        // Round-trip check right here, so a malformed file is caught at generation time, not the next time
        // gradlew runGameTestServer tries (and fails) to load it.
        CompoundTag reread = NbtIo.readCompressed(out.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        assertEquals(3, ((IntArrayTag) reread.get("size")).getAsIntArray()[0]);
        assertEquals(27, ((ListTag) reread.get("blocks")).size()); // 3x3 footprint * 3 y-layers
    }

    private static CompoundTag blockEntry(int paletteIndex, int x, int y, int z) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("state", paletteIndex);
        entry.put("pos", new IntArrayTag(new int[]{x, y, z}));
        return entry;
    }
}
