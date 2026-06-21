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

    public List<String> getModelsByBrand(String brand){
    Long brandId = marcaRepository.findByNome(brand);
     if(brandId == null){
         throw new RuntimeException("Brand non disponibile");
     }
     return modelloRepository.findAllByMarcaId(brandId).stream().map(Modello::getNome).toList();
    }

    public List<String> getEnginesByModel(String model){
        Long modelId = modelloRepository.findByNome(model);
        if(modelId == null){
        throw new RuntimeException("Modello non disponibile");}

        return motorizzazioneRepository.findAllByModelloId(modelId).stream().map(Motorizzazione::getNomeMotore).toList();
    }






}

