package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.model.PropositionsEnregistreur;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;

/// ViewModel de la modale « Modifier le rattachement » (E2.S8) : corrige l'année ou le numéro de
/// passage d'un passage importé, sans changer de site/point.
///
/// Pur (aucun `javafx.scene` — règle ArchUnit `viewmodel_sans_javafx_ui`). Expose les deux champs
/// éditables ([#anneeProperty], [#numeroPassageProperty]), un **récapitulatif** réactif des
/// conséquences ([#recapProperty] : « X → Y, N séquences renommées ») et un message d'erreur. Le
/// carré et le code point (inchangés) sont fournis par la navigation : le `model`/`viewmodel` ne
/// dépend pas de `sites`. [#valider] délègue à [ServiceRattachement#modifierRattachement].
public class RattachementViewModel {

    /// Sépare un avant et un après (récapitulatif de rattachement, réalignement d'heures).
    private static final String VERS = " → ";

    private final ServicePassage service;
    private final ServiceRattachement rattachement;

    /// Passerelle VigieChiro (axe 4), **optionnelle** : présente dans l'app complète (connexion). Sert à
    /// **pousser** les métadonnées du passage vers sa participation à la validation ([#pousserVersVigieChiro]).
    private final Optional<SynchronisationParticipation> synchronisation;

    private final IntegerProperty annee = new SimpleIntegerProperty(this, "annee");
    private final IntegerProperty numeroPassage = new SimpleIntegerProperty(this, "numeroPassage");
    private final ReadOnlyStringWrapper recap = new ReadOnlyStringWrapper(this, "recap", "");
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

    /// Le renommage (année + n°) est-il **verrouillé** ? Vrai dès qu'un passage est déposé (ou en cours de
    /// dépôt) : son nom est l'identité de ses fichiers côté serveur (garde #1134). Dans ce cas la modale
    /// reste ouvrable pour la **météo + le micro** (qui n'ont rien à voir avec le nom), mais l'année et le
    /// n° sont en lecture seule.
    private final ReadOnlyBooleanWrapper renommageVerrouille =
            new ReadOnlyBooleanWrapper(this, "renommageVerrouille", false);

    /// Conditions de dépôt (météo + matériel du micro) éditées **dans la même modale** : la saisie de
    /// ces métadonnées VigieChiro se fait au moment où l'on modifie le passage, plutôt que sur l'écran
    /// M-Passage. Partage la ligne de message d'erreur de la modale.
    private final SaisiePassageConditions conditions;

    private Long idPassage;
    private String carre;
    private String codePoint;
    private int anneeActuelle;
    private int numeroActuel;
    private int nombreSequences;

    public RattachementViewModel(
            ServicePassage service,
            ServiceRattachement rattachement,
            ServiceConditionsPassage conditionsPassage,
            PropositionsEnregistreur propositionsEnregistreur,
            Optional<SynchronisationParticipation> synchronisation) {
        this.service = Objects.requireNonNull(service, "service");
        this.synchronisation = Objects.requireNonNull(synchronisation, "synchronisation");
        this.rattachement = Objects.requireNonNull(rattachement, "rattachement");
        this.conditions = new SaisiePassageConditions(conditionsPassage, propositionsEnregistreur, messageErreur);
        annee.addListener((observable, avant, apres) -> majRecap());
        numeroPassage.addListener((observable, avant, apres) -> majRecap());
    }

    /// Sous-ViewModel des conditions de dépôt (météo + micro), lié aux champs de la modale.
    public SaisiePassageConditions conditions() {
        return conditions;
    }

    /// Initialise la modale sur le passage `idPassage` (carré et code point fournis par la
    /// navigation). Pré-remplit les champs avec les valeurs courantes lues via le service.
    public void ouvrirSur(Long idPassage, String carre, String codePoint) {
        this.idPassage = Objects.requireNonNull(idPassage, "idPassage");
        this.carre = Objects.requireNonNull(carre, "carre");
        this.codePoint = Objects.requireNonNull(codePoint, "codePoint");
        DetailPassage detail = service.detailPassage(idPassage);
        anneeActuelle = detail.annee();
        numeroActuel = detail.numeroPassage();
        nombreSequences = detail.nombreSequences();
        renommageVerrouille.set(
                detail.statut() == StatutWorkflow.DEPOSE || detail.statut() == StatutWorkflow.DEPOT_EN_COURS);
        conditions.charger(idPassage, detail.meteo(), detail.idEnregistreur(), detail.heureDebut(), detail.heureFin());
        messageErreur.set("");
        annee.set(detail.annee());
        numeroPassage.set(detail.numeroPassage());
        majRecap();
    }

    /// Applique le nouveau rattachement (année + n° saisis), après validation des bornes.
    ///
    /// @return `true` si l'opération a réussi (la vue peut fermer la modale) ; `false` sinon (saisie
    ///     invalide, ou échec opérationnel — R5, disque, base — dont le motif est dans
    ///     [#messageErreurProperty])
    public boolean valider() {
        if (numeroPassage.get() < 1) {
            messageErreur.set("Le numéro de passage doit être supérieur ou égal à 1.");
            return false;
        }
        if (annee.get() < 1000 || annee.get() > 9999) {
            messageErreur.set("L'année doit comporter quatre chiffres.");
            return false;
        }
        try {
            rattachement.modifierRattachement(
                    idPassage, new Prefixe(carre, annee.get(), numeroPassage.get(), codePoint));
            messageErreur.set("");
            return true;
        } catch (RuntimeException echec) {
            // Surface toute défaillance opérationnelle dans la modale (règle métier R5, disque, base)
            // plutôt que de la laisser échapper au gestionnaire d'action JavaFX (cf. PassageViewModel).
            messageErreur.set(echec.getMessage());
            return false;
        }
    }

    /// Applique les conditions de dépôt (météo + micro) et, **si le renommage n'est pas verrouillé**, le
    /// rattachement (année + n°). Enregistre d'abord les conditions (une saisie non numérique laisse la
    /// modale ouverte sans rien renommer sur le disque). Sur un passage déposé
    /// ([#renommageVerrouilleProperty]), l'année et le n° sont l'identité serveur : on ne les touche pas et
    /// on n'appelle pas [#valider] (aucun renommage). Sinon on délègue à [#valider] (qui renomme les
    /// séquences). Renvoie `true` si **tout** a réussi (la vue peut fermer la modale).
    public boolean appliquer() {
        if (!conditions.enregistrerMeteo()
                || !conditions.enregistrerMateriel()
                || !conditions.enregistrerEnregistreur()
                || !conditions.horaires().enregistrer()) {
            return false;
        }
        if (renommageVerrouille.get()) {
            return true;
        }
        return valider();
    }

    /// **Envoie** les métadonnées du passage (météo / micro / enregistreur / dates) vers sa participation
    /// VigieChiro (PATCH) et **rend compte** de ce qui s'est passé (#1839). À appeler **hors du fil JavaFX**
    /// (réseau) ; ne touche aucun contrôle (VM pur) - le compte rendu s'affiche ensuite via
    /// [#signalerEnvoi]. Idempotent : rejouable après un échec réseau.
    ///
    /// Avant #1839, cette méthode était `void` et avalait tout : le `ResultatEcriture` (qui porte la
    /// **cause** d'un refus) était jeté, l'exception aussi, et l'appelant ignorait ses erreurs. Un envoi
    /// raté était donc indiscernable d'un envoi réussi - ce que l'ADR 0008 interdit. Les trois causes
    /// d'empêchement (passage non lié, participation introuvable, point d'écoute introuvable) étaient de
    /// surcroît confondues sous un seul `catch` commenté « pas encore lié ».
    public CompteRenduEnvoi pousserVersVigieChiro() {
        if (idPassage == null) {
            return CompteRenduEnvoi.rienAFaire();
        }
        if (synchronisation.isEmpty()) {
            return CompteRenduEnvoi.reussi("Non connecté à Vigie-Chiro : les métadonnées partiront au dépôt.");
        }
        try {
            EnvoiParticipation envoi = synchronisation.get().pousserVers(idPassage);
            // Le succès se lit sur l'échec, pas sur la présence d'un identifiant : un PATCH ne crée rien.
            // L'ancien test `id().isPresent()` n'était vrai que parce que le client recopiait le paramètre
            // dans le champ `id` pour le satisfaire.
            if (!envoi.ecriture().estReussie()) {
                return CompteRenduEnvoi.echoue(
                        "Vigie-Chiro a refusé l'envoi : " + envoi.ecriture().echec());
            }
            // #1885 : un réalignement a modifié les heures de la nuit. Le taire reviendrait à corriger sa
            // saisie dans son dos, et à le priver du moyen de contester la correction si elle est fausse.
            return envoi.realignement()
                    .map(realignement ->
                            CompteRenduEnvoi.aSignaler("Métadonnées envoyées à Vigie-Chiro. " + phrase(realignement)))
                    .orElseGet(() -> CompteRenduEnvoi.reussi("Métadonnées envoyées à Vigie-Chiro."));
        } catch (RegleMetierException empeche) {
            // La cause EST dite (non lié / participation introuvable / point d'écoute introuvable) au lieu
            // d'être supposée bénigne.
            return CompteRenduEnvoi.echoue("Envoi impossible : " + empeche.getMessage());
        }
    }

    /// Affiche le compte rendu d'un envoi (**sur le fil JavaFX**), jamais un silence.
    public void signalerEnvoi(CompteRenduEnvoi compteRendu) {
        if (!compteRendu.message().isEmpty()) {
            messageErreur.set(compteRendu.message());
        }
    }

    /// Issue d'un envoi vers VigieChiro.
    ///
    /// Deux questions distinctes, qu'il ne faut pas confondre :
    ///  - `reussi` : l'envoi a-t-il abouti ? Distingue ce qui **peut être ignoré** (rien à envoyer, hors
    ///    connexion : les métadonnées partiront au dépôt) d'un refus serveur ou d'un empêchement ;
    ///  - `aSignaler` : s'est-il passé quelque chose que l'utilisateur **doit voir** ? Un envoi peut très
    ///    bien réussir *et* mériter d'être commenté - c'est le cas d'un réalignement d'heures (#1885), qui
    ///    modifie ses données.
    ///
    /// C'est [#peutFermer] qui croise les deux : fermer la modale sur un message non lu l'emporterait avec
    /// la fenêtre, exactement le défaut que #1839 a corrigé pour les échecs.
    ///
    /// @param reussi `true` si l'envoi n'a rien à reprocher
    /// @param aSignaler `true` si le message doit être lu avant de refermer
    /// @param message ce qu'il faut dire à l'utilisateur (vide s'il n'y a rien à dire)
    public record CompteRenduEnvoi(boolean reussi, boolean aSignaler, String message) {

        /// Envoi sans reproche ni commentaire.
        static CompteRenduEnvoi reussi(String message) {
            return new CompteRenduEnvoi(true, false, message);
        }

        /// Envoi abouti, mais dont l'issue **doit être lue** avant de refermer.
        static CompteRenduEnvoi aSignaler(String message) {
            return new CompteRenduEnvoi(true, true, message);
        }

        /// Envoi empêché ou refusé.
        static CompteRenduEnvoi echoue(String message) {
            return new CompteRenduEnvoi(false, true, message);
        }

        /// Aucun envoi n'était nécessaire.
        static CompteRenduEnvoi rienAFaire() {
            return new CompteRenduEnvoi(true, false, "");
        }

        /// `true` si la modale peut se refermer : l'envoi a abouti **et** rien n'attend d'être lu.
        public boolean peutFermer() {
            return reussi && !aSignaler;
        }
    }

    /// Phrase décrivant un réalignement, avec l'**avant** et l'**après** : dire seulement la nouvelle heure
    /// n'apprendrait pas ce qui a été corrigé, ni de combien.
    private static String phrase(EnvoiParticipation.Realignement realignement) {
        return "Les heures de la nuit ont été réalignées sur ses enregistrements : "
                + realignement.debutAvant() + VERS + realignement.debutApres() + " (début), "
                + realignement.finAvant() + VERS + realignement.finApres() + " (fin).";
    }

    /// **Tire** les métadonnées (météo / micro) de la participation VigieChiro vers le passage local (cas
    /// « participation préparée sur le site web »). À appeler **hors du fil JavaFX** (réseau) ; ne touche
    /// aucun contrôle (VM pur). Renvoie `true` si des données ont été récupérées (passerelle présente +
    /// passage lié + succès), `false` sinon (best-effort, silencieux).
    public boolean tirerDepuisVigieChiro() {
        if (idPassage == null || synchronisation.isEmpty()) {
            return false;
        }
        try {
            synchronisation.get().tirerDepuis(idPassage);
            return true;
        } catch (RegleMetierException nonLie) {
            return false;
        }
    }

    /// Recharge les champs météo/micro depuis la base **après un tir** (à exécuter sur le fil JavaFX) et
    /// publie un message : confirmation si des données ont été récupérées, aide sinon.
    public void rechargerApresTir(boolean recupere) {
        if (idPassage == null) {
            return;
        }
        if (recupere) {
            DetailPassage rafraichi = service.detailPassage(idPassage);
            conditions.charger(
                    idPassage,
                    rafraichi.meteo(),
                    rafraichi.idEnregistreur(),
                    rafraichi.heureDebut(),
                    rafraichi.heureFin());
            messageErreur.set("Métadonnées récupérées depuis Vigie-Chiro.");
        } else {
            messageErreur.set("Aucune participation Vigie-Chiro liée à ce passage (ou hors connexion).");
        }
    }

    /// `true` si la synchronisation VigieChiro est disponible (observateur connecté) : la vue n'affiche
    /// l'action « Récupérer depuis VigieChiro » que dans ce cas.
    public boolean peutSynchroniser() {
        return synchronisation.isPresent();
    }

    /// Route l'échec inattendu d'une opération réseau de la modale (météo, tir) vers sa ligne
    /// de message, **sur le fil JavaFX** : jamais un silence, ni un bouton resté figé (#1216).
    public void signalerErreur(Throwable erreur) {
        messageErreur.set("L'opération Vigie-Chiro a échoué : " + erreur.getMessage());
    }

    /// `true` si appliquer le rattachement courant **renommera effectivement** les séquences sur le disque
    /// (le préfixe de session change). `false` si rien ne change : l'action n'a alors aucun effet disque
    /// irréversible. La vue s'en sert pour ne demander une confirmation que dans ce cas (#798).
    public boolean entraineRenommage() {
        if (carre == null) {
            return false;
        }
        String avant = new Prefixe(carre, anneeActuelle, numeroActuel, codePoint).nomDossierSession();
        String apres = new Prefixe(carre, annee.get(), numeroPassage.get(), codePoint).nomDossierSession();
        return !avant.equals(apres);
    }

    private void majRecap() {
        if (carre == null) {
            recap.set("");
        } else if (!entraineRenommage()) {
            recap.set("Aucun changement de rattachement.");
        } else {
            String avant = new Prefixe(carre, anneeActuelle, numeroActuel, codePoint).nomDossierSession();
            String apres = new Prefixe(carre, annee.get(), numeroPassage.get(), codePoint).nomDossierSession();
            recap.set("Rattachement : "
                    + avant
                    + VERS
                    + apres
                    + " — "
                    + nombreSequences
                    + " séquence(s) de la nuit seront renommées. Action irréversible.");
        }
    }

    public IntegerProperty anneeProperty() {
        return annee;
    }

    public IntegerProperty numeroPassageProperty() {
        return numeroPassage;
    }

    public ReadOnlyStringProperty recapProperty() {
        return recap.getReadOnlyProperty();
    }

    /// `true` si l'année et le n° de passage sont **verrouillés** (passage déposé) : la vue met alors les
    /// deux spinners en lecture seule, tout en laissant éditer la météo et le micro.
    public ReadOnlyBooleanProperty renommageVerrouilleProperty() {
        return renommageVerrouille.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }
}
