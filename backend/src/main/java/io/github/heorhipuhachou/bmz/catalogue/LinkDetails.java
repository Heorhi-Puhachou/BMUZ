package io.github.heorhipuhachou.bmz.catalogue;

import java.math.BigDecimal;

public record LinkDetails(String url, PriceType priceType, BigDecimal minPrice, String currency, String note,
                          String embedCode) {
}
