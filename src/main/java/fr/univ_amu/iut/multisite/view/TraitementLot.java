package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.IssueTraitement;
import fr.univ_amu.iut.commun.model.MoteurTraitementGroupe;
import fr.univ_amu.iut.commun.model.ResultatTraitementGroupe;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.DialogueProgression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.GesteAttendu;
import fr.univ_amu.iut.commun.viewmodel.TexteCompteRendu;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.stage.Window;

/// Applique une [ActionGroupee] à la **sélection** de « Carte & passages » (#2357, lot 3).
///
/// ## Trois moments, trois ports déjà là
///
/// **Annoncer** ce qui sera écarté, et pourquoi, via [Confirmateur] : « un lot qui ignore
/// silencieusement la moitié de la sélection est pire qu'un lot qui refuse ». L'observateur lit la
/// liste avant de décider, et peut renoncer.
///
/// **Exécuter** via [SuiviOperation] : barre, étape courante et bouton « Annuler » existent déjà, et
/// le jeton d'annulation qu'il fournit est exactement ce que [MoteurTraitementGroupe] attend.
///
/// **Rendre compte** via [Notificateur] : une ligne par passage, avec son sort.
///
/// Aucune fenêtre n'est inventée ici. C'est délibéré : ces trois ports sont **injectables**, donc le
/// geste entier se teste headless, jusqu'à son effet. Une modale dédiée aurait été plus riche (le
/// journal ligne à ligne pendant l'exécution) et intestable au même prix.
///
/// ## Ce que cette classe ne fait pas
///
/// Elle ne connaît **aucune** règle d'éligibilité : chaque [ActionGroupee] porte la sienne. Elle ne
/// parallélise rien : le moteur est séquentiel, délibérément (voir sa documentation).
public final class TraitementLot {

    /// La rédaction des échecs est celle de **l'application** (ADR 2635) : le modèle dit ce qui manque,
    /// [GesteAttendu] ajoute où le régler. Sans elle, un jeton expiré en cours de lot laisserait autant
    /// de lignes « non connecté » que de nuits restantes, aucune ne disant quoi faire.
    private final MoteurTraitementGroupe moteur = new MoteurTraitementGroupe(GesteAttendu::message);
    private final Confirmateur confirmateur;
    private final Notificateur notificateur;
    private final SuiviOperation suivi;

    /// Les porteurs **modifiables** des deux ports de dialogue, quand c'est cet objet qui les tient.
    /// `null` avec le constructeur à trois ports (les tests fournissent leurs propres doubles).
    private final ConfirmateurModifiable porteurConfirmation;

    private final NotificateurModifiable porteurCompteRendu;

    /// Forme **de production** : l'objet porte ses propres ports, et l'écran n'a qu'un champ.
    ///
    /// Les porteurs sont exposés ([#confirmateur], [#notificateur]) pour qu'un test d'écran y pose ses
    /// doubles - c'est le patron du dépôt (#1013, #1405), et c'est ce qui rend le geste vérifiable
    /// jusqu'à son effet depuis la vue.
    public TraitementLot(ExecuteurTache executeur) {
        this.porteurConfirmation = new ConfirmateurModifiable();
        this.porteurCompteRendu = new NotificateurModifiable();
        this.confirmateur = porteurConfirmation;
        this.notificateur = porteurCompteRendu;
        this.suivi = new DialogueProgression(Objects.requireNonNull(executeur, "executeur"));
    }

    /// Forme **de test** : les trois ports sont fournis, aucun n'est porté.
    public TraitementLot(Confirmateur confirmateur, Notificateur notificateur, SuiviOperation suivi) {
        this.porteurConfirmation = null;
        this.porteurCompteRendu = null;
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
        this.notificateur = Objects.requireNonNull(notificateur, "notificateur");
        this.suivi = Objects.requireNonNull(suivi, "suivi");
    }

    /// Porteur de confirmation, pour qu'un test d'écran y pose son double.
    public ConfirmateurModifiable confirmateur() {
        return porteurConfirmation;
    }

    /// Porteur de compte rendu, pour qu'un test d'écran y pose son double.
    public NotificateurModifiable notificateur() {
        return porteurCompteRendu;
    }

    /// Lance `action` sur `lignes`, après annonce et confirmation. Sans effet si l'on renonce.
    ///
    /// @param proprietaire fenêtre porteuse du suivi
    /// @param apresCoup rejoué à la fin (rafraîchir le tableau : les statuts ont bougé)
    public void lancer(Window proprietaire, ActionGroupee action, List<LignePassage> lignes, Runnable apresCoup) {
        List<CiblePassage> cibles = lignes.stream().map(TraitementLot::cible).toList();
        List<CiblePassage> eligibles = cibles.stream()
                .filter(c -> action.motifNonEligible(c).isEmpty())
                .toList();

        if (!confirmateur.confirmer(annonce(action, cibles, eligibles))) {
            return;
        }
        if (eligibles.isEmpty()) {
            return; // rien à faire : l'annonce l'a déjà dit, inutile d'ouvrir un suivi vide
        }
        suivi.lancer(
                proprietaire,
                action.libelle(),
                (progression, jeton) -> moteur.executer(action, eligibles, jeton),
                resultat -> {
                    rendreCompte(action, resultat);
                    apresCoup.run();
                },
                apresCoup,
                echec -> notificateur.notifier(
                        NiveauNotification.AVERTISSEMENT,
                        action.libelle(),
                        "Le lot n'a pas pu être mené : " + echec.getMessage()));
    }

    /// Ce qu'on lit **avant** de lancer : combien seront traités, et **lesquels** seront écartés, avec
    /// leur motif. Un décompte sans les noms obligerait à deviner.
    private static CompteRendu annonce(ActionGroupee action, List<CiblePassage> cibles, List<CiblePassage> eligibles) {
        List<Constat> constats = new ArrayList<>();
        constats.add(Constat.de(
                eligibles.size() + " passage(s) sur " + cibles.size() + " vont être traités.",
                eligibles.isEmpty() ? Severite.AVERTISSEMENT : Severite.INFO));

        List<Detail> ecartes = cibles.stream()
                .map(c -> action.motifNonEligible(c).map(motif -> new Detail(c.designation(), motif)))
                .flatMap(Optional::stream)
                .toList();
        if (!ecartes.isEmpty()) {
            constats.add(new Constat(ecartes.size() + " écarté(s) :", Severite.AVERTISSEMENT, ecartes));
        }
        return new CompteRendu(
                action.libelle(), "", constats, eligibles.isEmpty() ? "Rien ne sera fait." : "Lancer le traitement ?");
    }

    /// Rend compte du lot : le bilan structuré, mis à plat par [TexteCompteRendu] puisque le port
    /// [Notificateur] parle en texte. Le niveau distingue « tout est passé » de « il reste à voir ».
    private void rendreCompte(ActionGroupee action, ResultatTraitementGroupe resultat) {
        boolean impeccable = resultat.reussis() == resultat.issues().size();
        notificateur.notifier(
                impeccable ? NiveauNotification.INFORMATION : NiveauNotification.AVERTISSEMENT,
                action.libelle(),
                TexteCompteRendu.rendre(bilan(action, resultat)));
    }

    /// Ce qu'on lit **après** : une ligne par passage, dans l'ordre de soumission, avec son sort.
    private static CompteRendu bilan(ActionGroupee action, ResultatTraitementGroupe resultat) {
        List<Detail> lignes = resultat.issues().stream()
                .map(issue -> new Detail(issue.cible().designation(), libelle(issue)))
                .toList();
        Constat entete = new Constat(
                resultat.reussis() + " réussi(s), " + (resultat.issues().size() - resultat.reussis()) + " non traité(s)"
                        + (resultat.interrompu() ? ", lot interrompu" : ""),
                resultat.reussis() == resultat.issues().size() ? Severite.SUCCES : Severite.AVERTISSEMENT,
                lignes);
        return CompteRendu.de(action.libelle(), List.of(entete));
    }

    private static String libelle(IssueTraitement issue) {
        return switch (issue.statut()) {
            case REUSSI -> "fait";
            case ECARTE -> "écarté : " + issue.motif();
            case ECHEC -> "échec : " + issue.motif();
            case NON_TRAITE -> "non traité (lot interrompu)";
        };
    }

    /// Désignation lisible d'une ligne, telle qu'elle préfixera le journal et le compte rendu.
    private static CiblePassage cible(LignePassage ligne) {
        return new CiblePassage(
                ligne.idPassage(),
                ligne.numeroCarre() + " / " + ligne.codePoint() + " / " + ligne.annee() + " n°"
                        + ligne.numeroPassage());
    }
}
