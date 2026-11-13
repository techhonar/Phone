//! High-performance T9 Predictive Keypad Matching Engine
//! Converts text to phone dialpad digits (ITU-T E.161 standard), maps exact character offsets,
//! and supports substring + word-initial predictive matching.

use serde::{Deserialize, Serialize};

/// Returns the dialpad digit ('2'-'9') for an ASCII character.
pub fn char_to_t9(c: char) -> Option<char> {
    match c.to_ascii_uppercase() {
        'A' | 'B' | 'C' => Some('2'),
        'D' | 'E' | 'F' => Some('3'),
        'G' | 'H' | 'I' => Some('4'),
        'J' | 'K' | 'L' => Some('5'),
        'M' | 'N' | 'O' => Some('6'),
        'P' | 'Q' | 'R' | 'S' => Some('7'),
        'T' | 'U' | 'V' => Some('8'),
        'W' | 'X' | 'Y' | 'Z' => Some('9'),
        '0'..='9' => Some(c),
        _ => None,
    }
}

/// Converts a full string (such as contact name) into its T9 digit sequence.
pub fn text_to_t9(text: &str) -> String {
    text.chars().filter_map(char_to_t9).collect()
}

/// Normalizes a phone number by stripping formatting characters like '+', '-', '(', ')', ' '.
pub fn normalize_number(number: &str) -> String {
    number.chars().filter(|c| c.is_ascii_digit()).collect()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct T9MatchResult {
    pub contact_id: String,
    pub name: String,
    pub phone: String,
    pub match_type: String, // "name", "initials", or "number"
    pub match_start: usize,
    pub match_length: usize,
    pub score: i32,
}

/// Maps each T9 digit back to its char index in the original string.
fn indexed_t9(text: &str) -> (String, Vec<usize>) {
    let mut t9 = String::new();
    let mut indices = Vec::new();
    for (idx, ch) in text.chars().enumerate() {
        if let Some(d) = char_to_t9(ch) {
            t9.push(d);
            indices.push(idx);
        }
    }
    (t9, indices)
}

/// Extracts word initials in T9 digits (e.g., "Alex Johnson" -> "25").
fn word_initials_t9(text: &str) -> String {
    text.split_whitespace()
        .filter_map(|w| w.chars().next().and_then(char_to_t9))
        .collect()
}

/// Performs T9 predictive search over a list of contacts given dialed digits.
pub fn search_contacts(
    query_digits: &str,
    contacts: &[(String, String, String)], // (id, name, phone)
) -> Vec<T9MatchResult> {
    if query_digits.is_empty() {
        return Vec::new();
    }

    let clean_query: String = query_digits
        .trim()
        .chars()
        .filter(|c| c.is_ascii_digit())
        .collect();
    if clean_query.is_empty() {
        return Vec::new();
    }

    let mut results = Vec::new();

    for (id, name, phone) in contacts {
        let mut best_match: Option<T9MatchResult> = None;

        // 1. Check Name T9 substring match with accurate original char offsets
        let (name_t9, char_indices) = indexed_t9(name);
        if let Some(pos) = name_t9.find(&clean_query) {
            let orig_start = char_indices[pos];
            let orig_end = char_indices[pos + clean_query.len() - 1];
            let orig_len = (orig_end - orig_start) + 1;
            let score = if pos == 0 { 110 } else { 85 } - (pos as i32 * 2);

            best_match = Some(T9MatchResult {
                contact_id: id.clone(),
                name: name.clone(),
                phone: phone.clone(),
                match_type: "name".to_string(),
                match_start: orig_start,
                match_length: orig_len,
                score,
            });
        }

        // 2. Check word initials match (e.g. "25" -> "Alex Johnson")
        if clean_query.len() >= 2 {
            let initials = word_initials_t9(name);
            if initials.starts_with(&clean_query) {
                let init_score = 92;
                if best_match.as_ref().map_or(true, |m| init_score > m.score) {
                    best_match = Some(T9MatchResult {
                        contact_id: id.clone(),
                        name: name.clone(),
                        phone: phone.clone(),
                        match_type: "initials".to_string(),
                        match_start: 0,
                        match_length: 1,
                        score: init_score,
                    });
                }
            }
        }

        // 3. Check Number match (digits dialed directly match contact phone)
        let clean_phone = normalize_number(phone);
        if let Some(pos) = clean_phone.find(&clean_query) {
            let num_score = if pos == 0 { 98 } else { 75 } - (pos as i32);
            if best_match.as_ref().map_or(true, |m| num_score > m.score) {
                best_match = Some(T9MatchResult {
                    contact_id: id.clone(),
                    name: name.clone(),
                    phone: phone.clone(),
                    match_type: "number".to_string(),
                    match_start: pos,
                    match_length: clean_query.len(),
                    score: num_score,
                });
            }
        }

        if let Some(m) = best_match {
            results.push(m);
        }
    }

    // Sort descending by score, then alphabetically
    results.sort_by(|a, b| b.score.cmp(&a.score).then_with(|| a.name.cmp(&b.name)));
    results
}
