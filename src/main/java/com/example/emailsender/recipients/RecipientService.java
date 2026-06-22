package com.example.emailsender.recipients;

import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RecipientService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_MEMBERS = 500;

    private final UserRepository userRepository;
    private final RecipientGroupRepository recipientGroupRepository;

    public RecipientService(
            UserRepository userRepository,
            RecipientGroupRepository recipientGroupRepository) {
        this.userRepository = userRepository;
        this.recipientGroupRepository = recipientGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipientGroupResponse> list(String email) {
        User user = findUser(email);
        return recipientGroupRepository.findByUserOrderByNameAsc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipientGroupResponse get(String email, Long id) {
        User user = findUser(email);
        return toResponse(findOwnedGroup(user, id));
    }

    @Transactional
    public RecipientGroupResponse create(String email, RecipientGroupRequest request) {
        User user = findUser(email);
        ValidatedGroup validated = validate(request);
        if (recipientGroupRepository.existsByUserAndNameIgnoreCase(
                user, validated.name())) {
            throw new IllegalArgumentException(
                    "A recipient group with this name already exists");
        }

        RecipientGroup group = new RecipientGroup();
        group.setUser(user);
        apply(group, validated);
        return toResponse(recipientGroupRepository.save(group));
    }

    @Transactional
    public RecipientGroupResponse update(
            String email, Long id, RecipientGroupRequest request) {
        User user = findUser(email);
        RecipientGroup group = findOwnedGroup(user, id);
        ValidatedGroup validated = validate(request);
        if (recipientGroupRepository.existsByUserAndNameIgnoreCaseAndIdNot(
                user, validated.name(), id)) {
            throw new IllegalArgumentException(
                    "A recipient group with this name already exists");
        }

        apply(group, validated);
        return toResponse(recipientGroupRepository.save(group));
    }

    @Transactional
    public void delete(String email, Long id) {
        User user = findUser(email);
        recipientGroupRepository.delete(findOwnedGroup(user, id));
    }

    @Transactional(readOnly = true)
    public RecipientGroup findOwnedGroup(String email, Long id) {
        return findOwnedGroup(findUser(email), id);
    }

    @Transactional(readOnly = true)
    public List<String> resolveMembers(String email, List<Long> groupIds) {
        User user = findUser(email);
        if (groupIds == null || groupIds.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient group is required");
        }

        Map<String, String> uniqueMembers = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            RecipientGroup group = findOwnedGroup(user, groupId);
            for (String member : group.getMembers()) {
                uniqueMembers.putIfAbsent(member.toLowerCase(Locale.ROOT), member);
            }
        }
        if (uniqueMembers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Selected recipient groups do not contain any members");
        }
        return List.copyOf(uniqueMembers.values());
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private RecipientGroup findOwnedGroup(User user, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Recipient group id is required");
        }
        return recipientGroupRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient group not found"));
    }

    private ValidatedGroup validate(RecipientGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String name = normalize(request.name());
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name must not exceed 100 characters");
        }
        if (request.members() == null || request.members().isEmpty()) {
            throw new IllegalArgumentException("At least one group member is required");
        }
        if (request.members().size() > MAX_MEMBERS) {
            throw new IllegalArgumentException(
                    "A recipient group cannot have more than 500 members");
        }

        Map<String, String> uniqueMembers = new LinkedHashMap<>();
        for (String value : request.members()) {
            String member = normalize(value);
            if (!isValidEmail(member)) {
                throw new IllegalArgumentException("Invalid member email: " + value);
            }
            uniqueMembers.putIfAbsent(member.toLowerCase(Locale.ROOT), member);
        }
        return new ValidatedGroup(name, new ArrayList<>(uniqueMembers.values()));
    }

    private boolean isValidEmail(String value) {
        if (value == null) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return value.equals(address.getAddress()) && value.contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void apply(RecipientGroup group, ValidatedGroup validated) {
        group.setName(validated.name());
        group.setMembers(new ArrayList<>(validated.members()));
    }

    private RecipientGroupResponse toResponse(RecipientGroup group) {
        List<String> members = List.copyOf(group.getMembers());
        return new RecipientGroupResponse(
                group.getId(),
                group.getName(),
                members,
                members.size()
        );
    }

    private record ValidatedGroup(String name, List<String> members) {
    }
}
