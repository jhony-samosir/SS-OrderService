package com.samstore.orderservice.controller;

import com.samstore.orderservice.dto.CreateOrderRequest;
import com.samstore.orderservice.dto.OrderResponse;
import com.samstore.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("REST API Request to create order");
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID publicId) {
        log.info("REST API Request to fetch order by public ID: {}", publicId);
        OrderResponse response = orderService.getOrderByPublicId(publicId);
        return ResponseEntity.ok(response);
    }
}
