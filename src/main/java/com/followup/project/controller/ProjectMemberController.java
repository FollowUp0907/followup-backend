package com.followup.project.controller;

import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectMemberResDto;
import com.followup.project.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Member")
@RestController
@RequestMapping("/api/project/{projectId}")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @Operation(summary = "프로젝트 멤버 목록 조회")
    @GetMapping("/members")
    public ResponseEntity<List<ProjectMemberResDto>> getMembers(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectMemberService.getMembers(projectId));
    }

    @Operation(summary = "프로젝트 멤버 추가")
    @ApiResponse(responseCode = "201", description = "추가 성공")
    @ApiResponse(responseCode = "409", description = "이미 프로젝트 멤버임")
    @PostMapping("/member")
    public ResponseEntity<ProjectMemberResDto> addMember(@PathVariable Long projectId,
                                                            @Valid @RequestBody ProjectMemberCreateReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMemberService.addMember(projectId, request));
    }

    @Operation(summary = "프로젝트 멤버 제거")
    @ApiResponse(responseCode = "204", description = "제거 성공")
    @ApiResponse(responseCode = "409", description = "OWNER는 제거할 수 없음")
    @DeleteMapping("/member/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectMemberService.removeMember(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
