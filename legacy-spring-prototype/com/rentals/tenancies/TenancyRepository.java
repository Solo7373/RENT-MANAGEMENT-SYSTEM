package com.rentals.tenancies;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TenancyRepository extends JpaRepository<Tenancy, Long> {
    List<Tenancy> findByTenantTenantId(Long tenantId);
    List<Tenancy> findByRoomRoomId(Long roomId);
    List<Tenancy> findByStatus(String status);
}
