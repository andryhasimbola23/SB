package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** Vue API d'une entrée de journal — jamais l'entité gérée. */
@Data
public class JournalAuditDto {

    private Long id;

    private Long acteurId;

    /** Nom complet de l'acteur, ou {@code null} pour une action sans session. */
    private String acteurNom;

    private String action;

    private String entite;

    private Long entiteId;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateAction;
}
