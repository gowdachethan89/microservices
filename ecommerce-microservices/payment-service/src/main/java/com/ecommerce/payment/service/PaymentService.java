package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.PaymentDTO;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    
    // Support idempotent processing by idempotencyKey and by orderId
    public PaymentDTO processPayment(PaymentDTO dto, String idempotencyKey) {
        log.info("Processing payment for order: {} (idempotencyKey={})", dto.getOrderId(), idempotencyKey);
        // If idempotency key provided, return existing if present
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing payment for idempotencyKey={}", idempotencyKey);
                return mapToDTO(existing.get());
            }
        }
        // If a payment already exists for this orderId, handle it based on status and idempotency.
        var byOrder = paymentRepository.findByOrderId(dto.getOrderId());
        if (byOrder.isPresent()) {
            Payment existingPayment = byOrder.get();
            log.info("Existing payment found for orderId={} with status={}", dto.getOrderId(), existingPayment.getStatus());

            // If idempotency key matches an existing record, return it (true idempotency)
            if (idempotencyKey != null && !idempotencyKey.isBlank()
                    && idempotencyKey.equals(existingPayment.getIdempotencyKey())) {
                log.info("Returning existing payment for idempotencyKey={}", idempotencyKey);
                return mapToDTO(existingPayment);
            }

            // If the existing payment is already completed or refunded, return it to avoid duplicate processing
            String status = existingPayment.getStatus();
            if (status != null && (status.equals("COMPLETED") || status.equals("REFUNDED"))) {
                log.info("Payment already in terminal state ({}). Returning existing.", status);
                return mapToDTO(existingPayment);
            }

            // Otherwise (e.g., PENDING or FAILED), attempt to process/update the existing payment instead of creating a new one.
            log.info("Attempting to process existing payment record for order {} (currentStatus={})", dto.getOrderId(), status);
            existingPayment.setPaymentMethod(dto.getPaymentMethod());
            existingPayment.setAmount(dto.getAmount());
            // attach idempotency key if provided
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                existingPayment.setIdempotencyKey(idempotencyKey);
            }

            boolean paymentSuccessExisting = simulatePaymentProcessing();
            if (paymentSuccessExisting) {
                existingPayment.setStatus("COMPLETED");
                existingPayment.setTransactionId(existingPayment.getTransactionId() == null ? generateTransactionId() : existingPayment.getTransactionId());
                existingPayment.setFailureReason(null);
                log.info("Existing payment processed successfully for order: {}", dto.getOrderId());
            } else {
                existingPayment.setStatus("FAILED");
                existingPayment.setFailureReason("Payment declined");
                log.warn("Existing payment failed for order: {}", dto.getOrderId());
            }

            Payment updated = paymentRepository.save(existingPayment);
            return mapToDTO(updated);
        }

        Payment  payment = new Payment();
        payment.setOrderId(dto.getOrderId());
        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setTransactionId(generateTransactionId());
        payment.setIdempotencyKey(idempotencyKey);
        
        // Simulate payment processing
        boolean paymentSuccess = simulatePaymentProcessing();
        
        if (paymentSuccess) {
            payment.setStatus("COMPLETED");
            log.info("Payment processed successfully for order: {}", dto.getOrderId());
        } else {
            payment.setStatus("FAILED");
            payment.setFailureReason("Payment declined");
            log.warn("Payment failed for order: {}", dto.getOrderId());
        }
        
        Payment savedPayment = paymentRepository.save(payment);
        return mapToDTO(savedPayment);
    }
    
    public PaymentDTO getPaymentById(Long id) {
        log.info("Fetching payment with ID: {}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));
        return mapToDTO(payment);
    }
    
    public PaymentDTO getPaymentByOrderId(Long orderId) {
        log.info("Fetching payment for order: {}", orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
        return mapToDTO(payment);
    }
    
    public List<PaymentDTO> getAllPayments() {
        log.info("Fetching all payments");
        return paymentRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public List<PaymentDTO> getPaymentsByStatus(String status) {
        log.info("Fetching payments with status: {}", status);
        return paymentRepository.findByStatus(status)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public PaymentDTO refundPayment(Long id, String reason) {
        log.info("Processing refund for payment: {}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));
        
        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new RuntimeException("Only completed payments can be refunded");
        }
        
        payment.setStatus("REFUNDED");
        payment.setFailureReason(reason);
        Payment refundedPayment = paymentRepository.save(payment);
        log.info("Payment refunded: {}", refundedPayment.getId());
        return mapToDTO(refundedPayment);
    }
    
    public void deletePayment(Long id) {
        log.info("Deleting payment with ID: {}", id);
        if (!paymentRepository.existsById(id)) {
            throw new RuntimeException("Payment not found with ID: " + id);
        }
        paymentRepository.deleteById(id);
        log.info("Payment deleted with ID: {}", id);
    }
    
    private boolean simulatePaymentProcessing() {
        // Force all mock payments to succeed for local demo flows.
        return true;
    }
    
    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis();
    }
    
    private PaymentDTO mapToDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getTransactionId(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
    
}

