package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.CodePointLibre;
import fr.univ_amu.iut.sites.model.LecturePosition;
import fr.univ_amu.iut.sites.model.PositionCollee;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableStringValue;

/// Le **premier point d'écoute**, créé avec son site (#5687).
///
/// L'observateur qui colle une position pour trouver son carré devait la ressaisir pour créer son
/// premier point. Une case lui offre de le créer là, à la position collée. Elle n'est offerte que si
/// cette position se lit, et seulement à la création : un carré récupéré apporte ses points.
///
/// Extraite de [SiteEditViewModel] pour la même raison que [PositionColleeViewModel] : ce concern a sa
/// case, sa condition et son compte rendu, et ne lit du formulaire que le texte de la position.
public final class PremierPoint {

    private final ServiceSites service;
    private final ObservableStringValue position;
    private final BooleanProperty demande = new SimpleBooleanProperty(this, "demande", false);
    private final BooleanBinding offert;
    private RetourOperation annonce = RetourOperation.AUCUN;

    PremierPoint(ServiceSites service, ObservableStringValue position, ObservableBooleanValue enCreation) {
        this.service = Objects.requireNonNull(service, "service");
        this.position = Objects.requireNonNull(position, "position");
        offert = Bindings.createBooleanBinding(() -> enCreation.get() && lue().isPresent(), position, enCreation);
    }

    /// La case a-t-elle lieu d'être ? Vrai à la création, quand la position collée se lit.
    public BooleanBinding offert() {
        return offert;
    }

    /// La case elle-même, décochée par défaut.
    public BooleanProperty demande() {
        return demande;
    }

    /// Ce que la dernière création a à dire du premier point : vide s'il a été créé ou n'était pas
    /// demandé, un avertissement s'il a été refusé alors que le site, lui, existe.
    public RetourOperation annonce() {
        return annonce;
    }

    /// Crée le point si la case est cochée et encore offerte. Le site est déjà enregistré : un refus
    /// ici ne le défait pas, il se dit.
    void creerPour(Site site) {
        annonce = RetourOperation.AUCUN;
        Optional<LecturePosition.Lue> lue = lue();
        if (!demande.get() || !offert.get() || lue.isEmpty()) {
            return;
        }
        try {
            service.ajouterPoint(
                    site.id(),
                    CodePointLibre.suivant(List.of()),
                    lue.get().latitude(),
                    lue.get().longitude(),
                    null);
        } catch (RegleMetierException | IllegalArgumentException refus) {
            annonce = RetourOperation.avertissement("Le site est créé, mais son premier point n'a pas pu l'être ("
                    + refus.getMessage() + "). Ajoutez-le depuis « Ajouter un point d'écoute ».");
        }
    }

    private Optional<LecturePosition.Lue> lue() {
        String texte = position.get();
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        return PositionCollee.lire(texte) instanceof LecturePosition.Lue lue ? Optional.of(lue) : Optional.empty();
    }
}
