package com.example.campusfind.repository;

import com.example.campusfind.model.Item;
import com.example.campusfind.model.ItemStatus;
import com.example.campusfind.model.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("SELECT i FROM Item i WHERE " +
            "(:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:category IS NULL OR LOWER(i.category) = LOWER(:category)) " +
            "AND (:location IS NULL OR LOWER(i.location) LIKE LOWER(CONCAT('%', :location, '%'))) " +
            "AND (:type IS NULL OR i.type = :type) " +
            "AND (:status IS NULL OR i.status = :status)")
    List<Item> search(@Param("keyword") String keyword,
                      @Param("category") String category,
                      @Param("location") String location,
                      @Param("type") ItemType type,
                      @Param("status") ItemStatus status);
}