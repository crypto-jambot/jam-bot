package dev.rabbit.pojo;

import java.lang.reflect.Field;
import java.util.Map;

public record Memento(Map<Field, Object> fieldValues) {
}

