package com.jobai.backend.domain.crawler.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 점 표기법 + 배열맵 경로 해석기.
 *
 * <p>입력 obj 는 Jackson 이 파싱한 구조(Map / List / String / Number / Boolean / null).
 *
 * <p>경로 문법:
 * <ul>
 *   <li>{@code "a.b.c"} — 중첩 맵 탐색</li>
 *   <li>{@code "field[].sub"} — field 가 배열이면 각 원소에서 sub 를 뽑아 리스트로</li>
 *   <li>{@code ""} 또는 null — obj 자체 반환</li>
 * </ul>
 */
public final class JsonPathResolver {

    private JsonPathResolver() {}

    /** Python resolve(obj, path). */
    @SuppressWarnings("unchecked")
    public static Object resolve(Object obj, String path) {
        if (obj == null || path == null || path.isEmpty()) {
            return obj;
        }
        int dot = path.indexOf('.');
        String seg = (dot < 0) ? path : path.substring(0, dot);
        String rest = (dot < 0) ? "" : path.substring(dot + 1);

        if (seg.endsWith("[]")) {
            String key = seg.substring(0, seg.length() - 2);
            Object lst = (obj instanceof Map) ? ((Map<String, Object>) obj).get(key) : null;
            if (!(lst instanceof List)) {
                return null;
            }
            List<Object> out = new ArrayList<>();
            for (Object item : (List<Object>) lst) {
                out.add(resolve(item, rest));
            }
            return out;
        }
        Object nxt = (obj instanceof Map) ? ((Map<String, Object>) obj).get(seg) : null;
        return resolve(nxt, rest);
    }

    /**
     * Python _resolve_field(raw, p).
     * p 가 List 면 각 경로를 해석해 빈 값 빼고 "\n\n" 으로 이어붙인다(Lever 본문 조각).
     * 아니면 단일 경로 resolve.
     */
    @SuppressWarnings("unchecked")
    public static Object resolveField(Object raw, Object p) {
        if (p instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object one : (List<Object>) p) {
                Object v = resolve(raw, String.valueOf(one));
                if (v != null && !"".equals(v)) {
                    parts.add(String.valueOf(v).strip());
                }
            }
            return parts.isEmpty() ? null : String.join("\n\n", parts);
        }
        return resolve(raw, String.valueOf(p));
    }
}
