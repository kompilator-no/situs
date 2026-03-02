use framework_core::{ActionStep, ProbeStep, Serializer, StepError, StepResult};
use serde_json::Value;
use std::sync::Arc;
use std::time::{Duration, Instant};

pub struct WsConnectStep {
    pub step_name: String,
    pub endpoint: String,
    pub timeout: Duration,
    pub is_available: bool,
}

impl ActionStep for WsConnectStep {
    fn name(&self) -> &str {
        &self.step_name
    }

    fn execute(&self) -> StepResult {
        let started = Instant::now();
        match self.run() {
            Ok(msg) => StepResult::success(self.name(), msg, started.elapsed())
                .with_metadata("adapter", "websocket")
                .with_metadata("endpoint", self.endpoint.clone()),
            Err(err) => StepResult::failed(self.name(), err, started.elapsed())
                .with_metadata("adapter", "websocket")
                .with_metadata("endpoint", self.endpoint.clone()),
        }
    }
}

impl WsConnectStep {
    fn run(&self) -> Result<String, StepError> {
        if self.timeout.is_zero() {
            return Err(StepError::Timeout {
                operation: format!("connecting websocket {}", self.endpoint),
                timeout: self.timeout,
            });
        }
        if !self.is_available {
            return Err(StepError::Connection {
                endpoint: self.endpoint.clone(),
                details: "websocket handshake failed".into(),
            });
        }
        Ok(format!("connected to {}", self.endpoint))
    }
}

pub struct WsSendStep {
    pub step_name: String,
    pub endpoint: String,
    pub payload: Value,
    pub serializer: Arc<dyn Serializer>,
    pub timeout: Duration,
}

impl ActionStep for WsSendStep {
    fn name(&self) -> &str {
        &self.step_name
    }

    fn execute(&self) -> StepResult {
        let started = Instant::now();
        match self.run() {
            Ok(msg) => StepResult::success(self.name(), msg, started.elapsed())
                .with_metadata("adapter", "websocket")
                .with_metadata("endpoint", self.endpoint.clone())
                .with_metadata("serializer", self.serializer.name()),
            Err(err) => StepResult::failed(self.name(), err, started.elapsed())
                .with_metadata("adapter", "websocket")
                .with_metadata("endpoint", self.endpoint.clone()),
        }
    }
}

impl WsSendStep {
    fn run(&self) -> Result<String, StepError> {
        if self.timeout.is_zero() {
            return Err(StepError::Timeout {
                operation: format!("sending websocket message to {}", self.endpoint),
                timeout: self.timeout,
            });
        }
        self.serializer.serialize(&self.payload)?;
        Ok("websocket message sent".into())
    }
}

pub struct WsReceiveStep {
    pub step_name: String,
    pub endpoint: String,
    pub message_filter: Option<String>,
    pub expected: Value,
    pub serializer: Arc<dyn Serializer>,
    pub timeout: Duration,
    pub simulated_message: Option<Value>,
}

impl ProbeStep for WsReceiveStep {
    fn name(&self) -> &str {
        &self.step_name
    }

    fn probe(&self) -> StepResult {
        let started = Instant::now();
        match self.run() {
            Ok(msg) => StepResult::success(self.name(), msg, started.elapsed())
                .with_metadata("adapter", "websocket")
                .with_metadata("endpoint", self.endpoint.clone())
                .with_metadata("serializer", self.serializer.name()),
            Err(err) => StepResult::failed(self.name(), err, started.elapsed())
                .with_metadata("adapter", "websocket")
                .with_metadata("endpoint", self.endpoint.clone()),
        }
    }
}

impl WsReceiveStep {
    fn run(&self) -> Result<String, StepError> {
        let incoming = self
            .simulated_message
            .as_ref()
            .ok_or_else(|| StepError::Timeout {
                operation: format!("receiving websocket message from {}", self.endpoint),
                timeout: self.timeout,
            })?;

        let encoded = self.serializer.serialize(incoming)?;
        let decoded = self.serializer.deserialize(&encoded)?;
        if decoded != self.expected {
            return Err(StepError::Validation {
                details: format!(
                    "websocket assertion failed at {}: expected {}, got {}",
                    self.endpoint, self.expected, decoded
                ),
            });
        }

        if let Some(filter) = &self.message_filter {
            let message = decoded.to_string();
            if !message.contains(filter) {
                return Err(StepError::Validation {
                    details: format!(
                        "websocket message-filter '{}' did not match payload {}",
                        filter, message
                    ),
                });
            }
        }

        Ok("websocket receive assertion passed".into())
    }
}
