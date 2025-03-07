# Java Cauchy-Reed-Solomon Erasure Code

A pure Java implementation of Cauchy-Reed-Solomon erasure code in GF(256).

## Overview

This implementation draws inspiration from multiple sources:

### Academic Papers
- "Optimizing Cauchy Reed-Solomon Codes for Fault-Tolerant Storage Applications" by James S. Plank and Lihao Xu (2006)
- "A Tutorial on Reed-Solomon Coding for Fault-Tolerance in RAID-like Systems" by James S. Plank (1997)

### Open Source Implementations
- Jerasure Cauchy implementation in C - [https://github.com/tsuraan/Jerasure](https://github.com/tsuraan/Jerasure)
- Longhair: Fast Cauchy Reed-Solomon Erasure Codes in C++ - [https://github.com/catid/longhair](https://github.com/catid/longhair)

This is a pure Java implementation that does not rely on SIMD instructions or native code, unlike some other implementations such as Longhair. This makes it portable across all Java platforms but may not achieve the same performance as implementations that leverage hardware-specific optimizations.

## Features

- Support for arbitrary k data blocks and m recovery blocks where k+m ≤ 256
- Fast matrix operations optimized for Java
- Block size independence (supports any block size multiple of 8 bytes)
- Robust recovery from any combination of up to m lost blocks
- Platform independence without native code dependencies

## Implementation Details

This implementation focuses on optimization for small block sizes while maintaining compatibility with standard Cauchy-Reed-Solomon coding techniques. It performs operations in the Galois Field GF(256) for byte-level encoding and provides efficient recovery mechanisms for lost data blocks.

## Usage

```java
import io.vawlt.Cauchy256;
import io.vawlt.GF256;

// Initialize the GF(256) context (only needed once)
Cauchy256.init();

// Encoding parameters
int k = 4;          // Number of data blocks
int m = 2;          // Number of recovery blocks
int blockBytes = 8; // Size of each block in bytes (must be multiple of 8)

// Create data blocks
byte[][] dataBlocks = new byte[k][blockBytes];
// ... fill data blocks with your data ...

// Create recovery blocks
byte[] recoveryBlocks = new byte[m * blockBytes];

// Encode the data
Cauchy256.encode(k, m, dataBlocks, recoveryBlocks, blockBytes);

// ... transmit or store dataBlocks and recoveryBlocks ...

// When recovering:
Cauchy256.Block[] blocks = new Cauchy256.Block[k + m];
// ... fill blocks array with available data and recovery blocks ...

// Decode to recover missing blocks
Cauchy256.decode(k, m, blocks, blockBytes);
```

## Performance Considerations

- Best suited for small block sizes
- Performance scales linearly with block size
- The implementation is optimized for Java without native dependencies
- For very large datasets, consider using smaller block sizes and more blocks

## Testing

The implementation includes comprehensive test suites for:

- Initialization and context verification
- Galois Field mathematics (addition, multiplication, division, inverse)
- Different file sizes
- Various k and m configurations
- Multiple recovery scenarios
- Parameter validation and edge cases

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
