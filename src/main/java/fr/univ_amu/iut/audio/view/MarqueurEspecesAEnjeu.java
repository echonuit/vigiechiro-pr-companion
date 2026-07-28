package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.Set;

/// Sait dire, ligne par ligne, si une observation porte une **espèce à enjeu de conservation** (#2353) :
/// une des espèces que le Plan National d'Actions Chiroptères désigne comme prioritaires.
///
/// Lu **une fois**, à l'ouverture de l'écran, et jamais rafraîchi : c'est une donnée de **référence**,
/// posée par la migration au démarrage. L'application ne l'écrit pas, rien ne peut la changer en cours de
/// session — la relire à chaque chargement de la table laisserait croire le contraire.
///
/// L'ensemble est ensuite consulté une fois **par ligne affichée**, par le repère de colonne comme par le
/// critère de filtre : une requête par ligne n'y résisterait pas.
final class MarqueurEspecesAEnjeu {

    private final Set<String> codes;

    MarqueurEspecesAEnjeu(EspecesPrioritaires referentiel) {
        this.codes = Set.copyOf(referentiel.codes());
    }

    /// Cette ligne porte-t-elle une espèce à enjeu ? Lu sur le **taxon retenu** — la correction de
    /// l'observateur si elle existe, sinon la proposition de Tadarida —, parce que c'est l'espèce que la
    /// ligne affirme, pas celle qui a été proposée. Corriger une détection vers une espèce prioritaire
    /// fait donc apparaître le repère, et l'inverse le fait disparaître.
    boolean aEnjeu(LigneObservationAudio ligne) {
        String retenu = ligne.taxonObservateur() != null ? ligne.taxonObservateur() : ligne.taxonTadarida();
        return retenu != null && codes.contains(retenu);
    }
}
