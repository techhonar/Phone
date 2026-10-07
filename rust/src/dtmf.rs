//! Dual-Tone Multi-Frequency (DTMF) Signaling Generator
//! Implements ITU-T Recommendation Q.23 standard for telephone dial tone generation.

use std::f32::consts::PI;

/// DTMF Frequency table (Hz)
pub struct DtmfPair {
    pub low: f32,
    pub high: f32,
}

/// Returns the standard DTMF low and high frequencies for a dialpad key.
pub fn get_dtmf_frequencies(key: char) -> Option<DtmfPair> {
    match key.to_ascii_uppercase() {
        '1' => Some(DtmfPair { low: 697.0, high: 1209.0 }),
        '2' => Some(DtmfPair { low: 697.0, high: 1336.0 }),
        '3' => Some(DtmfPair { low: 697.0, high: 1477.0 }),
        'A' => Some(DtmfPair { low: 697.0, high: 1633.0 }),

        '4' => Some(DtmfPair { low: 770.0, high: 1209.0 }),
        '5' => Some(DtmfPair { low: 770.0, high: 1336.0 }),
        '6' => Some(DtmfPair { low: 770.0, high: 1477.0 }),
        'B' => Some(DtmfPair { low: 770.0, high: 1633.0 }),

        '7' => Some(DtmfPair { low: 852.0, high: 1209.0 }),
        '8' => Some(DtmfPair { low: 852.0, high: 1336.0 }),
        '9' => Some(DtmfPair { low: 852.0, high: 1477.0 }),
        'C' => Some(DtmfPair { low: 852.0, high: 1633.0 }),

        '*' => Some(DtmfPair { low: 941.0, high: 1209.0 }),
        '0' => Some(DtmfPair { low: 941.0, high: 1336.0 }),
        '#' => Some(DtmfPair { low: 941.0, high: 1477.0 }),
        'D' => Some(DtmfPair { low: 941.0, high: 1633.0 }),

        _ => None,
    }
}

/// Generates 16-bit mono PCM audio samples for a DTMF tone.
/// sample_rate: usually 8000 Hz or 44100 Hz.
/// duration_ms: duration in milliseconds (e.g. 120ms).
pub fn generate_dtmf_pcm16(
    key: char,
    sample_rate: u32,
    duration_ms: u32,
) -> Vec<i16> {
    let freqs = match get_dtmf_frequencies(key) {
        Some(f) => f,
        None => return Vec::new(),
    };

    let total_samples = (sample_rate as f32 * (duration_ms as f32 / 1000.0)) as usize;
    let mut samples = Vec::with_capacity(total_samples);

    let fade_samples = (sample_rate as f32 * 0.005) as usize; // 5ms fade envelope

    for i in 0..total_samples {
        let t = i as f32 / sample_rate as f32;
        let tone1 = (2.0 * PI * freqs.low * t).sin();
        let tone2 = (2.0 * PI * freqs.high * t).sin();
        let combined = 0.5 * (tone1 + tone2);

        // Linear envelope to avoid audio clicks
        let envelope = if i < fade_samples {
            i as f32 / fade_samples as f32
        } else if i > total_samples.saturating_sub(fade_samples) {
            (total_samples - i) as f32 / fade_samples as f32
        } else {
            1.0
        };

        let sample_val = (combined * envelope * 24000.0) as i16;
        samples.push(sample_val);
    }

    samples
}
