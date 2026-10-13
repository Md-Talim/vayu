package me.talim.vayu.dto;

import java.util.List;

public record GetEndpointsResponse(
    List<GetEndpointResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {}
