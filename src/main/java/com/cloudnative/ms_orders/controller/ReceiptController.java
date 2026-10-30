package com.cloudnative.ms_orders.controller;

import com.cloudnative.ms_orders.dto.ReceiptResponseDTO;
import com.cloudnative.ms_orders.service.ReceiptService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders/{id}/receipt")
@PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
public class ReceiptController {
    private final ReceiptService receipts;
    public ReceiptController(ReceiptService receipts) { this.receipts = receipts; }
    @PostMapping public ResponseEntity<ReceiptResponseDTO> request(@PathVariable long id, JwtAuthenticationToken auth) {
        var receipt = receipts.request(id, auth);
        return ResponseEntity.status(receipt.status().equals("READY") ? 200 : 202).body(receipt);
    }
    @GetMapping public ReceiptResponseDTO get(@PathVariable long id, JwtAuthenticationToken auth) {
        return receipts.get(id, auth);
    }
}
