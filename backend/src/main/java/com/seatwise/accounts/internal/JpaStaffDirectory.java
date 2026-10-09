package com.seatwise.accounts.internal;

import com.seatwise.accounts.StaffDirectory;
import com.seatwise.accounts.StaffSummary;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaStaffDirectory implements StaffDirectory {

    private final StaffAccountRepository repository;

    JpaStaffDirectory(StaffAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StaffSummary> findById(UUID id) {
        return repository.findById(id).map(JpaStaffDirectory::toSummary);
    }

    @Override
    public Map<UUID, StaffSummary> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .map(JpaStaffDirectory::toSummary)
                .collect(Collectors.toUnmodifiableMap(StaffSummary::id, Function.identity()));
    }

    @Override
    public Optional<StaffSummary> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(StaffAccountEntity.normalizeEmail(email))
                .map(JpaStaffDirectory::toSummary);
    }

    private static StaffSummary toSummary(StaffAccountEntity e) {
        return new StaffSummary(e.getId(), e.getFullName(), e.getEmail(), e.getRole(), e.isActive());
    }
}
