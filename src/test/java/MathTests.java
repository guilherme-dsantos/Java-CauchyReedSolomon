import io.vawlt.cauchy.GF256;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MathTests {

  GF256 gf256Ctx = new GF256();

  @Test
  @DisplayName("Test add method with zero values")
  void testAddWithZero() {
    assertEquals((byte) 0, gf256Ctx.add((byte) 0, (byte) 0), "0 XOR 0 is 0");
    assertEquals((byte) 5, gf256Ctx.add((byte) 5, (byte) 0), "5 XOR 0 is 5");
    assertEquals((byte) 10, gf256Ctx.add((byte) 0, (byte) 10), "0 XOR 10 is 10");
  }

  @Test
  @DisplayName("Test add method with same values")
  void testAddWithSameValues() {
    assertEquals((byte) 0, gf256Ctx.add((byte) 5, (byte) 5), "Same values XORed is 0");
    assertEquals((byte) 0, gf256Ctx.add((byte) 127, (byte) 127), "Same values XORed is 0");
    assertEquals((byte) 0, gf256Ctx.add((byte) -128, (byte) -128), "Same values XORed is 0");
  }

  @Test
  @DisplayName("Test add method with positive values")
  void testAddWithPositiveValues() {
    assertEquals((byte) 3, gf256Ctx.add((byte) 1, (byte) 2), "1 XOR 2 is 3");
    assertEquals((byte) 15, gf256Ctx.add((byte) 10, (byte) 5), "10 XOR 5 is 15");
    assertEquals((byte) 124, gf256Ctx.add((byte) 31, (byte) 99), "31 XOR 99 is 124");
  }

  @Test
  @DisplayName("Test add method with negative values")
  void testAddWithNegativeValues() {
    assertEquals((byte) -1, gf256Ctx.add((byte) -5, (byte) 4), "-5 XOR 4 is -1");
    assertEquals((byte) -128, gf256Ctx.add((byte) -127, (byte) 1), "-127 XOR 1 is -128");
    assertEquals((byte) 1, gf256Ctx.add((byte) -10, (byte) -9), "-10 XOR -9 is 1");
  }

  @Test
  @DisplayName("Test add method with edge cases")
  void testAddWithEdgeCases() {
    assertEquals((byte) -1, gf256Ctx.add((byte) 127, (byte) -128),
        "Max positive XOR Max negative is -1");
    assertEquals((byte) -1, gf256Ctx.add((byte) -1, (byte) 0), "-1 XOR 0 is -1");
    assertEquals((byte) 0, gf256Ctx.add((byte) -1, (byte) -1), "-1 XOR -1 is 0");
  }

}
