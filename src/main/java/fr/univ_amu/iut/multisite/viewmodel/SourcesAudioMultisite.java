package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.List;

/// Construit les **sources audio** (descripteurs [SourceObservations]) alimentées par M-Multisite vers
/// la vue audio unifiée (#audio), à **deux granularités** :
/// - [#parPassage] : un seul passage (une ligne du tableau) → `ParPassage` ;
/// - [#parLot] : le **lot filtré** courant (toutes les lignes affichées) → `ParPassages`, pour
///   écouter / valider en lot à travers plusieurs passages.
///
/// Fonctions **pures** (aucune dépendance au `MultisiteViewModel` : ni `idUtilisateur` ni filtre n'entrent
/// dans un descripteur de passages), regroupées hors du ViewModel et du controller pour qu'ils restent
/// cohésifs (PMD GodClass / NcssCount).
public final class SourcesAudioMultisite {

    private SourcesAudioMultisite() {}

    /// Source d'**un** passage. Le nom de site est inconnu côté tableau multisite (`null`), comme à
    /// l'ouverture du passage par double-clic.
    public static SourceObservations parPassage(LignePassage ligne) {
        return new SourceObservations.ParPassage(new ContextePassage(
                ligne.idPassage(),
                ligne.numeroPassage(),
                new ContexteSite(ligne.numeroCarre(), ligne.codePoint(), null)));
    }

    /// Source du **lot filtré** : tous les passages des `lignes` affichées. Le libellé résume le lot
    /// pour le fil d'Ariane.
    public static SourceObservations parLot(List<LignePassage> lignes) {
        List<Long> idPassages = lignes.stream().map(LignePassage::idPassage).toList();
        String libelle = "lot (" + idPassages.size() + (idPassages.size() > 1 ? " passages)" : " passage)");
        return new SourceObservations.ParPassages(idPassages, libelle);
    }
}
