use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::jstring;
use payload_dumper_core::extractor::local::{
    ExtractionProgress, ExtractionStatus, ProgressCallback, extract_partition,
    extract_partition_zip, list_partitions, list_partitions_zip,
};
use payload_dumper_core::extractor::remote::{
    extract_partition_remote_bin, extract_partition_remote_zip, list_partitions_remote_bin,
    list_partitions_remote_zip,
};
use std::panic::AssertUnwindSafe;
use std::sync::Arc;

/* Utility Functions */

/// convert Java string to Rust string
fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String, String> {
    env.get_string(jstr)
        .map(|s| s.into())
        .map_err(|e| format!("Failed to convert Java string: {}", e))
}

/// convert Rust string to Java string (returns owned jstring)
fn string_to_jstring_owned(env: &mut JNIEnv, s: String) -> Result<jstring, String> {
    env.new_string(s)
        .map(|jstr| jstr.into_raw())
        .map_err(|e| format!("Failed to create Java string: {}", e))
}

/// Convert optional JString to Option<String>
fn optional_jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<Option<String>, String> {
    if jstr.is_null() {
        Ok(None)
    } else {
        jstring_to_string(env, jstr).map(Some)
    }
}

/// Generic wrapper for listing operations that return JSON strings
fn handle_list_operation<F>(mut env: JNIEnv, operation: F) -> jstring
where
    F: FnOnce(&mut JNIEnv) -> Result<String, String>,
{
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| operation(&mut env)));

    match result {
        Ok(Ok(json)) => match string_to_jstring_owned(&mut env, json) {
            Ok(jstr) => jstr,
            Err(e) => {
                let _ = env.throw_new("java/lang/RuntimeException", e);
                JObject::null().into_raw()
            }
        },
        Ok(Err(e)) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
            JObject::null().into_raw()
        }
        Err(_) => {
            let _ = env.throw_new(
                "java/lang/RuntimeException",
                "Panic occurred in native code",
            );
            JObject::null().into_raw()
        }
    }
}

/// Generic wrapper for extraction operations that return ()
fn handle_extraction_operation<F>(mut env: JNIEnv, operation: F)
where
    F: FnOnce(&mut JNIEnv) -> Result<(), String>,
{
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| operation(&mut env)));

    match result {
        Ok(Ok(())) => {}
        Ok(Err(e)) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
        }
        Err(_) => {
            let _ = env.throw_new(
                "java/lang/RuntimeException",
                "Panic occurred in native code",
            );
        }
    }
}

/// Create a progress callback from a Java callback object
fn create_progress_callback(
    env: &mut JNIEnv,
    callback: &JObject,
) -> Result<Option<ProgressCallback>, String> {
    if callback.is_null() {
        return Ok(None);
    }

    let callback_ref = env
        .new_global_ref(callback)
        .map_err(|e| format!("Failed to create global ref: {}", e))?;

    let jvm = env
        .get_java_vm()
        .map_err(|e| format!("Failed to get JavaVM: {}", e))?;

    let callback_ref = Arc::new(callback_ref);

    Ok(Some(Box::new(
        move |progress: ExtractionProgress| -> bool {
            let mut env = match jvm.attach_current_thread() {
                Ok(env) => env,
                Err(_) => return false,
            };

            match call_java_progress_callback(&mut env, callback_ref.as_obj(), progress) {
                Ok(should_continue) => should_continue,
                Err(_) => false,
            }
        },
    )))
}

fn call_java_progress_callback(
    env: &mut JNIEnv,
    callback: &JObject,
    progress: ExtractionProgress,
) -> Result<bool, String> {
    env.push_local_frame(16)
        .map_err(|e| format!("Failed to push local frame: {}", e))?;

    let result = (|| {
        let partition_name = env
            .new_string(&progress.partition_name)
            .map_err(|e| format!("Failed to create partition name string: {}", e))?;

        check_and_clear_exception(env, "creating partition name")?;

        let status_value = match progress.status {
            ExtractionStatus::Started => 0,
            ExtractionStatus::InProgress => 1,
            ExtractionStatus::Completed => 2,
            ExtractionStatus::Warning { .. } => 3,
        };

        let (warning_op_index, warning_message) = match progress.status {
            ExtractionStatus::Warning {
                operation_index,
                message,
            } => {
                let msg = env
                    .new_string(&message)
                    .map_err(|e| format!("Failed to create warning message: {}", e))?;

                check_and_clear_exception(env, "creating warning message")?;
                (operation_index as i32, msg)
            }
            _ => {
                let empty = env
                    .new_string("")
                    .map_err(|e| format!("Failed to create empty string: {}", e))?;

                check_and_clear_exception(env, "creating empty string")?;
                (0, empty)
            }
        };

        let call_result = env
            .call_method(
                callback,
                "onProgress",
                "(Ljava/lang/String;JJDIILjava/lang/String;)Z",
                &[
                    JValue::Object(&partition_name),
                    JValue::Long(progress.current_operation as i64),
                    JValue::Long(progress.total_operations as i64),
                    JValue::Double(progress.percentage),
                    JValue::Int(status_value),
                    JValue::Int(warning_op_index),
                    JValue::Object(&warning_message),
                ],
            )
            .map_err(|e| format!("Failed to call progress callback: {}", e))?;

        check_and_clear_exception(env, "Java callback")?;

        call_result
            .z()
            .map_err(|e| format!("Failed to extract boolean from callback result: {}", e))
    })();

    let _ = unsafe { env.pop_local_frame(&JObject::null()) };
    result
}

/// Helper to check and clear JNI exceptions
fn check_and_clear_exception(env: &mut JNIEnv, context: &str) -> Result<(), String> {
    if env
        .exception_check()
        .map_err(|e| format!("Failed to check exception: {}", e))?
    {
        let _ = env.exception_describe();
        env.exception_clear()
            .map_err(|e| format!("Failed to clear exception: {}", e))?;
        return Err(format!("Exception occurred while {}", context));
    }
    Ok(())
}

/* List Partitions */

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_listPartitions(
    env: JNIEnv,
    _class: JClass,
    payload_path: JString,
) -> jstring {
    handle_list_operation(env, |env| {
        let path = jstring_to_string(env, &payload_path)?;
        list_partitions(path).map_err(|e| format!("Failed to list partitions: {}", e))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_listPartitionsZip(
    env: JNIEnv,
    _class: JClass,
    zip_path: JString,
) -> jstring {
    handle_list_operation(env, |env| {
        let path = jstring_to_string(env, &zip_path)?;
        list_partitions_zip(path).map_err(|e| format!("Failed to list partitions: {}", e))
    })
}

/* Extract Partition */

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_extractPartition(
    env: JNIEnv,
    _class: JClass,
    payload_path: JString,
    partition_name: JString,
    output_path: JString,
    callback: JObject,
) {
    handle_extraction_operation(env, |env| {
        let payload = jstring_to_string(env, &payload_path)?;
        let partition = jstring_to_string(env, &partition_name)?;
        let output = jstring_to_string(env, &output_path)?;
        let progress_callback = create_progress_callback(env, &callback)?;

        extract_partition(payload, &partition, output, progress_callback)
            .map_err(|e| format!("Extraction failed: {}", e))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_extractPartitionZip(
    env: JNIEnv,
    _class: JClass,
    zip_path: JString,
    partition_name: JString,
    output_path: JString,
    callback: JObject,
) {
    handle_extraction_operation(env, |env| {
        let zip = jstring_to_string(env, &zip_path)?;
        let partition = jstring_to_string(env, &partition_name)?;
        let output = jstring_to_string(env, &output_path)?;
        let progress_callback = create_progress_callback(env, &callback)?;

        extract_partition_zip(zip, &partition, output, progress_callback)
            .map_err(|e| format!("Extraction failed: {}", e))
    })
}

/* Remote Operations - List Partitions */

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_listPartitionsRemoteZip(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    user_agent: JString,
    cookies: JString,
) -> jstring {
    handle_list_operation(env, |env| {
        let url_str = jstring_to_string(env, &url)?;
        let user_agent_str = optional_jstring_to_string(env, &user_agent)?;
        let cookies_str = optional_jstring_to_string(env, &cookies)?;

        let result =
            list_partitions_remote_zip(url_str, user_agent_str.as_deref(), cookies_str.as_deref())
                .map_err(|e| format!("Failed to list remote partitions: {}", e))?;

        Ok(result.json)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_listPartitionsRemoteBin(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    user_agent: JString,
    cookies: JString,
) -> jstring {
    handle_list_operation(env, |env| {
        let url_str = jstring_to_string(env, &url)?;
        let user_agent_str = optional_jstring_to_string(env, &user_agent)?;
        let cookies_str = optional_jstring_to_string(env, &cookies)?;

        let result =
            list_partitions_remote_bin(url_str, user_agent_str.as_deref(), cookies_str.as_deref())
                .map_err(|e| format!("Failed to list remote partitions: {}", e))?;

        Ok(result.json)
    })
}

/* Remote Operations - Extract Partition */

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_extractPartitionRemoteZip(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    partition_name: JString,
    output_path: JString,
    user_agent: JString,
    cookies: JString,
    callback: JObject,
) {
    handle_extraction_operation(env, |env| {
        let url_str = jstring_to_string(env, &url)?;
        let partition = jstring_to_string(env, &partition_name)?;
        let output = jstring_to_string(env, &output_path)?;
        let user_agent_str = optional_jstring_to_string(env, &user_agent)?;
        let cookies_str = optional_jstring_to_string(env, &cookies)?;
        let progress_callback = create_progress_callback(env, &callback)?;

        extract_partition_remote_zip(
            url_str,
            &partition,
            output,
            user_agent_str.as_deref(),
            cookies_str.as_deref(),
            progress_callback,
        )
        .map_err(|e| format!("Extraction failed: {}", e))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_extractPartitionRemoteBin(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    partition_name: JString,
    output_path: JString,
    user_agent: JString,
    cookies: JString,
    callback: JObject,
) {
    handle_extraction_operation(env, |env| {
        let url_str = jstring_to_string(env, &url)?;
        let partition = jstring_to_string(env, &partition_name)?;
        let output = jstring_to_string(env, &output_path)?;
        let user_agent_str = optional_jstring_to_string(env, &user_agent)?;
        let cookies_str = optional_jstring_to_string(env, &cookies)?;
        let progress_callback = create_progress_callback(env, &callback)?;

        extract_partition_remote_bin(
            url_str,
            &partition,
            output,
            user_agent_str.as_deref(),
            cookies_str.as_deref(),
            progress_callback,
        )
        .map_err(|e| format!("Extraction failed: {}", e))
    })
}
