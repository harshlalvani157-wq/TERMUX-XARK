# xodosark-native

[English](README.md) | العربية

جزء من تطبيق XoDos-ark

## الدور وما تم تنفيذه

مكتبة Rust من نوع **`cdylib`** يتم تحميلها بواسطة تطبيق أندرويد باسم **`libxodos2.so`** (`System.loadLibrary("xodos2")`). تُستخدم نقاط الدخول JNI من `NativeBridge`.

**ما تم تنفيذه (مستوى عالٍ):** التعامل مع عمليات proot و PTY، تنزيل أنظمة جذر rootfs متعددة للتوزيعات، أنظمة جذر محلية للتوزيعات، استخراج مرحلي وتثبيت ذري في تخزين التطبيق، تخزين مؤقت لمخرجات PTY، ومساعدات أخرى تستدعيها طبقة Kotlin.

## المتطلبات الأساسية

- [Rust](https://www.rust-lang.org/) عبر `rustup`
- Android NDK (`ANDROID_NDK_HOME` أو تحت `$HOME/Android/Sdk/ndk/`)

```bash
rustup target add aarch64-linux-android