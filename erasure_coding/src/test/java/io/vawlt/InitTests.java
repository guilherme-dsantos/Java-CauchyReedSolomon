package io.vawlt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InitTests {

  @BeforeEach
  void setUp() {
    GF256.polynomial = 0;
    Cauchy256.gf256Init = false;
  }

  @Test
  @DisplayName("Test constructor initializes tables correctly")
  void testConstructor() {
    // Verify the static tables are properly sized
    assertNotNull(GF256.gf256MulTable);
    assertEquals(256, GF256.gf256MulTable.length);
    assertEquals(256, GF256.gf256MulTable[0].length);

    assertNotNull(GF256.gf256DivTable);
    assertEquals(256, GF256.gf256DivTable.length);
    assertEquals(256, GF256.gf256DivTable[0].length);

    assertNotNull(GF256.gf256InvTable);
    assertEquals(256, GF256.gf256InvTable.length);

    assertNotNull(GF256.gf256LogTable);
    assertEquals(256, GF256.gf256LogTable.length);

    assertNotNull(GF256.gf256ExpTable);
    assertEquals(512 * 2 + 1, GF256.gf256ExpTable.length);
  }

  @Test
  @DisplayName("Test initialization process completes successfully")
  void testInitSuccess() {
    assertDoesNotThrow(Cauchy256::init);

    // Verify context was created
    assertTrue(Cauchy256.gf256Init);

    // Verify polynomial initialization
    GF256.polyInit(3); // Test with valid polynomial index
    assertEquals((GF256.GF256_GEN_POLY[3] << 1) | 1, GF256.polynomial);

    GF256.polyInit(0); // Test with different polynomial index
    assertEquals((GF256.GF256_GEN_POLY[0] << 1) | 1, GF256.polynomial);

    GF256.polyInit(-1); // Test with out-of-range index (should use DEFAULT_POLYNOMIAL_INDEX)
    assertEquals((GF256.GF256_GEN_POLY[GF256.DEFAULT_POLYNOMIAL_INDEX] << 1) | 1, GF256.polynomial);

    GF256.polyInit(16);
    assertEquals((GF256.GF256_GEN_POLY[GF256.DEFAULT_POLYNOMIAL_INDEX] << 1) | 1, GF256.polynomial);

    // Verify exp/log table initialization
    int[] expTable = GF256.gf256ExpTable;
    int[] logTable = GF256.gf256LogTable;

    assertEquals(512, logTable[0]); // Check log(0) is 512
    assertEquals(1, expTable[0]); // Check exp(0) = 1
    assertEquals(expTable[0], expTable[255]); // Check exp(255) = exp(0) = 1

    for (int i = 1; i < 256; i++) { // Check the property that exp(log(x)) = x for non-zero x
      int logValue = logTable[i] & 0xFFFF;
      int expValue = expTable[logValue] & 0xFF;
      assertEquals(i, expValue, "exp(log(" + i + ")) should equal " + i);
    }

    // Verify mult/div table initialization
    int[][] mulTable = GF256.gf256MulTable;
    int[][] divTable = GF256.gf256DivTable;

    for (int x = 0; x < 256; x++) { // Check y=0 row in mul table (everything * 0 = 0)
      assertEquals(0, mulTable[0][x]);
    }

    for (int y = 0; y < 256; y++) { // Check x=0 column in mul table (0 * everything = 0)
      assertEquals(0, mulTable[y][0]);
    }

    for (int x = 0; x < 256; x++) { // Check identity: x * 1 = x
      assertEquals(x, mulTable[1][x]);
    }

    for (int x = 1; x < 256; x++) { // Check division by same number = 1 (for non-zero numbers)
      assertEquals(1, divTable[x][x]);
    }

    // Verify inv table initialization
    int[] invTable = GF256.gf256InvTable;

    assertEquals(1, invTable[1]); // Inverse of 1 is 1

    for (int x = 1; x < 256; x++) { // x * inv(x) = 1
      byte result = GF256.mul((byte) x, (byte) invTable[x]);
      assertEquals(1, result & 0xFF, "x * inv(x) should be 1 for x = " + x);
    }

    for (int x = 1; x < 256; x++) { // Inverse of inverse is the original number
      int invX = invTable[x] & 0xFF;
      int invInvX = invTable[invX] & 0xFF;
      assertEquals(x, invInvX, "inv(inv(" + x + ")) should equal " + x);
    }
  }

  @Test
  @DisplayName("Test with uninitialized context")
  void testUninitializedContext() {
    // This test requires a fresh instance to work correctly
    // since we can't "uninitialize" the context once it's initialized

    // For demonstration purposes, let's mock the scenario by temporarily
    // forcing gf256Init to false (this is just for testing)
    boolean originalState = Cauchy256.gf256Init;
    try {
      // Force uninitialized state
      java.lang.reflect.Field field = Cauchy256.class.getDeclaredField("gf256Init");
      field.setAccessible(true);
      field.setBoolean(null, false);

      // Now attempt operations that should fail
      assertThrows(
              CauchyException.UninitializedContextException.class,
              () -> Cauchy256.encode(4, 2, new byte[4][8], new byte[2 * 8], 8),
              "Should throw UninitializedContextException when context is not initialized");

      assertThrows(
              CauchyException.UninitializedContextException.class,
              () -> Cauchy256.decode(4, 2, new Cauchy256.Block[6], 8),
              "Should throw UninitializedContextException when context is not initialized");
    } catch (Exception e) {
      fail("Test setup failed: " + e.getMessage());
    } finally {
      // Restore the original state
      try {
        java.lang.reflect.Field field = Cauchy256.class.getDeclaredField("gf256Init");
        field.setAccessible(true);
        field.setBoolean(null, originalState);
      } catch (Exception e) {
        fail("Failed to restore original state: " + e.getMessage());
      }
    }
  }
}
