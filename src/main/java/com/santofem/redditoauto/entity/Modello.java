package com.santofem.redditoauto.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "modello",
    uniqueConstraints = @UniqueConstraint(columnNames = {"marca_id", "nome", "anno_inizio", "anno_fine"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"marca", "motorizzazioni"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Modello {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @Column(nullable = false, length = 100)
    private String nome; // es. "Golf", "Serie 3", "Yaris"

    @Column(name = "anno_inizio")
    private Integer annoInizio; // Anno inizio generazione, es. 2020

    @Column(name = "anno_fine")
    private Integer annoFine;   // null = ancora in produzione

    @OneToMany(mappedBy = "modello", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Motorizzazione> motorizzazioni = new ArrayList<>();
}
