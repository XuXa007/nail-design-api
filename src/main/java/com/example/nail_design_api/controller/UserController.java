package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.UserDTO;
import com.example.nail_design_api.model.User;
import com.example.nail_design_api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register/client")
    public ResponseEntity<UserDTO> registerClient(@RequestBody UserDTO userDTO) {
        User user = userService.registerUser(userDTO.getUsername(), userDTO.getEmail(),
                userDTO.getPassword(), User.UserRole.CLIENT);
        return ResponseEntity.ok(UserDTO.fromUser(user));
    }

    @PostMapping("/register/master")
    public ResponseEntity<UserDTO> registerMaster(@RequestBody UserDTO userDTO) {
        User user = userService.registerUser(userDTO.getUsername(), userDTO.getEmail(),
                userDTO.getPassword(), User.UserRole.MASTER);
        user.setSalonName(userDTO.getSalonName());
        user.setAddress(userDTO.getAddress());
        user = userService.saveUser(user);
        return ResponseEntity.ok(UserDTO.fromUser(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserDTO> login(@RequestBody UserDTO loginRequest) {
        User user = userService.login(loginRequest.getUsername(), loginRequest.getPassword());
        return ResponseEntity.ok(UserDTO.fromUser(user));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getProfile(@RequestParam String username) {
        User user = userService.findByUsername(username);
        return ResponseEntity.ok(UserDTO.fromUser(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserDTO> updateProfile(@RequestBody UserDTO userDTO) {
        User user = userService.findByUsername(userDTO.getUsername());

        // Обновляем только разрешенные поля
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
    }
}