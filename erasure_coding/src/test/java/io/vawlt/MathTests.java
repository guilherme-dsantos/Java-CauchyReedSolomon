package io.vawlt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MathTests {

  @BeforeAll
  static void setUpAll() {
    // Initialize GF256 tables once before all tests
    GF256.init();
  }

  //
  // ADD OPERATION TESTS
  //

  @Test
  @DisplayName("Test add method with zero values")
  void testAddWithZero() {
    assertEquals((byte) 0, GF256.add((byte) 0, (byte) 0), "0 XOR 0 is 0");
    assertEquals((byte) 5, GF256.add((byte) 5, (byte) 0), "5 XOR 0 is 5");
    assertEquals((byte) 10, GF256.add((byte) 0, (byte) 10), "0 XOR 10 is 10");
  }

  @Test
  @DisplayName("Test add method with same values")
  void testAddWithSameValues() {
    assertEquals((byte) 0, GF256.add((byte) 5, (byte) 5), "Same values XORed is 0");
    assertEquals((byte) 0, GF256.add((byte) 127, (byte) 127), "Same values XORed is 0");
  }

  @Test
  @DisplayName("Test add method with positive values")
  void testAddWithPositiveValues() {
    assertEquals((byte) 3, GF256.add((byte) 1, (byte) 2), "1 XOR 2 is 3");
    assertEquals((byte) 15, GF256.add((byte) 10, (byte) 5), "10 XOR 5 is 15");
    assertEquals((byte) 124, GF256.add((byte) 31, (byte) 99), "31 XOR 99 is 124");
  }

  @Test
  @DisplayName("Test add method with negative values")
  void testAddWithNegativeValues() {
    assertEquals((byte) -1, GF256.add((byte) -5, (byte) 4), "-5 XOR 4 is -1");
    assertEquals((byte) -128, GF256.add((byte) -127, (byte) 1), "-127 XOR 1 is -128");
    assertEquals((byte) 1, GF256.add((byte) -10, (byte) -9), "-10 XOR -9 is 1");
  }

  @Test
  @DisplayName("Test add method with edge cases")
  void testAddWithEdgeCases() {
    assertEquals(
        (byte) -1, GF256.add((byte) 127, (byte) -128), "Max positive XOR Max negative is -1");
  }

  //
  // MULTIPLY OPERATION TESTS
  //

  @Test
  @DisplayName("Test multiply method with zero")
  void testMultiplyWithZero() {
    // Any number multiplied by zero is zero in GF(256)
    assertEquals((byte) 0, GF256.mul((byte) 0, (byte) 0), "0 * 0 is 0");
    assertEquals((byte) 0, GF256.mul((byte) 1, (byte) 0), "1 * 0 is 0");
    assertEquals((byte) 0, GF256.mul((byte) 0, (byte) 1), "0 * 1 is 0");
    assertEquals((byte) 0, GF256.mul((byte) 127, (byte) 0), "127 * 0 is 0");
  }

  @Test
  @DisplayName("Test multiply method with one (identity)")
  void testMultiplyWithOne() {
    // One is the multiplicative identity in GF(256)
    assertEquals((byte) 1, GF256.mul((byte) 1, (byte) 1), "1 * 1 is 1");
    assertEquals((byte) 5, GF256.mul((byte) 5, (byte) 1), "5 * 1 is 5");
    assertEquals((byte) 5, GF256.mul((byte) 1, (byte) 5), "1 * 5 is 5");
    assertEquals((byte) 127, GF256.mul((byte) 127, (byte) 1), "127 * 1 is 127");
  }

  @Test
  @DisplayName("Test multiply method with powers of 2")
  void testMultiplyWithPowersOfTwo() {
    // Test multiplying by powers of 2, which is like shifting in GF(256)
    // But with wraparound based on the irreducible polynomial
    assertEquals(2, GF256.mul((byte) 1, (byte) 2) & 0xFF);
    assertEquals(4, GF256.mul((byte) 1, (byte) 4) & 0xFF);
    assertEquals(8, GF256.mul((byte) 1, (byte) 8) & 0xFF);
    assertEquals(16, GF256.mul((byte) 1, (byte) 16) & 0xFF);
    assertEquals(32, GF256.mul((byte) 1, (byte) 32) & 0xFF);
    assertEquals(64, GF256.mul((byte) 1, (byte) 64) & 0xFF);
    assertEquals(128, GF256.mul((byte) 1, (byte) 128) & 0xFF);
  }

  //
  // DIVIDE OPERATION TESTS
  //

  @Test
  @DisplayName("Test divide method with zero")
  void testDivideWithZero() {
    // Zero divided by any non-zero number is zero
    assertEquals((byte) 0, GF256.div((byte) 0, (byte) 1), "0 / 1 is 0");
    assertEquals((byte) 0, GF256.div((byte) 0, (byte) 5), "0 / 5 is 0");
    assertEquals((byte) 0, GF256.div((byte) 0, (byte) 255), "0 / 255 is 0");
  }

  @Test
  @DisplayName("Test divide method with one (identity)")
  void testDivideWithOne() {
    // Any number divided by 1 is itself
    assertEquals((byte) 0, GF256.div((byte) 0, (byte) 1), "0 / 1 is 0");
    assertEquals((byte) 1, GF256.div((byte) 1, (byte) 1), "1 / 1 is 1");
    assertEquals((byte) 5, GF256.div((byte) 5, (byte) 1), "5 / 1 is 5");
    assertEquals((byte) 127, GF256.div((byte) 127, (byte) 1), "127 / 1 is 127");
  }

  @Test
  @DisplayName("Test divide method with same value")
  void testDivideWithSameValue() {
    // Any non-zero number divided by itself is 1
    assertEquals((byte) 1, GF256.div((byte) 1, (byte) 1), "1 / 1 is 1");
    assertEquals((byte) 1, GF256.div((byte) 5, (byte) 5), "5 / 5 is 1");
    assertEquals((byte) 1, GF256.div((byte) 127, (byte) 127), "127 / 127 is 1");
  }

  @Test
  @DisplayName("Test divide method inverse relationship with multiply")
  void testDivideMultiplyRelationship() {
    // For any non-zero a, b: (a * b) / b = a
    for (int a = 1; a < 256; a += 13) {
      for (int b = 1; b < 256; b += 17) {
        byte product = GF256.mul((byte) a, (byte) b);
        byte quotient = GF256.div(product, (byte) b);
        assertEquals((byte) a, quotient, "(" + a + " * " + b + ") / " + b + " should equal " + a);
      }
    }
  }

  //
  // INVERSE OPERATION TESTS
  //

  @Test
  @DisplayName("Test inverse method with special values")
  void testInverseSpecialValues() {
    assertEquals((byte) 1, GF256.inv((byte) 1), "inv(1) is 1");
    byte inv255 = GF256.inv((byte) 255);
    assertEquals((byte) 1, GF256.mul(inv255, (byte) 255), "inv(255) * 255 should equal 1");
  }

  @Test
  @DisplayName("Test inverse method for all field elements")
  void testInverseAllElements() {
    // Every non-zero element should have an inverse that satisfies: a * inv(a) = 1
    for (int a = 1; a < 256; a++) {
      byte invA = GF256.inv((byte) a);
      assertEquals((byte) 1, GF256.mul((byte) a, invA), a + " * inv(" + a + ") should equal 1");
    }
  }

  @Test
  @DisplayName("Test inverse of inverse property")
  void testInverseOfInverse() {
    // For any non-zero a: inv(inv(a)) = a
    for (int a = 1; a < 256; a += 7) {
      byte invA = GF256.inv((byte) a);
      byte invInvA = GF256.inv(invA);
      assertEquals((byte) a, invInvA, "inv(inv(" + a + ")) should equal " + a);
    }
  }

  @Test
  @DisplayName("Test inverse relationship with division")
  void testInverseDivisionRelationship() {
    // For any non-zero a: a / b = a * inv(b)
    for (int a = 1; a < 256; a += 19) {
      for (int b = 1; b < 256; b += 23) {
        byte divResult = GF256.div((byte) a, (byte) b);
        byte invB = GF256.inv((byte) b);
        byte mulResult = GF256.mul((byte) a, invB);
        assertEquals(
            divResult, mulResult, a + " / " + b + " should equal " + a + " * inv(" + b + ")");
      }
    }
  }
}
