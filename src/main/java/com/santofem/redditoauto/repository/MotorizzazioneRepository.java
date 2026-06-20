package com.santofem.redditoauto.repository;

import com.santofem.redditoauto.entity.Motorizzazione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MotorizzazioneRepository extends JpaRepository<Motorizzazione, Long> {



    @Query("SELECT m FROM Motorizzazione m JOIN FETCH m.modello WHERE m.id = :id")
    Optional<Motorizzazione> findByModelloId(@Param("id") Long id);


    /**
     * Ricerca per marca + modello + anno: utile per verificare
     * se un'auto è già presente prima di invocare l'AI.
     */
    @Query("""
        SELECT m FROM Motorizzazione m
        JOIN m.modello mo
        JOIN mo.marca ma
        WHERE LOWER(ma.nome) = LOWER(:marca)
          AND LOWER(mo.nome) = LOWER(:modello)
          AND m.annoProduzione = :anno
        """)
    List<Motorizzazione> findByMarcaModelloAnno(
            @Param("marca") String marca,
            @Param("modello") String modello,
            @Param("anno") Integer anno
    );

    Optional<Motorizzazione> findByModelloIdAndNomeMotoreIgnoreCaseAndAnnoProduzione(
            Long modelloId, String nomeMotore, Integer annoProduzione
    );
}
