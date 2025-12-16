/***************************************************************************
 * SHA-256 Header-Only Implementation                                      *
 * Based on FIPS 180-4 specification                                       *
 * Secure implementation of SHA-256                                        *
 * Apache-2 License                                                        *
 *                                                                         *
 * Usage:                                                                  *
 *     SHA256_CTX ctx;                                                     *
 *     if (sha256_init(&ctx) != 0) { handle error }                        *
 *     if (sha256_update(&ctx, data, len) != 0) { handle error }           *
 *     if (sha256_final(&ctx, hash) != 0) { handle error }                 *
 *                                                                         *
 * Or single-call convenience function:                                    *
 *     if (sha256(data, len, hash) != 0) { handle error }                  *
 ***************************************************************************/

#ifndef SHA256_H
#define SHA256_H

#include <stdint.h>
#include <string.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Return codes */
#define SHA256_SUCCESS 0
#define SHA256_ERROR_NULL_POINTER 1
#define SHA256_ERROR_OVERFLOW 2

/* Public constants */
#define SHA256_BLOCK_SIZE 64
#define SHA256_DIGEST_SIZE 32

/* Context structure - treat as opaque */
typedef struct {
    uint32_t state[8];
    uint64_t count;
    uint8_t buffer[SHA256_BLOCK_SIZE];
    size_t buffer_len; /* Current bytes in buffer */
} SHA256_CTX;

/* Internal macros - do not use directly */
#define SHA256_ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define SHA256_CH(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
#define SHA256_MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define SHA256_EP0(x) (SHA256_ROTR(x, 2) ^ SHA256_ROTR(x, 13) ^ SHA256_ROTR(x, 22))
#define SHA256_EP1(x) (SHA256_ROTR(x, 6) ^ SHA256_ROTR(x, 11) ^ SHA256_ROTR(x, 25))
#define SHA256_SIG0(x) (SHA256_ROTR(x, 7) ^ SHA256_ROTR(x, 18) ^ ((x) >> 3))
#define SHA256_SIG1(x) (SHA256_ROTR(x, 17) ^ SHA256_ROTR(x, 19) ^ ((x) >> 10))

/* SHA-256 round constants */
static const uint32_t SHA256_K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

/*
    Process a single 512-bit block
    Internal function - not part of public API
*/
static inline void sha256_transform(SHA256_CTX *ctx, const uint8_t data[SHA256_BLOCK_SIZE])
{
    uint32_t a, b, c, d, e, f, g, h, t1, t2, m[64];
    int i;

    /* Prepare message schedule - expand 16 x 32-bit words to 64 */
    for (i = 0; i < 16; i++) {
        m[i] = ((uint32_t)data[i * 4 + 0] << 24) |
               ((uint32_t)data[i * 4 + 1] << 16) |
               ((uint32_t)data[i * 4 + 2] << 8) |
               ((uint32_t)data[i * 4 + 3]);
    }

    for (i = 16; i < 64; i++) {
        m[i] = SHA256_SIG1(m[i - 2]) + m[i - 7] + SHA256_SIG0(m[i - 15]) + m[i - 16];
    }

    /* Initialize working variables with current hash value */
    a = ctx->state[0];
    b = ctx->state[1];
    c = ctx->state[2];
    d = ctx->state[3];
    e = ctx->state[4];
    f = ctx->state[5];
    g = ctx->state[6];
    h = ctx->state[7];

    /* Main compression loop - 64 rounds */
    for (i = 0; i < 64; i++) {
        t1 = h + SHA256_EP1(e) + SHA256_CH(e, f, g) + SHA256_K[i] + m[i];
        t2 = SHA256_EP0(a) + SHA256_MAJ(a, b, c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }

    /* Add compressed chunk to current hash value */
    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;

    /* Clear sensitive data from stack */
    memset(m, 0, sizeof(m));
}

/*
    Initialize SHA-256 context
    Must be called before first sha256_update()
    
    @param ctx Pointer to SHA-256 context
    @return SHA256_SUCCESS on success, SHA256_ERROR_NULL_POINTER if ctx is NULL
*/
static inline int sha256_init(SHA256_CTX *ctx)
{
    if (ctx == NULL) {
        return SHA256_ERROR_NULL_POINTER;
    }

    /* Initial hash values - first 32 bits of fractional parts of square roots of first 8 primes */
    ctx->state[0] = 0x6a09e667;
    ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372;
    ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f;
    ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab;
    ctx->state[7] = 0x5be0cd19;

    ctx->count = 0;
    ctx->buffer_len = 0;
    
    return SHA256_SUCCESS;
}

/*
    Add data to SHA-256 context
    Can be called multiple times to hash data incrementally
    
    @param ctx Initialized SHA-256 context
    @param data Data to hash (can be NULL if len is 0)
    @param len Length of data in bytes
    @return SHA256_SUCCESS, SHA256_ERROR_NULL_POINTER, or SHA256_ERROR_OVERFLOW
*/
static inline int sha256_update(SHA256_CTX *ctx, const void *data, size_t len)
{
    const uint8_t *input = (const uint8_t *)data;
    size_t remaining = len;

    if (ctx == NULL) {
        return SHA256_ERROR_NULL_POINTER;
    }

    if (len == 0) {
        return SHA256_SUCCESS;
    }

    if (data == NULL) {
        return SHA256_ERROR_NULL_POINTER;
    }

    /* Check for overflow (max message size is 2^64 - 1 bits = 2^61 - 1 bytes) */
    /* First check if adding len would overflow count */
    if (ctx->count > UINT64_MAX - len) {
        return SHA256_ERROR_OVERFLOW;
    }
    
    /* Then check if the bit count would exceed 2^64 - 1 */
    /* Maximum byte count is (2^64 - 1) / 8 = 0x1FFFFFFFFFFFFFFF */
    if (ctx->count + len > 0x1FFFFFFFFFFFFFFFULL) {
        return SHA256_ERROR_OVERFLOW;
    }

    ctx->count += len;

    /* If we have data in buffer, try to fill it */
    if (ctx->buffer_len > 0) {
        size_t space_in_buffer = SHA256_BLOCK_SIZE - ctx->buffer_len;
        size_t to_copy = (remaining < space_in_buffer) ? remaining : space_in_buffer;

        memcpy(ctx->buffer + ctx->buffer_len, input, to_copy);
        ctx->buffer_len += to_copy;
        input += to_copy;
        remaining -= to_copy;

        /* If buffer is full, process it */
        if (ctx->buffer_len == SHA256_BLOCK_SIZE) {
            sha256_transform(ctx, ctx->buffer);
            ctx->buffer_len = 0;
        }
    }

    /* Process complete blocks directly from input */
    while (remaining >= SHA256_BLOCK_SIZE) {
        sha256_transform(ctx, input);
        input += SHA256_BLOCK_SIZE;
        remaining -= SHA256_BLOCK_SIZE;
    }

    /* Store any remaining bytes in buffer */
    if (remaining > 0) {
        memcpy(ctx->buffer + ctx->buffer_len, input, remaining);
        ctx->buffer_len += remaining;
    }

    return SHA256_SUCCESS;
}

/*
    Finalize SHA-256 hash computation
    After calling this, the context is zeroed and must be reinitialized
    
    @param ctx SHA-256 context
    @param hash Output buffer for 32-byte hash (must be at least SHA256_DIGEST_SIZE bytes)
    @return SHA256_SUCCESS or SHA256_ERROR_NULL_POINTER
*/
static inline int sha256_final(SHA256_CTX *ctx, uint8_t hash[SHA256_DIGEST_SIZE])
{
    size_t pad_len;
    uint64_t bit_count;
    uint8_t padding[SHA256_BLOCK_SIZE * 2];
    size_t i;

    if (ctx == NULL || hash == NULL) {
        return SHA256_ERROR_NULL_POINTER;
    }

    /* Calculate bit count before padding */
    bit_count = ctx->count * 8;

    /* Padding: 0x80 followed by zeros, then 64-bit length */
    /* We need: current data + 0x80 + zeros + 8 bytes for length = multiple of 64 */
    padding[0] = 0x80;

    if (ctx->buffer_len < 56) {
        /* Enough room in current block for padding and length */
        pad_len = 56 - ctx->buffer_len;
    } else {
        /* Need an extra block */
        pad_len = SHA256_BLOCK_SIZE + 56 - ctx->buffer_len;
    }

    /* Fill padding with zeros (after the 0x80 byte) */
    memset(padding + 1, 0, pad_len - 1);

    /* Append 64-bit length in bits (big-endian) */
    padding[pad_len + 0] = (uint8_t)(bit_count >> 56);
    padding[pad_len + 1] = (uint8_t)(bit_count >> 48);
    padding[pad_len + 2] = (uint8_t)(bit_count >> 40);
    padding[pad_len + 3] = (uint8_t)(bit_count >> 32);
    padding[pad_len + 4] = (uint8_t)(bit_count >> 24);
    padding[pad_len + 5] = (uint8_t)(bit_count >> 16);
    padding[pad_len + 6] = (uint8_t)(bit_count >> 8);
    padding[pad_len + 7] = (uint8_t)(bit_count);

    /* Process padding directly without updating count */
    /* Copy padding to buffer and process blocks */
    i = 0;
    while (i < pad_len + 8) {
        size_t space_in_buffer = SHA256_BLOCK_SIZE - ctx->buffer_len;
        size_t to_copy = (pad_len + 8 - i < space_in_buffer) ? (pad_len + 8 - i) : space_in_buffer;
        
        memcpy(ctx->buffer + ctx->buffer_len, padding + i, to_copy);
        ctx->buffer_len += to_copy;
        i += to_copy;
        
        if (ctx->buffer_len == SHA256_BLOCK_SIZE) {
            sha256_transform(ctx, ctx->buffer);
            ctx->buffer_len = 0;
        }
    }

    /* Extract hash value (big-endian) */
    for (i = 0; i < 8; i++) {
        hash[i * 4 + 0] = (uint8_t)(ctx->state[i] >> 24);
        hash[i * 4 + 1] = (uint8_t)(ctx->state[i] >> 16);
        hash[i * 4 + 2] = (uint8_t)(ctx->state[i] >> 8);
        hash[i * 4 + 3] = (uint8_t)(ctx->state[i]);
    }

    /* Clear sensitive data */
    memset(ctx, 0, sizeof(SHA256_CTX));

    return SHA256_SUCCESS;
}

/*
    Convenience function to compute SHA-256 hash in a single call
    
    @param data Data to hash (can be NULL if len is 0)
    @param len Length of data in bytes
    @param hash Output buffer for 32-byte hash
    @return SHA256_SUCCESS, SHA256_ERROR_NULL_POINTER, or SHA256_ERROR_OVERFLOW
*/
static inline int sha256(const void *data, size_t len, uint8_t hash[SHA256_DIGEST_SIZE])
{
    SHA256_CTX ctx;
    int ret;

    if ((ret = sha256_init(&ctx)) != SHA256_SUCCESS) {
        return ret;
    }
    
    if ((ret = sha256_update(&ctx, data, len)) != SHA256_SUCCESS) {
        memset(&ctx, 0, sizeof(SHA256_CTX));
        return ret;
    }
    
    return sha256_final(&ctx, hash);
}

/*
    Convert binary hash to hexadecimal string
    
    @param hash 32-byte binary hash
    @param hex Output buffer for 65-byte hex string (64 hex chars + null terminator)
    @return SHA256_SUCCESS or SHA256_ERROR_NULL_POINTER
*/
static inline int sha256_to_hex(const uint8_t hash[SHA256_DIGEST_SIZE], char hex[65])
{
    static const char hex_chars[] = "0123456789abcdef";
    size_t i;

    if (hash == NULL || hex == NULL) {
        return SHA256_ERROR_NULL_POINTER;
    }

    for (i = 0; i < SHA256_DIGEST_SIZE; i++) {
        hex[i * 2 + 0] = hex_chars[(hash[i] >> 4) & 0xf];
        hex[i * 2 + 1] = hex_chars[hash[i] & 0xf];
    }
    hex[64] = '\0';

    return SHA256_SUCCESS;
}

/*
    Secure memory comparison for constant time to prevent timing attacks
    Use this to compare hash values securely
    
    @param hash1 First hash to compare
    @param hash2 Second hash to compare
    @return 0 if equal, non-zero if different, SHA256_ERROR_NULL_POINTER if either is NULL
*/
static inline int sha256_compare(const uint8_t hash1[SHA256_DIGEST_SIZE],
                                  const uint8_t hash2[SHA256_DIGEST_SIZE])
{
    volatile uint8_t result = 0;
    size_t i;

    if (hash1 == NULL || hash2 == NULL) {
        return SHA256_ERROR_NULL_POINTER;
    }

    for (i = 0; i < SHA256_DIGEST_SIZE; i++) {
        result |= hash1[i] ^ hash2[i];
    }

    return result;
}

#ifdef __cplusplus
}
#endif

#endif /* SHA256_H */