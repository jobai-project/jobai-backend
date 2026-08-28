package com.jobai.backend.global.cache;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 캐시 키 생성 유틸리티.
 * <p>파라미터 목록을 정렬·결합하여 결정적(deterministic) 키를 만든다.
 * List 파라미터는 정렬 후 쉼표 구분, null/빈 리스트는 {@code "_"}로 표현한다.</p>
 */
public final class CacheKeyGenerator {

    private CacheKeyGenerator() {
    }

    public static String buildKey(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            if (parts[i] == null) {
                sb.append('_');
            } else if (parts[i] instanceof List<?> list) {
                sb.append(list.isEmpty()
                        ? "_"
                        : list.stream()
                                .map(e -> Objects.toString(e, "_"))
                                .sorted()
                                .collect(Collectors.joining(",")));
            } else {
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }
}
