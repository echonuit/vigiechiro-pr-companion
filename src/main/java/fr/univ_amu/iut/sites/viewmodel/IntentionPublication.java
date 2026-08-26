package fr.univ_amu.iut.sites.viewmodel;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;

/// L'intention de **publier un point qu'on est en train de créer** (#3458) : la case à cocher de la
/// modale, ce qui la grise, et ce qu'elle décide au moment d'enregistrer.
///
/// Extraite de [PointEditViewModel], qui franchissait le seuil God-class en l'absorbant : cette modale
/// porte déjà la saisie, ses quatre validités, le contrôle du carré STOC et la résolution de commune.
///
/// L'état GPS n'est pas dupliqué ici : il est **demandé** au ViewModel par `gpsRenseigne`, qui seul sait
/// lire ses champs de saisie. En recopier une version locale ferait deux vérités à tenir d'accord.
public class IntentionPublication {

    private final PublicationDepuisLaFiche publication;
    private final BooleanSupplier gpsRenseigne;

    /// Choix de l'utilisateur. **Faux par défaut** : publier est un geste qui sort de la machine, il se
    /// demande, il ne se suppose pas.
    private final BooleanProperty demandee = new SimpleBooleanProperty(this, "demandee", false);

    /// Motif du gris, vide quand le geste est possible.
    private final ReadOnlyStringWrapper empechement = new ReadOnlyStringWrapper(this, "empechement", "");

    /// La case a-t-elle lieu d'être sur cet écran ?
    private final ReadOnlyBooleanWrapper offerte = new ReadOnlyBooleanWrapper(this, "offerte", false);

    private Long idSite;

    public IntentionPublication(PublicationDepuisLaFiche publication, BooleanSupplier gpsRenseigne) {
        this.publication = Objects.requireNonNull(publication, "publication");
        this.gpsRenseigne = Objects.requireNonNull(gpsRenseigne, "gpsRenseigne");
    }

    /// Écran de **création** : la case est offerte si la publication est installée.
    public void aLaCreation(long idSite) {
        this.idSite = idSite;
        offerte.set(publication.installee());
        demandee.set(false);
        recalculer();
    }

    /// Écran d'**édition** : pas de case. La carte du point porte déjà l'action, et la reproposer ici la
    /// dédoublerait - deux chemins pour un même geste finissent par diverger.
    public void aLEdition() {
        this.idSite = null;
        offerte.set(false);
        demandee.set(false);
        empechement.set("");
    }

    /// Recalcule le motif du gris, et **décoche** la case s'il vient d'apparaître.
    ///
    /// Laisser la case cochée sous un contrôle désactivé donnerait à lire une intention qui ne partira
    /// pas. La décocher est visible - la case est sous les yeux - là où le silence ne le serait pas.
    public void recalculer() {
        String motif = empechementActuel();
        empechement.set(motif);
        if (!motif.isEmpty()) {
            demandee.set(false);
        }
    }

    /// Le point qui vient d'être enregistré doit-il partir sur la plateforme ?
    ///
    /// On **redemande** le motif, au lieu de relire celui qui est affiché. Ce dernier ne se recalcule
    /// qu'à la saisie des coordonnées ; or la session peut expirer, ou le lien du carré disparaître, sans
    /// qu'aucun champ ne bouge. Relire l'affichage rendrait ce garde d'accord avec un écran périmé.
    public Optional<Long> pointAPublier(Long idPointEnregistre) {
        if (!demandee.get() || idPointEnregistre == null || !empechementActuel().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(idPointEnregistre);
    }

    public ReadOnlyBooleanProperty offerteProperty() {
        return offerte.getReadOnlyProperty();
    }

    public BooleanProperty demandeeProperty() {
        return demandee;
    }

    public ReadOnlyStringProperty empechementProperty() {
        return empechement.getReadOnlyProperty();
    }

    /// Ce qui empêche de publier **maintenant**, calculé et non relu. Chaîne vide si rien n'empêche.
    private String empechementActuel() {
        if (!offerte.get() || idSite == null) {
            return "";
        }
        return publication.empechement(idSite, gpsRenseigne.getAsBoolean()).orElse("");
    }
}
