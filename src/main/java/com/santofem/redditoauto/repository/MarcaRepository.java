package com.santofem.redditoauto.repository;

import com.santofem.redditoauto.entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarcaRepository extends JpaRepository<Marca, Long> {
    Optional<Marca> findByNomeIgnoreCase(String nome);

    @Query("SELECT m.id FROM Marca m WHERE m.nome = :nome")
    Long findByNome(@Param("nome") String nome);

}
