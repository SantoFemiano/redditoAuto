package com.santofem.redditoauto.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Modello modello = (Modello) o;
        return getId() != null && Objects.equals(getId(), modello.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
