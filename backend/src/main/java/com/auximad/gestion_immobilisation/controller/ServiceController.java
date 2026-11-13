package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.ServiceDto;
import com.auximad.gestion_immobilisation.service.ServiceMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ServiceController {

    private final ServiceMetier serviceMetier;

    @GetMapping
    public ResponseEntity<List<ServiceDto>> getAllServices() {
        return ResponseEntity.ok(serviceMetier.getAllServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceDto> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceMetier.getServiceById(id));
    }

    @PostMapping
    public ResponseEntity<ServiceDto> createService(@Valid @RequestBody ServiceDto dto) {
        return new ResponseEntity<>(serviceMetier.createService(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceDto> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceDto dto) {
        return ResponseEntity.ok(serviceMetier.updateService(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        serviceMetier.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ServiceDto>> searchServices(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(serviceMetier.searchServices(keyword));
    }

    @GetMapping("/agence/{agenceId}")
    public ResponseEntity<List<ServiceDto>> getServicesByAgence(@PathVariable Long agenceId) {
        return ResponseEntity.ok(serviceMetier.getServicesByAgence(agenceId));
    }
}