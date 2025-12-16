/***************************************************************************
 * SHA-256 and SHA-512 Comprehensive Test Program                         *
 * Tests against official NIST test vectors, edge cases, and boundaries   *
 ***************************************************************************/

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include "../sha256.h"
#include "../sha512.h"

/* ANSI color codes for pretty output */
#ifdef _WIN32
    #define COLOR_GREEN ""
    #define COLOR_RED ""
    #define COLOR_YELLOW ""
    #define COLOR_CYAN ""
    #define COLOR_RESET ""
#else
    #define COLOR_GREEN "\033[32m"
    #define COLOR_RED "\033[31m"
    #define COLOR_YELLOW "\033[33m"
    #define COLOR_CYAN "\033[36m"
    #define COLOR_RESET "\033[0m"
#endif

typedef struct {
    const char *name;
    const char *input;
    size_t input_len;
    const char *expected_sha256;
    const char *expected_sha512;
} test_vector_t;

static int total_tests = 0;
static int passed_tests = 0;

void test_pass(const char *name) {
    printf("  %s✓ %s: PASS%s\n", COLOR_GREEN, name, COLOR_RESET);
    passed_tests++;
    total_tests++;
}

void test_fail(const char *name, const char *reason) {
    printf("  %s✗ %s: FAILED%s", COLOR_RED, name, COLOR_RESET);
    if (reason) {
        printf(" (%s)", reason);
    }
    printf("\n");
    total_tests++;
}

/* Official NIST test vectors */
static const test_vector_t nist_vectors[] = {
    {
        "Empty string",
        "",
        0,
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce"
        "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
    },
    {
        "Single 'a'",
        "a",
        1,
        "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
        "1f40fc92da241694750979ee6cf582f2d5d7d28e18335de05abc54d0560e0f53"
        "02860c652bf08d560252aa5e74210546f369fbbbce8c12cfc7957b2652fe9a75"
    },
    {
        "String 'abc'",
        "abc",
        3,
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
        "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"
    },
    {
        "Message digest",
        "message digest",
        14,
        "f7846f55cf23e14eebeab5b4e1550cad5b509e3348fbc4efa3a1413d393cb650",
        "107dbf389d9e9f71a3a95f6c055b9251bc5268c2be16d6c13492ea45b0199f33"
        "09e16455ab1e96118e8a905d5597b72038ddb372a89826046de66687bb420e7c"
    },
    {
        "Alphabet",
        "abcdefghijklmnopqrstuvwxyz",
        26,
        "71c480df93d6ae2f1efad1447c66c9525e316218cf51fc8d9ed832f2daf18b73",
        "4dbff86cc2ca1bae1e16468a05cb9881c97f1753bce3619034898faa1aabe429"
        "955a1bf8ec483d7421fe3c1646613a59ed5441fb0f321389f77f48a879c7b1f1"
    },
    {
        "Alphanumeric",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
        62,
        "db4bfcbd4da0cd85a60c3c37d3fbd8805c77f15fc6b1fdfe614ee0a7c8fdb4c0",
        "1e07be23c26a86ea37ea810c8ec7809352515a970e9253c26f536cfc7a9996c4"
        "5c8370583e0a78fa4a90041d71a4ceab7423f19c71b9d5a3e01249f0bebd5894"
    },
    {
        "Numeric repetition",
        "12345678901234567890123456789012345678901234567890123456789012345678901234567890",
        80,
        "f371bc4a311f2b009eef952dd83ca80e2b60026c8e935592d0f9c308453c813e",
        "72ec1ef1124a45b047e8b7c75a932195135bb61de24ec0d1914042246e0aec3a"
        "2354e093d76f3048b456764346900cb130d2a4fd5dd16abb5e30bcb850dee843"
    }
};

void test_nist_vectors(void) {
    int i;
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    char hex256[65];
    char hex512[129];
    
    printf("\n%s[1] NIST Test Vectors%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    for (i = 0; i < sizeof(nist_vectors) / sizeof(nist_vectors[0]); i++) {
        const test_vector_t *test = &nist_vectors[i];
        
        /* Test SHA-256 */
        if (sha256((const uint8_t *)test->input, test->input_len, hash256) == SHA256_SUCCESS) {
            sha256_to_hex(hash256, hex256);
            if (strcmp(hex256, test->expected_sha256) == 0) {
                test_pass(test->name);
            } else {
                test_fail(test->name, "SHA-256 mismatch");
                printf("    Expected: %s\n    Got:      %s\n", test->expected_sha256, hex256);
            }
        } else {
            test_fail(test->name, "SHA-256 error");
        }
        
        /* Test SHA-512 */
        if (sha512((const uint8_t *)test->input, test->input_len, hash512) == SHA512_SUCCESS) {
            sha512_to_hex(hash512, hex512);
            if (strcmp(hex512, test->expected_sha512) == 0) {
                test_pass(test->name);
            } else {
                test_fail(test->name, "SHA-512 mismatch");
                printf("    Expected: %s\n    Got:      %s\n", test->expected_sha512, hex512);
            }
        } else {
            test_fail(test->name, "SHA-512 error");
        }
    }
}

void test_boundary_sizes(void) {
    printf("\n%s[2] Boundary Size Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    /* Test sizes around SHA-256 block boundary (64 bytes) */
    size_t sizes_256[] = {55, 56, 63, 64, 65, 119, 120, 127, 128, 129};
    /* Test sizes around SHA-512 block boundary (128 bytes) */
    size_t sizes_512[] = {111, 112, 127, 128, 129, 239, 240, 255, 256, 257};
    
    uint8_t *data = malloc(300);
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    int i;
    
    if (!data) {
        test_fail("Memory allocation", "malloc failed");
        return;
    }
    
    memset(data, 0xAB, 300);
    
    /* SHA-256 boundary tests */
    for (i = 0; i < sizeof(sizes_256) / sizeof(sizes_256[0]); i++) {
        char name[64];
        sprintf(name, "SHA-256 %zu bytes", sizes_256[i]);
        
        if (sha256(data, sizes_256[i], hash256) == SHA256_SUCCESS) {
            test_pass(name);
        } else {
            test_fail(name, NULL);
        }
    }
    
    /* SHA-512 boundary tests */
    for (i = 0; i < sizeof(sizes_512) / sizeof(sizes_512[0]); i++) {
        char name[64];
        sprintf(name, "SHA-512 %zu bytes", sizes_512[i]);
        
        if (sha512(data, sizes_512[i], hash512) == SHA512_SUCCESS) {
            test_pass(name);
        } else {
            test_fail(name, NULL);
        }
    }
    
    free(data);
}

void test_incremental_vs_single(void) {
    printf("\n%s[3] Incremental vs Single-Call Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    const char *test_data = "The quick brown fox jumps over the lazy dog";
    size_t len = strlen(test_data);
    uint8_t hash_single[SHA256_DIGEST_SIZE];
    uint8_t hash_incremental[SHA256_DIGEST_SIZE];
    uint8_t hash512_single[SHA512_DIGEST_SIZE];
    uint8_t hash512_incremental[SHA512_DIGEST_SIZE];
    SHA256_CTX ctx256;
    SHA512_CTX ctx512;
    size_t i;
    
    /* SHA-256: Single call */
    sha256((const uint8_t *)test_data, len, hash_single);
    
    /* SHA-256: Incremental (1 byte at a time) */
    sha256_init(&ctx256);
    for (i = 0; i < len; i++) {
        sha256_update(&ctx256, (const uint8_t *)&test_data[i], 1);
    }
    sha256_final(&ctx256, hash_incremental);
    
    if (sha256_compare(hash_single, hash_incremental) == 0) {
        test_pass("SHA-256 incremental 1-byte");
    } else {
        test_fail("SHA-256 incremental 1-byte", NULL);
    }
    
    /* SHA-256: Incremental (various chunk sizes) */
    sha256_init(&ctx256);
    sha256_update(&ctx256, (const uint8_t *)test_data, 10);
    sha256_update(&ctx256, (const uint8_t *)test_data + 10, 20);
    sha256_update(&ctx256, (const uint8_t *)test_data + 30, len - 30);
    sha256_final(&ctx256, hash_incremental);
    
    if (sha256_compare(hash_single, hash_incremental) == 0) {
        test_pass("SHA-256 incremental chunks");
    } else {
        test_fail("SHA-256 incremental chunks", NULL);
    }
    
    /* SHA-512: Single call */
    sha512((const uint8_t *)test_data, len, hash512_single);
    
    /* SHA-512: Incremental (1 byte at a time) */
    sha512_init(&ctx512);
    for (i = 0; i < len; i++) {
        sha512_update(&ctx512, (const uint8_t *)&test_data[i], 1);
    }
    sha512_final(&ctx512, hash512_incremental);
    
    if (sha512_compare(hash512_single, hash512_incremental) == 0) {
        test_pass("SHA-512 incremental 1-byte");
    } else {
        test_fail("SHA-512 incremental 1-byte", NULL);
    }
    
    /* SHA-512: Incremental (various chunk sizes) */
    sha512_init(&ctx512);
    sha512_update(&ctx512, (const uint8_t *)test_data, 10);
    sha512_update(&ctx512, (const uint8_t *)test_data + 10, 20);
    sha512_update(&ctx512, (const uint8_t *)test_data + 30, len - 30);
    sha512_final(&ctx512, hash512_incremental);
    
    if (sha512_compare(hash512_single, hash512_incremental) == 0) {
        test_pass("SHA-512 incremental chunks");
    } else {
        test_fail("SHA-512 incremental chunks", NULL);
    }
}

void test_multi_block(void) {
    printf("\n%s[4] Multi-Block Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    /* Test with data that spans multiple blocks */
    size_t test_sizes[] = {
        SHA256_BLOCK_SIZE * 2,      /* 2 blocks for SHA-256 */
        SHA256_BLOCK_SIZE * 3,      /* 3 blocks */
        SHA256_BLOCK_SIZE * 10,     /* 10 blocks */
        SHA512_BLOCK_SIZE * 2,      /* 2 blocks for SHA-512 */
        SHA512_BLOCK_SIZE * 5,      /* 5 blocks */
        1024 * 10                   /* 10KB */
    };
    
    uint8_t *data;
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    int i;
    size_t max_size = 1024 * 10;
    
    data = malloc(max_size);
    if (!data) {
        test_fail("Memory allocation", "malloc failed");
        return;
    }
    
    /* Fill with pseudo-random data */
    for (i = 0; i < (int)max_size; i++) {
        data[i] = (uint8_t)(i * 31 + 17);
    }
    
    for (i = 0; i < sizeof(test_sizes) / sizeof(test_sizes[0]); i++) {
        char name[64];
        
        if (test_sizes[i] <= SHA512_BLOCK_SIZE * 5) {
            /* SHA-256 test */
            sprintf(name, "SHA-256 %zu bytes", test_sizes[i]);
            if (sha256(data, test_sizes[i], hash256) == SHA256_SUCCESS) {
                test_pass(name);
            } else {
                test_fail(name, NULL);
            }
        }
        
        /* SHA-512 test */
        sprintf(name, "SHA-512 %zu bytes", test_sizes[i]);
        if (sha512(data, test_sizes[i], hash512) == SHA512_SUCCESS) {
            test_pass(name);
        } else {
            test_fail(name, NULL);
        }
    }
    
    free(data);
}

void test_padding_edge_cases(void) {
    printf("\n%s[5] Padding Edge Cases%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    uint8_t *data;
    
    /* SHA-256: Test exact padding boundary (55 bytes - needs padding in same block) */
    data = malloc(55);
    memset(data, 'A', 55);
    if (sha256(data, 55, hash256) == SHA256_SUCCESS) {
        test_pass("SHA-256 55 bytes (padding fits)");
    } else {
        test_fail("SHA-256 55 bytes (padding fits)", NULL);
    }
    
    /* SHA-256: Test 56 bytes (padding needs extra block) */
    data = realloc(data, 56);
    memset(data, 'A', 56);
    if (sha256(data, 56, hash256) == SHA256_SUCCESS) {
        test_pass("SHA-256 56 bytes (padding extra block)");
    } else {
        test_fail("SHA-256 56 bytes (padding extra block)", NULL);
    }
    
    /* SHA-512: Test exact padding boundary (111 bytes) */
    data = realloc(data, 111);
    memset(data, 'A', 111);
    if (sha512(data, 111, hash512) == SHA512_SUCCESS) {
        test_pass("SHA-512 111 bytes (padding fits)");
    } else {
        test_fail("SHA-512 111 bytes (padding fits)", NULL);
    }
    
    /* SHA-512: Test 112 bytes (padding needs extra block) */
    data = realloc(data, 112);
    memset(data, 'A', 112);
    if (sha512(data, 112, hash512) == SHA512_SUCCESS) {
        test_pass("SHA-512 112 bytes (padding extra block)");
    } else {
        test_fail("SHA-512 112 bytes (padding extra block)", NULL);
    }
    
    free(data);
}

void test_alignment(void) {
    printf("\n%s[6] Memory Alignment Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    /* Test with unaligned memory access */
    uint8_t buffer[256];
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    int offset;
    
    memset(buffer, 0x42, sizeof(buffer));
    
    /* Test various alignments */
    for (offset = 0; offset < 8; offset++) {
        char name[64];
        
        sprintf(name, "SHA-256 offset %d", offset);
        if (sha256(buffer + offset, 64, hash256) == SHA256_SUCCESS) {
            test_pass(name);
        } else {
            test_fail(name, NULL);
        }
        
        sprintf(name, "SHA-512 offset %d", offset);
        if (sha512(buffer + offset, 64, hash512) == SHA512_SUCCESS) {
            test_pass(name);
        } else {
            test_fail(name, NULL);
        }
    }
}

void test_large_messages(void) {
    printf("\n%s[7] Large Message Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    /* Test with 1 million 'a' characters */
    const size_t size = 1000000;
    uint8_t *data = malloc(size);
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    char hex256[65];
    char hex512[129];
    
    const char *expected256 = "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0";
    const char *expected512 = 
        "e718483d0ce769644e2e42c7bc15b4638e1f98b13b2044285632a803afa973eb"
        "de0ff244877ea60a4cb0432ce577c31beb009c5c2c49aa2e4eadb217ad8cc09b";
    
    if (!data) {
        test_fail("Memory allocation", "malloc failed");
        return;
    }
    
    memset(data, 'a', size);
    
    /* Test SHA-256 */
    if (sha256(data, size, hash256) == SHA256_SUCCESS) {
        sha256_to_hex(hash256, hex256);
        if (strcmp(hex256, expected256) == 0) {
            test_pass("SHA-256 1M 'a' characters");
        } else {
            test_fail("SHA-256 1M 'a' characters", "hash mismatch");
        }
    } else {
        test_fail("SHA-256 1M 'a' characters", "hash error");
    }
    
    /* Test SHA-512 */
    if (sha512(data, size, hash512) == SHA512_SUCCESS) {
        sha512_to_hex(hash512, hex512);
        if (strcmp(hex512, expected512) == 0) {
            test_pass("SHA-512 1M 'a' characters");
        } else {
            test_fail("SHA-512 1M 'a' characters", "hash mismatch");
        }
    } else {
        test_fail("SHA-512 1M 'a' characters", "hash error");
    }
    
    free(data);
}

void test_random_data(void) {
    printf("\n%s[8] Random Data Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    uint8_t data[1024];
    uint8_t hash256_1[SHA256_DIGEST_SIZE];
    uint8_t hash256_2[SHA256_DIGEST_SIZE];
    uint8_t hash512_1[SHA512_DIGEST_SIZE];
    uint8_t hash512_2[SHA512_DIGEST_SIZE];
    int i, test_num;
    
    srand((unsigned int)time(NULL));
    
    /* Test that same random data produces same hash */
    for (test_num = 0; test_num < 5; test_num++) {
        size_t len = 1 + (rand() % 1000);
        
        /* Generate random data */
        for (i = 0; i < (int)len; i++) {
            data[i] = (uint8_t)(rand() & 0xFF);
        }
        
        /* Hash twice and compare */
        sha256(data, len, hash256_1);
        sha256(data, len, hash256_2);
        
        if (sha256_compare(hash256_1, hash256_2) == 0) {
            char name[64];
            sprintf(name, "SHA-256 random %zu bytes (consistency)", len);
            test_pass(name);
        } else {
            test_fail("SHA-256 random consistency", NULL);
        }
        
        sha512(data, len, hash512_1);
        sha512(data, len, hash512_2);
        
        if (sha512_compare(hash512_1, hash512_2) == 0) {
            char name[64];
            sprintf(name, "SHA-512 random %zu bytes (consistency)", len);
            test_pass(name);
        } else {
            test_fail("SHA-512 random consistency", NULL);
        }
    }
    
    /* Test that different data produces different hashes */
    for (i = 0; i < 1024; i++) {
        data[i] = (uint8_t)i;
    }
    sha256(data, 1024, hash256_1);
    
    data[500]++;  /* Change one byte */
    sha256(data, 1024, hash256_2);
    
    if (sha256_compare(hash256_1, hash256_2) != 0) {
        test_pass("SHA-256 avalanche (1 bit change)");
    } else {
        test_fail("SHA-256 avalanche (1 bit change)", NULL);
    }
    
    /* Reset and test SHA-512 */
    for (i = 0; i < 1024; i++) {
        data[i] = (uint8_t)i;
    }
    sha512(data, 1024, hash512_1);
    
    data[500]++;
    sha512(data, 1024, hash512_2);
    
    if (sha512_compare(hash512_1, hash512_2) != 0) {
        test_pass("SHA-512 avalanche (1 bit change)");
    } else {
        test_fail("SHA-512 avalanche (1 bit change)", NULL);
    }
}

void test_error_handling(void) {
    printf("\n%s[9] Error Handling Tests%s\n", COLOR_CYAN, COLOR_RESET);
    printf("-------------------------------------------------------\n");
    
    SHA256_CTX ctx256;
    SHA512_CTX ctx512;
    uint8_t hash256[SHA256_DIGEST_SIZE];
    uint8_t hash512[SHA512_DIGEST_SIZE];
    
    /* NULL pointer tests */
    if (sha256_init(NULL) == SHA256_ERROR_NULL_POINTER) {
        test_pass("SHA-256 NULL ctx init");
    } else {
        test_fail("SHA-256 NULL ctx init", NULL);
    }
    
    if (sha512_init(NULL) == SHA512_ERROR_NULL_POINTER) {
        test_pass("SHA-512 NULL ctx init");
    } else {
        test_fail("SHA-512 NULL ctx init", NULL);
    }
    
    sha256_init(&ctx256);
    if (sha256_update(&ctx256, NULL, 10) == SHA256_ERROR_NULL_POINTER) {
        test_pass("SHA-256 NULL data update");
    } else {
        test_fail("SHA-256 NULL data update", NULL);
    }
    
    sha512_init(&ctx512);
    if (sha512_update(&ctx512, NULL, 10) == SHA512_ERROR_NULL_POINTER) {
        test_pass("SHA-512 NULL data update");
    } else {
        test_fail("SHA-512 NULL data update", NULL);
    }
    
    if (sha256_final(&ctx256, NULL) == SHA256_ERROR_NULL_POINTER) {
        test_pass("SHA-256 NULL hash final");
    } else {
        test_fail("SHA-256 NULL hash final", NULL);
    }
    
    if (sha512_final(&ctx512, NULL) == SHA512_ERROR_NULL_POINTER) {
        test_pass("SHA-512 NULL hash final");
    } else {
        test_fail("SHA-512 NULL hash final", NULL);
    }
    
    /* Zero-length updates */
    sha256_init(&ctx256);
    if (sha256_update(&ctx256, "data", 0) == SHA256_SUCCESS) {
        test_pass("SHA-256 zero-length update");
    } else {
        test_fail("SHA-256 zero-length update", NULL);
    }
    
    sha512_init(&ctx512);
    if (sha512_update(&ctx512, "data", 0) == SHA512_SUCCESS) {
        test_pass("SHA-512 zero-length update");
    } else {
        test_fail("SHA-512 zero-length update", NULL);
    }
    
    /* Compare function tests */
    sha256_init(&ctx256);
    sha256_update(&ctx256, (const uint8_t *)"test", 4);
    sha256_final(&ctx256, hash256);
    
    if (sha256_compare(hash256, hash256) == 0) {
        test_pass("SHA-256 compare equal");
    } else {
        test_fail("SHA-256 compare equal", NULL);
    }
    
    if (sha256_compare(NULL, hash256) == SHA256_ERROR_NULL_POINTER) {
        test_pass("SHA-256 compare NULL");
    } else {
        test_fail("SHA-256 compare NULL", NULL);
    }
    
    sha512_init(&ctx512);
    sha512_update(&ctx512, (const uint8_t *)"test", 4);
    sha512_final(&ctx512, hash512);
    
    if (sha512_compare(hash512, hash512) == 0) {
        test_pass("SHA-512 compare equal");
    } else {
        test_fail("SHA-512 compare equal", NULL);
    }
    
    if (sha512_compare(NULL, hash512) == SHA512_ERROR_NULL_POINTER) {
        test_pass("SHA-512 compare NULL");
    } else {
        test_fail("SHA-512 compare NULL", NULL);
    }
}

int main(void) {
    printf("\n");
    printf("=======================================================\n");
    printf("  SHA-256 and SHA-512 Comprehensive Test Suite\n");
    printf("=======================================================\n");
    
    test_nist_vectors();
    test_boundary_sizes();
    test_incremental_vs_single();
    test_multi_block();
    test_padding_edge_cases();
    test_alignment();
    test_large_messages();
    test_random_data();
    test_error_handling();
    
    /* Summary */
    printf("\n=======================================================\n");
    printf("  Test Summary\n");
    printf("=======================================================\n");
    printf("Total: %d/%d tests passed (%.1f%%)\n", 
           passed_tests, total_tests, 
           (total_tests > 0) ? (100.0 * passed_tests / total_tests) : 0.0);
    
    if (passed_tests == total_tests) {
        printf("\n%s✓ All tests passed!%s\n\n", COLOR_GREEN, COLOR_RESET);
        return 0;
    } else {
        printf("\n%s✗ %d test(s) failed%s\n\n", COLOR_RED, total_tests - passed_tests, COLOR_RESET);
        return 1;
    }
}