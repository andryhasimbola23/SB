package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "compteur_codification",
       uniqueConstraints = @UniqueConstraint(columnNames = {"service_id", "type_materiel"}))
public class CompteurCodification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "type_materiel", nullable = false, length = 10)
    private String typeMateriel;

    @Column(name = "dernier_numero", nullable = false)
    private Integer dernierNumero = 0;
}