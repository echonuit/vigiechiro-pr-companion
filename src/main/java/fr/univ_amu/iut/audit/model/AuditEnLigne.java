package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanVerification;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Audit **en ligne** (extrait de [ServiceAuditCoherence] pour la cohésion) : confronte le **dépôt** au
/// serveur ([VerificationDepot], #1132) et les **points d'écoute** aux localités serveur
/// ([AuditPointsServeur], #1178). Les deux moteurs sont injectés en `Optional` (absents dans les injecteurs
/// partiels / hors connexion). Dégrade proprement : si rien n'est disponible, un unique constat `INFO` ;
/// un passage non lié ou sans plan de dépôt est ignoré. Aucune exception ne remonte.
class AuditEnLigne {

    private final Optional<VerificationDepot> verificationDepot;
    private final Optional<AuditPointsServeur> auditPointsServeur;
    private final PassageDao passageDao;

    AuditEnLigne(
            Optional<VerificationDepot> verificationDepot,
            Optional<AuditPointsServeur> auditPointsServeur,
            PassageDao passageDao) {
        this.verificationDepot = Objects.requireNonNull(verificationDepot, "verificationDepot");
        this.auditPointsServeur = Objects.requireNonNull(auditPointsServeur, "auditPointsServeur");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    }

    List<ConstatAudit> auditer() {
        if (verificationDepot.isEmpty() && auditPointsServeur.isEmpty()) {
            return List.of(new ConstatAudit(
                    SeveriteConstat.INFO,
                    CategorieConstat.SERVEUR_INJOIGNABLE,
                    null,
                    "-",
                    "Vérification en ligne indisponible : connectez-vous à VigieChiro."));
        }
        List<ConstatAudit> constats = new ArrayList<>();
        verificationDepot.ifPresent(moteur -> constats.addAll(auditerDepots(moteur)));
        auditPointsServeur.ifPresent(audit -> constats.addAll(audit.auditer()));
        return constats;
    }

    /// Confronte, pour chaque passage, le plan de dépôt local au serveur (avale les passages non liés ou
    /// sans plan de dépôt local, hors périmètre).
    private List<ConstatAudit> auditerDepots(VerificationDepot moteur) {
        List<ConstatAudit> constats = new ArrayList<>();
        for (Passage passage : passageDao.findAll()) {
            try {
                constats.addAll(versConstats(passage.id(), moteur.verifier(passage.id())));
            } catch (RegleMetierException horsPerimetre) {
                // Passage non lié à une participation ou sans plan de dépôt local : rien à confronter en ligne.
            }
        }
        return constats;
    }

    private static List<ConstatAudit> versConstats(Long idPassage, BilanVerification bilan) {
        List<ConstatAudit> constats = new ArrayList<>();
        if (!bilan.journalDisponible()) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.INFO,
                    CategorieConstat.SERVEUR_INJOIGNABLE,
                    idPassage,
                    bilan.participationId(),
                    "Journal serveur indisponible (hors connexion ou traitement non terminé) : vérification"
                            + " partielle."));
        }
        for (String manquante : bilan.manquantes()) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.AVERTISSEMENT,
                    CategorieConstat.SERVEUR_MANQUANT,
                    idPassage,
                    manquante,
                    "Unité absente côté serveur (non traitée ou non déposée)."));
        }
        return constats;
    }
}
