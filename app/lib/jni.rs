// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 rhythmcache

use crate::extractor::{
    ExtractionProgress, ExtractionStatus, ProgressCallback, extract_local_partition,
    extract_remote_partition, list_local_partitions, list_remote_partitions,
};
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::jstring;
use std::panic::AssertUnwindSafe;
use std::sync::Arc;

fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String, String> {
    env.get_string(jstr)
        .map(|s| s.into())
        .map_err(|e| format!("Failed to convert Java string: {}", e))
}

fn string_to_jstring(env: &mut JNIEnv, s: String) -> Result<jstring, String> {
    env.new_string(s)
        .map(|jstr| jstr.into_raw())
        .map_err(|e| format!("Failed to create Java string: {}", e))
}

fn optional_jstring(env: &mut JNIEnv, jstr: &JString) -> Result<Option<String>, String> {
    if jstr.is_null() {
        Ok(None)
    } else {
        jstring_to_string(env, jstr).map(Some)
    }
}

fn handle_list<F>(mut env: JNIEnv, op: F) -> jstring
where
    F: FnOnce(&mut JNIEnv) -> Result<String, String>,
{
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| op(&mut env)));

    match result {
        Ok(Ok(json)) => match string_to_jstring(&mut env, json) {
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
            let _ = env.throw_new("java/lang/RuntimeException", "Panic in native code");
            JObject::null().into_raw()
        }
    }
}

fn handle_extract<F>(mut env: JNIEnv, op: F)
where
    F: FnOnce(&mut JNIEnv) -> Result<(), String>,
{
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| op(&mut env)));

    match result {
        Ok(Ok(())) => {}
        Ok(Err(e)) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
        }
        Err(_) => {
            let _ = env.throw_new("java/lang/RuntimeException", "Panic in native code");
        }
    }
}

fn create_callback(
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

            match call_java_callback(&mut env, callback_ref.as_obj(), progress) {
                Ok(should_continue) => should_continue,
                Err(_) => false,
            }
        },
    )))
}

fn call_java_callback(
    env: &mut JNIEnv,
    callback: &JObject,
    progress: ExtractionProgress,
) -> Result<bool, String> {
    env.push_local_frame(16)
        .map_err(|e| format!("Failed to push local frame: {}", e))?;

    let result = (|| {
        let partition_name = env
            .new_string(&progress.partition_name)
            .map_err(|e| format!("Failed to create partition name: {}", e))?;

        check_exception(env, "creating partition name")?;

        let status_value = match progress.status {
            ExtractionStatus::Started => 0,
            ExtractionStatus::InProgress => 1,
            ExtractionStatus::Completed => 2,
            ExtractionStatus::Warning { .. } => 3,
        };

        let (warning_idx, warning_msg) = match progress.status {
            ExtractionStatus::Warning {
                operation_index,
                message,
            } => {
                let msg = env
                    .new_string(&message)
                    .map_err(|e| format!("Failed to create warning message: {}", e))?;
                check_exception(env, "creating warning message")?;
                (operation_index as i32, msg)
            }
            _ => {
                let empty = env
                    .new_string("")
                    .map_err(|e| format!("Failed to create empty string: {}", e))?;
                check_exception(env, "creating empty string")?;
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
                    JValue::Int(warning_idx),
                    JValue::Object(&warning_msg),
                ],
            )
            .map_err(|e| format!("Failed to call callback: {}", e))?;

        check_exception(env, "Java callback")?;

        call_result
            .z()
            .map_err(|e| format!("Failed to extract boolean: {}", e))
    })();

    let _ = unsafe { env.pop_local_frame(&JObject::null()) };
    result
}

fn check_exception(env: &mut JNIEnv, context: &str) -> Result<(), String> {
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_listLocalPartitions(
    env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    handle_list(env, |env| {
        let path_str = jstring_to_string(env, &path)?;
        list_local_partitions(path_str).map_err(|e| format!("Failed to list: {}", e))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_listRemotePartitions(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    user_agent: JString,
    cookies: JString,
) -> jstring {
    handle_list(env, |env| {
        let url_str = jstring_to_string(env, &url)?;
        let ua = optional_jstring(env, &user_agent)?;
        let ck = optional_jstring(env, &cookies)?;
        list_remote_partitions(url_str, ua.as_deref(), ck.as_deref())
            .map_err(|e| format!("Failed to list: {}", e))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_extractLocalPartition(
    env: JNIEnv,
    _class: JClass,
    path: JString,
    partition_name: JString,
    output_path: JString,
    callback: JObject,
) {
    handle_extract(env, |env| {
        let path_str = jstring_to_string(env, &path)?;
        let partition = jstring_to_string(env, &partition_name)?;
        let output = jstring_to_string(env, &output_path)?;
        let cb = create_callback(env, &callback)?;

        extract_local_partition(path_str, &partition, output, cb)
            .map_err(|e| format!("Extraction failed: {}", e))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rhythmcache_payloaddumper_PayloadDumper_extractRemotePartition(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    partition_name: JString,
    output_path: JString,
    user_agent: JString,
    cookies: JString,
    callback: JObject,
) {
    handle_extract(env, |env| {
        let url_str = jstring_to_string(env, &url)?;
        let partition = jstring_to_string(env, &partition_name)?;
        let output = jstring_to_string(env, &output_path)?;
        let ua = optional_jstring(env, &user_agent)?;
        let ck = optional_jstring(env, &cookies)?;
        let cb = create_callback(env, &callback)?;

        extract_remote_partition(
            url_str,
            &partition,
            output,
            ua.as_deref(),
            ck.as_deref(),
            cb,
        )
        .map_err(|e| format!("Extraction failed: {}", e))
    })
}
