package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentDTO;
import com.ecommerce.payment.dto.RefundRequest;
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
        if ("COMPLETED".equalsIgnoreCase(payment.getStatus())) {
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

    @PostMapping("/{id}/refund")
    public ResponseEntity<Map<String, Object>> refundPayment(@PathVariable Long id,
                                                             @RequestBody RefundRequest request) {
        String reason = request != null && request.getReason() != null ? request.getReason() : "Customer request";
        BigDecimal amount = request != null && request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;

        PaymentDTO payment = paymentService.refundPayment(id, reason, amount);

        Map<String, Object> resp = Map.of(
                "refundId", payment.getId(),
                "paymentId", payment.getId(),
                "status", payment.getStatus(),
                "refundAmount", payment.getAmount()
        );
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDTO> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentDTO> getPaymentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentDTO>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<PaymentDTO>> getPaymentsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(paymentService.getPaymentsByStatus(status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }
}