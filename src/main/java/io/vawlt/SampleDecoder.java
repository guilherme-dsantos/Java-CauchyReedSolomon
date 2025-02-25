package io.vawlt;

import io.vawlt.cauchy.Cauchy256;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

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

      // Create blocks array
      Cauchy256.Block[] blocks = new Cauchy256.Block[k];
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
      byte[] reconstructedData = new byte[k * blockSize];

      // Sort blocks by row
      Arrays.sort(blocks, (a, b) -> a.row - b.row);

      // Copy data from blocks to reconstructed data buffer
      for (int i = 0; i < k; i++) {
        if (blocks[i].row >= k) {
          System.err.println("Error: Block " + i + " has invalid row number " + blocks[i].row);
          return;
        }
        System.arraycopy(blocks[i].data, 0, reconstructedData, blocks[i].row * blockSize,
            blockSize);
      }

      // Trim to original size
      byte[] trimmedData = Arrays.copyOf(reconstructedData, originalSize);

      // Write reconstructed file
      File outputFile = new File(basePath + ".reconstructed");
      Files.write(outputFile.toPath(), trimmedData);

      System.out.println("\nFile successfully reconstructed: " + outputFile.getAbsolutePath());

    } catch (IOException | NumberFormatException e) {
      System.err.println("Error processing file: " + e.getMessage());
      e.printStackTrace();
    }

  }
}
