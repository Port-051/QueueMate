package com.queuemate.common.social;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidOrderTest {

    /** PostgreSQL은 uuid를 바이트 순으로 본다. Java는 long을 부호 있는 값으로 본다. */
    @Test
    void ordersByUnsignedBytesUnlikeJavaCompareTo() {
        UUID zeros = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID effs = UUID.fromString("ffffffff-0000-4000-8000-000000000001");

        assertTrue(effs.compareTo(zeros) < 0, "전제가 깨졌다: Java 비교가 바뀌었다");
        assertTrue(UuidOrder.compare(zeros, effs) < 0);
        assertEquals(zeros, UuidOrder.lower(zeros, effs));
        assertEquals(effs, UuidOrder.higher(zeros, effs));
    }

    @Test
    void ordersByLeastSignificantBitsWhenHighHalvesMatch() {
        UUID first = UUID.fromString("11111111-1111-4111-8111-000000000001");
        UUID second = UUID.fromString("11111111-1111-4111-8111-000000000002");

        assertTrue(UuidOrder.compare(first, second) < 0);
    }

    @Test
    void isSymmetricAndStableForRandomPairs() {
        for (int i = 0; i < 500; i++) {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            assertEquals(-Integer.signum(UuidOrder.compare(a, b)), Integer.signum(UuidOrder.compare(b, a)));
            // lower/higher는 인자 순서와 무관하게 같은 답을 준다.
            assertEquals(UuidOrder.lower(a, b), UuidOrder.lower(b, a));
            assertEquals(UuidOrder.higher(a, b), UuidOrder.higher(b, a));
        }
    }

    @Test
    void treatsIdenticalValuesAsEqual() {
        UUID id = UUID.randomUUID();

        assertEquals(0, UuidOrder.compare(id, id));
    }
}
