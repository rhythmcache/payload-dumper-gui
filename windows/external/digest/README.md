# digest

### SHA-256 and SHA-512 Header-Only Library

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![C](https://img.shields.io/badge/language-C-blue.svg)](https://en.wikipedia.org/wiki/C_(programming_language))
[![C++](https://img.shields.io/badge/language-C%2B%2B-blue.svg)](https://isocpp.org/)

A secure, lightweight, and portable header-only implementation of SHA-256 and SHA-512 hash functions based on the FIPS 180-4 specification.

## Features
-  **Header-only** - Just include and use, no compilation needed
-  **Zero dependencies** - Only requires standard C library
-  **Cross-platform** - Works on Linux, macOS, Windows (MSVC/MinGW)
-  **Secure** - Follows FIPS 180-4 specification
-  **Well-tested** - Comprehensive test suite with 95+ tests
-  **C and C++ compatible** - Works with both languages


## Quick Start

### Basic Usage (Single Call)

```c
#include "sha256.h"
#include <stdio.h>

int main(void) {
    const char *data = "Hello, World!";
    uint8_t hash[SHA256_DIGEST_SIZE];
    char hex[65];
    
    // Compute hash
    if (sha256(data, strlen(data), hash) == SHA256_SUCCESS) {
        // Convert to hex string
        sha256_to_hex(hash, hex);
        printf("SHA-256: %s\n", hex);
    }
    
    return 0;
}
```

### Incremental Hashing

```c
#include "sha256.h"

int main(void) {
    SHA256_CTX ctx;
    uint8_t hash[SHA256_DIGEST_SIZE];
    
    // Initialize
    sha256_init(&ctx);
    
    // Add data incrementally
    sha256_update(&ctx, "Hello, ", 7);
    sha256_update(&ctx, "World!", 6);
    
    // Finalize
    sha256_final(&ctx, hash);
    
    return 0;
}
```

## Installation

Simply pick up and copy `sha256.h` and/or `sha512.h` to your project directory:

```bash
# Include in your code
#include "sha256.h"
#include "sha512.h"
```

## API Documentation

### SHA-256

#### Constants

```c
#define SHA256_BLOCK_SIZE 64        // Block size in bytes
#define SHA256_DIGEST_SIZE 32       // Output hash size in bytes

#define SHA256_SUCCESS 0             // Operation successful
#define SHA256_ERROR_NULL_POINTER 1  // NULL pointer passed
#define SHA256_ERROR_OVERFLOW 2      // Data size overflow
```

#### Data Types

```c
typedef struct {
    uint32_t state[8];
    uint64_t count;
    uint8_t buffer[SHA256_BLOCK_SIZE];
    size_t buffer_len;
} SHA256_CTX;
```

#### Functions

**Initialize Context**
```c
int sha256_init(SHA256_CTX *ctx);
```
- Initializes SHA-256 context
- Must be called before `sha256_update()`
- Returns: `SHA256_SUCCESS` or error code

**Update with Data**
```c
int sha256_update(SHA256_CTX *ctx, const void *data, size_t len);
```
- Adds data to hash computation
- Can be called multiple times
- `data` can be NULL if `len` is 0
- Returns: `SHA256_SUCCESS` or error code

**Finalize Hash**
```c
int sha256_final(SHA256_CTX *ctx, uint8_t hash[SHA256_DIGEST_SIZE]);
```
- Completes hash computation
- Context is zeroed after this call
- Returns: `SHA256_SUCCESS` or error code

**Convenience Function**
```c
int sha256(const void *data, size_t len, uint8_t hash[SHA256_DIGEST_SIZE]);
```
- Computes hash in a single call
- Equivalent to init → update → final
- Returns: `SHA256_SUCCESS` or error code

**Convert to Hex**
```c
int sha256_to_hex(const uint8_t hash[SHA256_DIGEST_SIZE], char hex[65]);
```
- Converts binary hash to hex string
- Output is null-terminated (65 bytes total)
- Returns: `SHA256_SUCCESS` or error code

**Secure Compare**
```c
int sha256_compare(const uint8_t hash1[SHA256_DIGEST_SIZE],
                   const uint8_t hash2[SHA256_DIGEST_SIZE]);
```
- Constant-time comparison
- Returns: 0 if equal, non-zero if different

### SHA-512

#### Constants

```c
#define SHA512_BLOCK_SIZE 128       // Block size in bytes
#define SHA512_DIGEST_SIZE 64       // Output hash size in bytes

#define SHA512_SUCCESS 0             // Operation successful
#define SHA512_ERROR_NULL_POINTER 1  // NULL pointer passed
#define SHA512_ERROR_OVERFLOW 2      // Data size overflow
```

#### Data Types

```c
typedef struct {
    uint64_t state[8];
    uint64_t count[2];
    uint8_t buffer[SHA512_BLOCK_SIZE];
    size_t buffer_len;
} SHA512_CTX;
```

#### Functions

SHA-512 has the same API as SHA-256, just replace `sha256` with `sha512` and use the appropriate constants.

**Example:**
```c
SHA512_CTX ctx;
uint8_t hash[SHA512_DIGEST_SIZE];
char hex[129];  // Note: 129 bytes for SHA-512

sha512_init(&ctx);
sha512_update(&ctx, data, len);
sha512_final(&ctx, hash);
sha512_to_hex(hash, hex);
```

## Usage Examples

### Hash a String

```c
#include "sha256.h"
#include <stdio.h>
#include <string.h>

int main(void) {
    const char *message = "The quick brown fox jumps over the lazy dog";
    uint8_t hash[SHA256_DIGEST_SIZE];
    char hex[65];
    
    if (sha256(message, strlen(message), hash) != SHA256_SUCCESS) {
        fprintf(stderr, "Hash computation failed\n");
        return 1;
    }
    
    sha256_to_hex(hash, hex);
    printf("Message: %s\n", message);
    printf("SHA-256: %s\n", hex);
    
    return 0;
}
```

### Hash a File

```c
#include "sha256.h"
#include <stdio.h>
#include <stdlib.h>

int hash_file(const char *filename) {
    FILE *file = fopen(filename, "rb");
    if (!file) return -1;
    
    SHA256_CTX ctx;
    uint8_t buffer[4096];
    uint8_t hash[SHA256_DIGEST_SIZE];
    char hex[65];
    size_t bytes;
    
    sha256_init(&ctx);
    
    while ((bytes = fread(buffer, 1, sizeof(buffer), file)) > 0) {
        sha256_update(&ctx, buffer, bytes);
    }
    
    sha256_final(&ctx, hash);
    sha256_to_hex(hash, hex);
    
    printf("SHA-256(%s): %s\n", filename, hex);
    
    fclose(file);
    return 0;
}
```

### Verify Hash

```c
#include "sha256.h"
#include <string.h>

int verify_password(const char *password, const uint8_t *expected_hash) {
    uint8_t computed_hash[SHA256_DIGEST_SIZE];
    
    // Compute hash of input password
    sha256(password, strlen(password), computed_hash);
    
    // Use constant-time comparison to prevent timing attacks
    if (sha256_compare(computed_hash, expected_hash) == 0) {
        return 1;  // Match
    }
    
    return 0;  // No match
}
```

### Both SHA-256 and SHA-512

```c
#include "sha256.h"
#include "sha512.h"
#include <stdio.h>

int main(void) {
    const char *data = "test";
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    char hex256[65];
    char hex512[129];
    
    sha256(data, 4, hash256);
    sha512(data, 4, hash512);
    
    sha256_to_hex(hash256, hex256);
    sha512_to_hex(hash512, hex512);
    
    printf("SHA-256: %s\n", hex256);
    printf("SHA-512: %s\n", hex512);
    
    return 0;
}
```

## Testing

The `tests/` directory contains a comprehensive test suite with 95+ tests covering:

-  NIST official test vectors
-  Boundary size tests (block boundaries)
-  Incremental vs single-call consistency
-  Multi-block processing
-  Padding edge cases
-  Memory alignment
-  Large messages
-  Random data and avalanche effect
-  Error handling

## Example Programs

The `example_programs/` directory contains two complete programs:

1. **file_hasher.c** - C implementation with cross-platform threading
2. **file_hasher.cpp** - Modern C++ with std::async

Both programs compute SHA-256 and SHA-512 hashes of files in parallel.


## Security Considerations

### What This Library Provides

- [x] Cryptographically secure hash functions (SHA-256, SHA-512)  
- [x] Constant-time comparison to prevent timing attacks  
- [x] Proper memory clearing of sensitive data  
- [x] Overflow protection for message lengths  

### What You Should Know

- **Not for password storage** - Use bcrypt, scrypt, or Argon2 instead
- **HMAC not included** - For message authentication, implement HMAC separately
- **Not a random number generator** - Don't use hashes as RNG
- **Side-channel attacks** - This implementation prioritizes correctness over side-channel resistance

## License


This repo is under the **Apache License 2.0**.

See [LICENSE](./LICENSE) file for details.

## References

- [FIPS 180-4: Secure Hash Standard (SHS)](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.180-4.pdf)
- [RFC 6234: US Secure Hash Algorithms](https://tools.ietf.org/html/rfc6234)
- [NIST CAVP Test Vectors](https://csrc.nist.gov/projects/cryptographic-algorithm-validation-program)

## FAQ

**Q: Is this library thread-safe?**  
A: Each context (`SHA256_CTX`, `SHA512_CTX`) is independent. Multiple threads can use separate contexts safely, but don't share a context between threads without synchronization.

**Q: Can I use this in commercial projects?**  
A: Yes! Apache-2.0 license allows commercial use.

**Q: Does this work on embedded systems?**  
A: Yes, it only requires standard C library. Tested on various platforms.

**Q: How do I hash a password?**  
A: **Don't use SHA-256/512 for passwords!** Use bcrypt, scrypt, or Argon2 instead. These are designed to be slow and memory-hard to prevent brute-force attacks.

**Q: Can I hash files larger than RAM?**  
A: Yes! Use incremental hashing (`init`, `update`, `final`) to process the file in chunks.
