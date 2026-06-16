package com.santofem.redditoauto.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper PERFEZIONATO per auto-data.net.
 *
 * NAVIGAZIONE 4 LIVELLI:
 * 1) brand URL  (mappa statica + discovery via sitemap)
 * 2) /it/<marca>-brand-<id>  -> link /it/<marca>-<modello>-model-<id>
 * 3) /it/<marca>-<modello>-model-<id> -> link generazione (match anno)
 * 4) /it/<marca>-<gen>-generation-<id> -> link motorizzazione (scoring)
 * 5) Scheda tecnica -> testo tabelle (Pulizia aggressiva per evitare dati misti)
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE = "https://www.auto-data.net";

    // ══ MAPPA STATICA BRAND ID (verificati manualmente per performance) ══
    // ══ MAPPA STATICA BRAND ID (ESTRATTA DA ALLBRANDS) ══════════════════════
    private static final Map<String, Integer> BRAND_IDS = new LinkedHashMap<>();
    static {
        BRAND_IDS.put("212", 382);
        BRAND_IDS.put("abarth", 200);
        BRAND_IDS.put("ac", 1);
        BRAND_IDS.put("acura", 6);
        BRAND_IDS.put("aeolus", 348);
        BRAND_IDS.put("aion", 365);
        BRAND_IDS.put("aistaland", 393);
        BRAND_IDS.put("aito", 315);
        BRAND_IDS.put("aiways", 301);
        BRAND_IDS.put("aixam", 255);
        BRAND_IDS.put("alfa romeo", 11);
        BRAND_IDS.put("alpina", 16);
        BRAND_IDS.put("alpine", 21);
        BRAND_IDS.put("anfini", 325);
        BRAND_IDS.put("apollo", 212);
        BRAND_IDS.put("appollen", 328);
        BRAND_IDS.put("arcfox", 277);
        BRAND_IDS.put("aria", 222);
        BRAND_IDS.put("ariel", 258);
        BRAND_IDS.put("aro", 26);
        BRAND_IDS.put("artega", 204);
        BRAND_IDS.put("asia", 31);
        BRAND_IDS.put("aspark", 206);
        BRAND_IDS.put("aston martin", 36);
        BRAND_IDS.put("astro", 135);
        BRAND_IDS.put("audi", 41);
        BRAND_IDS.put("aurus", 278);
        BRAND_IDS.put("austin", 46);
        BRAND_IDS.put("austin healey", 306);
        BRAND_IDS.put("autobianchi", 51);
        BRAND_IDS.put("avatr", 330);
        BRAND_IDS.put("b.engineering", 283);
        BRAND_IDS.put("bac", 68);
        BRAND_IDS.put("baic", 61);
        BRAND_IDS.put("baltasar", 304);
        BRAND_IDS.put("baltijas dzips", 56);
        BRAND_IDS.put("baojun", 228);
        BRAND_IDS.put("baw", 344);
        BRAND_IDS.put("bee bee", 201);
        BRAND_IDS.put("belgee", 362);
        BRAND_IDS.put("bentley", 66);
        BRAND_IDS.put("bertone", 71);
        BRAND_IDS.put("bestune", 263);
        BRAND_IDS.put("bisu", 285);
        BRAND_IDS.put("bitter", 76);
        BRAND_IDS.put("bizzarrini", 299);
        BRAND_IDS.put("blonell", 81);
        BRAND_IDS.put("bmw", 86);
        BRAND_IDS.put("bollinger", 318);
        BRAND_IDS.put("bordrin", 282);
        BRAND_IDS.put("borgward", 205);
        BRAND_IDS.put("brabham", 254);
        BRAND_IDS.put("bremach", 309);
        BRAND_IDS.put("brilliance", 91);
        BRAND_IDS.put("bristol", 96);
        BRAND_IDS.put("bufori", 101);
        BRAND_IDS.put("bugatti", 106);
        BRAND_IDS.put("buick", 111);
        BRAND_IDS.put("byd", 116);
        BRAND_IDS.put("cadillac", 121);
        BRAND_IDS.put("callaway", 126);
        BRAND_IDS.put("campagna", 273);
        BRAND_IDS.put("carbodies", 131);
        BRAND_IDS.put("caterham", 136);
        BRAND_IDS.put("cenntro", 320);
        BRAND_IDS.put("changan", 141);
        BRAND_IDS.put("changan nevo", 375);
        BRAND_IDS.put("changfeng", 146);
        BRAND_IDS.put("chery", 151);
        BRAND_IDS.put("chevrolet", 156);
        BRAND_IDS.put("chrysler", 161);
        BRAND_IDS.put("citroen", 166);
        BRAND_IDS.put("cizeta", 171);
        BRAND_IDS.put("corbellati", 229);
        BRAND_IDS.put("cupra", 256);
        BRAND_IDS.put("czinger", 292);
        BRAND_IDS.put("dacia", 181);
        BRAND_IDS.put("dadi", 186);
        BRAND_IDS.put("daewoo", 191);
        BRAND_IDS.put("daf", 196);
        BRAND_IDS.put("daihatsu", 2);
        BRAND_IDS.put("daimler", 7);
        BRAND_IDS.put("dallara", 224);
        BRAND_IDS.put("dallas", 12);
        BRAND_IDS.put("datsun", 265);
        BRAND_IDS.put("david brown", 208);
        BRAND_IDS.put("dc", 217);
        BRAND_IDS.put("de lorean", 17);
        BRAND_IDS.put("de tomaso", 22);
        BRAND_IDS.put("deepal", 345);
        BRAND_IDS.put("denza", 347);
        BRAND_IDS.put("derways", 27);
        BRAND_IDS.put("desoto", 294);
        BRAND_IDS.put("dfsk", 303);
        BRAND_IDS.put("dodge", 32);
        BRAND_IDS.put("dongfeng", 37);
        BRAND_IDS.put("doninvest", 42);
        BRAND_IDS.put("donkervoort", 47);
        BRAND_IDS.put("dorcen", 396);
        BRAND_IDS.put("dr", 267);
        BRAND_IDS.put("drako", 291);
        BRAND_IDS.put("ds", 198);
        BRAND_IDS.put("e.go", 251);
        BRAND_IDS.put("eadon green", 237);
        BRAND_IDS.put("eagle", 52);
        BRAND_IDS.put("ebro", 357);
        BRAND_IDS.put("elaris", 335);
        BRAND_IDS.put("elemental", 257);
        BRAND_IDS.put("emc", 383);
        BRAND_IDS.put("engler", 272);
        BRAND_IDS.put("enovate", 395);
        BRAND_IDS.put("epicland", 394);
        BRAND_IDS.put("evo", 314);
        BRAND_IDS.put("exeed", 340);
        BRAND_IDS.put("fangchengbao", 370);
        BRAND_IDS.put("farizon", 389);
        BRAND_IDS.put("faw", 57);
        BRAND_IDS.put("felino", 219);
        BRAND_IDS.put("ferrari", 62);
        BRAND_IDS.put("fiat", 67);
        BRAND_IDS.put("firefly", 381);
        BRAND_IDS.put("fisker", 288);
        BRAND_IDS.put("fittipaldi", 227);
        BRAND_IDS.put("fomm", 232);
        BRAND_IDS.put("force motors", 243);
        BRAND_IDS.put("ford", 72);
        BRAND_IDS.put("forthing", 333);
        BRAND_IDS.put("foton", 385);
        BRAND_IDS.put("fso", 77);
        BRAND_IDS.put("fulwin", 391);
        BRAND_IDS.put("fuqi", 82);
        BRAND_IDS.put("gaz", 145);
        BRAND_IDS.put("geely", 87);
        BRAND_IDS.put("genesis", 202);
        BRAND_IDS.put("geo", 92);
        BRAND_IDS.put("geometry", 336);
        BRAND_IDS.put("gfg style", 274);
        BRAND_IDS.put("ginetta", 215);
        BRAND_IDS.put("gleagle", 262);
        BRAND_IDS.put("gmc", 97);
        BRAND_IDS.put("gordon murray", 307);
        BRAND_IDS.put("great wall", 107);
        BRAND_IDS.put("hafei", 112);
        BRAND_IDS.put("haima", 241);
        BRAND_IDS.put("haval", 214);
        BRAND_IDS.put("hawtai", 327);
        BRAND_IDS.put("hennessey", 220);
        BRAND_IDS.put("hindustan", 117);
        BRAND_IDS.put("hiphi", 377);
        BRAND_IDS.put("hispano suiza", 271);
        BRAND_IDS.put("holden", 122);
        BRAND_IDS.put("honda", 127);
        BRAND_IDS.put("hongqi", 296);
        BRAND_IDS.put("hsv", 264);
        BRAND_IDS.put("huanghai", 132);
        BRAND_IDS.put("hummer", 137);
        BRAND_IDS.put("hurtan", 142);
        BRAND_IDS.put("hycan", 397);
        BRAND_IDS.put("hyper", 367);
        BRAND_IDS.put("hyptec", 366);
        BRAND_IDS.put("hyundai", 147);
        BRAND_IDS.put("icar", 372);
        BRAND_IDS.put("icaur", 373);
        BRAND_IDS.put("ich-x", 351);
        BRAND_IDS.put("ickx", 321);
        BRAND_IDS.put("im", 360);
        BRAND_IDS.put("imsa", 239);
        BRAND_IDS.put("ineos", 310);
        BRAND_IDS.put("infiniti", 152);
        BRAND_IDS.put("innocenti", 157);
        BRAND_IDS.put("invicta", 162);
        BRAND_IDS.put("invicta electric", 323);
        BRAND_IDS.put("iran khodro", 167);
        BRAND_IDS.put("irmscher", 172);
        BRAND_IDS.put("isdera", 177);
        BRAND_IDS.put("isorivolta", 249);
        BRAND_IDS.put("isuzu", 182);
        BRAND_IDS.put("itala", 398);
        BRAND_IDS.put("italdesign", 225);
        BRAND_IDS.put("iveco", 187);
        BRAND_IDS.put("izh", 160);
        BRAND_IDS.put("jac", 192);
        BRAND_IDS.put("jaecoo", 352);
        BRAND_IDS.put("jaguar", 3);
        BRAND_IDS.put("jeep", 8);
        BRAND_IDS.put("jetour", 356);
        BRAND_IDS.put("jiangling", 18);
        BRAND_IDS.put("jmev", 359);
        BRAND_IDS.put("jy", 388);
        BRAND_IDS.put("kaiyi", 380);
        BRAND_IDS.put("karlmann king", 236);
        BRAND_IDS.put("karma", 280);
        BRAND_IDS.put("kgm", 337);
        BRAND_IDS.put("kia", 23);
        BRAND_IDS.put("kimera", 308);
        BRAND_IDS.put("koenigsegg", 28);
        BRAND_IDS.put("ktm", 33);
        BRAND_IDS.put("lada", 140);
        BRAND_IDS.put("lamborghini", 38);
        BRAND_IDS.put("lancia", 43);
        BRAND_IDS.put("land rover", 48);
        BRAND_IDS.put("landwind", 53);
        BRAND_IDS.put("ldv", 392);
        BRAND_IDS.put("leapmotor", 332);
        BRAND_IDS.put("lepas", 390);
        BRAND_IDS.put("levc", 331);
        BRAND_IDS.put("lexus", 58);
        BRAND_IDS.put("li", 363);
        BRAND_IDS.put("ligier", 361);
        BRAND_IDS.put("lincoln", 73);
        BRAND_IDS.put("linktour", 399);
        BRAND_IDS.put("lister", 230);
        BRAND_IDS.put("livan", 353);
        BRAND_IDS.put("lordstown", 326);
        BRAND_IDS.put("lotus", 78);
        BRAND_IDS.put("lti", 83);
        BRAND_IDS.put("luaz", 170);
        BRAND_IDS.put("lucid", 302);
        BRAND_IDS.put("luxeed", 368);
        BRAND_IDS.put("luxgen", 281);
        BRAND_IDS.put("lvchi", 233);
        BRAND_IDS.put("lynk co", 342);
        BRAND_IDS.put("m-hero", 343);
        BRAND_IDS.put("maextro", 387);
        BRAND_IDS.put("mahindra", 88);
        BRAND_IDS.put("marcos", 93);
        BRAND_IDS.put("maruti", 103);
        BRAND_IDS.put("maserati", 108);
        BRAND_IDS.put("maxus", 259);
        BRAND_IDS.put("maybach", 113);
        BRAND_IDS.put("mazda", 118);
        BRAND_IDS.put("mazzanti", 305);
        BRAND_IDS.put("mcc", 128);
        BRAND_IDS.put("mclaren", 123);
        BRAND_IDS.put("mega", 133);
        BRAND_IDS.put("melkus", 293);
        BRAND_IDS.put("mercedes-benz", 138);
        BRAND_IDS.put("mercury", 143);
        BRAND_IDS.put("metrocab", 148);
        BRAND_IDS.put("mg", 153);
        BRAND_IDS.put("micro", 311);
        BRAND_IDS.put("milan", 250);
        BRAND_IDS.put("minelli", 163);
        BRAND_IDS.put("minemobility", 268);
        BRAND_IDS.put("mini", 168);
        BRAND_IDS.put("mitsubishi", 173);
        BRAND_IDS.put("mitsuoka", 178);
        BRAND_IDS.put("moke", 329);
        BRAND_IDS.put("monte carlo", 183);
        BRAND_IDS.put("morgan", 188);
        BRAND_IDS.put("morris", 193);
        BRAND_IDS.put("moskvich", 175);
        BRAND_IDS.put("munro", 316);
        BRAND_IDS.put("mw motors", 238);
        BRAND_IDS.put("neta", 384);
        BRAND_IDS.put("nio", 295);
        BRAND_IDS.put("nissan", 4);
        BRAND_IDS.put("noble", 9);
        BRAND_IDS.put("o.s.c.a.", 24);
        BRAND_IDS.put("oldsmobile", 14);
        BRAND_IDS.put("omoda", 354);
        BRAND_IDS.put("onvo", 369);
        BRAND_IDS.put("opel", 19);
        BRAND_IDS.put("ora", 286);
        BRAND_IDS.put("pagani", 29);
        BRAND_IDS.put("panoz", 34);
        BRAND_IDS.put("pariss", 270);
        BRAND_IDS.put("paykan", 39);
        BRAND_IDS.put("perodua", 44);
        BRAND_IDS.put("peugeot", 49);
        BRAND_IDS.put("picasso", 313);
        BRAND_IDS.put("pininfarina", 234);
        BRAND_IDS.put("plymouth", 54);
        BRAND_IDS.put("polaris", 242);
        BRAND_IDS.put("polestar", 223);
        BRAND_IDS.put("pontiac", 59);
        BRAND_IDS.put("porsche", 64);
        BRAND_IDS.put("praga", 235);
        BRAND_IDS.put("premier", 69);
        BRAND_IDS.put("proton", 74);
        BRAND_IDS.put("puch", 79);
        BRAND_IDS.put("puma", 84);
        BRAND_IDS.put("puritalia", 275);
        BRAND_IDS.put("qiantu", 284);
        BRAND_IDS.put("qoros", 248);
        BRAND_IDS.put("qvale", 89);
        BRAND_IDS.put("ram", 240);
        BRAND_IDS.put("ravon", 253);
        BRAND_IDS.put("reliant", 94);
        BRAND_IDS.put("renault", 99);
        BRAND_IDS.put("renault samsung", 104);
        BRAND_IDS.put("riddara", 349);
        BRAND_IDS.put("rimac", 210);
        BRAND_IDS.put("rinspeed", 244);
        BRAND_IDS.put("rivian", 279);
        BRAND_IDS.put("roewe", 247);
        BRAND_IDS.put("rolls-royce", 109);
        BRAND_IDS.put("ronart", 114);
        BRAND_IDS.put("rover", 119);
        BRAND_IDS.put("rox", 378);
        BRAND_IDS.put("ruf", 209);
        BRAND_IDS.put("saab", 124);
        BRAND_IDS.put("saic", 376);
        BRAND_IDS.put("saleen", 129);
        BRAND_IDS.put("santana", 379);
        BRAND_IDS.put("saturn", 134);
        BRAND_IDS.put("sbarro", 276);
        BRAND_IDS.put("scg", 211);
        BRAND_IDS.put("scion", 139);
        BRAND_IDS.put("scout", 371);
        BRAND_IDS.put("seat", 144);
        BRAND_IDS.put("seaz", 180);
        BRAND_IDS.put("seres", 298);
        BRAND_IDS.put("shuanghuan", 149);
        BRAND_IDS.put("silence", 346);
        BRAND_IDS.put("sin cars", 199);
        BRAND_IDS.put("skoda", 154);
        BRAND_IDS.put("skywell", 386);
        BRAND_IDS.put("sma", 159);
        BRAND_IDS.put("smart", 164);
        BRAND_IDS.put("sono motors", 300);
        BRAND_IDS.put("sony", 289);
        BRAND_IDS.put("soueast", 169);
        BRAND_IDS.put("spectre", 174);
        BRAND_IDS.put("sportequipe", 322);
        BRAND_IDS.put("spyker", 179);
        BRAND_IDS.put("spyros panopoulos", 312);
        BRAND_IDS.put("ssangyong", 184);
        BRAND_IDS.put("ssc", 252);
        BRAND_IDS.put("stelato", 364);
        BRAND_IDS.put("subaru", 189);
        BRAND_IDS.put("suda", 317);
        BRAND_IDS.put("suzuki", 194);
        BRAND_IDS.put("swm", 350);
        BRAND_IDS.put("tagaz", 190);
        BRAND_IDS.put("talbot", 5);
        BRAND_IDS.put("tank", 374);
        BRAND_IDS.put("tata", 10);
        BRAND_IDS.put("tatra", 15);
        BRAND_IDS.put("techrules", 231);
        BRAND_IDS.put("tenet", 400);
        BRAND_IDS.put("tesla", 197);
        BRAND_IDS.put("tianma", 20);
        BRAND_IDS.put("tianye", 25);
        BRAND_IDS.put("tiger", 358);
        BRAND_IDS.put("tofas", 30);
        BRAND_IDS.put("togg", 319);
        BRAND_IDS.put("tonggong", 35);
        BRAND_IDS.put("toyota", 40);
        BRAND_IDS.put("trabant", 45);
        BRAND_IDS.put("tramontana", 297);
        BRAND_IDS.put("triumph", 50);
        BRAND_IDS.put("trumpchi", 269);
        BRAND_IDS.put("tvr", 55);
        BRAND_IDS.put("uaz", 195);
        BRAND_IDS.put("uniti", 287);
        BRAND_IDS.put("vanderhall", 261);
        BRAND_IDS.put("vauxhall", 60);
        BRAND_IDS.put("vector", 65);
        BRAND_IDS.put("vencer", 221);
        BRAND_IDS.put("venturi", 70);
        BRAND_IDS.put("vespa", 75);
        BRAND_IDS.put("vinfast", 260);
        BRAND_IDS.put("volkswagen", 80);
        BRAND_IDS.put("volvo", 85);
        BRAND_IDS.put("voyah", 338);
        BRAND_IDS.put("vuhl", 216);
        BRAND_IDS.put("vw-porsche", 90);
        BRAND_IDS.put("w motors", 218);
        BRAND_IDS.put("wartburg", 95);
        BRAND_IDS.put("weltmeister", 334);
        BRAND_IDS.put("westfield", 100);
        BRAND_IDS.put("wey", 226);
        BRAND_IDS.put("wiesmann", 105);
        BRAND_IDS.put("xiaomi", 339);
        BRAND_IDS.put("xin kai", 110);
        BRAND_IDS.put("xpeng", 266);
        BRAND_IDS.put("yangwang", 341);
        BRAND_IDS.put("zacua", 290);
        BRAND_IDS.put("zastava", 120);
        BRAND_IDS.put("zaz", 150);
        BRAND_IDS.put("zeekr", 324);
        BRAND_IDS.put("zenvo", 203);
        BRAND_IDS.put("zhidou", 246);
        BRAND_IDS.put("zil", 155);
        BRAND_IDS.put("zotye", 245);
        BRAND_IDS.put("zx", 130);
    }

    private final Map<String, String> discoveredBrands = new ConcurrentHashMap<>();

    private static final Pattern YEAR_PAT  = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern POWER_PAT = Pattern.compile("(\\d{2,4})\\s*(?:cv|hp|kw)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POWER_NUM = Pattern.compile("(?<![.\\d])(\\d{2,3})(?![.\\d])");
    private static final Pattern DISP_PAT  = Pattern.compile("(\\d[.,]\\d)");
    private static final Pattern ENDS_NUM  = Pattern.compile("-\\d+$");

    private static final List<String> FUEL_TOKENS = List.of(
            "tdi", "tsi", "tfsi", "gdi", "crdi", "cdti", "jtd", "hdi",
            "gte", "phev", "hybrid", "ibrido", "mhev", "mild",
            "mpi", "fsi", "gpl", "cng", "tce", "puretech", "bluehdi",
            "multijet", "dci", "vtec", "skyactiv"
    );

    private static final List<String> GEAR_TOKENS = List.of(
            "dsg", "dct", "cvt", "automatic", "automatico", "pdk",
            "s-tronic", "stronic", "xtronic", "tiptronic"
    );

    private static final Map<String, String> FUEL_ALIAS = Map.of(
            "diesel",   "tdi",
            "benzina",  "tsi",
            "petrol",   "tsi",
            "ibrido",   "hybrid",
            "electric", "bev",
            "elettrico","bev",
            "gpl",      "gpl",
            "metano",   "cng"
    );

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    // ══════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════

    public ScraperResult scrapeConRisultato(
            String marca, String modello, String motore, int anno,
            int potenzaCv, String tipoCarburante, String tipoCambio) {
        log.info("[AutoDataNet] Navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            String brandUrl = findBrandUrl(marca);
            if (brandUrl == null) {
                log.warn("[AutoDataNet] Marca non trovata: {}", marca);
                return ScraperResult.empty(anno);
            }
            log.debug("[AutoDataNet] Livello 1 - Marca: {}", brandUrl);

            String modelUrl = findModelUrl(brandUrl, modello);
            if (modelUrl == null) {
                log.warn("[AutoDataNet] Modello non trovato: {} su {}", modello, brandUrl);
                return ScraperResult.empty(anno);
            }

            // --- ESTRAZIONE PREFISSO ANTI-CONTAMINAZIONE ---
            String modelPrefix = extractModelPrefix(modelUrl);
            log.debug("[AutoDataNet] Livello 2 - Modello: {} (Prefisso Esatto: {})", modelUrl, modelPrefix);

            GenerazioneResult genResult = findGenerazioneUrlConAnno(modelUrl, anno, modelPrefix);
            if (genResult == null) {
                log.warn("[AutoDataNet] Generazione non trovata per anno {}", anno);
                return ScraperResult.empty(anno);
            }
            log.debug("[AutoDataNet] Livello 3 - Generazione: {} (annoEffettivo={}{})",
                    genResult.url(), genResult.annoEffettivo(),
                    genResult.isFallback() ? " [FALLBACK]" : "");

            String motorUrl = findMotorizzazioneUrl(
                    genResult.url(), motore, potenzaCv, tipoCarburante, tipoCambio, modelPrefix);
            if (motorUrl == null) {
                log.warn("[AutoDataNet] Motorizzazione non trovata: {}", motore);
                return ScraperResult.empty(anno);
            }
            log.debug("[AutoDataNet] Livello 4 - Motorizzazione: {}", motorUrl);

            Optional<String> testo = fetchSchedaTecnica(motorUrl);
            if (testo.isEmpty()) {
                return ScraperResult.empty(anno);
            }

            if (genResult.isFallback()) {
                log.warn("[AutoDataNet] Anno {} non disponibile: usata generazione {}. Anno salvato: {}",
                        anno, genResult.annoEffettivo(), genResult.annoEffettivo());
                return ScraperResult.foundWithFallback(testo.get(), anno, genResult.annoEffettivo());
            }
            return ScraperResult.found(testo.get(), anno);

        } catch (Exception e) {
            log.warn("[AutoDataNet] Errore: {}", e.getMessage());
            return ScraperResult.empty(anno);
        }
    }

    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        ScraperResult result = scrapeConRisultato(marca, modello, motore, anno, 0, null, null);
        return result.hasText() ? Optional.of(result.testo()) : Optional.empty();
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 1 — BRAND URL
    // ══════════════════════════════════════════════

    private String findBrandUrl(String marca) throws IOException {
        String key = normalize(marca);
        if (BRAND_IDS.containsKey(key)) return buildBrandUrl(key, BRAND_IDS.get(key));

        for (Map.Entry<String, Integer> e : BRAND_IDS.entrySet()) {
            String k = e.getKey();
            if (k.contains(key) || key.contains(k) || key.replace(" ", "").equals(k.replace(" ", "").replace("-", ""))) {
                return buildBrandUrl(k, e.getValue());
            }
        }

        String alias = resolveAlias(key);
        if (alias != null && BRAND_IDS.containsKey(alias)) return buildBrandUrl(alias, BRAND_IDS.get(alias));

        if (discoveredBrands.containsKey(key)) return discoveredBrands.get(key);

        String discovered = discoverBrandUrl(key);
        if (discovered != null) {
            discoveredBrands.put(key, discovered);
            return discovered;
        }

        return null;
    }

    private String buildBrandUrl(String nome, int id) {
        String slug = nome.replace(" ", "-");
        return BASE + "/it/" + slug + "-brand-" + id;
    }

    private String resolveAlias(String key) {
        return switch (key) {
            case "vw"          -> "volkswagen";
            case "mb", "merc" -> "mercedes-benz";
            case "bmwm"        -> "bmw";
            case "alfiero", "ar", "alfa" -> "alfa romeo";
            case "rangerover", "range rover" -> "land rover";
            default            -> null;
        };
    }

    private String discoverBrandUrl(String marca) {
        try {
            Document sitemap = Jsoup.connect(BASE + "/sitemap.xml")
                    .userAgent(userAgent).timeout(timeoutMs)
                    .ignoreHttpErrors(true).get();
            String slug = marca.replace(" ", "-");
            for (Element loc : sitemap.select("loc")) {
                String url = loc.text();
                if (url.contains("/" + slug + "-brand-")) return url;
            }
        } catch (Exception e) {
            log.debug("[AutoDataNet] Sitemap non disponibile: {}", e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 2 — MODELLO
    // ══════════════════════════════════════════════

    private String findModelUrl(String brandUrl, String modello) throws IOException {
        Document doc = fetch(brandUrl);
        List<LinkEntry> candidates = new ArrayList<>();

        for (Element a : doc.select("a[href*=-model-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            String name = normalize(a.text());
            if (name.isEmpty()) {
                name = normalize(href.replaceAll(".*/it/", "").replaceAll("-model-\\d+.*", "").replace("-", " "));
            }
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href, 0, null));
        }

        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        return bestModelMatch(normalize(modello), candidates);
    }

    private String bestModelMatch(String modello, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;
        String mNorm    = modello.replace("-", " ").trim();
        String mCompact = mNorm.replace(" ", "");

        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").equals(mNorm)) return c.url();

        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").replace(" ", "").equals(mCompact)) return c.url();

        String[] tokens = mNorm.split("\\s+");

        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            List<String> cnTokens = Arrays.asList(cn.split("\\s+"));
            boolean allMatch = true;
            for (String t : tokens) {
                if (!cnTokens.contains(t)) { allMatch = false; break; }
            }
            if (allMatch) return c.url();
        }

        String best = null;
        int bestScore = 0;

        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            List<String> cnTokens = Arrays.asList(cn.split("\\s+"));
            String cnCompact = cn.replace(" ", "");
            int score = 0;

            for (String t : tokens) {
                if (t.length() >= 1) {
                    if (cnTokens.contains(t)) score += 5;
                    else if (cn.contains(t)) score += 1;
                }
            }

            int prefixLen = Math.min(3, mCompact.length());
            if (cnCompact.startsWith(mCompact.substring(0, prefixLen))) score += 2;

            if (score > bestScore) {
                bestScore = score;
                best = c.url();
            }
        }

        return best;
    }

    private String extractModelPrefix(String modelUrl) {
        if (modelUrl == null) return null;
        Matcher m = Pattern.compile("/it/([^/]+)-model-\\d+").matcher(modelUrl);
        if (m.find()) {
            return "/it/" + m.group(1) + "-";
        }
        return null;
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 3 — GENERAZIONE
    // ══════════════════════════════════════════════

    private GenerazioneResult findGenerazioneUrlConAnno(String modelUrl, int anno, String modelPrefix) throws IOException {
        Document doc = fetch(modelUrl);

        String bestUrl    = null;
        int    bestFrom   = Integer.MIN_VALUE;
        int    bestAnnoEff = anno;
        String fallbackUrl = null;
        int    fallbackAnno = anno;

        for (Element a : doc.select("a[href*=-generation-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;

            // --- FILTRO ANTI-CONTAMINAZIONE ---
            if (modelPrefix != null && !href.contains(modelPrefix)) {
                continue;
            }

            if (fallbackUrl == null) {
                fallbackUrl = href;
                Element parent = a.closest("div, li, tr, td");
                String ctx = (parent != null ? parent.text() : "") + " " + a.text();
                int[] range = extractYearRange(ctx);
                if (range != null) fallbackAnno = range[0];
            }

            Element parent = a.closest("div, li, tr, td");
            String ctx = (parent != null ? parent.text() : "") + " " + a.text();
            int[] range = extractYearRange(ctx);
            if (range == null) continue;

            int from = range[0], to = range[1];
            if (anno >= from && anno <= to && from > bestFrom) {
                bestFrom    = from;
                bestUrl     = href;
                bestAnnoEff = anno;
            }
        }

        if (bestUrl != null) return new GenerazioneResult(bestUrl, bestAnnoEff, false);
        if (fallbackUrl != null) return new GenerazioneResult(fallbackUrl, fallbackAnno, true);

        return null;
    }

    private int[] extractYearRange(String text) {
        List<Integer> years = new ArrayList<>();
        Matcher m = YEAR_PAT.matcher(text);
        while (m.find()) years.add(Integer.parseInt(m.group()));
        if (years.isEmpty()) return null;
        Collections.sort(years);
        return new int[]{ years.get(0), years.size() > 1 ? years.get(years.size() - 1) : 2099 };
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 4 — MOTORIZZAZIONE
    // ══════════════════════════════════════════════

    private String findMotorizzazioneUrl(
            String genUrl, String motore,
            int potenzaCvUtente, String tipoCarburanteUtente, String tipoCambioUtente, String modelPrefix)
            throws IOException {

        Document doc = fetch(genUrl);
        String ml = normalize(motore);

        int reqPower = potenzaCvUtente > 0 ? potenzaCvUtente : extractPower(ml);
        String reqDisp  = extractDisplacement(ml);
        List<String> reqFuel = buildFuelTokens(ml, tipoCarburanteUtente);
        List<String> reqGear = buildGearTokens(ml, tipoCambioUtente);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (!isMotorUrl(href, genUrl)) continue;

            // --- FILTRO ANTI-CONTAMINAZIONE ---
            if (modelPrefix != null && !href.contains(modelPrefix)) {
                continue;
            }

            String rawName = a.text();
            String name = normalize(rawName);

            if (name.isEmpty()) {
                name = normalize(href.replaceAll(".*/it/", "").replace("-", " ").replaceAll("\\d+$", "").trim());
            }

            if (!name.isEmpty()) {
                int cv = extractPower(rawName);
                String cilindrata = extractDisplacement(rawName);
                candidates.add(new LinkEntry(name, href, cv, cilindrata));
            }
        }

        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        if (candidates.isEmpty()) return null;

        String bestUrl = null;
        int bestScore = Integer.MIN_VALUE;

        for (LinkEntry c : candidates) {
            String urlL  = c.url().toLowerCase();
            String nameL = c.name();
            int score = 0;

            // --- Carburante ---
            if (!reqFuel.isEmpty()) {
                boolean fuelMatch = false;
                for (String ft : reqFuel)
                    if (urlL.contains(ft) || nameL.contains(ft)) { fuelMatch = true; break; }
                if (fuelMatch) score += 10;
                else           score -= 5;
            }

            // --- Potenza CV ---
            if (reqPower > 0) {
                int candCv = c.cv();
                if (candCv == 0) {
                    Matcher hpMatcher = Pattern.compile("(\\d{2,4})hp").matcher(urlL);
                    if (hpMatcher.find()) {
                        try { candCv = Integer.parseInt(hpMatcher.group(1)); } catch (NumberFormatException ignored) {}
                    }
                }

                if (candCv > 0) {
                    int diff = Math.abs(candCv - reqPower);
                    if (diff == 0)        score += 15;
                    else if (diff <= 10)  score += 10;
                    else if (diff <= 20)  score += 5;
                    else if (diff > 40)   score -= 20;
                } else if (nameL.contains(String.valueOf(reqPower))) {
                    score += 3;
                }
            }

            // --- Cilindrata ---
            if (reqDisp != null) {
                String d = reqDisp.replace(",", ".");
                if (c.cilindrata() != null && c.cilindrata().equals(d)) {
                    score += 3;
                } else if (urlL.contains(d) || nameL.contains(d)) {
                    score += 1;
                }
            }

            // --- Cambio ---
            if (!reqGear.isEmpty()) {
                for (String gt : reqGear)
                    if (urlL.contains(gt) || nameL.contains(gt)) { score += 2; break; }
            }

            log.debug("[AutoDataNet] Motor score={}: {} -> {}", score, nameL, c.url());
            if (score > bestScore) { bestScore = score; bestUrl = c.url(); }
        }

        if (bestScore <= -10) {
            log.warn("[AutoDataNet] Nessun motor con score accettabile (best={}), uso primo candidato", bestScore);
            return candidates.get(0).url();
        }

        return bestUrl != null ? bestUrl : candidates.get(0).url();
    }

    private List<String> buildFuelTokens(String motoreNorm, String tipoCarburanteUtente) {
        List<String> tokens = new ArrayList<>(matchTokens(motoreNorm, FUEL_TOKENS));
        if (tipoCarburanteUtente != null && !tipoCarburanteUtente.isBlank()) {
            String alias = FUEL_ALIAS.get(tipoCarburanteUtente.toLowerCase().trim());
            if (alias != null && !tokens.contains(alias)) {
                tokens.add(0, alias);
            }
        }
        return tokens;
    }

    private List<String> buildGearTokens(String motoreNorm, String tipoCambioUtente) {
        List<String> tokens = new ArrayList<>(matchTokens(motoreNorm, GEAR_TOKENS));
        if (tipoCambioUtente != null && !tipoCambioUtente.isBlank()) {
            String t = normalize(tipoCambioUtente);
            if (!tokens.contains(t)) tokens.add(0, t);
        }
        return tokens;
    }

    private boolean isMotorUrl(String href, String genUrl) {
        if (href == null || href.isBlank()) return false;
        if (!href.startsWith(BASE + "/it/")) return false;
        if (href.contains("-generation-") || href.contains("-model-") ||
                href.contains("-brand-") || href.contains("/login") ||
                href.contains("/register") || href.contains("/allbrands") ||
                href.contains("/results") || href.equals(genUrl)) return false;
        return ENDS_NUM.matcher(href).find();
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 5 — SCHEDA TECNICA (PERFEZIONATO)
    // ══════════════════════════════════════════════

    private Optional<String> fetchSchedaTecnica(String url) throws IOException {
        Document doc = fetch(url);

        // 1. PULIZIA AGGRESSIVA STRUTTURALE
        // Rimuoviamo tutto ciò che non fa parte dei dati del veicolo per evitare che Gemini
        // legga dati spazzatura o pubblicità.
        doc.select("nav, header, footer, script, style, iframe, .ad970, .ads, .cookie, noscript").remove();

        // 2. RIMOZIONE DELLE "ALTRE AUTO" E "MODIFICHE SIMILI"
        // auto-data.net aggiunge in fondo alla pagina tabelle con le auto della stessa
        // generazione o i modelli precedenti. Gemini si confonde facilmente e mischia i dati.
        doc.select("a[href*=-model-], a[href*=-generation-], .breadcrumb").remove();
        doc.select("table a").remove(); // Polverizza i link nelle tabelle ("Altre versioni")

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append(doc.title()).append("\n\n");

        // 3. ESTRAZIONE MIRATA (Solo le tabelle delle specifiche pulite)
        for (Element table : doc.select("table")) {
            for (Element row : table.select("tr")) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 2) {
                    String label = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();

                    // Condizione stringente: se la label è troppo lunga, probabilmente
                    // è un blocco di testo descrittivo o spazzatura, lo ignoriamo.
                    if (!label.isEmpty() && !value.isEmpty() && label.length() < 60) {
                        sb.append(label).append(": ").append(value).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        // 4. STOP AL FALLBACK SUL BODY
        // Precedentemente veniva letto il body.text() se il testo era < 400.
        // Questo era devastante perché portava dentro testo inutile e di altre auto.
        // Ora esigiamo che ci siano almeno 100 caratteri di tabelle vere, altrimenti è vuoto.
        String finalOutput = sb.toString().trim();
        return finalOutput.length() > 100 ? Optional.of(finalOutput) : Optional.empty();
    }

    // ══════════════════════════════════════════════
    //  UTILITY
    // ══════════════════════════════════════════════

    private int extractPower(String motore) {
        Matcher m = POWER_PAT.matcher(motore);
        while (m.find()) {
            try { int v = Integer.parseInt(m.group(1)); if (v >= 40 && v <= 900) return v; }
            catch (NumberFormatException ignored) {}
        }
        Matcher fb = POWER_NUM.matcher(motore);
        while (fb.find()) {
            try { int v = Integer.parseInt(fb.group(1)); if (v >= 40 && v <= 900) return v; }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String extractDisplacement(String motore) {
        Matcher m = DISP_PAT.matcher(motore);
        return m.find() ? m.group(1).replace(",", ".") : null;
    }

    private List<String> matchTokens(String text, List<String> tokens) {
        List<String> found = new ArrayList<>();
        for (String t : tokens) if (text.contains(t)) found.add(t);
        return found;
    }

    private String absoluteHref(Element a) {
        String href = a.attr("href");
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        if (href.startsWith("/")) return BASE + href;
        return "";
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("[àáâã]", "a").replaceAll("[èéêë]", "e")
                .replaceAll("[ìíîï]", "i").replaceAll("[òóôõ]", "o")
                .replaceAll("[ùúûü]", "u").replaceAll("[^a-z0-9 \\-]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.8,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .referrer("https://www.auto-data.net/it/")
                .get();
    }

    private record LinkEntry(String name, String url, int cv, String cilindrata) {}

    private record GenerazioneResult(String url, int annoEffettivo, boolean isFallback) {}
}