package io.vawlt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Tests for parameter validation and edge cases in Cauchy256. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParameterTest {

  // Random data generator with fixed seed for reproducibility
  private final Random random = new Random(42);

  // Common test parameters
  private static final int DEFAULT_BLOCK_BYTES = 8;

  @BeforeAll
  void setup() {
    // Initialize the Cauchy256 library
    assertTrue(GF256.init(), "GF256 context initialization should succeed");
    Cauchy256.init();
  }

  @Test
  @DisplayName("Test with invalid parameters")
  void testInvalidParameters() {
    // Test with invalid k values
    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(0, 1, new byte[1][8], new byte[8], 8),
        "Should throw InvalidParametersException with k=0");

    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(-5, 1, new byte[1][8], new byte[8], 8),
        "Should throw InvalidParametersException with negative k");

    // Test with invalid m values
    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(1, 0, new byte[1][8], new byte[1], 8),
        "Should throw InvalidParametersException with m=0");

    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(1, -3, new byte[1][8], new byte[1], 8),
        "Should throw InvalidParametersException with negative m");

    // Test with k+m exceeding 256
    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(250, 7, new byte[250][8], new byte[7 * 8], 8),
        "Should throw InvalidParametersException with k+m>256");

    // Test with invalid block sizes
    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(4, 2, new byte[4][7], new byte[2 * 7], 7),
        "Should throw InvalidParametersException with blockBytes not multiple of 8");

    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(4, 2, new byte[4][8], new byte[2 * 8], -8),
        "Should throw InvalidParametersException with negative blockBytes");

    // Test with null pointers
    assertThrows(
        CauchyException.NullDataException.class,
        () -> Cauchy256.encode(4, 2, null, new byte[2 * 8], 8),
        "Should throw NullDataException with null data pointers");

    assertThrows(
        CauchyException.NullDataException.class,
        () -> Cauchy256.encode(4, 2, new byte[4][8], null, 8),
        "Should throw NullDataException with null recovery blocks");

    // Test decode with invalid parameters
    assertThrows(
        CauchyException.InvalidParametersException.class,
        () -> Cauchy256.decode(0, 2, new Cauchy256.Block[2], 8),
        "Should throw InvalidParametersException with k=0 in decode");

    assertThrows(
        CauchyException.NullDataException.class,
        () -> Cauchy256.decode(4, 2, null, 8),
        "Should throw NullDataException with null blocks array in decode");
  }

  @Test
  @DisplayName("Test decoding when no blocks are missing")
  void testDecodeNoMissingBlocks() {
    // Define test parameters
    int k = 3;
    int m = 2;
    int blockBytes = DEFAULT_BLOCK_BYTES;

    // Generate random data
    byte[][] originalData = generateRandomData(k, blockBytes);
    byte[] recoveryBlocks = new byte[m * blockBytes];

    // Encode the data
    Cauchy256.encode(k, m, originalData, recoveryBlocks, blockBytes);

    // Create blocks with all original data and no recovery data
    Cauchy256.Block[] blocks = new Cauchy256.Block[k];
    for (int i = 0; i < k; i++) {
      blocks[i] = new Cauchy256.Block(originalData[i].clone(), (byte) i);
    }

    // Decode should succeed immediately without using recovery blocks
    assertDoesNotThrow(
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decode should succeed with no missing blocks");

    // Verify all data matches the original
    for (int i = 0; i < k; i++) {
      assertArrayEquals(
          originalData[i],
          blocks[i].data,
          "Block " + i + " data should be unchanged after decoding");
    }
  }

  @Test
  @DisplayName("Test decoding when too many blocks are missing")
  void testDecodeTooManyMissingBlocks() {
    // Define test parameters
    int k = 4;
    int m = 2;
    int blockBytes = DEFAULT_BLOCK_BYTES;

    // Generate random data
    byte[][] originalData = generateRandomData(k, blockBytes);
    byte[] recoveryBlocks = new byte[m * blockBytes];

    // Encode the data
    Cauchy256.encode(k, m, originalData, recoveryBlocks, blockBytes);

    // Create blocks with only one original block and all recovery blocks
    // This should be unrecoverable since we need at least k-m original blocks
    Cauchy256.Block[] blocks = new Cauchy256.Block[k];

    // Add just one original block (not enough)
    blocks[0] = new Cauchy256.Block(originalData[0].clone(), (byte) 0);

    // Add recovery blocks
    for (int i = 0; i < m; i++) {
      byte[] recoveryData = new byte[blockBytes];
      System.arraycopy(recoveryBlocks, i * blockBytes, recoveryData, 0, blockBytes);
      blocks[i + 1] = new Cauchy256.Block(recoveryData, (byte) (k + i));
    }

    // Decode should fail because we're missing more blocks than we can recover
    assertThrows(
        CauchyException.InsufficientBlocksException.class,
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decode should fail with too many missing blocks");
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

  @Test
  @DisplayName("Test with exactly enough blocks for recovery")
  void testDecodeEdgeCase() {
    // Define test parameters
    int k = 5;
    int m = 3;
    int blockBytes = DEFAULT_BLOCK_BYTES;

    // Generate random data
    byte[][] originalData = generateRandomData(k, blockBytes);
    byte[] recoveryBlocks = new byte[m * blockBytes];

    // Encode the data
    Cauchy256.encode(k, m, originalData, recoveryBlocks, blockBytes);

    // Create blocks with exactly k-m original blocks and m recovery blocks
    // This should be exactly recoverable
    Cauchy256.Block[] blocks = new Cauchy256.Block[k + m];

    // Add exactly k-m original blocks (minimum needed)
    for (int i = 0; i < k - m; i++) {
      blocks[i] = new Cauchy256.Block(originalData[i].clone(), (byte) i);
    }

    // Add all recovery blocks
    for (int i = 0; i < m; i++) {
      byte[] recoveryData = new byte[blockBytes];
      System.arraycopy(recoveryBlocks, i * blockBytes, recoveryData, 0, blockBytes);
      blocks[k - m + i] = new Cauchy256.Block(recoveryData, (byte) (k + i));
    }

    // Decode should succeed because we have exactly enough blocks
    assertDoesNotThrow(
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decode should succeed with exactly k-m original blocks and m recovery blocks");

    // Verify all original data is recovered correctly
    for (int i = 0; i < k; i++) {
      boolean found = false;
      byte[] decodedData = null;

      for (Cauchy256.Block block : blocks) {
        if (block != null && block.data != null && block.row == i) {
          found = true;
          decodedData = block.data;
          break;
        }
      }

      assertTrue(found, "Block " + i + " should be present after decoding");
      assertArrayEquals(
          originalData[i], decodedData, "Decoded data should match original for block " + i);
    }
  }

  /**
   * Helper method to generate random test data
   *
   * @param k Number of data blocks
   * @param blockBytes Size of each block in bytes
   * @return Array of randomly filled data blocks
   */
  private byte[][] generateRandomData(int k, int blockBytes) {
    byte[][] data = new byte[k][blockBytes];
    for (int i = 0; i < k; i++) {
      random.nextBytes(data[i]);
    }
    return data;
  }
}
