package io.vawlt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive test for Cauchy256 implementation covering different file sizes, k and m
 * configurations, and recovery scenarios.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsTest {

  // Random data generator with fixed seed for reproducibility
  private final Random random = new Random(42);

  @BeforeAll
  void setup() {
    // Initialize the Cauchy256 library
    assertTrue(GF256.init(), "GF256 context initialization should succeed");
    Cauchy256.init();
    System.out.println("GF256 context initialized successfully");
  }

  @ParameterizedTest(name = "File size = {0} bytes")
  @ValueSource(
      ints = {
        8, // Minimum block size
        1024, // 1KB
        8192, // 8KB
        65536, // 64KB
        1048576 // 1MB
      })
  @DisplayName("Test various file sizes")
  void testFileSize(int fileSize) {
    System.out.println("\n=== Testing file size: " + fileSize + " bytes ===");

    // Fixed test parameters
    int k = 4;
    int m = 2;

    // Calculate block size (round up to multiple of 8)
    int blockBytes = (int) Math.ceil(fileSize / (double) k / 8) * 8;
    if (blockBytes == 0) blockBytes = 8; // Ensure minimum block size

    System.out.println("Block size: " + blockBytes + " bytes");

    // Generate random data to simulate file
    byte[] fileData = new byte[fileSize];
    random.nextBytes(fileData);

    // Split into data blocks
    byte[][] dataBlocks = new byte[k][blockBytes];

    // Copy file data to data blocks (with padding if needed)
    int remaining = fileSize;
    for (int i = 0; i < k && remaining > 0; i++) {
      int toCopy = Math.min(remaining, blockBytes);
      System.arraycopy(fileData, fileSize - remaining, dataBlocks[i], 0, toCopy);
      remaining -= toCopy;
    }

    // Create recovery blocks
    byte[] recoveryBlocks = new byte[m * blockBytes];

    // Encode data
    try {
      Cauchy256.encode(k, m, dataBlocks, recoveryBlocks, blockBytes);
    } catch (Exception e) {
      fail("Encoding should succeed but threw " + e);
    }

    // Test Scenario 1: Maximum number of data block loss
    testRecoveryWithLostDataBlocks(dataBlocks, recoveryBlocks, blockBytes, k, m, m);

    // Test Scenario 2: Partial data block loss
    testRecoveryWithLostDataBlocks(dataBlocks, recoveryBlocks, blockBytes, k, m, m / 2);

    // Test Scenario 3: Mixed loss
    testRecoveryWithMixedLoss(dataBlocks, recoveryBlocks, blockBytes, k, m);

    // Test Scenario 4: No data blocks lost
    testRecoveryWithAllDataBlocks(dataBlocks, recoveryBlocks, blockBytes, k, m);

    System.out.println("File size " + fileSize + " bytes test passed");
  }

  @Test
  @DisplayName("Test with varying K and M values")
  void testVaryingKM() {
    // Configuration pairs of k, m to test
    int[][] configs = {
      {1, 1}, // k=1 m=1
      {4, 2}, // k=4 m=2
      {8, 4}, // k=8 m=4
      {16, 4} // k=16 m=4
    };

    int fileSize = 16384; // 16KB

    for (int[] config : configs) {
      int k = config[0];
      int m = config[1];

      System.out.println("\n=== Testing config: k=" + k + ", m=" + m + " ===");

      // Calculate block size (round up to multiple of 8)
      int blockBytes = (int) Math.ceil(fileSize / (double) k / 8) * 8;

      System.out.println("Block size: " + blockBytes + " bytes");

      // Generate random data
      byte[] fileData = new byte[fileSize];
      random.nextBytes(fileData);

      // Split into data blocks
      byte[][] dataBlocks = new byte[k][blockBytes];

      // Copy file data to data blocks (with padding if needed)
      int remaining = fileSize;
      for (int i = 0; i < k && remaining > 0; i++) {
        int toCopy = Math.min(remaining, blockBytes);
        System.arraycopy(fileData, fileSize - remaining, dataBlocks[i], 0, toCopy);
        remaining -= toCopy;
      }

      // Create recovery blocks
      byte[] recoveryBlocks = new byte[m * blockBytes];

      // Encode data
      assertDoesNotThrow(
          () -> Cauchy256.encode(k, m, dataBlocks, recoveryBlocks, blockBytes),
          "Encoding with k=" + k + ", m=" + m + " should succeed");

      // Test different loss scenarios
      // 1. Maximum recoverable loss
      testRecoveryWithLostDataBlocks(dataBlocks, recoveryBlocks, blockBytes, k, m, m);

      // 2. Partial loss (if possible)
      if (m > 1) {
        testRecoveryWithLostDataBlocks(dataBlocks, recoveryBlocks, blockBytes, k, m, 1);
      }

      // 3. Mixed loss scenario
      if (m > 1 && k > 1) {
        testRecoveryWithMixedLoss(dataBlocks, recoveryBlocks, blockBytes, k, m);
      }

      System.out.println("Config k=" + k + ", m=" + m + " test passed");
    }
  }

  //
  // HELPER METHODS
  //

  /**
   * Helper method to test recovery with specified number of lost data blocks This tests the
   * scenario where specific data blocks are missing but all recovery blocks are available
   */
  private void testRecoveryWithLostDataBlocks(
      byte[][] dataBlocks, byte[] recoveryBlocks, int blockBytes, int k, int m, int lossCount) {
    if (lossCount > m) {
      throw new IllegalArgumentException("Cannot test loss beyond recovery capability");
    }

    System.out.println("Testing recovery with " + lossCount + " lost data blocks");

    // Create blocks array
    Cauchy256.Block[] blocks = new Cauchy256.Block[k + m];

    // Decide which data blocks to lose
    boolean[] lostBlock = new boolean[k];
    int lostSoFar = 0;

    while (lostSoFar < lossCount) {
      int idx = random.nextInt(k);
      if (!lostBlock[idx]) {
        lostBlock[idx] = true;
        lostSoFar++;
      }
    }

    // Add available data blocks
    int blockIndex = 0;
    for (int i = 0; i < k; i++) {
      if (!lostBlock[i]) {
        blocks[blockIndex++] = new Cauchy256.Block(dataBlocks[i].clone(), (byte) i);
      }
    }

    // Add all recovery blocks necessary for recovery
    for (int i = 0; i < lossCount; i++) {
      byte[] blockData = Arrays.copyOfRange(recoveryBlocks, i * blockBytes, (i + 1) * blockBytes);
      blocks[blockIndex++] = new Cauchy256.Block(blockData, (byte) (k + i));
    }

    // Decode
    assertDoesNotThrow(
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decoding with " + lossCount + " lost data blocks should succeed");

    verifyRecoveredBlocks(dataBlocks, blocks, k);
  }

  /**
   * Helper method to test recovery when all data blocks are present This tests the scenario where
   * no recovery is actually needed
   */
  private void testRecoveryWithAllDataBlocks(
      byte[][] dataBlocks, byte[] recoveryBlocks, int blockBytes, int k, int m) {
    System.out.println("Testing recovery with all data blocks present (no recovery needed)");

    // Create blocks array
    Cauchy256.Block[] blocks = new Cauchy256.Block[k];

    // Add all data blocks
    for (int i = 0; i < k; i++) {
      blocks[i] = new Cauchy256.Block(dataBlocks[i].clone(), (byte) i);
    }

    // No recovery blocks needed in this scenario
    assertDoesNotThrow(
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decoding with all data blocks present should succeed");

    verifyRecoveredBlocks(dataBlocks, blocks, k);
  }

  /**
   * Helper method to test recovery with a mix of lost data and recovery blocks This tests a more
   * realistic scenario where some data and some recovery blocks are available
   */
  private void testRecoveryWithMixedLoss(
      byte[][] dataBlocks, byte[] recoveryBlocks, int blockBytes, int k, int m) {
    // We'll lose half the recoverable data blocks and use half the recovery blocks
    int dataLossCount = Math.max(1, m / 2);

    System.out.println(
        "Testing mixed loss scenario: "
            + dataLossCount
            + " lost data blocks with "
            + dataLossCount
            + " recovery blocks");

    // Create blocks array
    Cauchy256.Block[] blocks = new Cauchy256.Block[k + dataLossCount];

    // Decide which data blocks to lose (we'll pick the first dataLossCount for simplicity)
    boolean[] lostBlock = new boolean[k];
    for (int i = 0; i < dataLossCount; i++) {
      lostBlock[i] = true;
    }

    // Add available data blocks
    int blockIndex = 0;
    for (int i = 0; i < k; i++) {
      if (!lostBlock[i]) {
        blocks[blockIndex++] = new Cauchy256.Block(dataBlocks[i].clone(), (byte) i);
      }
    }

    // Add only the first dataLossCount recovery blocks
    for (int i = 0; i < dataLossCount; i++) {
      byte[] blockData = Arrays.copyOfRange(recoveryBlocks, i * blockBytes, (i + 1) * blockBytes);
      blocks[blockIndex++] = new Cauchy256.Block(blockData, (byte) (k + i));
    }

    // Decode
    assertDoesNotThrow(
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decoding with mixed block loss should succeed");

    verifyRecoveredBlocks(dataBlocks, blocks, k);
  }

  /** Helper method to verify that all data blocks were correctly recovered */
  private void verifyRecoveredBlocks(byte[][] originalData, Cauchy256.Block[] blocks, int k) {
    for (int i = 0; i < k; i++) {
      boolean blockFound = false;
      for (Cauchy256.Block block : blocks) {
        if (block != null && block.row == i) {
          assertArrayEquals(
              originalData[i], block.data, "Recovered block " + i + " should match original");
          blockFound = true;
          break;
        }
      }
      assertTrue(blockFound, "Block " + i + " should be present after decoding");
    }
  }
}
