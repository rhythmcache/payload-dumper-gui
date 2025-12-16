project(
  'payload-dumper-gui',
  'cpp',
  version: '1.0.0',
  default_options: [
    'cpp_std=c++17',
    'warning_level=2',
    'b_vscrt=mt',
    'buildtype=release'
  ]
)

if host_machine.system() != 'windows'
  error('This project only supports Windows')
endif

cpp = meson.get_compiler('cpp')
cpp_id = cpp.get_id()

fs = import('fs')
windows = import('windows')

git_cmd = find_program('git', required: false)

digest_header = meson.project_source_root() / 'external/digest/sha256.h'
json_header   = meson.project_source_root() / 'external/json.h/json.h'

if not fs.exists(digest_header) or not fs.exists(json_header)
  if git_cmd.found()
    message('Submodules not initialized. Initializing...')
    run_command(
      git_cmd,
      'submodule', 'update', '--init', '--recursive', '--depth', '1',
      check: true,
      capture: true
    )
  else
    error('Git submodules not initialized and git not found.')
  endif
endif

if not fs.exists(digest_header)
  error('SHA256 header missing: ' + digest_header)
endif

if not fs.exists(json_header)
  error('JSON header missing: ' + json_header)
endif

message('Submodules initialized')

payload_inc_dir = get_option('payload_inc')
payload_lib_dir = get_option('payload_lib')

if payload_inc_dir == '' or payload_lib_dir == ''
  error('You must pass -Dpayload_inc=... and -Dpayload_lib=... to meson setup')
endif

payload_inc = include_directories(payload_inc_dir)

if cpp_id == 'msvc' or cpp_id == 'clang-cl'
  payload_dumper_dep = declare_dependency(
    include_directories: payload_inc,
    link_args: [
      '/LIBPATH:' + payload_lib_dir,
      'payload_dumper.lib'
    ]
  )
elif cpp_id == 'gcc' or cpp_id == 'clang'
  payload_dumper_dep = declare_dependency(
    include_directories: payload_inc,
    link_args: [
      '-L' + payload_lib_dir,
      '-lpayload_dumper'
    ]
  )
else
  error('Unsupported compiler for payload_dumper: ' + cpp_id)
endif

message('Using payload-dumper include: ' + payload_inc_dir)
message('Using payload-dumper libdir: ' + payload_lib_dir)

d3d11_dep    = cpp.find_library('d3d11')
dxgi_dep     = cpp.find_library('dxgi')
comdlg32_dep = cpp.find_library('comdlg32')
shell32_dep  = cpp.find_library('shell32')
ole32_dep    = cpp.find_library('ole32')
userenv_dep  = cpp.find_library('userenv')
bcrypt_dep   = cpp.find_library('bcrypt')
ntdll_dep    = cpp.find_library('ntdll')
dwmapi_dep   = cpp.find_library('dwmapi')

d3dcompiler_dep = cpp.find_library('d3dcompiler_47', required: false)
if not d3dcompiler_dep.found()
  d3dcompiler_dep = cpp.find_library('d3dcompiler')
endif

external_inc = include_directories(
  'external/digest',
  'external/json.h',
  'external/imgui',
  'external/imgui/backends'
)

imgui_src = files(
  'external/imgui/imgui.cpp',
  'external/imgui/imgui_draw.cpp',
  'external/imgui/imgui_tables.cpp',
  'external/imgui/imgui_widgets.cpp',
  'external/imgui/backends/imgui_impl_win32.cpp',
  'external/imgui/backends/imgui_impl_dx11.cpp'
)

rc_objs = windows.compile_resources(
  'src/app.rc'
)


sources = [
  'src/bootstrap.cpp',
  'src/window.cpp',
  'src/resource.h',
] + imgui_src + rc_objs

exe = executable(
  'payload-dumper-gui',
  sources,
  include_directories: external_inc,
  dependencies: [
    payload_dumper_dep,
    d3d11_dep,
    dxgi_dep,
    comdlg32_dep,
    shell32_dep,
    ole32_dep,
    userenv_dep,
    bcrypt_dep,
    ntdll_dep,
    dwmapi_dep,
    d3dcompiler_dep,
  ],
  win_subsystem: 'windows',
  install: true
)

message('''
        Build Configuration Summary
  Project:         payload-dumper-gui
  Build Type:      ''' + get_option('buildtype') + '''
  Install Prefix:  ''' + get_option('prefix') + '''
  payload_inc:     ''' + payload_inc_dir + '''
  payload_lib:     ''' + payload_lib_dir + '''
''')
