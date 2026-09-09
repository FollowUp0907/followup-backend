package com.followup.project.controller;

import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.dto.ProjectUpdateReqDto;
import com.followup.project.service.ProjectService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "내 프로젝트 목록 조회")
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResDto>> getProjects() {
        return ResponseEntity.ok(projectService.getProjects());
    }

    @Operation(summary = "프로젝트 생성")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/project")
    public ResponseEntity<ProjectResDto> createProject(@Valid @RequestBody ProjectCreateReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(request));
    }

    @Operation(summary = "프로젝트 상세 조회")
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ProjectResDto> getProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProject(projectId));
    }

    @Operation(summary = "프로젝트 수정")
    @PatchMapping("/project/{projectId}")
    public ResponseEntity<ProjectResDto> updateProject(@PathVariable Long projectId,
                                                          @Valid @RequestBody ProjectUpdateReqDto request) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request));
    }

    @Operation(summary = "프로젝트 삭제")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "409", description = "삭제할 수 없음(연관 데이터 존재)")
    @DeleteMapping("/project/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
