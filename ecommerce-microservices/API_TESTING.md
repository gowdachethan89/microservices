# API Testing Guide

## Test All Services

Before running tests, ensure all services are started:

1. Discovery Server (8761)
2. Config Server (8888)
3. API Gateway (8080)
4. Customer Service (8083)
5. Product Service (8084)
6. Order Service (8081)
7. Payment Service (8082)

## Customer Service Tests

### Create Customer
```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "firstName": "Alice",
    "lastName": "Johnson",
    "phone": "+1-555-1001"
  }'
```

**Expected Response**: 201 Created
```json
{
  "id": 1,
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Johnson",
  "phone": "+1-555-1001",
  "createdAt": 1712390000000,
  "updatedAt": 1712390000000
}
```

### Get All Customers
```bash
curl http://localhost:8080/api/customers
```

**Expected Response**: 200 OK with list of customers

### Get Customer by ID
```bash
curl http://localhost:8080/api/customers/1
```

**Expected Response**: 200 OK with customer details

### Update Customer
```bash
curl -X PUT http://localhost:8080/api/customers/1 \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice.updated@example.com",
    "firstName": "Alice",
    "lastName": "Johnson",
    "phone": "+1-555-1002"
  }'
```

**Expected Response**: 200 OK with updated customer

### Delete Customer
```bash
curl -X DELETE http://localhost:8080/api/customers/1
```

**Expected Response**: 204 No Content

---

## Product Service Tests

### Create Product
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro",
    "description": "High-performance laptop with 16GB RAM",
    "price": 1299.99,
    "quantity": 50,
    "category": "Electronics"
  }'
```

**Expected Response**: 201 Created
```json
{
  "id": 1,
  "name": "Laptop Pro",
  "description": "High-performance laptop with 16GB RAM",
  "price": 1299.99,
  "quantity": 50,
  "category": "Electronics",
  "createdAt": 1712390100000,
  "updatedAt": 1712390100000
}
```

### Get All Products
```bash
curl http://localhost:8080/api/products
```

### Get Product by ID
```bash
curl http://localhost:8080/api/products/1
```

### Get Products by Category
```bash
curl http://localhost:8080/api/products/category/Electronics
```

### Search Products
```bash
curl "http://localhost:8080/api/products/search?query=Laptop"
```

### Update Product
```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro Max",
    "description": "Ultra high-performance laptop with 32GB RAM",
    "price": 1499.99,
    "quantity": 30,
    "category": "Electronics"
  }'
```

### Delete Product
```bash
curl -X DELETE http://localhost:8080/api/products/1
```

---

## Order Service Tests

### Create Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "totalAmount": 1299.99
  }'
```

**Expected Response**: 201 Created
```json
{
  "id": 1,
  "customerId": 1,
  "status": "PENDING",
  "totalAmount": 1299.99,
  "paymentId": null,
  "paymentStatus": null,
  "createdAt": 1712390200000,
  "updatedAt": 1712390200000
}
```

### Get All Orders
```bash
curl http://localhost:8080/api/orders
```

### Get Order by ID
```bash
curl http://localhost:8080/api/orders/1
```

### Get Orders by Customer ID
```bash
curl http://localhost:8080/api/orders/customer/1
```

### Get Orders by Status
```bash
curl http://localhost:8080/api/orders/status/PENDING
```

### Update Order Status
```bash
curl -X PUT "http://localhost:8080/api/orders/1/status?status=CONFIRMED"
```

### Update Payment Info
```bash
curl -X PUT "http://localhost:8080/api/orders/1/payment?paymentId=1&paymentStatus=COMPLETED"
```

### Delete Order
```bash
curl -X DELETE http://localhost:8080/api/orders/1
```

---

## Payment Service Tests

### Process Payment
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 1,
    "amount": 1299.99,
    "paymentMethod": "CREDIT_CARD"
  }'
```

**Expected Response**: 201 Created
```json
{
  "id": 1,
  "orderId": 1,
  "amount": 1299.99,
  "status": "COMPLETED",
  "paymentMethod": "CREDIT_CARD",
  "transactionId": "TXN-1712390300000",
  "failureReason": null,
  "createdAt": 1712390300000,
  "updatedAt": 1712390300000
}
```

### Get All Payments
```bash
curl http://localhost:8080/api/payments
```

### Get Payment by ID
```bash
curl http://localhost:8080/api/payments/1
```

### Get Payment by Order ID
```bash
curl http://localhost:8080/api/payments/order/1
```

### Get Payments by Status
```bash
curl http://localhost:8080/api/payments/status/COMPLETED
```

### Refund Payment
```bash
curl -X POST "http://localhost:8080/api/payments/1/refund?reason=Customer%20requested%20refund"
```

### Delete Payment
```bash
curl -X DELETE http://localhost:8080/api/payments/1
```

---

## Complete End-to-End Flow Test

### 1. Create Customer
```bash
CUSTOMER_ID=$(curl -s -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "bob@example.com",
    "firstName": "Bob",
    "lastName": "Smith",
    "phone": "+1-555-2001"
  }' | jq -r '.id')
```

### 2. Create Product
```bash
PRODUCT_ID=$(curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mouse Wireless",
    "description": "Ergonomic wireless mouse",
    "price": 29.99,
    "quantity": 100,
    "category": "Accessories"
  }' | jq -r '.id')
```

### 3. Create Order
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": $CUSTOMER_ID,
    \"totalAmount\": 29.99
  }" | jq -r '.id')
```

### 4. Process Payment
```bash
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d "{
    \"orderId\": $ORDER_ID,
    \"amount\": 29.99,
    \"paymentMethod\": \"CREDIT_CARD\"
  }" | jq -r '.id')
```

### 5. Update Order with Payment Info
```bash
curl -X PUT "http://localhost:8080/api/orders/$ORDER_ID/payment?paymentId=$PAYMENT_ID&paymentStatus=COMPLETED"
```

### 6. Verify All Steps
```bash
echo "Customer: $CUSTOMER_ID"
echo "Product: $PRODUCT_ID"
echo "Order: $ORDER_ID"
echo "Payment: $PAYMENT_ID"

# Fetch final state
curl http://localhost:8080/api/customers/$CUSTOMER_ID
curl http://localhost:8080/api/products/$PRODUCT_ID
curl http://localhost:8080/api/orders/$ORDER_ID
curl http://localhost:8080/api/payments/$PAYMENT_ID
```

---

## Using Postman

### Import Collection Template

```json
{
  "info": {
    "name": "E-Commerce Microservices",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Customer",
      "item": [
        {
          "name": "Create Customer",
          "request": {
            "method": "POST",
            "url": "http://localhost:8080/api/customers",
            "header": [
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "raw": "{\"email\": \"test@example.com\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"phone\": \"+1-555-1234\"}"
            }
          }
        }
      ]
    }
  ]
}
```

---

## Common Response Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Request successful |
| 201 | Created - Resource created successfully |
| 204 | No Content - Request successful, no content |
| 400 | Bad Request - Invalid request |
| 404 | Not Found - Resource not found |
| 500 | Internal Server Error |

---

## Troubleshooting

### Service Not Found (404)
- Verify Eureka server is running
- Check service is registered in Eureka dashboard (http://localhost:8761)
- Verify correct API endpoint path

### Connection Refused
- Verify service is running on correct port
- Check firewall settings
- Verify correct localhost/host name

### Timeout Error
- Service might be slow to respond
- Increase curl timeout: `curl --max-time 30 <url>`
- Check service logs

### Database Errors
- H2 database is in-memory, lost on restart
- Add seed data on startup if needed
- Check database connection URL in application.yml

