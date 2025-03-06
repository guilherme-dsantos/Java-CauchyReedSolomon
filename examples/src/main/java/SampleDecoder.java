import io.vawlt.Cauchy256;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SampleDecoder {

    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: SampleDecoder <file_path>");
            return;
        }

        // Parse input parameters
        String basePath = args[0];

        // Initialize the Cauchy256 library
        Cauchy256.init();

        try {

            File infoFile = new File(basePath + ".info");
            if (!infoFile.exists()) {
                System.err.println("Info file not found: " + infoFile.getAbsolutePath());
                return;
            }

            // Read info file
            String infoStr = new String(Files.readAllBytes(infoFile.toPath()));
            String[] infoParts = infoStr.split(",");
            if (infoParts.length < 4) {
                System.err.println("Invalid info file format");
                return;
            }

            int originalSize = Integer.parseInt(infoParts[0]);
            int k = Integer.parseInt(infoParts[1]);
            int m = Integer.parseInt(infoParts[2]);
            int blockSize = Integer.parseInt(infoParts[3]);

            System.out.println("\nFile info:");
            System.out.println("Original size: " + originalSize + " bytes");
            System.out.println("Data blocks (k): " + k);
            System.out.println("Recovery blocks (m): " + m);
            System.out.println("Block size: " + blockSize + " bytes");

            // Create blocks array with enough space for all blocks
            Cauchy256.Block[] blocks = new Cauchy256.Block[k + m];

            // Track which data blocks we found
            boolean[] dataBlockFound = new boolean[k];
            int blocksFound = 0;

            // Try to read data blocks
            System.out.println("\nScanning for available data blocks...");
            for (int i = 0; i < k; i++) {
                File blockFile = new File(basePath + ".d" + i);
                if (blockFile.exists() && blockFile.isFile()) {
                    byte[] blockData = Files.readAllBytes(blockFile.toPath());
                    if (blockData.length == blockSize) {
                        // Find first empty slot in blocks array
                        for (int j = 0; j < blocks.length; j++) {
                            if (blocks[j] == null) {
                                blocks[j] = new Cauchy256.Block(blockData, (byte) i);
                                blocksFound++;
                                dataBlockFound[i] = true;
                                System.out.println("Found data block " + i);
                                break;
                            }
                        }
                    }
                }
            }

            // If we need more blocks, try to read recovery blocks
            System.out.println("\nScanning for available recovery blocks...");
            int recoveryBlocksNeeded = k - blocksFound;
            int recoveryBlocksFound = 0;

            for (int i = 0; i < m && recoveryBlocksFound < recoveryBlocksNeeded; i++) {
                File blockFile = new File(basePath + ".r" + i);
                if (blockFile.exists() && blockFile.isFile()) {
                    byte[] blockData = Files.readAllBytes(blockFile.toPath());
                    if (blockData.length == blockSize) {
                        // Find first empty slot in blocks array
                        for (int j = 0; j < blocks.length; j++) {
                            if (blocks[j] == null) {
                                blocks[j] = new Cauchy256.Block(blockData, (byte) (k + i));
                                recoveryBlocksFound++;
                                System.out.println("Found recovery block " + i + " (row " + (k + i) + ")");
                                break;
                            }
                        }
                    }
                }
            }

            // Check if we have enough blocks
            if (blocksFound + recoveryBlocksFound < k) {
                System.err.println("\nNot enough blocks available for decoding.");
                System.err.println(
                        "Have: " + blocksFound + " data blocks, " + recoveryBlocksFound + " recovery blocks");
                System.err.println("Need: " + k + " total blocks");
                return;
            }

            // Decode
            System.out.println("\nDecoding...");
            long startTime = System.currentTimeMillis();
            Cauchy256.decode(k, m, blocks, blockSize);
            long endTime = System.currentTimeMillis();

            System.out.println("Decoding completed in " + (endTime - startTime) + " ms");

            // Rebuild the file
            byte[] reconstructedData = new byte[originalSize];

            // After decoding, we need to find all data blocks (rows 0 to k-1)
            // and copy them to the right positions in the output file
            for (int dataIndex = 0; dataIndex < k; dataIndex++) {
                boolean found = false;

                // Look for the block with row = dataIndex
                for (Cauchy256.Block block : blocks) {
                    if (block != null && block.data != null && block.row == dataIndex) {
                        // Calculate where in the output file this block goes
                        int copyLength = Math.min(blockSize, originalSize - dataIndex * blockSize);
                        if (copyLength > 0) {
                            System.arraycopy(block.data, 0, reconstructedData, dataIndex * blockSize, copyLength);
                        }
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.err.println("Error: Missing data block " + dataIndex + " after decoding");
                    return;
                }
            }

            // Write reconstructed file
            File outputFile = new File(basePath + ".reconstructed");
            Files.write(outputFile.toPath(), reconstructedData);

            System.out.println("\nFile successfully reconstructed: " + outputFile.getAbsolutePath());

        } catch (IOException | NumberFormatException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}