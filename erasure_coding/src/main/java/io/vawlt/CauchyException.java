package io.vawlt;

/** Base exception class for all Cauchy Reed-Solomon related errors */
public class CauchyException extends RuntimeException {

  public CauchyException(String message) {
    super(message);
  }

  /** Exception thrown when invalid parameters are provided */
  public static class InvalidParametersException extends CauchyException {
    public InvalidParametersException(String message) {
      super(message);
    }
  }

  /** Exception thrown when null data is provided */
  public static class NullDataException extends CauchyException {
    public NullDataException(String message) {
      super(message);
    }
  }

  /** Exception thrown when the context is not initialized */
  public static class UninitializedContextException extends CauchyException {
    public UninitializedContextException(String message) {
      super(message);
    }
  }

  /** Exception thrown when there are not enough blocks to recover data */
  public static class InsufficientBlocksException extends CauchyException {
    public InsufficientBlocksException(String message) {
      super(message);
    }
  }

  /** Exception thrown when matrix operations fail (e.g., matrix inversion) */
  public static class MatrixOperationException extends CauchyException {
    public MatrixOperationException(String message) {
      super(message);
    }
  }

  /** Exception thrown when there's an issue with block buffers */
  public static class BlockBufferException extends CauchyException {
    public BlockBufferException(String message) {
      super(message);
    }
  }
}
