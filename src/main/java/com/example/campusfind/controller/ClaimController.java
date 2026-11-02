package com.example.campusfind.controller;

import com.example.campusfind.model.*;
import com.example.campusfind.repository.ClaimRepository;
import com.example.campusfind.repository.ItemRepository;
import com.example.campusfind.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClaimController {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    // A student claims an item
    @PostMapping("/items/{itemId}/claim")
    public ResponseEntity<?> claimItem(@PathVariable Long itemId,
                                       @RequestBody(required = false) Map<String, String> body,
                                       Authentication authentication) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return error(HttpStatus.NOT_FOUND, "Item not found");
        }
        if (item.getStatus() == ItemStatus.RESOLVED) {
            return error(HttpStatus.BAD_REQUEST, "This item is already resolved");
        }

        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (item.getReportedBy() != null && item.getReportedBy().getId().equals(user.getId())) {
            return error(HttpStatus.BAD_REQUEST, "You cannot claim your own item");
        }
        if (claimRepository.existsByItemIdAndClaimedById(itemId, user.getId())) {
            return error(HttpStatus.BAD_REQUEST, "You have already claimed this item");
        }

        Claim claim = new Claim();
        claim.setItem(item);
        claim.setClaimedBy(user);
        claim.setMessage(body != null ? body.get("message") : null);
        claim.setStatus(ClaimStatus.PENDING);
        claim.setCreatedAt(LocalDateTime.now());
        claimRepository.save(claim);

        item.setStatus(ItemStatus.CLAIMED);
        itemRepository.save(item);

        return ResponseEntity.ok(claim);
    }

    // Reporter or admin views the claims on an item
    @GetMapping("/items/{itemId}/claims")
    public ResponseEntity<?> getClaims(@PathVariable Long itemId, Authentication authentication) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return error(HttpStatus.NOT_FOUND, "Item not found");
        }
        if (!canManage(item, authentication)) {
            return error(HttpStatus.FORBIDDEN, "Only the reporter or an admin can view claims");
        }
        return ResponseEntity.ok(claimRepository.findByItemId(itemId));
    }

    // Reporter or admin approves a claim
    @PutMapping("/claims/{claimId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long claimId, Authentication authentication) {
        Claim claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null) {
            return error(HttpStatus.NOT_FOUND, "Claim not found");
        }
        Item item = claim.getItem();
        if (!canManage(item, authentication)) {
            return error(HttpStatus.FORBIDDEN, "Only the reporter or an admin can approve claims");
        }

        claim.setStatus(ClaimStatus.APPROVED);
        claimRepository.save(claim);

        item.setStatus(ItemStatus.RESOLVED);
        itemRepository.save(item);

        return ResponseEntity.ok(claim);
    }

    // Reporter or admin rejects a claim
    @PutMapping("/claims/{claimId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long claimId, Authentication authentication) {
        Claim claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null) {
            return error(HttpStatus.NOT_FOUND, "Claim not found");
        }
        Item item = claim.getItem();
        if (!canManage(item, authentication)) {
            return error(HttpStatus.FORBIDDEN, "Only the reporter or an admin can reject claims");
        }

        claim.setStatus(ClaimStatus.REJECTED);
        claimRepository.save(claim);

        // If no pending claims remain, reopen the item
        boolean anyPending = claimRepository.findByItemId(item.getId()).stream()
                .anyMatch(c -> c.getStatus() == ClaimStatus.PENDING);
        if (!anyPending) {
            item.setStatus(ItemStatus.OPEN);
            itemRepository.save(item);
        }

        return ResponseEntity.ok(claim);
    }

    // ---- helpers ----

    private boolean canManage(Item item, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isReporter = item.getReportedBy() != null
                && item.getReportedBy().getEmail().equals(authentication.getName());
        return isAdmin || isReporter;
    }

    private ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}