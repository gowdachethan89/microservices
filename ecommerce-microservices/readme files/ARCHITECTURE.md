# E-Commerce Microservices Architecture

## High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT APPLICATIONS                              │
│                         (Web, Mobile, Desktop)                                │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │                             │
                    ▼                             ▼
        ┌─────────────────────────┐  ┌──────────────────────┐
        │     API GATEWAY         │  │ CONFIG SERVER        │
        │ (Spring Cloud Gateway)  │  │ (Git-backed Config)  │
        │  - Route requests       │  │ - Centralized config │
        │  - Load balancing       │  │ - Dev/Prod profiles  │
        │  - Rate limiting        │  └──────────────────────┘
        └──────────┬──────────────┘           ▲
                   │                          │
      ┌────────────┼────────────┬─────────────┼──────────┐
      │            │            │             │          │
      ▼            ▼            ▼             ▼          ▼
   ┌──────┐   ┌──────┐    ┌──────┐    ┌──────┐    ┌──────┐
   │Order │   │Payment│   │Cust. │    │Product│   │Eureka│
   │Srvc  │   │ Srvc  │   │Srvc  │    │ Srvc  │   │Server│
   └──────┘   └──────┘    └──────┘    └──────┘   └──────┘
      ▲            ▲            ▲             ▲
      │            │            │             │
      └────────────┴────────────┴─────────────┘
              (REST APIs)

      ┌──────┐   ┌──────┐    ┌──────┐    ┌──────┐
      │Orders│   │Payment│   │Cust. │    │Product│
      │  DB  │   │  DB   │   │ DB   │    │  DB   │
      └──────┘   └──────┘    └──────┘    └──────┘
   (PostgreSQL) (PostgreSQL) (PostgreSQL) (PostgreSQL)
```

## System Components

### 1. **API Gateway** (Spring Cloud Gateway)
- **Purpose**: Entry point for all client requests
- **Responsibilities**:
  - Route requests to appropriate microservices
  - Load balancing across service instances
  - Rate limiting and request throttling
  - Request/Response logging
  - JWT validation and authentication
  - CORS handling

### 2. **Service Discovery** (Eureka)
- **Purpose**: Dynamic service registration and discovery
- **Responsibilities**:
  - Services register themselves on startup
  - Maintains health check status
  - Provides service instance information
  - Enables dynamic service scaling

### 3. **Configuration Server** (Spring Cloud Config)
- **Purpose**: Centralized configuration management
- **Responsibilities**:
  - Store configuration in Git repository
  - Provide environment-specific configurations (dev, prod)
  - Real-time configuration updates
  - Version control for configurations

### 4. **Microservices**

#### **Order Service**
- **Port**: 8081
- **Database**: PostgreSQL (orders_db)
- **Responsibilities**:
  - Create and manage customer orders
  - Track order status
  - Calculate order totals
  - Interact with Payment Service for payment processing
  - Interact with Customer Service for customer details
  - Interact with Product Service for inventory management

#### **Payment Service**
- **Port**: 8082
- **Database**: PostgreSQL (payment_db)
- **Responsibilities**:
  - Process payment transactions
  - Track payment status
  - Handle refunds
  - Maintain payment history
  - Validate payment methods

#### **Customer Service**
- **Port**: 8083
- **Database**: PostgreSQL (customer_db)
- **Responsibilities**:
  - Manage customer profiles
  - Handle customer registration and authentication
  - Manage customer addresses
  - Track customer preferences
  - Update customer information

#### **Product Service**
- **Port**: 8084
- **Database**: PostgreSQL (product_db)
- **Responsibilities**:
  - Maintain product catalog
  - Manage inventory levels
  - Handle product pricing
  - Provide product details and search
  - Track product availability

---

## Microservices Responsibilities

### Order Service
```
REST Endpoints:
- POST   /api/orders                    → Create new order
- GET    /api/orders/{orderId}          → Get order details
- GET    /api/orders?customerId={id}    → Get customer's orders
- PUT    /api/orders/{orderId}/status   → Update order status
- DELETE /api/orders/{orderId}          → Cancel order

Dependencies:
- Calls Payment Service to process payment
- Calls Customer Service to validate customer
- Calls Product Service to check inventory
```

### Payment Service
```
REST Endpoints:
- POST   /api/payments                  → Process payment
- GET    /api/payments/{paymentId}      → Get payment details
- GET    /api/payments?orderId={id}     → Get payment for order
- POST   /api/payments/{paymentId}/refund → Refund payment
- GET    /api/payments/status/{orderId} → Get payment status

Database Tables:
- payments (id, order_id, amount, status, method, created_at, updated_at)
- transaction_history (id, payment_id, status_before, status_after, timestamp)
```

### Customer Service
```
REST Endpoints:
- POST   /api/customers                 → Register new customer
- GET    /api/customers/{customerId}    → Get customer profile
- PUT    /api/customers/{customerId}    → Update customer info
- DELETE /api/customers/{customerId}    → Delete customer
- GET    /api/customers/{customerId}/addresses → Get customer addresses
- POST   /api/customers/{customerId}/addresses → Add address
- PUT    /api/customers/{customerId}/addresses/{addressId} → Update address

Database Tables:
- customers (id, email, first_name, last_name, phone, created_at, updated_at)
- addresses (id, customer_id, street, city, state, zip, country, is_default)
```

### Product Service
```
REST Endpoints:
- GET    /api/products                  → List all products
- GET    /api/products/{productId}      → Get product details
- POST   /api/products                  → Create product (admin)
- PUT    /api/products/{productId}      → Update product (admin)
- DELETE /api/products/{productId}      → Delete product (admin)
- GET    /api/products/search?query={q} → Search products
- GET    /api/products/{productId}/inventory → Get stock level
- PUT    /api/products/{productId}/inventory → Update inventory

Database Tables:
- products (id, name, description, price, category, created_at, updated_at)
- inventory (id, product_id, quantity, reserved, available)
- categories (id, name, description)
```

---

## API Communication Flow

### Order Creation Workflow

```
Client Request: POST /api/orders
│
▼
API Gateway (routes to Order Service)
│
▼
Order Service receives request
│
├─► Step 1: Validate Customer
│   └─► Call: GET /api/customers/{customerId}
│       Response: Customer details or 404 error
│
├─► Step 2: Validate Products & Reserve Inventory
│   └─► Call: GET /api/products/{productId}/inventory
│       Response: Available quantity or out of stock
│       Call: PUT /api/products/{productId}/inventory (reserve)
│
├─► Step 3: Calculate Total
│   └─► Aggregate product prices and taxes
│
├─► Step 4: Initiate Payment
│   └─► Call: POST /api/payments
│       Body: { orderId, amount, customerId, paymentMethod }
│       Response: paymentId and status
│
└─► Step 5: Create Order
    ├─► If Payment Success (status=COMPLETED)
    │   └─► Create order in database
    │       Set status=CONFIRMED
    │       Return order details to client
    │
    └─► If Payment Fails (status=FAILED)
        ├─► Release reserved inventory
        │   Call: PUT /api/products/{productId}/inventory (unreserve)
        └─► Return error to client
```

### Data Consistency Considerations

1. **Saga Pattern**: Used for distributed transactions across services
   - Compensating transactions for rollback scenarios
   - Example: If payment fails, order creation is compensated

2. **Event-Driven Communication** (Optional Enhancement)
   - Events published: `OrderCreated`, `PaymentProcessed`, `InventoryReserved`
   - Services subscribe to relevant events
   - Improves decoupling between services

3. **Data Isolation**
   - No direct database access between services
   - All communication through REST APIs only
   - Each service manages its own data

---

## Service Discovery Flow

```
Startup Sequence:
│
├─► Each Service (Order, Payment, Customer, Product)
│   │
│   ├─► Reads configuration from Config Server
│   │   (bootstrap config with Eureka URL)
│   │
│   └─► Registers with Eureka Server
│       POST /eureka/apps/{APP_NAME}
│       Includes: IP, port, health-check URL, metadata
│
├─► API Gateway
│   │
│   ├─► Reads configuration from Config Server
│   │
│   └─► Queries Eureka for service instances
│       GET /eureka/apps/{SERVICE_NAME}
│
└─► Load Balancer (Ribbon/Spring Cloud LoadBalancer)
    ├─► Receives list of available instances
    └─► Routes requests using round-robin or least-active strategy
```

---

## Configuration Management Flow

```
Service Startup:
│
├─► Bootstrap Phase (application-bootstrap.yml)
│   ├─► Eureka Server URL
│   ├─► Config Server URL
│   └─► Application name
│
├─► Fetch from Config Server
│   ├─► Request: GET /config-server/order-service/dev
│   └─► Response: application-dev.yml from Git repo
│
└─► Application Configuration (application.yml + fetched config)
    ├─► Database connection pooling
    ├─► Logging levels
    ├─► API timeouts
    ├─► Feature flags
    └─► Other environment-specific settings
```

---

## Benefits of This Architecture

1. **Scalability**: Each service can scale independently
2. **Resilience**: Failure in one service doesn't cascade
3. **Technology Flexibility**: Each service can use different tech stacks
4. **Ease of Deployment**: Services can be deployed independently
5. **Team Autonomy**: Different teams can work on different services
6. **Centralized Configuration**: Easy management across environments
7. **Service Discovery**: Automatic handling of instance changes
8. **API Gateway**: Single entry point, simplified client integration

---

## Deployment Architecture

```
Development Environment:
├─► Docker Compose
│   ├─► Eureka Server container
│   ├─► Config Server container
│   ├─► API Gateway container
│   ├─► 4 Microservices containers
│   ├─► PostgreSQL container (or 4 separate)
│   └─► Docker network bridge

Production Environment:
├─► Kubernetes Cluster
│   ├─► ConfigMap for centralized config
│   ├─► Service discovery via Kubernetes DNS
│   ├─► StatefulSets for databases
│   ├─► Deployments for microservices
│   ├─► Ingress for API Gateway
│   └─► NetworkPolicy for security
```

---

## Next Steps

1. Create parent Maven project with shared dependencies
2. Set up Config Server with Git repository
3. Create each microservice as Maven module
4. Implement API Gateway routing
5. Configure Eureka Server and Clients
6. Implement REST endpoints for each service
7. Set up Inter-service communication using Feign clients
8. Implement error handling and circuit breakers
9. Add comprehensive logging and monitoring
10. Create Docker and Kubernetes manifests

