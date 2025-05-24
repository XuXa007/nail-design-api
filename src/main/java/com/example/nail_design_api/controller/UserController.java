package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.AuthResponseDTO;
import com.example.nail_design_api.dto.UserDTO;
import com.example.nail_design_api.model.User;
import com.example.nail_design_api.security.JwtUtils;
import com.example.nail_design_api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/register/client")
    public ResponseEntity<?> registerClient(@RequestBody UserDTO userDTO) {
        try {
            // не существует ли уже пользователь
            if (userService.existsByUsername(userDTO.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Ошибка: Пользователь с таким именем уже существует!"));
            }

            User user = userService.registerUser(
                    userDTO.getUsername(),
                    userDTO.getEmail(),
                    passwordEncoder.encode(userDTO.getPassword()),
                    User.UserRole.CLIENT
            );

            // JWT токен
            String jwt = jwtUtils.generateJwtToken(user.getUsername(), user.getRole().name());

            return ResponseEntity.ok(new AuthResponseDTO(jwt, UserDTO.fromUser(user)));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ошибка регистрации: " + e.getMessage()));
        }
    }

    @PostMapping("/register/master")
    public ResponseEntity<?> registerMaster(@RequestBody UserDTO userDTO) {
        try {
            if (userService.existsByUsername(userDTO.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Ошибка: Пользователь с таким именем уже существует!"));
            }

            User user = userService.registerUser(
                    userDTO.getUsername(),
                    userDTO.getEmail(),
                    passwordEncoder.encode(userDTO.getPassword()),
                    User.UserRole.MASTER
            );

            user.setSalonName(userDTO.getSalonName());
            user.setAddress(userDTO.getAddress());
            user = userService.saveUser(user);

            String jwt = jwtUtils.generateJwtToken(user.getUsername(), user.getRole().name());

            return ResponseEntity.ok(new AuthResponseDTO(jwt, UserDTO.fromUser(user)));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ошибка регистрации: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO loginRequest) {
        try {
            User user = userService.findByUsername(loginRequest.getUsername());

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Пользователь не найден"));
            }

            // check пароль
            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Неверный пароль"));
            }

            // генер JWT токен
            String jwt = jwtUtils.generateJwtToken(user.getUsername(), user.getRole().name());

            // возвращаем токен и информацию о пользователе
            return ResponseEntity.ok(new AuthResponseDTO(jwt, UserDTO.fromUser(user)));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ошибка входа: " + e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Пользователь не найден"));
            }

            return ResponseEntity.ok(UserDTO.fromUser(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ошибка получения профиля: " + e.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserDTO userDTO) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Пользователь не найден"));
            }

            if (userDTO.getEmail() != null) {
                user.setEmail(userDTO.getEmail());
            }

            if (user.getRole() == User.UserRole.MASTER) {
                if (userDTO.getSalonName() != null) {
                    user.setSalonName(userDTO.getSalonName());
                }
                if (userDTO.getAddress() != null) {
                    user.setAddress(userDTO.getAddress());
                }
            }

            user = userService.saveUser(user);
            return ResponseEntity.ok(UserDTO.fromUser(user));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ошибка обновления профиля: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);

            if (jwtUtils.validateJwtToken(token)) {
                String username = jwtUtils.getUsernameFromJwtToken(token);
                User user = userService.findByUsername(username);

                if (user != null) {
                    // Генерируем новый токен
                    String newToken = jwtUtils.generateJwtToken(user.getUsername(), user.getRole().name());
                    return ResponseEntity.ok(new AuthResponseDTO(newToken, UserDTO.fromUser(user)));
                }
            }

            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Недействительный токен"));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ошибка обновления токена: " + e.getMessage()));
        }
    }

    // class для ответов с сообщениями
    public static class MessageResponse {
        private String message;

        public MessageResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}