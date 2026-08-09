package fr.univ_amu.iut.commun.viewmodel;

import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.JournalMutations;
import java.util.Objects;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;

/// Compteur observable des mutations structurelles validées (#3541) : côté observable du port
/// [JournalMutations], pour qu'un écran suive la **base** au lieu de suivre la navigation.
///
/// Même figure que [ReglagesReactifs], appliquée à la base plutôt qu'aux réglages : un service qui
/// n'est pas observable le devient par une couche mince au-dessus de lui.
///
/// ## Pourquoi un compteur et pas un booléen
///
/// Deux mutations qui se suivent doivent réveiller **deux fois** un lecteur qui aurait manqué la
/// première. Un drapeau qu'on lève et qu'on baisse perd la seconde ; un compteur qui avance ne perd
/// rien.
///
/// ## Pourquoi un exécuteur injecté
///
/// Une synchronisation ou un import signalent depuis un fil d'arrière-plan, et une `Property` JavaFX
/// se mute sur le fil JavaFX. L'exécuteur du **fil d'affichage** est donc fourni au constructeur :
/// `ExecuteurTache#surFilJavaFx()` en production, `Runnable::run` en test. Écrire `Platform.runLater`
/// en dur ferait de chaque test un test asynchrone, et rendrait cette classe intestable hors TestFX.
@Singleton
public final class RevisionDonnees implements JournalMutations {

    private final Executor filAffichage;
    private final ReadOnlyLongWrapper revision = new ReadOnlyLongWrapper(this, "revision", 0L);

    public RevisionDonnees(Executor filAffichage) {
        this.filAffichage = Objects.requireNonNull(filAffichage, "filAffichage");
    }

    /// Révision courante, en **lecture seule** : un lecteur observe, il ne fait pas avancer le
    /// compteur. Sa valeur n'a pas de sens en soi ; seul son **changement** en a un.
    public ReadOnlyLongProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    /// La révision avance, **sur le fil d'affichage**. Appelable depuis n'importe quel fil ; les deux
    /// règles d'appel (après validation, une fois par opération métier) appartiennent au port.
    @Override
    public void mutationStructurelleValidee() {
        filAffichage.execute(() -> revision.set(revision.get() + 1));
    }
}
