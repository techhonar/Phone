//! Telephony Call State Machine and Duration Tracking
//! Models standard telephony call lifecycle states with full feature controls.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum CallState {
    Idle,
    Dialing { number: String, contact_name: Option<String> },
    Ringing { number: String, contact_name: Option<String> },
    Active {
        number: String,
        contact_name: Option<String>,
        seconds: u64,
        is_muted: bool,
        is_speaker: bool,
        is_held: bool,
        is_recording: bool,
    },
    Ended {
        number: String,
        contact_name: Option<String>,
        total_seconds: u64,
        reason: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallSession {
    pub current_state: CallState,
}

impl CallSession {
    pub fn new() -> Self {
        Self {
            current_state: CallState::Idle,
        }
    }

    pub fn dial(&mut self, number: String, contact_name: Option<String>) {
        self.current_state = CallState::Dialing { number, contact_name };
    }

    pub fn answered(&mut self) {
        if let CallState::Dialing { ref number, ref contact_name }
        | CallState::Ringing { ref number, ref contact_name } = self.current_state {
            self.current_state = CallState::Active {
                number: number.clone(),
                contact_name: contact_name.clone(),
                seconds: 0,
                is_muted: false,
                is_speaker: false,
                is_held: false,
                is_recording: false,
            };
        }
    }

    pub fn tick_second(&mut self) {
        if let CallState::Active { ref mut seconds, is_held, .. } = self.current_state {
            if !is_held {
                *seconds += 1;
            }
        }
    }

    pub fn toggle_mute(&mut self) {
        if let CallState::Active { ref mut is_muted, .. } = self.current_state {
            *is_muted = !*is_muted;
        }
    }

    pub fn toggle_speaker(&mut self) {
        if let CallState::Active { ref mut is_speaker, .. } = self.current_state {
            *is_speaker = !*is_speaker;
        }
    }

    pub fn toggle_hold(&mut self) {
        if let CallState::Active { ref mut is_held, .. } = self.current_state {
            *is_held = !*is_held;
        }
    }

    pub fn toggle_recording(&mut self) {
        if let CallState::Active { ref mut is_recording, .. } = self.current_state {
            *is_recording = !*is_recording;
        }
    }

    pub fn end_call(&mut self, reason: &str) {
        match &self.current_state {
            CallState::Active { number, contact_name, seconds, .. } => {
                self.current_state = CallState::Ended {
                    number: number.clone(),
                    contact_name: contact_name.clone(),
                    total_seconds: *seconds,
                    reason: reason.to_string(),
                };
            }
            CallState::Dialing { number, contact_name }
            | CallState::Ringing { number, contact_name } => {
                self.current_state = CallState::Ended {
                    number: number.clone(),
                    contact_name: contact_name.clone(),
                    total_seconds: 0,
                    reason: "Cancelled".to_string(),
                };
            }
            _ => {
                self.current_state = CallState::Idle;
            }
        }
    }

    pub fn reset_to_idle(&mut self) {
        self.current_state = CallState::Idle;
    }
}

/// Formats call elapsed seconds into MM:SS or HH:MM:SS string.
pub fn format_duration(seconds: u64) -> String {
    let hrs = seconds / 3600;
    let mins = (seconds % 3600) / 60;
    let secs = seconds % 60;
    if hrs > 0 {
        format!("{:02}:{:02}:{:02}", hrs, mins, secs)
    } else {
        format!("{:02}:{:02}", mins, secs)
    }
}
