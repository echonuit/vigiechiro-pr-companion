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

    /// Reprise silencieuse : aucune mention. Défaut des appels sans IHM et des tests.
    SuiviReprise SILENCIEUX = (tentative, delai) -> {};
}
