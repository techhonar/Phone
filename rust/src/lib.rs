//! Samsung Phone Engine (Rust Core)
//! Core native telephony library containing T9 predictive search, DTMF synthesis, and call management.

pub mod call_manager;
pub mod contacts;
pub mod dtmf;
pub mod jni;
pub mod t9;

pub use call_manager::{CallSession, CallState};
pub use contacts::ContactEntry;
pub use dtmf::generate_dtmf_pcm16;
pub use t9::{search_contacts, T9MatchResult};
