package fr.univ_amu.iut.fixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// **Le journal d'un enregistreur, écrit une bonne fois** (#2868) : `LogPR<serie>.txt` et le relevé
/// climatique `PaRecPR<serie>_THLog.csv` que l'import attend sur une carte SD.
///
/// ## Pourquoi cette classe existe
///
/// Le format du journal (`JJ/MM/AA - HH:MM:SS PR<serie> …`) est celui que produit un vrai enregistreur.
/// Vingt-neuf fichiers de test le recopiaient à la main, et trois portaient leur propre helper
/// `ecrireLog`. Autant d'endroits à retoucher le jour où un champ bouge, et autant d'occasions d'en
/// oublier un sans que rien ne le dise.
///
/// La version paramétrée existait déjà, mais enfouie dans `GenerateurCartesSD` et pilotée par une
/// `SpecCarteSd` : inutilisable pour un test qui veut simplement « un journal valide ». Elle est extraite
/// ici, et le générateur de recette s'en sert désormais - une seule source, deux usages.
///
/// ## Ce que ce journal contient
///
/// Le tracé complet d'une nuit : accès à la carte, démarrage, passage en mode protocole, batteries,
/// paramètres d'acquisition, réveil, mise en veille. C'est **plus** que ce que la plupart des tests
/// écrivaient à la main - ils se contentaient des trois lignes dont l'analyseur tire la série et la date.
///
/// ⚠️ Ce n'est donc **pas** une substitution neutre : un test qui affirme quelque chose sur le *contenu*
/// du journal (résumé affiché, nombre de lignes) doit être relu, pas converti. Là où le journal n'est
/// qu'un préalable à l'import, la bascule ne change rien d'observable.
///
/// ## Ce qui ne passe pas par ici
///
/// `AnalyseurLogPRTest` et `InspecteurDossierTest` gardent leurs littéraux, délibérément : ils testent
/// les **analyseurs** de ce format. Leur donner ce générateur reviendrait à le tester contre lui-même.
public final class JournalDeCapteur {

    private static final DateTimeFormatter FORMAT_JOURNAL = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT);

    /// En-tête du relevé climatique, seul contenu qu'exige l'import quand la sonde n'a rien relevé.
    public static final String ENTETE_THLOG = "Date\tHour\tTemperature(C)\tHumidity(%)\n";

    private JournalDeCapteur() {}

    /// Les lignes du journal d'une nuit, dans l'ordre où l'enregistreur les écrit.
    ///
    /// @param serie numéro de série de l'enregistreur, tel qu'il apparaît dans le nom du fichier
    /// @param nuit date du **coucher** de soleil : la nuit se termine le lendemain matin
    /// @param sondePresente vrai si l'enregistreur porte une sonde température/hygrométrie
    public static List<String> lignes(String serie, LocalDate nuit, boolean sondePresente) {
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
        if (sondePresente) {
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

    /// Écrit `LogPR<serie>.txt` dans `racineSd`, sonde présente.
    public static Path ecrire(Path racineSd, String serie, LocalDate nuit) throws IOException {
        Path journal = racineSd.resolve("LogPR" + serie + ".txt");
        Files.write(journal, lignes(serie, nuit, true), StandardCharsets.UTF_8);
        return journal;
    }

    /// Écrit le relevé climatique `PaRecPR<serie>_THLog.csv`, réduit à son en-tête.
    ///
    /// L'import exige que le fichier existe ; son contenu ne l'intéresse que si des relevés y figurent.
    /// Un en-tête seul est donc l'état le plus courant sur une carte réelle dont la sonde n'a rien dit.
    public static Path ecrireReleve(Path racineSd, String serie) throws IOException {
        Path releve = racineSd.resolve("PaRecPR" + serie + "_THLog.csv");
        Files.writeString(releve, ENTETE_THLOG, StandardCharsets.UTF_8);
        return releve;
    }

    private static String ligne(String date, String heure, String serie, String message) {
        return date + " - " + heure + " PR" + serie + " " + message;
    }
}
