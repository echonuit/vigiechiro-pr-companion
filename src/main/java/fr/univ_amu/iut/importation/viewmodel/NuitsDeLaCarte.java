package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.AnalyseMelange;
import fr.univ_amu.iut.importation.model.JournalParse;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Ce que l'import juge des **nuits présentes sur la carte** : déjà importées, et sous quelle identité
/// (#5600). [InspectionImportViewModel] tient la table des nuits et lui délègue ces jugements.
///
/// Les nuits jugées sont celles de la **table**, tirées des noms des WAV. Le journal de l'enregistreur
/// ne donne que la série : il est circulaire, et sa première ligne désigne souvent une nuit effacée de
/// la carte depuis. C'est cette nuit-là que l'avertissement, la confirmation et le contrôle du n° de
/// passage jugeaient.
final class NuitsDeLaCarte {

    private final ServiceImport serviceImport;
    private final List<NuitVM> nuits;

    NuitsDeLaCarte(ServiceImport serviceImport, List<NuitVM> nuits) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.nuits = Objects.requireNonNull(nuits, "nuits");
    }

    /// Numéro de série de l'enregistreur de la carte (commun à toutes les nuits) : issu du **journal**
    /// s'il est présent, sinon **reconstitué des noms de WAV** (mode dégradé #107). `null` si
    /// indéterminable.
    static String serie(RapportInspection inspection) {
        JournalParse journal = inspection
                .journalOptionnel()
                .filter(j -> j.numeroSerie() != null)
                .orElse(null);
        if (journal != null) {
            return journal.numeroSerie();
        }
        AnalyseMelange analyse = AnalyseMelange.depuis(inspection.originaux());
        return analyse.series().isEmpty() ? null : analyse.series().first();
    }

    /// Badge « déjà importée » (#147) d'une nuit (même enregistreur + même date en base), vide sinon.
    String badge(String serie, NuitDetectee nuit) {
        if (serie == null) {
            return "";
        }
        List<PassageExistant> existants =
                serviceImport.nuitDejaImportee(serie, nuit.dateNuit().toString());
        return (existants == null || existants.isEmpty()) ? "" : "déjà importée";
    }

    /// Les nuits **cochées** de la table déjà en base : même enregistreur, même date, chacune avec ses
    /// passages.
    List<NuitDejaImportee> cocheesDejaImportees(String serie) {
        if (serie == null) {
            return List.of();
        }
        List<NuitDejaImportee> dejaImportees = new ArrayList<>();
        for (NuitVM nuit : nuits) {
            if (!nuit.estIncluse()) {
                continue;
            }
            List<PassageExistant> existants =
                    serviceImport.nuitDejaImportee(serie, nuit.date().toString());
            if (existants != null && !existants.isEmpty()) {
                dejaImportees.add(new NuitDejaImportee(nuit.date(), existants));
            }
        }
        return List.copyOf(dejaImportees);
    }

    /// L'identité de chaque nuit **cochée** : l'enregistreur de la carte et la date de la nuit.
    List<IdentiteNuit> identitesCochees(String serie) {
        if (serie == null) {
            return List.of();
        }
        return nuits.stream()
                .filter(NuitVM::estIncluse)
                .map(nuit -> new IdentiteNuit(serie, nuit.date().toString()))
                .toList();
    }
}
