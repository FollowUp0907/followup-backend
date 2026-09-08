package com.followup.project.controller;

import com.followup.project.dto.ProjectMemberCreateRequest;
import com.followup.project.dto.ProjectMemberResponse;
import com.followup.project.service.ProjectMemberService;
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

@RestController
@RequestMapping("/api/project/{projectId}")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping("/members")
    public ResponseEntity<List<ProjectMemberResponse>> getMembers(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectMemberService.getMembers(projectId));
    }

    @PostMapping("/member")
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable Long projectId,
                                                            @Valid @RequestBody ProjectMemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMemberService.addMember(projectId, request));
    }

    @DeleteMapping("/member/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectMemberService.removeMember(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
