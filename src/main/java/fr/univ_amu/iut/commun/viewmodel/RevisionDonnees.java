package fr.univ_amu.iut.commun.viewmodel;

import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.JournalMutations;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /// Vrai quand une avancée est déjà postée sur le fil d'affichage et pas encore appliquée.
    /// Posé depuis n'importe quel fil émetteur, baissé sur le fil d'affichage : d'où l'atomique.
    private final AtomicBoolean avanceePostee = new AtomicBoolean(false);

    public RevisionDonnees(Executor filAffichage) {
        this.filAffichage = Objects.requireNonNull(filAffichage, "filAffichage");
    }

    /// Révision courante, en **lecture seule** : un lecteur observe, il ne fait pas avancer le
    /// compteur. Sa valeur n'a pas de sens en soi ; seul son **changement** en a un.
    public ReadOnlyLongProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    /// La révision avance, **sur le fil d'affichage**. Appelable depuis n'importe quel fil.
    ///
    /// **Les signaux en rafale sont amortis** (#3542) : tant qu'une avancée est déjà postée et pas
    /// encore appliquée, les suivantes ne postent rien. Une synchronisation qui crée deux cent
    /// cinquante sites un par un produit donc **un** réveil, pas deux cent cinquante relectures de
    /// quatre `COUNT(*)`.
    ///
    /// C'est ce qui permet à l'émetteur de garder une règle triviale (« tu écris, tu signales »)
    /// plutôt que de devoir reconnaître lui-même la frontière d'une opération métier : la question se
    /// tranche ici, en un endroit, sous test.
    ///
    /// Aucune mutation n'est perdue pour autant. Le lecteur **relit tout** à chaque réveil : ce qui
    /// compte est qu'il finisse à jour, pas qu'il soit réveillé autant de fois qu'il y a eu
    /// d'écritures.
    @Override
    public void mutationStructurelleValidee() {
        // Le drapeau se baisse AVANT l'avancée : une mutation survenue pendant que les lecteurs
        // réagissent est une mutation neuve, et doit reposter. L'inverse l'avalerait.
        if (avanceePostee.compareAndSet(false, true)) {
            filAffichage.execute(() -> {
                avanceePostee.set(false);
                revision.set(revision.get() + 1);
            });
        }
    }
}
