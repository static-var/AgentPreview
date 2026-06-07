#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK_DIR="$ROOT_DIR/build/agentpreview-compat"
MODE="quick"
KEEP_PROJECTS="false"
RUN_REAL="false"
ONLY_CASE=""
LIST_ONLY="false"
USE_BUILD_BRIEF="${AGENTPREVIEW_COMPAT_USE_BUILD_BRIEF:-auto}"
ANDROID_HOME_VALUE="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

QUICK_CASES=(
  "android-current|android-app|9.2.1|8.13.2|2.3.21|2026.05.01||1.11.2|36|36|23"
  "cmp-current|cmp-app|9.2.1|8.13.2|2.3.21||1.11.0|1.11.2|36|36|23"
  "kmp-current|kmp-library|9.2.1|8.13.2|2.3.21||1.11.0|1.11.2|36|36|23"
)

EXTENDED_CASES=(
  "android-agp-8-7-kotlin-2-0|android-app|8.10.2|8.7.3|2.0.21|2024.10.00||1.7.8|35|35|23"
  "android-agp-8-13-target-35|android-app|9.2.1|8.13.2|2.3.21|2026.05.01||1.11.2|36|35|23"
  "cmp-current-sdk-35|cmp-app|9.2.1|8.13.2|2.3.21||1.11.0|1.11.2|35|35|23"
)

usage() {
  cat <<'EOF'
Usage: scripts/agentpreview-compatibility-matrix.sh [options]

Generates disposable Android/CMP/KMP projects and runs AgentPreview against each
configured Gradle, AGP, Kotlin, Compose, CMP, and SDK version cell.

Options:
  --quick              Run the current-version smoke matrix. Default.
  --extended           Add older/probing cells that may expose upstream breaks.
  --case NAME          Run one case by name.
  --real               Run real Robolectric capture after fake capture passes.
  --work-dir DIR       Write generated projects and logs under DIR.
  --keep               Keep generated projects after completion.
  --list               Print selected matrix cells without running them.
  -h, --help           Show this help.

Environment:
  ANDROID_HOME         Android SDK path. Defaults to ~/Library/Android/sdk.
  AGENTPREVIEW_COMPAT_USE_BUILD_BRIEF=auto|true|false
                       Use build-brief for Gradle invocations when available.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick)
      MODE="quick"
      ;;
    --extended)
      MODE="extended"
      ;;
    --case)
      shift
      ONLY_CASE="${1:-}"
      ;;
    --real)
      RUN_REAL="true"
      ;;
    --work-dir)
      shift
      WORK_DIR="${1:-}"
      ;;
    --keep)
      KEEP_PROJECTS="true"
      ;;
    --list)
      LIST_ONLY="true"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if [[ -z "$ANDROID_HOME_VALUE" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required." >&2
  exit 2
fi

GRADLE_PREFIX=()
if [[ "$USE_BUILD_BRIEF" == "true" ]] || { [[ "$USE_BUILD_BRIEF" == "auto" ]] && command -v build-brief >/dev/null 2>&1; }; then
  GRADLE_PREFIX=(build-brief)
fi

all_cases() {
  printf '%s\n' "${QUICK_CASES[@]}"
  if [[ "$MODE" == "extended" ]]; then
    printf '%s\n' "${EXTENDED_CASES[@]}"
  fi
}

selected_cases() {
  local found="false"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    local name="${line%%|*}"
    if [[ -n "$ONLY_CASE" && "$name" != "$ONLY_CASE" ]]; then
      continue
    fi
    found="true"
    printf '%s\n' "$line"
  done < <(all_cases)
  if [[ -n "$ONLY_CASE" && "$found" != "true" ]]; then
    echo "No compatibility case named '$ONLY_CASE'." >&2
    exit 2
  fi
}

safe_name() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9_.-' '-'
}

kotlin_string() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

case_module_path() {
  case "$1" in
    android-app) printf ':app' ;;
    cmp-app) printf ':composeApp' ;;
    kmp-library) printf ':designSystem' ;;
    *) echo "Unknown project kind: $1" >&2; exit 2 ;;
  esac
}

run_logged() {
  local label="$1"
  local log_file="$2"
  shift 2
  mkdir -p "$(dirname "$log_file")"
  echo "[$label] $*"
  ANDROID_HOME="$ANDROID_HOME_VALUE" ANDROID_SDK_ROOT="$ANDROID_HOME_VALUE" "$@" >"$log_file" 2>&1
}

write_common_settings() {
  local project_dir="$1"
  local project_name="$2"
  local include_line="$3"
  local escaped_root
  escaped_root="$(kotlin_string "$ROOT_DIR")"
  cat >"$project_dir/settings.gradle.kts" <<EOF
pluginManagement {
    includeBuild("$escaped_root")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "$project_name"
$include_line
EOF
}

write_common_gradle_properties() {
  local project_dir="$1"
  cat >"$project_dir/gradle.properties" <<'EOF'
android.useAndroidX=true
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
EOF
}

write_wrapper_bootstrap() {
  local project_dir="$1"
  mkdir -p "$project_dir"
  cat >"$project_dir/settings.gradle.kts" <<'EOF'
rootProject.name = "AgentPreviewCompatWrapperBootstrap"
EOF
}

write_android_app() {
  local project_dir="$1"
  local agp="$2"
  local kotlin="$3"
  local compose_bom="$4"
  local compile_sdk="$5"
  local target_sdk="$6"
  local min_sdk="$7"

  write_common_settings "$project_dir" "AgentPreviewCompatAndroidApp" 'include(":app")'
  write_common_gradle_properties "$project_dir"
  mkdir -p "$project_dir/app/src/main/java/dev/staticvar/agentpreview/compat"
  mkdir -p "$project_dir/app/src/main/res/values"

  cat >"$project_dir/build.gradle.kts" <<EOF
plugins {
    id("com.android.application") version "$agp" apply false
    id("org.jetbrains.kotlin.android") version "$kotlin" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "$kotlin" apply false
}
EOF

  cat >"$project_dir/app/build.gradle.kts" <<EOF
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "dev.staticvar.agentpreview.compat"
    compileSdk = $compile_sdk

    defaultConfig {
        applicationId = "dev.staticvar.agentpreview.compat"
        minSdk = $min_sdk
        targetSdk = $target_sdk
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:$compose_bom"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
EOF

  cat >"$project_dir/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:label="AgentPreview Compat"
        android:supportsRtl="true"
        android:theme="@style/AppTheme" />
</manifest>
EOF

  cat >"$project_dir/app/src/main/res/values/styles.xml" <<'EOF'
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar" />
</resources>
EOF

  cat >"$project_dir/app/src/main/java/dev/staticvar/agentpreview/compat/CompatPreview.kt" <<'EOF'
package dev.staticvar.agentpreview.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Compat Preview", group = "Compatibility", widthDp = 240, heightDp = 120, showBackground = true)
@Composable
fun CompatPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp).testTag("compat_preview"),
        ) {
            Text("AgentPreview")
            Text("Android app matrix")
        }
    }
}
EOF
}

write_cmp_app() {
  local project_dir="$1"
  local agp="$2"
  local kotlin="$3"
  local cmp="$4"
  local compose_ui="$5"
  local compile_sdk="$6"
  local target_sdk="$7"
  local min_sdk="$8"

  write_common_settings "$project_dir" "AgentPreviewCompatCmpApp" 'include(":composeApp")'
  write_common_gradle_properties "$project_dir"
  mkdir -p "$project_dir/composeApp/src/commonMain/kotlin/dev/staticvar/agentpreview/compat"
  mkdir -p "$project_dir/composeApp/src/androidMain"

  cat >"$project_dir/build.gradle.kts" <<EOF
plugins {
    id("com.android.application") version "$agp" apply false
    id("org.jetbrains.kotlin.multiplatform") version "$kotlin" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "$kotlin" apply false
    id("org.jetbrains.compose") version "$cmp" apply false
}
EOF

  cat >"$project_dir/composeApp/build.gradle.kts" <<EOF
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.12.0")
            implementation("androidx.compose.ui:ui-tooling-preview:$compose_ui")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}

android {
    namespace = "dev.staticvar.agentpreview.compat.cmp"
    compileSdk = $compile_sdk

    defaultConfig {
        applicationId = "dev.staticvar.agentpreview.compat.cmp"
        minSdk = $min_sdk
        targetSdk = $target_sdk
        versionCode = 1
        versionName = "1.0"
    }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}
EOF

  cat >"$project_dir/composeApp/src/androidMain/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application />
</manifest>
EOF

  cat >"$project_dir/composeApp/src/commonMain/kotlin/dev/staticvar/agentpreview/compat/CompatPreview.kt" <<'EOF'
package dev.staticvar.agentpreview.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Compat Preview", group = "Compatibility", widthDp = 240, heightDp = 120, showBackground = true)
@Composable
fun CompatPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp).testTag("compat_preview"),
        ) {
            Text("AgentPreview")
            Text("CMP app matrix")
        }
    }
}
EOF
}

write_kmp_library() {
  local project_dir="$1"
  local agp="$2"
  local kotlin="$3"
  local cmp="$4"
  local compose_ui="$5"
  local compile_sdk="$6"
  local min_sdk="$7"

  write_common_settings "$project_dir" "AgentPreviewCompatKmpLibrary" 'include(":designSystem")'
  write_common_gradle_properties "$project_dir"
  mkdir -p "$project_dir/designSystem/src/commonMain/kotlin/dev/staticvar/agentpreview/compat"

  cat >"$project_dir/build.gradle.kts" <<EOF
plugins {
    id("com.android.kotlin.multiplatform.library") version "$agp" apply false
    id("org.jetbrains.kotlin.multiplatform") version "$kotlin" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "$kotlin" apply false
    id("org.jetbrains.compose") version "$cmp" apply false
}
EOF

  cat >"$project_dir/designSystem/build.gradle.kts" <<EOF
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    androidLibrary {
        namespace = "dev.staticvar.agentpreview.compat.kmp"
        compileSdk = $compile_sdk
        minSdk = $min_sdk
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("androidx.compose.ui:ui-tooling-preview:$compose_ui")
        }
    }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}
EOF

  cat >"$project_dir/designSystem/src/commonMain/kotlin/dev/staticvar/agentpreview/compat/CompatPreview.kt" <<'EOF'
package dev.staticvar.agentpreview.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Compat Preview", group = "Compatibility", widthDp = 240, heightDp = 120, showBackground = true)
@Composable
fun CompatPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp).testTag("compat_preview"),
        ) {
            Text("AgentPreview")
            Text("KMP library matrix")
        }
    }
}
EOF
}

write_project() {
  local project_dir="$1"
  local kind="$2"
  local agp="$3"
  local kotlin="$4"
  local compose_bom="$5"
  local cmp="$6"
  local compose_ui="$7"
  local compile_sdk="$8"
  local target_sdk="$9"
  local min_sdk="${10}"

  case "$kind" in
    android-app)
      write_android_app "$project_dir" "$agp" "$kotlin" "$compose_bom" "$compile_sdk" "$target_sdk" "$min_sdk"
      ;;
    cmp-app)
      write_cmp_app "$project_dir" "$agp" "$kotlin" "$cmp" "$compose_ui" "$compile_sdk" "$target_sdk" "$min_sdk"
      ;;
    kmp-library)
      write_kmp_library "$project_dir" "$agp" "$kotlin" "$cmp" "$compose_ui" "$compile_sdk" "$min_sdk"
      ;;
    *)
      echo "Unknown project kind: $kind" >&2
      exit 2
      ;;
  esac
}

print_case_table_header() {
  printf '%-28s %-12s %-8s %-8s %-8s %-12s %-8s %-8s %-6s %-6s %-6s\n' \
    "name" "kind" "gradle" "agp" "kotlin" "composeBom" "cmp" "compose" "compile" "target" "min"
}

print_case_line() {
  local line="$1"
  IFS='|' read -r name kind gradle agp kotlin compose_bom cmp compose_ui compile_sdk target_sdk min_sdk <<<"$line"
  printf '%-28s %-12s %-8s %-8s %-8s %-12s %-8s %-8s %-6s %-6s %-6s\n' \
    "$name" "$kind" "$gradle" "$agp" "$kotlin" "${compose_bom:--}" "${cmp:--}" "$compose_ui" "$compile_sdk" "$target_sdk" "$min_sdk"
}

if [[ "$LIST_ONLY" == "true" ]]; then
  print_case_table_header
  while IFS= read -r selected; do
    print_case_line "$selected"
  done < <(selected_cases)
  exit 0
fi

mkdir -p "$WORK_DIR/logs"
REPORT="$WORK_DIR/report.md"
SUMMARY="$WORK_DIR/summary.tsv"
printf '# AgentPreview Compatibility Matrix\n\n' >"$REPORT"
printf 'Generated from `%s`.\n\n' "$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || printf unknown)" >>"$REPORT"
printf '| Case | Kind | Gradle | AGP | Kotlin | Compose BOM | CMP | Compose UI | SDK | Fake | Real | Logs |\n' >>"$REPORT"
printf '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n' >>"$REPORT"
printf 'case\tkind\tgradle\tagp\tkotlin\tcompose_bom\tcmp\tcompose_ui\tcompile_sdk\ttarget_sdk\tmin_sdk\tfake\treal\tproject_dir\n' >"$SUMMARY"

overall_status=0
while IFS= read -r selected; do
  IFS='|' read -r name kind gradle agp kotlin compose_bom cmp compose_ui compile_sdk target_sdk min_sdk <<<"$selected"
  safe="$(safe_name "$name")"
  project_dir="$WORK_DIR/projects/$safe"
  log_dir="$WORK_DIR/logs/$safe"
  module_path="$(case_module_path "$kind")"
  fake_status="not-run"
  real_status="not-run"

  rm -rf "$project_dir" "$log_dir"
  mkdir -p "$project_dir" "$log_dir"

  echo "==> $name ($kind, Gradle $gradle, AGP $agp, Kotlin $kotlin)"
  write_wrapper_bootstrap "$project_dir"
  if run_logged "$name wrapper" "$log_dir/wrapper.log" "${GRADLE_PREFIX[@]}" "$ROOT_DIR/gradlew" -p "$project_dir" wrapper --gradle-version "$gradle" --distribution-type bin; then
    chmod +x "$project_dir/gradlew"
    write_project "$project_dir" "$kind" "$agp" "$kotlin" "$compose_bom" "$cmp" "$compose_ui" "$compile_sdk" "$target_sdk" "$min_sdk"
    if run_logged "$name fake" "$log_dir/fake.log" "${GRADLE_PREFIX[@]}" "$project_dir/gradlew" -p "$project_dir" "$module_path:listComposePreviews" "$module_path:captureComposePreviews" -PagentPreview.previewNameFilter=CompatPreview -PagentPreview.maxCaptures=1 -PagentPreview.fakeRenderer=true; then
      fake_status="pass"
      if [[ "$RUN_REAL" == "true" ]]; then
        if run_logged "$name real" "$log_dir/real.log" "${GRADLE_PREFIX[@]}" "$project_dir/gradlew" -p "$project_dir" "$module_path:captureComposePreviews" -PagentPreview.previewNameFilter=CompatPreview -PagentPreview.maxCaptures=1; then
          real_status="pass"
        else
          real_status="fail"
          overall_status=1
        fi
      fi
    else
      fake_status="fail"
      overall_status=1
    fi
  else
    fake_status="wrapper-fail"
    overall_status=1
  fi

  log_link="logs/$safe"
  sdk="$compile_sdk/$target_sdk/$min_sdk"
  printf '| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
    "$name" "$kind" "$gradle" "$agp" "$kotlin" "${compose_bom:--}" "${cmp:--}" "$compose_ui" "$sdk" "$fake_status" "$real_status" "$log_link" >>"$REPORT"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$name" "$kind" "$gradle" "$agp" "$kotlin" "$compose_bom" "$cmp" "$compose_ui" "$compile_sdk" "$target_sdk" "$min_sdk" "$fake_status" "$real_status" "$project_dir" >>"$SUMMARY"

  if [[ "$KEEP_PROJECTS" != "true" ]]; then
    rm -rf "$project_dir"
  fi
done < <(selected_cases)

echo
echo "Report: $REPORT"
echo "Summary: $SUMMARY"
exit "$overall_status"
