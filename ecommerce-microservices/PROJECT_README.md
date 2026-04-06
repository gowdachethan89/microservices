# E-Commerce Microservices - Multi-Module Maven Project

## Project Structure

```
ecommerce-microservices/
├── pom.xml (Parent POM)
│
├── discovery-server/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/discovery/
│   │   └── DiscoveryServerApplication.java
│   └── src/main/resources/
│       └── application.yml
│
├── config-server/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/config/
│   │   └── ConfigServerApplication.java
│   └── src/main/resources/
│       └── application.yml
│
├── api-gateway/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/gateway/
│   │   └── ApiGatewayApplication.java
│   └── src/main/resources/
│       └── application.yml
│
├── customer-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/customer/
│   │   ├── CustomerServiceApplication.java
│   │   ├── controller/
│   │   │   └── CustomerController.java
│   │   ├── service/
│   │   │   └── CustomerService.java
│   │   ├── repository/
│   │   │   └── CustomerRepository.java
│   │   ├── entity/
│   │   │   └── Customer.java
│   │   └── dto/
│   │       └── CustomerDTO.java
│   └── src/main/resources/
│       └── application.yml
│
├── product-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/product/
│   │   ├── ProductServiceApplication.java
│   │   ├── controller/
│   │   │   └── ProductController.java
│   │   ├── service/
│   │   │   └── ProductService.java
│   │   ├── repository/
│   │   │   └── ProductRepository.java
│   │   ├── entity/
│   │   │   └── Product.java
│   │   └── dto/
│   │       └── ProductDTO.java
│   └── src/main/resources/
│       └── application.yml
│
├── order-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/order/
│   │   ├── OrderServiceApplication.java
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── service/
│   │   │   └── OrderService.java
│   │   ├── repository/
│   │   │   └── OrderRepository.java
│   │   ├── entity/
│   │   │   └── Order.java
│   │   └── dto/
│   │       └── OrderDTO.java
│   └── src/main/resources/
│       └── application.yml
│
└── payment-service/
    ├── pom.xml
    ├── src/main/java/com/ecommerce/payment/
    │   ├── PaymentServiceApplication.java
    │   ├── controller/
    │   │   └── PaymentController.java
    │   ├── service/
    │   │   └── PaymentService.java
    │   ├── repository/
    │   │   └── PaymentRepository.java
    │   ├── entity/
    │   │   └── Payment.java
    │   └── dto/
    │       └── PaymentDTO.java
    └── src/main/resources/
        └── application.yml
```

## Services Overview

### 1. Discovery Server (Eureka) - Port 8761
- Service registration and discovery
- Eureka Server enabled
- Manages all service instances

**Key Class**: `DiscoveryServerApplication`

### 2. Config Server - Port 8888
- Centralized configuration management
- Native profile with classpath resources
- Configuration server for all services

**Key Class**: `ConfigServerApplication`

### 3. API Gateway - Port 8080
- Routes requests to appropriate services
- Service discovery enabled
- Load balancing support

**Key Class**: `ApiGatewayApplication`

### 4. Customer Service - Port 8083
- Manages customer data
- CRUD operations on customers
- H2 in-memory database

**Structure**:
- Controller: `CustomerController` - REST endpoints
- Service: `CustomerService` - Business logic
- Repository: `CustomerRepository` - Data access
- Entity: `Customer` - Database model
- DTO: `CustomerDTO` - Data transfer object

**REST Endpoints**:
```
POST   /api/customers              → Create customer
GET    /api/customers              → Get all customers
GET    /api/customers/{id}         → Get customer by ID
PUT    /api/customers/{id}         → Update customer
DELETE /api/customers/{id}         → Delete customer
```

### 5. Product Service - Port 8084
- Manages product catalog
- Search and filtering capabilities
- H2 in-memory database

**Structure**:
- Controller: `ProductController` - REST endpoints
- Service: `ProductService` - Business logic
- Repository: `ProductRepository` - Data access
- Entity: `Product` - Database model
- DTO: `ProductDTO` - Data transfer object

**REST Endpoints**:
```
POST   /api/products               → Create product
GET    /api/products               → Get all products
GET    /api/products/{id}          → Get product by ID
GET    /api/products/category/{cat}→ Get by category
GET    /api/products/search        → Search products
PUT    /api/products/{id}          → Update product
DELETE /api/products/{id}          → Delete product
```

### 6. Order Service - Port 8081
- Manages customer orders
- Order status tracking
- H2 in-memory database

**Structure**:
- Controller: `OrderController` - REST endpoints
- Service: `OrderService` - Business logic
- Repository: `OrderRepository` - Data access
- Entity: `Order` - Database model
- DTO: `OrderDTO` - Data transfer object

**REST Endpoints**:
```
POST   /api/orders                 → Create order
GET    /api/orders                 → Get all orders
GET    /api/orders/{id}            → Get order by ID
GET    /api/orders/customer/{id}   → Get by customer
GET    /api/orders/status/{status} → Get by status
PUT    /api/orders/{id}/status     → Update status
PUT    /api/orders/{id}/payment    → Update payment info
DELETE /api/orders/{id}            → Delete order
```

### 7. Payment Service - Port 8082
- Processes payments
- Manages payment transactions
- H2 in-memory database

**Structure**:
- Controller: `PaymentController` - REST endpoints
- Service: `PaymentService` - Business logic
- Repository: `PaymentRepository` - Data access
- Entity: `Payment` - Database model
- DTO: `PaymentDTO` - Data transfer object

**REST Endpoints**:
```
POST   /api/payments               → Process payment
GET    /api/payments               → Get all payments
GET    /api/payments/{id}          → Get payment by ID
GET    /api/payments/order/{id}    → Get by order
GET    /api/payments/status/{stat} → Get by status
POST   /api/payments/{id}/refund   → Refund payment
DELETE /api/payments/{id}          → Delete payment
```

## Technology Stack

- **Spring Boot**: 3.4.0
- **Spring Cloud**: 2024.0.0
- **Java**: 21
- **Database**: H2 (in-memory)
- **Build Tool**: Maven 3.9+

## Key Dependencies (Parent POM)

```xml
<!-- Spring Cloud Components -->
- spring-cloud-starter-netflix-eureka-client
- spring-cloud-starter-config
- spring-cloud-starter-openfeign
- spring-cloud-starter-circuitbreaker-resilience4j
- spring-cloud-starter-netflix-eureka-server
- spring-cloud-config-server
- spring-cloud-starter-gateway

<!-- Spring Boot -->
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-actuator

<!-- Database -->
- h2 (runtime)

<!-- Utils -->
- lombok
```

## Running the Services

### Prerequisites
- Java 21 JDK
- Maven 3.9+

### Build All Modules
```bash
mvn clean install
```

### Run Individual Services

#### 1. Start Discovery Server (First)
```bash
cd discovery-server
mvn spring-boot:run
# Access: http://localhost:8761
```

#### 2. Start Config Server
```bash
cd config-server
mvn spring-boot:run
# Access: http://localhost:8888
```

#### 3. Start API Gateway
```bash
cd api-gateway
mvn spring-boot:run
# Access: http://localhost:8080
```

#### 4. Start Business Services (in any order)
```bash
# Customer Service
cd customer-service && mvn spring-boot:run

# Product Service (new terminal)
cd product-service && mvn spring-boot:run

# Order Service (new terminal)
cd order-service && mvn spring-boot:run

# Payment Service (new terminal)
cd payment-service && mvn spring-boot:run
```

## Service Ports

| Service | Port | Health |
|---------|------|--------|
| Discovery Server | 8761 | http://localhost:8761 |
| Config Server | 8888 | http://localhost:8888/actuator/health |
| API Gateway | 8080 | http://localhost:8080/actuator/health |
| Customer Service | 8083 | http://localhost:8083/actuator/health |
| Product Service | 8084 | http://localhost:8084/actuator/health |
| Order Service | 8081 | http://localhost:8081/actuator/health |
| Payment Service | 8082 | http://localhost:8082/actuator/health |

## Architecture Layers

### Controller Layer
- Handles HTTP requests
- Validates input
- Returns ResponseEntity with appropriate HTTP status codes
- RESTful endpoint design

### Service Layer
- Business logic implementation
- Transaction management
- Logging and error handling
- DTOs to Entity conversion

### Repository Layer
- JPA Repository for database operations
- Custom query methods
- CRUD operations

### Entity Layer
- JPA entities mapped to database tables
- Timestamps (createdAt, updatedAt) tracking
- Relationships and constraints

### DTO Layer
- Data Transfer Objects
- Decouples API from entities
- Serialization/Deserialization

## Best Practices Implemented

✅ **Three-tier architecture**: Controller → Service → Repository
✅ **DTOs**: Separation of concerns
✅ **Logging**: SLF4J with Lombok's @Slf4j
✅ **Exception Handling**: Proper HTTP status codes
✅ **Transactions**: @Transactional annotations
✅ **Dependency Injection**: Constructor injection with @RequiredArgsConstructor
✅ **Service Discovery**: Eureka integration
✅ **Configuration Management**: Spring Cloud Config ready
✅ **Health Checks**: Spring Boot Actuator endpoints
✅ **Database Abstraction**: H2 with JPA

## Maven Commands

```bash
# Clean and install all modules
mvn clean install

# Build specific module
mvn -pl customer-service clean install

# Skip tests during build
mvn clean install -DskipTests

# Run specific service
mvn -pl customer-service spring-boot:run

# Run tests
mvn test

# Package for deployment
mvn clean package
```

## Feign Clients (Service-to-Service Communication)

To add inter-service communication, you can add Feign clients:

```java
@FeignClient(name = "customer-service")
public interface CustomerClient {
    @GetMapping("/api/customers/{id}")
    CustomerDTO getCustomer(@PathVariable Long id);
}
```

Then use in other services with `@EnableFeignClients` annotation.

## Configuration Properties

Each service's `application.yml` includes:

```yaml
spring:
  application:
    name: service-name
  datasource: H2 config
  jpa: Hibernate config
  
server:
  port: specific-port
  
eureka:
  client: registration config
  instance: instance metadata
  
management:
  endpoints: actuator endpoints
```

## Next Steps

1. Add Feign clients for inter-service communication
2. Implement error handling with @ControllerAdvice
3. Add validation with @Valid
4. Implement authorization/authentication
5. Add unit and integration tests
6. Configure Docker containerization
7. Setup CI/CD pipeline
8. Implement distributed tracing

## Notes

- All services use H2 in-memory databases (suitable for development/testing)
- Replace with PostgreSQL/MySQL for production
- Eureka server should be started first for service registration
- API Gateway uses service discovery for routing
- Each service is independently deployable

