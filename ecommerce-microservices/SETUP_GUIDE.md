# E-Commerce Microservices - Setup Guide

## Prerequisites

- **Java 21 JDK** - Download from oracle.com or use OpenJDK
- **Maven 3.9+** - Download from maven.apache.org
- **IDE** - IntelliJ IDEA, Eclipse, or VS Code
- **Git** - For version control

## Environment Setup

### Windows
```powershell
# Set JAVA_HOME
setx JAVA_HOME "C:\Program Files\Java\jdk-21"
setx PATH "%PATH%;%JAVA_HOME%\bin"

# Verify Java installation
java -version
```

### Mac/Linux
```bash
# Set JAVA_HOME
export JAVA_HOME=/usr/libexec/java_home -v 21
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java installation
java -version
```

## Building the Project

### Clone the Repository
```bash
cd /path/to/workspace
git clone <repository-url>
cd ecommerce-microservices
```

### Build All Modules
```bash
# Clean and build all modules
mvn clean install

# Build without running tests
mvn clean install -DskipTests

# Build specific module
mvn -pl customer-service clean install
```

### Verify Build
```bash
# Check if all modules are built successfully
mvn verify

# Run all tests
mvn test
```

## Running Services Locally

### Option 1: Start All Services (Recommended)

**Terminal 1: Discovery Server**
```bash
cd discovery-server
mvn spring-boot:run
# Wait for startup, then access http://localhost:8761
```

**Terminal 2: Config Server**
```bash
cd config-server
mvn spring-boot:run
# Access: http://localhost:8888/actuator/health
```

**Terminal 3: API Gateway**
```bash
cd api-gateway
mvn spring-boot:run
# Access: http://localhost:8080/actuator/health
```

**Terminal 4: Customer Service**
```bash
cd customer-service
mvn spring-boot:run
# Access: http://localhost:8083/actuator/health
```

**Terminal 5: Product Service**
```bash
cd product-service
mvn spring-boot:run
# Access: http://localhost:8084/actuator/health
```

**Terminal 6: Order Service**
```bash
cd order-service
mvn spring-boot:run
# Access: http://localhost:8081/actuator/health
```

**Terminal 7: Payment Service**
```bash
cd payment-service
mvn spring-boot:run
# Access: http://localhost:8082/actuator/health
```

### Option 2: Run from IDE

#### IntelliJ IDEA
1. Open the project as Maven project
2. Right-click on each `*Application.java` class
3. Select "Run" or "Debug"
4. Services start with spring-boot runner

#### Eclipse
1. Import project as Maven project
2. Right-click pom.xml → Run As → Maven build
3. Set goals: `spring-boot:run`

### Option 3: Build JAR and Run

```bash
# Build all JARs
mvn clean package

# Run discovery-server JAR
java -jar discovery-server/target/discovery-server-1.0.0.jar

# Run other services (in separate terminals)
java -jar config-server/target/config-server-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
# ... and so on
```

## Verifying Services

### Check Eureka Dashboard
```
http://localhost:8761
```
You should see all services registered.

### Check Service Health
```bash
curl http://localhost:8761/actuator/health
curl http://localhost:8888/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### API Gateway Available Routes
```bash
curl http://localhost:8080/actuator/gateway/routes
```

## Testing APIs

### Using cURL

#### Create Customer
```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1-555-1234"
  }'
```

#### Get All Customers
```bash
curl http://localhost:8080/api/customers
```

#### Get Customer by ID
```bash
curl http://localhost:8080/api/customers/1
```

#### Create Product
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 999.99,
    "quantity": 10,
    "category": "Electronics"
  }'
```

#### Create Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "totalAmount": 999.99
  }'
```

#### Process Payment
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 1,
    "amount": 999.99,
    "paymentMethod": "CREDIT_CARD"
  }'
```

### Using Postman

1. **Import Collection**
   - Create a new Postman collection
   - Add requests for each endpoint
   - Save for reuse

2. **Sample Requests**
   - Create Customer: POST to http://localhost:8080/api/customers
   - Create Product: POST to http://localhost:8080/api/products
   - Create Order: POST to http://localhost:8080/api/orders
   - Process Payment: POST to http://localhost:8080/api/payments

### Using REST Client (VS Code)

Create a `.http` or `.rest` file:

```http
### Create Customer
POST http://localhost:8080/api/customers
Content-Type: application/json

{
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1-555-1234"
}

### Get All Customers
GET http://localhost:8080/api/customers

### Get Customer by ID
GET http://localhost:8080/api/customers/1
```

## Logs and Debugging

### View Service Logs
Each service outputs logs to console. Set log levels in `application.yml`:

```yaml
logging:
  level:
    root: INFO
    com.ecommerce: DEBUG
    org.springframework.cloud: DEBUG
```

### Common Issues

#### Services Not Registering with Eureka
- Ensure Discovery Server is running first
- Check that Eureka URL in other services is correct
- Verify network connectivity

#### API Gateway Returns 503
- Check if backend services are running
- Verify service is registered with Eureka
- Check application logs

#### H2 Console Access
- Customer Service: http://localhost:8083/h2-console
- Product Service: http://localhost:8084/h2-console
- Order Service: http://localhost:8081/h2-console
- Payment Service: http://localhost:8082/h2-console

### Common Port Issues

If ports are already in use, change in `application.yml`:

```yaml
server:
  port: 8085  # Change to available port
```

## IDE Configuration

### IntelliJ IDEA
1. File → Open → Select pom.xml
2. Open as Project
3. Maven sidebar appears on right
4. Run each service by clicking "run" icon

### Eclipse
1. File → Import → Maven → Existing Maven Projects
2. Select the project root
3. Select all modules
4. Click Finish

## Troubleshooting

### Maven Build Fails
```bash
# Clear local repository cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean install
```

### Services Won't Start
```bash
# Check if ports are in use
lsof -i :8761  # Mac/Linux
netstat -ano | findstr :8761  # Windows

# Kill process on port
kill -9 <PID>  # Mac/Linux
taskkill /PID <PID> /F  # Windows
```

### Database Connection Issues
- H2 databases are in-memory
- Data is lost on service restart
- For persistent data, use PostgreSQL/MySQL

## Next Steps

1. **Add Integration Tests**
   - MockMvc for controller tests
   - TestRestTemplate for integration tests

2. **Add Validation**
   - @Valid annotations
   - Custom validators

3. **Add Authorization**
   - Spring Security
   - JWT tokens

4. **Containerize Services**
   - Docker images
   - Docker Compose

5. **Setup CI/CD**
   - GitHub Actions
   - GitLab CI

## Useful Maven Commands

```bash
# Clean previous builds
mvn clean

# Compile source code
mvn compile

# Run tests
mvn test

# Package as JAR
mvn package

# Install locally
mvn install

# Deploy artifacts
mvn deploy

# Run specific service
mvn -pl customer-service spring-boot:run

# Skip tests
mvn clean install -DskipTests

# Show dependency tree
mvn dependency:tree

# Find duplicate dependencies
mvn dependency:analyze
```

## Performance Tips

1. **Disable Spring Boot DevTools in production**
   - It's included in parent POM as optional
   - Removed during package phase

2. **Use connection pooling**
   - HikariCP is default with Spring Boot

3. **Enable caching**
   - Add Spring Cache abstraction
   - Configure Redis for distributed cache

4. **Optimize queries**
   - Use appropriate Feign client timeouts
   - Implement circuit breakers

## Documentation

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Maven Documentation](https://maven.apache.org)

## Support

For issues or questions:
1. Check logs in service console
2. Review the PROJECT_README.md
3. Check individual service configuration files
4. Review Spring Cloud documentation

