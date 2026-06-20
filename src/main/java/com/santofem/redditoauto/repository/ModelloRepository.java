package com.santofem.redditoauto.repository;

import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelloRepository extends JpaRepository<Modello, Long> {
    List<Modello> findByMarcaId(Long marcaId);
    List<Modello> findByBrand(Marca marca);

    Optional<Modello> findByMarcaIdAndNomeIgnoreCase(Long marcaId, String nome);

}
