package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.importation.model.ReglageConservationOriginaux;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/// Préférence **« conserver les originaux »** de l'import, extraite de [ImportationViewModel] (Extract
/// Class) : porte le choix éditable (la case de la vue), son défaut persisté et sa mémorisation.
///
/// ## Le propriétaire du réglage a changé, et cette classe ne l'avait pas suivi (#3471)
///
/// La case vivait autrefois **sur l'écran d'import**, liée bidirectionnellement à la propriété
/// ci-dessous, et [#memoriser] la persistait au lancement. Le contrat se tenait.
///
/// Depuis, la case a **déménagé dans Réglages ▸ Import** (réglage avancé), qui écrit la clé
/// directement. Cette classe est pourtant restée un **instantané pris au démarrage** : elle lisait le
/// réglage dans son constructeur, en `@Singleton`, et ne le relisait jamais. Cocher la case puis
/// importer ne conservait donc rien - et pire, `memoriser()` réécrivait la valeur périmée par-dessus
/// celle qu'on venait de poser.
///
/// Elle lit désormais **au moment de servir**, comme le fait la commande CLI `importer` depuis
/// toujours. Les deux surfaces ne peuvent plus diverger.
///
/// VM-agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est importé,
/// jamais `javafx.scene`. La persistance passe par le service socle [Reglages] (jamais un DAO).
public final class PreferenceConservation {

    /// Clé du réglage persisté, **et** son défaut : tous deux dans [ReglageConservationOriginaux], parce
    /// que la commande CLI `importer` les lit aussi et ne peut pas citer le `viewmodel` d'une autre
    /// feature (#2181). Le motif de ce défaut y est écrit une fois pour toutes.
    static final String CLE = ReglageConservationOriginaux.CLE;

    private final Reglages reglages;
    private final BooleanProperty conserverOriginaux = new SimpleBooleanProperty(this, "conserverOriginaux", false);

    public PreferenceConservation(Reglages reglages) {
        this.reglages = Objects.requireNonNull(reglages, "reglages");
        conserverOriginaux.set(lireLeReglage());
    }

    private boolean lireLeReglage() {
        return reglages.lireBooleen(CLE, ReglageConservationOriginaux.DEFAUT);
    }

    /// Propriété **éditable** : la vue y lie bidirectionnellement sa case à cocher (`true` = copie dans
    /// `bruts/`, `false` = transformation directe depuis la source).
    public BooleanProperty conserverOriginauxProperty() {
        return conserverOriginaux;
    }

    /// Valeur courante du choix (`true` = conserver les originaux), **relue** à chaque appel.
    ///
    /// Relire coûte une lecture de réglage par import - négligeable devant les minutes que dure une
    /// transformation - et supprime toute fenêtre pendant laquelle l'écran et la base divergeraient.
    public boolean valeur() {
        boolean courant = lireLeReglage();
        // La propriété reste alignée pour les liaisons éventuelles : elle suit la base, jamais l'inverse.
        conserverOriginaux.set(courant);
        return courant;
    }
}
