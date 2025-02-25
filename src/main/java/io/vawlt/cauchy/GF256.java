package io.vawlt.cauchy;

import java.util.logging.Logger;

public class GF256 {

  static final int GF256_GEN_POLY_COUNT = 16;
  // Available generator polynomials for GF(2^8)
  private static final int[] GF256_GEN_POLY = {0x8e, 0x95, 0x96, 0xa6, 0xaf, 0xb1, 0xb2, 0xb4, 0xb8,
      0xc3, 0xc6, 0xd4, 0xe1, 0xe7, 0xf3, 0xfa};
  public static final int DEFAULT_POLYNOMIAL_INDEX = 3;
  // Table data
  private final int[][] gf256MulTable; // Multiplication table [256][256]
  private final int[][] gf256DivTable; // Division table [256][256]
  private final int[] gf256InvTable; // Inverse table [256]
  private final int[] gf256LogTable; // Log table [256]
  private final int[] gf256ExpTable; // Exp table [512*2+1]
  // Selected polynomial
  public int polynomial;

  Logger logger = Logger.getLogger(getClass().getName());

  public GF256() {
    try {

      // Mul/Div/Inv/Sqr tables
      logger.info("Allocating GF(256) tables...");
      gf256MulTable = new int[256][256];
      gf256DivTable = new int[256][256];
      gf256InvTable = new int[256];

      // Log/Exp tables
      gf256LogTable = new int[256];
      gf256ExpTable = new int[512 * 2 + 1];


      logger.info("GF(256) tables allocated successfully");
    } catch (Exception e) {
      logger.info("Failed to allocate GF(256) tables: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Initialize the GF(256) tables
   *
   * @return true on success, false on failure
   */

  public void init() {
    try {
      logger.info("Initializing GF(256) context...");

      // Set up polynomial
      polyInit(DEFAULT_POLYNOMIAL_INDEX);
      logger.info("Polynomial initialized: 0x" + Integer.toHexString(polynomial));

      // Generate exponential and log tables
      expLogInit();
      logger.info("Exp/Log tables initialized");

      // Generate multiplication and division tables
      mulDivInit();
      logger.info("Mul/Div tables initialized");

      // Generate inverse table
      invInit();
      logger.info("Inverse table initialized");


    } catch (Exception e) {
      logger.info("GF256 initialization failed with exception: " + e.getMessage());
      throw e;
    }
  }


  /**
   * Select which polynomial to use
   */

  public void polyInit(int polynomialIndex) {
    if (polynomialIndex < 0 || polynomialIndex >= GF256_GEN_POLY_COUNT)
      polynomialIndex = DEFAULT_POLYNOMIAL_INDEX;

    polynomial = (GF256_GEN_POLY[polynomialIndex] << 1) | 1;
  }

  /**
   * Construct EXP and LOG tables from polynomial
   */

  public void expLogInit() {
    int poly = polynomial;

    // Initialize log table entry for 0
    gf256LogTable[0] = 512;
    gf256ExpTable[0] = 1;

    try {
      for (int jj = 1; jj < 255; ++jj) {
        int next = gf256ExpTable[jj - 1] & 0xFF;
        next = (next << 1);
        if (next >= 256) {
          next ^= poly;
        }

        gf256ExpTable[jj] = next;
        gf256LogTable[gf256ExpTable[jj] & 0xFF] = jj;
      }

      gf256ExpTable[255] = gf256ExpTable[0];
      gf256LogTable[gf256ExpTable[255] & 0xFF] = 255;

      for (int jj = 256; jj < 2 * 255; ++jj) {
        gf256ExpTable[jj] = gf256ExpTable[jj % 255];
      }

      gf256ExpTable[2 * 255] = 1;

      for (int jj = 2 * 255 + 1; jj < 4 * 255; ++jj) {
        gf256ExpTable[jj] = 0;
      }
    } catch (Exception e) {
      logger.info("Exception in expLogInit at index: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Initialize MUL and DIV tables using LOG and EXP tables
   */

  public void mulDivInit() {
    // Set up y = 0 subtable
    for (int x = 0; x < 256; ++x) {
      gf256MulTable[0][x] = 0;
      gf256DivTable[0][x] = 0;
    }

    // For each other y value:
    for (int y = 1; y < 256; ++y) {
      // Calculate log(y) for mult and 255 - log(y) for div
      // Ensure we get positive values
      final int logY = gf256LogTable[y] & 0xFFFF;
      final int logYn = 255 - logY;

      // Unroll x = 0
      gf256MulTable[y][0] = 0;
      gf256DivTable[y][0] = 0;

      // Calculate x * y, x / y
      for (int x = 1; x < 256; ++x) {
        int logX = gf256LogTable[x] & 0xFFFF;

        // Ensure indices are valid
        int mulIndex = (logX + logY) % 255;

        int divIndex = (logX + logYn) % 255;
        if (divIndex < 0)
          divIndex += 255;

        gf256MulTable[y][x] = gf256ExpTable[mulIndex] & 0xFF;
        gf256DivTable[y][x] = gf256ExpTable[divIndex] & 0xFF;
      }
    }
  }

  /**
   * Initialize INV table using DIV table
   */

  public void invInit() {
    for (int x = 0; x < 256; ++x) {
      gf256InvTable[x] = div((byte) 1, (byte) x) & 0xFF;
    }
  }


  // ------------------------------------------------------------------------------
  // Math Operations

  /**
   * Add in GF(256): x + y
   */
  public byte add(byte x, byte y) {
    return (byte) (x ^ y);
  }

  /**
   * Multiply in GF(256): x * y For repeated multiplication by a constant, it is faster to put the
   * constant in y.
   */
  public byte mul(byte x, byte y) {
    return (byte) gf256MulTable[y & 0xFF][x & 0xFF];
  }

  /**
   * Divide in GF(256): x / y Memory-access optimized for constant divisors in y.
   */
  public byte div(byte x, byte y) {
    return (byte) gf256DivTable[y & 0xFF][x & 0xFF];
  }

  /**
   * Inverse in GF(256): 1 / x
   */
  public byte inv(byte x) {
    return (byte) gf256InvTable[x & 0xFF];
  }

  // ------------------------------------------------------------------------------
  // Getters (for tests)

  public int[][] getGf256MulTable() {
    return gf256MulTable;
  }

  public int[][] getGf256DivTable() {
    return gf256DivTable;
  }

  public int[] getGf256InvTable() {
    return gf256InvTable;
  }

  public int[] getGf256LogTable() {
    return gf256LogTable;
  }

  public int[] getGf256ExpTable() {
    return gf256ExpTable;
  }



}
