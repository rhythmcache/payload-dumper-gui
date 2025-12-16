/***************************************************************************
 * SHA-512 Header-Only Implementation                                      *
 * Based on FIPS 180-4 specification                                       *
 * Secure implementation of SHA-512                                        *
 * Apache-2 License                                                        *
 *                                                                         *
 * Usage:                                                                  *
 *     SHA512_CTX ctx;                                                     *
 *     if (sha512_init(&ctx) != 0) { handle error }                        *
 *     if (sha512_update(&ctx, data, len) != 0) { handle error }           *
 *     if (sha512_final(&ctx, hash) != 0) { handle error }                 *
 *                                                                         *
 * Or single-call convenience function:                                    *
 *     if (sha512(data, len, hash) != 0) { handle error }                  *
 ***************************************************************************/

#ifndef SHA512_H
#define SHA512_H

#include <stdint.h>
#include <string.h>
#include <stddef.h>
#include <limits.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Return codes */
#define SHA512_SUCCESS 0
#define SHA512_ERROR_NULL_POINTER 1
#define SHA512_ERROR_OVERFLOW 2

/* Public constants */
#define SHA512_BLOCK_SIZE 128
#define SHA512_DIGEST_SIZE 64
#define SHA512_LENGTH_SIZE 16  /* 128-bit length field in bytes */
#define SHA512_PAD_THRESHOLD (SHA512_BLOCK_SIZE - SHA512_LENGTH_SIZE)  /* 112 bytes */

/* Context structure - treat as opaque */
typedef struct {
    uint64_t state[8];
    uint64_t count[2];  /* 128-bit bit count (low, high) */
    uint8_t buffer[SHA512_BLOCK_SIZE];
    size_t buffer_len; /* Current bytes in buffer */
} SHA512_CTX;

/* Internal macros - do not use directly */
#define SHA512_ROTR(x, n) (((x) >> (n)) | ((x) << (64 - (n))))
#define SHA512_CH(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
#define SHA512_MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define SHA512_EP0(x) (SHA512_ROTR(x, 28) ^ SHA512_ROTR(x, 34) ^ SHA512_ROTR(x, 39))
#define SHA512_EP1(x) (SHA512_ROTR(x, 14) ^ SHA512_ROTR(x, 18) ^ SHA512_ROTR(x, 41))
#define SHA512_SIG0(x) (SHA512_ROTR(x, 1) ^ SHA512_ROTR(x, 8) ^ ((x) >> 7))
#define SHA512_SIG1(x) (SHA512_ROTR(x, 19) ^ SHA512_ROTR(x, 61) ^ ((x) >> 6))

/* SHA-512 round constants */
static const uint64_t SHA512_K[80] = {
    0x428a2f98d728ae22ULL, 0x7137449123ef65cdULL, 0xb5c0fbcfec4d3b2fULL, 0xe9b5dba58189dbbcULL,
    0x3956c25bf348b538ULL, 0x59f111f1b605d019ULL, 0x923f82a4af194f9bULL, 0xab1c5ed5da6d8118ULL,
    0xd807aa98a3030242ULL, 0x12835b0145706fbeULL, 0x243185be4ee4b28cULL, 0x550c7dc3d5ffb4e2ULL,
    0x72be5d74f27b896fULL, 0x80deb1fe3b1696b1ULL, 0x9bdc06a725c71235ULL, 0xc19bf174cf692694ULL,
    0xe49b69c19ef14ad2ULL, 0xefbe4786384f25e3ULL, 0x0fc19dc68b8cd5b5ULL, 0x240ca1cc77ac9c65ULL,
    0x2de92c6f592b0275ULL, 0x4a7484aa6ea6e483ULL, 0x5cb0a9dcbd41fbd4ULL, 0x76f988da831153b5ULL,
    0x983e5152ee66dfabULL, 0xa831c66d2db43210ULL, 0xb00327c898fb213fULL, 0xbf597fc7beef0ee4ULL,
    0xc6e00bf33da88fc2ULL, 0xd5a79147930aa725ULL, 0x06ca6351e003826fULL, 0x142929670a0e6e70ULL,
    0x27b70a8546d22ffcULL, 0x2e1b21385c26c926ULL, 0x4d2c6dfc5ac42aedULL, 0x53380d139d95b3dfULL,
    0x650a73548baf63deULL, 0x766a0abb3c77b2a8ULL, 0x81c2c92e47edaee6ULL, 0x92722c851482353bULL,
    0xa2bfe8a14cf10364ULL, 0xa81a664bbc423001ULL, 0xc24b8b70d0f89791ULL, 0xc76c51a30654be30ULL,
    0xd192e819d6ef5218ULL, 0xd69906245565a910ULL, 0xf40e35855771202aULL, 0x106aa07032bbd1b8ULL,
    0x19a4c116b8d2d0c8ULL, 0x1e376c085141ab53ULL, 0x2748774cdf8eeb99ULL, 0x34b0bcb5e19b48a8ULL,
    0x391c0cb3c5c95a63ULL, 0x4ed8aa4ae3418acbULL, 0x5b9cca4f7763e373ULL, 0x682e6ff3d6b2b8a3ULL,
    0x748f82ee5defb2fcULL, 0x78a5636f43172f60ULL, 0x84c87814a1f0ab72ULL, 0x8cc702081a6439ecULL,
    0x90befffa23631e28ULL, 0xa4506cebde82bde9ULL, 0xbef9a3f7b2c67915ULL, 0xc67178f2e372532bULL,
    0xca273eceea26619cULL, 0xd186b8c721c0c207ULL, 0xeada7dd6cde0eb1eULL, 0xf57d4f7fee6ed178ULL,
    0x06f067aa72176fbaULL, 0x0a637dc5a2c898a6ULL, 0x113f9804bef90daeULL, 0x1b710b35131c471bULL,
    0x28db77f523047d84ULL, 0x32caab7b40c72493ULL, 0x3c9ebe0a15c9bebcULL, 0x431d67c49c100d4cULL,
    0x4cc5d4becb3e42b6ULL, 0x597f299cfc657e2aULL, 0x5fcb6fab3ad6faecULL, 0x6c44198c4a475817ULL
};

/*
    Process a single 1024-bit block
    Internal function - not part of public API
*/
static inline void sha512_transform(SHA512_CTX *ctx, const uint8_t data[SHA512_BLOCK_SIZE])
{
    uint64_t a, b, c, d, e, f, g, h, t1, t2, m[80];
    int i;

    /* Prepare message schedule - expand 16 x 64-bit words to 80 */
    for (i = 0; i < 16; i++) {
        m[i] = ((uint64_t)data[i * 8 + 0] << 56) |
               ((uint64_t)data[i * 8 + 1] << 48) |
               ((uint64_t)data[i * 8 + 2] << 40) |
               ((uint64_t)data[i * 8 + 3] << 32) |
               ((uint64_t)data[i * 8 + 4] << 24) |
               ((uint64_t)data[i * 8 + 5] << 16) |
               ((uint64_t)data[i * 8 + 6] << 8) |
               ((uint64_t)data[i * 8 + 7]);
    }

    for (i = 16; i < 80; i++) {
        m[i] = SHA512_SIG1(m[i - 2]) + m[i - 7] + SHA512_SIG0(m[i - 15]) + m[i - 16];
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

    /* Main compression loop - 80 rounds */
    for (i = 0; i < 80; i++) {
        t1 = h + SHA512_EP1(e) + SHA512_CH(e, f, g) + SHA512_K[i] + m[i];
        t2 = SHA512_EP0(a) + SHA512_MAJ(a, b, c);
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
    Initialize SHA-512 context
    Must be called before first sha512_update()
    
    @param ctx Pointer to SHA-512 context
    @return SHA512_SUCCESS on success, SHA512_ERROR_NULL_POINTER if ctx is NULL
*/
static inline int sha512_init(SHA512_CTX *ctx)
{
    if (ctx == NULL) {
        return SHA512_ERROR_NULL_POINTER;
    }

    /* Initial hash values - first 64 bits of fractional parts of square roots of first 8 primes */
    ctx->state[0] = 0x6a09e667f3bcc908ULL;
    ctx->state[1] = 0xbb67ae8584caa73bULL;
    ctx->state[2] = 0x3c6ef372fe94f82bULL;
    ctx->state[3] = 0xa54ff53a5f1d36f1ULL;
    ctx->state[4] = 0x510e527fade682d1ULL;
    ctx->state[5] = 0x9b05688c2b3e6c1fULL;
    ctx->state[6] = 0x1f83d9abfb41bd6bULL;
    ctx->state[7] = 0x5be0cd19137e2179ULL;

    ctx->count[0] = 0;
    ctx->count[1] = 0;
    ctx->buffer_len = 0;
    
    return SHA512_SUCCESS;
}

/*
    Add data to SHA-512 context
    Can be called multiple times to hash data incrementally
    
    @param ctx Initialized SHA-512 context
    @param data Data to hash (can be NULL if len is 0)
    @param len Length of data in bytes
    @return SHA512_SUCCESS, SHA512_ERROR_NULL_POINTER, or SHA512_ERROR_OVERFLOW
*/
static inline int sha512_update(SHA512_CTX *ctx, const void *data, size_t len)
{
    const uint8_t *input = (const uint8_t *)data;
    size_t remaining = len;
    uint64_t old_count_low;

    if (ctx == NULL) {
        return SHA512_ERROR_NULL_POINTER;
    }

    if (len == 0) {
        return SHA512_SUCCESS;
    }

    if (data == NULL) {
        return SHA512_ERROR_NULL_POINTER;
    }

    /* Update 128-bit bit count */
    old_count_low = ctx->count[0];
    
    /* Add low bits (len * 8) */
    ctx->count[0] += (uint64_t)len << 3;  /* Convert bytes to bits */
    
    /* Add high bits from byte count (bits that shifted out when we did len << 3) */
    /* This handles cases where len is very large (>= 2^61 bytes) */
    uint64_t high_bits = 0;
    if (len >= (UINT64_C(1) << 61)) {
        high_bits = (uint64_t)len >> 61;
    }
    
    /* Check for overflow in low 64 bits and handle carry */
    uint64_t carry = 0;
    if (ctx->count[0] < old_count_low) {
        /* Overflow occurred in low word, need to carry */
        carry = 1;
    }
    
    /* Now add both carry and high_bits to count[1], checking for overflow */
    uint64_t total_to_add = carry + high_bits;
    if (total_to_add > 0) {
        if (ctx->count[1] > UINT64_MAX - total_to_add) {
            return SHA512_ERROR_OVERFLOW;
        }
        ctx->count[1] += total_to_add;
    }

    /* If we have data in buffer, try to fill it */
    if (ctx->buffer_len > 0) {
        size_t space_in_buffer = SHA512_BLOCK_SIZE - ctx->buffer_len;
        size_t to_copy = (remaining < space_in_buffer) ? remaining : space_in_buffer;

        memcpy(ctx->buffer + ctx->buffer_len, input, to_copy);
        ctx->buffer_len += to_copy;
        input += to_copy;
        remaining -= to_copy;

        /* If buffer is full, process it */
        if (ctx->buffer_len == SHA512_BLOCK_SIZE) {
            sha512_transform(ctx, ctx->buffer);
            ctx->buffer_len = 0;
        }
    }

    /* Process complete blocks directly from input */
    while (remaining >= SHA512_BLOCK_SIZE) {
        sha512_transform(ctx, input);
        input += SHA512_BLOCK_SIZE;
        remaining -= SHA512_BLOCK_SIZE;
    }

    /* Store any remaining bytes in buffer */
    if (remaining > 0) {
        memcpy(ctx->buffer + ctx->buffer_len, input, remaining);
        ctx->buffer_len += remaining;
    }

    return SHA512_SUCCESS;
}

/*
    Finalize SHA-512 hash computation
    After calling this, the context is zeroed and must be reinitialized
    
    @param ctx SHA-512 context
    @param hash Output buffer for 64-byte hash (must be at least SHA512_DIGEST_SIZE bytes)
    @return SHA512_SUCCESS or SHA512_ERROR_NULL_POINTER
*/
static inline int sha512_final(SHA512_CTX *ctx, uint8_t hash[SHA512_DIGEST_SIZE])
{
    size_t pad_len;
    uint8_t padding[SHA512_BLOCK_SIZE * 2];
    size_t i;

    if (ctx == NULL || hash == NULL) {
        return SHA512_ERROR_NULL_POINTER;
    }

    /* Padding: 0x80 followed by zeros, then 128-bit length */
    /* We need: current data + 0x80 + zeros + 16 bytes for length = multiple of 128 */
    padding[0] = 0x80;

    if (ctx->buffer_len < SHA512_PAD_THRESHOLD) {
        /* Enough room in current block for padding and length */
        pad_len = SHA512_PAD_THRESHOLD - ctx->buffer_len;
    } else {
        /* Need an extra block */
        pad_len = SHA512_BLOCK_SIZE + SHA512_PAD_THRESHOLD - ctx->buffer_len;
    }

    /* Fill padding with zeros (after the 0x80 byte) */
    memset(padding + 1, 0, pad_len - 1);

    /* Append 128-bit length in bits (big-endian) */
    padding[pad_len + 0] = (uint8_t)(ctx->count[1] >> 56);
    padding[pad_len + 1] = (uint8_t)(ctx->count[1] >> 48);
    padding[pad_len + 2] = (uint8_t)(ctx->count[1] >> 40);
    padding[pad_len + 3] = (uint8_t)(ctx->count[1] >> 32);
    padding[pad_len + 4] = (uint8_t)(ctx->count[1] >> 24);
    padding[pad_len + 5] = (uint8_t)(ctx->count[1] >> 16);
    padding[pad_len + 6] = (uint8_t)(ctx->count[1] >> 8);
    padding[pad_len + 7] = (uint8_t)(ctx->count[1]);
    padding[pad_len + 8] = (uint8_t)(ctx->count[0] >> 56);
    padding[pad_len + 9] = (uint8_t)(ctx->count[0] >> 48);
    padding[pad_len + 10] = (uint8_t)(ctx->count[0] >> 40);
    padding[pad_len + 11] = (uint8_t)(ctx->count[0] >> 32);
    padding[pad_len + 12] = (uint8_t)(ctx->count[0] >> 24);
    padding[pad_len + 13] = (uint8_t)(ctx->count[0] >> 16);
    padding[pad_len + 14] = (uint8_t)(ctx->count[0] >> 8);
    padding[pad_len + 15] = (uint8_t)(ctx->count[0]);

    /* Process padding directly without updating count */
    /* Copy padding to buffer and process blocks */
    i = 0;
    while (i < pad_len + SHA512_LENGTH_SIZE) {
        size_t space_in_buffer = SHA512_BLOCK_SIZE - ctx->buffer_len;
        size_t to_copy = (pad_len + SHA512_LENGTH_SIZE - i < space_in_buffer) ? (pad_len + SHA512_LENGTH_SIZE - i) : space_in_buffer;
        
        memcpy(ctx->buffer + ctx->buffer_len, padding + i, to_copy);
        ctx->buffer_len += to_copy;
        i += to_copy;
        
        if (ctx->buffer_len == SHA512_BLOCK_SIZE) {
            sha512_transform(ctx, ctx->buffer);
            ctx->buffer_len = 0;
        }
    }

    /* Extract hash value (big-endian) */
    for (i = 0; i < 8; i++) {
        hash[i * 8 + 0] = (uint8_t)(ctx->state[i] >> 56);
        hash[i * 8 + 1] = (uint8_t)(ctx->state[i] >> 48);
        hash[i * 8 + 2] = (uint8_t)(ctx->state[i] >> 40);
        hash[i * 8 + 3] = (uint8_t)(ctx->state[i] >> 32);
        hash[i * 8 + 4] = (uint8_t)(ctx->state[i] >> 24);
        hash[i * 8 + 5] = (uint8_t)(ctx->state[i] >> 16);
        hash[i * 8 + 6] = (uint8_t)(ctx->state[i] >> 8);
        hash[i * 8 + 7] = (uint8_t)(ctx->state[i]);
    }

    /* Clear sensitive data */
    memset(ctx, 0, sizeof(SHA512_CTX));

    return SHA512_SUCCESS;
}

/*
    Convenience function to compute SHA-512 hash in a single call
    
    @param data Data to hash (can be NULL if len is 0)
    @param len Length of data in bytes
    @param hash Output buffer for 64-byte hash
    @return SHA512_SUCCESS, SHA512_ERROR_NULL_POINTER, or SHA512_ERROR_OVERFLOW
*/
static inline int sha512(const void *data, size_t len, uint8_t hash[SHA512_DIGEST_SIZE])
{
    SHA512_CTX ctx;
    int ret;

    if ((ret = sha512_init(&ctx)) != SHA512_SUCCESS) {
        return ret;
    }
    
    if ((ret = sha512_update(&ctx, data, len)) != SHA512_SUCCESS) {
        memset(&ctx, 0, sizeof(SHA512_CTX));
        return ret;
    }
    
    return sha512_final(&ctx, hash);
}

/*
    Convert binary hash to hexadecimal string
    
    @param hash 64-byte binary hash
    @param hex Output buffer for 129-byte hex string (128 hex chars + null terminator)
    @return SHA512_SUCCESS or SHA512_ERROR_NULL_POINTER
*/
static inline int sha512_to_hex(const uint8_t hash[SHA512_DIGEST_SIZE], char hex[129])
{
    static const char hex_chars[] = "0123456789abcdef";
    size_t i;

    if (hash == NULL || hex == NULL) {
        return SHA512_ERROR_NULL_POINTER;
    }

    for (i = 0; i < SHA512_DIGEST_SIZE; i++) {
        hex[i * 2 + 0] = hex_chars[(hash[i] >> 4) & 0xf];
        hex[i * 2 + 1] = hex_chars[hash[i] & 0xf];
    }
    hex[128] = '\0';

    return SHA512_SUCCESS;
}

/*
    Secure memory comparison for constant time to prevent timing attacks
    Use this to compare hash values securely
    
    @param hash1 First hash to compare
    @param hash2 Second hash to compare
    @return 0 if equal, 1 if different, SHA512_ERROR_NULL_POINTER if either is NULL
*/
static inline int sha512_compare(const uint8_t hash1[SHA512_DIGEST_SIZE],
                                  const uint8_t hash2[SHA512_DIGEST_SIZE])
{
    volatile uint8_t result = 0;
    size_t i;

    if (hash1 == NULL || hash2 == NULL) {
        return SHA512_ERROR_NULL_POINTER;
    }

    for (i = 0; i < SHA512_DIGEST_SIZE; i++) {
        result |= hash1[i] ^ hash2[i];
    }

    /* Normalize to 0 or 1 */
    return result != 0 ? 1 : 0;
}

#ifdef __cplusplus
}
#endif

#endif /* SHA512_H */
