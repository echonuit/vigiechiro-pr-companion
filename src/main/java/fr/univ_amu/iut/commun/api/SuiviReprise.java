package fr.univ_amu.iut.commun.api;

import java.time.Duration;

/// Prévenu **avant chaque nouvelle tentative** d'un appel réessayé ([PolitiqueReessai], #2354).
///
/// Décision utilisateur (#2350) : pendant un réessai, l'utilisateur voit une **mention discrète**
/// (« nouvelle tentative dans N s »), ni silence trompeur ni bandeau d'erreur pour un incident absorbé.
/// Ce port porte l'information ; libre à l'appelant de la router vers son canal de statut (la ligne
/// d'unité du dépôt, par exemple) ou de l'ignorer ([#SILENCIEUX]).
///
/// Type **public** parce que la reprise se décide au fond du transport ([TransportVigieChiro]) mais se
/// montre au bord de l'IHM : il traverse les paquets, là où [PolitiqueReessai] reste interne.
@FunctionalInterface
public interface SuiviReprise {

    /// Une nouvelle tentative (`tentative`, la première portant le n° 1) est prévue dans `delai`.
    void nouvelleTentative(int tentative, Duration delai);

    /// `true` si l'appelant a **demandé l'annulation** (#2686). Consulté pendant la temporisation, là où
    /// le dépôt ne consulte son drapeau qu'**entre deux unités** : une reprise à l'intérieur d'un envoi
    /// est sinon une attente que « Annuler » ne traverse pas.
    ///
    /// Porté par ce port plutôt que par un paramètre de plus : il descend **déjà** du dépôt jusqu'au
    /// transport, et trois signatures y sont au bord de l'arité que le portail qualité tolère.
    ///
    /// Le défaut est « jamais » : un appel qui n'offre pas d'annulation ne renonce pas.
    default boolean renonce() {
        return false;
    }

    /// Reprise silencieuse : aucune mention. Défaut des appels sans IHM et des tests.
    SuiviReprise SILENCIEUX = (tentative, delai) -> {};
}
