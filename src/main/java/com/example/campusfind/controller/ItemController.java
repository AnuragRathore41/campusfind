package com.example.campusfind.controller;

import jakarta.validation.Valid;
import com.example.campusfind.model.Item;
import com.example.campusfind.model.ItemStatus;
import com.example.campusfind.model.ItemType;
import com.example.campusfind.model.User;
import com.example.campusfind.repository.ClaimRepository;
import com.example.campusfind.repository.ItemRepository;
import com.example.campusfind.repository.UserRepository;
import com.example.campusfind.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private FileStorageService fileStorageService;

    // Create a new item (reporter is taken from the JWT token)
    @PostMapping
    public Item createItem(@Valid @RequestBody Item item, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        item.setId(null);
        item.setReportedBy(user);
        item.setStatus(ItemStatus.OPEN);
        item.setImageUrl(null);   // images are added through the upload endpoint
        return itemRepository.save(item);
    }

    // Upload (or replace) the image of an item
    @PostMapping("/{id}/image")
    public Item uploadImage(@PathVariable Long id,
                            @RequestParam("file") MultipartFile file,
                            Authentication authentication) {
        Item item = findItemOrThrow(id);

        if (!canModify(item, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the reporter or an admin can upload an image for this item");
        }

        String oldImageUrl = item.getImageUrl();
        String newImageUrl = fileStorageService.store(file);

        item.setImageUrl(newImageUrl);
        Item saved = itemRepository.save(item);

        fileStorageService.delete(oldImageUrl);   // remove the previous photo, if any
        return saved;
    }

    // Get all items
    @GetMapping
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    // Search / filter items
    @GetMapping("/search")
    public List<Item> searchItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) ItemStatus status) {
        return itemRepository.search(keyword, category, location, type, status);
    }

    // Get one item by id
    @GetMapping("/{id}")
    public Item getItemById(@PathVariable Long id) {
        return findItemOrThrow(id);
    }

    // Update an item (only the reporter or an admin). Image is NOT changed here.
    @PutMapping("/{id}")
    public Item updateItem(@PathVariable Long id,
                           @Valid @RequestBody Item updatedItem,
                           Authentication authentication) {
        Item existing = findItemOrThrow(id);

        if (!canModify(existing, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the reporter or an admin can update this item");
        }

        existing.setTitle(updatedItem.getTitle());
        existing.setDescription(updatedItem.getDescription());
        existing.setType(updatedItem.getType());
        existing.setCategory(updatedItem.getCategory());
        existing.setLocation(updatedItem.getLocation());
        if (updatedItem.getStatus() != null) {
            existing.setStatus(updatedItem.getStatus());
        }

        return itemRepository.save(existing);
    }

    // Delete an item (admin only, enforced in SecurityConfig)
    @DeleteMapping("/{id}")
    @Transactional
    public String deleteItem(@PathVariable Long id) {
        Item item = findItemOrThrow(id);

        claimRepository.deleteAll(claimRepository.findByItemId(id));  // remove claims first (foreign key)
        itemRepository.delete(item);
        fileStorageService.delete(item.getImageUrl());                // remove the image file too

        return "Item deleted successfully";
    }

    // ---- helpers ----

    private Item findItemOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private boolean canModify(Item item, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isReporter = item.getReportedBy() != null
                && item.getReportedBy().getEmail().equals(authentication.getName());
        return isAdmin || isReporter;
    }
}