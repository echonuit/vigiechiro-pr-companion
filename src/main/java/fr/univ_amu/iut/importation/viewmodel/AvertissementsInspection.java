package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import fr.univ_amu.iut.importation.model.AnalyseCoherence;
import fr.univ_amu.iut.importation.model.AnalyseMelange;
import fr.univ_amu.iut.importation.model.PassageExistant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/// Ce que l'inspection d'un dossier a relevé **avant** l'import : mélange d'enregistreurs (#33),
/// désaccord entre le journal et les fichiers (#33), nuit déjà importée (#147).
///
/// Les trois vivaient en libellés séparés, chacun une chaîne portant un « ⚠ » en tête et joignant ses
/// listes à la virgule - trois propriétés, trois `Label`, et des listes sans borne dans chacune (#2050).
/// Ce sont trois **constats** d'un même compte rendu : ils décrivent le même dossier, au même moment,
/// et l'utilisateur les lit ensemble pour décider s'il importe.
///
/// Rien n'est perdu au passage : les séries, les dates et les passages existants deviennent des détails
/// au lieu d'être aplatis dans une phrase.
public final class AvertissementsInspection {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private AvertissementsInspection() {}

    /// Assemble le compte rendu de l'inspection. Vide quand le dossier ne pose aucune question.
    static CompteRendu rediger(AnalyseMelange melange, AnalyseCoherence coherence, List<PassageExistant> existants) {
        List<Constat> constats = new ArrayList<>();
        melangeConstat(melange).ifPresent(constats::add);
        coherenceConstat(coherence).ifPresent(constats::add);
        nuitExistanteConstat(existants).ifPresent(constats::add);
        return constats.isEmpty() ? CompteRendu.de("", List.of()) : CompteRendu.de("", constats);
    }

    /// Mélange d'enregistreurs : un import correspond à un seul enregistreur. Chaque série est un détail,
    /// là où elles étaient jointes par des virgules dans la phrase.
    private static Optional<Constat> melangeConstat(AnalyseMelange melange) {
        if (melange == null || !melange.plusieursEnregistreurs()) {
            return Optional.empty();
        }
        List<Detail> details = new ArrayList<>(melange.series().stream()
                .map(serie -> Detail.de("série " + serie))
                .toList());
        if (melange.plusieursNuits()) {
            details.add(Detail.de(String.format("sur %d nuits", melange.nuits().size())));
        }
        return Optional.of(new Constat(
                "Ce dossier mélange plusieurs enregistreurs : vérifiez la source avant d'importer.",
                Severite.AVERTISSEMENT,
                details));
    }

    /// Désaccord entre le journal du capteur et les fichiers : sur la série, sur la date, ou les deux.
    /// Chaque désaccord est un détail à part - la phrase les liait par un « et » qui obligeait à
    /// reconstruire mentalement ce qui portait sur quoi.
    private static Optional<Constat> coherenceConstat(AnalyseCoherence coherence) {
        if (coherence == null || !coherence.incoherent()) {
            return Optional.empty();
        }
        List<Detail> details = new ArrayList<>();
        if (coherence.serieIncoherente()) {
            details.add(new Detail(
                    "série déclarée absente des fichiers",
                    String.join(", ", coherence.seriesDeclareesAbsentes()) + " (fichiers : "
                            + String.join(", ", coherence.seriesFichiers()) + ")"));
        }
        if (coherence.dateIncoherente()) {
            details.add(new Detail(
                    "date du journal hors de la nuit des fichiers",
                    coherence.dateJournal().map(JOUR::format).orElse("?") + " (fichiers : "
                            + dates(coherence.nuitsFichiers()) + ")"));
        }
        return Optional.of(new Constat(
                "Le journal du capteur ne correspond pas aux enregistrements : vérifiez qu'ils viennent"
                        + " bien de la même nuit.",
                Severite.AVERTISSEMENT,
                details));
    }

    /// Nuit déjà importée (#147) : non bloquant, l'utilisateur peut vouloir un nouveau passage.
    private static Optional<Constat> nuitExistanteConstat(List<PassageExistant> existants) {
        if (existants == null || existants.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Constat(
                "Cette nuit a déjà été importée : l'importer créera un nouveau passage.",
                Severite.AVERTISSEMENT,
                existants.stream().map(AvertissementsInspection::detail).toList()));
    }

    /// Le libellé d'un passage déjà présent, **écrit une seule fois**.
    ///
    /// Il l'était deux fois : ici avant l'import, et dans [CompteRenduImport] après, avec deux rédactions
    /// différentes pour la même donnée. La plus riche l'emporte - l'année et le point sont ce qui permet
    /// à l'observateur de reconnaître le passage qu'il a déjà déposé.
    static Detail detail(PassageExistant passage) {
        return Detail.de(libelle(passage));
    }

    private static String libelle(PassageExistant passage) {
        return String.format(
                "n° %d (%d) au carré %s, point %s",
                passage.numeroPassage(), passage.annee(), passage.carre(), passage.codePoint());
    }

    /// La question posée avant d'importer une nuit déjà présente, **vide** s'il n'y en a pas.
    ///
    /// Publique parce que la capture d'écran l'appelle : `apercu-import-doublon.png` montrait jusqu'ici
    /// une phrase **écrite en dur** dans l'outil de capture, présentée en commentaire comme « le texte que
    /// l'assistant compose réellement ». Elle ne l'était pas - l'application en produisait une autre. Le
    /// dialogue passait par le code de production, son contenu non (ADR 0025).
    ///
    /// Une modale a la place de tout dire : les passages y sont listés un par ligne, et non joints par des
    /// points-virgules comme le faisait la phrase d'origine. C'est la même rédaction que les détails du
    /// compte rendu - la donnée n'est mise en forme qu'ici.
    public static String question(List<PassageExistant> existants) {
        if (existants == null || existants.isEmpty()) {
            return "";
        }
        String liste =
                existants.stream().map(passage -> "\n  - " + libelle(passage)).collect(Collectors.joining());
        return "Cette nuit a déjà été importée :" + liste + "\n\nImporter quand même comme nouveau passage ?";
    }

    private static String dates(Iterable<LocalDate> nuits) {
        List<String> rendues = new ArrayList<>();
        nuits.forEach(nuit -> rendues.add(JOUR.format(nuit)));
        return String.join(", ", rendues);
    }
}
