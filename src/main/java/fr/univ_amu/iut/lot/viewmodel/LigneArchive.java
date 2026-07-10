package fr.univ_amu.iut.lot.viewmodel;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Ligne **observable** de la table de suivi du dépôt (#820) : une archive ZIP `<préfixe>-N.zip` et son
/// avancement. L'IHM lie ses propriétés à une ligne de `TableView` (état → couleur/icône, fraction →
/// barre, taille → texte, avec un `~` tant que l'état n'est pas [EtatArchive#TERMINEE]).
///
/// Le numéro et le nombre de fichiers sont fixés dès la planification ; l'état, la fraction, la taille et
/// la raison d'échec évoluent au fil de la compression. Les mutateurs sont **paquet-privés** : seul
/// [SuiviLignesArchives] les pilote, à appeler sur le **fil JavaFX**.
public final class LigneArchive {

    private final int numero;
    private final int nombreFichiers;
    private final ReadOnlyObjectWrapper<EtatArchive> etat;
    private final ReadOnlyDoubleWrapper fraction;
    private final ReadOnlyLongWrapper tailleOctets;
    private final ReadOnlyStringWrapper raisonEchec;

    /// Crée une ligne « en attente » : `tailleEstimeeOctets` est l'estimation compressée affichée avant que
    /// la taille réelle ne soit connue ([#terminer]).
    LigneArchive(int numero, int nombreFichiers, long tailleEstimeeOctets) {
        this.numero = numero;
        this.nombreFichiers = nombreFichiers;
        this.etat = new ReadOnlyObjectWrapper<>(this, "etat", EtatArchive.EN_ATTENTE);
        this.fraction = new ReadOnlyDoubleWrapper(this, "fraction", 0.0);
        this.tailleOctets = new ReadOnlyLongWrapper(this, "tailleOctets", tailleEstimeeOctets);
        this.raisonEchec = new ReadOnlyStringWrapper(this, "raisonEchec", "");
    }

    /// Numéro croissant de l'archive (1, 2, …), tel qu'il nomme `<préfixe>-N.zip`.
    public int numero() {
        return numero;
    }

    /// Nombre de séquences que l'archive contient.
    public int nombreFichiers() {
        return nombreFichiers;
    }

    public ReadOnlyObjectProperty<EtatArchive> etatProperty() {
        return etat.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty fractionProperty() {
        return fraction.getReadOnlyProperty();
    }

    public ReadOnlyLongProperty tailleOctetsProperty() {
        return tailleOctets.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty raisonEchecProperty() {
        return raisonEchec.getReadOnlyProperty();
    }

    /// `true` tant que la taille affichée est une **estimation** (état autre que [EtatArchive#TERMINEE]) :
    /// l'IHM préfixe alors la taille d'un `~`.
    public boolean tailleEstimee() {
        return etat.get() != EtatArchive.TERMINEE;
    }

    /// La compression de l'archive commence : passe « en cours » (fraction remise à 0).
    void demarrer() {
        etat.set(EtatArchive.EN_COURS);
        fraction.set(0.0);
    }

    /// Avancement intra-archive `f` (0→1) : garde la fraction **monotone** et l'état « en cours ».
    void progresser(double f) {
        etat.set(EtatArchive.EN_COURS);
        fraction.set(Math.max(fraction.get(), f));
    }

    /// L'archive est écrite : passe « terminée », fraction à 1, `tailleReelleOctets` remplace l'estimation.
    void terminer(long tailleReelleOctets) {
        etat.set(EtatArchive.TERMINEE);
        fraction.set(1.0);
        tailleOctets.set(tailleReelleOctets);
    }

    /// La compression a échoué : passe « échec » et retient `raison` (pour une infobulle côté IHM).
    void echouer(String raison) {
        etat.set(EtatArchive.ECHEC);
        raisonEchec.set(raison == null ? "" : raison);
    }
}
