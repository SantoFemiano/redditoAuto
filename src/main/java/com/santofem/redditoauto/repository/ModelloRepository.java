package com.santofem.redditoauto.repository;

import com.santofem.redditoauto.entity.Modello;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelloRepository extends JpaRepository<Modello, Long> {
    Optional<Modello> findByMarcaIdAndNomeIgnoreCase(Long marcaId, String nome);

    List<Modello> findAllByMarcaId(Long marcaId);

    @Query("SELECT m.id FROM Modello m WHERE LOWER(m.nome) = LOWER(:nome)")
    Long findByNome(@Param("nome") String model);
}
