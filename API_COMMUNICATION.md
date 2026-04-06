# Microservices API Communication Flow

## Detailed Communication Flows

### 1. Order Creation Complete Flow

```
CLIENT REQUEST:
┌────────────────────────────────────┐
│ POST /api/orders                   │
│ {                                  │
│   "customerId": 1,                │
│   "items": [                       │
│     {                              │
│       "productId": 101,           │
│       "quantity": 2               │
│     }                              │
│   ],                               │
│   "paymentMethod": "CREDIT_CARD"  │
│ }                                  │
└────────────────────────────────────┘
           │
           ▼
┌──────────────────────────┐
│   API GATEWAY (8080)     │
│  Routes to order-service │
└────────────────┬─────────┘
                 │
                 ▼
    ┌────────────────────────┐
    │  ORDER SERVICE (8081)  │
    └────────────┬───────────┘
                 │
                 ├─── [1] Validate Customer
                 │    │
                 │    └─► Customer Service (8083)
                 │        GET /api/customers/1
                 │        Response: 200 {customerId, email, name}
                 │        Failure: 404 Not Found
                 │
                 ├─── [2] Check & Reserve Inventory
                 │    │
                 │    └─► Product Service (8084)
                 │        GET /api/products/101/inventory
                 │        Response: 200 {available: 5}
                 │        
                 │        PUT /api/products/101/inventory/reserve
                 │        Body: {quantity: 2}
                 │        Response: 200 {reserved: 2}
                 │        Failure: 409 Conflict (insufficient stock)
                 │
                 ├─── [3] Calculate Order Total
                 │    │
                 │    └─► Product Service (8084)
                 │        GET /api/products/101
                 │        Response: 200 {price: 29.99}
                 │        Total: 2 * 29.99 = 59.98
                 │
                 ├─── [4] Process Payment
                 │    │
                 │    └─► Payment Service (8082)
                 │        POST /api/payments
                 │        {
                 │          "orderId": "temp-order-123",
                 │          "amount": 59.98,
                 │          "customerId": 1,
                 │          "paymentMethod": "CREDIT_CARD"
                 │        }
                 │        Response: 200 {paymentId: 501, status: "COMPLETED"}
                 │        Failure: 402 Payment Required
                 │
                 └─── [5] Create Order Record
                      │
                      └─► Order Service Database
                          INSERT INTO orders (
                            customer_id, status, total_amount
                          ) VALUES (1, 'CONFIRMED', 59.98)
                          
                          INSERT INTO order_items (
                            order_id, product_id, quantity, unit_price
                          ) VALUES (201, 101, 2, 29.99)

SUCCESS RESPONSE:
┌─────────────────────────────────┐
│ 200 OK                           │
│ {                               │
│   "orderId": 201,              │
│   "customerId": 1,             │
│   "status": "CONFIRMED",       │
│   "totalAmount": 59.98,        │
│   "items": [                   │
│     {                          │
│       "productId": 101,        │
│       "quantity": 2,           │
│       "unitPrice": 29.99       │
│     }                          │
│   ],                           │
│   "paymentId": 501,            │
│   "createdAt": "2024-04-06..." │
│ }                              │
└─────────────────────────────────┘
```

### 2. Order Failure Scenarios (Compensation)

#### Scenario A: Customer Not Found
```
Order Service
  │
  ├─► Customer Service: GET /api/customers/999
  │   Response: 404 Not Found
  │
  └─► Order Service Response: 400 Bad Request
      {
        "error": "CUSTOMER_NOT_FOUND",
        "message": "Customer with ID 999 not found"
      }
```

#### Scenario B: Insufficient Inventory
```
Order Service
  │
  ├─► [✓] Customer validated
  │
  ├─► Product Service: PUT /api/products/101/inventory/reserve
  │   Response: 409 Conflict
  │   {
  │     "error": "INSUFFICIENT_STOCK",
  │     "requested": 2,
  │     "available": 1
  │   }
  │
  └─► Order Service Response: 409 Conflict
      {
        "error": "INSUFFICIENT_INVENTORY",
        "message": "Requested quantity exceeds available stock"
      }
```

#### Scenario C: Payment Failed
```
Order Service
  │
  ├─► [✓] Customer validated
  ├─► [✓] Inventory reserved
  ├─► [✓] Order total calculated
  │
  ├─► Payment Service: POST /api/payments
  │   Response: 402 Payment Required
  │   {
  │     "error": "PAYMENT_DECLINED",
  │     "paymentId": 502,
  │     "reason": "Card declined"
  │   }
  │
  ├─► COMPENSATION: Release Reserved Inventory
  │   Product Service: PUT /api/products/101/inventory/release
  │   Body: {quantity: 2}
  │
  └─► Order Service Response: 402 Payment Required
      {
        "error": "PAYMENT_FAILED",
        "message": "Payment processing failed: Card declined"
      }
```

---

## Sequence Diagrams

### Order Creation Sequence

```
Client       API Gateway    Order Svc    Customer Svc    Product Svc    Payment Svc
  │               │              │             │               │              │
  │─POST /orders─►│              │             │               │              │
  │               │─request─────►│             │               │              │
  │               │              │             │               │              │
  │               │              │─GET /cust─►│               │              │
  │               │              │            │───validate────►│              │
  │               │              │◄───200────│               │              │
  │               │              │             │               │              │
  │               │              │─reserve inventory──────────►│              │
  │               │              │                            │──check qty──►│
  │               │              │                            │◄──200 OK────│
  │               │              │                            │──reserve────►│
  │               │              │                            │◄──200 OK────│
  │               │              │             │               │              │
  │               │              │─POST /payments────────────────────────────►│
  │               │              │                                           │
  │               │              │             │               │         process
  │               │              │             │               │              │
  │               │              │◄──paymentId,status,COMPLETED──────────────│
  │               │              │             │               │              │
  │               │              │─save order─────────────────────────────────│
  │               │              │   (in local DB)            │              │
  │               │              │             │               │              │
  │               │◄────200 OK───│             │               │              │
  │◄──200 OK──────│              │             │               │              │
  │   +orderId                   │             │               │              │
  │

Legend:
───► Synchronous Call
◄─── Response
```

### Payment Processing with Refund

```
Payment Service   Order Service   Bank/Payment Processor
      │                │                    │
      │                │                    │
      │◄──refund req───│                    │
      │                │                    │
      ├─read payment───┼────────────────────┤
      │    (amount,    │                    │
      │    transaction)│                    │
      │                │                    │
      ├─POST /refund─────────────────────────►
      │    (txn_id,    │                    │
      │    amount)     │                    │
      │                │                    │
      │                │◄────refund_id─────│
      │                │    status=PENDING  │
      │                │                    │
      │ [Async notification after processor confirmation]
      │                │                    │
      │ Event: refund_completed             │
      │    └─► publish event                │
      │    └─► Order Service receives       │
      │        (updates order to            │
      │         REFUNDED status)            │
      │                │                    │
```

---

## API Endpoint Details

### Order Service Endpoints

#### POST /api/orders
```
Request:
{
  "customerId": 1,
  "items": [
    {
      "productId": 101,
      "quantity": 2
    }
  ],
  "paymentMethod": "CREDIT_CARD",
  "shippingAddressId": 5
}

Response (201 Created):
{
  "orderId": 201,
  "customerId": 1,
  "status": "CONFIRMED",
  "items": [
    {
      "productId": 101,
      "productName": "Laptop",
      "quantity": 2,
      "unitPrice": 999.99,
      "subtotal": 1999.98
    }
  ],
  "subtotal": 1999.98,
  "tax": 159.99,
  "totalAmount": 2159.97,
  "paymentId": 501,
  "paymentStatus": "COMPLETED",
  "shippingAddressId": 5,
  "createdAt": "2024-04-06T10:30:00Z",
  "updatedAt": "2024-04-06T10:30:00Z"
}

Error Responses:
400: Invalid customer or product IDs
402: Payment declined
409: Insufficient inventory
503: Service unavailable (downstream service down)
```

#### GET /api/orders/{orderId}
```
Response (200 OK):
{
  "orderId": 201,
  "customerId": 1,
  "status": "CONFIRMED",
  "items": [...],
  "totalAmount": 2159.97,
  "paymentId": 501,
  "paymentStatus": "COMPLETED",
  "trackingNumber": "TRK123456789",
  "estimatedDelivery": "2024-04-10",
  "createdAt": "2024-04-06T10:30:00Z",
  "updatedAt": "2024-04-06T10:30:00Z"
}
```

### Payment Service Endpoints

#### POST /api/payments
```
Request:
{
  "orderId": 201,
  "amount": 2159.97,
  "customerId": 1,
  "paymentMethod": "CREDIT_CARD",
  "cardDetails": {
    "cardNumber": "****1234",
    "expiryMonth": 12,
    "expiryYear": 2025,
    "cvv": "***"
  }
}

Response (200 OK):
{
  "paymentId": 501,
  "orderId": 201,
  "amount": 2159.97,
  "status": "COMPLETED",
  "paymentMethod": "CREDIT_CARD",
  "transactionId": "txn_1234567890",
  "processor": "Stripe",
  "timestamp": "2024-04-06T10:30:15Z"
}

Response (402 Payment Required):
{
  "paymentId": 502,
  "orderId": 201,
  "amount": 2159.97,
  "status": "FAILED",
  "error": "CARD_DECLINED",
  "message": "Your card was declined",
  "timestamp": "2024-04-06T10:30:20Z"
}
```

#### POST /api/payments/{paymentId}/refund
```
Request:
{
  "reason": "Customer request",
  "amount": 2159.97  # Optional, full refund if not provided
}

Response (200 OK):
{
  "paymentId": 501,
  "refundId": 1001,
  "originalAmount": 2159.97,
  "refundAmount": 2159.97,
  "status": "PROCESSING",
  "reason": "Customer request",
  "timestamp": "2024-04-06T11:00:00Z"
}
```

### Customer Service Endpoints

#### POST /api/customers
```
Request:
{
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1-555-123-4567",
  "password": "securePassword123"
}

Response (201 Created):
{
  "customerId": 1,
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1-555-123-4567",
  "createdAt": "2024-04-06T09:00:00Z"
}
```

#### GET /api/customers/{customerId}
```
Response (200 OK):
{
  "customerId": 1,
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1-555-123-4567",
  "registrationDate": "2024-04-06T09:00:00Z",
  "totalOrders": 5,
  "totalSpent": 10599.85
}
```

#### POST /api/customers/{customerId}/addresses
```
Request:
{
  "street": "123 Main Street",
  "city": "San Francisco",
  "state": "CA",
  "zip": "94102",
  "country": "USA",
  "isDefault": true
}

Response (201 Created):
{
  "addressId": 5,
  "customerId": 1,
  "street": "123 Main Street",
  "city": "San Francisco",
  "state": "CA",
  "zip": "94102",
  "country": "USA",
  "isDefault": true,
  "createdAt": "2024-04-06T09:30:00Z"
}
```

### Product Service Endpoints

#### GET /api/products
```
Query Parameters:
- page: 0
- size: 20
- category: "Electronics"
- sortBy: "price"
- order: "asc"

Response (200 OK):
{
  "content": [
    {
      "productId": 101,
      "name": "Laptop",
      "description": "High-performance laptop",
      "price": 999.99,
      "category": "Electronics",
      "available": 5,
      "rating": 4.5,
      "reviews": 128
    },
    {
      "productId": 102,
      "name": "Mouse",
      "description": "Wireless mouse",
      "price": 29.99,
      "category": "Accessories",
      "available": 150,
      "rating": 4.2,
      "reviews": 45
    }
  ],
  "totalElements": 1000,
  "totalPages": 50,
  "currentPage": 0,
  "pageSize": 20
}
```

#### GET /api/products/{productId}
```
Response (200 OK):
{
  "productId": 101,
  "name": "Laptop",
  "description": "High-performance laptop with 16GB RAM",
  "price": 999.99,
  "category": "Electronics",
  "inventory": {
    "quantity": 10,
    "reserved": 5,
    "available": 5
  },
  "specifications": {
    "cpu": "Intel i7",
    "ram": "16GB",
    "storage": "512GB SSD"
  },
  "images": ["url1", "url2"],
  "rating": 4.5,
  "reviews": 128,
  "createdAt": "2023-01-15T00:00:00Z",
  "updatedAt": "2024-04-06T10:00:00Z"
}
```

#### GET /api/products/{productId}/inventory
```
Response (200 OK):
{
  "productId": 101,
  "quantity": 10,
  "reserved": 5,
  "available": 5,
  "status": "IN_STOCK",
  "lastUpdated": "2024-04-06T10:15:00Z"
}
```

#### PUT /api/products/{productId}/inventory/reserve
```
Request:
{
  "quantity": 2,
  "orderId": 201,
  "reason": "ORDER_PLACEMENT"
}

Response (200 OK):
{
  "productId": 101,
  "quantity": 10,
  "reserved": 7,  # Was 5, now +2
  "available": 3,
  "reservationId": "res_12345",
  "expiresAt": "2024-04-13T10:15:00Z"
}

Error Response (409 Conflict):
{
  "error": "INSUFFICIENT_STOCK",
  "productId": 101,
  "requested": 10,
  "available": 3
}
```

---

## Error Handling Strategy

### Standard Error Response Format

```json
{
  "timestamp": "2024-04-06T10:30:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Invalid customer ID",
  "path": "/api/orders",
  "traceId": "abc123def456"  # For distributed tracing
}
```

### HTTP Status Codes Used

| Code | Scenario |
|------|----------|
| 200 | Successful GET/PUT |
| 201 | Successful POST (resource created) |
| 202 | Accepted (async processing) |
| 400 | Bad request (validation error) |
| 402 | Payment required (payment failed) |
| 404 | Resource not found |
| 409 | Conflict (inventory issue) |
| 500 | Internal server error |
| 502 | Bad gateway (downstream service error) |
| 503 | Service unavailable |

---

## Feign Client Examples

### Order Service → Payment Service

```java
@FeignClient(
  name = "payment-service",
  url = "${payment.service.url:http://localhost:8082}",
  fallback = PaymentServiceFallback.class
)
public interface PaymentClient {
  
  @PostMapping("/api/payments")
  PaymentResponse processPayment(@RequestBody PaymentRequest request);
  
  @GetMapping("/api/payments/{paymentId}")
  PaymentResponse getPaymentStatus(@PathVariable Long paymentId);
  
  @PostMapping("/api/payments/{paymentId}/refund")
  RefundResponse refundPayment(
    @PathVariable Long paymentId,
    @RequestBody RefundRequest request
  );
}
```

### Order Service → Product Service

```java
@FeignClient(
  name = "product-service",
  url = "${product.service.url:http://localhost:8084}",
  fallback = ProductServiceFallback.class
)
public interface ProductClient {
  
  @GetMapping("/api/products/{productId}")
  ProductResponse getProduct(@PathVariable Long productId);
  
  @GetMapping("/api/products/{productId}/inventory")
  InventoryResponse getInventory(@PathVariable Long productId);
  
  @PutMapping("/api/products/{productId}/inventory/reserve")
  InventoryResponse reserveInventory(
    @PathVariable Long productId,
    @RequestBody ReservationRequest request
  );
  
  @PutMapping("/api/products/{productId}/inventory/release")
  InventoryResponse releaseInventory(
    @PathVariable Long productId,
    @RequestBody ReleaseRequest request
  );
}
```

---

## Testing Inter-Service Communication

### Using REST Client / Curl

```bash
# Create Order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [
      {
        "productId": 101,
        "quantity": 2
      }
    ],
    "paymentMethod": "CREDIT_CARD"
  }'

# Get Order Status
curl http://localhost:8080/api/orders/201

# Create Customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1-555-123-4567"
  }'

# Get Products
curl http://localhost:8080/api/products?page=0&size=10

# Check Product Inventory
curl http://localhost:8080/api/products/101/inventory
```

