package com.vista.pdg.auth.service;

import com.vista.pdg.auth.dto.CreateUserRequest;
import com.vista.pdg.auth.dto.UserDto;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  public List<UserDto> findAll() {
    return userRepository.findAll().stream().map(this::toDto).toList();
  }

  public UserDto create(CreateUserRequest req) {
    User user =
        User.builder()
            .displayName(req.displayName())
            .email(req.email())
            .password(passwordEncoder.encode(req.password()))
            .roles(new HashSet<>(roleRepository.findAllById(req.roleIds())))
            .build();
    return toDto(userRepository.save(user));
  }

  public UserDto update(Long id, CreateUserRequest req) {
    User user = userRepository.findById(id).orElseThrow();
    user.setDisplayName(req.displayName());
    user.setEmail(req.email());
    if (req.password() != null && !req.password().isBlank()) {
      user.setPassword(passwordEncoder.encode(req.password()));
    }
    user.setRoles(new HashSet<>(roleRepository.findAllById(req.roleIds())));
    return toDto(userRepository.save(user));
  }

  public void delete(Long id) {
    userRepository.deleteById(id);
  }

  public UserDto assignRoles(Long id, List<Long> roleIds) {
    User user = userRepository.findById(id).orElseThrow();
    user.setRoles(new HashSet<>(roleRepository.findAllById(roleIds)));
    return toDto(userRepository.save(user));
  }

  private UserDto toDto(User u) {
    return new UserDto(
        u.getId(),
        u.getDisplayName(),
        u.getEmail(),
        u.isEnabled(),
        u.getRoles().stream().map(Role::getName).toList());
  }
}
