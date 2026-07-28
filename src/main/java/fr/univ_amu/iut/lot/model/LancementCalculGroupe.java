package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.Objects;
import java.util.Optional;

/// « Déclencher le calcul » appliqué à plusieurs passages (#2357, lot 3, PR 5/5).
///
/// L'issue supposait ce geste **hors périmètre** ; il a été rattaché après arbitrage. C'est le plus
/// délicat des quatre, pour une raison qui n'a rien de théorique.
///
/// ## Jamais de relance, jamais de `forcer`
///
/// À chaque calcul, le serveur **supprime toutes les `donnees` avant de recalculer**. Sur une nuit
/// déposée en **archives ZIP** - le mode par défaut - les WAV extraits ne sont pas conservés côté
/// plateforme : le recalcul ne peut pas les relire, et les observations sont **définitivement
/// perdues**.
///
/// Cette action passe donc toujours `forcer = false`, et rien ne permet de le changer. La garde locale
/// de [DepotVigieChiro#lancerTraitement] fait le reste : elle relit l'état et refuse d'elle-même. Un
/// lot qui pourrait forcer serait un moyen de détruire vingt nuits d'un clic.
///
/// ## Un résultat qui n'est pas un succès doit **lever**
///
/// C'est le piège de cette action, et il est spécifique. [DepotVigieChiro#lancerTraitement] **rend** un
/// [ResultatLancement] au lieu de lever : relance bloquée, plateforme injoignable, refus serveur sont
/// des **valeurs**, pas des exceptions. Or le moteur ne connaît l'échec que par une exception - un
/// retour muet aurait donc été compté « fait », et le compte rendu aurait annoncé vingt calculs lancés
/// dont dix-neuf n'ont pas eu lieu.
///
/// La traduction est donc faite ici, explicitement, et c'est le vrai contenu de cette classe.
public class LancementCalculGroupe implements ActionGroupee {

    private final Optional<DepotVigieChiro> depot;

    public LancementCalculGroupe(Optional<DepotVigieChiro> depot) {
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    @Override
    public String libelle() {
        return "Déclencher le calcul";
    }

    /// Seul écart **local** possible : l'absence de connexion. Savoir si un calcul a déjà eu lieu
    /// demanderait d'interroger la plateforme pour chaque passage avant de commencer ; l'éligibilité
    /// doit rester peu coûteuse. Une relance bloquée ressort donc en **échec**, avec son motif.
    @Override
    public Optional<String> motifNonEligible(CiblePassage cible) {
        if (depot.isEmpty()) {
            return Optional.of("hors connexion à Vigie-Chiro");
        }
        if (!depot.get().participationLiee(cible.idPassage())) {
            return Optional.of("pas encore déposé sur Vigie-Chiro");
        }
        return Optional.empty();
    }

    /// Le jeton est ignoré : une demande de calcul est un appel unique, sans état intermédiaire nommé.
    @Override
    public void executer(CiblePassage cible, JetonAnnulation jeton) {
        ResultatLancement resultat = depot.orElseThrow().lancerTraitement(cible.idPassage(), false);
        if (!resultat.traitementEnRoute()) {
            throw new RegleMetierException(motifDe(resultat));
        }
    }

    /// Ce que l'observateur lira dans le compte rendu, pour chaque passage qui n'a pas abouti.
    private static String motifDe(ResultatLancement resultat) {
        return switch (resultat.issue()) {
            case RELANCE_BLOQUEE -> "déjà calculé : relancer effacerait les observations";
            case INJOIGNABLE -> "Vigie-Chiro injoignable ; par sécurité, rien n'a été lancé";
            case REFUSE -> "refusé par Vigie-Chiro (" + resultat.détail() + ")";
            case ACCEPTE, DEJA_LANCE -> throw new IllegalStateException("issue en route traitée comme un échec");
        };
    }
}
