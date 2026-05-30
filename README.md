# SS-OrderService

## Overview

SS-OrderService is the core order-processing microservice for the SamStore e-commerce platform. Built with Java 21 and Spring Boot 3.4.5, it manages the checkout transaction, order status transitions, historical billing/shipping address mapping, payment state synchronization, and audit logging.

The service adopts an event-driven architecture to communicate with other microservices (like inventory or payment) using RabbitMQ. To avoid duplicate message handling and ensure reliable message dispatching, it incorporates robust Inbox/Outbox relational database structures.

## Features

- **Order Processing**: Manage full creation and state modifications for purchase transactions.
- **Order Lifecycle**: Tracks order transitions through custom workflows (e.g., `pending` -> `awaiting_payment` -> `processing` -> `shipped` -> `completed` -> `cancelled`).
- **Relational Address Capture**: Captures permanent historical billing and shipping snapshots on order tables to prevent profile updates from corrupting older records.
- **Audit Histories**: Structured audit trailing logging every transition of an order's status.
- **Idempotency with Inbox Pattern**: Guards consumers against processing duplicate integration messages from RabbitMQ.
- **Transactional Outbox Pattern**: Assures that events tied to order updates are successfully queued and published to the messaging broker.

## Tech Stack

| Category       | Technology                                                     |
| -------------- | -------------------------------------------------------------- |
| Language       | Java 21                                                        |
| Framework      | Spring Boot 3.4.5                                              |
| Build Tool     | Maven                                                          |
| Database       | PostgreSQL                                                     |
| Migration Tool | Flyway                                                         |
| Message Broker | RabbitMQ (Spring AMQP)                                         |
| Security       | Spring Security with OAuth2 Resource Server (JWT verification) |

## Project Structure

```text
SS-OrderService/
├── src/
│   ├── main/
│   │   ├── java/com/samstore/orderservice/
│   │   │   ├── config/       # Spring Boot configurations (RabbitMQ, security, outbox)
│   │   │   ├── controller/   # REST endpoint controllers
│   │   │   ├── dto/          # Data Transfer Objects
│   │   │   ├── entity/       # Relational JPA entities
│   │   │   ├── messaging/    # RabbitMQ publishers and listener components
│   │   │   ├── repository/   # JPA data repositories
│   │   │   └── service/      # Core order business services
│   │   └── resources/
│   │       ├── application.yml # Standard application parameters and configs
│   │       └── db/migration/ # Flyway database schemas
│   └── test/                 # JUnit testing suites
└── pom.xml                   # Maven dependencies build script
```

## Requirements

- Java 21 SDK
- Maven 3.x
- PostgreSQL
- RabbitMQ
- PKCS8 Public key certificate path for JWT signature verification

## Installation

```bash
git clone <repository>
cd SamStore/SS-OrderService
```

Build the executable package:

```bash
mvn clean install
```

## Configuration

The configuration parameters are managed inside the `src/main/resources/application.yml` file and can be overridden via environment variables:

```env
DATABASE_URL=             # PostgreSQL JDBC connection URL (e.g. jdbc:postgresql://localhost:5432/ss_order_db)
DATABASE_USERNAME=        # Username for PostgreSQL database
DATABASE_PASSWORD=        # Password for PostgreSQL database
RABBITMQ_HOST=            # Host address of RabbitMQ server
RABBITMQ_PORT=            # Connection port of RabbitMQ server (default: 5672)
RABBITMQ_USERNAME=        # Username for RabbitMQ broker
RABBITMQ_PASSWORD=        # Password for RabbitMQ broker
```

## Running Locally

To run the application:

```bash
mvn spring-boot:run
```

## Build

To compile and package the JAR artifact:

```bash
mvn package
```

## Testing

To run the JUnit testing suites:

```bash
mvn test
```

## API Documentation

The REST controllers map endpoint requests:

| Method | Endpoint         | Description                                         |
| ------ | ---------------- | --------------------------------------------------- |
| GET    | /actuator/health | Diagnostic endpoint exposed by Spring Boot Actuator |

## Database

- **Database Type**: PostgreSQL.
- **ORM**: Spring Data JPA with Hibernate.
- **Migrations**: Handled via `Flyway` utilizing SQL versioning scripts situated inside `src/main/resources/db/migration/`.
- **Entities**: Coordinates key relations: `orders`, `order_items`, `order_addresses`, `order_status_histories`, `outbox_events`, and `inbox_events`.

## Deployment

- **Docker**: Deployed using the multi-stage [Dockerfile](Dockerfile).
- **Docker Compose**: Service definitions configure DB dependencies alongside the API Gateway.

## Architecture Notes

- **Layered Spring Architecture**: Standard MVC division (Controller -> Service -> Repository -> JPA).
- **Outbox Scheduler**: Custom scheduled publisher guarantees transactional integrity, delivering queued events to RabbitMQ synchronously.

## Known Issues

Not identified from source code.

## Future Improvements

- Integrate a caching mechanism for product detail validation inside the order-creation logic.

## License

```text
License information not specified.
```
