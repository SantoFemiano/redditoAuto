package com.santofem.redditoauto.service;

import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

@Data
@Service
public class GetInfo {

    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;

    public List<String> getBrands(){
        return marcaRepository.findAll().stream().map(Marca::getNome).toList();
    }

    public List<String> getModels(){
        return modelloRepository.findAll().stream().map(Modello::getNome).toList();
    }

    public List<String> getEngines(){
        return motorizzazioneRepository.findAll().stream().map(Motorizzazione::getNomeMotore).toList();
    }

    public List<Modello> getModelsByBrand(String brand) {
        String marca = marcaRepository.findByNomeIgnoreCase(brand).get();
        return modelloRepository.findByBrand(marca);

    }


}

