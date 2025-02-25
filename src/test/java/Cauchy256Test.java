import io.vawlt.cauchy.Cauchy256;
import io.vawlt.cauchy.CauchyException;
import io.vawlt.cauchy.GF256;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Cauchy256Test {

  // Random data generator
  private final Random random = new Random(42);

  @BeforeAll
  void setup() {
    Cauchy256.init();
  }

  @Test
  void testInit() {
    assertDoesNotThrow(() -> Cauchy256.init());
  }

  @ParameterizedTest
  @CsvSource({"4, 2, 8", // Small
      "10, 3, 16", // Medium
      "20, 5, 64" // Large
  })
  void testEncodeAndDecode(int k, int m, int blockBytes) {
    // Generate random data
    byte[][] originalData = generateRandomData(k, blockBytes);

    // Encode data to create recovery blocks
    byte[] recoveryBlocks = new byte[m * blockBytes];
    assertDoesNotThrow(() -> Cauchy256.encode(k, m, originalData, recoveryBlocks, blockBytes),
        "Encoding should succeed");

    // Test various loss scenarios
    testLossScenario(k, m, blockBytes, originalData, recoveryBlocks, 0); // No loss
    testLossScenario(k, m, blockBytes, originalData, recoveryBlocks, 1); // One data block lost
    testLossScenario(k, m, blockBytes, originalData, recoveryBlocks, m); // Maximum recoverable loss
    testLossScenario(k, m, blockBytes, originalData, recoveryBlocks, m / 2); // Half of recoverable
                                                                             // loss
  }

  private void testLossScenario(int k, int m, int blockBytes, byte[][] originalData,
      byte[] recoveryBlocks, int lossCount) {
    if (lossCount > m) {
      throw new IllegalArgumentException("Cannot test loss beyond recovery capability");
    }

    // Create blocks array with missing data
    Cauchy256.Block[] blocks = new Cauchy256.Block[k + m];

    // Prepare random lost block indices
    boolean[] lostBlocks = new boolean[k];
    int actualLossCount = 0;

    while (actualLossCount < lossCount && actualLossCount < k) {
      int idx = random.nextInt(k);
      if (!lostBlocks[idx]) {
        lostBlocks[idx] = true;
        actualLossCount++;
      }
    }

    // Fill in the available data blocks
    for (int i = 0; i < k; i++) {
      if (!lostBlocks[i]) {
        blocks[i] = new Cauchy256.Block(originalData[i].clone(), (byte) i);
      }
    }

    // Fill in all recovery blocks
    for (int i = 0; i < m; i++) {
      byte[] recoveryData = new byte[blockBytes];
      System.arraycopy(recoveryBlocks, i * blockBytes, recoveryData, 0, blockBytes);
      blocks[k + i] = new Cauchy256.Block(recoveryData, (byte) (k + i));
    }

    // Decode the data
    assertDoesNotThrow(() -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decoding should succeed with " + actualLossCount + " lost blocks");

    // Verify all the data matches the original
    for (int i = 0; i < k; i++) {
      boolean found = false;
      byte[] decodedData = null;

      for (Cauchy256.Block block : blocks) {
        if (block != null && block.row == i) {
          found = true;
          decodedData = block.data;
          break;
        }
      }

      assertTrue(found, "Block " + i + " should be present after decoding");
      assertArrayEquals(originalData[i], decodedData,
          "Decoded data should match original for block " + i);
    }
  }

  @Test
  void testInvalidParameters() {
    // Test with invalid k, m
    assertThrows(CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(0, 1, new byte[1][1], new byte[1], 8),
        "Should throw InvalidParametersException with k=0");

    assertThrows(CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(1, 0, new byte[1][1], new byte[1], 8),
        "Should throw InvalidParametersException with m=0");

    assertThrows(CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(250, 7, new byte[250][8], new byte[7 * 8], 8),
        "Should throw InvalidParametersException with k+m>256");

    // Test with invalid block size
    assertThrows(CauchyException.InvalidParametersException.class,
        () -> Cauchy256.encode(4, 2, new byte[4][7], new byte[2 * 7], 7),
        "Should throw InvalidParametersException with blockBytes not multiple of 8");

    // Test with null pointers
    assertThrows(CauchyException.NullDataException.class,
        () -> Cauchy256.encode(4, 2, null, new byte[2 * 8], 8),
        "Should throw NullDataException with null data pointers");

    assertThrows(CauchyException.NullDataException.class,
        () -> Cauchy256.encode(4, 2, new byte[4][8], null, 8),
        "Should throw NullDataException with null recovery blocks");
  }

  @Test
  void testDecodeNoMissingBlocks() {
    // Test case where no blocks are missing
    int k = 4;
    int m = 2;
    int blockBytes = 8;

    byte[][] originalData = generateRandomData(k, blockBytes);
    byte[] recoveryBlocks = new byte[m * blockBytes];

    Cauchy256.encode(k, m, originalData, recoveryBlocks, blockBytes);

    // Create blocks with all original data and no recovery data
    Cauchy256.Block[] blocks = new Cauchy256.Block[k];
    for (int i = 0; i < k; i++) {
      blocks[i] = new Cauchy256.Block(originalData[i].clone(), (byte) i);
    }

    // Decode should succeed immediately without using recovery blocks
    assertDoesNotThrow(() -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decode should succeed with no missing blocks");
  }

  @Test
  void testDecodeTooManyMissingBlocks() {
    // Test case where too many blocks are missing to recover
    int k = 4;
    int m = 2;
    int blockBytes = 8;

    byte[][] originalData = generateRandomData(k, blockBytes);
    byte[] recoveryBlocks = new byte[m * blockBytes];

    Cauchy256.encode(k, m, originalData, recoveryBlocks, blockBytes);

    // Create blocks with just k-m-1 original blocks and all recovery blocks
    // This should be unrecoverable (need at least k-m original blocks)
    Cauchy256.Block[] blocks = new Cauchy256.Block[k + m - 1];

    // Add just k-m-1 original blocks (not enough)
    for (int i = 0; i < k - m - 1; i++) {
      blocks[i] = new Cauchy256.Block(originalData[i].clone(), (byte) i);
    }

    // Add recovery blocks
    for (int i = 0; i < m; i++) {
      byte[] recoveryData = new byte[blockBytes];
      System.arraycopy(recoveryBlocks, i * blockBytes, recoveryData, 0, blockBytes);
      blocks[k - m - 1 + i] = new Cauchy256.Block(recoveryData, (byte) (k + i));
    }

    // Decode should fail because we're missing m+1 original blocks but only have m recovery blocks
    assertThrows(CauchyException.InsufficientBlocksException.class,
        () -> Cauchy256.decode(k, m, blocks, blockBytes),
        "Decode should fail with too many missing blocks");
  }

  @Test
  void testUninitializedContext() {
    // Create a temporary class that extends Cauchy256 for testing
    class TestCauchy extends Cauchy256 {
      public static void resetContext() {
        gf256Ctx = null;
      }
    }

    // Save the current context
    GF256 savedContext = Cauchy256.gf256Ctx;

    try {
      // Reset the context to null
      TestCauchy.resetContext();

      // Try operations with null context
      assertThrows(CauchyException.UninitializedContextException.class,
          () -> Cauchy256.encode(4, 2, new byte[4][8], new byte[2 * 8], 8),
          "Should throw UninitializedContextException when context is null");

      assertThrows(CauchyException.UninitializedContextException.class,
          () -> Cauchy256.decode(4, 2, new Cauchy256.Block[6], 8),
          "Should throw UninitializedContextException when context is null");
    } finally {
      // Restore the context
      Cauchy256.gf256Ctx = savedContext;
    }
  }

  // Utility methods
  private byte[][] generateRandomData(int k, int blockBytes) {
    byte[][] data = new byte[k][blockBytes];
    for (int i = 0; i < k; i++) {
      random.nextBytes(data[i]);
    }
    return data;
  }
}
