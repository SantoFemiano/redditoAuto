package com.santofem.redditoauto.service;

import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.entity.enums.TipoCambio;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalcoloSostenibilitaService — Unit Tests")
class CalcoloSostenibilitaServiceTest {

    @Mock
    private MotorizzazioneRepository motorizzazioneRepository;

    @InjectMocks
    private CalcoloSostenibilitaService service;

    private Motorizzazione golf;

    @BeforeEach
    void setUp() {
        Marca vw     = Marca.builder().id(1L).nome("Volkswagen").build();
        Modello modello = Modello.builder().id(1L).marca(vw).nome("Golf").build();

        golf = Motorizzazione.builder()
            .id(1L)
            .modello(modello)
            .nomeMotore("2.0 TDI 150 CV DSG")
            .annoProduzione(2022)
            .tipoCarburante(TipoCarburante.DIESEL)
            .tipoCambio(TipoCambio.DCT)
            .potenzaKw(110)
            .potenzaCv(150)
            .consumoMedioLitri100km(new BigDecimal("5.8"))
            .misuraPneumaticiAnteriori("205/55 R16")
            .runFlat(false)
            .prezzoListinoEur(new BigDecimal("35000"))
            .costoTagliandoBaseEur(new BigDecimal("250"))
            .costoTagliandoMaiorEur(new BigDecimal("700"))
            .intervalloTagliandoKm(15000)
            .intervalloTagliandoMaiorKm(60000)
            .gruppoAssicurativo(8)
            .build();
    }

    // -----------------------------------------------
    // TEST RATA FRANCESE
    // -----------------------------------------------

    @Test
    @DisplayName("Rata francese — TAN 7%, 60 mesi, acconto 5000")
    void rataFrancese_TAN7_60mesi() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRequestDTO req = CalcoloRequestDTO.builder()
            .motorizzazioneId(1L)
            .redditoNettoMensile(new BigDecimal("2500"))
            .acconto(new BigDecimal("5000"))
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();

        CalcoloRispostaDTO ris = service.calcola(req);

        // Prezzo finanziato = 35000 - 5000 = 30000
        assertThat(ris.getPrezzoFinanziato()).isEqualByComparingTo(new BigDecimal("30000.00"));
        // Rata attesa ~594 € (formula francese: 30000 * 0.005833 / (1 - 1.005833^-60))
        assertThat(ris.getRataFiananziamentoMensile())
            .isBetween(new BigDecimal("580"), new BigDecimal("610"));
        // Costo totale > prezzo finanziato (ci sono interessi)
        assertThat(ris.getCostoTotaleFinanziamento())
            .isGreaterThan(ris.getPrezzoFinanziato());
    }

    @Test
    @DisplayName("Rata francese — TAN 0% (rata = P/n, no divisione per zero)")
    void rataFrancese_TAN0() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRequestDTO req = CalcoloRequestDTO.builder()
            .motorizzazioneId(1L)
            .redditoNettoMensile(new BigDecimal("3000"))
            .acconto(BigDecimal.ZERO)
            .durataFinanziamentoMesi(60)
            .tanPercentuale(BigDecimal.ZERO)
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();

        CalcoloRispostaDTO ris = service.calcola(req);

        // Con TAN 0%: rata = 35000 / 60 ≈ 583.33
        assertThat(ris.getRataFiananziamentoMensile())
            .isEqualByComparingTo(new BigDecimal("583.33"));
        // Interessi = 0
        assertThat(ris.getInteressiTotali())
            .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    // -----------------------------------------------
    // TEST BOLLO ACI
    // -----------------------------------------------

    @Test
    @DisplayName("Bollo ACI — 110 kW (sotto soglia 100 kW: i primi 100 a 2.58, i 10 eccedenti a 3.87)")
    void bolloAci_110kw() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRequestDTO req = defaultRequest();
        CalcoloRispostaDTO ris = service.calcola(req);

        // Bollo annuo = (100 * 2.58) + (10 * 3.87) = 258 + 38.70 = 296.70
        // Mensile = 296.70 / 12 = 24.725 → 24.73
        assertThat(ris.getCostoBolloMensile())
            .isEqualByComparingTo(new BigDecimal("24.73"));
    }

    @Test
    @DisplayName("Bollo ACI — Veicolo elettrico → €0")
    void bolloAci_elettrico_zero() {
        golf.setTipoCarburante(TipoCarburante.ELETTRICO);
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRispostaDTO ris = service.calcola(defaultRequest());

        assertThat(ris.getCostoBolloMensile()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    // -----------------------------------------------
    // TEST COSTO CARBURANTE
    // -----------------------------------------------

    @Test
    @DisplayName("Costo carburante — 5.8 l/100km, 1500 km/mese, 1.85 €/L")
    void costoCarburante_corretto() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRispostaDTO ris = service.calcola(defaultRequest());

        // 1500 * (5.8/100) * 1.85 = 1500 * 0.058 * 1.85 = 161.10 €
        assertThat(ris.getCostoCarburanteMensile())
            .isEqualByComparingTo(new BigDecimal("161.10"));
    }

    // -----------------------------------------------
    // TEST GIUDIZIO SOSTENIBILITÀ
    // -----------------------------------------------

    @Test
    @DisplayName("Giudizio OTTIMO — reddito alto, auto economica")
    void giudizio_ottimo() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRequestDTO req = defaultRequest().toBuilder()
            .redditoNettoMensile(new BigDecimal("6000"))
            .build();

        CalcoloRispostaDTO ris = service.calcola(req);
        assertThat(ris.getGiudizio()).isEqualTo("OTTIMO");
        assertThat(ris.isSostenibile()).isTrue();
    }

    @Test
    @DisplayName("Giudizio CRITICO — reddito basso, auto costosa")
    void giudizio_critico() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRequestDTO req = defaultRequest().toBuilder()
            .redditoNettoMensile(new BigDecimal("1000"))
            .build();

        CalcoloRispostaDTO ris = service.calcola(req);
        assertThat(ris.getGiudizio()).isEqualTo("CRITICO");
        assertThat(ris.isSostenibile()).isFalse();
    }

    @Test
    @DisplayName("Errore — acconto maggiore del prezzo")
    void errore_acconto_troppo_alto() {
        when(motorizzazioneRepository.findById(1L)).thenReturn(Optional.of(golf));

        CalcoloRequestDTO req = defaultRequest().toBuilder()
            .acconto(new BigDecimal("40000"))
            .build();

        assertThatThrownBy(() -> service.calcola(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("acconto");
    }

    @Test
    @DisplayName("Errore — motorizzazione non trovata")
    void errore_motorizzazione_non_trovata() {
        when(motorizzazioneRepository.findById(99L)).thenReturn(Optional.empty());

        CalcoloRequestDTO req = defaultRequest().toBuilder()
            .motorizzazioneId(99L)
            .build();

        assertThatThrownBy(() -> service.calcola(req))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // -----------------------------------------------
    // HELPER
    // -----------------------------------------------

    private CalcoloRequestDTO defaultRequest() {
        return CalcoloRequestDTO.builder()
            .motorizzazioneId(1L)
            .redditoNettoMensile(new BigDecimal("2500"))
            .acconto(new BigDecimal("5000"))
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();
    }
}
