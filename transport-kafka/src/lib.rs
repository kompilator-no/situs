use framework_core::{ActionStep, ProbeStep, Serializer, StepError, StepResult};
use serde_json::Value;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};

pub struct ProducerStep {
    pub step_name: String,
    pub topic: String,
    pub key: Option<String>,
    pub headers: HashMap<String, String>,
    pub payload: Value,
    pub serializer: Arc<dyn Serializer>,
    pub timeout: Duration,
}

impl ActionStep for ProducerStep {
    fn name(&self) -> &str {
        &self.step_name
    }

    fn execute(&self) -> StepResult {
        let started = Instant::now();
        match self.run() {
            Ok(msg) => StepResult::success(self.name(), msg, started.elapsed())
                .with_metadata("adapter", "kafka")
                .with_metadata("topic", self.topic.clone())
                .with_metadata("serializer", self.serializer.name()),
            Err(err) => StepResult::failed(self.name(), err, started.elapsed())
                .with_metadata("adapter", "kafka")
                .with_metadata("topic", self.topic.clone()),
        }
    }
}

impl ProducerStep {
    fn run(&self) -> Result<String, StepError> {
        if self.topic.is_empty() {
            return Err(StepError::Validation {
                details: "producer-step requires a non-empty topic".into(),
            });
        }
        if self.timeout.is_zero() {
            return Err(StepError::Timeout {
                operation: format!("producing to topic '{}'", self.topic),
                timeout: self.timeout,
            });
        }

        self.serializer.serialize(&self.payload)?;
        Ok(format!("message produced to topic '{}'", self.topic))
    }
}

pub struct ConsumerStep {
    pub step_name: String,
    pub topic: String,
    pub expected_match: Value,
    pub serializer: Arc<dyn Serializer>,
    pub timeout: Duration,
    pub simulated_message: Option<Value>,
}

impl ProbeStep for ConsumerStep {
    fn name(&self) -> &str {
        &self.step_name
    }

    fn probe(&self) -> StepResult {
        let started = Instant::now();
        match self.run() {
            Ok(msg) => StepResult::success(self.name(), msg, started.elapsed())
                .with_metadata("adapter", "kafka")
                .with_metadata("topic", self.topic.clone())
                .with_metadata("serializer", self.serializer.name()),
            Err(err) => StepResult::failed(self.name(), err, started.elapsed())
                .with_metadata("adapter", "kafka")
                .with_metadata("topic", self.topic.clone()),
        }
    }
}

impl ConsumerStep {
    fn run(&self) -> Result<String, StepError> {
        if self.topic.is_empty() {
            return Err(StepError::Validation {
                details: "consumer-step requires a non-empty topic".into(),
            });
        }

        let incoming = self
            .simulated_message
            .as_ref()
            .ok_or_else(|| StepError::Timeout {
                operation: format!("consuming from topic '{}'", self.topic),
                timeout: self.timeout,
            })?;

        let payload = self.serializer.serialize(incoming)?;
        let decoded = self.serializer.deserialize(&payload)?;
        if decoded != self.expected_match {
            return Err(StepError::Validation {
                details: format!(
                    "consumer-step expectation mismatch on topic '{}': expected {}, got {}",
                    self.topic, self.expected_match, decoded
                ),
            });
        }

        Ok(format!(
            "message matched expectation on topic '{}'",
            self.topic
        ))
    }
}
