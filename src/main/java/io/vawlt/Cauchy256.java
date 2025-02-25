package io.vawlt;


import java.util.Arrays;

/**
 * Java implementation of Cauchy-Reed-Solomon erasure code in GF(256)
 */
public class Cauchy256 {

    // GF256 context
    public static GF256 gf256Ctx;
    
    public static void init() {
        try {
            // Initialize the GF(256) math context
            System.out.println("Creating GF(256) context...");
            gf256Ctx = new GF256();

            System.out.println("Initializing GF(256) context...");
            gf256Ctx.init();

        } catch (Exception e) {
            System.err.println("Exception during initialization: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Cauchy encode
     * <p>
     * This produces a set of recovery blocks that should be transmitted after the
     * original data blocks.
     * <p>
     * It takes in k equal-sized blocks and produces m equal-sized recovery blocks.
     * The input block pointer array allows more natural usage of the library.
     * The output recovery blocks are stored end-to-end in the recovery_blocks.
     * <p>
     * The number of bytes per block (blockBytes) should be a multiple of 8.
     * <p>
     * The sum of k and m should be less than or equal to 256: k + m <= 256.
     * <p>
     * When transmitting the data, the block index of the data should be sent,
     * and the recovery block index is also needed. The decoder should also
     * be provided with the values of k, m, and blockBytes used for encoding.
     *
     * @param k              Number of data blocks
     * @param m              Number of recovery blocks
     * @param dataPtrs       Array of data blocks, each of size blockBytes
     * @param recoveryBlocks Output buffer for recovery blocks (size m * blockBytes)
     * @param blockBytes     Size of each block in bytes
     * @return 0 on success, and any other code indicates failure
     */

    public static void encode(int k, int m, byte[][] dataPtrs, byte[] recoveryBlocks, int blockBytes) {
        // Check parameters
        if (k <= 0 || m <= 0 || k + m > 256 || blockBytes <= 0 || blockBytes % 8 != 0) {
            throw new CauchyException.InvalidParametersException(
                    "Invalid parameters: k=" + k + ", m=" + m + ", blockBytes=" + blockBytes);
        }

        // Check data pointer validity
        if (dataPtrs == null || recoveryBlocks == null) {
            throw new CauchyException.NullDataException(
                    "Data pointers or recovery blocks are null");
        }

        // Ensure that the GF256 context is initialized
        if (gf256Ctx == null) {
            throw new CauchyException.UninitializedContextException(
                    "GF256 context not initialized. Call init() first.");
        }

        // Generate the Cauchy matrix for encoding
        byte[][] cauchyMatrix = generateCauchyMatrix(k, m);

        // Clear the recovery blocks
        Arrays.fill(recoveryBlocks, (byte) 0);

        // Perform the matrix multiplication to compute the recovery blocks
        for (int recoveryRow = 0; recoveryRow < m; recoveryRow++) {
            // Get the starting offset for this recovery block
            int recoveryOffset = recoveryRow * blockBytes;

            // For each data block
            for (int dataCol = 0; dataCol < k; dataCol++) {
                // Get the matrix coefficient
                byte coefficient = cauchyMatrix[recoveryRow][dataCol];

                // If coefficient is 1, we can just XOR without multiplication
                if (coefficient == 1) {
                    for (int byteIndex = 0; byteIndex < blockBytes; byteIndex++) {
                        recoveryBlocks[recoveryOffset + byteIndex] ^= dataPtrs[dataCol][byteIndex];
                    }
                }
                // If coefficient is not 0, perform multiply-and-add
                else if (coefficient != 0) {
                    for (int byteIndex = 0; byteIndex < blockBytes; byteIndex++) {
                        byte product = gf256Ctx.mul(dataPtrs[dataCol][byteIndex], coefficient);
                        recoveryBlocks[recoveryOffset + byteIndex] ^= product;
                    }
                }
                // If coefficient is 0, skip this data block (nothing to add)
            }
        }
    }

    /**
     * Generates a Cauchy matrix for the encoding process
     *
     * @param k Number of data blocks (columns)
     * @param m Number of recovery blocks (rows)
     * @return A Cauchy matrix of size m x k
     */
    private static byte[][] generateCauchyMatrix(int k, int m) {
        byte[][] matrix = new byte[m][k];

        // For a Cauchy matrix, we need two sets of distinct elements
        // X = {x_0, x_1, ..., x_{m-1}} and Y = {y_0, y_1, ..., y_{k-1}}
        // The matrix A is defined as A_{i,j} = 1/(x_i + y_j)

        for (int i = 0; i < m; i++) {
            byte x = (byte) (i + k); // Starting from k to avoid overlap with Y

            for (int j = 0; j < k; j++) {
                byte y = (byte) j;

                // In GF(256), the inverse of (x + y) gives us the Cauchy matrix element
                byte sum = gf256Ctx.add(x, y);
                matrix[i][j] = gf256Ctx.inv(sum);
            }
        }

        return matrix;
    }

    /**
     * Cauchy decode
     * <p>
     * This recovers the original data from the recovery data in the provided
     * blocks.
     * <p>
     * You should provide the same k, m, blockBytes values used by the encoder.
     * <p>
     * The blocks array contains data buffers each with blockBytes.
     * This array allows you to arrange the blocks in memory in any way that is
     * convenient.
     * <p>
     * The "row" should be set to the block index of the original data.
     * For example the second packet should be row = 1. The "row" should be set to
     * k + i for the i'th recovery block. For example the first recovery block row
     * is k, and the second recovery block row is k + 1.
     * <p>
     * It is recommended to fill in recovery blocks at the end of the array, and filling
     * in original data from the start. This way when the function completes, all
     * the missing data will be clustered at the end.
     *
     * @param k          Number of data blocks
     * @param m          Number of recovery blocks
     * @param blocks     Array of blocks containing received data and recovery blocks
     * @param blockBytes Size of each block in bytes
     * @return 0 on success, and any other code indicates failure
     */
    public static void decode(int k, int m, Block[] blocks, int blockBytes) {
        // Check parameters
        if (k <= 0 || m <= 0 || k + m > 256 || blockBytes <= 0 || blockBytes % 8 != 0) {
            throw new CauchyException.InvalidParametersException(
                    "Invalid parameters: k=" + k + ", m=" + m + ", blockBytes=" + blockBytes);
        }

        // Check data pointer validity
        if (blocks == null || blocks.length < k) {
            throw new CauchyException.NullDataException(
                    "Blocks array is null or too short");
        }

        // Ensure that the GF256 context is initialized
        if (gf256Ctx == null) {
            throw new CauchyException.UninitializedContextException(
                    "GF256 context not initialized. Call init() first.");
        }

        // Track which original data blocks are missing
        boolean[] missingOriginal = new boolean[k];
        int missingCount = 0;

        // Count how many blocks we have and track missing ones
        for (int i = 0; i < k; i++) {
            boolean found = false;
            for (Block block : blocks) {
                if (block != null && block.data != null && block.row == i) {
                    found = true;
                    break;
                }
            }
            missingOriginal[i] = !found;
            if (!found) {
                missingCount++;
            }
        }

        // If nothing is missing, we're done
        if (missingCount == 0) {
            return;
        }

        // If we have fewer blocks than k, we can't recover
        if (blocks.length < k) {
            throw new CauchyException.InsufficientBlocksException(
                    "Not enough blocks provided for recovery");
        }

        // Find recovery blocks and build a list of available recovery rows
        int[] recoveryRows = new int[m];
        int recoveryCount = 0;

        for (Block block : blocks) {
            if (block != null && block.data != null && block.row >= k && block.row < k + m) {
                recoveryRows[recoveryCount++] = block.row - k;
                if (recoveryCount >= missingCount) {
                    break; // We have enough recovery blocks
                }
            }
        }

        // Check if we have enough recovery blocks
        if (recoveryCount < missingCount) {
            throw new CauchyException.InsufficientBlocksException(
                    "Not enough recovery blocks to restore missing data");
        }

        // Get the list of missing original indices
        int[] missingIndices = new int[missingCount];
        int missingIndex = 0;
        for (int i = 0; i < k; i++) {
            if (missingOriginal[i]) {
                missingIndices[missingIndex++] = i;
            }
        }

        // Generate the Cauchy matrix
        byte[][] cauchyMatrix = generateCauchyMatrix(k, m);

        // Create a submatrix containing just the needed coefficients
        // (Recovery rows needed vs. missing columns)
        byte[][] subMatrix = new byte[missingCount][missingCount];
        for (int i = 0; i < missingCount; i++) {
            for (int j = 0; j < missingCount; j++) {
                subMatrix[i][j] = cauchyMatrix[recoveryRows[i]][missingIndices[j]];
            }
        }

        // Invert the submatrix to solve the linear system
        byte[][] invSubMatrix = invertMatrix(subMatrix);
        if (invSubMatrix == null) {
            throw new CauchyException.MatrixOperationException(
                    "Failed to invert recovery matrix");
        }

        // For each missing original block
        for (int i = 0; i < missingCount; i++) {
            int missingCol = missingIndices[i];

            // Create a temporary buffer for computing the missing block
            byte[] tempBuffer = new byte[blockBytes];
            Arrays.fill(tempBuffer, (byte) 0);

            // For each recovery row we're using
            for (int j = 0; j < missingCount; j++) {
                int recoveryRow = recoveryRows[j];

                // Find the recovery block in our blocks array
                byte[] recoveryData = null;
                for (Block block : blocks) {
                    if (block != null && block.data != null && block.row == recoveryRow + k) {
                        recoveryData = block.data;
                        break;
                    }
                }

                if (recoveryData == null) {
                    throw new CauchyException.BlockBufferException(
                            "Recovery block data unexpectedly null");
                }

                // Create a temporary copy of the recovery data
                byte[] recoveryTemp = new byte[blockBytes];
                System.arraycopy(recoveryData, 0, recoveryTemp, 0, blockBytes);

                // Subtract out the contribution from available original data blocks
                for (int l = 0; l < k; l++) {
                    if (!missingOriginal[l]) {
                        // Find the original data
                        byte[] originalData = null;
                        for (Block block : blocks) {
                            if (block != null && block.data != null && block.row == l) {
                                originalData = block.data;
                                break;
                            }
                        }

                        if (originalData == null) {
                            throw new CauchyException.BlockBufferException(
                                    "Original block data unexpectedly null");
                        }

                        // Subtract the contribution: recovery -= original * coefficient
                        byte coefficient = cauchyMatrix[recoveryRow][l];
                        if (coefficient == 1) {
                            for (int p = 0; p < blockBytes; p++) {
                                recoveryTemp[p] ^= originalData[p];
                            }
                        } else if (coefficient != 0) {
                            for (int p = 0; p < blockBytes; p++) {
                                byte product = gf256Ctx.mul(originalData[p], coefficient);
                                recoveryTemp[p] ^= product;
                            }
                        }
                    }
                }

                // Apply the inverted matrix coefficient
                byte coefficient = invSubMatrix[i][j];
                if (coefficient == 1) {
                    for (int p = 0; p < blockBytes; p++) {
                        tempBuffer[p] ^= recoveryTemp[p];
                    }
                } else if (coefficient != 0) {
                    for (int p = 0; p < blockBytes; p++) {
                        byte product = gf256Ctx.mul(recoveryTemp[p], coefficient);
                        tempBuffer[p] ^= product;
                    }
                }
            }

            // Find or create a block for the recovered data
            boolean blockFound = false;
            for (int j = 0; j < blocks.length; j++) {
                if (blocks[j] == null || blocks[j].data == null) {
                    blocks[j] = new Block(new byte[blockBytes], (byte) missingCol);
                    System.arraycopy(tempBuffer, 0, blocks[j].data, 0, blockBytes);
                    blockFound = true;
                    break;
                }
            }

            if (!blockFound) {
                throw new CauchyException.BlockBufferException(
                        "No space in blocks array for recovered data");
            }
        }
    }

    /**
     * Inverts a square matrix in GF(256)
     *
     * @param matrix The square matrix to invert
     * @return The inverted matrix, or null if the matrix is not invertible
     */
    private static byte[][] invertMatrix(byte[][] matrix) {
        int size = matrix.length;
        if (size == 0 || matrix[0].length != size) {
            return null; // Not a square matrix
        }

        // Create augmented matrix [A|I]
        byte[][] aug = new byte[size][size * 2];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                aug[i][j] = matrix[i][j];
            }
            aug[i][i + size] = 1; // Identity matrix on the right
        }

        // Perform Gaussian elimination
        for (int i = 0; i < size; i++) {
            // Find pivot
            int pivotRow = i;
            for (int j = i + 1; j < size; j++) {
                if (aug[j][i] > aug[pivotRow][i]) {
                    pivotRow = j;
                }
            }

            // If pivot is zero, matrix is singular
            if (aug[pivotRow][i] == 0) {
                return null;
            }

            // Swap rows if needed
            if (pivotRow != i) {
                for (int j = 0; j < size * 2; j++) {
                    byte temp = aug[i][j];
                    aug[i][j] = aug[pivotRow][j];
                    aug[pivotRow][j] = temp;
                }
            }

            // Scale pivot row
            byte pivot = aug[i][i];
            byte pivotInv = gf256Ctx.inv(pivot);
            for (int j = 0; j < size * 2; j++) {
                aug[i][j] = gf256Ctx.mul(aug[i][j], pivotInv);
            }

            // Eliminate other rows
            for (int j = 0; j < size; j++) {
                if (j != i) {
                    byte factor = aug[j][i];
                    for (int k = 0; k < size * 2; k++) {
                        aug[j][k] ^= gf256Ctx.mul(aug[i][k], factor);
                    }
                }
            }
        }

        // Extract inverse from augmented matrix
        byte[][] inverse = new byte[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                inverse[i][j] = aug[i][j + size];
            }
        }

        return inverse;
    }

    /**
     * Descriptor for received data block
     */

    public static class Block {
        public byte[] data;
        public byte row;

        public Block(byte[] data, byte row) {
            this.data = data;
            this.row = row;
        }
    }
}