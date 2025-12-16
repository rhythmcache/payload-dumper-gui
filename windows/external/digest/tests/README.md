
# Running tests
## Linux/macOS

```
gcc -o test test.c -O2 -Wall -Wextra
./test
```
## Windows (MSVC)

```
cl /Fe:test.exe test.c /O2 /W4 /I.. /nologo
test.exe
```

# Expected Output

```
=======================================================
  SHA-256 and SHA-512 Comprehensive Test Suite
=======================================================

[1] NIST Test Vectors
-------------------------------------------------------
  ✓ Empty string: PASS
  ✓ Empty string: PASS
  ✓ Single 'a': PASS
  ✓ Single 'a': PASS
  ✓ String 'abc': PASS
  ✓ String 'abc': PASS
  ✓ Message digest: PASS
  ✓ Message digest: PASS
  ✓ Alphabet: PASS
  ✓ Alphabet: PASS
  ✓ Alphanumeric: PASS
  ✓ Alphanumeric: PASS
  ✓ Numeric repetition: PASS
  ✓ Numeric repetition: PASS

[2] Boundary Size Tests
-------------------------------------------------------
  ✓ SHA-256 55 bytes: PASS
  ✓ SHA-256 56 bytes: PASS
  ✓ SHA-256 63 bytes: PASS
  ✓ SHA-256 64 bytes: PASS
  ✓ SHA-256 65 bytes: PASS
  ✓ SHA-256 119 bytes: PASS
  ✓ SHA-256 120 bytes: PASS
  ✓ SHA-256 127 bytes: PASS
  ✓ SHA-256 128 bytes: PASS
  ✓ SHA-256 129 bytes: PASS
  ✓ SHA-512 111 bytes: PASS
  ✓ SHA-512 112 bytes: PASS
  ✓ SHA-512 127 bytes: PASS
  ✓ SHA-512 128 bytes: PASS
  ✓ SHA-512 129 bytes: PASS
  ✓ SHA-512 239 bytes: PASS
  ✓ SHA-512 240 bytes: PASS
  ✓ SHA-512 255 bytes: PASS
  ✓ SHA-512 256 bytes: PASS
  ✓ SHA-512 257 bytes: PASS

[3] Incremental vs Single-Call Tests
-------------------------------------------------------
  ✓ SHA-256 incremental 1-byte: PASS
  ✓ SHA-256 incremental chunks: PASS
  ✓ SHA-512 incremental 1-byte: PASS
  ✓ SHA-512 incremental chunks: PASS

[4] Multi-Block Tests
-------------------------------------------------------
  ✓ SHA-256 128 bytes: PASS
  ✓ SHA-512 128 bytes: PASS
  ✓ SHA-256 192 bytes: PASS
  ✓ SHA-512 192 bytes: PASS
  ✓ SHA-256 640 bytes: PASS
  ✓ SHA-512 640 bytes: PASS
  ✓ SHA-256 256 bytes: PASS
  ✓ SHA-512 256 bytes: PASS
  ✓ SHA-256 640 bytes: PASS
  ✓ SHA-512 640 bytes: PASS
  ✓ SHA-512 10240 bytes: PASS

[5] Padding Edge Cases
-------------------------------------------------------
  ✓ SHA-256 55 bytes (padding fits): PASS
  ✓ SHA-256 56 bytes (padding extra block): PASS
  ✓ SHA-512 111 bytes (padding fits): PASS
  ✓ SHA-512 112 bytes (padding extra block): PASS

[6] Memory Alignment Tests
-------------------------------------------------------
  ✓ SHA-256 offset 0: PASS
  ✓ SHA-512 offset 0: PASS
  ✓ SHA-256 offset 1: PASS
  ✓ SHA-512 offset 1: PASS
  ✓ SHA-256 offset 2: PASS
  ✓ SHA-512 offset 2: PASS
  ✓ SHA-256 offset 3: PASS
  ✓ SHA-512 offset 3: PASS
  ✓ SHA-256 offset 4: PASS
  ✓ SHA-512 offset 4: PASS
  ✓ SHA-256 offset 5: PASS
  ✓ SHA-512 offset 5: PASS
  ✓ SHA-256 offset 6: PASS
  ✓ SHA-512 offset 6: PASS
  ✓ SHA-256 offset 7: PASS
  ✓ SHA-512 offset 7: PASS

[7] Large Message Tests
-------------------------------------------------------
  ✓ SHA-256 1M 'a' characters: PASS
  ✓ SHA-512 1M 'a' characters: PASS

[8] Random Data Tests
-------------------------------------------------------
  ✓ SHA-256 random 892 bytes (consistency): PASS
  ✓ SHA-512 random 892 bytes (consistency): PASS
  ✓ SHA-256 random 774 bytes (consistency): PASS
  ✓ SHA-512 random 774 bytes (consistency): PASS
  ✓ SHA-256 random 789 bytes (consistency): PASS
  ✓ SHA-512 random 789 bytes (consistency): PASS
  ✓ SHA-256 random 230 bytes (consistency): PASS
  ✓ SHA-512 random 230 bytes (consistency): PASS
  ✓ SHA-256 random 625 bytes (consistency): PASS
  ✓ SHA-512 random 625 bytes (consistency): PASS
  ✓ SHA-256 avalanche (1 bit change): PASS
  ✓ SHA-512 avalanche (1 bit change): PASS

[9] Error Handling Tests
-------------------------------------------------------
  ✓ SHA-256 NULL ctx init: PASS
  ✓ SHA-512 NULL ctx init: PASS
  ✓ SHA-256 NULL data update: PASS
  ✓ SHA-512 NULL data update: PASS
  ✓ SHA-256 NULL hash final: PASS
  ✓ SHA-512 NULL hash final: PASS
  ✓ SHA-256 zero-length update: PASS
  ✓ SHA-512 zero-length update: PASS
  ✓ SHA-256 compare equal: PASS
  ✓ SHA-256 compare NULL: PASS
  ✓ SHA-512 compare equal: PASS
  ✓ SHA-512 compare NULL: PASS

=======================================================
  Test Summary
=======================================================
Total: 95/95 tests passed (100.0%)

✓ All tests passed!
```





