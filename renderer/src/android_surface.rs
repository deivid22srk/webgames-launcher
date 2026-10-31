//! Superfície wgpu a partir de um `ANativeWindow*` (obtido da Surface do Android).

use raw_window_handle::{
    AndroidDisplayHandle, AndroidNdkWindowHandle, RawDisplayHandle, RawWindowHandle,
};

/// Monta um [`wgpu::SurfaceTargetUnsafe`] a partir do ponteiro do ANativeWindow.
///
/// # Safety
/// `ptr` deve ser um `ANativeWindow*` válido (ref adquirida via
/// `ANativeWindow_fromSurface`) que permanece vivo até o drop da surface.
pub unsafe fn surface_target_from_ptr(
    ptr: *mut core::ffi::c_void,
) -> Result<wgpu::SurfaceTargetUnsafe, String> {
    if ptr.is_null() {
        return Err("ANativeWindow nulo".into());
    }
    let handle = AndroidNdkWindowHandle::new(std::ptr::NonNull::new_unchecked(ptr));
    Ok(wgpu::SurfaceTargetUnsafe::RawHandle {
        raw_display_handle: Some(RawDisplayHandle::Android(AndroidDisplayHandle::new())),
        raw_window_handle: RawWindowHandle::AndroidNdk(handle),
    })
}

/// Devolve a referência do ANativeWindow adquirida em `ANativeWindow_fromSurface`.
///
/// # Safety
/// `ptr` precisa ter vindo de `ANativeWindow_fromSurface` e não ser usado depois.
pub unsafe fn release_window(ptr: *mut core::ffi::c_void) {
    if !ptr.is_null() {
        unsafe extern "C" {
            fn ANativeWindow_release(window: *mut core::ffi::c_void);
        }
        ANativeWindow_release(ptr);
    }
}
