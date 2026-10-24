package com.example.demo.repositeries;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.models.ApiKeys;

public interface ApiKeyRepository extends JpaRepository<ApiKeys, Long> {
    
}
