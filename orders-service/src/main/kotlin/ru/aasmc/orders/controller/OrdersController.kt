package ru.aasmc.orders.controller

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.service.OrdersService

private val log = LoggerFactory.getLogger(OrdersController::class.java)

@RestController
@RequestMapping("/v1/orders")
class OrdersController(
    private val ordersService: OrdersService
) {

    @GetMapping("/{id}")
    fun getOrder(
        @PathVariable("id") id: String,
        @RequestParam("timeout", defaultValue = "2000") timeout: Long
    ): DeferredResult<OrderDto> {
        log.info("Received request to GET order with id = {}", id)
        val deferredResult = DeferredResult<OrderDto>(timeout)
        ordersService.getOrderDto(id, deferredResult, timeout)
        return deferredResult
    }

    @GetMapping("/{id}/validated")
    fun getValidatedOrder(
        @PathVariable("id") id: String,
        @RequestParam("timeout", defaultValue = "2000") timeout: Long
    ): DeferredResult<OrderDto> {
        log.info("Received request to GET validated order with id = {}", id)
        val deferredResult = DeferredResult<OrderDto>(timeout)
        ordersService.getValidatedOrder(id, deferredResult, timeout)
        return deferredResult
    }

    @PostMapping
    fun submitOrder(
        @RequestBody dto: OrderDto,
        @RequestParam("timeout", defaultValue = "2000") timeout: Long
    ): DeferredResult<String> {
        log.info("Received POST request to submit order {}", dto)
        val deferredResult = DeferredResult<String>(timeout)
        ordersService.submitOrder(dto, deferredResult)
        return deferredResult
    }
}