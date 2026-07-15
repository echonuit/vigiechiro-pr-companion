package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// **Le point de relevé unique** de l'état du traitement serveur (EPIC #1259) : demander à la plateforme
/// où en est l'analyse d'une nuit, et **s'en souvenir** au passage (cache #1262).
///
/// Il vit dans `commun` à dessein : l'écran de dépôt (#1263), la modale de rattachement (#1264) et la
/// ligne de commande (#1265) le consomment tous, sans qu'aucune feature n'ait à dépendre d'une autre —
/// un `passage` qui dépendrait de `lot` fermerait un cycle qu'ArchUnit refuse.
///
/// **Lecture seule** vis-à-vis du serveur : on observe, on n'écrit jamais. Le lancement, lui, passe par
/// le dépôt (#1261), parce qu'il engage.
public final class SuiviTraitement {

    private final TraitementVigieChiro traitement;
    private final LienVigieChiroDao liens;
    private final ReleveTraitementDao releves;
    private final Horloge horloge;

    public SuiviTraitement(
            TraitementVigieChiro traitement, LienVigieChiroDao liens, ReleveTraitementDao releves, Horloge horloge) {
        this.traitement = Objects.requireNonNull(traitement, "traitement");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.releves = Objects.requireNonNull(releves, "releves");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Demande au serveur où en est l'analyse de cette nuit, **et enregistre le relevé**. Bloquant
    /// (réseau) : à appeler hors du fil JavaFX.
    ///
    /// Lève une [RegleMetierException] si le passage n'est lié à aucune participation (rien à suivre :
    /// la nuit n'a pas été déposée), ou si le serveur n'a **pas pu être lu** (#1284 : non connecté,
    /// injoignable, ou refus) — dans ce cas le **dernier relevé persisté est conservé tel quel** : un
    /// échec de lecture n'écrase jamais un souvenir acquis, même famille que la garde anti-purge des
    /// rapprochements. Un [Traitement#estInconnu()] rendu ici veut donc dire, enfin sans ambiguïté :
    /// « le serveur répond, et la nuit n'a jamais été calculée ».
    public Traitement relever(Long idPassage) {
        String participationId = participationDe(idPassage);
        Traitement etat =
                switch (traitement.etat(participationId)) {
                    case ReponseApi.Succes<Traitement>(Traitement lu) -> lu;
                    case ReponseApi.NonConnecte<Traitement> nonConnecte ->
                        throw new RegleMetierException(
                                "Non connecté à VigieChiro : impossible de relever l'état du traitement."
                                        + " Le dernier état connu reste affiché.");
                    case ReponseApi.Injoignable<Traitement>(String cause) ->
                        throw new RegleMetierException(
                                "VigieChiro est injoignable (" + cause
                                        + ") : impossible de relever l'état du traitement. Le dernier état connu reste affiché.");
                    case ReponseApi.Refuse<Traitement>(int statut, String corps) ->
                        throw new RegleMetierException("VigieChiro a refusé la lecture de la participation (HTTP "
                                + statut + " : " + corps + ").");
                };
        releves.enregistrer(new ReleveTraitement(
                idPassage, participationId, etat, horloge.maintenant().toString()));
        return etat;
    }

    /// Relève l'état de **plusieurs** nuits d'un coup, **à la demande** (#1338) : c'est le relevé groupé
    /// que les vues d'ensemble déclenchent par un bouton, jamais un sondage automatique — la règle du
    /// chantier reste « on n'interroge le serveur que quand l'utilisateur le demande ».
    ///
    /// **Best-effort** : une nuit qui échoue (injoignable, refus, non liée) n'interrompt pas les autres,
    /// et son dernier relevé connu est conservé (garde de [#relever]). Le [BilanReleveGroupe] rend compte
    /// de ce qui a été rafraîchi et de ce qui a échoué, pour que la vue le dise sans mentir sur une
    /// fraîcheur qu'elle n'a pas obtenue.
    ///
    /// Bloquant (une requête réseau par nuit) : à appeler **hors du fil JavaFX**.
    ///
    /// @param idPassages nuits à relever (typiquement les passages déposés d'une vue)
    public BilanReleveGroupe releverTout(List<Long> idPassages) {
        Objects.requireNonNull(idPassages, "idPassages");
        int rafraichis = 0;
        int echecs = 0;
        for (Long idPassage : idPassages) {
            try {
                relever(idPassage);
                rafraichis++;
            } catch (RegleMetierException echec) {
                // Best-effort : le dernier état connu de cette nuit reste affiché, on continue les autres.
                echecs++;
            }
        }
        return new BilanReleveGroupe(rafraichis, echecs);
    }

    /// Compte rendu d'un [#releverTout] : combien de nuits ont été rafraîchies, combien ont échoué.
    ///
    /// @param rafraichis nuits dont l'état a été relu et réenregistré
    /// @param echecs nuits qui n'ont pas pu être relevées (leur dernier état connu est conservé)
    public record BilanReleveGroupe(int rafraichis, int echecs) {

        /// Nombre de nuits traitées (rafraîchies ou en échec).
        public int total() {
            return rafraichis + echecs;
        }
    }

    /// **Dernier état connu**, sans toucher au réseau : ce que la plateforme disait la dernière fois qu'on
    /// le lui a demandé, avec la date de cette lecture. Vide si on ne l'a jamais relevé. C'est ce qui
    /// permet d'afficher quelque chose hors connexion et à la réouverture de l'application.
    public Optional<ReleveTraitement> dernierReleve(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return releves.pour(idPassage);
    }

    /// Participation liée au passage, ou refus explicite : suivre le traitement d'une nuit jamais déposée
    /// n'a pas de sens.
    private String participationDe(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage))
                .orElseThrow(() -> new RegleMetierException(
                        "Aucune participation VigieChiro liée à ce passage : déposez d'abord la nuit."));
    }
}
