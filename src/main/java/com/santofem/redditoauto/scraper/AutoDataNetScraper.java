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
    // Pattern esatti basati sulla struttura di auto-data.net
    private static final Pattern PATTERN_BRAND = Pattern.compile(".*-brand-\\d+");
    private static final Pattern PATTERN_MODEL = Pattern.compile(".*-model-\\d+");
    private static final Pattern PATTERN_GENERATION = Pattern.compile(".*-generation-\\d+");
    // L'auto finale non ha keyword specifiche prima dell'ID, ma termina con un numero
    private static final Pattern PATTERN_VEHICLE = Pattern.compile(".*-\\d+$");

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
    // Pattern per range espliciti come "2015-2018" o "2015–2018" (con eventuale end mancante)
    private static final Pattern RANGE_PAT = Pattern.compile("(20|19)\\d{2}\\s*[–—-]\\s*((?:20|19)\\d{2})?");
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

            Optional<SchedaData> scheda = fetchSchedaTecnica(motorUrl);
            if (scheda.isEmpty()) {
                return ScraperResult.empty(anno);
            }

            // Se la scheda fornisce from/to più precisi, usali per persistere annoFine
            Integer schedaFrom = scheda.get().annoFrom();
            Integer schedaTo   = scheda.get().annoTo();
            int finalFrom = schedaFrom != null ? schedaFrom : genResult.from();
            int finalTo   = schedaTo   != null ? schedaTo   : genResult.to();

            if (genResult.isFallback()) {
                log.warn("[AutoDataNet] Anno {} non disponibile: usata generazione {}. Anno salvato: {}",
                        anno, genResult.annoEffettivo(), genResult.annoEffettivo());
                return ScraperResult.foundWithFallback(scheda.get().testo(), anno, genResult.annoEffettivo(), finalFrom, finalTo);
            }
            return ScraperResult.found(scheda.get().testo(), anno, finalFrom, finalTo);

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

// ══════════════════════════════════════════════
    //  SCORING INTELLIGENTE DEL MODELLO
    // ══════════════════════════════════════════════

    private String bestModelMatch(String modello, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;
        String mNorm    = normalize(modello).trim();
        String mCompact = mNorm.replace(" ", "");

        // 1. Match Esatto (Priorità assoluta)
        for (LinkEntry c : candidates) {
            if (c.name().equals(mNorm)) return c.url();
        }

        // 2. Match compatto (es. "Serie 3" vs "Serie3")
        for (LinkEntry c : candidates) {
            if (c.name().replace(" ", "").equals(mCompact)) return c.url();
        }

        // Elenco delle parole relative a varianti di carrozzeria da scartare se non richieste esplicitamente
        List<String> variantiIndesiderate = Arrays.asList(
                "sedan", "variant", "cross", "cabriolet", "cabrio", "estate",
                "touring", "sportback", "alltrack", "gti", "sw", "plus", "cc"
        );

        // 3. Assegnazione Punteggio (Se il match esatto fallisce)
        String bestUrl = null;
        int bestScore = -1000;
        String[] tokens = mNorm.split("\\s+");
        List<String> userTokensList = Arrays.asList(tokens);

        for (LinkEntry c : candidates) {
            String cn = c.name();
            List<String> cnTokens = Arrays.asList(cn.split("\\s+"));
            int score = 0;
            boolean containsAll = true;

            for (String t : tokens) {
                if (cnTokens.contains(t)) {
                    score += 10;
                } else if (cn.contains(t)) {
                    score += 3;
                } else {
                    containsAll = false;
                }
            }

            if (containsAll) score += 20;

            // --- NOVITÀ: PENALITÀ ANTI-VARIANTE ---
            // Se il nome candidato contiene "sedan" ma tu non l'hai scritto, viene distrutto in classifica
            for (String variant : variantiIndesiderate) {
                if (cnTokens.contains(variant) && !userTokensList.contains(variant)) {
                    score -= 50; // Penalità severissima
                }
            }

            // Penalità per parole extra (per preferire i nomi corti e puliti)
            int extraWords = cnTokens.size() - tokens.length;
            if (extraWords > 0) {
                score -= (extraWords * 3);
            }

            if (score > bestScore) {
                bestScore = score;
                bestUrl = c.url();
            }
        }

        return bestUrl != null ? bestUrl : candidates.getFirst().url();
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
        int    minDiff = Integer.MAX_VALUE; // Memorizza la distanza di anni minima

        int bestTo = Integer.MIN_VALUE;
        int fallbackTo = Integer.MIN_VALUE;
        for (Element a : doc.select("a[href*=-generation-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;

            // Filtro anti-contaminazione
            if (modelPrefix != null && !href.contains(modelPrefix)) continue;

            String ctx = gatherNearbyText(a);
            int[] range = extractYearRange(ctx);
            if (range == null) continue;

            int from = range[0], to = range[1];

            // Match Perfetto: L'anno rientra nella generazione
            if (anno >= from && anno <= to && from > bestFrom) {
                bestFrom    = from;
                bestTo      = to;
                bestUrl     = href;
                bestAnnoEff = anno;
            }

            // CALCOLO FALLBACK INTELLIGENTE (La vera correzione del bug)
            // Calcola quanti anni di distanza ci sono tra l'anno cercato e questa generazione
            int diffToFrom = Math.abs(anno - from);
            int diffToTo = Math.abs(anno - to);
            int currentDiff = Math.min(diffToFrom, diffToTo);

            // Se non abbiamo ancora un match perfetto, teniamo in memoria la generazione più vicina
            if (bestUrl == null && currentDiff < minDiff) {
                minDiff = currentDiff;
                fallbackUrl = href;
                // Imposta come anno di fallback l'estremo più vicino a quello cercato
                fallbackAnno = (anno < from) ? from : to;
                fallbackTo = to;
            }
        }

        if (bestUrl != null) return new GenerazioneResult(bestUrl, bestAnnoEff, false, bestFrom, bestTo == Integer.MIN_VALUE ? 2099 : bestTo);
        if (fallbackUrl != null) return new GenerazioneResult(fallbackUrl, fallbackAnno, true, (bestFrom == Integer.MIN_VALUE ? fallbackAnno : bestFrom), fallbackTo == Integer.MIN_VALUE ? fallbackAnno : fallbackTo);

        return null;
    }
    private int[] extractYearRange(String text) {
        if (text == null) return null;
        // 1) Proviamo a matchare esplicitamente range tipo "2015 - 2018" o "2015–2018" (anche con end mancante)
        Matcher r = RANGE_PAT.matcher(text);
        if (r.find()) {
            int from = Integer.parseInt(r.group(0).replaceAll("[^0-9].*", "").replaceAll("[^0-9]", ""));
            String g2 = r.group(2);
            int to = (g2 != null && !g2.isEmpty()) ? Integer.parseInt(g2) : 2099;
            return new int[]{from, to};
        }

        // 2) Fallback: raccogliamo tutte le occorrenze di anni nel contesto e usiamo min/max
        List<Integer> years = new ArrayList<>();
        Matcher m = YEAR_PAT.matcher(text);
        while (m.find()) years.add(Integer.parseInt(m.group()));
        if (years.isEmpty()) return null;
        Collections.sort(years);
        int from = years.get(0);
        int to = years.size() > 1 ? years.get(years.size() - 1) : from;

        // Se c'è solo un anno e il testo contiene un trattino senza anno dopo (es. "2015–") consideriamo end aperto
        if (years.size() == 1) {
            if (text.matches(".*[–—-]\s*$")) {
                to = 2099;
            }
        }
        return new int[]{from, to};
    }

    /**
     * Raccoglie testo utile attorno al link della generazione: testo dell'elemento, parent, siblings, grandparent.
     */
    private String gatherNearbyText(Element a) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(a.text()).append(" ");
            Element parent = a.parent();
            if (parent != null) sb.append(parent.text()).append(" ");
            Element grand = parent != null ? parent.parent() : null;
            if (grand != null) sb.append(grand.text()).append(" ");
            Element closest = a.closest("li, tr, div, td, span, p");
            if (closest != null) sb.append(closest.text()).append(" ");
            Element prev = a.previousElementSibling();
            if (prev != null) sb.append(prev.text()).append(" ");
            Element next = a.nextElementSibling();
            if (next != null) sb.append(next.text()).append(" ");
        } catch (Exception e) {
            // Non fallire per problemi di parsing del DOM
        }
        return sb.toString();
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

        String[] userTokens = ml.split("\\s+");

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (!isMotorUrl(href, genUrl)) continue;

            if (modelPrefix != null && !href.contains(modelPrefix)) continue;

            // --- NOVITÀ: ESTRARRE IL NOME DALL'URL E NON DAL TESTO HTML ---
            // L'url è tipo: .../volkswagen-polo-iv-9n-1.4-tdi-75hp-8450
            String urlSlug = href.substring(href.lastIndexOf("/") + 1); // Prende l'ultima parte
            urlSlug = urlSlug.replaceAll("-\\d+$", ""); // Rimuove l'ID finale (es. -8450)

            // Uniamo il testo visibile con le parole dell'URL per avere la certezza di trovare cilindrata e cavalli
            String rawName = a.text() + " " + urlSlug.replace("-", " ");
            String name = normalize(rawName);

            if (!name.isEmpty()) {
                int cv = extractPower(name);
                String cilindrata = extractDisplacement(name);
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

            // --- 1. MATCH DELLE PAROLE ---
            int tokenMatches = 0;
            for (String t : userTokens) {
                if (t.length() > 1 && (nameL.contains(t) || urlL.contains(t))) {
                    score += 10;
                    tokenMatches++;
                }
            }
            if (tokenMatches > 0 && tokenMatches == userTokens.length) {
                score += 30;
            }

            // --- 2. CARBURANTE ---
            if (!reqFuel.isEmpty()) {
                boolean fuelMatch = false;
                for (String ft : reqFuel)
                    if (urlL.contains(ft) || nameL.contains(ft)) { fuelMatch = true; break; }

                if (fuelMatch) {
                    score += 20;
                } else {
                    score -= 40;
                }
            }

            // --- 3. POTENZA CV ---
            if (reqPower > 0) {
                int candCv = c.cv();
                if (candCv > 0) {
                    int diff = Math.abs(candCv - reqPower);
                    if (diff == 0)        score += 30;
                    else if (diff <= 5)   score += 10;
                    else if (diff <= 15)  score += 0;
                    else                  score -= 30;
                } else if (nameL.contains(String.valueOf(reqPower))) {
                    score += 10;
                }
            }

            // --- 4. CILINDRATA (CON ANTI-INTRUSO SEVERISSIMO) ---
            if (reqDisp != null) {
                String reqD = reqDisp.replace(",", ".");
                String candDisp = c.cilindrata();

                if (candDisp != null) {
                    if (candDisp.equals(reqD)) {
                        score += 30;
                    } else {
                        score -= 80; // PENALITÀ ESTREMA: Cilindrata diversa scartata
                    }
                } else if (urlL.contains(reqD) || nameL.contains(reqD)) {
                    score += 15;
                } else {
                    score -= 30;
                }
            }

            // --- 5. CAMBIO ---
            if (!reqGear.isEmpty()) {
                for (String gt : reqGear)
                    if (urlL.contains(gt) || nameL.contains(gt)) { score += 10; break; }
            }

            log.debug("[AutoDataNet] Valutazione motore '{}' -> Score: {}", nameL, score);

            if (score > bestScore) {
                bestScore = score;
                bestUrl = c.url();
            }
        }

        if (bestScore <= -20) {
            log.warn("[AutoDataNet] Nessun motore con requisiti accettabili, uso il fallback di sicurezza");
            return candidates.getFirst().url();
        }

        return bestUrl != null ? bestUrl : candidates.getFirst().url();
    }

    private List<String> buildFuelTokens(String motoreNorm, String tipoCarburanteUtente) {
        List<String> tokens = new ArrayList<>(matchTokens(motoreNorm, FUEL_TOKENS));
        if (tipoCarburanteUtente != null && !tipoCarburanteUtente.isBlank()) {
            String alias = FUEL_ALIAS.get(tipoCarburanteUtente.toLowerCase().trim());
            if (alias != null && !tokens.contains(alias)) {
                tokens.addFirst(alias);
            }
        }
        return tokens;
    }

    private List<String> buildGearTokens(String motoreNorm, String tipoCambioUtente) {
        List<String> tokens = new ArrayList<>(matchTokens(motoreNorm, GEAR_TOKENS));
        if (tipoCambioUtente != null && !tipoCambioUtente.isBlank()) {
            String t = normalize(tipoCambioUtente);
            if (!tokens.contains(t)) tokens.addFirst(t);
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
    //  LIVELLO 5 — SCHEDA TECNICA
    // ══════════════════════════════════════════════

    private static record SchedaData(String testo, Integer annoFrom, Integer annoTo) {}

    private Optional<SchedaData> fetchSchedaTecnica(String url) throws IOException {
        Document doc = fetch(url);

        // 1. PULIZIA AGGRESSIVA STRUTTURALE
        doc.select("nav, header, footer, script, style, iframe, .ad970, .ads, .cookie, noscript").remove();
        doc.select(".breadcrumb, .similar-cars, a[href*=-model-], a[href*=-generation-]").remove();
        doc.select("table a").remove(); // Rimuove i link dentro le tabelle

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append("TITOLO: ").append(doc.title()).append("\n\n");

        // 2. ESTRAZIONE DI TUTTE LE TABELLE RIMASTE NEL DOM PULITO
        Elements tabelleTecniche = doc.select("table");

        Integer foundFrom = null;
        Integer foundTo = null;
        List<String> savedRows = new ArrayList<>();

        for (Element table : tabelleTecniche) {
            for (Element row : table.select("tr")) {
                Elements th = row.select("th"); // Es. "Consumo di carburante"
                Elements td = row.select("td"); // Es. "5.5 l/100km"

                // A volte le tabelle auto-data usano due <td> invece di th/td
                if (th.isEmpty() && td.size() >= 2) {
                    th = new Elements(td.get(0));
                    td = new Elements(td.get(1));
                }

                if (!th.isEmpty() && !td.isEmpty()) {
                    String label = th.text().trim();
                    String value = td.text().trim();

                    // Proviamo a catturare in modo esplicito i campi Inizio/Fine anno di produzione
                    String labelNorm = label.toLowerCase();
                    if (labelNorm.contains("inizio") && labelNorm.contains("anno")) {
                        Matcher m = YEAR_PAT.matcher(value);
                        if (m.find()) {
                            try { foundFrom = Integer.parseInt(m.group());
                                log.debug("[AutoDataNet] Parsed annoInizio from '{}': {}", value, foundFrom);
                            } catch (NumberFormatException ignored) { log.debug("[AutoDataNet] Failed to parse annoInizio '{}'", m.group()); }
                        } else {
                            log.debug("[AutoDataNet] No year match in annoInizio value='{}'", value);
                        }
                    } else if (labelNorm.contains("fine") && labelNorm.contains("anno")) {
                        Matcher m = YEAR_PAT.matcher(value);
                        if (m.find()) {
                            try { foundTo = Integer.parseInt(m.group());
                                log.debug("[AutoDataNet] Parsed annoFine from '{}': {}", value, foundTo);
                            } catch (NumberFormatException ignored) { log.debug("[AutoDataNet] Failed to parse annoFine '{}'", m.group()); }
                        } else {
                            log.debug("[AutoDataNet] No year match in annoFine value='{}'", value);
                        }
                    }

                    // Salviamo solo se le righe sono sensate (label corte)
                    if (!label.isEmpty() && !value.isEmpty() && label.length() < 120) {
                        String rowLine = label + ": " + value;
                        sb.append(rowLine).append("\n");
                        savedRows.add(rowLine);
                    }
                }
            }
        }

        String finalOutput = sb.toString().trim();
        log.debug("[AutoDataNet] fetchSchedaTecnica: collected {} rows, finalOutput.length={}", savedRows.size(), finalOutput.length());
        if (!savedRows.isEmpty()) log.debug("[AutoDataNet] Sample rows: {}", savedRows.subList(0, Math.min(8, savedRows.size())));

        // Restituisci SchedaData anche se il testo è corto, ma sono stati trovati annoFrom/annoTo
        if (foundFrom != null || foundTo != null || finalOutput.length() > 120) {
            return Optional.of(new SchedaData(finalOutput, foundFrom, foundTo));
        }

        return Optional.empty();
    }    // ══════════════════════════════════════════════
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
        if (href.isBlank()) return "";
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

    private record GenerazioneResult(String url, int annoEffettivo, boolean isFallback, int from, int to) {}
}