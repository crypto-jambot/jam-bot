package dev.rabbit.record;

import java.util.List;

public record Swap(List<Exchange> exchanges, boolean ordered) {}
