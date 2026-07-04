package com.jobai.backend.domain.member.service;

import com.jobai.backend.domain.member.dto.ResumeResponseDTO;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import com.jobai.backend.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeService {

    private final ResumesRepository resumesRepository;
    private final MemberRepository memberRepository;
    private final FileStorageService fileStorageService;

    public ResumeResponseDTO.ResumeListDTO getResumes(String email) {
        List<Resumes> resumes = resumesRepository.findByMemberEmailOrderByUpdatedAtDescIdDesc(email);
        List<ResumeResponseDTO.ResumeItemDTO> items = resumes.stream()
                .map(r -> ResumeResponseDTO.ResumeItemDTO.builder()
                        .resumeId(r.getId())
                        .originalFilename(r.getOriginalFilename())
                        .storedFileUrl(r.getStoredFileUrl())
                        .fileSize(r.getFileSize())
                        .isActive(r.getIsActive())
                        .uploadedAt(r.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResumeResponseDTO.ResumeListDTO.builder().resumes(items).build();
    }

    @Transactional
    public Long uploadResume(String email, MultipartFile file) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND));

        validatePdf(file);

        String key = "resumes/" + member.getId() + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        String fileUrl = fileStorageService.upload(file, key);

        try {
            Resumes resume = Resumes.builder()
                    .member(member)
                    .originalFilename(file.getOriginalFilename())
                    .storedFileUrl(fileUrl)
                    .fileSize(formatFileSize(file.getSize()))
                    .isActive(true) // 새로 업로드한 이력서를 항상 활성 이력서로 설정
                    .updatedAt(LocalDate.now())
                    .build();
            Long resumeId = resumesRepository.save(resume).getId();
            // 같은 회원의 기존 이력서는 모두 비활성화
            resumesRepository.deactivateOthersByMemberId(member.getId(), resumeId);
            return resumeId;
        } catch (Exception e) {
            // DB 저장 실패 시 S3에 올라간 파일 제거
            fileStorageService.delete(fileUrl);
            throw e;
        }
    }

    @Transactional
    public void activateResume(String email, Long resumeId) {
        Resumes resume = resumesRepository.findById(resumeId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 이력서를 찾을 수 없습니다."));

        if (!resume.getMember().getEmail().equals(email)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 이력서에 대한 권한이 없습니다.");
        }

        resumesRepository.deactivateOthersByMemberId(resume.getMember().getId(), resumeId);
        resume.activate();
    }

    @Transactional
    public void deleteResume(String email, Long resumeId) {
        Resumes resume = resumesRepository.findById(resumeId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 이력서를 찾을 수 없습니다."));

        if (!resume.getMember().getEmail().equals(email)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 이력서에 대한 권한이 없습니다.");
        }

        // DB를 먼저 삭제 후 S3 삭제: DB 실패 시 파일은 보존되어 재시도 가능
        String storedFileUrl = resume.getStoredFileUrl();
        resumesRepository.delete(resume);
        fileStorageService.delete(storedFileUrl);
    }

    private void validatePdf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        try {
            // Content-Type은 클라이언트가 위조 가능하므로 실제 파일 시그니처(%PDF-)로 검증
            byte[] header = file.getInputStream().readNBytes(5);
            if (header.length < 5 || !new String(header).startsWith("%PDF-")) {
                throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "PDF 파일만 업로드할 수 있습니다.");
            }
        } catch (GeneralException e) {
            throw e;
        } catch (IOException e) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "파일을 읽는 중 오류가 발생했습니다.");
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
