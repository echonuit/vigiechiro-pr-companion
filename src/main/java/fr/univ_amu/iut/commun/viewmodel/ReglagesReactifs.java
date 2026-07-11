package fr.univ_amu.iut.commun.viewmodel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Reglages;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// Couche **réactive write-through** au-dessus du service [Reglages] : expose chaque réglage comme
/// une `Property` JavaFX observable, initialisée depuis la valeur persistée (ou son défaut) et
/// **réécrite automatiquement** dans [Reglages] à chaque changement.
///
/// Deux avantages :
/// - les contrôles d'IHM se **lient** simplement à la Property (pas de sauvegarde manuelle) ;
/// - deux contrôles demandant la **même clé** reçoivent la **même** Property (cache par clé) : ils
///   restent donc synchronisés en direct. C'est ce qui permet, par exemple, à un item du menu ☰ et à
///   l'onglet de réglages de refléter instantanément le même réglage.
///
/// Singleton (le cache doit être partagé par tout le chrome et toutes les features).
@Singleton
public final class ReglagesReactifs {

    private final Reglages reglages;
    private final Map<String, Property<?>> proprietes = new HashMap<>();

    @Inject
    public ReglagesReactifs(Reglages reglages) {
        this.reglages = Objects.requireNonNull(reglages, "reglages");
    }

    /// Property booléenne du réglage `cle` : initialisée à sa valeur persistée (ou `defaut`), toute
    /// modification étant réécrite dans [Reglages]. Rappels successifs sur la même clé : même instance.
    public BooleanProperty proprieteBooleen(String cle, boolean defaut) {
        return (BooleanProperty) proprietes.computeIfAbsent(cle, c -> {
            BooleanProperty propriete = new SimpleBooleanProperty(reglages.lireBooleen(c, defaut));
            propriete.addListener((observable, ancien, nouveau) -> reglages.ecrireBooleen(c, nouveau));
            return propriete;
        });
    }

    /// Property texte du réglage `cle` (init depuis la valeur persistée ou `defaut`, write-through).
    /// Rappels successifs sur la même clé : même instance.
    public StringProperty proprieteTexte(String cle, String defaut) {
        return (StringProperty) proprietes.computeIfAbsent(cle, c -> {
            StringProperty propriete = new SimpleStringProperty(reglages.lireTexte(c, defaut));
            propriete.addListener((observable, ancien, nouveau) -> reglages.ecrireTexte(c, nouveau));
            return propriete;
        });
    }

    /// Property entière du réglage `cle` (init depuis la valeur persistée ou `defaut`, write-through).
    /// Rappels successifs sur la même clé : même instance.
    public IntegerProperty proprieteEntier(String cle, int defaut) {
        return (IntegerProperty) proprietes.computeIfAbsent(cle, c -> {
            IntegerProperty propriete = new SimpleIntegerProperty(reglages.lireEntier(c, defaut));
            propriete.addListener((observable, ancien, nouveau) -> reglages.ecrireEntier(c, nouveau.intValue()));
            return propriete;
        });
    }
}
