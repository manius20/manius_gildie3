package pl.s16.countries;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NationTopologyTest {
    private final UUID world=UUID.randomUUID();
    private ClaimKey at(int x,int z) {return new ClaimKey(world,x,z);}
    private Nation fresh() {return new Nation("Test",UUID.randomUUID(),"prezydent",false);}

    @Test void cardinalNeighborsOnly() {
        Nation n=fresh();
        n.claims.add(at(0,0));
        assertTrue(n.touches(at(1,0)));
        assertTrue(n.touches(at(0,-1)));
        assertFalse(n.touches(at(1,1)));
        assertFalse(n.touches(new ClaimKey(UUID.randomUUID(),1,0)));
    }
    @Test void removalOfBridgeForbidden() {
        Nation n=fresh();
        n.claims.add(at(0,0));n.claims.add(at(1,0));n.claims.add(at(2,0));
        assertFalse(n.remainsConnectedWithout(at(1,0)));
        assertTrue(n.remainsConnectedWithout(at(0,0)));
    }
    @Test void removeFromLoopAllowed() {
        Nation n=fresh();
        n.claims.add(at(0,0));n.claims.add(at(1,0));n.claims.add(at(1,1));n.claims.add(at(0,1));
        assertTrue(n.remainsConnectedWithout(at(1,0)));
    }
    @Test void keyRoundtripAndNegatives() {
        assertEquals(at(-120,500),ClaimKey.parse(at(-120,500).serialize()));
    }
}
