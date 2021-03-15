package me.clementino.apiproduct.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import me.clementino.apiproduct.client.PriceServiceClient;
import me.clementino.apiproduct.domain.entity.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
public class PriceServiceImpl implements PriceService {

    private final PriceServiceClient priceServiceClient;

    private static CircuitBreaker circuitBreaker;

    static {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(3)
                .minimumNumberOfCalls(0)
                .waitDurationInOpenState(Duration.ofSeconds(45))
                .recordExceptions(Exception.class)
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("getPrice");
    }

    @Autowired
    public PriceServiceImpl(PriceServiceClient priceServiceClient) {
        this.priceServiceClient = priceServiceClient;
    }

    @Override
    public Optional<BigDecimal> getPriceByProduct(Product product) {
        Supplier<BigDecimal> tSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> priceServiceClient.fetchPriceByProductId(product.getId()));
        Try<BigDecimal> recover = Try.of(tSupplier::get).recover(t -> handleException(t, product));
        return Optional.ofNullable(recover.get());
    }

    private BigDecimal handleException(Throwable t,  Product product) {
        log.info(String.format("The circuit breaker is OPEN. The service exception is %s. The product's price will be %s", t.getCause().getMessage(), product.getPrice()));
        return product.getPrice();
    }
}