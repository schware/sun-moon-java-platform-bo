package com.sunmoon.bo.infrastructure.order;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.sales.SalesOrder;
import com.sunmoon.bo.domain.sales.SalesRepository;
import com.sunmoon.bo.domain.sales.SalesSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Reads 매출 from the Order service over HTTP.
 *
 * <p>A {@code @Repository} that makes no query: the name is about the role
 * in the design, not the technology behind it. The point of the port is
 * that {@code SalesService} does not know the difference — and that the
 * day BO gets its own reporting store, only this class changes.
 *
 * <p>Order returns more fields than {@link SalesOrder} declares
 * ({@code kdsDeviceId} today, {@code 거래번호} eventually). Spring Boot's
 * Jackson default ignores unknown properties rather than failing on them,
 * which is what lets Order add to its model without this screen going dark
 * until BO is rebuilt — worth knowing that the default is load-bearing
 * here rather than incidental.
 */
@Repository
public class HttpSalesRepository implements SalesRepository {

    private static final ParameterizedTypeReference<List<SalesSummary>> SUMMARIES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<SalesOrder>> ORDERS =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public HttpSalesRepository(@Qualifier("orderClient") RestClient client) {
        this.client = client;
    }

    @Override
    public List<SalesSummary> summarize(LocalDate from, LocalDate to, String storeId) {
        return get(uri -> {
            UriBuilder builder = uri.path("/sales");
            if (from != null) {
                builder.queryParam("from", from.toString());
            }
            if (to != null) {
                builder.queryParam("to", to.toString());
            }
            if (storeId != null && !storeId.isBlank()) {
                builder.queryParam("storeId", storeId);
            }
            return builder.build();
        }, SUMMARIES);
    }

    @Override
    public List<SalesOrder> detail(LocalDate businessDate, String storeId) {
        return get(uri -> {
            UriBuilder builder = uri.path("/sales/orders").queryParam("businessDate", businessDate.toString());
            if (storeId != null && !storeId.isBlank()) {
                builder.queryParam("storeId", storeId);
            }
            return builder.build();
        }, ORDERS);
    }

    private <T> List<T> get(Function<UriBuilder, java.net.URI> uri, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> body = client.get().uri(uri).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException e) {
            // Deliberately not swallowed into an empty list — see
            // UpstreamUnavailableException.
            throw new UpstreamUnavailableException("Order 서비스", e);
        }
    }
}
