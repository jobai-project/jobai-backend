package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * YAML 파일을 CrawlSpec 으로 로드. Jackson(YAMLFactory) 사용.
 *
 * <p>검증된 YAML(examples/*.yaml)을 그대로 읽는다. snake_case 는 각 spec 클래스의
 * {@code @JsonNaming(SnakeCaseStrategy)} 로 매핑된다.
 */
public class SpecLoader {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public CrawlSpec load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return yaml.readValue(in, CrawlSpec.class);
        }
    }

    public CrawlSpec loadFromClasspath(String resource) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("리소스 없음: " + resource);
            return yaml.readValue(in, CrawlSpec.class);
        }
    }

    public CrawlSpec loadFromString(String yamlText) throws IOException {
        return yaml.readValue(yamlText, CrawlSpec.class);
    }
}
