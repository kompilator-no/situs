use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fmt::{Display, Formatter};
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum StepStatus {
    Success,
    Failed,
    Timeout,
    ConnectionError,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct StepResult {
    pub step_name: String,
    pub status: StepStatus,
    pub message: String,
    pub elapsed_ms: u128,
    pub metadata: HashMap<String, String>,
}

impl StepResult {
    pub fn success(
        step_name: impl Into<String>,
        message: impl Into<String>,
        elapsed: Duration,
    ) -> Self {
        Self {
            step_name: step_name.into(),
            status: StepStatus::Success,
            message: message.into(),
            elapsed_ms: elapsed.as_millis(),
            metadata: HashMap::new(),
        }
    }

    pub fn failed(step_name: impl Into<String>, error: StepError, elapsed: Duration) -> Self {
        let status = match error {
            StepError::Timeout { .. } => StepStatus::Timeout,
            StepError::Connection { .. } => StepStatus::ConnectionError,
            StepError::Validation { .. } | StepError::Serialization { .. } => StepStatus::Failed,
        };

        Self {
            step_name: step_name.into(),
            status,
            message: error.to_string(),
            elapsed_ms: elapsed.as_millis(),
            metadata: HashMap::new(),
        }
    }

    pub fn with_metadata(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.metadata.insert(key.into(), value.into());
        self
    }
}

#[derive(Debug, Clone)]
pub enum StepError {
    Timeout {
        operation: String,
        timeout: Duration,
    },
    Connection {
        endpoint: String,
        details: String,
    },
    Validation {
        details: String,
    },
    Serialization {
        details: String,
    },
}

impl Display for StepError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            StepError::Timeout { operation, timeout } => {
                write!(
                    f,
                    "Timeout while {} after {} ms",
                    operation,
                    timeout.as_millis()
                )
            }
            StepError::Connection { endpoint, details } => {
                write!(f, "Connection error at {}: {}", endpoint, details)
            }
            StepError::Validation { details } => write!(f, "Validation error: {}", details),
            StepError::Serialization { details } => write!(f, "Serialization error: {}", details),
        }
    }
}

impl std::error::Error for StepError {}

pub trait Serializer: Send + Sync {
    fn serialize(&self, value: &serde_json::Value) -> Result<Vec<u8>, StepError>;
    fn deserialize(&self, payload: &[u8]) -> Result<serde_json::Value, StepError>;
    fn name(&self) -> &'static str;
}

#[derive(Default)]
pub struct JsonSerializer;

impl Serializer for JsonSerializer {
    fn serialize(&self, value: &serde_json::Value) -> Result<Vec<u8>, StepError> {
        serde_json::to_vec(value).map_err(|e| StepError::Serialization {
            details: format!("could not encode JSON payload: {e}"),
        })
    }

    fn deserialize(&self, payload: &[u8]) -> Result<serde_json::Value, StepError> {
        serde_json::from_slice(payload).map_err(|e| StepError::Serialization {
            details: format!("could not decode JSON payload: {e}"),
        })
    }

    fn name(&self) -> &'static str {
        "json"
    }
}

pub trait ActionStep {
    fn name(&self) -> &str;
    fn execute(&self) -> StepResult;
}

pub trait ProbeStep {
    fn name(&self) -> &str;
    fn probe(&self) -> StepResult;
}
