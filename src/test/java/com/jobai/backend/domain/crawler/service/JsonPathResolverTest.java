package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.service.JsonPathResolver;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonPathResolverTest {

    @Test
    void dot_경로() {
        var data = new LinkedHashMap<String,Object>();
        data.put("title", "개발자");
        var obj = new LinkedHashMap<String,Object>();
        obj.put("data", data);

        assertEquals("개발자", JsonPathResolver.resolve(obj, "data.title"));
    }

    @Test
    void 빈경로는_자기자신() {
        var obj = new LinkedHashMap<String,Object>();
        assertSame(obj, JsonPathResolver.resolve(obj, ""));
    }

    @Test
    void 없는키는_null() {
        var obj = new LinkedHashMap<String,Object>();
        assertNull(JsonPathResolver.resolve(obj, "data.none"));
    }

    @Test
    void 배열맵_field_sub() {
        var arr = new ArrayList<Object>();
        arr.add(Map.of("location", "서울"));
        arr.add(Map.of("location", "부산"));
        var root = new LinkedHashMap<String,Object>();
        root.put("offices", arr);

        Object r = JsonPathResolver.resolve(root, "offices[].location");
        assertEquals(List.of("서울", "부산"), r);
    }

    @Test
    void resolveField_리스트경로_줄바꿈_join() {
        var lever = Map.of("desc", "설명", "extra", "추가");
        assertEquals("설명\n\n추가",
                JsonPathResolver.resolveField(lever, List.of("desc", "extra")));
    }
}