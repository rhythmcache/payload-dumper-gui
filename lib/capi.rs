// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 rhythmcache

use std::ffi::{CStr, CString, c_char, c_void};
use std::panic;
use std::ptr;
use std::sync::Arc;

use crate::extractor::{
    ExtractionProgress, ExtractionStatus, ProgressCallback, extract_local_partition,
    extract_remote_partition, list_local_partitions, list_remote_partitions,
};

/* Error Handling */

thread_local! {
    static LAST_ERROR: std::cell::RefCell<Option<CString>> = std::cell::RefCell::new(None);
}

fn set_last_error(err: String) {
    LAST_ERROR.with(|last| {
        *last.borrow_mut() = CString::new(err).ok();
    });
}

fn clear_last_error() {
    LAST_ERROR.with(|last| {
        *last.borrow_mut() = None;
    });
}

/// get the last error message
/// returns NULL if no error occurred
/// the returned string is valid until the next call from the same thread
///
/// note: errors are thread-local. Each thread maintains its own error state.
#[unsafe(no_mangle)]
pub extern "C" fn payload_get_last_error() -> *const c_char {
    LAST_ERROR.with(|last| {
        last.borrow()
            .as_ref()
            .map(|s| s.as_ptr())
            .unwrap_or(ptr::null())
    })
}

/// clear the last error
#[unsafe(no_mangle)]
pub extern "C" fn payload_clear_error() {
    clear_last_error();
}

/* String Handling */

/// free a string allocated by this library
#[unsafe(no_mangle)]
pub extern "C" fn payload_free_string(s: *mut c_char) {
    if !s.is_null() {
        unsafe {
            drop(CString::from_raw(s));
        }
    }
}

/* Helper Functions */

/// Convert C string pointer to Rust &str with error handling
fn c_str_to_rust<'a>(ptr: *const c_char, param_name: &str) -> Result<&'a str, String> {
    if ptr.is_null() {
        return Err(format!("{} is NULL", param_name));
    }
    unsafe {
        CStr::from_ptr(ptr)
            .to_str()
            .map_err(|e| format!("Invalid UTF-8 in {}: {}", param_name, e))
    }
}

/// Convert optional C string pointer to Option<&str>
fn optional_c_str_to_rust<'a>(
    ptr: *const c_char,
    param_name: &str,
) -> Result<Option<&'a str>, String> {
    if ptr.is_null() {
        Ok(None)
    } else {
        c_str_to_rust(ptr, param_name).map(Some)
    }
}

/// Wrap function in panic handler and error management
fn with_error_handling<F>(f: F) -> i32
where
    F: FnOnce() -> Result<(), String> + panic::UnwindSafe,
{
    clear_last_error();

    let result = panic::catch_unwind(f);

    match result {
        Ok(Ok(())) => 0,
        Ok(Err(e)) => {
            set_last_error(e);
            -1
        }
        Err(_) => {
            set_last_error("Panic occurred".to_string());
            -1
        }
    }
}

/// Wrap function that returns string in panic handler
fn with_string_error_handling<F>(f: F) -> *mut c_char
where
    F: FnOnce() -> Result<String, String> + panic::UnwindSafe,
{
    clear_last_error();

    let result = panic::catch_unwind(f);

    match result {
        Ok(Ok(s)) => match CString::new(s) {
            Ok(c_str) => c_str.into_raw(),
            Err(e) => {
                set_last_error(format!("Failed to create C string: {}", e));
                ptr::null_mut()
            }
        },
        Ok(Err(e)) => {
            set_last_error(e);
            ptr::null_mut()
        }
        Err(_) => {
            set_last_error("Panic occurred".to_string());
            ptr::null_mut()
        }
    }
}

/* Partition List API */

/// list all partitions in a local file (payload.bin or ZIP)
/// Returns a JSON string on success, NULL on failure
/// the caller must free the returned string with payload_free_string()
///
/// the returned JSON structure:
/// {
///   "partitions": [...],
///   "total_partitions": 10,
///   "total_operations": 1000,
///   "total_size_bytes": 5000000000,
///   "total_size_readable": "4.66 GB",
///   "security_patch_level": "2025-12-05", // optional, present only if available in payload
///   "is_incremental": true // true if any partition requires differential update
/// }
///
/// The function automatically detects whether the file is a payload.bin or ZIP file
#[unsafe(no_mangle)]
pub extern "C" fn payload_list_local_partitions(path: *const c_char) -> *mut c_char {
    with_string_error_handling(|| {
        let path_str = c_str_to_rust(path, "path")?;
        list_local_partitions(path_str).map_err(|e| format!("Failed to list partitions: {}", e))
    })
}

/// list all partitions in a remote file (payload.bin or ZIP)
/// returns a JSON string on success, NULL on failure
/// the caller must free the returned string with payload_free_string()
///
/// @param url URL to the remote file
/// @param user_agent Optional user agent string (pass NULL for default)
/// @param cookies Optional cookie string (pass NULL for default)
/// @return JSON string on success, NULL on failure
///
/// the returned JSON format is the same as payload_list_local_partitions()
/// The function automatically detects whether the file is a payload.bin or ZIP file
/// Cookies must be provided as a raw HTTP "Cookie" header value
/// (for example "key1=value1; key2=value2")
#[unsafe(no_mangle)]
pub extern "C" fn payload_list_remote_partitions(
    url: *const c_char,
    user_agent: *const c_char,
    cookies: *const c_char,
) -> *mut c_char {
    with_string_error_handling(|| {
        let url_str = c_str_to_rust(url, "url")?;
        let user_agent_str = optional_c_str_to_rust(user_agent, "user_agent")?;
        let cookies_str = optional_c_str_to_rust(cookies, "cookies")?;

        list_remote_partitions(url_str.to_string(), user_agent_str, cookies_str)
            .map_err(|e| format!("Failed to list remote partitions: {}", e))
    })
}

/* Progress Callback */

/// progress callback function type
///
/// @param user_data User-provided data pointer
/// @param partition_name Name of the partition being extracted (temporary pointer)
/// @param current_operation Current operation number (0-based)
/// @param total_operations Total number of operations
/// @param percentage Completion percentage (0.0 to 100.0)
/// @param status Status code (see STATUS_* constants)
/// @param warning_message Warning message if status is STATUS_WARNING (temporary pointer)
/// @return non-zero to continue extraction, 0 to cancel
pub type CProgressCallback = Option<
    extern "C" fn(
        user_data: *mut c_void,
        partition_name: *const c_char,
        current_operation: u64,
        total_operations: u64,
        percentage: f64,
        status: i32,
        warning_message: *const c_char,
    ) -> i32,
>;

/// status codes for progress callback
pub const STATUS_STARTED: i32 = 0;
pub const STATUS_IN_PROGRESS: i32 = 1;
pub const STATUS_COMPLETED: i32 = 2;
pub const STATUS_WARNING: i32 = 3;

struct CCallbackWrapper {
    callback: extern "C" fn(*mut c_void, *const c_char, u64, u64, f64, i32, *const c_char) -> i32,
    user_data: *mut c_void,
}

// we require the user_data to be thread-safe
unsafe impl Send for CCallbackWrapper {}
unsafe impl Sync for CCallbackWrapper {}

impl CCallbackWrapper {
    fn call(&self, progress: ExtractionProgress) -> bool {
        // catch panics to prevent unwinding through C
        let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
            // allocate partition name as local CString
            let partition_name = match CString::new(progress.partition_name.clone()) {
                Ok(s) => s,
                Err(_) => return true, // continue on error
            };

            // handle status and warning message
            let warning_msg;
            let (status, warning_msg_ptr) = match progress.status {
                ExtractionStatus::Started => (STATUS_STARTED, ptr::null()),
                ExtractionStatus::InProgress => (STATUS_IN_PROGRESS, ptr::null()),
                ExtractionStatus::Completed => (STATUS_COMPLETED, ptr::null()),
                ExtractionStatus::Warning { message, .. } => {
                    warning_msg = CString::new(message).ok();
                    let msg_ptr = warning_msg
                        .as_ref()
                        .map(|s| s.as_ptr())
                        .unwrap_or(ptr::null());
                    (STATUS_WARNING, msg_ptr)
                }
            };

            // call the C callback
            let result = (self.callback)(
                self.user_data,
                partition_name.as_ptr(),
                progress.current_operation,
                progress.total_operations,
                progress.percentage,
                status,
                warning_msg_ptr,
            );

            result != 0
        }));

        // if callback panicked, log error and continue
        match result {
            Ok(should_continue) => should_continue,
            Err(_) => {
                eprintln!("WARNING: Progress callback panicked - continuing extraction");
                true
            }
        }
    }
}

fn create_progress_callback(
    callback: CProgressCallback,
    user_data: *mut c_void,
) -> Option<ProgressCallback> {
    let cb = callback?; // unwrap Option<fn>

    let wrapper = Arc::new(CCallbackWrapper {
        callback: cb,
        user_data,
    });

    Some(Box::new(move |progress| wrapper.call(progress)) as ProgressCallback)
}

/// extract a single partition from a local file (payload.bin or ZIP)
///
/// @param path Path to the local file (payload.bin or ZIP)
/// @param partition_name Name of the partition to extract
/// @param output_path Path where the partition image will be written
/// @param source_dir Optional path to directory containing source partition images for incremental updates (pass NULL if not incremental)
/// @param callback Optional progress callback (pass NULL for no callback)
/// @param user_data User data passed to callback (can be NULL)
/// @return 0 on success, -1 on failure (check payload_get_last_error())
///
/// This function can be safely called from multiple threads concurrently.
/// Each thread can extract a different partition in parallel.
/// The function automatically detects whether the file is a payload.bin or ZIP file
///
/// For incremental/differential OTA updates, you must provide source_dir containing the
/// old partition images (e.g., system.img, vendor.img). The function will look for
/// <partition_name>.img in the source_dir.
///
/// - pass NULL for callback parameter if you don't want progress updates
/// - the partition_name and warning_message pointers passed to the callback
///   are ONLY valid during the callback execution. Do NOT store these pointers.
/// - If you need to keep the strings, copy them immediately in the callback.
/// - do NOT call free() on these strings, they are managed by the library.
///
/// - Return 0 from the callback to cancel extraction
/// - Return non-zero to continue
/// - cancellation may not be immediate
#[unsafe(no_mangle)]
pub extern "C" fn payload_extract_local_partition(
    path: *const c_char,
    partition_name: *const c_char,
    output_path: *const c_char,
    source_dir: *const c_char,
    callback: CProgressCallback,
    user_data: *mut c_void,
) -> i32 {
    with_error_handling(|| {
        let path_str = c_str_to_rust(path, "path")?;
        let partition_str = c_str_to_rust(partition_name, "partition_name")?;
        let output_str = c_str_to_rust(output_path, "output_path")?;
        let source_str = optional_c_str_to_rust(source_dir, "source_dir")?;
        let progress_cb = create_progress_callback(callback, user_data);

        extract_local_partition(
            path_str,
            partition_str,
            output_str,
            source_str.map(|s| s.to_string()),
            progress_cb,
        )
        .map_err(|e| format!("Extraction failed: {}", e))
    })
}

/// extract a single partition from a remote file (payload.bin or ZIP)
///
/// @param url URL to the remote file
/// @param partition_name Name of the partition to extract
/// @param output_path Path where the partition image will be written
/// @param user_agent Optional user agent string (pass NULL for default)
/// @param cookies Optional cookie string (pass NULL for default)
/// @param source_dir Optional path to directory containing source partition images for incremental updates (pass NULL if not incremental)
/// @param callback Optional progress callback (pass NULL for no callback)
/// @param user_data User data passed to callback (can be NULL)
/// @return 0 on success, -1 on failure (check payload_get_last_error())
///
/// this function can be safely called from multiple threads concurrently.
/// each thread can extract a different partition in parallel.
/// The function automatically detects whether the file is a payload.bin or ZIP file
///
/// For incremental/differential OTA updates, you must provide source_dir containing the
/// old partition images (e.g., system.img, vendor.img). The function will look for
/// <partition_name>.img in the source_dir.
///
/// - pass NULL for callback parameter if you don't want progress updates
/// - the partition_name and warning_message pointers passed to the callback
///   are ONLY valid during the callback execution. Do NOT store these pointers.
/// - if you need to keep the strings, copy them immediately in the callback.
/// - Do NOT call free() on these strings, they are managed by the library.
///
/// - Return 0 from the callback to cancel extraction
/// - Return non-zero to continue
/// - Cancellation may not be immediate
#[unsafe(no_mangle)]
pub extern "C" fn payload_extract_remote_partition(
    url: *const c_char,
    partition_name: *const c_char,
    output_path: *const c_char,
    user_agent: *const c_char,
    cookies: *const c_char,
    source_dir: *const c_char,
    callback: CProgressCallback,
    user_data: *mut c_void,
) -> i32 {
    with_error_handling(|| {
        let url_str = c_str_to_rust(url, "url")?;
        let partition_str = c_str_to_rust(partition_name, "partition_name")?;
        let output_str = c_str_to_rust(output_path, "output_path")?;
        let user_agent_str = optional_c_str_to_rust(user_agent, "user_agent")?;
        let cookies_str = optional_c_str_to_rust(cookies, "cookies")?;
        let source_str = optional_c_str_to_rust(source_dir, "source_dir")?;
        let progress_cb = create_progress_callback(callback, user_data);

        extract_remote_partition(
            url_str.to_string(),
            partition_str,
            output_str,
            user_agent_str,
            cookies_str,
            source_str.map(|s| s.to_string()),
            progress_cb,
        )
        .map_err(|e| format!("Remote extraction failed: {}", e))
    })
}

/* Utility Functions */

/// get library version
/// returns a static string, do not free
#[unsafe(no_mangle)]
pub extern "C" fn payload_get_version() -> *const c_char {
    static C_VERSION: &[u8] = concat!(env!("CARGO_PKG_VERSION"), "\0").as_bytes();
    C_VERSION.as_ptr() as *const c_char
}

/// initialize the library (optional, but recommended for thread safety)
/// should be called once before any other library functions
/// @return 0 on success, -1 on failure
#[unsafe(no_mangle)]
pub extern "C" fn payload_init() -> i32 {
    // not yet implemented
    0
}

/// cleanup library resources
/// should be called once when done using the library
/// no library functions should be called after this
#[unsafe(no_mangle)]
pub extern "C" fn payload_cleanup() {
    // not yet implemented
}
