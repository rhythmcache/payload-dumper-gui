/************************************************************
 * Example Program: SHA-256 and SHA-512 File Hashing (C++)
 *
 * Demonstrates how to use sha256.h and sha512.h to compute
 * cryptographic hashes for large files using incremental
 * update calls. This example also shows parallel computation
 * using std::async for performance.
 ************************************************************/

#include <iostream>
#include <fstream>
#include <vector>
#include <future>
#include <memory>
#include <chrono>

#ifdef _WIN32
    #include <sys/types.h>
    #include <sys/stat.h>
    #define stat _stat
#else
    #include <sys/stat.h>
#endif

/* Include C-style hashing implementations */
extern "C" {
    #include "../sha256.h"
    #include "../sha512.h"
}

/* Buffer size used for streaming file input */
constexpr size_t BUFFER_SIZE = 1024 * 1024;  // 1 MB

/************************************************************
 * HashResult
 * Stores raw hash bytes, return code, and hex representation
 ************************************************************/
struct HashResult {
    std::vector<uint8_t> hash;
    int error_code;
    std::string hex_string;

    HashResult() : error_code(-1) {}
};

/************************************************************
 * FileHasher
 * Helper class that streams file contents into SHA contexts.
 * Demonstrates typical usage: init -> update -> final.
 ************************************************************/
class FileHasher {
public:
    explicit FileHasher(const std::string& filepath)
        : filepath_(filepath) {}

    /********************************************************
     * compute_sha256
     * Example of how to compute SHA-256 using sha256.h API.
     ********************************************************/
    HashResult compute_sha256() {
        HashResult result;
        result.hash.resize(SHA256_DIGEST_SIZE);

        std::ifstream file(filepath_, std::ios::binary);
        if (!file) {
            std::cerr << "Error: Cannot open file for SHA-256\n";
            return result;
        }

        SHA256_CTX ctx;
        result.error_code = sha256_init(&ctx);
        if (result.error_code != SHA256_SUCCESS)
            return result;

        auto buffer = std::make_unique<uint8_t[]>(BUFFER_SIZE);

        /* Read file in chunks and update hash */
        while (file.read(reinterpret_cast<char*>(buffer.get()), BUFFER_SIZE)
               || file.gcount() > 0) {

            size_t bytes_read = file.gcount();
            result.error_code = sha256_update(&ctx, buffer.get(), bytes_read);
            if (result.error_code != SHA256_SUCCESS)
                return result;
        }

        /* Finalize SHA-256 */
        result.error_code = sha256_final(&ctx, result.hash.data());

        if (result.error_code == SHA256_SUCCESS) {
            char hex[65];
            sha256_to_hex(result.hash.data(), hex);
            result.hex_string = hex;
        }

        return result;
    }

    /********************************************************
     * compute_sha512
     * Example of how to compute SHA-512 using sha512.h API.
     ********************************************************/
    HashResult compute_sha512() {
        HashResult result;
        result.hash.resize(SHA512_DIGEST_SIZE);

        std::ifstream file(filepath_, std::ios::binary);
        if (!file) {
            std::cerr << "Error: Cannot open file for SHA-512\n";
            return result;
        }

        SHA512_CTX ctx;
        result.error_code = sha512_init(&ctx);
        if (result.error_code != SHA512_SUCCESS)
            return result;

        auto buffer = std::make_unique<uint8_t[]>(BUFFER_SIZE);

        /* Stream file into the SHA-512 context */
        while (file.read(reinterpret_cast<char*>(buffer.get()), BUFFER_SIZE)
               || file.gcount() > 0) {

            size_t bytes_read = file.gcount();
            result.error_code = sha512_update(&ctx, buffer.get(), bytes_read);
            if (result.error_code != SHA512_SUCCESS)
                return result;
        }

        /* Final digest */
        result.error_code = sha512_final(&ctx, result.hash.data());

        if (result.error_code == SHA512_SUCCESS) {
            char hex[129];
            sha512_to_hex(result.hash.data(), hex);
            result.hex_string = hex;
        }

        return result;
    }

    /********************************************************
     * get_file_size
     * Utility used by this example to show file information.
     ********************************************************/
    static long get_file_size(const std::string& filepath) {
        struct stat st;
        if (stat(filepath.c_str(), &st) == 0)
            return st.st_size;
        return -1;
    }

private:
    std::string filepath_;
};

/************************************************************
 * main
 * Driver for the example program. Demonstrates:
 *  • file size detection
 *  • running SHA-256 and SHA-512 in parallel
 *  • printing hex digests
 ************************************************************/
int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <filepath>\n";
        return 1;
    }

    std::string filepath = argv[1];

    long file_size = FileHasher::get_file_size(filepath);
    if (file_size < 0) {
        std::cerr << "Error: Cannot access file '" << filepath << "'\n";
        return 1;
    }

    std::cout << "Example: SHA-256 and SHA-512 hashing\n";
    std::cout << "Target file: " << filepath << "\n";
    std::cout << "File size:   " << file_size << " bytes\n\n";

    FileHasher hasher(filepath);

    /* Start timing */
    auto start = std::chrono::high_resolution_clock::now();

    /* Run both hashing routines concurrently */
    auto sha256_future = std::async(std::launch::async,
                                    &FileHasher::compute_sha256, &hasher);

    auto sha512_future = std::async(std::launch::async,
                                    &FileHasher::compute_sha512, &hasher);

    HashResult sha256_result = sha256_future.get();
    HashResult sha512_result = sha512_future.get();

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);

    /* Validate results */
    if (sha256_result.error_code != SHA256_SUCCESS) {
        std::cerr << "SHA-256 failed (code "
                  << sha256_result.error_code << ")\n";
        return 1;
    }

    if (sha512_result.error_code != SHA512_SUCCESS) {
        std::cerr << "SHA-512 failed (code "
                  << sha512_result.error_code << ")\n";
        return 1;
    }

    /* Output example results */
    std::cout << "SHA-256: " << sha256_result.hex_string << "\n";
    std::cout << "SHA-512: " << sha512_result.hex_string << "\n";
    std::cout << "\nTime taken: " << duration.count() << " ms\n";

    return 0;
}
