package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.service.GetInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/info")
@RequiredArgsConstructor
@Slf4j
public class InfoController {

    private final GetInfo getInfo;

    @GetMapping("/brands")
    public ResponseEntity<List<String>> getBrand(){
        return ResponseEntity.ok(getInfo.getBrands());
    }

    @GetMapping("/models")
    public ResponseEntity<List<String>> getModels(){
        return ResponseEntity.ok(getInfo.getModels());
    }

    @GetMapping("/engines")
    public ResponseEntity<List<String>> getEngines(){
        return ResponseEntity.ok(getInfo.getEngines());
    }

    @GetMapping("/models/{brand}")
    public ResponseEntity<List<String>> getModelBrand(@PathVariable String brand){
        return ResponseEntity.ok(getInfo.getModelsByBrand(brand));
    }

    @GetMapping("/models/engines/{model}")
    public ResponseEntity<List<String>> getEnginesByModel(@PathVariable String model){
        return ResponseEntity.ok(getInfo.getEnginesByModel(model));
    }

    @GetMapping("/models/years/{year}")
    public ResponseEntity<List<String>> getModelsByYear(@PathVariable Integer year){
        return ResponseEntity.ok(getInfo.getModelsByYear(year));
    }


}




