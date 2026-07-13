package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.SuiviLignes;
import fr.univ_amu.iut.lot.model.DepotUnite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

/// Table de dépôt VigieChiro (#983) côté ViewModel : spécialise le socle [SuiviLignes] pour refléter
/// l'état **persisté** des unités (`depot_unite`, #981 — réhydratation à la réouverture) et les
/// événements du moteur reprenable (#982) pendant un dépôt. Les événements ciblent leur ligne par
/// **identifiant** (nom du fichier, la clé de reprise), traduit ici en numéro de ligne.
///
/// Expose [#resteAReprendreProperty()] : `true` quand un dépôt a été entamé et qu'il reste des unités
/// non déposées — l'IHM bascule alors le bouton en « Retenter les échecs » (le moteur ne re-téléverse
/// que le manquant).
///
/// **Fil JavaFX** : ces méthodes mutent des collections/propriétés observables ; l'appelant (le
/// controller) les invoque sur le fil JavaFX fourni par le socle (`ExecuteurTache#surFilJavaFx()`), comme le
/// callback de progression global.
public final class SuiviLignesDepot extends SuiviLignes<LigneDepot> {

    private final ReadOnlyBooleanWrapper resteAReprendre = new ReadOnlyBooleanWrapper(this, "resteAReprendre", false);

    /// Compteurs du dépôt courant, pour la barre de statut (#823) : unités déposées / total du plan.
    private final ReadOnlyIntegerWrapper deposees = new ReadOnlyIntegerWrapper(this, "deposees", 0);

    private final ReadOnlyIntegerWrapper total = new ReadOnlyIntegerWrapper(this, "total", 0);

    /// Unités **en cours** de téléversement (compteur honnête #984) : avec le dépôt parallèle, jusqu'à 5
    /// simultanément.
    private final ReadOnlyIntegerWrapper enCours = new ReadOnlyIntegerWrapper(this, "enCours", 0);

    /// Unités **en échec** du dépôt courant (compteur honnête #984) : rendues visibles pour ne plus
    /// masquer un dépôt qui n'avance pas — la barre ne comptait auparavant que les succès.
    private final ReadOnlyIntegerWrapper echecs = new ReadOnlyIntegerWrapper(this, "echecs", 0);

    /// Pose (ou re-pose) la table depuis l'état **persisté** des unités : chaque statut de `depot_unite`
    /// est traduit en état de ligne (à déposer → en attente ; en cours interrompu → en attente, il sera
    /// re-tenté ; déposé → terminée ; échec → échec avec sa raison en infobulle).
    public void planifier(List<DepotUnite> unites) {
        List<LigneDepot> lignes = new ArrayList<>(unites.size());
        int numero = 1;
        for (DepotUnite unite : unites) {
            LigneDepot ligne = new LigneDepot(numero++, unite.identifiantUnite(), unite.type());
            switch (unite.statut()) {
                case A_DEPOSER -> {
                    /* état initial de LigneSuivi : en attente */
                }
                case EN_COURS -> {
                    // Interrompue sans confirmation (#981) : elle sera re-tentée, on la montre en attente.
                }
                case DEPOSE -> ligne.terminer();
                case ECHEC -> ligne.echouer(unite.messageErreur());
            }
            lignes.add(ligne);
        }
        remplacerLignes(lignes);
        recalculerReste();
    }

    /// Le téléversement de l'unité `identifiant` commence (ligne « en cours »).
    public void demarree(String identifiant) {
        parIdentifiant(identifiant).ifPresent(LigneDepot::demarrer);
        recalculerReste();
    }

    /// L'unité `identifiant` est téléversée et finalisée (ligne « terminée »).
    public void deposee(String identifiant) {
        parIdentifiant(identifiant).ifPresent(LigneDepot::terminer);
        recalculerReste();
    }

    /// Le téléversement de l'unité `identifiant` a échoué (ligne « échec », raison en infobulle).
    public void echouee(String identifiant, String raison) {
        parIdentifiant(identifiant).ifPresent(ligne -> ligne.echouer(raison));
        recalculerReste();
    }

    /// Avancement (fraction 0 à 1, #984) du téléversement de l'unité `identifiant` : alimente la barre
    /// déterminée de sa ligne. N'affecte pas les compteurs (l'unité reste « en cours »).
    public void progresse(String identifiant, double fraction) {
        parIdentifiant(identifiant).ifPresent(ligne -> ligne.progresser(fraction));
    }

    /// Vide la table **et** le drapeau de reprise (retour à « Téléverser sur Vigie-Chiro »).
    @Override
    public void reinitialiser() {
        super.reinitialiser();
        recalculerReste();
    }

    /// `true` quand un dépôt a été entamé (table non vide) et qu'il reste des unités non déposées :
    /// l'action de téléversement devient une **reprise** (« Retenter les échecs »).
    public ReadOnlyBooleanProperty resteAReprendreProperty() {
        return resteAReprendre.getReadOnlyProperty();
    }

    private Optional<LigneDepot> parIdentifiant(String identifiant) {
        return lignes().stream()
                .filter(ligne -> ligne.identifiant().equals(identifiant))
                .findFirst();
    }

    /// Unités déposées du plan courant (barre de statut #823).
    public ReadOnlyIntegerProperty deposeesProperty() {
        return deposees.getReadOnlyProperty();
    }

    /// Taille du plan courant (barre de statut #823) ; `0` sans dépôt entamé.
    public ReadOnlyIntegerProperty totalProperty() {
        return total.getReadOnlyProperty();
    }

    /// Unités en cours de téléversement du plan courant (compteur honnête #984).
    public ReadOnlyIntegerProperty enCoursProperty() {
        return enCours.getReadOnlyProperty();
    }

    /// Unités en échec du plan courant (compteur honnête #984).
    public ReadOnlyIntegerProperty echecsProperty() {
        return echecs.getReadOnlyProperty();
    }

    private void recalculerReste() {
        int terminees = 0;
        int enCoursN = 0;
        int echecsN = 0;
        for (LigneDepot ligne : lignes()) {
            switch (ligne.etatProperty().get()) {
                case TERMINEE -> terminees++;
                case EN_COURS -> enCoursN++;
                case ECHEC -> echecsN++;
                case EN_ATTENTE -> {
                    /* planifiée, pas encore traitée */
                }
            }
        }
        deposees.set(terminees);
        enCours.set(enCoursN);
        echecs.set(echecsN);
        total.set(lignes().size());
        resteAReprendre.set(!lignes().isEmpty() && terminees < lignes().size());
    }
}
