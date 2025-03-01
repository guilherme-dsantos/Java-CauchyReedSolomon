package io.vawlt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    // Verify polynomial was initialized
    assertEquals((GF256.GF256_GEN_POLY[GF256.DEFAULT_POLYNOMIAL_INDEX] << 1) | 1, GF256.polynomial);
  }

  @Test
  @DisplayName("Test polynomial initialization")
  void testPolyInit() {
    // Test with valid polynomial index
    GF256.polyInit(3);
    assertEquals((GF256.GF256_GEN_POLY[3] << 1) | 1, GF256.polynomial);

    // Test with different polynomial index
    GF256.polyInit(0);
    assertEquals((GF256.GF256_GEN_POLY[0] << 1) | 1, GF256.polynomial);

    // Test with out-of-range index (should use DEFAULT_POLYNOMIAL_INDEX)
    GF256.polyInit(-1);
    assertEquals((GF256.GF256_GEN_POLY[GF256.DEFAULT_POLYNOMIAL_INDEX] << 1) | 1, GF256.polynomial);

    GF256.polyInit(16);
    assertEquals((GF256.GF256_GEN_POLY[GF256.DEFAULT_POLYNOMIAL_INDEX] << 1) | 1, GF256.polynomial);
  }

  @Test
  @DisplayName("Test exponential and log table initialization")
  void testExpLogInit() {
    // Set up the polynomial first
    GF256.polyInit(GF256.DEFAULT_POLYNOMIAL_INDEX);

    // Initialize exp/log tables
    assertDoesNotThrow(GF256::expLogInit);

    // Verify properties of the tables
    int[] expTable = GF256.gf256ExpTable;
    int[] logTable = GF256.gf256LogTable;

    // Check log(0) is 512
    assertEquals(512, logTable[0]);

    // Check exp(0) = 1
    assertEquals(1, expTable[0]);

    // Check exp(255) = exp(0) = 1
    assertEquals(expTable[0], expTable[255]);

    // Check the property that exp(log(x)) = x for non-zero x
    for (int i = 1; i < 256; i++) {
      int logValue = logTable[i] & 0xFFFF;
      int expValue = expTable[logValue] & 0xFF;
      assertEquals(i, expValue, "exp(log(" + i + ")) should equal " + i);
    }
  }

  @Test
  @DisplayName("Test multiplication and division table initialization")
  void testMulDivInit() {
    // Set up tables needed for mul/div initialization
    GF256.polyInit(GF256.DEFAULT_POLYNOMIAL_INDEX);
    GF256.expLogInit();

    // Initialize mul/div tables
    assertDoesNotThrow(GF256::mulDivInit);

    int[][] mulTable = GF256.gf256MulTable;
    int[][] divTable = GF256.gf256DivTable;

    // Check y=0 row in mul table (everything * 0 = 0)
    for (int x = 0; x < 256; x++) {
      assertEquals(0, mulTable[0][x]);
    }

    // Check x=0 column in mul table (0 * everything = 0)
    for (int y = 0; y < 256; y++) {
      assertEquals(0, mulTable[y][0]);
    }

    // Check identity: x * 1 = x
    for (int x = 0; x < 256; x++) {
      assertEquals(x, mulTable[1][x]);
    }

    // Check division by same number = 1 (for non-zero numbers)
    for (int x = 1; x < 256; x++) {
      assertEquals(1, divTable[x][x]);
    }
  }

  @Test
  @DisplayName("Test inverse table initialization")
  void testInvInit() {
    // Set up all required tables first
    GF256.polyInit(GF256.DEFAULT_POLYNOMIAL_INDEX);
    GF256.expLogInit();
    GF256.mulDivInit();

    // Initialize the inverse table
    assertDoesNotThrow(GF256::invInit);

    int[] invTable = GF256.gf256InvTable;

    // Inverse of 1 is 1
    assertEquals(1, invTable[1]);

    // x * inv(x) = 1
    for (int x = 1; x < 256; x++) {
      byte result = GF256.mul((byte) x, (byte) invTable[x]);
      assertEquals(1, result & 0xFF, "x * inv(x) should be 1 for x = " + x);
    }

    // Inverse of inverse is the original number
    for (int x = 1; x < 256; x++) {
      int invX = invTable[x] & 0xFF;
      int invInvX = invTable[invX] & 0xFF;
      assertEquals(x, invInvX, "inv(inv(" + x + ")) should equal " + x);
    }
  }
}
