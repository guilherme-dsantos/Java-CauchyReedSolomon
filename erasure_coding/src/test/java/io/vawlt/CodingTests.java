package io.vawlt;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for Cauchy256 implementation covering different file sizes, k and m
 * configurations, and recovery scenarios.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodingTests {

    @BeforeAll
    void setup() {
        // Initialize the Cauchy256 library
        assertTrue(GF256.init(), "GF256 context initialization should succeed");
        Cauchy256.init();
        System.out.println("GF256 context initialized successfully");
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
    @DisplayName("Test encoding and decoding with dataset ~2.5k files from 1 byte to 16MB in different lost scenarios")
    void datasetCodingTest() {

        int[][] configs = {
                {2, 2}, // k=2 m=2
                {3, 1}, // k=3 m=1
        };

        URL resourceUrl = getClass().getClassLoader().getResource("SampleFiles");
        Assumptions.assumeTrue(resourceUrl != null, "SampleFiles directory not found in resources");
        File directory = new File(resourceUrl.getFile());
        File[] sampleFiles = directory.listFiles();
        Assumptions.assumeTrue(sampleFiles != null, "Files not found in directory");
        for (int[] km : configs) {
            int k = km[0];
            int m = km[1];
            for (File f : sampleFiles) {
                try {
                    byte[] fileData = Files.readAllBytes(f.toPath());
                    int fileSize = fileData.length;

                    // Calculate block size (round up to multiple of 8)
                    int blockBytes = (int) Math.ceil(fileSize / (double) k / 8) * 8;

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

                    //1. All possible combinations
                    testRecoveryWithAllPossibleLossCombinations(dataBlocks, recoveryBlocks, blockBytes, k, m);

                    //2. No blocks lost
                    testDecodeNoMissingBlocks(k, m, dataBlocks, blockBytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Test
    @DisplayName("Test decoding when too many blocks are missing")
    void testDecodeTooManyMissingBlocks() {
        // Define test parameters
        int k = 4;
        int m = 2;
        int blockBytes = 8;

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

    //
    // HELPER METHODS
    //


    void testDecodeNoMissingBlocks(int k, int m, byte[][] originalData, int blockBytes) {

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



    private void testRecoveryWithAllPossibleLossCombinations(
            byte[][] dataBlocks, byte[] recoveryBlocks, int blockBytes, int k, int m) {
        System.out.println(
                "Starting test with parameters: k=" + k + ", m=" + m + ", blockBytes=" + blockBytes);

        // Test all loss scenarios from 1 to m blocks
        for (int lossCount = 1; lossCount <= m; lossCount++) {
            System.out.println("\n===== TESTING SCENARIOS WITH " + lossCount + " LOST BLOCKS =====");

            // Generate all combinations of losing 'lossCount' blocks
            List<List<Integer>> lossCombinations = getCombinationIndices(k + m, lossCount);
            System.out.println(
                    "Generated " + lossCombinations.size() + " combinations for loss count " + lossCount);

            int combinationCounter = 0;
            int successCount = 0;
            int skipCount = 0;

            for (List<Integer> combination : lossCombinations) {
                combinationCounter++;

                System.out.println(
                        "\n[Combo "
                                + combinationCounter
                                + "/"
                                + lossCombinations.size()
                                + "] Testing loss combination: "
                                + combination);

                // Count lost data blocks vs. recovery blocks
                int lostDataBlockCount = 0;
                int lostRecoveryBlockCount = 0;
                for (int idx : combination) {
                    if (idx < k) {
                        lostDataBlockCount++;
                    } else {
                        lostRecoveryBlockCount++;
                    }
                }

                System.out.println(
                        "  - Lost data blocks: "
                                + lostDataBlockCount
                                + ", Lost recovery blocks: "
                                + lostRecoveryBlockCount);

                // Skip combinations where we lose more data blocks than we can recover
                if (lostDataBlockCount > m) {
                    System.out.println("  - SKIPPING: Cannot recover more data blocks than m=" + m);
                    skipCount++;
                    continue;
                }

                // Create a fresh blocks array for each test
                Cauchy256.Block[] blocks = new Cauchy256.Block[k + m];

                // Set up the lost blocks array
                boolean[] lostBlock = new boolean[k + m];
                for (int idx : combination) {
                    lostBlock[idx] = true;
                }

                System.out.println("  - Lost blocks array: " + Arrays.toString(lostBlock));

                // Add available data blocks and leave null spaces for lost data blocks
                System.out.println("  - Setting up blocks array:");
                for (int i = 0; i < k; i++) {
                    if (!lostBlock[i]) {
                        blocks[i] = new Cauchy256.Block(dataBlocks[i].clone(), (byte) i);
                        System.out.println("    - Added data block " + i);
                    } else {
                        System.out.println("    - Left null space for lost data block " + i);
                    }
                }

                // Add available recovery blocks
                for (int i = 0; i < m; i++) {
                    if (!lostBlock[k + i]) {
                        byte[] blockData =
                                Arrays.copyOfRange(recoveryBlocks, i * blockBytes, (i + 1) * blockBytes);
                        blocks[k + i] = new Cauchy256.Block(blockData, (byte) (k + i));
                        System.out.println("    - Added recovery block " + (k + i));
                    } else {
                        System.out.println("    - Left null space for lost recovery block " + (k + i));
                    }
                }

                System.out.println("  - Attempting decode...");

                // Decode
                final int finalLostDataCount = lostDataBlockCount; // For use in lambda
                assertDoesNotThrow(
                        () -> {
                            Cauchy256.decode(k, m, blocks, blockBytes);
                            System.out.println(
                                    "  - Decode SUCCESSFUL with " + finalLostDataCount + " lost data blocks");

                            // Print blocks array after decode for debugging
                            System.out.println("  - Blocks array after decode:");
                            for (int i = 0; i < blocks.length; i++) {
                                if (blocks[i] != null) {
                                    System.out.println(
                                            "    - Block["
                                                    + i
                                                    + "]: row="
                                                    + blocks[i].row
                                                    + ", data length="
                                                    + blocks[i].data.length);
                                } else {
                                    System.out.println("    - Block[" + i + "]: null");
                                }
                            }
                        },
                        "Decoding with " + finalLostDataCount + " lost data blocks should succeed");

                // Verify recovered blocks
                System.out.println("  - Verifying recovered blocks...");
                verifyRecoveredBlocks(dataBlocks, blocks, k);
                System.out.println("  - Verification SUCCESSFUL");

                successCount++;
            }

            System.out.println("\n===== SUMMARY FOR " + lossCount + " LOST BLOCKS =====");
            System.out.println("Total combinations: " + lossCombinations.size());
            System.out.println("Successful tests: " + successCount);
            System.out.println("Skipped tests: " + skipCount);
        }

        System.out.println("\n===== TEST COMPLETE =====");
    }

    /**
     * Helper method to verify that all data blocks were correctly recovered
     */
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

    /**
     * Helper method to generate random test data
     *
     * @param k          Number of data blocks
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

    // Random data generator with fixed seed for reproducibility
    private final Random random = new Random(42);

    /**
     * Helper method to generate all possible combinations from a given list,
     * this helps to create all possible scenarios of lost blocks
     *
     * @param n Number of blocks
     * @param m Number of tolerated lost blocks
     * @return List with all possible combinations
     */
    private static List<List<Integer>> getCombinationIndices(int n, int m) {
        List<List<Integer>> result = new ArrayList<>();

        // Check if m is valid
        if (m <= 0 || m > n) {
            return result;
        }

        // Initialize the first combination indices
        int[] indices = new int[m];
        for (int i = 0; i < m; i++) {
            indices[i] = i;
        }

        while (true) {
            // Add current combination to result
            List<Integer> combination = new ArrayList<>(m);
            for (int index : indices) {
                combination.add(index);
            }
            result.add(combination);

            // Find the rightmost index that can be incremented
            int i = m - 1;
            while (i >= 0 && indices[i] == n - m + i) {
                i--;
            }

            // If no index can be incremented, we're done
            if (i < 0) {
                break;
            }

            // Increment the found index
            indices[i]++;

            // Reset all indices to the right
            for (int j = i + 1; j < m; j++) {
                indices[j] = indices[j - 1] + 1;
            }
        }

        return result;
    }
}
