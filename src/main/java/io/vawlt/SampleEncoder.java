package io.vawlt;

import io.vawlt.cauchy.Cauchy256;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class SampleEncoder {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: SampleEncoder <file_path> [k] [m]");
      System.out.println("  k: Number of data blocks (default: 4)");
      System.out.println("  m: Number of recovery blocks (default: 2)");
      return;
    }

    // Parse input parameters
    String filePath = args[0];
    int k = (args.length > 1) ? Integer.parseInt(args[1]) : 4;
    int m = (args.length > 2) ? Integer.parseInt(args[2]) : 2;

    // Initialize the Cauchy256 library
    Cauchy256.init();

    try {
      // Read the input file
      File inputFile = new File(filePath);
      if (!inputFile.exists() || !inputFile.isFile()) {
        System.err.println("File not found or not a regular file: " + filePath);
        return;
      }

      byte[] fileData = Files.readAllBytes(inputFile.toPath());
      System.out.println("File size: " + fileData.length + " bytes");

      // Calculate block size (round up to multiple of 8)
      int blockSize = (fileData.length + k - 1) / k;
      if (blockSize % 8 != 0) {
        blockSize = ((blockSize / 8) + 1) * 8;
      }

      System.out.println("Using parameters:");
      System.out.println("  k = " + k + " (data blocks)");
      System.out.println("  m = " + m + " (recovery blocks)");
      System.out.println("  Block size = " + blockSize + " bytes");

      // Create data blocks
      byte[][] dataBlocks = new byte[k][blockSize];

      // Pad the file data to fill complete blocks
      byte[] paddedData = new byte[k * blockSize];
      System.arraycopy(fileData, 0, paddedData, 0, fileData.length);

      // Split into blocks
      for (int i = 0; i < k; i++) {
        System.arraycopy(paddedData, i * blockSize, dataBlocks[i], 0, blockSize);
      }

      // Create recovery blocks
      byte[] recoveryBlocks = new byte[m * blockSize];

      // Encode
      long startTime = System.currentTimeMillis();
      Cauchy256.encode(k, m, dataBlocks, recoveryBlocks, blockSize);
      long endTime = System.currentTimeMillis();

      System.out.println("Encoding completed in " + (endTime - startTime) + " ms");

      // Write data blocks and recovery blocks to files
      String baseName = inputFile.getName();
      String baseDir = inputFile.getParent();
      if (baseDir == null)
        baseDir = ".";

      // Write original size info (to use during decoding)
      File infoFile = new File(baseDir, baseName + ".info");
      String info = fileData.length + "," + k + "," + m + "," + blockSize;
      Files.write(infoFile.toPath(), info.getBytes());
      System.out.println("Wrote file info to: " + infoFile.getAbsolutePath());

      // Write each data block
      for (int i = 0; i < k; i++) {
        File blockFile = new File(baseDir, baseName + ".d" + i);
        Files.write(blockFile.toPath(), dataBlocks[i]);
        System.out.println("Wrote data block " + i + " to: " + blockFile.getAbsolutePath());
      }

      // Write each recovery block
      for (int i = 0; i < m; i++) {
        File blockFile = new File(baseDir, baseName + ".r" + i);
        byte[] recoveryBlock =
            Arrays.copyOfRange(recoveryBlocks, i * blockSize, (i + 1) * blockSize);
        Files.write(blockFile.toPath(), recoveryBlock);
        System.out.println("Wrote recovery block " + i + " to: " + blockFile.getAbsolutePath());
      }

      System.out.println("\nEncoding complete. Data and recovery blocks saved successfully.");

    } catch (IOException e) {
      System.err.println("Error processing file: " + e.getMessage());
    }
  }
}
