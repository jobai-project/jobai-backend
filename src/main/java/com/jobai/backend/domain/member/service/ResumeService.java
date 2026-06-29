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

        Resumes resume = Resumes.builder()
                .member(member)
                .originalFilename(file.getOriginalFilename())
                .storedFileUrl(fileUrl)
                .fileSize(formatFileSize(file.getSize()))
                .isActive(false)
                .updatedAt(LocalDate.now())
                .build();

        return resumesRepository.save(resume).getId();
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

        fileStorageService.delete(resume.getStoredFileUrl());
        resumesRepository.delete(resume);
    }

    private void validatePdf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "PDF 파일만 업로드할 수 있습니다.");
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
