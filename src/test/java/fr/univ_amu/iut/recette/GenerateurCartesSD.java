package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.recette.SpecCarteSd.Enregistreur;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/// Générateur **déterministe** de cartes SD de recette : d'une [SpecCarteSd], il écrit sur disque
/// l'arbre attendu par l'import (`LogPR<serie>.txt`, `PaRecPR<serie>_THLog.csv`, `bruts/*.wav`).
///
/// Il ne tire **aucune** date de l'horloge ni **aucun** octet aléatoire : deux exécutions sur la même
/// spec produisent des octets identiques. C'est cette propriété qui permet de remplacer les ~530 Mo de
/// cartes SD faites main (non versionnables) par des specs de quelques kilo-octets.
///
/// **Portée test** : l'application distribuée n'embarque pas cette génération de fixtures ; ce sont les
/// tests qui fabriquent WAV/ZIP. Le [#main] sert à matérialiser un dossier pour la recette manuelle
/// (cf. `dev-docs/recette/fixtures.md`), et [#genererVers] est appelé directement par le garde-fou.
public final class GenerateurCartesSD {

    private static final String SOUS_DOSSIER_BRUTS = "bruts";

    private static final DateTimeFormatter FORMAT_JOURNAL = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT);
    private static final DateTimeFormatter FORMAT_THLOG = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter FORMAT_HEURE_THLOG = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter FORMAT_HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private final LecteurSpec lecteur = new LecteurSpec();

    /// Point d'entrée pour matérialiser une ou plusieurs cartes SD : `<spec.yaml | dossierSpecs> <dest>`.
    /// Chaque carte est écrite dans un sous-dossier `<dest>/<fixture>`.
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            System.err.println("Usage : GenerateurCartesSD <spec.yaml | dossierSpecs> <dossierDest>");
            System.exit(2);
            return;
        }
        Path source = Path.of(arguments[0]);
        Path destination = Path.of(arguments[1]);
        GenerateurCartesSD generateur = new GenerateurCartesSD();
        List<Path> specs = specsDe(source);
        for (Path spec : specs) {
            Path carte = generateur.genererDepuisFichier(spec, destination);
            System.out.println("Carte SD générée : " + carte);
        }
        System.out.println(specs.size() + " carte(s) générée(s) dans " + destination);
    }

    /// Lit la spec `fichierSpec` et matérialise la carte dans `<destParente>/<fixture>`.
    ///
    /// @return le chemin du dossier de carte généré
    Path genererDepuisFichier(Path fichierSpec, Path destParente) throws IOException {
        SpecCarteSd spec = lecteur.lire(fichierSpec);
        Path carte = destParente.resolve(spec.fixture());
        genererVers(spec, carte);
        return carte;
    }

    /// Écrit l'arbre SD décrit par `spec` **directement** sous `racineSd` (journal, relevé, `bruts/`).
    void genererVers(SpecCarteSd spec, Path racineSd) throws IOException {
        Path bruts = racineSd.resolve(SOUS_DOSSIER_BRUTS);
        Files.createDirectories(bruts);

        if (spec.journal().present()) {
            Path journal = racineSd.resolve("LogPR" + spec.journal().serie() + ".txt");
            Files.write(journal, lignesJournal(spec), StandardCharsets.UTF_8);
        }
        if (spec.thlog().present()) {
            Path releve = racineSd.resolve("PaRecPR" + serieReleve(spec) + "_THLog.csv");
            Files.writeString(releve, contenuThlog(spec), StandardCharsets.UTF_8);
        }
        ecrireBruts(spec, bruts);
    }

    private static void ecrireBruts(SpecCarteSd spec, Path bruts) throws IOException {
        int trames = FabriqueWav.tramesPour(spec.wav().frequenceHz(), spec.wav().dureeSecondes());
        byte[] octets = FabriqueWav.octetsWav(spec.wav().frequenceHz(), trames);
        for (Enregistreur enregistreur : spec.enregistreurs()) {
            for (String horodatage : enregistreur.horodatages()) {
                Path fichier = bruts.resolve("PaRecPR" + enregistreur.serie() + "_" + horodatage + ".wav");
                Files.write(fichier, octets);
            }
        }
    }

    private static List<String> lignesJournal(SpecCarteSd spec) {
        String serie = spec.journal().serie();
        LocalDate nuit = spec.journal().nuit();
        String soir = nuit.format(FORMAT_JOURNAL);
        String matin = nuit.plusDays(1).format(FORMAT_JOURNAL);

        List<String> lignes = new ArrayList<>();
        lignes.add(ligne(soir, "16:02:20", serie, "Test accès carte SD"));
        lignes.add(ligne(soir, "16:02:20", serie, "=========================================="));
        lignes.add(ligne(
                soir,
                "16:02:20",
                serie,
                "Démarrage Passive Recorder numéro de série " + serie + ", V1.01, CPU 600000000, T4.1"));
        lignes.add(ligne(soir, "16:02:21", serie, "### Passage en mode Protocole Point fixe"));
        if (spec.journal().sondePresente()) {
            lignes.add(
                    ligne(soir, "16:02:21", serie, "Sonde température/hygrométrie présente, lecture toutes les 600s"));
        }
        lignes.add(ligne(soir, "16:02:21", serie, "Batteries internes 4.1V (90%) (MCP3221)"));
        lignes.add(ligne(
                soir,
                "16:02:21",
                serie,
                "Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH 00, S. R. 16dB 1dt. GN0,"
                        + " Bd. Freq. 8-120kHz, Wav 2-30s SD 99%"));
        lignes.add(ligne(soir, "20:26:13", serie, "Wakeup by ALARM... Cpt 1"));
        lignes.add(ligne(matin, "07:48:00", serie, "### Passage en mode Veille"));
        lignes.add(ligne(matin, "07:52:21", serie, "Mise en veille, réveil à 20:25, Bat. Interne 4.0 90%"));
        return lignes;
    }

    private static String ligne(String date, String heure, String serie, String message) {
        return date + " - " + heure + " PR" + serie + " " + message;
    }

    private static String contenuThlog(SpecCarteSd spec) {
        String jour = dateReference(spec).format(FORMAT_THLOG);
        StringBuilder contenu = new StringBuilder("Date\tHour\tTemperature\tHumidity\n");
        LocalTime base = LocalTime.of(20, 26, 14);
        for (int i = 0; i < spec.thlog().mesures(); i++) {
            LocalTime heure = base.plusMinutes(10L * i);
            double temperature = 18.0 - 0.2 * i;
            int humidite = 60 + i;
            contenu.append(jour)
                    .append('\t')
                    .append(heure.format(FORMAT_HEURE_THLOG))
                    .append('\t')
                    .append(String.format(Locale.ROOT, "%+.1f", temperature))
                    .append('\t')
                    .append(humidite)
                    .append('\n');
        }
        return contenu.toString();
    }

    /// Série portée par le nom du relevé climatique : celle du journal si présent, sinon celle du
    /// premier enregistreur (mode dégradé, sans journal).
    private static String serieReleve(SpecCarteSd spec) {
        if (spec.journal().present() && spec.journal().serie() != null) {
            return spec.journal().serie();
        }
        return spec.enregistreurs().get(0).serie();
    }

    /// Date de référence du relevé : la nuit du journal si connue, sinon dérivée du premier horodatage.
    private static LocalDate dateReference(SpecCarteSd spec) {
        if (spec.journal().nuit() != null) {
            return spec.journal().nuit();
        }
        String horodatage = spec.enregistreurs().get(0).horodatages().get(0);
        return LocalDate.parse(horodatage.substring(0, 8), FORMAT_HORODATAGE);
    }

    private static List<Path> specsDe(Path source) throws IOException {
        if (Files.isRegularFile(source)) {
            return List.of(source);
        }
        try (Stream<Path> flux = Files.list(source)) {
            return flux.filter(Files::isRegularFile)
                    .filter(p -> {
                        String nom = p.getFileName().toString();
                        return nom.endsWith(".yaml") || nom.endsWith(".yml");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (UncheckedIOException echec) {
            throw echec.getCause();
        }
    }
}
