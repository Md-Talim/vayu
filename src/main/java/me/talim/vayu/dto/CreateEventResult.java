package me.talim.vayu.dto;

public record CreateEventResult(
        CreateEventResponse response,
        boolean replay
        ) {}
