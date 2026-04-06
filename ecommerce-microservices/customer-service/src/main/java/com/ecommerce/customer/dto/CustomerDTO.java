package com.ecommerce.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Long createdAt;
    private Long updatedAt;
}

