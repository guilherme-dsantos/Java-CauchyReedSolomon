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

/**
 * Java implementation of Cauchy-Reed-Solomon erasure code in GF(256)
 *
 * @author Guilherme Santos
 */
public class Cauchy256 {

    // GF256 context
    static boolean gf256Init;

    public static void init() {
        try {
            // Initialize the GF(256) math context
            System.out.println("Creating GF(256) context...");
            gf256Init = GF256.init();

        } catch (CauchyException.UninitializedContextException e) {
            throw new CauchyException.UninitializedContextException(e.getMessage());
        }
    }

    static final byte[] CAUCHY_MATRIX_2 = {
            1, (byte) 195, 2, 4, (byte) 162, 81, 8, (byte) 194, 3, 97, 6, (byte) 163, 5, 10, 12, 20, 80, 40, (byte) 235, 16,
            (byte) 146, (byte) 193, 24, 73, 48, (byte) 243, 9, 96, (byte) 160, 18, 36, (byte) 199, (byte) 182, (byte) 192, 72, (byte) 231, (byte) 186, 89, (byte) 178, 32,
            (byte) 176, 17, (byte) 166, 83, (byte) 234, (byte) 227, 69, (byte) 138, 7, (byte) 147, (byte) 161, (byte) 203, 21, 88, 65, (byte) 225, 13, (byte) 197, 11, 41,
            34, 93, 85, 91, (byte) 201, 25, (byte) 242, 44, (byte) 198, 22, 14, 82, (byte) 144, 64, (byte) 233, 117, (byte) 170, (byte) 239, (byte) 179, 49,
            99, (byte) 164, 28, (byte) 183, 68, (byte) 167, 113, 121, 67, (byte) 226, 42, 84, (byte) 154, (byte) 207, 77, (byte) 168, (byte) 215, (byte) 230, (byte) 134, (byte) 152,
            19, (byte) 211, 26, 33, 98, (byte) 202, (byte) 219, 101, (byte) 180, (byte) 241, (byte) 187, 56, 37, (byte) 251, (byte) 209, 92, (byte) 237, 90, (byte) 139, 50,
            (byte) 130, 76, 75, (byte) 174, (byte) 229, (byte) 177, 112, (byte) 249, 46, (byte) 171, (byte) 145, 38, (byte) 150, (byte) 238, 100, (byte) 128, 116, 115, (byte) 232, (byte) 196,
            87, (byte) 158, (byte) 224, (byte) 184, 45, 66, 52, 58, (byte) 190, 105, 23, 15, 30, 60, 74, 120, (byte) 200, (byte) 155, 29, (byte) 247,
            (byte) 165, (byte) 181, (byte) 210, (byte) 136, (byte) 255, (byte) 240, 79, (byte) 142, 71, (byte) 214, (byte) 250, (byte) 206, (byte) 131, 43, 103, (byte) 169, 35, 104, (byte) 228, (byte) 148,
            (byte) 213, 109, 27, (byte) 151, (byte) 188, 107, 95, 70, 86, 57, (byte) 135, 114, (byte) 218, (byte) 153, (byte) 205, (byte) 175, (byte) 191, (byte) 245, 119, (byte) 208,
            51, (byte) 140, (byte) 132, (byte) 185, (byte) 236, (byte) 248, 39, (byte) 159, (byte) 143, (byte) 129, 125, (byte) 246, (byte) 172, 54, 78, (byte) 137, 94, (byte) 217, 53, 102,
            (byte) 156, (byte) 223, 118, (byte) 204, 124, (byte) 254, 47, 106, (byte) 212, 123, 108, 61, (byte) 149, 59, (byte) 133, (byte) 253, 31, (byte) 221, (byte) 189, (byte) 173,
            122, 62, (byte) 216, 127, (byte) 141, (byte) 157, (byte) 244, (byte) 222, (byte) 252, 111, 55, 126, 110, (byte) 220
    };

    // Pre-computed bit patterns for slices
    private static boolean[][][] bitPatterns;

    // Pre-computed source offsets
    private static int[] srcOffsets;

    // Flag to check if initialization has been done
    private static boolean initialized = false;

    // Last blockBytes value used in initialization
    private static int lastBlockBytes = -1;

    /**
     * Initialize the encoder with specific parameters.
     * Call this before encoding to pre-compute lookup tables.
     *
     * @param blockBytes Size of each block in bytes
     */
    public static void initialize(int blockBytes) {
        // Skip if already initialized with same blockBytes
        if (initialized && lastBlockBytes == blockBytes) {
            return;
        }

        lastBlockBytes = blockBytes;

        // Verify block size is multiple of 8
        if (blockBytes % 8 != 0) {
            throw new IllegalArgumentException("Block size must be a multiple of 8");
        }

        // Pre-compute source offsets
        srcOffsets = new int[8];
        for (int bitX = 0; bitX < 8; bitX++) {
            srcOffsets[bitX] = bitX * (blockBytes / 8);
        }

        // Pre-compute bit patterns for all possible slice values
        bitPatterns = new boolean[256][8][8]; // [slice][bitY][bitX]
        for (int slice = 0; slice < 256; slice++) {
            byte currentSlice = (byte)slice;
            for (int bitY = 0; bitY < 8; bitY++) {
                for (int bitX = 0; bitX < 8; bitX++) {
                    bitPatterns[slice & 0xFF][bitY][bitX] = (currentSlice & (1 << bitX)) != 0;
                }
                currentSlice = GF256.mul(currentSlice, (byte)2);
            }
        }

        initialized = true;
    }

    /**
     * Optimized encoding for m=2 using pre-computed values.
     * Handles k=2 and k=3 specially.
     *
     * @param data Original data blocks (k blocks)
     * @param k Number of original blocks
     * @param recoveryBlocks Output buffer for recovery blocks (size = 2 * blockBytes)
     * @param blockBytes Size of each block in bytes
     */
    public static void encode(int k, int m, byte[][] data, byte[] recoveryBlocks, int blockBytes) {
        // Ensure initialization is done
        if (!initialized || lastBlockBytes != blockBytes) {
            initialize(blockBytes);
        }

        // Clear recovery blocks
        Arrays.fill(recoveryBlocks, (byte)0);

        // Handle common cases specially for better performance
        if (k == 2 && m==2) {
            encodeK2(data, recoveryBlocks, blockBytes);
        } else {
            encodeGeneral(data, k,m, recoveryBlocks, blockBytes);
        }
    }

    /**
     * Highly optimized encoding for exactly k=2
     */
    private static void encodeK2(byte[][] data, byte[] recoveryBlocks, int blockBytes) {
        // First recovery block - XOR of data blocks
        for (int j = 0; j < blockBytes; j++) {
            recoveryBlocks[j] = (byte)(data[0][j] ^ data[1][j]);
        }

        // Second recovery block - optimized for k=2
        // Get slices from the pre-computed matrix
        byte slice0 = CAUCHY_MATRIX_2[0];
        byte slice1 = CAUCHY_MATRIX_2[1];

        // For each bitY position
        for (int bitY = 0; bitY < 8; bitY++) {
            int destOffset = blockBytes + bitY * (blockBytes / 8);

            // Process data block 0
            for (int bitX = 0; bitX < 8; bitX++) {
                if (bitPatterns[slice0 & 0xFF][bitY][bitX]) {
                    int srcOffset = srcOffsets[bitX];
                    for (int j = 0; j < blockBytes / 8; j++) {
                        recoveryBlocks[destOffset + j] ^= data[0][srcOffset + j];
                    }
                }
            }

            // Process data block 1
            for (int bitX = 0; bitX < 8; bitX++) {
                if (bitPatterns[slice1 & 0xFF][bitY][bitX]) {
                    int srcOffset = srcOffsets[bitX];
                    for (int j = 0; j < blockBytes / 8; j++) {
                        recoveryBlocks[destOffset + j] ^= data[1][srcOffset + j];
                    }
                }
            }
        }
    }


    /**
     * General encoder for any value of k
     */
    private static void encodeGeneral(byte[][] data, int k, int m, byte[] recoveryBlocks, int blockBytes) {
        // First recovery block - XOR of all data blocks
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < blockBytes; j++) {
                recoveryBlocks[j] ^= data[i][j];
            }
        }

        if(m==1) {
            return;
        }
        // Second recovery block - using Cauchy matrix
        for (int i = 0; i < k; i++) {
            byte slice = CAUCHY_MATRIX_2[i];

            // Skip if coefficient is 0
            if (slice == 0) continue;

            // Special case for coefficient = 1
            if (slice == 1) {
                for (int j = 0; j < blockBytes; j++) {
                    recoveryBlocks[blockBytes + j] ^= data[i][j];
                }
                continue;
            }

            // Process using pre-computed bit patterns
            for (int bitY = 0; bitY < 8; bitY++) {
                int destOffset = blockBytes + bitY * (blockBytes / 8);

                for (int bitX = 0; bitX < 8; bitX++) {
                    if (bitPatterns[slice & 0xFF][bitY][bitX]) {
                        int srcOffset = srcOffsets[bitX];

                        for (int j = 0; j < blockBytes / 8; j++) {
                            recoveryBlocks[destOffset + j] ^= data[i][srcOffset + j];
                        }
                    }
                }
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

        // First row is all 1's (for simple XOR)
        Arrays.fill(matrix[0], (byte) 1);

        // For a Cauchy matrix, we need two sets of distinct elements
        // X = {x_0, x_1, ..., x_{m-1}} and Y = {y_0, y_1, ..., y_{k-1}}
        // The matrix A is defined as A_{i,j} = 1/(x_i + y_j)

        for (int i = 1; i < m; i++) {
            byte x = (byte) (i + k); // Starting from k to avoid overlap with Y

            for (int j = 0; j < k; j++) {
                byte y = (byte) j;

                // In GF(256), the inverse of (x + y) gives us the Cauchy matrix element
                byte sum = GF256.add(x, y);
                matrix[i][j] = GF256.inv(sum);
            }
        }

        return matrix;
    }

    /**
     * Decode data using Cauchy Reed-Solomon for m=1 or 2
     *
     * @param blocks Array of available blocks (must have k blocks)
     * @param k Number of original data blocks
     * @param m Number of recovery blocks (1 or 2)
     * @param blockBytes Size of each block in bytes
     * @return true if decoding was successful, false otherwise
     */
    public static void decode(int k, int m, Block[] blocks, int blockBytes) {
        // Check parameters
        if (k <= 0 || m <= 0 || k + m > 256 || blockBytes <= 0 || blockBytes % 8 != 0) {
            throw new CauchyException.InvalidParametersException(
                    "Invalid parameters: k=" + k + ", m=" + m + ", blockBytes=" + blockBytes);
        }

        // Check data pointer validity
        if (blocks == null || blocks.length < k) {
            throw new CauchyException.NullDataException("Blocks array is null or too short");
        }

        // Ensure that the GF256 context is initialized - MOVE THIS CHECK BEFORE any initialization
        if (!gf256Init) {
            throw new CauchyException.UninitializedContextException(
                    "GF256 context not initialized. Call init() first.");
        }

        // Ensure look-up tables are initialized - this should come after the gf256Init check
        if (!initialized || lastBlockBytes != blockBytes) {
            initialize(blockBytes);
        }

        // Ensure lookup tables are initialized
        if (!initialized || lastBlockBytes != blockBytes) {
            initialize(blockBytes);
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

        // Special case: m=1 and missing one block
        if (m >= 1 && missingCount == 1 && k > 1) {
            // Find the missing row
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
                // Create new block for the missing data
                byte[] missingData = new byte[blockBytes];

                // First recovery block is XOR of all data blocks
                System.arraycopy(recoveryBlock.data, 0, missingData, 0, blockBytes);

                // XOR all available original blocks
                for (Block block : blocks) {
                    if (block != null && block.data != null && block.row < k && block.row != missingRow) {
                        for (int j = 0; j < blockBytes; j++) {
                            missingData[j] ^= block.data[j];
                        }
                    }
                }

                // Add recovered block to blocks array
                boolean blockFound = false;
                for (int j = 0; j < blocks.length; j++) {
                    if (blocks[j] == null || blocks[j].data == null) {
                        blocks[j] = new Block(missingData, (byte)missingRow);
                        blockFound = true;
                        break;
                    }
                }

                if (!blockFound) {
                    throw new CauchyException.BlockBufferException(
                            "No space in blocks array for recovered data");
                }

                return;
            }
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

        // Special case optimization for m=2 and exactly two blocks missing
        if (m >= 2 && missingCount == 2 && recoveryCount == 2 &&
                recoveryRows[0] == 0 && recoveryRows[1] == 1) {
            // We have both recovery blocks and need to recover exactly two blocks
            // This is an optimized case that can use bit-level operations

            Block recoveryBlock1 = null;
            Block recoveryBlock2 = null;

            // Find the two recovery blocks
            for (Block block : blocks) {
                if (block != null && block.data != null) {
                    if (block.row == k) {
                        recoveryBlock1 = block;
                    } else if (block.row == k + 1) {
                        recoveryBlock2 = block;
                    }
                }
            }

            if (recoveryBlock1 != null && recoveryBlock2 != null) {
                // Create temporary buffers for recovery data
                byte[] r1Data = new byte[blockBytes];
                byte[] r2Data = new byte[blockBytes];

                // Copy recovery data
                System.arraycopy(recoveryBlock1.data, 0, r1Data, 0, blockBytes);
                System.arraycopy(recoveryBlock2.data, 0, r2Data, 0, blockBytes);

                // Subtract contribution from available original blocks
                for (int i = 0; i < k; i++) {
                    if (!missingOriginal[i]) {
                        // Find original block
                        for (Block block : blocks) {
                            if (block != null && block.data != null && block.row == i) {
                                // Subtract from first recovery block (XOR)
                                for (int j = 0; j < blockBytes; j++) {
                                    r1Data[j] ^= block.data[j];
                                }

                                // Subtract from second recovery block (matrix-based)
                                byte coefficient = CAUCHY_MATRIX_2[i];
                                if (coefficient == 1) {
                                    for (int j = 0; j < blockBytes; j++) {
                                        r2Data[j] ^= block.data[j];
                                    }
                                } else if (coefficient != 0) {
                                    // Bit-level approach for better performance
                                    byte slice = coefficient;
                                    for (int bitY = 0; bitY < 8; bitY++) {
                                        int destOffset = bitY * (blockBytes / 8);

                                        for (int bitX = 0; bitX < 8; bitX++) {
                                            if ((slice & (1 << bitX)) != 0) {
                                                int srcOffset = srcOffsets[bitX];

                                                for (int j = 0; j < blockBytes / 8; j++) {
                                                    r2Data[destOffset + j] ^= block.data[srcOffset + j];
                                                }
                                            }
                                        }

                                        // Calculate next slice
                                        slice = GF256.mul(slice, (byte)2);
                                    }
                                }

                                break;
                            }
                        }
                    }
                }

                // Now r1Data and r2Data contain the linear combinations of the two missing blocks
                // Create a 2x2 matrix for the system of equations
                byte[][] subMatrix = new byte[2][2];
                subMatrix[0][0] = 1; // First row for first missing block
                subMatrix[0][1] = 1; // First row for second missing block
                subMatrix[1][0] = CAUCHY_MATRIX_2[missingIndices[0]]; // Second row for first missing block
                subMatrix[1][1] = CAUCHY_MATRIX_2[missingIndices[1]]; // Second row for second missing block

                // Invert the 2x2 matrix
                byte[][] invSubMatrix = invertMatrix(subMatrix);

                // Solve for missing blocks
                for (int i = 0; i < 2; i++) {
                    byte[] missingData = new byte[blockBytes];
                    int missingRow = missingIndices[i];

                    // Apply first coefficient
                    byte coef1 = invSubMatrix[i][0];
                    if (coef1 == 1) {
                        for (int j = 0; j < blockBytes; j++) {
                            missingData[j] ^= r1Data[j];
                        }
                    } else if (coef1 != 0) {
                        byte slice = coef1;
                        for (int bitY = 0; bitY < 8; bitY++) {
                            int destOffset = bitY * (blockBytes / 8);

                            for (int bitX = 0; bitX < 8; bitX++) {
                                if ((slice & (1 << bitX)) != 0) {
                                    int srcOffset = srcOffsets[bitX];

                                    for (int j = 0; j < blockBytes / 8; j++) {
                                        missingData[destOffset + j] ^= r1Data[srcOffset + j];
                                    }
                                }
                            }

                            // Calculate next slice
                            slice = GF256.mul(slice, (byte)2);
                        }
                    }

                    // Apply second coefficient
                    byte coef2 = invSubMatrix[i][1];
                    if (coef2 == 1) {
                        for (int j = 0; j < blockBytes; j++) {
                            missingData[j] ^= r2Data[j];
                        }
                    } else if (coef2 != 0) {
                        byte slice = coef2;
                        for (int bitY = 0; bitY < 8; bitY++) {
                            int destOffset = bitY * (blockBytes / 8);

                            for (int bitX = 0; bitX < 8; bitX++) {
                                if ((slice & (1 << bitX)) != 0) {
                                    int srcOffset = srcOffsets[bitX];

                                    for (int j = 0; j < blockBytes / 8; j++) {
                                        missingData[destOffset + j] ^= r2Data[srcOffset + j];
                                    }
                                }
                            }

                            // Calculate next slice
                            slice = GF256.mul(slice, (byte)2);
                        }
                    }

                    // Create a new block for the missing data
                    Block missingBlock = new Block(missingData, (byte)missingRow);

                    // Add to the main blocks array
                    boolean blockFound = false;
                    for (int j = 0; j < blocks.length; j++) {
                        if (blocks[j] == null || blocks[j].data == null) {
                            blocks[j] = missingBlock;
                            blockFound = true;
                            break;
                        }
                    }

                    if (!blockFound) {
                        throw new CauchyException.BlockBufferException(
                                "No space in blocks array for recovered data");
                    }
                }

                return;
            }
        }

        // Fall back to your existing matrix-based approach for general case
        // Generate the Cauchy matrix
        byte[][] cauchyMatrix = generateCauchyMatrix(k, m);

        // Create a submatrix containing just the needed coefficients
        byte[][] subMatrix = new byte[missingCount][missingCount];
        for (int i = 0; i < missingCount; i++) {
            for (int j = 0; j < missingCount; j++) {
                subMatrix[i][j] = cauchyMatrix[recoveryRows[i]][missingIndices[j]];
            }
        }

        // Invert the submatrix to solve the linear system
        byte[][] invSubMatrix = invertMatrix(subMatrix);

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
                byte[] recoveryData = getRecoveryData(blocks, recoveryRow + k, "Recovery block data unexpectedly null");

                // Create a temporary copy of the recovery data
                byte[] recoveryTemp = new byte[blockBytes];
                System.arraycopy(recoveryData, 0, recoveryTemp, 0, blockBytes);

                // Subtract out the contribution from available original data blocks
                for (int l = 0; l < k; l++) {
                    if (!missingOriginal[l]) {
                        // Find the original data
                        byte[] originalData = getRecoveryData(blocks, l, "Original block data unexpectedly null");

                        // Subtract the contribution: recovery -= original * coefficient
                        byte coefficient = cauchyMatrix[recoveryRow][l];
                        if (coefficient == 1) {
                            for (int p = 0; p < blockBytes; p++) {
                                recoveryTemp[p] ^= originalData[p];
                            }
                        } else if (coefficient != 0) {
                            // Use bit-level operations for better performance
                            if ((coefficient & 0xFF) < 32) {
                                // Use pre-computed bit patterns
                                byte slice = coefficient;
                                for (int bitY = 0; bitY < 8; bitY++) {
                                    int destOffset = bitY * (blockBytes / 8);

                                    for (int bitX = 0; bitX < 8; bitX++) {
                                        if (bitPatterns[slice & 0xFF][bitY][bitX]) {
                                            int srcOffset = srcOffsets[bitX];

                                            for (int p = 0; p < blockBytes / 8; p++) {
                                                recoveryTemp[destOffset + p] ^= originalData[srcOffset + p];
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Fall back to byte-level multiplication
                                for (int p = 0; p < blockBytes; p++) {
                                    byte product = GF256.mul(originalData[p], coefficient);
                                    recoveryTemp[p] ^= product;
                                }
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
                    // Use bit-level operations for better performance
                    if ((coefficient & 0xFF) < 32) {
                        // Use pre-computed bit patterns
                        byte slice = coefficient;
                        for (int bitY = 0; bitY < 8; bitY++) {
                            int destOffset = bitY * (blockBytes / 8);

                            for (int bitX = 0; bitX < 8; bitX++) {
                                if (bitPatterns[slice & 0xFF][bitY][bitX]) {
                                    int srcOffset = srcOffsets[bitX];

                                    for (int p = 0; p < blockBytes / 8; p++) {
                                        tempBuffer[destOffset + p] ^= recoveryTemp[srcOffset + p];
                                    }
                                }
                            }
                        }
                    } else {
                        // Fall back to byte-level multiplication
                        for (int p = 0; p < blockBytes; p++) {
                            byte product = GF256.mul(recoveryTemp[p], coefficient);
                            tempBuffer[p] ^= product;
                        }
                    }
                }
            }

            // Find or create a block for the recovered data
            boolean blockFound = false;
            for (int j = 0; j < blocks.length; j++) {
                if (blocks[j] == null || blocks[j].data == null) {
                    blocks[j] = new Block(tempBuffer, (byte) missingCol);
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


    private static byte[] getRecoveryData(
            Block[] blocks, int recoveryRow, String Recovery_block_data_unexpectedly_null) {
        byte[] recoveryData = null;
        for (Block block : blocks) {
            if (block != null && block.data != null && block.row == recoveryRow) {
                recoveryData = block.data;
                break;
            }
        }

        if (recoveryData == null) {
            throw new CauchyException.BlockBufferException(Recovery_block_data_unexpectedly_null);
        }
        return recoveryData;
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
