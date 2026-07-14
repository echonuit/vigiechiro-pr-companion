package fr.univ_amu.iut.audit.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.BaseNeuve;
import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// **Exécute** la procédure de reset (#1419, EPIC #1154) : repartir d'une base neuve **sans perte
/// silencieuse**. C'est la réponse exécutable à la question de l'EPIC ; `reset-guide` seul se contentait
/// de l'annoncer ([ServiceRecuperabilite]).
///
/// L'ordre des étapes n'est pas une commodité, c'est la **garantie** :
///
/// 1. **Dire ce qu'on perdrait.** Une nuit dont l'audio n'est ni sur le disque ni sur le serveur (dépôt
///    ZIP) est perdue pour de bon. Sans acceptation explicite, on **s'arrête ici**.
/// 2. **Vérifier que la plateforme répond.** C'est le garde-fou décisif : la base neuve se repeuple
///    **depuis le serveur**. Détruire la base alors que VigieChiro est injoignable laisserait un
///    workspace **vide**, et le remède serait pire que le mal. On refuse **avant** de détruire.
/// 3. **Sauvegarder** base **et** audio (#1346), en disant ce qui n'a pas pu être copié (#1151).
/// 4. **Base neuve** ([BaseNeuve]) — un filet est posé au passage.
/// 5. **Repeupler depuis VigieChiro** : rapprochements (taxons, sites, points) puis reconstruction des
///    participations en passages archivés (#1050, #1305).
/// 6. **Auditer** : le workspace doit être sain. Un audit qui crierait ici trahirait un reset raté.
///
/// L'**audio** (étape 5 de la procédure documentée) n'est **pas** automatisable : il faut une carte SD
/// montée, ou des fichiers retrouvés. Le service ne le fait pas — il **nomme** les nuits concernées pour
/// que l'utilisateur sache exactement quoi rebrancher, plutôt que de le laisser découvrir le trou.
public class ServiceReset {

    private final ServiceRecuperabilite recuperabilite;
    private final ClientVigieChiro client;
    private final ServiceSauvegarde sauvegarde;
    private final BaseNeuve baseNeuve;
    private final Set<RapprochementVigieChiro> rapprocheurs;
    private final ServiceReconstructionPassages reconstruction;
    private final ServiceAuditCoherence audit;
    private final UtilisateurDao utilisateurDao;

    @Inject
    public ServiceReset(
            ServiceRecuperabilite recuperabilite,
            ClientVigieChiro client,
            ServiceSauvegarde sauvegarde,
            BaseNeuve baseNeuve,
            Set<RapprochementVigieChiro> rapprocheurs,
            ServiceReconstructionPassages reconstruction,
            ServiceAuditCoherence audit,
            UtilisateurDao utilisateurDao) {
        this.recuperabilite = Objects.requireNonNull(recuperabilite, "recuperabilite");
        this.client = Objects.requireNonNull(client, "client");
        this.sauvegarde = Objects.requireNonNull(sauvegarde, "sauvegarde");
        this.baseNeuve = Objects.requireNonNull(baseNeuve, "baseNeuve");
        this.rapprocheurs = Set.copyOf(Objects.requireNonNull(rapprocheurs, "rapprocheurs"));
        this.reconstruction = Objects.requireNonNull(reconstruction, "reconstruction");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.utilisateurDao = Objects.requireNonNull(utilisateurDao, "utilisateurDao");
    }

    /// Exécute le reset complet. **Détruit la base** — à n'appeler qu'après une confirmation explicite de
    /// l'utilisateur.
    ///
    /// @param dossierSauvegarde où écrire la sauvegarde complète (base + audio) avant de détruire
    /// @param accepterPerte l'utilisateur a **vu et accepté** que l'audio de certaines nuits ne reviendra
    ///     pas. Sans cela, la procédure refuse de démarrer dès qu'une nuit est en « perdu »
    /// @return ce qui s'est passé, ou le **refus** motivé — auquel cas rien n'a été touché
    public ResultatReset executer(Path dossierSauvegarde, boolean accepterPerte) {
        Objects.requireNonNull(dossierSauvegarde, "dossierSauvegarde");

        BilanRecuperabilite bilan = recuperabilite.bilan();
        if (bilan.perteAnnoncee() && !accepterPerte) {
            return new ResultatReset.Refuse(
                    "l'audio de " + bilan.nombre(SourceAudio.PERDU)
                            + " nuit(s) ne reviendrait pas, et la perte n'a pas été acceptée.",
                    bilan);
        }

        // LE garde-fou : la base neuve se repeuple depuis le serveur. S'il ne répond pas, la détruire
        // laisserait un workspace vide. Aucune sauvegarde ne rendrait ça acceptable : on refuse ici.
        if (!(client.moi() instanceof ReponseApi.Succes)) {
            return new ResultatReset.Refuse(
                    "VigieChiro ne répond pas (ou vous n'êtes pas connecté). La base neuve se repeuple"
                            + " depuis la plateforme : sans elle, le reset laisserait un workspace vide.",
                    bilan);
        }

        List<String> aRetablir = bilan.nuits().stream()
                .filter(nuit -> nuit.source() != SourceAudio.SERVEUR)
                .map(RecuperabiliteNuit::enClair)
                .toList();

        // L'observateur survit au reset : c'est la MEME personne qui repart d'une base neuve. Sans cette
        // précaution, tout ce que les rapprocheurs recréeraient (sites, points) pointerait sur un
        // propriétaire disparu — clé étrangère morte, échec avalé par le contrat best-effort, workspace
        // muet. Le service `idUtilisateurCourant` est un singleton déjà résolu : il ne se recréerait pas.
        List<Utilisateur> observateur = utilisateurDao.findAll();

        BilanSauvegarde copie = sauvegarde.sauvegarderComplet(dossierSauvegarde);
        Path filet = baseNeuve.repartirDeZero();
        observateur.forEach(utilisateurDao::insert);
        int reconstruits = repeupler();

        return new ResultatReset.Fait(copie, filet, reconstruits, audit.auditerTout(), aRetablir);
    }

    /// Rejoue depuis la plateforme tout ce qu'elle connaît de l'observateur : d'abord les rapprochements
    /// (référentiel de taxons, sites, points — sans eux, une participation n'aurait nulle part où
    /// s'accrocher), puis chaque participation orpheline, reconstruite en **passage archivé**.
    ///
    /// Une participation qui échoue n'arrête pas les autres : l'audit final la signalera, et un
    /// `reconstruire-passage` ciblé la rattrapera. Tout abandonner parce qu'une nuit résiste laisserait
    /// le workspace à moitié repeuplé, ce qui est pire.
    private int repeupler() {
        rapprocheurs.forEach(rapprocheur -> rapprocheur.synchroniser(client));

        int reconstruits = 0;
        List<ParticipationOrpheline> orphelines = new ArrayList<>(reconstruction.orphelines());
        for (ParticipationOrpheline orpheline : orphelines) {
            try {
                reconstruction.reconstruire(orpheline.idParticipation());
                reconstruits++;
            } catch (RuntimeException echec) {
                // Signalée par l'audit final : c'est lui qui fait foi sur l'état du workspace.
            }
        }
        return reconstruits;
    }
}
