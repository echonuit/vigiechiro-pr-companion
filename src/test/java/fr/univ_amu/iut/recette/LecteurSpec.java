package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.recette.SpecCarteSd.Attendu;
import fr.univ_amu.iut.recette.SpecCarteSd.Enregistreur;
import fr.univ_amu.iut.recette.SpecCarteSd.Journal;
import fr.univ_amu.iut.recette.SpecCarteSd.Prefixe;
import fr.univ_amu.iut.recette.SpecCarteSd.Thlog;
import fr.univ_amu.iut.recette.SpecCarteSd.Wav;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// Lit une spec de carte SD YAML (`recette/fixtures/spec/<fixture>.yaml`) en un [SpecCarteSd].
///
/// Le document est chargé via le `SafeConstructor` de SnakeYAML : seuls des types de base (maps,
/// listes, chaînes, nombres, booléens) sont produits, aucune instanciation de type arbitraire. Les
/// champs absents reçoivent des valeurs par défaut raisonnables, ce qui laisse les specs concises.
final class LecteurSpec {

    private static final int FREQUENCE_PAR_DEFAUT_HZ = 384_000;
    private static final double DUREE_PAR_DEFAUT_SECONDES = 1.5;
    private static final int MESURES_THLOG_PAR_DEFAUT = 6;
    private static final int INTERVALLE_PAR_DEFAUT_SECONDES = 300;

    private static final DateTimeFormatter FORMAT_HORODATAGE =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    /// Lit et interprète le fichier de spec `fichierYaml`.
    SpecCarteSd lire(Path fichierYaml) throws IOException {
        try (Reader lecteur = Files.newBufferedReader(fichierYaml, StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<?, ?> racine = asMap(yaml.load(lecteur));

            Journal journal = lireJournal(asMap(racine.get("journal")));
            Thlog thlog = lireThlog(asMap(racine.get("thlog")));
            Wav wav = lireWav(asMap(racine.get("wav")));
            List<Enregistreur> enregistreurs = lireEnregistreurs(asList(racine.get("enregistreurs")));
            Prefixe prefixe = lirePrefixe(asMap(racine.get("prefixe")));
            boolean zip = bool(racine, "zip", false);
            Attendu attendu = lireAttendu(asMap(racine.get("attendu")), journal, thlog, prefixe != null);

            return new SpecCarteSd(
                    str(racine, "fixture"),
                    str(racine, "but", ""),
                    journal,
                    thlog,
                    wav,
                    enregistreurs,
                    prefixe,
                    zip,
                    attendu);
        }
    }

    private static Journal lireJournal(Map<?, ?> map) {
        String nuit = str(map, "nuit");
        return new Journal(
                bool(map, "present", true),
                str(map, "serie"),
                nuit == null ? null : LocalDate.parse(nuit),
                bool(map, "sondePresente", true),
                bool(map, "corrompu", false));
    }

    private static Thlog lireThlog(Map<?, ?> map) {
        return new Thlog(bool(map, "present", true), intOf(map, "mesures", MESURES_THLOG_PAR_DEFAUT));
    }

    private static Wav lireWav(Map<?, ?> map) {
        return new Wav(
                intOf(map, "frequenceHz", FREQUENCE_PAR_DEFAUT_HZ),
                dblOf(map, "dureeSecondes", DUREE_PAR_DEFAUT_SECONDES));
    }

    private static List<Enregistreur> lireEnregistreurs(List<?> liste) {
        List<Enregistreur> enregistreurs = new ArrayList<>();
        for (Object element : liste) {
            Map<?, ?> map = asMap(element);
            enregistreurs.add(new Enregistreur(
                    str(map, "serie"), lireHorodatages(map.get("horodatages")), lireChaines(map.get("fauxWav"))));
        }
        return enregistreurs;
    }

    /// Horodatages soit explicites (liste), soit génératifs (`{debut, nombre, intervalleSecondes}`)
    /// pour les grosses cartes, expansés de façon déterministe (aucune horloge).
    private static List<String> lireHorodatages(Object valeur) {
        if (valeur instanceof Map<?, ?> generatif) {
            LocalDateTime base = LocalDateTime.parse(str(generatif, "debut"), FORMAT_HORODATAGE);
            int nombre = intOf(generatif, "nombre", 0);
            int intervalle = intOf(generatif, "intervalleSecondes", INTERVALLE_PAR_DEFAUT_SECONDES);
            List<String> horodatages = new ArrayList<>();
            for (int i = 0; i < nombre; i++) {
                horodatages.add(base.plusSeconds((long) intervalle * i).format(FORMAT_HORODATAGE));
            }
            return horodatages;
        }
        return lireChaines(valeur);
    }

    private static Prefixe lirePrefixe(Map<?, ?> map) {
        if (map.isEmpty()) {
            return null;
        }
        return new Prefixe(str(map, "carre"), intOf(map, "annee", 2026), intOf(map, "passage", 1), str(map, "point"));
    }

    private static Attendu lireAttendu(Map<?, ?> map, Journal journal, Thlog thlog, boolean prefixe) {
        return new Attendu(
                bool(map, "aJournal", journal.present()),
                bool(map, "aReleve", thlog.present()),
                bool(map, "journalLisible", !journal.corrompu()),
                bool(map, "plusieursEnregistreurs", false),
                bool(map, "incoherent", false),
                intOf(map, "nuits", 1),
                str(map, "etatNommage", prefixe ? "PREFIXE" : "BRUT"),
                intOf(map, "rejets", 0));
    }

    private static List<String> lireChaines(Object valeur) {
        List<String> chaines = new ArrayList<>();
        for (Object element : asList(valeur)) {
            chaines.add(element.toString());
        }
        return chaines;
    }

    private static Map<?, ?> asMap(Object valeur) {
        return valeur instanceof Map<?, ?> map ? map : Map.of();
    }

    private static List<?> asList(Object valeur) {
        return valeur instanceof List<?> liste ? liste : List.of();
    }

    private static String str(Map<?, ?> map, String cle) {
        return str(map, cle, null);
    }

    private static String str(Map<?, ?> map, String cle, String defaut) {
        Object valeur = map.get(cle);
        return valeur == null ? defaut : valeur.toString();
    }

    private static boolean bool(Map<?, ?> map, String cle, boolean defaut) {
        return map.get(cle) instanceof Boolean valeur ? valeur : defaut;
    }

    private static int intOf(Map<?, ?> map, String cle, int defaut) {
        return map.get(cle) instanceof Number valeur ? valeur.intValue() : defaut;
    }

    private static double dblOf(Map<?, ?> map, String cle, double defaut) {
        return map.get(cle) instanceof Number valeur ? valeur.doubleValue() : defaut;
    }
}
