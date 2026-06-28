package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.ai.client.AiEmbeddingClient;
import com.jobai.backend.domain.ai.dto.EmbedRequest;
import com.jobai.backend.domain.ai.dto.EmbedResponse;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.domain.search.entity.JobSource;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AiEmbeddingClient aiEmbeddingClient;
    private final JobEmbeddingRepository jobEmbeddingRepository;

    @Value("${search.embedding.max-text-length:8000}")
    private int maxTextLength;

    @Transactional
    public void embedPrivatePosting(PrivateJobPosting posting) {
        String text = buildText(posting.getTitle(), posting.getDescription());
        float[] vector = requestEmbedding(text);
        saveOrUpdate(JobSource.PRIVATE, posting.getId(), vector, text);
    }

    @Transactional
    public void embedPublicPosting(PublicJobPosting posting) {
        String rawContent = posting.getHtmlContent();
        String cleanContent = stripHtml(rawContent);
        String text = buildText(posting.getTitle(), cleanContent);
        float[] vector = requestEmbedding(text);
        saveOrUpdate(JobSource.PUBLIC, posting.getId(), vector, text);
    }

    public float[] embedQuery(String query) {
        return requestEmbedding(query.trim());
    }

    private float[] requestEmbedding(String text) {
        EmbedResponse response = aiEmbeddingClient.embed(new EmbedRequest(text)).block();
        if (response == null || response.vector() == null || response.vector().isEmpty()) {
            throw new IllegalStateException("임베딩 응답이 비어있습니다");
        }
        return toFloatArray(response.vector());
    }

    private void saveOrUpdate(JobSource source, Long sourceId, float[] vector, String text) {
        Optional<JobEmbedding> existing = jobEmbeddingRepository.findBySourceAndSourceId(source, sourceId);
        if (existing.isPresent()) {
            existing.get().updateEmbedding(vector, text);
        } else {
            jobEmbeddingRepository.save(JobEmbedding.builder()
                    .source(source)
                    .sourceId(sourceId)
                    .embedding(vector)
                    .embeddingText(text)
                    .build());
        }
    }

    String buildText(String title, String body) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (body != null && !body.isBlank()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(body.trim());
        }
        String result = sb.toString();
        if (result.length() > maxTextLength) {
            result = result.substring(0, maxTextLength);
        }
        return result;
    }

    String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text();
    }

    private static float[] toFloatArray(List<Double> doubles) {
        float[] floats = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            floats[i] = doubles.get(i).floatValue();
        }
        return floats;
    }
}
