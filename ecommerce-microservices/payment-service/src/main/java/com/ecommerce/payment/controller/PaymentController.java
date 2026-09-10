package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentDTO;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentService paymentService;
    
    @PostMapping
    public ResponseEntity<Object> processPayment(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @RequestBody PaymentDTO dto) {
        PaymentDTO payment = paymentService.processPayment(dto, idempotencyKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if ("COMPLETED".equals(payment.getStatus())) {
            return ResponseEntity.ok(payment);
        }

        Map<String, Object> err = Map.of(
                "paymentId", payment.getId(),
                "status", "FAILED",
                "error", "CARD_DECLINED",
                "message", payment.getFailureReason() != null ? payment.getFailureReason() : "Payment failed"
        );
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(err);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDTO> getPayment(@PathVariable Long id) {
        PaymentDTO payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(payment);
    }
    
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentDTO> getPaymentByOrder(@PathVariable Long orderId) {
        PaymentDTO payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(payment);
    }
    
    @GetMapping
    public ResponseEntity<List<PaymentDTO>> getAllPayments() {
        List<PaymentDTO> payments = paymentService.getAllPayments();
        return ResponseEntity.ok(payments);
    }
    
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PaymentDTO>> getPaymentsByStatus(@PathVariable String status) {
        List<PaymentDTO> payments = paymentService.getPaymentsByStatus(status);
        return ResponseEntity.ok(payments);
    }
    
    @PostMapping("/{id}/refund")
    public ResponseEntity<Map<String, Object>> refundPayment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        // amount optional
        Object amountObj = body.get("amount");
        BigDecimal amount = null;
        if (amountObj instanceof Number) {
            amount = BigDecimal.valueOf(((Number) amountObj).doubleValue());
        }
        var payment = paymentService.refundPayment(id, reason);
        Map<String, Object> resp = Map.of(
                "paymentId", payment.getId(),
                "status", payment.getStatus(),
                "message", "REFUND_PROCESSED"
        );
        return ResponseEntity.ok(resp);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }
    
}

