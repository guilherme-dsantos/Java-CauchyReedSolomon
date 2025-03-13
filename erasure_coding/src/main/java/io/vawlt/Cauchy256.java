package io.vawlt;

import java.util.Arrays;

import static io.vawlt.Cauchy256Tables.*;

public class Cauchy256 {

    // GF256 context initialization flag
    static boolean gf256Init;

    private static final int STRIDE_2 = 254;
    private static final int STRIDE_3 = 253;
    private static final int STRIDE_4 = 252;
    private static final int STRIDE_5 = 251;
    private static final int STRIDE_6 = 250;

    public static void init() {
        try {
            // Initialize the GF(256) math context
            System.out.println("Creating GF(256) context...");
            gf256Init = GF256.init();
        } catch (CauchyException.UninitializedContextException e) {
            throw new CauchyException.UninitializedContextException(e.getMessage());
        }
    }

    /**
     * Encodes data using Cauchy Reed-Solomon
     *
     * @param k Number of original data blocks
     * @param m Number of recovery blocks to generate
     * @param data Original data blocks (size k)
     * @param recoveryBlocks Output buffer for recovery blocks (size m*blockBytes)
     * @param blockBytes Size of each block in bytes
     */
    public static void encode(int k, int m, byte[][] data, byte[] recoveryBlocks, int blockBytes) {
        // Check parameters
        if (k <= 0 || m <= 0 || k + m > 256 || blockBytes <= 0 || blockBytes % 8 != 0) {
            throw new CauchyException.InvalidParametersException(
                    "Invalid parameters: k=" + k + ", m=" + m + ", blockBytes=" + blockBytes);
        }

        // Check data pointers
        if (data == null || recoveryBlocks == null) {
            throw new CauchyException.NullDataException("Data pointers or recovery blocks are null");
        }

        // Ensure that the GF256 context is initialized
        if (!gf256Init) {
            throw new CauchyException.UninitializedContextException(
                    "GF256 context not initialized. Call init() first.");
        }

        // Calculate block sub-bytes (used for bit-level operations)
        final int subbytes = blockBytes / 8;

        // Clear recovery blocks
        Arrays.fill(recoveryBlocks, (byte)0);

        // First recovery block - simple XOR of all data blocks
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < blockBytes; j++) {
                recoveryBlocks[j] ^= data[i][j];
            }
        }

        // If only one recovery block needed, we're done
        if (m == 1) {
            return;
        }

        // For each additional recovery block
        for (int recoveryIdx = 1; recoveryIdx < m; recoveryIdx++) {
            int recoveryOffset = recoveryIdx * blockBytes;

            // For each data block
            for (int i = 0; i < k; i++) {
                // Get the appropriate matrix coefficient
                byte slice = getCauchyMatrixElement(recoveryIdx, i, k);

                // Skip if coefficient is 0
                if (slice == 0) continue;

                // Special case for coefficient = 1
                if (slice == 1) {
                    for (int j = 0; j < blockBytes; j++) {
                        recoveryBlocks[recoveryOffset + j] ^= data[i][j];
                    }
                    continue;
                }

                // Process using bit-level operations like in C++ version
                for (int bitY = 0; bitY < 8; bitY++) {
                    int destOffset = recoveryOffset + bitY * subbytes;

                    for (int bitX = 0; bitX < 8; bitX++) {
                        if ((slice & (1 << bitX)) != 0) {
                            int srcOffset = bitX * subbytes;

                            for (int j = 0; j < subbytes; j++) {
                                recoveryBlocks[destOffset + j] ^= data[i][srcOffset + j];
                            }
                        }
                    }

                    // Calculate next slice (multiply by 2 in GF(256))
                    slice = GF256.mul(slice, (byte)2);
                }
            }
        }
    }

    /**
     * Gets an element from the Cauchy matrix, using pre-computed tables when available
     */
    private static byte getCauchyMatrixElement(int row, int col, int k) {
        if (row == 0) {
            // First row is all 1's for simple XOR
            return 1;
        } else if (row == 1 && col < STRIDE_2) {
            // Second row from pre-computed table
            return CAUCHY_MATRIX_2[col];
        } else if (row == 2 && col < STRIDE_3) {
            // Third row from pre-computed table
            return CAUCHY_MATRIX_3[col];
        } else if (row == 3 && col < STRIDE_4) {
            // Fourth row from pre-computed table
            return CAUCHY_MATRIX_4[col];
        } else if (row == 4 && col < STRIDE_5) {
            // Fifth row from pre-computed table
            return CAUCHY_MATRIX_5[col];
        } else if (row == 5 && col < STRIDE_6) {
            // Sixth row from pre-computed table
            return CAUCHY_MATRIX_6[col];
        } else {
            // For other rows, calculate dynamically
            byte x = (byte)(row + k); // Starting from k to avoid overlap with Y
            byte y = (byte)col;
            byte sum = GF256.add(x, y);
            return GF256.inv(sum);
        }
    }

    /**
     * Decodes blocks using Cauchy Reed-Solomon
     */
    public static void decode(int k, int m, Block[] blocks, int blockBytes) {
        // Check parameters
        if (k <= 0 || m <= 0 || k + m > 256 || blockBytes <= 0 || blockBytes % 8 != 0) {
            throw new CauchyException.InvalidParametersException(
                    "Invalid parameters: k=" + k + ", m=" + m + ", blockBytes=" + blockBytes);
        }

        // Check blocks array
        if (blocks == null || blocks.length < k) {
            throw new CauchyException.NullDataException("Blocks array is null or too short");
        }

        // Ensure that the GF256 context is initialized
        if (!gf256Init) {
            throw new CauchyException.UninitializedContextException(
                    "GF256 context not initialized. Call init() first.");
        }

        // Calculate block sub-bytes (used for bit-level operations)
        final int subbytes = blockBytes / 8;

        // Track which original blocks are missing
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

        // Special case: single missing block with first recovery block available
        if (missingCount == 1) {
            int missingRow = -1;
            for (int i = 0; i < k; i++) {
                if (missingOriginal[i]) {
                    missingRow = i;
                    break;
                }
            }

            // Find the first recovery block (k)
            Block recoveryBlock = null;
            for (Block block : blocks) {
                if (block != null && block.data != null && block.row == k) {
                    recoveryBlock = block;
                    break;
                }
            }

            if (recoveryBlock != null) {
                // Fast path for single missing block - just XOR recovery with available blocks
                byte[] missingData = new byte[blockBytes];
                System.arraycopy(recoveryBlock.data, 0, missingData, 0, blockBytes);

                // XOR all available original blocks
                for (Block block : blocks) {
                    if (block != null && block.data != null && block.row < k && block.row != missingRow) {
                        for (int j = 0; j < blockBytes; j++) {
                            missingData[j] ^= block.data[j];
                        }
                    }
                }

                // Add recovered block directly
                addRecoveredBlock(blocks, missingData, (byte)missingRow);
                return;
            }
        }

        // Get the list of missing original indices
        int[] missingIndices = new int[missingCount];
        int missingIndex = 0;
        for (int i = 0; i < k; i++) {
            if (missingOriginal[i]) {
                missingIndices[missingIndex++] = i;
            }
        }

        // Find recovery blocks and build a list of available recovery rows
        int[] recoveryRows = new int[m];
        Block[] recoveryBlocks = new Block[m];
        int recoveryCount = 0;

        for (Block block : blocks) {
            if (block != null && block.data != null && block.row >= k && block.row < k + m) {
                int recoveryIndex = block.row - k;
                recoveryRows[recoveryCount] = recoveryIndex;
                recoveryBlocks[recoveryCount] = block;
                recoveryCount++;

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

        // OPTIMIZATION: Special case for exactly two missing blocks and both recovery blocks available
        if (missingCount == 2 && recoveryCount >= 2 &&
                recoveryRows[0] == 0 && recoveryRows[1] == 1) {
            // Direct 2x2 recovery is faster than general case
            recoverTwoBlocks(blocks, k, blockBytes, subbytes, missingIndices, recoveryBlocks);
            return;
        }

        // General case: solve system of linear equations
        // Create coefficient matrix for the system
        byte[][] coeffMatrix = new byte[missingCount][missingCount];
        byte[][] recoveryData = new byte[missingCount][blockBytes];

        // For each recovery block we're using
        for (int i = 0; i < missingCount; i++) {
            int recoveryRow = recoveryRows[i];

            // Copy recovery data
            System.arraycopy(recoveryBlocks[i].data, 0, recoveryData[i], 0, blockBytes);

            // Subtract out contribution from available original blocks
            for (int j = 0; j < k; j++) {
                if (!missingOriginal[j]) {
                    // Find this original block
                    byte[] originalData = null;
                    for (Block block : blocks) {
                        if (block != null && block.data != null && block.row == j) {
                            originalData = block.data;
                            break;
                        }
                    }

                    if (originalData != null) {
                        byte coefficient = getCauchyMatrixElement(recoveryRow, j, k);

                        if (coefficient == 1) {
                            // Simple XOR for coefficient=1
                            for (int p = 0; p < blockBytes; p++) {
                                recoveryData[i][p] ^= originalData[p];
                            }
                        } else if (coefficient != 0) {
                            // Bit-level approach for other coefficients
                            byte slice = coefficient;

                            for (int bitY = 0; bitY < 8; bitY++) {
                                int destOffset = bitY * subbytes;

                                for (int bitX = 0; bitX < 8; bitX++) {
                                    if ((slice & (1 << bitX)) != 0) {
                                        int srcOffset = bitX * subbytes;

                                        for (int p = 0; p < subbytes; p++) {
                                            recoveryData[i][destOffset + p] ^= originalData[srcOffset + p];
                                        }
                                    }
                                }

                                // Next slice
                                slice = GF256.mul(slice, (byte)2);
                            }
                        }
                    }
                }
            }

            // Build the coefficient matrix for missing blocks
            for (int j = 0; j < missingCount; j++) {
                coeffMatrix[i][j] = getCauchyMatrixElement(recoveryRow, missingIndices[j], k);
            }
        }

        // Invert the coefficient matrix
        byte[][] invMatrix = invertMatrix(coeffMatrix);

        // For each missing block
        for (int i = 0; i < missingCount; i++) {
            int missingRow = missingIndices[i];
            byte[] missingData = new byte[blockBytes];

            // Apply the inverted matrix to recovery data to solve for missing block
            for (int j = 0; j < missingCount; j++) {
                byte coefficient = invMatrix[i][j];

                if (coefficient == 1) {
                    // Simple XOR for coefficient=1
                    for (int p = 0; p < blockBytes; p++) {
                        missingData[p] ^= recoveryData[j][p];
                    }
                } else if (coefficient != 0) {
                    // Bit-level approach for other coefficients
                    byte slice = coefficient;

                    for (int bitY = 0; bitY < 8; bitY++) {
                        int destOffset = bitY * subbytes;

                        for (int bitX = 0; bitX < 8; bitX++) {
                            if ((slice & (1 << bitX)) != 0) {
                                int srcOffset = bitX * subbytes;

                                for (int p = 0; p < subbytes; p++) {
                                    missingData[destOffset + p] ^= recoveryData[j][srcOffset + p];
                                }
                            }
                        }

                        // Next slice
                        slice = GF256.mul(slice, (byte)2);
                    }
                }
            }

            // Add recovered block
            addRecoveredBlock(blocks, missingData, (byte)missingRow);
        }
    }

    /**
     * Optimized method to recover exactly two missing blocks
     */
    private static void recoverTwoBlocks(
            Block[] blocks, int k, int blockBytes, int subbytes,
            int[] missingIndices, Block[] recoveryBlocks) {

        // Create a 2x2 matrix for the system of equations
        byte[][] matrix = new byte[2][2];
        matrix[0][0] = 1;  // First recovery block, first missing block
        matrix[0][1] = 1;  // First recovery block, second missing block
        matrix[1][0] = getCauchyMatrixElement(1, missingIndices[0], k);  // Second recovery block, first missing
        matrix[1][1] = getCauchyMatrixElement(1, missingIndices[1], k);  // Second recovery block, second missing

        // Invert the 2x2 matrix (special-cased for performance)
        byte det = GF256.add(
                GF256.mul(matrix[0][0], matrix[1][1]),
                GF256.mul(matrix[0][1], matrix[1][0])
        );
        byte invDet = GF256.inv(det);

        byte[][] invMatrix = new byte[2][2];
        invMatrix[0][0] = GF256.mul(matrix[1][1], invDet);
        invMatrix[0][1] = GF256.mul(matrix[0][1], invDet);
        invMatrix[1][0] = GF256.mul(matrix[1][0], invDet);
        invMatrix[1][1] = GF256.mul(matrix[0][0], invDet);

        // Process recovery blocks to remove contribution from available blocks
        byte[][] recoveryData = new byte[2][blockBytes];

        for (int i = 0; i < 2; i++) {
            // Copy recovery data
            System.arraycopy(recoveryBlocks[i].data, 0, recoveryData[i], 0, blockBytes);

            // Subtract contribution from available original blocks
            for (int j = 0; j < k; j++) {
                if (j != missingIndices[0] && j != missingIndices[1]) {
                    // Find original block
                    for (Block block : blocks) {
                        if (block != null && block.data != null && block.row == j) {
                            // Apply coefficient to original data
                            byte coefficient = (i == 0) ? (byte)1 : getCauchyMatrixElement(1, j, k);

                            if (coefficient == 1) {
                                // Simple XOR
                                for (int p = 0; p < blockBytes; p++) {
                                    recoveryData[i][p] ^= block.data[p];
                                }
                            } else if (coefficient != 0) {
                                // Use bit-level operations
                                byte slice = coefficient;
                                for (int bitY = 0; bitY < 8; bitY++) {
                                    int destOffset = bitY * subbytes;

                                    for (int bitX = 0; bitX < 8; bitX++) {
                                        if ((slice & (1 << bitX)) != 0) {
                                            int srcOffset = bitX * subbytes;

                                            for (int p = 0; p < subbytes; p++) {
                                                recoveryData[i][destOffset + p] ^= block.data[srcOffset + p];
                                            }
                                        }
                                    }

                                    // Next slice
                                    slice = GF256.mul(slice, (byte)2);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        // Now recoveryData[0] and recoveryData[1] contain linear combinations of missing blocks
        // Apply inverse matrix to solve for missing blocks
        for (int i = 0; i < 2; i++) {
            byte[] missingData = new byte[blockBytes];

            // Apply first coefficient from inverse matrix
            byte coef1 = invMatrix[i][0];
            if (coef1 == 1) {
                // Simple XOR
                for (int j = 0; j < blockBytes; j++) {
                    missingData[j] ^= recoveryData[0][j];
                }
            } else if (coef1 != 0) {
                // Use bit-level operations
                byte slice = coef1;
                for (int bitY = 0; bitY < 8; bitY++) {
                    int destOffset = bitY * subbytes;

                    for (int bitX = 0; bitX < 8; bitX++) {
                        if ((slice & (1 << bitX)) != 0) {
                            int srcOffset = bitX * subbytes;

                            for (int j = 0; j < subbytes; j++) {
                                missingData[destOffset + j] ^= recoveryData[0][srcOffset + j];
                            }
                        }
                    }

                    // Next slice
                    slice = GF256.mul(slice, (byte)2);
                }
            }

            // Apply second coefficient from inverse matrix
            byte coef2 = invMatrix[i][1];
            if (coef2 == 1) {
                // Simple XOR
                for (int j = 0; j < blockBytes; j++) {
                    missingData[j] ^= recoveryData[1][j];
                }
            } else if (coef2 != 0) {
                // Use bit-level operations
                byte slice = coef2;
                for (int bitY = 0; bitY < 8; bitY++) {
                    int destOffset = bitY * subbytes;

                    for (int bitX = 0; bitX < 8; bitX++) {
                        if ((slice & (1 << bitX)) != 0) {
                            int srcOffset = bitX * subbytes;

                            for (int j = 0; j < subbytes; j++) {
                                missingData[destOffset + j] ^= recoveryData[1][srcOffset + j];
                            }
                        }
                    }

                    // Next slice
                    slice = GF256.mul(slice, (byte)2);
                }
            }

            // Add the recovered block
            addRecoveredBlock(blocks, missingData, (byte)missingIndices[i]);
        }
    }


    /**
     * Add a recovered block to the blocks array
     */
    private static void addRecoveredBlock(Block[] blocks, byte[] data, byte row) {
        boolean blockFound = false;
        for (int j = 0; j < blocks.length; j++) {
            if (blocks[j] == null || blocks[j].data == null) {
                blocks[j] = new Block(data, row);
                blockFound = true;
                break;
            }
        }

        if (!blockFound) {
            throw new CauchyException.BlockBufferException(
                    "No space in blocks array for recovered data");
        }
    }


    /**
     * Inverts a square matrix in GF(256)
     */
    private static byte[][] invertMatrix(byte[][] matrix) {
        // Your existing implementation is already good
        int size = matrix.length;
        if (size == 0 || matrix[0].length != size) {
            throw new CauchyException.MatrixOperationException("Failed to invert recovery matrix");
        }

        // Create augmented matrix [A|I]
        byte[][] aug = new byte[size][size * 2];
        for (int i = 0; i < size; i++) {
            System.arraycopy(matrix[i], 0, aug[i], 0, size);
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
                throw new CauchyException.MatrixOperationException("Failed to invert recovery matrix");
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
            byte pivotInv = GF256.inv(pivot);
            for (int j = 0; j < size * 2; j++) {
                aug[i][j] = GF256.mul(aug[i][j], pivotInv);
            }

            // Eliminate other rows
            for (int j = 0; j < size; j++) {
                if (j != i) {
                    byte factor = aug[j][i];
                    for (int k = 0; k < size * 2; k++) {
                        aug[j][k] ^= GF256.mul(aug[i][k], factor);
                    }
                }
            }
        }

        // Extract inverse from augmented matrix
        byte[][] inverse = new byte[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(aug[i], size, inverse[i], 0, size);
        }

        return inverse;
    }

    /**
     * Block class for data storage
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