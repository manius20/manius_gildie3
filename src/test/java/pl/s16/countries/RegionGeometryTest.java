package pl.s16.countries;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class RegionGeometryTest {
    private final UUID world = UUID.randomUUID();
    @Test void aOneBlockRegionBlocksWholeTouchedChunk() {
        RegionStore.Region r = new RegionStore.Region("spawn", world, "world", "BLOCKS", 15, 15, 15, 15);
        assertTrue(r.intersectsChunk(new ClaimKey(world,0,0)));
        assertFalse(r.intersectsChunk(new ClaimKey(world,1,0)));
    }
    @Test void negativeChunkCoordinatesHaveCorrectBounds() {
        RegionStore.Region r = new RegionStore.Region("spawn", world, "world", "BLOCKS", -1, 0, -1, 0);
        assertTrue(r.intersectsChunk(new ClaimKey(world,-1,-1)));
        assertTrue(r.intersectsChunk(new ClaimKey(world,0,0)));
        assertFalse(r.intersectsChunk(new ClaimKey(world,1,1)));
        assertEquals(4, RegionStore.chunkArea(r));
    }
    @Test void differentWorldCannotBeClaimBlocked() {
        RegionStore.Region r = new RegionStore.Region("spawn", world, "world", "CHUNKS",0,31,0,31);
        assertFalse(r.intersectsChunk(new ClaimKey(UUID.randomUUID(),0,0)));
    }
    @Test void overlappingRegionsAreDetected() {
        RegionStore.Region a = new RegionStore.Region("a",world,"world","BLOCKS",0,10,0,10);
        RegionStore.Region b = new RegionStore.Region("b",world,"world","BLOCKS",10,20,2,8);
        assertTrue(a.intersects(b));
    }
}
