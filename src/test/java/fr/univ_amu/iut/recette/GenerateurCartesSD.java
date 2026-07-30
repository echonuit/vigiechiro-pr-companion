package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.recette.SpecCarteSd.Enregistreur;
import fr.univ_amu.iut.recette.SpecCarteSd.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /// Journal illisible : aucune ligne ne porte de motif `PR<n>` ni « numéro de série <n> », donc
    /// l'analyseur ne peut extraire aucune série et l'inspection échoue (cas `sd-journal-corrompu`).
    private static final List<String> LIGNES_JOURNAL_CORROMPU =
            List.of("?? bloc illisible ??", "journal corrompu : aucune ligne exploitable");

    /// Octets d'un faux WAV : nom en `.wav` mais contenu non-WAV, rejeté au découpage à l'import.
    private static final byte[] OCTETS_FAUX_WAV = "pas un WAV".getBytes(StandardCharsets.US_ASCII);

    /// Horodatage figé des entrées ZIP (2020-01-01Z) pour une archive déterministe octet à octet.
    private static final long HORODATAGE_ZIP_FIXE = 1_577_836_800_000L;

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
        if (spec.zip()) {
            compresserVers(carte, destParente.resolve(spec.fixture() + ".zip"));
        }
        return carte;
    }

    /// Écrit l'arbre SD décrit par `spec` **directement** sous `racineSd` (journal, relevé, `bruts/`).
    void genererVers(SpecCarteSd spec, Path racineSd) throws IOException {
        Path bruts = racineSd.resolve(SOUS_DOSSIER_BRUTS);
        Files.createDirectories(bruts);

        if (spec.journal().present()) {
            Path journal = racineSd.resolve("LogPR" + spec.journal().serie() + ".txt");
            List<String> lignes = spec.journal().corrompu() ? LIGNES_JOURNAL_CORROMPU : lignesJournal(spec);
            Files.write(journal, lignes, StandardCharsets.UTF_8);
        }
        if (spec.thlog().present()) {
            Path releve = racineSd.resolve("PaRecPR" + serieReleve(spec) + "_THLog.csv");
            Files.writeString(releve, contenuThlog(spec), StandardCharsets.UTF_8);
        }
        ecrireBruts(spec, bruts);
    }

    private static void ecrireBruts(SpecCarteSd spec, Path bruts) throws IOException {
        int frequenceHz = spec.wav().frequenceHz();
        int trames = FabriqueWav.tramesPour(frequenceHz, spec.wav().dureeSecondes());
        String prefixe = prefixeFichier(spec.prefixe());
        for (Enregistreur enregistreur : spec.enregistreurs()) {
            for (String horodatage : enregistreur.horodatages()) {
                FabriqueWav.ecrireWav(
                        cheminBrut(bruts, prefixe, enregistreur.serie(), horodatage), frequenceHz, trames);
            }
            for (String horodatage : enregistreur.fauxWav()) {
                Files.write(cheminBrut(bruts, prefixe, enregistreur.serie(), horodatage), OCTETS_FAUX_WAV);
            }
        }
    }

    private static Path cheminBrut(Path bruts, String prefixe, String serie, String horodatage) {
        return bruts.resolve(prefixe + "PaRecPR" + serie + "_" + horodatage + ".wav");
    }

    /// Préfixe R6 appliqué aux noms de bruts (`Car<carre>-<annee>-Pass<passage>-<point>-`), ou chaîne
    /// vide si la carte reste en fichiers bruts.
    private static String prefixeFichier(Prefixe prefixe) {
        if (prefixe == null) {
            return "";
        }
        return "Car" + prefixe.carre() + "-" + prefixe.annee() + "-Pass" + prefixe.passage() + "-" + prefixe.point()
                + "-";
    }

    /// Compresse l'arbre `racineSd` dans l'archive `zipCible` (entrées triées, horodatage figé) pour le
    /// chemin décompression de la recette. Entrées à plat, chemins relatifs en séparateurs `/`.
    static void compresserVers(Path racineSd, Path zipCible) throws IOException {
        if (zipCible.getParent() != null) {
            Files.createDirectories(zipCible.getParent());
        }
        List<Path> fichiers;
        try (Stream<Path> flux = Files.walk(racineSd)) {
            fichiers = flux.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> racineSd.relativize(p).toString()))
                    .toList();
        }
        try (ZipOutputStream sortie = new ZipOutputStream(Files.newOutputStream(zipCible))) {
            for (Path fichier : fichiers) {
                ZipEntry entree =
                        new ZipEntry(racineSd.relativize(fichier).toString().replace('\\', '/'));
                entree.setTime(HORODATAGE_ZIP_FIXE);
                sortie.putNextEntry(entree);
                Files.copy(fichier, sortie);
                sortie.closeEntry();
            }
        }
    }

    /// Délègue à [fr.univ_amu.iut.fixture.JournalDeCapteur] : le tracé d'une nuit est le même ici et
    /// dans les tests qui ont juste besoin d'un journal valide (#2868). Une seule source, deux usages.
    private static List<String> lignesJournal(SpecCarteSd spec) {
        return JournalDeCapteur.lignes(
                spec.journal().serie(), spec.journal().nuit(), spec.journal().sondePresente());
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
