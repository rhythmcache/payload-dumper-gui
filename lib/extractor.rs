// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 rhythmcache

use anyhow::{Result, anyhow};
use once_cell::sync::Lazy;
use payload_dumper_core::constants::{PAYLOAD_MAGIC, ZIP_MAGIC};
use payload_dumper_core::http::HttpReader;
use payload_dumper_core::metadata::get_metadata;
use payload_dumper_core::payload::payload_dumper::{ProgressReporter, dump_partition};
use payload_dumper_core::payload::payload_parser::{
    parse_local_payload, parse_local_zip_payload, parse_remote_bin_payload, parse_remote_payload,
};
use payload_dumper_core::readers::{
    local_reader::LocalAsyncPayloadReader, local_zip_reader::LocalAsyncZipPayloadReader,
    remote_bin_reader::RemoteAsyncBinPayloadReader, remote_zip_reader::RemoteAsyncZipPayloadReader,
};
use payload_dumper_core::utils::{format_size, is_diff_operation};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::fs::File;
use tokio::io::AsyncReadExt;
use tokio::runtime::Runtime;

pub static RUNTIME: Lazy<Runtime> = Lazy::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(num_cpus::get().max(2))
        .enable_all()
        .build()
        .expect("Failed to create tokio runtime")
});

#[derive(Debug, Clone)]
pub struct ExtractionProgress {
    pub partition_name: String,
    pub current_operation: u64,
    pub total_operations: u64,
    pub percentage: f64,
    pub status: ExtractionStatus,
}

#[derive(Debug, Clone)]
pub enum ExtractionStatus {
    Started,
    InProgress,
    Completed,
    Warning {
        operation_index: usize,
        message: String,
    },
}

pub type ProgressCallback = Box<dyn Fn(ExtractionProgress) -> bool + Send + Sync>;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PartitionInfo {
    pub name: String,
    pub size_bytes: u64,
    pub size_readable: String,
    pub operations_count: usize,
    pub compression_type: String,
    pub hash: Option<String>,
    pub is_differential: bool,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PayloadSummary {
    pub partitions: Vec<PartitionInfo>,
    pub total_partitions: usize,
    pub total_operations: usize,
    pub total_size_bytes: u64,
    pub total_size_readable: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub security_patch_level: Option<String>,
    pub is_incremental: bool,
}

#[derive(Debug, Clone, Copy)]
enum FileType {
    Zip,
    Bin,
}

async fn detect_local_type(path: &Path) -> Result<FileType> {
    let mut file = File::open(path).await?;
    let mut magic = [0u8; 4];
    file.read_exact(&mut magic).await?;

    if magic[0..2] == ZIP_MAGIC {
        Ok(FileType::Zip)
    } else if &magic == PAYLOAD_MAGIC {
        Ok(FileType::Bin)
    } else {
        Err(anyhow!("Unsupported file format"))
    }
}

async fn detect_remote_type(url: &str, ua: Option<&str>, ck: Option<&str>) -> Result<FileType> {
    let reader = HttpReader::new(url.to_string(), ua, ck).await?;
    let mut magic = [0u8; 4];
    reader.read_at(0, &mut magic).await?;

    if magic[0..2] == ZIP_MAGIC {
        Ok(FileType::Zip)
    } else if &magic == PAYLOAD_MAGIC {
        Ok(FileType::Bin)
    } else {
        Err(anyhow!("Unsupported file format"))
    }
}

pub struct CallbackProgressReporter {
    callback: Arc<ProgressCallback>,
    cancelled: Arc<AtomicBool>,
}

impl CallbackProgressReporter {
    pub fn new(callback: ProgressCallback) -> Self {
        Self {
            callback: Arc::new(callback),
            cancelled: Arc::new(AtomicBool::new(false)),
        }
    }
}

impl ProgressReporter for CallbackProgressReporter {
    fn on_start(&self, partition_name: &str, total_operations: u64) {
        let progress = ExtractionProgress {
            partition_name: partition_name.to_string(),
            current_operation: 0,
            total_operations,
            percentage: 0.0,
            status: ExtractionStatus::Started,
        };
        if !(self.callback)(progress) {
            self.cancelled.store(true, Ordering::SeqCst);
        }
    }

    fn on_progress(&self, partition_name: &str, current_op: u64, total_ops: u64) {
        let percentage = if total_ops > 0 {
            (current_op as f64 / total_ops as f64) * 100.0
        } else {
            0.0
        };
        let progress = ExtractionProgress {
            partition_name: partition_name.to_string(),
            current_operation: current_op,
            total_operations: total_ops,
            percentage,
            status: ExtractionStatus::InProgress,
        };
        if !(self.callback)(progress) {
            self.cancelled.store(true, Ordering::SeqCst);
        }
    }

    fn on_complete(&self, partition_name: &str, total_operations: u64) {
        let progress = ExtractionProgress {
            partition_name: partition_name.to_string(),
            current_operation: total_operations,
            total_operations,
            percentage: 100.0,
            status: ExtractionStatus::Completed,
        };
        (self.callback)(progress);
    }

    fn on_warning(&self, partition_name: &str, operation_index: usize, message: String) {
        let progress = ExtractionProgress {
            partition_name: partition_name.to_string(),
            current_operation: operation_index as u64,
            total_operations: 0,
            percentage: 0.0,
            status: ExtractionStatus::Warning {
                operation_index,
                message,
            },
        };
        (self.callback)(progress);
    }

    fn is_cancelled(&self) -> bool {
        self.cancelled.load(Ordering::SeqCst)
    }
}

pub fn list_local_partitions<P: AsRef<Path>>(path: P) -> Result<String> {
    if tokio::runtime::Handle::try_current().is_ok() {
        panic!("Cannot be called from async context");
    }

    RUNTIME.block_on(async {
        let file_type = detect_local_type(path.as_ref()).await?;

        let (manifest, data_offset) = match file_type {
            FileType::Bin => parse_local_payload(path.as_ref()).await?,
            FileType::Zip => parse_local_zip_payload(path.as_ref().to_path_buf()).await?,
        };

        let metadata = get_metadata(&manifest, data_offset, false, None).await?;
        build_summary(&manifest, &metadata)
    })
}

pub fn list_remote_partitions(url: String, ua: Option<&str>, ck: Option<&str>) -> Result<String> {
    if tokio::runtime::Handle::try_current().is_ok() {
        panic!("Cannot be called from async context");
    }

    RUNTIME.block_on(async {
        let file_type = detect_remote_type(&url, ua, ck).await?;

        let (manifest, data_offset, _) = match file_type {
            FileType::Zip => parse_remote_payload(url, ua, ck).await?,
            FileType::Bin => parse_remote_bin_payload(url, ua, ck).await?,
        };

        let metadata = get_metadata(&manifest, data_offset, false, None).await?;
        build_summary(&manifest, &metadata)
    })
}

pub fn extract_local_partition<P1: AsRef<Path>, P2: AsRef<Path>>(
    path: P1,
    partition_name: &str,
    output_path: P2,
    source_dir: Option<String>,
    callback: Option<ProgressCallback>,
) -> Result<()> {
    if tokio::runtime::Handle::try_current().is_ok() {
        panic!("Cannot be called from async context");
    }

    RUNTIME.block_on(async {
        let file_type = detect_local_type(path.as_ref()).await?;

        let (manifest, data_offset) = match file_type {
            FileType::Bin => parse_local_payload(path.as_ref()).await?,
            FileType::Zip => parse_local_zip_payload(path.as_ref().to_path_buf()).await?,
        };

        let partition = find_partition(&manifest, partition_name)?;
        let block_size = manifest.block_size.unwrap_or(4096) as u64;

        if let Some(parent) = output_path.as_ref().parent() {
            tokio::fs::create_dir_all(parent).await?;
        }

        let reporter = create_reporter(callback);

        let source_path = source_dir.map(PathBuf::from);

        match file_type {
            FileType::Bin => {
                let reader = LocalAsyncPayloadReader::new(path.as_ref().to_path_buf()).await?;
                dump_partition(
                    partition,
                    data_offset,
                    block_size,
                    output_path.as_ref().to_path_buf(),
                    &reader,
                    &*reporter,
                    source_path,
                )
                .await
            }
            FileType::Zip => {
                let reader = LocalAsyncZipPayloadReader::new(path.as_ref().to_path_buf()).await?;
                dump_partition(
                    partition,
                    data_offset,
                    block_size,
                    output_path.as_ref().to_path_buf(),
                    &reader,
                    &*reporter,
                    source_path,
                )
                .await
            }
        }
    })
}

pub fn extract_remote_partition<P: AsRef<Path>>(
    url: String,
    partition_name: &str,
    output_path: P,
    ua: Option<&str>,
    ck: Option<&str>,
    source_dir: Option<String>,
    callback: Option<ProgressCallback>,
) -> Result<()> {
    if tokio::runtime::Handle::try_current().is_ok() {
        panic!("Cannot be called from async context");
    }

    RUNTIME.block_on(async {
        let file_type = detect_remote_type(&url, ua, ck).await?;

        let (manifest, data_offset, _) = match file_type {
            FileType::Zip => parse_remote_payload(url.clone(), ua, ck).await?,
            FileType::Bin => parse_remote_bin_payload(url.clone(), ua, ck).await?,
        };

        let partition = find_partition(&manifest, partition_name)?;
        let block_size = manifest.block_size.unwrap_or(4096) as u64;

        if let Some(parent) = output_path.as_ref().parent() {
            tokio::fs::create_dir_all(parent).await?;
        }

        let reporter = create_reporter(callback);

        let source_path = source_dir.map(PathBuf::from);

        match file_type {
            FileType::Zip => {
                let reader = RemoteAsyncZipPayloadReader::new(url, ua, ck).await?;
                dump_partition(
                    partition,
                    data_offset,
                    block_size,
                    output_path.as_ref().to_path_buf(),
                    &reader,
                    &*reporter,
                    source_path,
                )
                .await
            }
            FileType::Bin => {
                let reader = RemoteAsyncBinPayloadReader::new(url, ua, ck).await?;
                dump_partition(
                    partition,
                    data_offset,
                    block_size,
                    output_path.as_ref().to_path_buf(),
                    &reader,
                    &*reporter,
                    source_path,
                )
                .await
            }
        }
    })
}

fn is_partition_differential(partition: &payload_dumper_core::structs::PartitionUpdate) -> bool {
    partition
        .operations
        .iter()
        .any(|op| is_diff_operation(op.r#type()))
}

fn build_summary(
    manifest: &payload_dumper_core::structs::DeltaArchiveManifest,
    metadata: &payload_dumper_core::structs::PayloadMetadata,
) -> Result<String> {
    let mut is_incremental = false;

    let partitions: Vec<PartitionInfo> = manifest
        .partitions
        .iter()
        .zip(metadata.partitions.iter())
        .map(|(manifest_part, metadata_part)| {
            let is_diff = is_partition_differential(manifest_part);
            if is_diff {
                is_incremental = true;
            }

            PartitionInfo {
                name: metadata_part.partition_name.clone(),
                size_bytes: metadata_part.size_in_bytes,
                size_readable: metadata_part.size_readable.clone(),
                operations_count: metadata_part.operations_count,
                compression_type: metadata_part.compression_type.clone(),
                hash: metadata_part.hash.clone(),
                is_differential: is_diff,
            }
        })
        .collect();

    let total_size: u64 = partitions.iter().map(|p| p.size_bytes).sum();

    let summary = PayloadSummary {
        total_partitions: partitions.len(),
        total_operations: metadata.total_operations_count,
        total_size_bytes: total_size,
        total_size_readable: format_size(total_size),
        partitions,
        security_patch_level: metadata.security_patch_level.clone(),
        is_incremental,
    };

    serde_json::to_string_pretty(&summary).map_err(|e| anyhow!("Serialization failed: {}", e))
}

fn find_partition<'a>(
    manifest: &'a payload_dumper_core::structs::DeltaArchiveManifest,
    partition_name: &str,
) -> Result<&'a payload_dumper_core::structs::PartitionUpdate> {
    manifest
        .partitions
        .iter()
        .find(|p| p.partition_name == partition_name)
        .ok_or_else(|| anyhow!("Partition '{}' not found", partition_name))
}

fn create_reporter(callback: Option<ProgressCallback>) -> Box<dyn ProgressReporter> {
    if let Some(cb) = callback {
        Box::new(CallbackProgressReporter::new(cb))
    } else {
        Box::new(payload_dumper_core::payload::payload_dumper::NoOpReporter)
    }
}
