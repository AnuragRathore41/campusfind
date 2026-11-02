package com.example.campusfind.repository;

import com.example.campusfind.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByItemId(Long itemId);
    boolean existsByItemIdAndClaimedById(Long itemId, Long userId);
}