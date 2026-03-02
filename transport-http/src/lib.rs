use framework_core::{ActionStep, Serializer, StepError, StepResult};
use serde_json::Value;
use std::sync::Arc;
use std::time::{Duration, Instant};

#[derive(Debug, Clone)]
pub enum HttpMethod {
    Get,
    Post,
    Put,
    Delete,
}

pub struct HttpRequestStep {
    pub step_name: String,
    pub base_url: String,
    pub path: String,
    pub method: HttpMethod,
    pub body: Option<Value>,
    pub expected_status: u16,
    pub expected_body: Option<Value>,
    pub serializer: Arc<dyn Serializer>,
    pub timeout: Duration,
    pub simulated_status: Option<u16>,
    pub simulated_body: Option<Value>,
}

impl ActionStep for HttpRequestStep {
    fn name(&self) -> &str {
        &self.step_name
    }

    fn execute(&self) -> StepResult {
        let started = Instant::now();
        match self.run() {
            Ok(msg) => StepResult::success(self.name(), msg, started.elapsed())
                .with_metadata("adapter", "http")
                .with_metadata("method", format!("{:?}", self.method))
                .with_metadata("path", self.path.clone())
                .with_metadata("serializer", self.serializer.name()),
            Err(err) => StepResult::failed(self.name(), err, started.elapsed())
                .with_metadata("adapter", "http")
                .with_metadata("path", self.path.clone()),
        }
    }
}

impl HttpRequestStep {
    fn run(&self) -> Result<String, StepError> {
        if self.base_url.is_empty() {
            return Err(StepError::Connection {
                endpoint: self.path.clone(),
                details: "missing base_url for request-step".into(),
            });
        }
        if self.timeout.is_zero() {
            return Err(StepError::Timeout {
                operation: format!("executing {:?} {}", self.method, self.path),
                timeout: self.timeout,
            });
        }

        if let Some(body) = &self.body {
            self.serializer.serialize(body)?;
        }

        let status = self.simulated_status.ok_or_else(|| StepError::Connection {
            endpoint: format!("{}{}", self.base_url, self.path),
            details: "request-step could not connect to endpoint".into(),
        })?;

        if status != self.expected_status {
            return Err(StepError::Validation {
                details: format!(
                    "request-step status assertion failed: expected {}, got {}",
                    self.expected_status, status
                ),
            });
        }

        if let Some(expected) = &self.expected_body {
            let actual = self
                .simulated_body
                .as_ref()
                .ok_or_else(|| StepError::Validation {
                    details: "request-step body assertion failed: missing response body".into(),
                })?;
            if actual != expected {
                return Err(StepError::Validation {
                    details: format!(
                        "request-step body assertion failed: expected {}, got {}",
                        expected, actual
                    ),
                });
            }
        }

        Ok(format!("{:?} {} assertions passed", self.method, self.path))
    }
}
