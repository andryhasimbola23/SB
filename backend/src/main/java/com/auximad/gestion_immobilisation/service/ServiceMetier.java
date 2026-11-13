package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ServiceDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceMetier {

    private final ServiceRepository serviceRepository;
    private final AgenceRepository agenceRepository;
    private final ImmobilisationRepository immobilisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DemandeImmobilisationRepository demandeRepository;

    @Transactional(readOnly = true)
    public List<ServiceDto> getAllServices() {
        return serviceRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceDto getServiceById(Long id) {
        com.auximad.gestion_immobilisation.model.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'ID : " + id));
        return toDto(service);
    }

    @Transactional
    public ServiceDto createService(ServiceDto dto) {
        // Vérifier unicité du code
        if (serviceRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Un service avec le code '" + dto.getCode() + "' existe déjà.");
        }

        // Vérifier que l'agence existe
        Agence agence = agenceRepository.findById(dto.getAgenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée avec l'ID : " + dto.getAgenceId()));

        com.auximad.gestion_immobilisation.model.Service service = com.auximad.gestion_immobilisation.model.Service.builder()
                .code(dto.getCode().toUpperCase())
                .nomService(dto.getNomService())
                .description(dto.getDescription())
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .agence(agence)
                .build();

        return toDto(serviceRepository.save(service));
    }

    @Transactional
    public ServiceDto updateService(Long id, ServiceDto dto) {
        com.auximad.gestion_immobilisation.model.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'ID : " + id));

        // Vérifier unicité du code (sauf pour ce service)
        if (!service.getCode().equalsIgnoreCase(dto.getCode())
                && serviceRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Un service avec le code '" + dto.getCode() + "' existe déjà.");
        }

        // Vérifier que l'agence existe
        Agence agence = agenceRepository.findById(dto.getAgenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée avec l'ID : " + dto.getAgenceId()));

        service.setCode(dto.getCode().toUpperCase());
        service.setNomService(dto.getNomService());
        service.setDescription(dto.getDescription());
        service.setActif(dto.getActif());
        service.setAgence(agence);

        return toDto(serviceRepository.save(service));
    }

    /**
     * Suppression contrôlée d'un service : refusée (409) tant qu'il porte
     * encore des biens, des utilisateurs ou des demandes.
     */
    @Transactional
    public void deleteService(Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Service non trouvé avec l'ID : " + id);
        }

        long immobilisations = immobilisationRepository.countByServiceId(id);
        long utilisateurs = utilisateurRepository.countByServiceId(id);
        long demandes = demandeRepository.countByServiceId(id);

        if (immobilisations > 0 || utilisateurs > 0 || demandes > 0) {
            throw new ConflitMetierException(
                    "Impossible de supprimer ce service : il est référencé par "
                    + immobilisations + " immobilisation(s), " + utilisateurs
                    + " utilisateur(s) et " + demandes + " demande(s). "
                    + "Désactivez-le plutôt (champ « actif »).");
        }

        serviceRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> searchServices(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllServices();
        }
        return serviceRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> getServicesByAgence(Long agenceId) {
        return serviceRepository.findByAgenceId(agenceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ServiceDto toDto(com.auximad.gestion_immobilisation.model.Service service) {
        ServiceDto dto = new ServiceDto();
        dto.setId(service.getId());
        dto.setCode(service.getCode());
        dto.setNomService(service.getNomService());
        dto.setDescription(service.getDescription());
        dto.setActif(service.getActif());
        dto.setDateModification(service.getDateModification());

        if (service.getAgence() != null) {
            dto.setAgenceId(service.getAgence().getId());
            dto.setAgenceNom(service.getAgence().getNomAgence());
            dto.setAgenceCode(service.getAgence().getCodeAgence());
        }
        return dto;
    }
}