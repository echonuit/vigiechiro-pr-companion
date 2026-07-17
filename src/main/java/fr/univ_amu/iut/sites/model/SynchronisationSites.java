package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// **Synchronisation à la demande depuis M-Sites** (#1045) : rejoue, sans se reconnecter, ce que la
/// connexion fait pour les sites. Rapproche d'abord la **structure des sites** ([RapprochementSites]),
/// puis les rapprocheurs qui **dépendent** de cette structure locale ([RapprochementVigieChiro.Phase#DEPENDANTE],
/// p. ex. les squelettes de nuits de l'EPIC #1662) : un site tout juste rapatrié voit ainsi ses passages
/// **dès ce clic**, en un seul tour (la structure précède ses dépendants, #1776).
///
/// Le référentiel taxons ([RapprochementVigieChiro.Phase#STRUCTURE] global) est **hors sujet** ici : un
/// clic « Mes sites » ne re-tire pas tout le dictionnaire d'espèces. Le module ne fournit donc que la
/// structure des sites et les dépendants.
///
/// Même sémantique conservatrice qu'à la connexion : liste distante vide → no-op, best-effort par
/// rapprocheur (chacun avale ses erreurs), jamais d'écrasement de données locales. Activée par
/// `OptionalBinder` (absente hors app complète), patron de `SynchronisationParticipation` (#937).
public final class SynchronisationSites {

    private final RapprochementVigieChiro sites;
    private final List<RapprochementVigieChiro> dependants;
    private final ClientVigieChiro client;

    public SynchronisationSites(
            RapprochementVigieChiro sites, List<RapprochementVigieChiro> dependants, ClientVigieChiro client) {
        this.sites = Objects.requireNonNull(sites, "sites");
        this.dependants = List.copyOf(Objects.requireNonNull(dependants, "dependants"));
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Rejoue la structure des sites **puis** ses dépendants (dans cet ordre), et rend un compte-rendu par
    /// rapprocheur ayant quelque chose à dire (les autres restent silencieux). À appeler **hors du fil
    /// JavaFX** (réseau + écritures base).
    public List<RapportSynchro> synchroniser() {
        List<RapportSynchro> rapports = new ArrayList<>();
        sites.synchroniser(client).ifPresent(rapports::add);
        for (RapprochementVigieChiro dependant : dependants) {
            dependant.synchroniser(client).ifPresent(rapports::add);
        }
        return rapports;
    }
}
