package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.VolumeEnLectureSeule;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.importation.model.AnalyseMelange;
import fr.univ_amu.iut.importation.model.JournalParse;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// Ce que l'inspection d'un dossier a **relevé**, et la question qu'elle pose au support de la source.
///
/// Extrait d'[InspectionImportViewModel] (Extract Class) : le ViewModel a franchi le seuil `GodClass`
/// du portail en gagnant la sonde du support, et le cliquet de la zone de production est à **zéro** -
/// il n'y a pas de relevé possible, seulement un allègement. La rédaction du compte rendu était le
/// morceau qui se nommait d'un trait : deux appels, un porteur de résultat, une liste de passages
/// existants et une sonde, tous au service de la même phrase.
///
/// La **sonde du support** vit ici plutôt que dans le ViewModel parce que c'est ici qu'elle sert.
/// Elle est remplaçable : ni un banc ni un outil de capture ne sait monter un volume en lecture
/// seule, et sans cette couture le chemin qui va de [VolumeEnLectureSeule] au bandeau n'est traversé
/// par rien - ce qui était le cas depuis #5091 (#5101).
final class CompteRenduDInspection {

    private final ServiceImport serviceImport;

    CompteRenduDInspection(ServiceImport serviceImport) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
    }

    private Predicate<Path> supportEnLectureSeule = VolumeEnLectureSeule::vrai;

    /// Remplace la sonde du support (double de test, et jeu d'essai des aperçus).
    void definirSondeDuSupport(Predicate<Path> sonde) {
        this.supportEnLectureSeule = Objects.requireNonNull(sonde, "sonde");
    }

    /// Le compte rendu d'une inspection, passages déjà importés compris.
    ///
    /// Le support est interrogé **ici**, une fois par rédaction : le volume peut être retiré entre
    /// deux gestes, et la question ne se pose qu'au moment où l'on regarde la carte. La lecture
    /// n'écrit rien (#4991).
    CompteRendu rediger(RapportInspection rapport, List<PassageExistant> existants) {
        return AvertissementsInspection.rediger(
                rapport.melange(), rapport.coherence(), existants, supportEnLectureSeule.test(rapport.dossierSource()));
    }

    /// Passages **déjà en base** pour cette nuit (#147) : même enregistreur, même date.
    ///
    /// L'identité vient du journal s'il est présent, sinon elle est reconstituée des noms de WAV
    /// (mode dégradé #107), pour que la détection couvre aussi les réimports sans journal. Sans
    /// identité exploitable, rien à signaler ; la mise en forme est déléguée à
    /// [AvertissementsInspection].
    List<PassageExistant> passagesDeLaNuit(RapportInspection rapport) {
        return identiteNuit(rapport)
                .map(identite -> serviceImport.nuitDejaImportee(identite.numeroSerie(), identite.dateNuit()))
                .filter(Objects::nonNull)
                .orElseGet(List::of);
    }

    /// Identité de la nuit inspectée - l'enregistreur et la date - ou vide si le dossier ne la livre pas.
    ///
    /// Cette identité vient du **journal** s'il est présent, sinon - mode dégradé (#107) - elle est
    /// **reconstituée des noms de WAV**, exactement comme à l'import. C'est la même règle qui sert au
    /// badge « déjà importée » ; l'extraire la rend consultable par les autres contrôles, en premier lieu
    /// celui du n° de passage, qui doit reconnaître une nuit déjà récupérée de Vigie-Chiro (#2580).
    static java.util.Optional<IdentiteNuit> identiteNuit(RapportInspection rapport) {
        JournalParse journal =
                rapport.journalOptionnel().filter(j -> j.dateDebut() != null).orElse(null);
        if (journal != null) {
            return java.util.Optional.of(
                    new IdentiteNuit(journal.numeroSerie(), journal.dateDebut().toString()));
        }
        AnalyseMelange analyse = AnalyseMelange.depuis(rapport.originaux());
        if (analyse.series().isEmpty() || analyse.nuits().isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new IdentiteNuit(
                analyse.series().first(), analyse.nuits().first().toString()));
    }

    /// Le compte rendu vide, celui d'un écran sans inspection.
    static CompteRendu vide() {
        return CompteRendu.de("", List.of());
    }
}
