package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** Vue API d'une notification — jamais le destinataire ni l'entité. */
@Data
public class NotificationDto {

    private Long id;

    private String type;

    private String titre;

    private String message;

    /** Route frontend à ouvrir au clic (null = simple information). */
    private String lien;

    private boolean lue;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateCreation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateLue;
}
