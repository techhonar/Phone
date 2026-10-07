//! Contacts Management, Indexing, and Phone Formatting in Rust

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContactEntry {
    pub id: String,
    pub name: String,
    pub phone: String,
    pub email: String,
    pub is_favorite: bool,
    pub group_name: String,
}

/// Formats raw digits into readable phone representation:
/// e.g. "1234567890" -> "(123) 456-7890"
pub fn format_phone_number(raw: &str) -> String {
    let digits: Vec<char> = raw.chars().filter(|c| c.is_ascii_digit()).collect();
    match digits.len() {
        10 => format!(
            "({}) {}-{}",
            digits[0..3].iter().collect::<String>(),
            digits[3..6].iter().collect::<String>(),
            digits[6..10].iter().collect::<String>()
        ),
        11 if digits[0] == '1' => format!(
            "+1 ({}) {}-{}",
            digits[1..4].iter().collect::<String>(),
            digits[4..7].iter().collect::<String>(),
            digits[7..11].iter().collect::<String>()
        ),
        7 => format!(
            "{}-{}",
            digits[0..3].iter().collect::<String>(),
            digits[3..7].iter().collect::<String>()
        ),
        _ => raw.to_string(),
    }
}

/// Computes the leading alphabetical section header for a contact name.
pub fn get_section_header(name: &str) -> char {
    let first = name.chars().next().unwrap_or('#').to_ascii_uppercase();
    if first.is_ascii_alphabetic() {
        first
    } else {
        '#'
    }
}
