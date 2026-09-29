#!/bin/bash

# LLmQuantBench Release APK Build Script
# This script automates the process of building a signed release APK for Android

set -e  # Exit on any error

# Configuration
PROJECT_NAME="LLmQuantBench"
KEYSTORE_FILE="android/app/release.keystore"
KEYSTORE_ALIAS="llmquantbench_release"
BUILD_TYPE="release"
APK_OUTPUT_DIR="android/app/build/outputs/apk/release"
APK_NAME="app-release.apk"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    # Check for Node.js
    if ! command_exists node; then
        print_error "Node.js is not installed. Please install Node.js and try again."
        exit 1
    fi

    # Check for npm
    if ! command_exists npm; then
        print_error "npm is not installed. Please install npm and try again."
        exit 1
    fi

    # Check for Java
    if ! command_exists java; then
        print_error "Java is not installed. Please install Java JDK and try again."
        exit 1
    fi

    # Check for keytool
    if ! command_exists keytool; then
        print_error "keytool is not found. Please ensure Java JDK is properly installed."
        exit 1
    fi

    print_success "Prerequisites check passed."
}

# Function to create keystore if it doesn't exist
create_keystore() {
    if [ -f "$KEYSTORE_FILE" ]; then
        print_warning "Keystore already exists at $KEYSTORE_FILE"
        read -p "Do you want to overwrite it? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Using existing keystore."
            return
        fi
    fi

    print_info "Creating keystore..."

    # Generate keystore
    keytool -genkey -v -keystore "$KEYSTORE_FILE" \
        -alias "$KEYSTORE_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" \
        -storepass android \
        -keypass android

    print_success "Keystore created successfully."
}

# Function to update build.gradle with keystore configuration
update_build_gradle() {
    print_info "Updating build.gradle configuration..."

    BUILD_GRADLE_FILE="android/app/build.gradle"

    # Check if release signing config already exists
    if grep -q "signingConfigs.*release" "$BUILD_GRADLE_FILE"; then
        print_warning "Release signing configuration already exists in build.gradle"
        return
    fi

    # Add release signing configuration
    sed -i '/signingConfigs {/a\
        release {\
            storeFile file("release.keystore")\
            storePassword "android"\
            keyAlias "'"$KEYSTORE_ALIAS"'"\
            keyPassword "android"\
        }' "$BUILD_GRADLE_FILE"

    # Update release build type to use release signing config
    sed -i 's/signingConfig signingConfigs\.debug/signingConfig signingConfigs.release/g' "$BUILD_GRADLE_FILE"

    print_success "build.gradle updated with release signing configuration."
}

# Function to create assets directory if it doesn't exist
create_assets_dir() {
    ASSETS_DIR="android/app/src/main/assets"

    if [ ! -d "$ASSETS_DIR" ]; then
        print_info "Creating assets directory..."
        mkdir -p "$ASSETS_DIR"
        print_success "Assets directory created."
    else
        print_info "Assets directory already exists."
    fi
}

# Function to bundle React Native assets
bundle_assets() {
    print_info "Bundling React Native assets..."

    # Clean previous bundle
    rm -rf "$ASSETS_DIR/index.android.bundle"

    # Bundle the assets
    npx react-native bundle \
        --platform android \
        --dev false \
        --entry-file index.js \
        --bundle-output "$ASSETS_DIR/index.android.bundle" \
        --assets-dest android/app/src/main/res/

    print_success "Assets bundled successfully."
}

# Function to clean previous builds
clean_build() {
    print_info "Cleaning previous builds..."

    cd android
    ./gradlew clean
    cd ..

    print_success "Clean completed."
}

# Function to build release APK
build_release_apk() {
    print_info "Building release APK..."

    cd android

    # Build the release APK
    ./gradlew assembleRelease

    cd ..

    print_success "Release APK built successfully."
}

# Function to verify APK
verify_apk() {
    APK_PATH="$APK_OUTPUT_DIR/$APK_NAME"

    if [ -f "$APK_PATH" ]; then
        print_success "Release APK created successfully!"
        print_info "APK location: $APK_PATH"

        # Get APK info
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        print_info "APK size: $APK_SIZE"

        # Verify signing
        print_info "Verifying APK signature..."
        jarsigner -verify -verbose "$APK_PATH" > /dev/null 2>&1 && \
            print_success "APK signature verification passed." || \
            print_warning "APK signature verification failed."

    else
        print_error "APK not found at expected location: $APK_PATH"
        exit 1
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help          Show this help message"
    echo "  -c, --clean         Clean previous builds before building"
    echo "  -k, --keystore      Recreate keystore even if it exists"
    echo "  -s, --skip-bundle   Skip React Native bundling step"
    echo "  -v, --verbose       Enable verbose output"
    echo ""
    echo "This script will:"
    echo "  1. Check prerequisites"
    echo "  2. Create/update keystore"
    echo "  3. Update build.gradle configuration"
    echo "  4. Create assets directory"
    echo "  5. Bundle React Native assets"
    echo "  6. Build signed release APK"
    echo "  7. Verify the built APK"
}

# Parse command line arguments
CLEAN_BUILD=false
FORCE_KEYSTORE=false
SKIP_BUNDLE=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -c|--clean)
            CLEAN_BUILD=true
            shift
            ;;
        -k|--keystore)
            FORCE_KEYSTORE=true
            shift
            ;;
        -s|--skip-bundle)
            SKIP_BUNDLE=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Enable verbose mode if requested
if [ "$VERBOSE" = true ]; then
    set -x
fi

# Main execution
print_info "Starting $PROJECT_NAME Release APK Build Process"
print_info "=============================================="

# Check prerequisites
check_prerequisites

# Clean build if requested
if [ "$CLEAN_BUILD" = true ]; then
    clean_build
fi

# Create keystore
if [ "$FORCE_KEYSTORE" = true ] || [ ! -f "$KEYSTORE_FILE" ]; then
    create_keystore
else
    print_info "Using existing keystore."
fi

# Update build.gradle
update_build_gradle

# Create assets directory
create_assets_dir

# Bundle assets unless skipped
if [ "$SKIP_BUNDLE" = false ]; then
    bundle_assets
else
    print_info "Skipping asset bundling as requested."
fi

# Build release APK
build_release_apk

# Verify APK
verify_apk

print_success "=============================================="
print_success "$PROJECT_NAME Release APK Build Process Completed!"
print_success "You can find your APK at: $APK_OUTPUT_DIR/$APK_NAME"
print_info "Ready to upload to Google Play Store or distribute."







