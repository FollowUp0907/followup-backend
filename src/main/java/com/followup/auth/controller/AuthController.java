package com.followup.auth.controller;

import com.followup.auth.dto.LoginReqDto;
import com.followup.auth.dto.SignupReqDto;
import com.followup.auth.dto.SignupResDto;
import com.followup.auth.dto.TokenResDto;
import com.followup.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @ApiResponse(responseCode = "201", description = "가입 성공")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<SignupResDto> signup(@Valid @RequestBody SignupReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @Operation(summary = "로그인")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResDto> login(@Valid @RequestBody LoginReqDto request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
