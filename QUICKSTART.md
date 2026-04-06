# Quick Start Guide

## Prerequisites

- Java 21 JDK
- Maven 3.9+
- Docker & Docker Compose (optional, for containerized deployment)
- PostgreSQL 16 (optional, or use Docker)
- Git (for Config Server repository)

## Step-by-Step Setup

### 1. Initialize Git Repository for Config Server

```bash
# Create a new repository for centralized configuration
mkdir ecommerce-config
cd ecommerce-config
git init
git remote add origin https://github.com/yourusername/ecommerce-config.git

# Create directory structure
mkdir config

# Create configuration files (examples below)
```

**config/application-default.yml**
```yaml
logging:
  level:
    root: INFO
    com.ecommerce: DEBUG
```

**config/order-service-dev.yml**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: order_user
    password: order_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  application:
    name: order-service

server:
  port: 8081
  servlet:
    context-path: /

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    hostname: localhost
    prefer-ip-address: false
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

**config/payment-service-dev.yml**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_db
    username: payment_user
    password: payment_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  application:
    name: payment-service

server:
  port: 8082

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

**config/customer-service-dev.yml**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/customer_db
    username: customer_user
    password: customer_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  application:
    name: customer-service

server:
  port: 8083

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

**config/product-service-dev.yml**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/product_db
    username: product_user
    password: product_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  application:
    name: product-service

server:
  port: 8084

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

**config/api-gateway-dev.yml**
```yaml
spring:
  application:
    name: api-gateway

server:
  port: 8080

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/

spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

```bash
# Push to Git
git add .
git commit -m "Initial configuration"
git push origin main
```

### 2. Create Project Structure

```bash
# Create parent project
mkdir ecommerce-microservices
cd ecommerce-microservices

# Create subdirectories for each service
mkdir config-server
mkdir eureka-server
mkdir api-gateway
mkdir order-service
mkdir payment-service
mkdir customer-service
mkdir product-service

# Initialize Git
git init
```

### 3. Create Parent POM

Create `pom.xml` in the root directory (see IMPLEMENTATION_GUIDE.md for complete parent pom.xml)

### 4. Start PostgreSQL

**Option A: Using Docker**
```bash
# Create databases
docker run --name postgres \
  -e POSTGRES_PASSWORD=root \
  -e POSTGRES_DB=postgres \
  -p 5432:5432 \
  -d postgres:16-alpine

# Create individual databases for each service
docker exec -it postgres psql -U postgres -c "CREATE DATABASE order_db;"
docker exec -it postgres psql -U postgres -c "CREATE DATABASE payment_db;"
docker exec -it postgres psql -U postgres -c "CREATE DATABASE customer_db;"
docker exec -it postgres psql -U postgres -c "CREATE DATABASE product_db;"

# Create users
docker exec -it postgres psql -U postgres -c "CREATE USER order_user WITH ENCRYPTED PASSWORD 'order_password';"
docker exec -it postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE order_db TO order_user;"
# Repeat for other users and databases
```

**Option B: Local PostgreSQL Installation**
```bash
# Create databases and users using psql
psql -U postgres
postgres=# CREATE DATABASE order_db;
postgres=# CREATE USER order_user WITH ENCRYPTED PASSWORD 'order_password';
postgres=# GRANT ALL PRIVILEGES ON DATABASE order_db TO order_user;
# Repeat for other databases
```

### 5. Start Services in Order

**Terminal 1: Config Server**
```bash
cd config-server
mvn spring-boot:run
# Should start on http://localhost:8888
```

**Terminal 2: Eureka Server**
```bash
cd eureka-server
mvn spring-boot:run
# Should start on http://localhost:8761
# Visit http://localhost:8761 to see dashboard
```

**Terminal 3: Order Service**
```bash
cd order-service
mvn spring-boot:run
# Should start on http://localhost:8081
# Visit http://localhost:8761 to see registration
```

**Terminal 4: Payment Service**
```bash
cd payment-service
mvn spring-boot:run
# Should start on http://localhost:8082
```

**Terminal 5: Customer Service**
```bash
cd customer-service
mvn spring-boot:run
# Should start on http://localhost:8083
```

**Terminal 6: Product Service**
```bash
cd product-service
mvn spring-boot:run
# Should start on http://localhost:8084
```

**Terminal 7: API Gateway**
```bash
cd api-gateway
mvn spring-boot:run
# Should start on http://localhost:8080
```

### 6. Verify All Services are Running

```bash
# Check Eureka Dashboard
curl http://localhost:8761

# Health checks
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

---

## Testing the System

### Create a Customer

```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1-555-123-4567"
  }'

# Response example:
# {
#   "customerId": 1,
#   "email": "john@example.com",
#   "firstName": "John",
#   "lastName": "Doe",
#   "phone": "+1-555-123-4567",
#   "createdAt": "2024-04-06T10:00:00Z"
# }
```

### Create a Product

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 999.99,
    "category": "Electronics",
    "inventory": {
      "quantity": 10
    }
  }'

# Response example:
# {
#   "productId": 1,
#   "name": "Laptop",
#   "price": 999.99,
#   "available": 10
# }
```

### Create an Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ],
    "paymentMethod": "CREDIT_CARD"
  }'

# Response example:
# {
#   "orderId": 1,
#   "customerId": 1,
#   "status": "CONFIRMED",
#   "totalAmount": 1999.98,
#   "items": [
#     {
#       "productId": 1,
#       "quantity": 2,
#       "unitPrice": 999.99
#     }
#   ],
#   "paymentId": 1,
#   "paymentStatus": "COMPLETED",
#   "createdAt": "2024-04-06T10:30:00Z"
# }
```

### Get Order Details

```bash
curl http://localhost:8080/api/orders/1

# Response example:
# {
#   "orderId": 1,
#   "customerId": 1,
#   "status": "CONFIRMED",
#   "totalAmount": 1999.98,
#   "paymentId": 1,
#   "paymentStatus": "COMPLETED",
#   "createdAt": "2024-04-06T10:30:00Z",
#   "updatedAt": "2024-04-06T10:30:00Z"
# }
```

### Get Customer Orders

```bash
curl http://localhost:8080/api/orders?customerId=1
```

### Get Product Details

```bash
curl http://localhost:8080/api/products/1
```

### Check Product Inventory

```bash
curl http://localhost:8080/api/products/1/inventory

# Response example:
# {
#   "productId": 1,
#   "quantity": 10,
#   "reserved": 2,
#   "available": 8,
#   "status": "IN_STOCK"
# }
```

---

## Docker Compose Quick Start

```bash
# Navigate to root directory with docker-compose.yml
cd ecommerce-microservices

# Build all services
docker-compose build

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f api-gateway

# Stop all services
docker-compose down

# Verify services are running
curl http://localhost:8080/actuator/health
curl http://localhost:8761
```

---

## Troubleshooting

### Service Not Registering with Eureka
- Check if Eureka server is running on port 8761
- Verify `eureka.client.serviceUrl.defaultZone` in config files
- Check service logs for errors

### Config Server Not Found
- Verify Config Server is running on port 8888
- Check Git repository URL and branch name
- Ensure bootstrap.yml has correct config server URL

### Database Connection Failed
- Verify PostgreSQL is running and accessible
- Check database credentials in config files
- Ensure databases are created
- Verify JDBC URL format

### API Gateway Returns 503 Service Unavailable
- Check if downstream services are registered with Eureka
- Verify all services are running
- Check API Gateway logs for routing errors

### Inventory Reservation Failed
- Check if Product Service is running
- Verify inventory exists for product
- Check if quantity requested exceeds available stock

---

## Useful Commands

```bash
# Check if port is in use
lsof -i :8080
netstat -tulpn | grep :8080 (Linux)
netstat -ano | findstr :8080 (Windows)

# Kill process on port
kill -9 <PID> (Linux/Mac)
taskkill /PID <PID> /F (Windows)

# View running Docker containers
docker ps

# View Docker logs
docker logs -f <container_name>

# Check Maven build
mvn clean install

# Skip tests during build
mvn clean install -DskipTests

# Run specific test
mvn test -Dtest=OrderServiceTest

# Build only parent
mvn install -N

# Build single module
mvn clean install -pl order-service -am
```

---

## Production Checklist

- [ ] All services tested locally
- [ ] Config Server backed by Git repository
- [ ] Eureka Server configured for high availability
- [ ] Database backups configured
- [ ] Monitoring and alerting set up
- [ ] API Gateway security configured (JWT, rate limiting)
- [ ] Circuit breakers configured for resilience
- [ ] Centralized logging configured
- [ ] Load balancing configured for each service
- [ ] Auto-scaling policies defined
- [ ] Disaster recovery plan documented
- [ ] Performance testing completed
- [ ] Security scanning completed
- [ ] SSL/TLS certificates configured
- [ ] Documentation up to date

---

## Next Steps

1. **Implement Authentication & Authorization**
   - Add Spring Security
   - Implement JWT tokens
   - Configure role-based access control (RBAC)

2. **Add API Documentation**
   - Integrate SpringDoc OpenAPI (Swagger)
   - Document all endpoints

3. **Implement Event-Driven Communication**
   - Add Apache Kafka or RabbitMQ
   - Implement async event publishing

4. **Add Service Mesh** (Optional)
   - Implement Istio for advanced traffic management
   - Add distributed tracing with Jaeger

5. **Performance Optimization**
   - Implement caching strategies
   - Optimize database queries
   - Add indexing where needed

6. **Advanced Monitoring**
   - Set up Prometheus and Grafana
   - Configure distributed tracing
   - Implement custom metrics

---

## Documentation References

- [ARCHITECTURE.md](./ARCHITECTURE.md) - System architecture and design
- [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md) - Project structure and configuration
- [API_COMMUNICATION.md](./API_COMMUNICATION.md) - API endpoints and communication flows
- [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - Code implementation examples
- [DEPLOYMENT_MONITORING.md](./DEPLOYMENT_MONITORING.md) - Deployment and monitoring guides

---

## Support and Resources

- Spring Boot Documentation: https://spring.io/projects/spring-boot
- Spring Cloud Documentation: https://spring.io/projects/spring-cloud
- Docker Documentation: https://docs.docker.com
- Kubernetes Documentation: https://kubernetes.io/docs
- Maven Documentation: https://maven.apache.org

Good luck with your microservices journey! 🚀

