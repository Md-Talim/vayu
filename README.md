# vayu

```mermaid
stateDiagram-v2
    [*] --> PENDING : Event Scheduled

    PENDING --> DELIVERING: Worker claims delivery

    DELIVERING --> DELIVERED: HTTP success
    DELIVERING --> PENDING: HTTP failure + retries remain
    DELIVERING --> PENDING: Timeout + retries remain

    DELIVERING --> FAILED: Max attempts reached
    DELIVERING --> FAILED: Permanent failure

    FAILED --> PENDING: Manual retry

    DELIVERED --> [*]
```
