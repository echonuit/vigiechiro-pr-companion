package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.IntegerProperty;
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

    private final ServicePassage service;
    private final ServiceRattachement rattachement;

    /// Passerelle VigieChiro (axe 4), **optionnelle** : présente dans l'app complète (connexion). Sert à
    /// **pousser** les métadonnées du passage vers sa participation à la validation ([#pousserVersVigieChiro]).
    private final Optional<SynchronisationParticipation> synchronisation;

    /// Import des observations (#1264), via le port du socle : absent hors connexion.
    private final Optional<ImportObservations> importObservations;

    private final IntegerProperty annee = new SimpleIntegerProperty(this, "annee");
    private final IntegerProperty numeroPassage = new SimpleIntegerProperty(this, "numeroPassage");
    private final ReadOnlyStringWrapper recap = new ReadOnlyStringWrapper(this, "recap", "");
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

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
            Optional<SynchronisationParticipation> synchronisation,
            Optional<ImportObservations> importObservations) {
        this.service = Objects.requireNonNull(service, "service");
        this.synchronisation = Objects.requireNonNull(synchronisation, "synchronisation");
        this.importObservations = Objects.requireNonNull(importObservations, "importObservations");
        this.rattachement = Objects.requireNonNull(rattachement, "rattachement");
        this.conditions = new SaisiePassageConditions(conditionsPassage, messageErreur);
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
        conditions.charger(idPassage, detail.meteo());
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

    /// Applique **d'un bloc** le rattachement (année + n°) et les conditions de dépôt (météo + micro)
    /// saisis dans la modale. Enregistre d'abord les conditions (une saisie non numérique laisse la
    /// modale ouverte sans rien renommer sur le disque), puis délègue au rattachement [#valider] (qui,
    /// lui, renomme les séquences). Renvoie `true` si **tout** a réussi (la vue peut fermer la modale).
    public boolean appliquer() {
        if (!conditions.enregistrerMeteo() || !conditions.enregistrerMateriel()) {
            return false;
        }
        return valider();
    }

    /// Pousse les métadonnées du passage (météo / micro / dates) vers sa **participation VigieChiro** (PATCH),
    /// **au mieux** : silencieux si le passage n'est pas (encore) lié à une participation (import hors-ligne →
    /// les métadonnées partiront au dépôt) ou si l'API est indisponible. À appeler **hors du fil JavaFX**
    /// (réseau) ; ne touche aucun contrôle (VM pur). Idempotent — rappelable après chaque validation.
    public void pousserVersVigieChiro() {
        if (idPassage == null) {
            return;
        }
        synchronisation.ifPresent(sync -> {
            try {
                sync.pousserVers(idPassage);
            } catch (RegleMetierException nonLie) {
                // Passage pas encore lié à une participation, ou participation introuvable : best-effort ;
                // les métadonnées seront envoyées au dépôt (création à ce moment-là). Silencieux.
            }
        });
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
            conditions.charger(idPassage, service.detailPassage(idPassage).meteo());
            messageErreur.set("Métadonnées récupérées depuis VigieChiro.");
        } else {
            messageErreur.set("Aucune participation VigieChiro liée à ce passage (ou hors connexion).");
        }
    }

    /// `true` si la synchronisation VigieChiro est disponible (observateur connecté) : la vue n'affiche
    /// l'action « Synchroniser depuis VigieChiro » que dans ce cas.
    public boolean peutSynchroniser() {
        return synchronisation.isPresent();
    }

    /// `true` si l'action **« Importer les observations »** a lieu d'être (#1264) : l'import est disponible
    /// (observateur connecté) **et** la nuit est rattachée à une participation. Sinon, il n'y a rien à
    /// importer — et l'action n'apparaît pas plutôt que d'apparaître inerte.
    public boolean peutImporter() {
        return idPassage != null
                && importObservations.isPresent()
                && importObservations.get().estRattache(idPassage);
    }

    /// Importe les observations de la nuit depuis Vigie-Chiro. **Bloquant** (réseau) : à appeler **hors du
    /// fil JavaFX**. Renvoie le compte rendu à afficher — ou, s'il n'y a rien à importer, **la raison**
    /// (analyse jamais lancée, en cours, en échec…), que l'import sait désormais donner (#1264).
    public String importerObservations() {
        try {
            return importObservations
                    .orElseThrow(() -> new RegleMetierException("Import VigieChiro indisponible dans ce contexte."))
                    .importer(idPassage, false);
        } catch (RegleMetierException rien) {
            // Le refus n'est pas un incident : c'est une réponse, et elle dit quoi faire.
            return rien.getMessage();
        }
    }

    /// Publie le compte rendu de l'import (à exécuter **sur le fil JavaFX**).
    public void restituerImport(String compteRendu) {
        messageErreur.set(compteRendu);
    }

    /// Route l'échec inattendu d'une opération réseau de la modale (import, météo, tir) vers sa ligne
    /// de message, **sur le fil JavaFX** : jamais un silence, ni un bouton resté figé (#1216).
    public void signalerErreur(Throwable erreur) {
        messageErreur.set("L'opération VigieChiro a échoué : " + erreur.getMessage());
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
                    + " → "
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

    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }
}
