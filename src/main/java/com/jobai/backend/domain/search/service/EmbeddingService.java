package com.jobai.backend.domain.search.service;

import com.jobai.backend.global.ai.client.AiEmbeddingClient;
import com.jobai.backend.global.ai.dto.EmbedRequest;
import com.jobai.backend.global.ai.dto.EmbedResponse;
import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.enums.JobSource;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 공고 및 검색 쿼리의 임베딩 벡터를 생성하고 관리하는 서비스.
 * ai-server의 /embed 엔드포인트를 호출하여 텍스트를 768차원 벡터로 변환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final int EMBEDDING_DIMENSION = 768;

    private final AiEmbeddingClient aiEmbeddingClient;
    private final JobEmbeddingRepository jobEmbeddingRepository;

    @Value("${search.embedding.max-text-length:8000}")
    private int maxTextLength;

    /** 사기업 공고의 title + description을 임베딩하여 저장한다. */
    @Transactional
    public void embedPrivatePosting(PrivateJobPosting posting) {
        String text = buildText(posting.getTitle(), posting.getDescription());
        float[] vector = requestJdEmbedding(text);
        saveOrUpdate(JobSource.PRIVATE, posting.getId(), vector, text);
    }

    /** 공기업 공고의 title + htmlContent(HTML 제거)를 임베딩하여 저장한다. */
    @Transactional
    public void embedPublicPosting(PublicJobPosting posting) {
        String rawContent = posting.getHtmlContent();
        String cleanContent = stripHtml(rawContent);
        String text = buildText(posting.getTitle(), cleanContent);
        float[] vector = requestNcsEmbedding(text);
        saveOrUpdate(JobSource.PUBLIC, posting.getId(), vector, text);
    }

    /** 사용자 검색 쿼리를 임베딩 벡터로 변환한다. DB에 저장하지 않는다. */
    public float[] embedQuery(String query) {
        return requestJdEmbedding(query.trim());
    }

    /** 이력서 텍스트를 AI 서버 /embed/jd로 전송하여 768차원 벡터로 변환한다(사기업 매칭용). */
    public float[] embedResumeText(String resumeText) {
        String text = resumeText.length() > maxTextLength
                ? resumeText.substring(0, maxTextLength) : resumeText;
        return requestJdEmbedding(text);
    }

    /** 이력서 텍스트를 AI 서버 /embed/ncs로 전송하여 768차원 벡터로 변환한다(공기업 매칭용). */
    public float[] embedResumeTextNcs(String resumeText) {
        String text = resumeText.length() > maxTextLength
                ? resumeText.substring(0, maxTextLength) : resumeText;
        return requestNcsEmbedding(text);
    }

    private float[] requestJdEmbedding(String text) {
        EmbedResponse response = aiEmbeddingClient.embed(new EmbedRequest(text)).block();
        return validateAndConvert(response);
    }

    private float[] requestNcsEmbedding(String text) {
        EmbedResponse response = aiEmbeddingClient.embedNcs(new EmbedRequest(text)).block();
        return validateAndConvert(response);
    }

    private float[] validateAndConvert(EmbedResponse response) {
        if (response == null || response.vector() == null || response.vector().isEmpty()) {
            throw new IllegalStateException("임베딩 응답이 비어있습니다");
        }
        if (response.vector().size() != EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "임베딩 차원이 올바르지 않습니다: expected=" + EMBEDDING_DIMENSION
                            + ", actual=" + response.vector().size());
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
