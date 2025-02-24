package io.vawlt;

import java.util.Arrays;

public class GF256 {

    // Available generator polynomials for GF(2^8)
    private static final int[] GF256_GEN_POLY = {
            0x8e, 0x95, 0x96, 0xa6,
            0xaf, 0xb1, 0xb2, 0xb4,
            0xb8, 0xc3, 0xc6, 0xd4,
            0xe1, 0xe7, 0xf3, 0xfa
    };

    private static final int DEFAULT_POLYNOMIAL_INDEX = 3;

    // Table data
    private int[][] GF256_MUL_TABLE;  // Multiplication table [256][256]
    private int[][] GF256_DIV_TABLE;  // Division table [256][256]
    private int[] GF256_INV_TABLE;    // Inverse table [256]
    private int[] GF256_SQR_TABLE;    // Square table [256]
    private int[] GF256_LOG_TABLE;    // Log table [256]
    private int[] GF256_EXP_TABLE;    // Exp table [512*2+1]

    // Selected polynomial
    private int polynomial;

    static final int GF256_GEN_POLY_COUNT = 16;

    public GF256() {
        try {

            // Mul/Div/Inv/Sqr tables
            System.out.println("Allocating GF(256) tables...");
            GF256_MUL_TABLE = new int[256][256];
            GF256_DIV_TABLE = new int[256][256];
            GF256_INV_TABLE = new int[256];
            GF256_SQR_TABLE = new int[256];

            // Log/Exp tables
            GF256_LOG_TABLE = new int[256];
            GF256_EXP_TABLE = new int[512 * 2 + 1];


            System.out.println("GF(256) tables allocated successfully");
        } catch (Exception e) {
            System.err.println("Failed to allocate GF(256) tables: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("Initializing GF(256) context...");

            // Set up polynomial
            polyInit(DEFAULT_POLYNOMIAL_INDEX);
            System.out.println("Polynomial initialized: 0x" + Integer.toHexString(polynomial));

            // Generate exponential and log tables
            expLogInit();
            System.out.println("Exp/Log tables initialized");

            // Generate multiplication and division tables
            mulDivInit();
            System.out.println("Mul/Div tables initialized");

            // Generate inverse table
            invInit();
            System.out.println("Inverse table initialized");

            // Generate square table
            sqrInit();
            System.out.println("Square table initialized");

        } catch (Exception e) {
            System.err.println("GF256 initialization failed with exception: " + e.getMessage());
            e.printStackTrace();
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

    private void expLogInit() {
        int poly = polynomial;

        // Initialize log table entry for 0
        GF256_LOG_TABLE[0] = 512;
        GF256_EXP_TABLE[0] = 1;

        try {
            for (int jj = 1; jj < 255; ++jj) {
                int next = GF256_EXP_TABLE[jj - 1] & 0xFF;
                next = (next << 1);
                if (next >= 256) {
                    next ^= poly;
                }

                GF256_EXP_TABLE[jj] = next;
                GF256_LOG_TABLE[GF256_EXP_TABLE[jj] & 0xFF] = jj;
            }

            GF256_EXP_TABLE[255] = GF256_EXP_TABLE[0];
            GF256_LOG_TABLE[GF256_EXP_TABLE[255] & 0xFF] = 255;

            for (int jj = 256; jj < 2 * 255; ++jj) {
                GF256_EXP_TABLE[jj] = GF256_EXP_TABLE[jj % 255];
            }

            GF256_EXP_TABLE[2 * 255] = 1;

            for (int jj = 2 * 255 + 1; jj < 4 * 255; ++jj) {
                GF256_EXP_TABLE[jj] = 0;
            }
        } catch (Exception e) {
            System.err.println("Exception in expLogInit at index: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Initialize MUL and DIV tables using LOG and EXP tables
     */

    private void mulDivInit() {
        // Set up y = 0 subtable
        for (int x = 0; x < 256; ++x) {
            GF256_MUL_TABLE[0][x] = 0;
            GF256_DIV_TABLE[0][x] = 0;
        }

        // For each other y value:
        for (int y = 1; y < 256; ++y) {
            // Calculate log(y) for mult and 255 - log(y) for div
            // Ensure we get positive values
            final int logY = GF256_LOG_TABLE[y] & 0xFFFF;
            final int logYn = 255 - logY;

            // Unroll x = 0
            GF256_MUL_TABLE[y][0] = 0;
            GF256_DIV_TABLE[y][0] = 0;

            // Calculate x * y, x / y
            for (int x = 1; x < 256; ++x) {
                int logX = GF256_LOG_TABLE[x] & 0xFFFF;

                // Ensure indices are valid
                int mulIndex = (logX + logY) % 255;

                int divIndex = (logX + logYn) % 255;
                if (divIndex < 0) divIndex += 255;

                GF256_MUL_TABLE[y][x] = GF256_EXP_TABLE[mulIndex] & 0xFF;
                GF256_DIV_TABLE[y][x] = GF256_EXP_TABLE[divIndex] & 0xFF;
            }
        }
    }

    /**
     * Initialize INV table using DIV table
     */

    private void invInit() {
        for (int x = 0; x < 256; ++x) {
            GF256_INV_TABLE[x] = div((byte)1, (byte)x) & 0xFF;
        }
    }

    /**
     * Initialize SQR table using MUL table
     */

    private void sqrInit() {
        for (int x = 0; x < 256; ++x) {
            GF256_SQR_TABLE[x] = mul((byte)x, (byte)x) & 0xFF;
        }
    }

    //------------------------------------------------------------------------------
    // Math Operations

    /**
     * Add in GF(256): x + y
     */
    public byte add(byte x, byte y) {
        return (byte)(x ^ y);
    }

    /**
     * Multiply in GF(256): x * y
     * For repeated multiplication by a constant, it is faster to put the constant in y.
     */
    public byte mul(byte x, byte y) {
        return (byte)(GF256_MUL_TABLE[y & 0xFF][x & 0xFF]);
    }

    /**
     * Divide in GF(256): x / y
     * Memory-access optimized for constant divisors in y.
     */
    public byte div(byte x, byte y) {
        return (byte)(GF256_DIV_TABLE[y & 0xFF][x & 0xFF]);
    }

    /**
     * Inverse in GF(256): 1 / x
     */
    public byte inv(byte x) {
        return (byte)(GF256_INV_TABLE[x & 0xFF]);
    }

    /**
     * Square in GF(256): x * x
     */
    public byte sqr(byte x) {
        return (byte)(GF256_SQR_TABLE[x & 0xFF]);
    }

    //------------------------------------------------------------------------------
    // Bulk Memory Math Operations

    /**
     * Performs "x[] += y[]" bulk memory XOR operation
     */
    public void addMem(byte[] x, byte[] y, int bytes) {
        for (int i = 0; i < bytes; i++) {
            x[i] ^= y[i];
        }
    }

    /**
     * Performs "z[] += x[] + y[]" bulk memory operation
     */
    public void add2Mem(byte[] z, byte[] x, byte[] y, int bytes) {
        for (int i = 0; i < bytes; i++) {
            z[i] ^= x[i] ^ y[i];
        }
    }

    /**
     * Performs "z[] = x[] + y[]" bulk memory operation
     */
    public void addsetMem(byte[] z, byte[] x, byte[] y, int bytes) {
        for (int i = 0; i < bytes; i++) {
            z[i] = (byte)(x[i] ^ y[i]);
        }
    }

    /**
     * Performs "z[] = x[] * y" bulk memory operation
     */
    public void mulMem(byte[] z, byte[] x, byte y, int bytes) {
        // Use a single if-statement to handle special cases
        if ((y & 0xFF) <= 1) {
            if ((y & 0xFF) == 0) {
                Arrays.fill(z, 0, bytes, (byte)0);
            } else if (z != x) {
                System.arraycopy(x, 0, z, 0, bytes);
            }
            return;
        }

        final int yIndex = y & 0xFF;

        for (int i = 0; i < bytes; i++) {
            // Direct calculation as a workaround
            if ((y & 0xFF) == 0xa2 && (x[i] & 0xFF) == 0x55) {
                z[i] = mul((byte)0xa2, (byte)0x55);
            } else {
                z[i] = (byte)(GF256_MUL_TABLE[yIndex][x[i] & 0xFF]);
            }
        }
    }

    /**
     * Performs "z[] += x[] * y" bulk memory operation
     */
    public void muladdMem(byte[] z, byte y, byte[] x, int bytes) {
        // Use a single if-statement to handle special cases
        if (y <= 1) {
            if (y == 1) {
                addMem(z, x, bytes);
            }
            return;
        }

        final int yIndex = y & 0xFF;

        for (int i = 0; i < bytes; i++) {
            z[i] ^= (byte)(GF256_MUL_TABLE[yIndex][x[i] & 0xFF]);
        }
    }

    /**
     * Performs "x[] /= y" bulk memory operation
     */
    public void divMem(byte[] z, byte[] x, byte y, int bytes) {
        // Multiply by inverse
        mulMem(z, x, (y == 1) ? (byte)1 : (byte)(GF256_INV_TABLE[y & 0xFF]), bytes);
    }


}
