package fr.univ_amu.iut.audio.viewmodel;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// **Ce que le panneau d'écoute doit savoir** de la séquence sélectionnée, extrait du
/// [AudioViewModel] (cohésion, seuil PMD GodClass) comme l'a été [EtatSelectionAudio].
///
/// Trois situations, et une seule offre l'écoute :
///
/// - **fichier là et concordant** : le chemin est servi au lecteur ;
/// - **fichier absent** (#1301) : chemin `null` - le lecteur ne reçoit **jamais** un chemin mort - et
///   [#audioManquant] déclenche l'encart d'explication ;
/// - **fichier là mais substitué** (#2254) : [#audioDivergent] et son [#motifDivergence]. Ce n'est pas
///   une absence : l'absence est un **état**, la substitution un **conflit**. Écouter dessus ferait
///   valider une espèce sur un autre enregistrement, en silence.
///
/// Un fichier absent n'a rien à faire diverger : on ne confronte que ce qui est là.
public final class EtatEcouteAudio {

    private final ReadOnlyObjectWrapper<Path> cheminAudioCourant =
            new ReadOnlyObjectWrapper<>(this, "cheminAudioCourant");
    private final ReadOnlyBooleanWrapper audioManquant = new ReadOnlyBooleanWrapper(this, "audioManquant", false);
    private final ReadOnlyBooleanWrapper audioDivergent = new ReadOnlyBooleanWrapper(this, "audioDivergent", false);
    private final ReadOnlyStringWrapper motifDivergence = new ReadOnlyStringWrapper(this, "motifDivergence", "");

    /// Recalcule les quatre observables depuis le `chemin` de la séquence sélectionnée (`null` =
    /// aucune sélection).
    ///
    /// @param chemin chemin persisté de la séquence, ou `null`
    /// @param manquant le fichier est-il absent du disque ?
    /// @param motifDeDivergence le motif si le fichier présent ne concorde pas, vide sinon
    void maj(Path chemin, Predicate<Path> manquant, Function<Path, String> motifDeDivergence) {
        boolean absent = manquant.test(chemin);
        cheminAudioCourant.set(absent ? null : chemin);
        audioManquant.set(absent);
        String motif = absent || chemin == null ? "" : motifDeDivergence.apply(chemin);
        motifDivergence.set(motif);
        audioDivergent.set(!motif.isEmpty());
    }

    /// Remet l'état au repos : aucune sélection, rien à expliquer.
    void reinitialiser() {
        cheminAudioCourant.set(null);
        audioManquant.set(false);
        audioDivergent.set(false);
        motifDivergence.set("");
    }

    /// Chemin servi au lecteur, `null` quand il n'y a rien d'écoutable.
    public ReadOnlyObjectProperty<Path> cheminAudioCourantProperty() {
        return cheminAudioCourant.getReadOnlyProperty();
    }

    /// `true` quand le fichier de la séquence n'est plus sur disque (#1301).
    public ReadOnlyBooleanProperty audioManquantProperty() {
        return audioManquant.getReadOnlyProperty();
    }

    /// `true` quand le fichier présent n'est **pas** celui que la base décrit (#2254).
    public ReadOnlyBooleanProperty audioDivergentProperty() {
        return audioDivergent.getReadOnlyProperty();
    }

    /// Le motif de la divergence, à afficher en explication ; vide s'il n'y en a pas.
    public ReadOnlyStringProperty motifDivergenceProperty() {
        return motifDivergence.getReadOnlyProperty();
    }
}
