package fr.univ_amu.iut.audit.viewmodel;

import fr.univ_amu.iut.audit.model.BilanNettoyage;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.nio.file.Path;
import java.util.List;

/// Les **textes** du retrait des dossiers orphelins (#3482) : ce qu'on demande avant, ce qu'on annonce
/// après.
///
/// Classe sans état, volontairement à part du [AuditViewModel] : ce sont ces deux textes qui décident si
/// l'utilisateur comprend ce qu'il fait et ce qui s'est produit, et ils méritent d'être éprouvés seuls.
public final class RetraitOrphelins {

    private RetraitOrphelins() {}

    /// Les dossiers désignés par les constats **de catégorie [CategorieConstat#DOSSIER_ORPHELIN]**.
    ///
    /// ⚠️ Le filtre sur la catégorie n'est pas une commodité : le champ `cible` d'un constat porte tantôt
    /// un dossier, tantôt un **fichier** ([CategorieConstat#DISQUE_MANQUANT] cite un `.wav`). Élargir la
    /// sélection reviendrait à effacer le dossier d'un passage vivant.
    public static List<Path> dossiers(List<ConstatAudit> constats) {
        return constats.stream()
                .filter(constat -> constat.categorie() == CategorieConstat.DOSSIER_ORPHELIN)
                .map(constat -> Path.of(constat.cible()))
                .toList();
    }

    /// Ce que l'utilisateur lit **avant** d'accepter : combien de dossiers, lesquels, et quelle place
    /// ils rendront. Le volume est le chiffre qui décide - c'est pour lui qu'on fait le ménage.
    public static CompteRendu confirmation(List<Path> dossiers, long octets) {
        List<CompteRendu.Constat> lignes = dossiers.stream()
                .map(dossier -> CompteRendu.Constat.de(nom(dossier), Severite.AVERTISSEMENT))
                .toList();
        return new CompteRendu(
                "Retirer " + dossiers.size() + " dossier(s) de session sans passage ?",
                "Ces dossiers ne sont réclamés par aucun passage en base. Leur suppression est irréversible :"
                        + " les enregistrements qu'ils contiennent ne pourront pas être récupérés.",
                lignes,
                "Place regagnée : environ " + Formats.octetsLisibles(octets) + ".");
    }

    /// Ce que l'utilisateur lit **après**, et qui doit décrire ce qui s'est produit - pas ce qu'on
    /// espérait. Un dossier resté en place fait basculer le retour en avertissement et se nomme : sur
    /// Windows, un dossier ouvert dans l'explorateur résiste, et l'utilisateur doit savoir lequel
    /// reprendre.
    public static RetourOperation compteRendu(BilanNettoyage bilan) {
        if (bilan.estVide()) {
            return RetourOperation.info("Aucun dossier à retirer.");
        }
        String fait = bilan.retires().size() + " dossier(s) retiré(s), " + Formats.octetsLisibles(bilan.octetsLiberes())
                + " regagné(s).";
        if (bilan.estComplet()) {
            return RetourOperation.succes(fait);
        }
        String restes = bilan.resistants().stream()
                .map(resistant -> nom(resistant.dossier()))
                .reduce((premier, second) -> premier + ", " + second)
                .orElse("");
        return RetourOperation.avertissement(
                fait + " " + bilan.resistants().size() + " dossier(s) n'ont pas pu être retirés : " + restes
                        + ". Ils sont peut-être ouverts dans une autre fenêtre.");
    }

    /// Le nom du dossier plutôt que son chemin entier : c'est sous ce nom que l'utilisateur reconnaît une
    /// nuit, et une modale n'a pas la largeur d'un chemin absolu.
    private static String nom(Path dossier) {
        Path nom = dossier.getFileName();
        return nom == null ? dossier.toString() : nom.toString();
    }
}
