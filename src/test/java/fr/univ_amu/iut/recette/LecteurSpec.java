package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.recette.SpecCarteSd.Attendu;
import fr.univ_amu.iut.recette.SpecCarteSd.Enregistreur;
import fr.univ_amu.iut.recette.SpecCarteSd.Journal;
import fr.univ_amu.iut.recette.SpecCarteSd.Thlog;
import fr.univ_amu.iut.recette.SpecCarteSd.Wav;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    /// Lit et interprète le fichier de spec `fichierYaml`.
    SpecCarteSd lire(Path fichierYaml) throws IOException {
        try (Reader lecteur = Files.newBufferedReader(fichierYaml, StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<?, ?> racine = asMap(yaml.load(lecteur));

            Journal journal = lireJournal(asMap(racine.get("journal")));
            Thlog thlog = lireThlog(asMap(racine.get("thlog")));
            Wav wav = lireWav(asMap(racine.get("wav")));
            List<Enregistreur> enregistreurs = lireEnregistreurs(asList(racine.get("enregistreurs")));
            Attendu attendu = lireAttendu(asMap(racine.get("attendu")), journal, thlog);

            return new SpecCarteSd(
                    str(racine, "fixture"), str(racine, "but", ""), journal, thlog, wav, enregistreurs, attendu);
        }
    }

    private static Journal lireJournal(Map<?, ?> map) {
        String nuit = str(map, "nuit");
        return new Journal(
                bool(map, "present", true),
                str(map, "serie"),
                nuit == null ? null : LocalDate.parse(nuit),
                bool(map, "sondePresente", true));
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
            List<String> horodatages = new ArrayList<>();
            for (Object horodatage : asList(map.get("horodatages"))) {
                horodatages.add(horodatage.toString());
            }
            enregistreurs.add(new Enregistreur(str(map, "serie"), horodatages));
        }
        return enregistreurs;
    }

    private static Attendu lireAttendu(Map<?, ?> map, Journal journal, Thlog thlog) {
        return new Attendu(
                bool(map, "aJournal", journal.present()),
                bool(map, "aReleve", thlog.present()),
                bool(map, "journalLisible", true),
                bool(map, "plusieursEnregistreurs", false),
                bool(map, "incoherent", false),
                intOf(map, "nuits", 1));
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
