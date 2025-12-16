/************************************************************
 * Example Program: SHA-256 and SHA-512 File Hashing (C)
 *
 * Demonstrates how to use sha256.h and sha512.h from C code
 * with threaded hashing on large files. Shows typical usage:
 *
 *     init -> update -> final
 *
 * Also demonstrates cross-platform threading:
 *     * pthreads (Linux/Unix)
 *     * Win32 threads (Windows)
 ************************************************************/

#include <stdio.h>
#include <stdlib.h>

#ifdef _WIN32
    #include <windows.h>
    #include <sys/types.h>
    #include <sys/stat.h>
    #define stat _stat
#else
    #include <pthread.h>
    #include <sys/stat.h>
#endif


#include "../sha256.h"
#include "../sha512.h"

/* Buffer size for streaming file chunks */
#define BUFFER_SIZE (1024 * 1024)  /* 1 MB */

/************************************************************
 * hash_data_t
 * Shared structure used by both hashing threads
 ************************************************************/
typedef struct {
    const char *filepath;
    uint8_t sha256_hash[SHA256_DIGEST_SIZE];
    uint8_t sha512_hash[SHA512_DIGEST_SIZE];
    int sha256_result;
    int sha512_result;
} hash_data_t;

/* Forward declarations */
void *compute_sha256_thread(void *arg);
void *compute_sha512_thread(void *arg);

/************************************************************
 * Thread type abstraction
 ************************************************************/
#ifdef _WIN32
typedef HANDLE thread_t;

/* Windows wrapper functions map Win32 thread API to POSIX-like functions */
static DWORD WINAPI win32_sha256_wrapper(LPVOID arg) {
    compute_sha256_thread(arg);
    return 0;
}

static DWORD WINAPI win32_sha512_wrapper(LPVOID arg) {
    compute_sha512_thread(arg);
    return 0;
}

#else
typedef pthread_t thread_t;
#endif

/************************************************************
 * compute_sha256_thread
 * Thread procedure to compute SHA-256 of the file.
 * Reads file in 1MB chunks and streams into SHA256_CTX.
 ************************************************************/
void *compute_sha256_thread(void *arg) {
    hash_data_t *data = (hash_data_t *)arg;
    SHA256_CTX ctx;
    FILE *file;
    uint8_t *buffer;
    size_t bytes_read;

    buffer = malloc(BUFFER_SIZE);
    if (!buffer) {
        data->sha256_result = -1;
        return NULL;
    }

    file = fopen(data->filepath, "rb");
    if (!file) {
        free(buffer);
        data->sha256_result = -1;
        return NULL;
    }

    data->sha256_result = sha256_init(&ctx);
    if (data->sha256_result != SHA256_SUCCESS) {
        fclose(file);
        free(buffer);
        return NULL;
    }

    /* Stream file into SHA-256 context */
    while ((bytes_read = fread(buffer, 1, BUFFER_SIZE, file)) > 0) {
        data->sha256_result = sha256_update(&ctx, buffer, bytes_read);
        if (data->sha256_result != SHA256_SUCCESS) {
            fclose(file);
            free(buffer);
            return NULL;
        }
    }

    /* Final digest */
    data->sha256_result = sha256_final(&ctx, data->sha256_hash);

    fclose(file);
    free(buffer);
    return NULL;
}

/************************************************************
 * compute_sha512_thread
 * Thread procedure to compute SHA-512 using the same pattern
 * as SHA-256: init -> update -> final.
 ************************************************************/
void *compute_sha512_thread(void *arg) {
    hash_data_t *data = (hash_data_t *)arg;
    SHA512_CTX ctx;
    FILE *file;
    uint8_t *buffer;
    size_t bytes_read;

    buffer = malloc(BUFFER_SIZE);
    if (!buffer) {
        data->sha512_result = -1;
        return NULL;
    }

    file = fopen(data->filepath, "rb");
    if (!file) {
        free(buffer);
        data->sha512_result = -1;
        return NULL;
    }

    data->sha512_result = sha512_init(&ctx);
    if (data->sha512_result != SHA512_SUCCESS) {
        fclose(file);
        free(buffer);
        return NULL;
    }

    /* Stream file into SHA-512 context */
    while ((bytes_read = fread(buffer, 1, BUFFER_SIZE, file)) > 0) {
        data->sha512_result = sha512_update(&ctx, buffer, bytes_read);
        if (data->sha512_result != SHA512_SUCCESS) {
            fclose(file);
            free(buffer);
            return NULL;
        }
    }

    /* Final digest */
    data->sha512_result = sha512_final(&ctx, data->sha512_hash);

    fclose(file);
    free(buffer);
    return NULL;
}

/************************************************************
 * get_file_size
 * Uses stat() to check if file exists and return its size.
 ************************************************************/
long get_file_size(const char *filepath) {
    struct stat st;
    if (stat(filepath, &st) == 0)
        return st.st_size;
    return -1;
}

/************************************************************
 * main
 * Coordinates thread creation and prints SHA-256 and SHA-512.
 ************************************************************/
int main(int argc, char *argv[]) {
    thread_t sha256_thread, sha512_thread;
    hash_data_t hash_data;
    char sha256_hex[65];
    char sha512_hex[129];
    long file_size;

    if (argc != 2) {
        fprintf(stderr, "Usage: %s <filepath>\n", argv[0]);
        return 1;
    }

    hash_data.filepath = argv[1];

    /* Verify file existence */
    file_size = get_file_size(argv[1]);
    if (file_size < 0) {
        fprintf(stderr, "Error: Cannot access file '%s'\n", argv[1]);
        return 1;
    }

    printf("Computing hashes for: %s\n", argv[1]);
    printf("File size: %ld bytes\n\n", file_size);

    /********************************************************
     * Launch SHA-256 and SHA-512 threads in parallel
     ********************************************************/
#ifdef _WIN32
    sha256_thread = CreateThread(NULL, 0, win32_sha256_wrapper, &hash_data, 0, NULL);
    if (!sha256_thread) {
        fprintf(stderr, "Error: Failed to create SHA-256 thread\n");
        return 1;
    }

    sha512_thread = CreateThread(NULL, 0, win32_sha512_wrapper, &hash_data, 0, NULL);
    if (!sha512_thread) {
        fprintf(stderr, "Error: Failed to create SHA-512 thread\n");
        WaitForSingleObject(sha256_thread, INFINITE);
        CloseHandle(sha256_thread);
        return 1;
    }

    WaitForSingleObject(sha256_thread, INFINITE);
    WaitForSingleObject(sha512_thread, INFINITE);
    CloseHandle(sha256_thread);
    CloseHandle(sha512_thread);

#else
    if (pthread_create(&sha256_thread, NULL, compute_sha256_thread, &hash_data) != 0) {
        fprintf(stderr, "Error: Failed to create SHA-256 thread\n");
        return 1;
    }

    if (pthread_create(&sha512_thread, NULL, compute_sha512_thread, &hash_data) != 0) {
        fprintf(stderr, "Error: Failed to create SHA-512 thread\n");
        pthread_join(sha256_thread, NULL);
        return 1;
    }

    pthread_join(sha256_thread, NULL);
    pthread_join(sha512_thread, NULL);
#endif

    /********************************************************
     * Validate and print results
     ********************************************************/
    if (hash_data.sha256_result != SHA256_SUCCESS) {
        fprintf(stderr, "Error: SHA-256 computation failed (code %d)\n",
                hash_data.sha256_result);
        return 1;
    }

    if (hash_data.sha512_result != SHA512_SUCCESS) {
        fprintf(stderr, "Error: SHA-512 computation failed (code %d)\n",
                hash_data.sha512_result);
        return 1;
    }

    /* Convert raw hash bytes to hex strings */
    sha256_to_hex(hash_data.sha256_hash, sha256_hex);
    sha512_to_hex(hash_data.sha512_hash, sha512_hex);

    printf("SHA-256: %s\n", sha256_hex);
    printf("SHA-512: %s\n", sha512_hex);

    return 0;
}
