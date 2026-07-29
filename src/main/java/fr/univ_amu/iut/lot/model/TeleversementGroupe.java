package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.util.Objects;
import java.util.Optional;

/// « Téléverser vers Vigie-Chiro » appliqué à plusieurs passages (#2357, lot 3, PR 3/5).
///
/// ## Le plafond de parallélisme reste global
///
/// C'est le critère d'acceptation le plus facile à trahir. [DepotVigieChiro#deposer] téléverse déjà
/// **cinq unités de front**, plafond calqué sur le front web. Le moteur de traitement groupé, lui,
/// est **séquentiel entre les passages** : le plafond reste donc celui d'un passage, quel que soit le
/// nombre de nuits sélectionnées. C'est ce qui distingue un confort d'une rafale que le serveur rejette.
///
/// Rien n'est écrit ici pour l'obtenir : cela découle de la conception du moteur. Mais c'est
/// précisément le genre de propriété qu'on perd en « optimisant » un jour sans savoir pourquoi elle
/// était là.
///
/// ## Ici, l'annulation traverse le passage courant
///
/// Le moteur consulte son jeton **entre** deux passages ; cette action le passe **aussi** au dépôt,
/// qui le consulte avant chaque unité. C'est la seule action du lot qui le fasse, et c'est légitime :
/// un téléversement interrompu laisse la nuit en `DEPOT_EN_COURS` avec son plan persisté, c'est-à-dire
/// dans un état **nommé et reprenable**, pas dans un entre-deux. Le contrat du moteur - « soit avant,
/// soit après » - est tenu, avec « après » qui vaut ici « repris plus tard ».
///
/// Sans cela, renoncer à un lot de six nuits obligerait à attendre la fin du téléversement en cours,
/// soit plusieurs minutes.
public class TeleversementGroupe implements ActionGroupee {

    private final ServiceLot service;

    /// Le dépôt est **optionnel** : sa liaison n'existe qu'en connexion à Vigie-Chiro. Absent, tous les
    /// passages sont écartés avec ce motif - ce qui est très exactement ce que l'annonce doit dire.
    private final Optional<DepotVigieChiro> depot;

    public TeleversementGroupe(ServiceLot service, Optional<DepotVigieChiro> depot) {
        this.service = Objects.requireNonNull(service, "service");
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    @Override
    public String libelle() {
        return "Téléverser vers Vigie-Chiro";
    }

    /// Même règle que le bouton de l'écran M-Lot : le lot doit être **préparé** (ou son dépôt déjà
    /// entamé, donc reprenable) et **cohérent**.
    ///
    /// Elle est relue ici plutôt que déduite d'un échec : c'est ce qui permet d'annoncer les écartés
    /// avant de partir, et de ne pas créer une participation côté plateforme pour une nuit qu'on aurait
    /// refusée trois lignes plus loin.
    @Override
    public Optional<String> motifNonEligible(CiblePassage cible) {
        if (depot.isEmpty()) {
            return Optional.of("hors connexion à Vigie-Chiro");
        }
        EtatLot etat;
        try {
            etat = service.consulterLot(cible.idPassage());
        } catch (RegleMetierException introuvable) {
            return Optional.of(introuvable.getMessage());
        }
        if (etat.statut() == StatutWorkflow.DEPOSE) {
            return Optional.of("déjà déposé");
        }
        if (etat.statut() != StatutWorkflow.PRET_A_DEPOSER && etat.statut() != StatutWorkflow.DEPOT_EN_COURS) {
            return Optional.of("dépôt pas encore préparé");
        }
        if (etat.aDesEchecs()) {
            return Optional.of("contrôle de cohérence en échec");
        }
        return Optional.empty();
    }

    /// Le `orElseThrow` est une **garde de ceinture**, inatteignable par le moteur : il ne lance une
    /// action qu'après un [#motifNonEligible] vide, qui écarte déjà le hors-connexion. Elle reste parce
    /// que le contrat est public et qu'un futur appelant pourrait s'en passer. PIT la signale en
    /// permanence comme non couverte : c'est **assumé**, la couvrir demanderait un test creux.
    @Override
    public void executer(CiblePassage cible, JetonAnnulation jeton) {
        depot.orElseThrow(() -> new RegleMetierException("Hors connexion à Vigie-Chiro."))
                .deposer(
                        cible.idPassage(),
                        service.sourceDepotParDefaut(cible.idPassage()),
                        jeton::estAnnule,
                        SuiviDepot.inerte());
    }
}
