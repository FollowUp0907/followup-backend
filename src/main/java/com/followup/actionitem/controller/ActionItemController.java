package com.followup.actionitem.controller;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.dto.ActionItemListResDto;
import com.followup.actionitem.dto.ActionItemUpdateReqDto;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.service.ActionItemService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ActionItemController {

    private final ActionItemService actionItemService;

    @GetMapping("/project/{projectId}/action-items")
    public ResponseEntity<List<ActionItemListResDto>> getActionItems(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Priority priority) {
        return ResponseEntity.ok(actionItemService.getActionItems(projectId, status, assigneeId, priority));
    }

    @PostMapping("/project/{projectId}/action-item")
    public ResponseEntity<ActionItemDetailResDto> createActionItem(
            @PathVariable Long projectId,
            @Valid @RequestBody ActionItemCreateReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actionItemService.createActionItem(projectId, request));
    }

    @GetMapping("/action-item/{actionItemId}")
    public ResponseEntity<ActionItemDetailResDto> getActionItem(@PathVariable Long actionItemId) {
        return ResponseEntity.ok(actionItemService.getActionItem(actionItemId));
    }

    @PatchMapping("/action-item/{actionItemId}")
    public ResponseEntity<ActionItemDetailResDto> updateActionItem(
            @PathVariable Long actionItemId,
            @Valid @RequestBody ActionItemUpdateReqDto request) {
        return ResponseEntity.ok(actionItemService.updateActionItem(actionItemId, request));
    }

    @DeleteMapping("/action-item/{actionItemId}")
    public ResponseEntity<Void> deleteActionItem(@PathVariable Long actionItemId) {
        actionItemService.deleteActionItem(actionItemId);
        return ResponseEntity.noContent().build();
    }
}
