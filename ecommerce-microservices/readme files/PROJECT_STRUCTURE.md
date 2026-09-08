# Microservices Project Structure Guide

## Proposed Maven Multi-Module Project Layout

```
ecommerce-microservices/
├── pom.xml (parent - defines shared dependencies)
│
├── config-server/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/configserver/
│   │   └── ConfigServerApplication.java
│   └── src/main/resources/
│       ├── application.yml
│       └── bootstrap.yml
│
├── api-gateway/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/gateway/
│   │   ├── ApiGatewayApplication.java
│   │   ├── config/
│   │   │   ├── GatewayConfig.java
│   │   │   ├── SecurityConfig.java
│   │   │   └── CorsConfig.java
│   │   └── filter/
│   │       ├── AuthenticationFilter.java
│   │       └── LoggingFilter.java
│   └── src/main/resources/
│       ├── application.yml
│       └── bootstrap.yml
│
├── order-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/orderservice/
│   │   ├── OrderServiceApplication.java
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── service/
│   │   │   ├── OrderService.java
│   │   │   ├── OrderServiceImpl.java
│   │   │   └── PaymentClient.java (Feign Client)
│   │   ├── entity/
│   │   │   └── Order.java
│   │   ├── repository/
│   │   │   └── OrderRepository.java
│   │   └── dto/
│   │       ├── OrderDTO.java
│   │       └── CreateOrderRequest.java
│   └── src/main/resources/
│       ├── application.yml
│       ├── bootstrap.yml
│       └── db/migration/ (Flyway migrations)
│
├── payment-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/paymentservice/
│   │   ├── PaymentServiceApplication.java
│   │   ├── controller/
│   │   │   └── PaymentController.java
│   │   ├── service/
│   │   │   ├── PaymentService.java
│   │   │   └── PaymentServiceImpl.java
│   │   ├── entity/
│   │   │   ├── Payment.java
│   │   │   └── Transaction.java
│   │   ├── repository/
│   │   │   ├── PaymentRepository.java
│   │   │   └── TransactionRepository.java
│   │   └── dto/
│   │       ├── PaymentDTO.java
│   │       └── PaymentRequest.java
│   └── src/main/resources/
│       ├── application.yml
│       └── bootstrap.yml
│
├── customer-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/customerservice/
│   │   ├── CustomerServiceApplication.java
│   │   ├── controller/
│   │   │   ├── CustomerController.java
│   │   │   └── AddressController.java
│   │   ├── service/
│   │   │   ├── CustomerService.java
│   │   │   ├── CustomerServiceImpl.java
│   │   │   ├── AddressService.java
│   │   │   └── AddressServiceImpl.java
│   │   ├── entity/
│   │   │   ├── Customer.java
│   │   │   └── Address.java
│   │   ├── repository/
│   │   │   ├── CustomerRepository.java
│   │   │   └── AddressRepository.java
│   │   └── dto/
│   │       ├── CustomerDTO.java
│   │       └── AddressDTO.java
│   └── src/main/resources/
│       ├── application.yml
│       └── bootstrap.yml
│
├── product-service/
│   ├── pom.xml
│   ├── src/main/java/com/ecommerce/productservice/
│   │   ├── ProductServiceApplication.java
│   │   ├── controller/
│   │   │   ├── ProductController.java
│   │   │   └── InventoryController.java
│   │   ├── service/
│   │   │   ├── ProductService.java
│   │   │   ├── ProductServiceImpl.java
│   │   │   ├── InventoryService.java
│   │   │   └── InventoryServiceImpl.java
│   │   ├── entity/
│   │   │   ├── Product.java
│   │   │   ├── Inventory.java
│   │   │   └── Category.java
│   │   ├── repository/
│   │   │   ├── ProductRepository.java
│   │   │   ├── InventoryRepository.java
│   │   │   └── CategoryRepository.java
│   │   └── dto/
│   │       ├── ProductDTO.java
│   │       └── InventoryDTO.java
│   └── src/main/resources/
│       ├── application.yml
│       └── bootstrap.yml
│
└── config-repo/ (Git repository for centralized config)
    ├── order-service-dev.yml
    ├── order-service-prod.yml
    ├── payment-service-dev.yml
    ├── payment-service-prod.yml
    ├── customer-service-dev.yml
    ├── customer-service-prod.yml
    ├── product-service-dev.yml
    ├── product-service-prod.yml
    ├── api-gateway-dev.yml
    └── api-gateway-prod.yml
```

## Key File Descriptions

### Parent pom.xml
- Defines all shared Spring Cloud versions
- Common dependencies (Eureka, Config Client, Feign)
- Build plugins configuration

### Each Service pom.xml
- Inherits from parent
- Service-specific dependencies (JPA, specific libraries)
- Service-specific build configuration

### bootstrap.yml
Located in each service's resources folder:
```yaml
spring:
  application:
    name: order-service  # Or respective service name
  cloud:
    config:
      uri: http://localhost:8888  # Config Server URL
  profiles:
    active: dev
```

### application.yml (Fetched from Config Server)
```yaml
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  instance:
    instance-id: ${spring.application.name}:${spring.application.instance_id:${random.value}}

spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: user
    password: password
  
logging:
  level:
    com.ecommerce: DEBUG
    org.springframework.cloud: INFO
```

## Database Schema Examples

### Order Service (orders_db)
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### Payment Service (payment_db)
```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transaction_history (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    status_before VARCHAR(50),
    status_after VARCHAR(50),
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(status);
```

### Customer Service (customer_db)
```sql
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50),
    zip VARCHAR(20),
    country VARCHAR(100) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_addresses_customer_id ON addresses(customer_id);
```

### Product Service (product_db)
```sql
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    category_id BIGINT REFERENCES categories(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE REFERENCES products(id),
    quantity INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER DEFAULT 0,
    available INTEGER GENERATED ALWAYS AS (quantity - reserved) STORED
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category_id ON products(category_id);
```

## Technology Stack Summary

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.4.0 |
| Java Version | 21 |
| Service Discovery | Spring Cloud Eureka |
| Configuration Server | Spring Cloud Config |
| API Gateway | Spring Cloud Gateway |
| Inter-service Communication | OpenFeign (REST) |
| Database | PostgreSQL (per service) |
| Build Tool | Maven 3.9+ |
| Testing | JUnit 5, MockMvc |
| Containerization | Docker |
| Orchestration | Kubernetes (optional) |
| Logging | SLF4J + Logback |
| Monitoring | Spring Boot Actuator, Micrometer |

## Implementation Steps

1. **Initialize Parent POM**
   - Define all versions in `<dependencyManagement>`
   - Set up build plugins
   - Create multi-module structure

2. **Create Config Server**
   - Set up Git repository for configs
   - Configure Config Server application
   - Create config files for each service

3. **Create Eureka Server**
   - Simple Spring Boot app with `@EnableEurekaServer`
   - Configure Eureka properties

4. **Create Each Microservice**
   - Set up service-specific pom.xml
   - Create entity, repository, service layers
   - Implement REST controllers
   - Configure Eureka client

5. **Create API Gateway**
   - Configure routing rules
   - Implement filters
   - Set up security

6. **Integration Testing**
   - Test service-to-service communication
   - Test API Gateway routing
   - Test service discovery

## Running Services Locally

```bash
# Terminal 1: Config Server
cd config-server
mvn spring-boot:run

# Terminal 2: Eureka Server
cd eureka-server
mvn spring-boot:run

# Terminal 3-6: Microservices (in separate terminals)
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd customer-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run

# Terminal 7: API Gateway
cd api-gateway
mvn spring-boot:run
```

## Service Ports

- Eureka Server: 8761
- Config Server: 8888
- API Gateway: 8080
- Order Service: 8081
- Payment Service: 8082
- Customer Service: 8083
- Product Service: 8084

