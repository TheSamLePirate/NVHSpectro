import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Collect activeFilters state in MainScreen
code = code.replace(
    'val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()',
    'val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()\n    val activeFilters by viewModel.activeFilters.collectAsState()'
)

# 2. Pass them to SettingsDialog
old_settings = '''            com.example.nvhspectro.ui.SettingsDialog(
                onDismiss = { showSettingsDialog = false },'''
new_settings = '''            com.example.nvhspectro.ui.SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                activeFilters = activeFilters,
                onAddFilter = { filter -> viewModel.addAudioFilter(filter) },
                onRemoveFilter = { filterId -> viewModel.removeAudioFilter(filterId) },'''

code = code.replace(old_settings, new_settings)

# 3. Pass them to VideoPlayerView
old_video = '''                            VideoPlayerView(
                                videoUri = videoUri,'''
new_video = '''                            VideoPlayerView(
                                videoUri = videoUri,
                                activeFilters = activeFilters,'''
code = code.replace(old_video, new_video)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated MainScreen.kt")
