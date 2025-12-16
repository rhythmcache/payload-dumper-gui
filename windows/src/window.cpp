#include <windows.h>
#include <commdlg.h>
#include <shellapi.h>
#include <shlobj.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "imgui.h"
#include "json.h"
#include "payload_dumper.hpp"
#include "sha256.h"

struct Part {
  std::string name;
  uint64_t size_bytes;
  std::string size_readable;
  uint64_t operations_count;
  std::string hash;

  std::atomic<bool> selected;
  std::atomic<bool> extracting;
  std::atomic<bool> verifying;
  std::atomic<float> progress;
  std::atomic<float> verify_progress;
  std::atomic<bool> cancel_flag;
  std::atomic<bool> verification_passed;

  mutable std::mutex status_mutex;
  std::string status_msg;
  std::string verify_status_msg;

  Part()
      : size_bytes(0),
        operations_count(0),
        selected(false),
        extracting(false),
        verifying(false),
        progress(0.0f),
        verify_progress(0.0f),
        cancel_flag(false),
        verification_passed(false) {}

  Part(const Part&) = delete;
  Part& operator=(const Part&) = delete;

  Part(Part&& other) noexcept
      : name(std::move(other.name)),
        size_bytes(other.size_bytes),
        size_readable(std::move(other.size_readable)),
        operations_count(other.operations_count),
        hash(std::move(other.hash)),
        selected(other.selected.load()),
        extracting(other.extracting.load()),
        verifying(other.verifying.load()),
        progress(other.progress.load()),
        verify_progress(other.verify_progress.load()),
        cancel_flag(other.cancel_flag.load()),
        verification_passed(other.verification_passed.load()),
        status_msg(std::move(other.status_msg)),
        verify_status_msg(std::move(other.verify_status_msg)) {}

  Part& operator=(Part&& other) noexcept {
    if (this != &other) {
      name = std::move(other.name);
      size_bytes = other.size_bytes;
      size_readable = std::move(other.size_readable);
      operations_count = other.operations_count;
      hash = std::move(other.hash);
      selected.store(other.selected.load());
      extracting.store(other.extracting.load());
      verifying.store(other.verifying.load());
      progress.store(other.progress.load());
      verify_progress.store(other.verify_progress.load());
      cancel_flag.store(other.cancel_flag.load());
      verification_passed.store(other.verification_passed.load());
      std::lock_guard<std::mutex> lock(status_mutex);
      status_msg = std::move(other.status_msg);
      verify_status_msg = std::move(other.verify_status_msg);
    }
    return *this;
  }

  void set_status(const std::string& msg) {
    std::lock_guard<std::mutex> lock(status_mutex);
    status_msg = msg;
  }

  std::string get_status() const {
    std::lock_guard<std::mutex> lock(status_mutex);
    return status_msg;
  }

  void set_verify_status(const std::string& msg) {
    std::lock_guard<std::mutex> lock(status_mutex);
    verify_status_msg = msg;
  }

  std::string get_verify_status() const {
    std::lock_guard<std::mutex> lock(status_mutex);
    return verify_status_msg;
  }
};

struct Status {
  enum class Source { SRC_FILE, SRC_URL };
  enum class SRC_TYPE { TYPE_NONE, TYPE_BIN, TYPE_ZIP };

  Source input_mode;
  SRC_TYPE detected_file_type;

  char file_path[512];
  char url_input[1024];
  char output_dir[512];
  char user_agent[256];

  std::deque<Part> partitions;
  std::vector<std::thread> extraction_threads;

  uint64_t total_partitions;
  uint64_t total_operations;
  uint64_t total_size_bytes;
  std::string total_size_readable;
  std::string security_patch_level;

  std::mutex partitions_mutex;

  std::string error_message;
  bool show_error_popup;
  bool partitions_loaded;
  bool enable_verification;

  std::atomic<bool> loading_partitions;
  std::thread loading_thread;

  std::atomic<bool> shutdown_requested;

  Status()
      : input_mode(Source::SRC_FILE),
        detected_file_type(SRC_TYPE::TYPE_NONE),
        total_partitions(0),
        total_operations(0),
        total_size_bytes(0),
        security_patch_level(""),
        show_error_popup(false),
        partitions_loaded(false),
        enable_verification(true),
        loading_partitions(false),
        shutdown_requested(false) {
    file_path[0] = '\0';
    url_input[0] = '\0';
    output_dir[0] = '\0';
    snprintf(user_agent, sizeof(user_agent), "PayloadDumper-GUI/%d.%d.%d",
             PAYLOAD_DUMPER_MAJOR, PAYLOAD_DUMPER_MINOR, PAYLOAD_DUMPER_PATCH);

    GetCurrentDirectoryA(sizeof(output_dir), output_dir);
  }

  void clear_partitions() {
    std::lock_guard<std::mutex> lock(partitions_mutex);
    partitions.clear();
    total_partitions = 0;
    total_operations = 0;
    total_size_bytes = 0;
    total_size_readable.clear();
    security_patch_level.clear();
    partitions_loaded = false;
  }

  void set_error(const char* msg) {
    error_message = msg ? msg : "TYPE_NONE error";
    show_error_popup = true;
  }

  SRC_TYPE detect_file_type(const char* path) {
    size_t len = strlen(path);
    if (len < 4) return SRC_TYPE::TYPE_NONE;

    const char* ext = path + len - 4;
    if (_stricmp(ext, ".zip") == 0) return SRC_TYPE::TYPE_ZIP;
    if (_stricmp(ext, ".bin") == 0) return SRC_TYPE::TYPE_BIN;

    if (len > 12 && _stricmp(path + len - 12, ".payload.bin") == 0)
      return SRC_TYPE::TYPE_BIN;

    return SRC_TYPE::TYPE_NONE;
  }
};

static Status G;

bool chooser(char* buffer, size_t buffer_size) {
  OPENFILENAMEA ofn;
  ZeroMemory(&ofn, sizeof(ofn));

  ofn.lStructSize = sizeof(ofn);
  ofn.hwndOwner = GetActiveWindow();
  ofn.lpstrFile = buffer;
  ofn.nMaxFile = static_cast<DWORD>(buffer_size);

  ofn.lpstrFilter =
      "bin/zip (*.bin, *.zip)\0*.bin;*.zip\0All Files (*.*)\0*.*\0";

  ofn.nFilterIndex = 1;
  ofn.lpstrFileTitle = nullptr;
  ofn.nMaxFileTitle = 0;
  ofn.lpstrInitialDir = nullptr;
  ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST | OFN_NOCHANGEDIR;

  return GetOpenFileNameA(&ofn) != 0;
}

bool out_chooser(char* buffer, size_t buffer_size) {
  BROWSEINFOA bi;
  ZeroMemory(&bi, sizeof(bi));

  bi.hwndOwner = GetActiveWindow();
  bi.lpszTitle = "Select Output Directory";
  bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE | BIF_USENEWUI;

  LPITEMIDLIST pidl = SHBrowseForFolderA(&bi);
  if (pidl != nullptr) {
    bool result = SHGetPathFromIDListA(pidl, buffer);
    CoTaskMemFree(pidl);
    return result;
  }
  return false;
}

bool read_json(const char* json_str, Status& state) {
  struct json_value_s* root = json_parse(json_str, strlen(json_str));
  if (!root) return false;

  struct json_object_s* root_obj = (struct json_object_s*)root->payload;

  state.clear_partitions();

  std::lock_guard<std::mutex> lock(state.partitions_mutex);

  for (struct json_object_element_s* elem = root_obj->start; elem;
       elem = elem->next) {
    const char* key = elem->name->string;

    if (strcmp(key, "total_partitions") == 0) {
      struct json_number_s* num = (struct json_number_s*)elem->value->payload;
      state.total_partitions = strtoull(num->number, nullptr, 10);
    } else if (strcmp(key, "total_operations") == 0) {
      struct json_number_s* num = (struct json_number_s*)elem->value->payload;
      state.total_operations = strtoull(num->number, nullptr, 10);
    } else if (strcmp(key, "total_size_bytes") == 0) {
      struct json_number_s* num = (struct json_number_s*)elem->value->payload;
      state.total_size_bytes = strtoull(num->number, nullptr, 10);
    } else if (strcmp(key, "total_size_readable") == 0) {
      struct json_string_s* str = (struct json_string_s*)elem->value->payload;
      state.total_size_readable = str->string;
    } else if (strcmp(key, "security_patch_level") == 0) {
      struct json_string_s* str = (struct json_string_s*)elem->value->payload;
      state.security_patch_level = str->string;
    } else if (strcmp(key, "partitions") == 0) {
      struct json_array_s* arr = (struct json_array_s*)elem->value->payload;

      for (struct json_array_element_s* part_elem = arr->start; part_elem;
           part_elem = part_elem->next) {
        struct json_object_s* part_obj =
            (struct json_object_s*)part_elem->value->payload;

        Part info;
        for (struct json_object_element_s* field = part_obj->start; field;
             field = field->next) {
          const char* field_key = field->name->string;

          if (strcmp(field_key, "name") == 0) {
            struct json_string_s* str =
                (struct json_string_s*)field->value->payload;
            info.name = str->string;
          } else if (strcmp(field_key, "size_bytes") == 0) {
            struct json_number_s* num =
                (struct json_number_s*)field->value->payload;
            info.size_bytes = strtoull(num->number, nullptr, 10);
          } else if (strcmp(field_key, "size_readable") == 0) {
            struct json_string_s* str =
                (struct json_string_s*)field->value->payload;
            info.size_readable = str->string;
          } else if (strcmp(field_key, "operations_count") == 0) {
            struct json_number_s* num =
                (struct json_number_s*)field->value->payload;
            info.operations_count = strtoull(num->number, nullptr, 10);
          } else if (strcmp(field_key, "hash") == 0) {
            struct json_string_s* str =
                (struct json_string_s*)field->value->payload;
            info.hash = str->string;
          }
        }

        state.partitions.emplace_back(std::move(info));
      }
    }
  }

  free(root);
  state.partitions_loaded = true;
  return true;
}

int32_t progress_callback(void* user_data, const char* partition_name,
                          uint64_t current_op, uint64_t total_ops,
                          double percentage, int32_t status,
                          const char* warning_msg) {
  Part* info = static_cast<Part*>(user_data);

  if (info->cancel_flag.load() || G.shutdown_requested.load()) {
    return 0;
  }

  info->progress.store(static_cast<float>(percentage));

  switch (status) {
    case STATUS_STARTED:
      info->set_status("Starting...");
      break;
    case STATUS_IN_PROGRESS:
      info->set_status("Extracting...");
      break;
    case STATUS_COMPLETED:
      info->set_status("Completed");
      break;
    case STATUS_WARNING:
      if (warning_msg) {
        info->set_status(std::string("Warning: ") + warning_msg);
      }
      break;
  }

  return 1;
}

void verify_part(Part* info, const std::string& output_path) {
  info->verifying.store(true);
  info->verify_progress.store(0.0f);
  info->set_verify_status("Verifying...");

  FILE* file = fopen(output_path.c_str(), "rb");
  if (!file) {
    info->set_verify_status("Error: Cannot open file");
    info->verification_passed.store(false);
    info->verifying.store(false);
    return;
  }

  fseek(file, 0, SEEK_END);
  uint64_t file_size = ftell(file);
  fseek(file, 0, SEEK_SET);

  SHA256_CTX ctx;
  sha256_init(&ctx);

  const size_t BUFFER_SIZE = 1024 * 1024;
  uint8_t* buffer = new uint8_t[BUFFER_SIZE];
  uint64_t bytes_read = 0;

  while (!feof(file) && !info->cancel_flag.load() &&
         !G.shutdown_requested.load()) {
    size_t n = fread(buffer, 1, BUFFER_SIZE, file);
    if (n > 0) {
      sha256_update(&ctx, buffer, n);
      bytes_read += n;
      float progress = (bytes_read * 100.0f) / file_size;
      info->verify_progress.store(progress);
    }
    if (n < BUFFER_SIZE) break;
  }

  delete[] buffer;
  fclose(file);

  if (info->cancel_flag.load() || G.shutdown_requested.load()) {
    info->set_verify_status("Verification cancelled");
    info->verification_passed.store(false);
    info->verifying.store(false);
    return;
  }

  uint8_t computed_hash[SHA256_DIGEST_SIZE];
  sha256_final(&ctx, computed_hash);

  char computed_hex[65];
  sha256_to_hex(computed_hash, computed_hex);

  if (info->hash.empty()) {
    info->set_verify_status("No hash to verify");
    info->verification_passed.store(false);
  } else if (_stricmp(computed_hex, info->hash.c_str()) == 0) {
    info->set_verify_status("Verified");
    info->verification_passed.store(true);
  } else {
    info->set_verify_status("Verification FAILED!");
    info->verification_passed.store(false);
  }

  info->verify_progress.store(100.0f);
  info->verifying.store(false);
}

void dump_part(Part* info, const std::string& source_path, Status::Source mode,
               Status::SRC_TYPE file_type, const std::string& output_dir,
               const std::string& user_agent, bool verify) {
  char output_path[512];
  snprintf(output_path, sizeof(output_path), "%s/%s.img", output_dir.c_str(),
           info->name.c_str());

  int32_t result = -1;

  if (mode == Status::Source::SRC_FILE) {
    if (file_type == Status::SRC_TYPE::TYPE_ZIP) {
      result =
          payload_extract_partition_zip(source_path.c_str(), info->name.c_str(),
                                        output_path, progress_callback, info);
    } else {
      result =
          payload_extract_partition(source_path.c_str(), info->name.c_str(),
                                    output_path, progress_callback, info);
    }
  } else {
    if (file_type == Status::SRC_TYPE::TYPE_ZIP) {
      result = payload_extract_partition_remote_zip(
          source_path.c_str(), info->name.c_str(), output_path,
          user_agent.c_str(), nullptr, progress_callback, info);
    } else {
      result = payload_extract_partition_remote_bin(
          source_path.c_str(), info->name.c_str(), output_path,
          user_agent.c_str(), nullptr, progress_callback, info);
    }
  }

  if (result != 0 && !info->cancel_flag.load()) {
    const char* err = payload_get_last_error();
    info->set_status(err ? std::string("Error: ") + err : "Extraction failed");
  } else if (info->cancel_flag.load()) {
    info->set_status("Cancelled");
  } else {
    info->set_status("Completed");

    if (verify && !info->hash.empty()) {
      verify_part(info, output_path);
    } else if (verify && info->hash.empty()) {
      info->set_verify_status("No hash available");
    }
  }

  info->extracting.store(false);
}

void start_extraction(Part* info) {
  std::string source =
      G.input_mode == Status::Source::SRC_FILE ? G.file_path : G.url_input;
  std::string output = G.output_dir;
  std::string ua = G.user_agent;
  auto mode = G.input_mode;
  auto ftype = G.detected_file_type;
  bool verify = G.enable_verification;

  info->extracting.store(true);
  info->progress.store(0);
  info->cancel_flag.store(false);
  info->set_status("Starting...");
  info->verification_passed.store(false);
  info->set_verify_status("");

  G.extraction_threads.emplace_back(dump_part, info, source, mode, ftype,
                                    output, ua, verify);
}

void load_it() {
  G.loading_partitions.store(true);

  char* json_result = nullptr;

  if (G.input_mode == Status::Source::SRC_FILE) {
    if (G.detected_file_type == Status::SRC_TYPE::TYPE_ZIP) {
      json_result = payload_list_partitions_zip(G.file_path);
    } else {
      json_result = payload_list_partitions(G.file_path);
    }
  } else {
    if (G.detected_file_type == Status::SRC_TYPE::TYPE_ZIP) {
      json_result = payload_list_partitions_remote_zip(
          G.url_input, G.user_agent, nullptr, nullptr);
    } else {
      json_result = payload_list_partitions_remote_bin(
          G.url_input, G.user_agent, nullptr, nullptr);
    }
  }

  if (json_result) {
    if (!read_json(json_result, G)) {
      G.set_error("Failed to parse partition information");
    }
    payload_free_string(json_result);
  } else {
    const char* err = payload_get_last_error();
    G.set_error(err ? err : "Failed to load partitions");
  }

  G.loading_partitions.store(false);
}

void top_box() {
  ImGui::BeginChild("TopPanel", ImVec2(0, 200), true,
                    ImGuiWindowFlags_NoScrollbar);

  ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(8, 6));

  ImGui::Text("Source Type:");
  ImGui::SameLine(120);
  if (ImGui::RadioButton("Local File##mode1",
                         G.input_mode == Status::Source::SRC_FILE)) {
    G.input_mode = Status::Source::SRC_FILE;
    G.clear_partitions();
  }
  ImGui::SameLine();
  if (ImGui::RadioButton("Remote URL##mode2",
                         G.input_mode == Status::Source::SRC_URL)) {
    G.input_mode = Status::Source::SRC_URL;
    G.clear_partitions();
  }

  ImGui::Spacing();
  ImGui::Separator();
  ImGui::Spacing();

  if (G.input_mode == Status::Source::SRC_FILE) {
    ImGui::Text("File Path:");
    ImGui::SameLine(120);
    ImGui::SetNextItemWidth(-120);
    ImGui::InputText("##filepath", G.file_path, sizeof(G.file_path));

    ImGui::SameLine();
    if (ImGui::Button("Browse...##filebrowse", ImVec2(110, 0))) {
      if (chooser(G.file_path, sizeof(G.file_path))) {
        G.detected_file_type = G.detect_file_type(G.file_path);
        G.clear_partitions();
      }
    }

    if (G.detected_file_type != Status::SRC_TYPE::TYPE_NONE) {
      ImGui::SameLine();
      ImGui::TextColored(
          ImVec4(0.4f, 0.8f, 0.4f, 1.0f), "[%s]",
          G.detected_file_type == Status::SRC_TYPE::TYPE_ZIP ? "ZIP" : "BIN");
    }
  } else {
    ImGui::Text("URL:");
    ImGui::SameLine(120);
    ImGui::SetNextItemWidth(-10);
    if (ImGui::InputText("##urlfield", G.url_input, sizeof(G.url_input))) {
      G.detected_file_type = G.detect_file_type(G.url_input);
      G.clear_partitions();
    }

    ImGui::Text("User Agent:");
    ImGui::SameLine(120);
    ImGui::SetNextItemWidth(-10);
    ImGui::InputText("##useragentfield", G.user_agent, sizeof(G.user_agent));
  }

  ImGui::Spacing();

  ImGui::Text("Output Dir:");
  ImGui::SameLine(120);
  ImGui::SetNextItemWidth(-120);
  ImGui::InputText("##outputdirfield", G.output_dir, sizeof(G.output_dir));

  ImGui::SameLine();
  if (ImGui::Button("Browse...##dirbrowse", ImVec2(110, 0))) {
    out_chooser(G.output_dir, sizeof(G.output_dir));
  }

  ImGui::Spacing();
  ImGui::Separator();
  ImGui::Spacing();

  const char* source =
      G.input_mode == Status::Source::SRC_FILE ? G.file_path : G.url_input;
  bool can_load = strlen(source) > 0 && strlen(G.output_dir) > 0;

  bool is_loading = G.loading_partitions.load();

  if (!can_load || is_loading) {
    ImGui::PushStyleVar(ImGuiStyleVar_Alpha, 0.5f);
  }

  ImGui::SetCursorPosX((ImGui::GetWindowWidth() - 150) * 0.5f);

  if (is_loading) {
    ImGui::InvisibleButton("##loading_area", ImVec2(150, 35));
    ImVec2 rect_min = ImGui::GetItemRectMin();
    ImVec2 rect_max = ImGui::GetItemRectMax();
    ImVec2 center((rect_min.x + rect_max.x) * 0.5f,
                  (rect_min.y + rect_max.y) * 0.5f + 6.0f);

    ImDrawList* dl = ImGui::GetWindowDrawList();
    static float spinner_angle = 0.0f;
    spinner_angle += ImGui::GetIO().DeltaTime * 6.0f;
    const float TWO_PI = 6.28318530718f;
    if (spinner_angle > TWO_PI) spinner_angle -= TWO_PI;

    const int SEGMENTS = 8;
    const float radius = 9.0f;
    for (int i = 0; i < SEGMENTS; ++i) {
      float a = spinner_angle + i * (TWO_PI / SEGMENTS);
      float alpha = (float)(i + 1) / (float)SEGMENTS;
      ImVec2 p(center.x + cosf(a) * radius, center.y + sinf(a) * radius);
      dl->AddCircleFilled(p, 2.5f,
                          ImGui::GetColorU32(ImVec4(0.4f, 0.8f, 1.0f, alpha)));
    }
  } else if (ImGui::Button("Load Partitions##loadbtn", ImVec2(150, 35)) &&
             can_load) {
    if (G.loading_thread.joinable()) {
      G.loading_thread.join();
    }
    G.loading_thread = std::thread(load_it);
    G.loading_thread.detach();
  }

  if (!can_load || is_loading) {
    ImGui::PopStyleVar();
  }

  ImGui::PopStyleVar();
  ImGui::EndChild();
}

char* fetch_jsn() {
  if (G.input_mode == Status::Source::SRC_FILE) {
    if (G.detected_file_type == Status::SRC_TYPE::TYPE_ZIP)
      return payload_list_partitions_zip(G.file_path);
    return payload_list_partitions(G.file_path);
  } else {
    if (G.detected_file_type == Status::SRC_TYPE::TYPE_ZIP)
      return payload_list_partitions_remote_zip(G.url_input, G.user_agent,
                                                nullptr, nullptr);
    return payload_list_partitions_remote_bin(G.url_input, G.user_agent,
                                              nullptr, nullptr);
  }
}

void right_box() {
  ImGui::BeginChild("RightPanel", ImVec2(200, 0), true);

  ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(8, 6));

  ImGui::Text("Actions");
  ImGui::Separator();
  ImGui::Spacing();

  ImGui::Checkbox("Output Verification", &G.enable_verification);
  if (ImGui::IsItemHovered()) {
    ImGui::SetTooltip("Verify SHA-256 hash after extraction");
  }
  ImGui::Spacing();
  ImGui::Separator();
  ImGui::Spacing();

  bool has_partitions = false;
  bool any_selected = false;
  bool any_extracting = false;

  {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    has_partitions = !G.partitions.empty();
    for (auto& part : G.partitions) {
      if (part.selected.load()) any_selected = true;
      if (part.extracting.load()) any_extracting = true;
    }
  }

  if (!has_partitions) ImGui::BeginDisabled();
  if (ImGui::Button("Select All##selectall", ImVec2(-1, 30))) {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    for (auto& part : G.partitions) {
      if (!part.extracting.load()) part.selected.store(true);
    }
  }
  if (!has_partitions) ImGui::EndDisabled();

  if (!has_partitions) ImGui::BeginDisabled();
  if (ImGui::Button("Deselect All##deselectall", ImVec2(-1, 30))) {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    for (auto& part : G.partitions) {
      part.selected.store(false);
    }
  }
  if (!has_partitions) ImGui::EndDisabled();

  ImGui::Spacing();
  ImGui::Separator();
  ImGui::Spacing();

  if (!any_selected || any_extracting) ImGui::BeginDisabled();
  if (ImGui::Button("Extract Selected##extractselected", ImVec2(-1, 35))) {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    for (auto& part : G.partitions) {
      if (part.selected.load() && !part.extracting.load()) {
        start_extraction(&part);
      }
    }
  }
  if (!any_selected || any_extracting) ImGui::EndDisabled();

  if (!any_extracting) ImGui::BeginDisabled();
  if (ImGui::Button("Cancel All##cancelall", ImVec2(-1, 35))) {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    for (auto& part : G.partitions) {
      if (part.extracting.load()) {
        part.cancel_flag.store(true);
      }
    }
  }
  if (!any_extracting) ImGui::EndDisabled();

  ImGui::Spacing();
  ImGui::Separator();
  ImGui::Spacing();

  if (!G.partitions_loaded) ImGui::BeginDisabled();
  if (ImGui::Button("View Raw JSON##viewjson", ImVec2(-1, 30))) {
    char* json_result = fetch_jsn();
    if (!json_result) {
      G.set_error("Failed to retrieve JSON data");
    } else {
      char temp_path[MAX_PATH];
      char temp_file[MAX_PATH];

      GetTempPathA(MAX_PATH, temp_path);
      GetTempFileNameA(temp_path, "pjson", 0, temp_file);

      char* ext = strrchr(temp_file, '.');
      if (ext) strcpy(ext, ".json");

      FILE* f = fopen(temp_file, "w");
      if (!f) {
        G.set_error("Failed to create temporary JSON file");
      } else {
        fwrite(json_result, 1, strlen(json_result), f);
        fclose(f);
        ShellExecuteA(nullptr, "open", temp_file, nullptr, nullptr,
                      SW_SHOWNORMAL);
      }

      payload_free_string(json_result);
    }
  }

  if (!G.partitions_loaded) ImGui::EndDisabled();

  ImGui::Spacing();

  if (has_partitions) {
    ImGui::Separator();
    ImGui::Spacing();
    ImGui::Text("Statistics");
    ImGui::Separator();
    ImGui::Spacing();

    ImGui::Text("Partitions:");
    ImGui::TextColored(ImVec4(0.6f, 0.8f, 1.0f, 1.0f), "%llu",
                       G.total_partitions);

    ImGui::Text("Total Size:");
    ImGui::TextWrapped("%s", G.total_size_readable.c_str());

    ImGui::Text("Operations:");
    ImGui::TextColored(ImVec4(0.6f, 0.8f, 1.0f, 1.0f), "%llu",
                       G.total_operations);
  }

  if (!G.security_patch_level.empty()) {
    ImGui::Spacing();
    ImGui::Text("Security Patch:");
    ImGui::TextWrapped("%s", G.security_patch_level.c_str());
  }

  ImGui::PopStyleVar();
  ImGui::EndChild();
}

void table() {
  ImGui::BeginChild("PartitionTable", ImVec2(-210, 0), true);

  bool is_empty = false;
  {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    is_empty = G.partitions.empty();
  }

  if (is_empty) {
    ImVec2 size = ImGui::GetWindowSize();
    ImGui::SetCursorPos(ImVec2(size.x * 0.5f - 100, size.y * 0.5f - 20));
    ImGui::TextColored(ImVec4(0.6f, 0.6f, 0.6f, 1.0f), "No partitions loaded");
    ImGui::SetCursorPosX(size.x * 0.5f - 100);
    ImGui::TextColored(ImVec4(0.5f, 0.5f, 0.5f, 1.0f),
                       "Load a payload file to begin");
  } else {
    if (ImGui::BeginTable("Partitions", 7,
                          ImGuiTableFlags_Borders | ImGuiTableFlags_RowBg |
                              ImGuiTableFlags_ScrollY |
                              ImGuiTableFlags_Resizable |
                              ImGuiTableFlags_HighlightHoveredColumn)) {
      ImGui::TableSetupColumn("Select", ImGuiTableColumnFlags_WidthFixed, 50);
      ImGui::TableSetupColumn("Partition", ImGuiTableColumnFlags_WidthStretch);
      ImGui::TableSetupColumn("Size", ImGuiTableColumnFlags_WidthFixed, 90);
      ImGui::TableSetupColumn("Operations", ImGuiTableColumnFlags_WidthFixed,
                              85);
      ImGui::TableSetupColumn("Progress", ImGuiTableColumnFlags_WidthStretch);
      ImGui::TableSetupColumn("Verify", ImGuiTableColumnFlags_WidthFixed, 120);
      ImGui::TableSetupColumn("Actions", ImGuiTableColumnFlags_WidthFixed, 80);
      ImGui::TableSetupScrollFreeze(0, 1);
      ImGui::TableHeadersRow();

      size_t partition_count = 0;
      {
        std::lock_guard<std::mutex> lock(G.partitions_mutex);
        partition_count = G.partitions.size();
      }

      for (size_t i = 0; i < partition_count; i++) {
        std::string name, size_str, status, verify_status;
        uint64_t ops;
        bool selected, extracting, verifying;
        float progress, verify_progress;
        bool verified;

        {
          std::lock_guard<std::mutex> lock(G.partitions_mutex);
          auto& part = G.partitions[i];
          name = part.name;
          size_str = part.size_readable;
          ops = part.operations_count;
          selected = part.selected.load();
          extracting = part.extracting.load();
          verifying = part.verifying.load();
          progress = part.progress.load();
          verify_progress = part.verify_progress.load();
          status = part.get_status();
          verify_status = part.get_verify_status();
          verified = part.verification_passed.load();
        }

        ImGui::TableNextRow();
        ImGui::PushID((int)i);

        ImGui::TableNextColumn();
        if (!extracting) {
          bool sel = selected;
          if (ImGui::Checkbox("##select", &sel)) {
            std::lock_guard<std::mutex> lock(G.partitions_mutex);
            G.partitions[i].selected.store(sel);
          }
        } else {
          ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.4f, 0.8f, 0.4f, 1.0f));
          ImGui::Text("[*]");
          ImGui::PopStyleColor();
        }

        ImGui::TableNextColumn();
        ImGui::Text("%s", name.c_str());

        ImGui::TableNextColumn();
        ImGui::Text("%s", size_str.c_str());

        ImGui::TableNextColumn();
        ImGui::Text("%llu", ops);

        ImGui::TableNextColumn();
        if (extracting) {
          ImGui::ProgressBar(progress / 100.0f, ImVec2(-1, 0), "");
          ImGui::SameLine(0, 5);
          ImGui::Text("%.1f%%", progress);
          if (!status.empty()) {
            ImGui::TextColored(ImVec4(0.7f, 0.7f, 0.7f, 1.0f), "%s",
                               status.c_str());
            if (ImGui::IsItemHovered()) {
              ImGui::SetTooltip("%s", status.c_str());
            }
          }
        } else if (!status.empty()) {
          if (status.find("Completed") != std::string::npos) {
            ImGui::TextColored(ImVec4(0.4f, 0.8f, 0.4f, 1.0f), "%s",
                               status.c_str());
          } else if (status.find("Error") != std::string::npos) {
            ImGui::TextColored(ImVec4(0.9f, 0.3f, 0.3f, 1.0f), "%s",
                               status.c_str());
          } else if (status.find("Cancelled") != std::string::npos) {
            ImGui::TextColored(ImVec4(0.9f, 0.6f, 0.2f, 1.0f), "%s",
                               status.c_str());
          } else {
            ImGui::Text("%s", status.c_str());
          }

          if (ImGui::IsItemHovered()) {
            ImGui::SetTooltip("%s", status.c_str());
          }
        } else {
          ImGui::TextDisabled("Ready");
        }

        ImGui::TableNextColumn();
        if (verifying) {
          ImGui::ProgressBar(verify_progress / 100.0f, ImVec2(-1, 0), "");
          ImGui::SameLine(0, 5);
          ImGui::Text("%.0f%%", verify_progress);
          ImGui::TextColored(ImVec4(0.7f, 0.7f, 0.7f, 1.0f), "Verifying...");
        } else if (!verify_status.empty()) {
          if (verified) {
            ImGui::TextColored(ImVec4(0.4f, 0.9f, 0.4f, 1.0f), "%s",
                               verify_status.c_str());
          } else if (verify_status.find("FAILED") != std::string::npos) {
            ImGui::TextColored(ImVec4(0.9f, 0.2f, 0.2f, 1.0f), "%s",
                               verify_status.c_str());
          } else {
            ImGui::TextColored(ImVec4(0.7f, 0.7f, 0.7f, 1.0f), "%s",
                               verify_status.c_str());
          }
        } else {
          ImGui::TextDisabled("-");
        }

        ImGui::TableNextColumn();
        if (extracting) {
          if (ImGui::Button("Cancel##cancel", ImVec2(-1, 0))) {
            std::lock_guard<std::mutex> lock(G.partitions_mutex);
            G.partitions[i].cancel_flag.store(true);
          }
        } else {
          if (ImGui::Button("Extract##extract", ImVec2(-1, 0))) {
            std::lock_guard<std::mutex> lock(G.partitions_mutex);
            start_extraction(&G.partitions[i]);
          }
        }

        ImGui::PopID();
      }

      ImGui::EndTable();
    }
  }

  ImGui::EndChild();
}

void err_box() {
  if (G.show_error_popup) {
    ImGui::OpenPopup("Error");
    ImVec2 center = ImGui::GetMainViewport()->GetCenter();
    ImGui::SetNextWindowPos(center, ImGuiCond_Appearing, ImVec2(0.5f, 0.5f));
    ImGui::SetNextWindowSize(ImVec2(420, 0), ImGuiCond_Appearing);

    if (ImGui::BeginPopupModal("Error", nullptr)) {
      ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.9f, 0.3f, 0.3f, 1.0f));
      ImGui::Text("Error!");
      ImGui::PopStyleColor();
      ImGui::Separator();
      ImGui::Spacing();

      ImGui::TextWrapped("%s", G.error_message.c_str());
      ImGui::Spacing();
      ImGui::Separator();
      ImGui::Spacing();

      ImGui::SetCursorPosX((ImGui::GetWindowWidth() - 120) * 0.5f);
      if (ImGui::Button("OK", ImVec2(120, 0))) {
        G.show_error_popup = false;
        ImGui::CloseCurrentPopup();
      }
      ImGui::EndPopup();
    }
  }
}

void draw() {
  ImGui::SetNextWindowPos(ImVec2(0, 0));
  ImGui::SetNextWindowSize(ImGui::GetIO().DisplaySize);
  ImGui::Begin("Payload Dumper", nullptr,
               ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                   ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoTitleBar |
                   ImGuiWindowFlags_NoBringToFrontOnFocus);

  top_box();

  ImGui::Spacing();

  ImGui::BeginGroup();
  table();
  ImGui::EndGroup();

  ImGui::SameLine();

  right_box();

  ImGui::End();

  err_box();
}

void begin() { payload_init(); }

void quit() {
  G.shutdown_requested.store(true);

  if (G.loading_thread.joinable()) {
    G.loading_thread.join();
  }

  {
    std::lock_guard<std::mutex> lock(G.partitions_mutex);
    for (auto& part : G.partitions) {
      part.cancel_flag.store(true);
    }
  }

  for (auto& t : G.extraction_threads) {
    if (t.joinable()) {
      t.join();
    }
  }

  G.extraction_threads.clear();

  payload_cleanup();
}