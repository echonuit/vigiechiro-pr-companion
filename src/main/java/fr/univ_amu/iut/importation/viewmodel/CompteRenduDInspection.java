package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.VolumeEnLectureSeule;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.importation.model.RapportInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// Ce que l'inspection d'un dossier a **relevé**, et la question qu'elle pose au support de la source.
///
/// Extrait d'[InspectionImportViewModel] (Extract Class) : le ViewModel a franchi le seuil `GodClass`
/// du portail en gagnant la sonde du support, et le cliquet de la zone de production est à **zéro** -
/// il n'y a pas de relevé possible, seulement un allègement. La rédaction du compte rendu était le
/// morceau qui se nommait d'un trait : un porteur de résultat, les nuits déjà importées et une sonde,
/// tous au service de la même phrase. Il ne cherche plus lui-même les passages existants (#5600) : il
/// les jugeait d'après la première ligne du journal, et c'est le ViewModel, qui tient la table des
/// nuits, qui les lui fournit désormais.
///
/// La **sonde du support** vit ici plutôt que dans le ViewModel parce que c'est ici qu'elle sert.
/// Elle est remplaçable : ni un banc ni un outil de capture ne sait monter un volume en lecture
/// seule, et sans cette couture le chemin qui va de [VolumeEnLectureSeule] au bandeau n'est traversé
/// par rien - ce qui était le cas depuis #5091 (#5101).
final class CompteRenduDInspection {

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
    CompteRendu rediger(RapportInspection rapport, List<NuitDejaImportee> nuitsDejaImportees) {
        return AvertissementsInspection.redigerPourLesNuits(
                rapport.melange(),
                rapport.coherence(),
                nuitsDejaImportees,
                supportEnLectureSeule.test(rapport.dossierSource()));
    }

    /// Le compte rendu vide, celui d'un écran sans inspection.
    static CompteRendu vide() {
        return CompteRendu.de("", List.of());
    }
}
