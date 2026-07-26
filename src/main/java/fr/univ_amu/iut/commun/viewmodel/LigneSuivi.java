package fr.univ_amu.iut.commun.viewmodel;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Ligne **observable** d'une table de suivi par unité : une unité de travail (archive ZIP #820,
/// fichier d'import…) et son avancement. L'IHM lie ses propriétés à une ligne de `TableView` (état →
/// couleur/icône, fraction → barre, raison d'échec → infobulle).
///
/// Le numéro est fixé dès la planification ; l'état, la fraction et la raison d'échec évoluent au fil
/// du travail. Les mutateurs sont réservés au pilote ([SuiviLignes]) et à ses spécialisations par
/// feature, à appeler sur le **fil JavaFX**. Une feature qui a des colonnes propres (taille, nom de
/// fichier…) **étend** cette classe.
public class LigneSuivi {

    private final int numero;
    private final ReadOnlyObjectWrapper<EtatUnite> etat;
    private final ReadOnlyDoubleWrapper fraction;
    private final ReadOnlyStringWrapper raisonEchec;

    /// Mention transitoire de **reprise réseau** (#2354) : « Nouvelle tentative dans N s… » pendant
    /// qu'une coupure momentanée est réessayée. Vide en temps normal ; effacée dès que le travail
    /// (re)progresse ou se conclut. Générique : toute opération longue réessayée peut la poser.
    private final ReadOnlyStringWrapper messageReprise;

    /// Crée une ligne « en attente », de fraction nulle et sans raison d'échec.
    public LigneSuivi(int numero) {
        this.numero = numero;
        this.etat = new ReadOnlyObjectWrapper<>(this, "etat", EtatUnite.EN_ATTENTE);
        this.fraction = new ReadOnlyDoubleWrapper(this, "fraction", 0.0);
        this.raisonEchec = new ReadOnlyStringWrapper(this, "raisonEchec", "");
        this.messageReprise = new ReadOnlyStringWrapper(this, "messageReprise", "");
    }

    /// Numéro croissant de l'unité (1, 2, …), par lequel les événements de suivi ciblent leur ligne.
    public int numero() {
        return numero;
    }

    public ReadOnlyObjectProperty<EtatUnite> etatProperty() {
        return etat.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty fractionProperty() {
        return fraction.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty raisonEchecProperty() {
        return raisonEchec.getReadOnlyProperty();
    }

    /// Mention de reprise réseau à afficher (vide si aucune reprise en cours), observable par la cellule
    /// de progression.
    public ReadOnlyStringProperty messageRepriseProperty() {
        return messageReprise.getReadOnlyProperty();
    }

    /// Pose la mention de reprise (#2354), réservée aux spécialisations qui réessaient (le dépôt). Elle
    /// s'efface d'elle-même au prochain changement d'état de l'unité.
    protected void signalerReprise(String message) {
        messageReprise.set(message == null ? "" : message);
    }

    /// Le traitement de l'unité commence : passe « en cours » (fraction remise à 0).
    public void demarrer() {
        etat.set(EtatUnite.EN_COURS);
        fraction.set(0.0);
        messageReprise.set("");
    }

    /// Avancement intra-unité `f` (0→1) : garde la fraction **monotone** et l'état « en cours ». Efface
    /// une éventuelle mention de reprise : les octets repartent, la coupure est absorbée.
    public void progresser(double f) {
        etat.set(EtatUnite.EN_COURS);
        fraction.set(Math.max(fraction.get(), f));
        messageReprise.set("");
    }

    /// L'unité est traitée : passe « terminée », fraction à 1.
    public void terminer() {
        etat.set(EtatUnite.TERMINEE);
        fraction.set(1.0);
        messageReprise.set("");
    }

    /// Le traitement a échoué : passe « échec » et retient `raison` (pour une infobulle côté IHM).
    public void echouer(String raison) {
        etat.set(EtatUnite.ECHEC);
        raisonEchec.set(raison == null ? "" : raison);
        messageReprise.set("");
    }
}
