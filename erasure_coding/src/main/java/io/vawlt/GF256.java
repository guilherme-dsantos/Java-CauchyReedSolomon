package io.vawlt;

import java.util.logging.Logger;

import io.vawlt.CauchyException.UninitializedContextException;

/**
 * Java implementation of Cauchy-Reed-Solomon erasure code in GF(256)
 *
 * <p>Based on the GitHub repository: longhair Repository URL: <a
 * href="https://github.com/catid/longhair/blob/master/gf256.cpp">...</a>
 *
 * <p>
 */
public class GF256 {

  static final int GF256_GEN_POLY_COUNT = 16;
  // Available generator polynomials for GF(2^8)
  static final int[] GF256_GEN_POLY = {
    0x8e, 0x95, 0x96, 0xa6, 0xaf, 0xb1, 0xb2, 0xb4, 0xb8, 0xc3, 0xc6, 0xd4, 0xe1, 0xe7, 0xf3, 0xfa
  };
  static final int DEFAULT_POLYNOMIAL_INDEX = 3;
  // Table data
  static final int[][] gf256MulTable = new int[256][256]; // Multiplication table [256][256]
  static final int[][] gf256DivTable = new int[256][256]; // Division table [256][256]
  static final int[] gf256InvTable = new int[256]; // Inverse table [256]
  static final int[] gf256LogTable = new int[256]; // Log table [256]
  static final int[] gf256ExpTable = new int[512 * 2 + 1]; // Exp table [512*2+1]
  // Selected polynomial
  static int polynomial;

  static Logger logger = Logger.getLogger(GF256.class.getName());

  /** Initialize the GF(256) tables */
  public static boolean init() throws UninitializedContextException {

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

    return true;
  }

  /** Select which polynomial to use */
  static void polyInit(int polynomialIndex) throws UninitializedContextException {
    if (polynomialIndex < 0 || polynomialIndex >= GF256_GEN_POLY_COUNT)
      polynomialIndex = DEFAULT_POLYNOMIAL_INDEX;

    polynomial = (GF256_GEN_POLY[polynomialIndex] << 1) | 1;
  }

  /** Construct EXP and LOG tables from polynomial */
  static void expLogInit() throws UninitializedContextException {
    int poly = polynomial;

    // Initialize log table entry for 0
    gf256LogTable[0] = 512;
    gf256ExpTable[0] = 1;

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
  }

  /** Initialize MUL and DIV tables using LOG and EXP tables */
  static void mulDivInit() throws UninitializedContextException {
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
        if (divIndex < 0) divIndex += 255;

        gf256MulTable[y][x] = gf256ExpTable[mulIndex] & 0xFF;
        gf256DivTable[y][x] = gf256ExpTable[divIndex] & 0xFF;
      }
    }
  }

  /** Initialize INV table using DIV table */
  static void invInit() throws UninitializedContextException {
    for (int x = 0; x < 256; ++x) {
      gf256InvTable[x] = div((byte) 1, (byte) x) & 0xFF;
    }
  }

  // ------------------------------------------------------------------------------
  // Math Operations

  /** Add in GF(256): x + y */
  static byte add(byte x, byte y) {
    return (byte) (x ^ y);
  }

  /**
   * Multiply in GF(256): x * y For repeated multiplication by a constant, it is faster to put the
   * constant in y.
   */
  static byte mul(byte x, byte y) {
    return (byte) gf256MulTable[y & 0xFF][x & 0xFF];
  }

  /** Divide in GF(256): x / y Memory-access optimized for constant divisors in y. */
  static byte div(byte x, byte y) {
    return (byte) gf256DivTable[y & 0xFF][x & 0xFF];
  }

  /** Inverse in GF(256): 1 / x */
  static byte inv(byte x) {
    return (byte) gf256InvTable[x & 0xFF];
  }
}
