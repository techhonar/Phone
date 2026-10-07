//! JNI Bridge Bindings for Android Kotlin
//! Exports native symbols mapping to com.example.rust.RustPhoneBridge

use jni::objects::{JClass, JString};
use jni::sys::{jbyteArray, jint, jstring};
use jni::JNIEnv;

use crate::dtmf;
use crate::t9;
use crate::contacts;

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustPhoneBridge_nativeGetEngineVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let version = "Rust PhoneEngine v1.0.0 (Samsung One UI Edition)";
    let output = env.new_string(version).expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustPhoneBridge_nativeT9Search(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
    contacts_json: JString,
) -> jstring {
    let query_str: String = match env.get_string(&query) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let contacts_str: String = match env.get_string(&contacts_json) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    // Parse contacts from JSON: [ [id, name, phone], ... ]
    let parsed: Result<Vec<(String, String, String)>, _> = serde_json::from_str(&contacts_str);
    let contacts_list = parsed.unwrap_or_default();

    let results = t9::search_contacts(&query_str, &contacts_list);
    let json_output = serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string());

    let output = env.new_string(json_output).expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustPhoneBridge_nativeGenerateDtmfPcm(
    env: JNIEnv,
    _class: JClass,
    key_char: jint,
    sample_rate: jint,
    duration_ms: jint,
) -> jbyteArray {
    let c = (key_char as u8) as char;
    let samples = dtmf::generate_dtmf_pcm16(c, sample_rate as u32, duration_ms as u32);

    // Convert Vec<i16> to Vec<u8> (little endian)
    let mut bytes = Vec::with_capacity(samples.len() * 2);
    for sample in samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }

    let byte_array = match env.byte_array_from_slice(&bytes) {
        Ok(arr) => arr,
        Err(_) => match env.new_byte_array(0) {
            Ok(arr) => arr,
            Err(_) => return std::ptr::null_mut(),
        },
    };

    byte_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustPhoneBridge_nativeFormatPhoneNumber(
    mut env: JNIEnv,
    _class: JClass,
    raw_number: JString,
) -> jstring {
    let raw_str: String = match env.get_string(&raw_number) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let formatted = contacts::format_phone_number(&raw_str);
    let output = env.new_string(formatted).expect("Couldn't create java string!");
    output.into_raw()
}
