# Deployment, Monitoring & DevOps Guide

## Docker Deployment

### Dockerfile for Each Service

```dockerfile
# Multi-stage build for Order Service (applicable to all services)
# Dockerfile.order-service

FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY order-service/pom.xml .
RUN mvn dependency:go-offline -B

COPY order-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/order-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose Setup

**docker-compose.yml**

```yaml
version: '3.8'

services:
  # PostgreSQL Databases
  order-db:
    image: postgres:16-alpine
    container_name: order-db
    environment:
      POSTGRES_DB: order_db
      POSTGRES_USER: order_user
      POSTGRES_PASSWORD: order_password
    ports:
      - "5432:5432"
    volumes:
      - order-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U order_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  payment-db:
    image: postgres:16-alpine
    container_name: payment-db
    environment:
      POSTGRES_DB: payment_db
      POSTGRES_USER: payment_user
      POSTGRES_PASSWORD: payment_password
    ports:
      - "5433:5432"
    volumes:
      - payment-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payment_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  customer-db:
    image: postgres:16-alpine
    container_name: customer-db
    environment:
      POSTGRES_DB: customer_db
      POSTGRES_USER: customer_user
      POSTGRES_PASSWORD: customer_password
    ports:
      - "5434:5432"
    volumes:
      - customer-db-data:/var/lib/postgresql/data

  product-db:
    image: postgres:16-alpine
    container_name: product-db
    environment:
      POSTGRES_DB: product_db
      POSTGRES_USER: product_user
      POSTGRES_PASSWORD: product_password
    ports:
      - "5435:5432"
    volumes:
      - product-db-data:/var/lib/postgresql/data

  # Config Server
  config-server:
    build:
      context: .
      dockerfile: Dockerfile.config-server
    container_name: config-server
    ports:
      - "8888:8888"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    networks:
      - microservices-net
    depends_on:
      - order-db
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8888/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Eureka Server
  eureka-server:
    build:
      context: .
      dockerfile: Dockerfile.eureka-server
    container_name: eureka-server
    ports:
      - "8761:8761"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    networks:
      - microservices-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Microservices
  order-service:
    build:
      context: .
      dockerfile: Dockerfile.order-service
    container_name: order-service
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://order-db:5432/order_db
      SPRING_DATASOURCE_USERNAME: order_user
      SPRING_DATASOURCE_PASSWORD: order_password
    networks:
      - microservices-net
    depends_on:
      config-server:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      order-db:
        condition: service_healthy

  payment-service:
    build:
      context: .
      dockerfile: Dockerfile.payment-service
    container_name: payment-service
    ports:
      - "8082:8082"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://payment-db:5432/payment_db
      SPRING_DATASOURCE_USERNAME: payment_user
      SPRING_DATASOURCE_PASSWORD: payment_password
    networks:
      - microservices-net
    depends_on:
      config-server:
        condition: service_healthy
      eureka-server:
        condition: service_healthy

  customer-service:
    build:
      context: .
      dockerfile: Dockerfile.customer-service
    container_name: customer-service
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://customer-db:5432/customer_db
      SPRING_DATASOURCE_USERNAME: customer_user
      SPRING_DATASOURCE_PASSWORD: customer_password
    networks:
      - microservices-net
    depends_on:
      config-server:
        condition: service_healthy
      eureka-server:
        condition: service_healthy

  product-service:
    build:
      context: .
      dockerfile: Dockerfile.product-service
    container_name: product-service
    ports:
      - "8084:8084"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://product-db:5432/product_db
      SPRING_DATASOURCE_USERNAME: product_user
      SPRING_DATASOURCE_PASSWORD: product_password
    networks:
      - microservices-net
    depends_on:
      config-server:
        condition: service_healthy
      eureka-server:
        condition: service_healthy

  # API Gateway
  api-gateway:
    build:
      context: .
      dockerfile: Dockerfile.api-gateway
    container_name: api-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    networks:
      - microservices-net
    depends_on:
      - eureka-server
      - order-service
      - payment-service
      - customer-service
      - product-service

volumes:
  order-db-data:
  payment-db-data:
  customer-db-data:
  product-db-data:

networks:
  microservices-net:
    driver: bridge
```

### Running with Docker Compose

```bash
# Build all services
docker-compose build

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Remove volumes as well
docker-compose down -v
```

---

## Kubernetes Deployment

### ConfigMap for Centralized Configuration

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
  namespace: ecommerce
data:
  application-k8s.yml: |
    spring:
      datasource:
        hikari:
          maximum-pool-size: 10
          minimum-idle: 2
      jpa:
        hibernate:
          ddl-auto: validate
    server:
      port: 8081
    eureka:
      client:
        serviceUrl:
          defaultZone: http://eureka-server.ecommerce.svc.cluster.local:8761/eureka/
      instance:
        prefer-ip-address: true
```

### Namespace Creation

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce
```

### PostgreSQL StatefulSet

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: ecommerce
spec:
  ports:
    - port: 5432
  clusterIP: None
  selector:
    app: postgres
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: ecommerce
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:16-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: ecommerce_db
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: username
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: password
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: postgres-storage
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 10Gi
```

### Eureka Server Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: eureka-server
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: eureka-server
  template:
    metadata:
      labels:
        app: eureka-server
    spec:
      containers:
      - name: eureka-server
        image: ecommerce/eureka-server:latest
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8761
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: k8s
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8761
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8761
          initialDelaySeconds: 20
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: eureka-server
  namespace: ecommerce
spec:
  type: ClusterIP
  selector:
    app: eureka-server
  ports:
  - port: 8761
    targetPort: 8761
```

### Microservice Deployment (Order Service Example)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ecommerce
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        version: v1
    spec:
      containers:
      - name: order-service
        image: ecommerce/order-service:latest
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: k8s
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: http://eureka-server.ecommerce.svc.cluster.local:8761/eureka/
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: order-service-config
              key: datasource-url
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: order-db-secret
              key: username
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: order-db-secret
              key: password
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ecommerce
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
  - port: 8081
    targetPort: 8081
```

### API Gateway Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-gateway-ingress
  namespace: ecommerce
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - api.ecommerce.example.com
    secretName: api-tls
  rules:
  - host: api.ecommerce.example.com
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 8080
```

---

## Monitoring & Observability

### Prometheus Configuration

```yaml
# prometheus-config.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'order-service'
    static_configs:
      - targets: ['order-service:8081']
    metrics_path: '/actuator/prometheus'

  - job_name: 'payment-service'
    static_configs:
      - targets: ['payment-service:8082']
    metrics_path: '/actuator/prometheus'

  - job_name: 'customer-service'
    static_configs:
      - targets: ['customer-service:8083']
    metrics_path: '/actuator/prometheus'

  - job_name: 'product-service'
    static_configs:
      - targets: ['product-service:8084']
    metrics_path: '/actuator/prometheus'

  - job_name: 'api-gateway'
    static_configs:
      - targets: ['api-gateway:8080']
    metrics_path: '/actuator/prometheus'
```

### Grafana Dashboard Configuration

Create dashboards for:
- Request latency per service
- Error rates and types
- CPU and memory usage
- Database connection pool status
- Eureka service availability
- Payment transaction success rates

### Centralized Logging with ELK Stack

```yaml
# Add to each service's pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-logging</artifactId>
</dependency>

<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.2</version>
</dependency>
```

**logback-spring.xml** (in resources folder)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

### Health Checks Configuration

**application.yml** (in all services)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

---

## CI/CD Pipeline (GitHub Actions)

**.github/workflows/build-and-deploy.yml**

```yaml
name: Build and Deploy

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Build with Maven
      run: mvn -B clean package -DskipTests
    
    - name: Run Tests
      run: mvn test
    
    - name: Build Docker Images
      run: |
        docker build -t ecommerce/order-service:${{ github.sha }} -f Dockerfile.order-service .
        docker build -t ecommerce/payment-service:${{ github.sha }} -f Dockerfile.payment-service .
        docker build -t ecommerce/customer-service:${{ github.sha }} -f Dockerfile.customer-service .
        docker build -t ecommerce/product-service:${{ github.sha }} -f Dockerfile.product-service .
        docker build -t ecommerce/api-gateway:${{ github.sha }} -f Dockerfile.api-gateway .
    
    - name: Push to Registry
      if: github.ref == 'refs/heads/main'
      run: |
        echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
        docker push ecommerce/order-service:${{ github.sha }}
        docker push ecommerce/payment-service:${{ github.sha }}
        # ... push other services
    
    - name: Deploy to Kubernetes
      if: github.ref == 'refs/heads/main'
      run: |
        kubectl set image deployment/order-service order-service=ecommerce/order-service:${{ github.sha }}
        kubectl set image deployment/payment-service payment-service=ecommerce/payment-service:${{ github.sha }}
        # ... update other deployments
        kubectl rollout status deployment/order-service
```

---

## Performance Optimization Tips

### 1. Database Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
```

### 2. Caching

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "products",
            "customers",
            "inventory"
        );
    }
}

@Service
public class ProductService {
    
    @Cacheable(value = "products", key = "#productId")
    public ProductDTO getProduct(Long productId) {
        // ...
    }
    
    @CacheEvict(value = "products", key = "#product.id")
    public void updateProduct(ProductDTO product) {
        // ...
    }
}
```

### 3. Async Processing

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}

@Service
public class EmailService {
    
    @Async("taskExecutor")
    public void sendOrderConfirmationEmail(Order order) {
        // Send email asynchronously
    }
}
```

---

## Troubleshooting Guide

### Common Issues and Solutions

1. **Service Registration Failures**
   - Check Eureka server is running
   - Verify `eureka.client.service-url.defaultZone` URL
   - Check network connectivity between services

2. **Configuration Server Fetch Fails**
   - Verify Config Server is running on port 8888
   - Check Git repository URL and credentials
   - Review bootstrap.yml configuration

3. **Database Connection Errors**
   - Verify database is running and accessible
   - Check connection URL, username, password
   - Verify correct port number in connection string

4. **Payment Processing Timeout**
   - Check timeout configuration in Feign clients
   - Verify Payment Service is responding
   - Check network latency and firewall rules

5. **Inventory Reservation Lock Contention**
   - Add database indexes on frequently queried columns
   - Implement optimistic locking with @Version
   - Consider queue-based inventory management

---

## Backup and Recovery

### Database Backups

```bash
# Backup PostgreSQL
docker exec order-db pg_dump -U order_user order_db > order_db_backup.sql

# Restore PostgreSQL
docker exec -i order-db psql -U order_user order_db < order_db_backup.sql

# Kubernetes backup
kubectl get all -n ecommerce -o yaml > ecommerce-backup.yaml

# Restore from backup
kubectl apply -f ecommerce-backup.yaml
```

This comprehensive guide covers:
- Docker and Docker Compose deployment
- Kubernetes manifests and configuration
- Prometheus and Grafana monitoring
- ELK stack centralized logging
- CI/CD pipelines
- Performance optimization
- Troubleshooting common issues
- Backup and recovery procedures

