APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
#it is highly recommended that you compile the CKFFT sources with clang instead of gcc, as clang produces much more efficient NEON code from intrinsics.
NDK_TOOLCHAIN_VERSION := clang
APP_PLATFORM := android-19
