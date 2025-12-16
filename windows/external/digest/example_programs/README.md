# Example Programs

This directory contains two example programs (in C and CPP) demonstrating the usage of the SHA-256 and SHA-512 header-only libraries.

## Programs

- **file_hasher.c** - C implementation using platform-native threading (pthreads/Win32)
- **file_hasher.cpp** - C++ implementation using std::async

Both programs compute SHA-256 and SHA-512 hashes of a file in parallel and display the results.

## Building

### Linux/macOS (GCC/Clang)

```bash
gcc -o file_hasher file_hasher.c -pthread -O2 -Wall -Wextra
g++ -o file_hasher_cpp file_hasher.cpp -std=c++11 -pthread -O2 -Wall -Wextra
# same command to build for android and mingw, just '-pthread' needs to be omitted
```

### Windows (MSVC)

```batch
cl /Fe:file_hasher.exe file_hasher.c /O2 /W4 /I.. /nologo
cl /Fe:file_hasher_cpp.exe file_hasher.cpp /O2 /W4 /EHsc /std:c++14 /I.. /nologo
```

## Usage

```bash
# Linux/macOS
./file_hasher <filepath>
./file_hasher_cpp <filepath>

# Windows
file_hasher.exe <filepath>
file_hasher_cpp.exe <filepath>
```

## Sample Output

```
Computing hashes for: ../sha256.h
File size: 12345 bytes

SHA-256: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
SHA-512: cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e

Computation time: 42 ms

```
