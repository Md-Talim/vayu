package me.talim.vayu.dto;

import java.util.List;

public record GetDeliveriesResponse(
    List<DeliverySummaryResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {}
