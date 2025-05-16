/**
 * Copyright (c) 2025 Guilherme Santos.
 * Copyright (c) 2014 Christopher A. Taylor.  All rights reserved.
 * <p>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vawlt;

import java.util.Arrays;


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
                    "Invalid parameters: k=%d, m=%d, blockBytes=%d".formatted(k, m, blockBytes));
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
        final int subBytes = blockBytes / 8;

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
                byte slice = getCauchyMatrixElement(recoveryIdx, i, k, m);

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
                    int destOffset = recoveryOffset + bitY * subBytes;

                    int sliceValue = slice & 0xFF;
                    for (int bitX = 0; bitX < 8; bitX++) {
                        if ((sliceValue & (1 << bitX)) != 0) {
                            int srcOffset = bitX * subBytes;

                            for (int j = 0; j < subBytes; j++) {
                                recoveryBlocks[destOffset + j] ^= data[i][srcOffset + j];
                            }
                        }
                    }

                    // Calculate next slice (multiply by 2 in GF(256))
                    sliceValue = GF256.mul((byte)sliceValue, (byte)2) & 0xFF;
                    slice = (byte)sliceValue;
                }
            }
        }
    }

    /**
     * Decodes blocks using Cauchy Reed-Solomon
     */
    public static void decode(int k, int m, Block[] blocks, int blockBytes) {
        // Check parameters
        if (k <= 0 || m <= 0 || k + m > 256 || blockBytes <= 0 || blockBytes % 8 != 0) {
            throw new CauchyException.InvalidParametersException(
                    "Invalid parameters: k=%d, m=%d, blockBytes=%d".formatted(k, m, blockBytes));
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
        final int subBytes = blockBytes / 8;

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
        if (missingCount == 2 && recoveryRows[0] == 0 && recoveryRows[1] == 1) {
            // Direct 2x2 recovery is faster than general case
            recoverTwoBlocks(blocks, k, m,  blockBytes, subBytes, missingIndices, recoveryBlocks);
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
                        byte coefficient = getCauchyMatrixElement(recoveryRow, j, k,m);

                        if (coefficient == 1) {
                            // Simple XOR for coefficient=1
                            for (int p = 0; p < blockBytes; p++) {
                                recoveryData[i][p] ^= originalData[p];
                            }
                        } else if (coefficient != 0) {
                            // Bit-level approach for other coefficients
                            int sliceValue = coefficient & 0xFF; // Convert to unsigned int

                            for (int bitY = 0; bitY < 8; bitY++) {
                                int destOffset = bitY * subBytes;

                                for (int bitX = 0; bitX < 8; bitX++) {
                                    if ((sliceValue & (1 << bitX)) != 0) {
                                        int srcOffset = bitX * subBytes;

                                        for (int p = 0; p < subBytes; p++) {
                                            recoveryData[i][destOffset + p] ^= originalData[srcOffset + p];
                                        }
                                    }
                                }

                                // Next slice
                                sliceValue = GF256.mul((byte)sliceValue, (byte)2) & 0xFF;
                            }
                        }

                    }
                }
            }

            // Build the coefficient matrix for missing blocks
            for (int j = 0; j < missingCount; j++) {
                coeffMatrix[i][j] = getCauchyMatrixElement(recoveryRow, missingIndices[j], k,m);
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
                    int sliceValue = coefficient & 0xFF; // Convert to unsigned int

                    for (int bitY = 0; bitY < 8; bitY++) {
                        int destOffset = bitY * subBytes;

                        for (int bitX = 0; bitX < 8; bitX++) {
                            if ((sliceValue & (1 << bitX)) != 0) {
                                int srcOffset = bitX * subBytes;

                                for (int p = 0; p < subBytes; p++) {
                                    missingData[destOffset + p] ^= recoveryData[j][srcOffset + p];
                                }
                            }
                        }

                        // Next slice
                        sliceValue = GF256.mul((byte)sliceValue, (byte)2) & 0xFF;
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
            Block[] blocks, int k,int m, int blockBytes, int subbytes,
            int[] missingIndices, Block[] recoveryBlocks) {

        // Create a 2x2 matrix for the system of equations
        byte[][] matrix = new byte[2][2];
        matrix[0][0] = 1;  // First recovery block, first missing block
        matrix[0][1] = 1;  // First recovery block, second missing block
        matrix[1][0] = getCauchyMatrixElement(1, missingIndices[0], k,m);  // Second recovery block, first missing
        matrix[1][1] = getCauchyMatrixElement(1, missingIndices[1], k,m);  // Second recovery block, second missing

        // Invert the 2x2 matrix (special-cased for performance)
        byte det = GF256.add(
                GF256.mul(matrix[0][0], matrix[1][1]),
                GF256.mul(matrix[0][1], matrix[1][0])
        );

        if ((det & 0xFF) == 0) {
            throw new CauchyException.MatrixOperationException("Failed to invert recovery matrix - zero determinant");
        }

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
                            byte coefficient = (i == 0) ? (byte)1 : getCauchyMatrixElement(1, j, k,m);

                            if ((coefficient & 0xFF) == 1) {
                                // Simple XOR
                                for (int p = 0; p < blockBytes; p++) {
                                    recoveryData[i][p] ^= block.data[p];
                                }
                            } else if ((coefficient & 0xFF) != 0) {
                                // Use bit-level operations
                                int sliceValue = coefficient & 0xFF;
                                for (int bitY = 0; bitY < 8; bitY++) {
                                    int destOffset = bitY * subbytes;

                                    for (int bitX = 0; bitX < 8; bitX++) {
                                        if ((sliceValue & (1 << bitX)) != 0) {
                                            int srcOffset = bitX * subbytes;

                                            for (int p = 0; p < subbytes; p++) {
                                                recoveryData[i][destOffset + p] ^= block.data[srcOffset + p];
                                            }
                                        }
                                    }

                                    // Next slice
                                    sliceValue = GF256.mul((byte)sliceValue, (byte)2) & 0xFF;
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
            if ((coef1 & 0xFF) == 1) {
                // Simple XOR
                for (int j = 0; j < blockBytes; j++) {
                    missingData[j] ^= recoveryData[0][j];
                }
            } else if ((coef1 & 0xFF) != 0) {
                // Use bit-level operations
                int sliceValue = coef1 & 0xFF;
                for (int bitY = 0; bitY < 8; bitY++) {
                    int destOffset = bitY * subbytes;

                    for (int bitX = 0; bitX < 8; bitX++) {
                        if ((sliceValue & (1 << bitX)) != 0) {
                            int srcOffset = bitX * subbytes;

                            for (int j = 0; j < subbytes; j++) {
                                missingData[destOffset + j] ^= recoveryData[0][srcOffset + j];
                            }
                        }
                    }

                    // Next slice
                    sliceValue = GF256.mul((byte)sliceValue, (byte)2) & 0xFF;
                }
            }

            // Apply second coefficient from inverse matrix
            byte coef2 = invMatrix[i][1];
            if ((coef2 & 0xFF) == 1) {
                // Simple XOR
                for (int j = 0; j < blockBytes; j++) {
                    missingData[j] ^= recoveryData[1][j];
                }
            } else if ((coef2 & 0xFF) != 0) {
                // Use bit-level operations
                int sliceValue = coef2 & 0xFF;
                for (int bitY = 0; bitY < 8; bitY++) {
                    int destOffset = bitY * subbytes;

                    for (int bitX = 0; bitX < 8; bitX++) {
                        if ((sliceValue & (1 << bitX)) != 0) {
                            int srcOffset = bitX * subbytes;

                            for (int j = 0; j < subbytes; j++) {
                                missingData[destOffset + j] ^= recoveryData[1][srcOffset + j];
                            }
                        }
                    }

                    // Next slice
                    sliceValue = GF256.mul((byte)sliceValue, (byte)2) & 0xFF;
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

        // Special case for 2x2 matrices
        if (size == 2) {
            // For a 2x2 matrix [[a,b],[c,d]], the inverse is [[d,-b],[-c,a]]/(ad-bc) in GF(256)
            byte a = matrix[0][0];
            byte b = matrix[0][1];
            byte c = matrix[1][0];
            byte d = matrix[1][1];

            // Calculate determinant: ad-bc in GF(256)
            // In GF(256), subtraction is the same as addition (XOR)
            byte det = GF256.add(
                    GF256.mul(a, d),
                    GF256.mul(b, c)
            );

            // Check for singularity with unsigned comparison
            if ((det & 0xFF) == 0) {
                throw new CauchyException.MatrixOperationException("Failed to invert recovery matrix - zero determinant in 2x2 case");
            }

            byte invDet = GF256.inv(det);

            byte[][] inverse = new byte[2][2];
            inverse[0][0] = GF256.mul(d, invDet);
            inverse[0][1] = GF256.mul(b, invDet);
            inverse[1][0] = GF256.mul(c, invDet);
            inverse[1][1] = GF256.mul(a, invDet);

            return inverse;
        }

        // Create augmented matrix [A|I]
        byte[][] aug = new byte[size][size * 2];
        for (int i = 0; i < size; i++) {
            System.arraycopy(matrix[i], 0, aug[i], 0, size);
            aug[i][i + size] = 1;
        }

        // Perform Gaussian elimination
        for (int i = 0; i < size; i++) {
            // Find pivot
            int pivotRow = -1;
            for (int j = i; j < size; j++) {
                if ((aug[j][i] & 0xFF) != 0) {
                    pivotRow = j;
                    break;
                }
            }

            // If pivot is zero, matrix is singular
            if (pivotRow == -1) {
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
                    if ((factor & 0xFF) != 0) {
                        for (int k = 0; k < size * 2; k++) {
                            aug[j][k] ^= GF256.mul(aug[i][k], factor);
                        }
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
     * Gets an element from the Cauchy matrix, using pre-computed tables when available
     * @param row Row index (0-based)
     * @param col Column index (0-based)
     * @param k Number of original data blocks
     * @param m Number of recovery blocks
     * @return The Cauchy matrix element at the specified position
     * @throws IllegalArgumentException if the parameters are invalid or the element cannot be retrieved
     */
    private static byte getCauchyMatrixElement(int row, int col, int k, int m) throws IllegalArgumentException {
        // Validate input parameters
        if (row < 0 || col < 0 || k <= 0 || m <= 0 || k + m > 256) {
            throw new IllegalArgumentException(
                    "Invalid parameters: row=%d, col=%d, k=%d, m=%d".formatted(row, col, k, m));
        }

        // Ensure row and column are within bounds
        if (row >= m) {
            throw new IllegalArgumentException(
                    "Row index %d is out of bounds for matrix with %d rows".formatted(row, m));
        }

        if (col >= k) {
            throw new IllegalArgumentException(
                    "Column index %d is out of bounds for matrix with %d columns".formatted(col, k));
        }

        // First row is all 1's for simple XOR
        if (row == 0) {
            return 1;
        }

        // Handle precomputed tables for m ≤ 6
        if (m <= 6) {
            byte[] matrixData;
            int stride = switch (m) {
                case 2 -> {
                    matrixData = Cauchy256Tables.CAUCHY_MATRIX_2;
                    yield STRIDE_2;
                }
                case 3 -> {
                    matrixData = Cauchy256Tables.CAUCHY_MATRIX_3;
                    yield STRIDE_3;
                }
                case 4 -> {
                    matrixData = Cauchy256Tables.CAUCHY_MATRIX_4;
                    yield STRIDE_4;
                }
                case 5 -> {
                    matrixData = Cauchy256Tables.CAUCHY_MATRIX_5;
                    yield STRIDE_5;
                }
                case 6 -> {
                    matrixData = Cauchy256Tables.CAUCHY_MATRIX_6;
                    yield STRIDE_6;
                }
                default ->
                    // Shouldn't happen due to previous check, but handle for completeness
                        throw new IllegalArgumentException("Unexpected value for m: %d".formatted(m));
            };

            // Select the appropriate precomputed matrix and stride

            // Calculate the index in the precomputed matrix
            int index = switch (row) {
                case 1 -> col;
                case 2 -> stride + col;
                case 3 -> stride * 2 + col;
                case 4 -> stride * 3 + col;
                case 5 -> stride * 4 + col;
                default -> throw new IllegalArgumentException("Unexpected value for row: %d".formatted(row));
            };

            // Check if the index is within bounds of the array
            if (index < 0 || index >= matrixData.length) {
                throw new IllegalArgumentException(
                        "Index %d out of bounds for matrix data with length %d".formatted(index, matrixData.length));
            }

            return matrixData[index];
        }
        // Handle dynamically constructed matrix for m > 6
        else {
            throw new IllegalArgumentException(
                    "Invalid value of m (should be m <= 6): %d".formatted(m));
        }

    }

//    public static void printEncodingMatrix(int k, int m) {
//        System.out.printf("Cauchy Encoding Matrix for k=%d, m=%d:%n", k, m);
//
//        // Print header
//        System.out.print("    ");
//        for (int col = 0; col < k; col++) {
//            System.out.printf("Col%-2d ", col);
//        }
//        System.out.println();
//
//        // First row is special - all 1's
//        System.out.print("Row0 ");
//        for (int col = 0; col < k; col++) {
//            System.out.printf("%-5d ", 1);
//        }
//        System.out.println();
//
//        // Print remaining rows
//        for (int row = 1; row < m; row++) {
//            System.out.printf("Row%-1d ", row);
//            for (int col = 0; col < k; col++) {
//                byte element = getCauchyMatrixElement(row, col, k, m);
//                System.out.printf("0x%-3X ", element & 0xFF);
//            }
//            System.out.println();
//        }
//
//        System.out.println("\nMatrix element details:");
//        for (int row = 0; row < m; row++) {
//            for (int col = 0; col < k; col++) {
//                byte element = getCauchyMatrixElement(row, col, k, m);
//
//                // Also calculate the dynamic version for comparison
//                byte x = (byte) (row + k);
//                byte y = (byte) col;
//                byte sum = GF256.add(x, y);
//                byte dynamicElement = GF256.inv(sum);
//
//                System.out.printf("Element[%d][%d]: Table=0x%X, Dynamic=0x%X, Match=%b%n",
//                        row, col, element & 0xFF, dynamicElement & 0xFF,
//                        element == dynamicElement);
//            }
//        }
//    }

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