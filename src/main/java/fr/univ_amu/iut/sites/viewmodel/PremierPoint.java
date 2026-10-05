package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.CodePointLibre;
import fr.univ_amu.iut.sites.model.LecturePosition;
import fr.univ_amu.iut.sites.model.PositionCollee;
import fr.univ_amu.iut.sites.model.PropositionCarre;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.VerdictCarre;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
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
    private final PropositionCarre proposition;
    private final ObjectBinding<RetourOperation> avertissement;
    private RetourOperation annonce = RetourOperation.AUCUN;

    /// @param proposition le carroyage qui situe une position, celui que « Situer » lit
    /// @param numeroSaisi le numéro de carré du formulaire, que la position est confrontée à
    PremierPoint(
            ServiceSites service,
            ObservableStringValue position,
            ObservableBooleanValue enCreation,
            PropositionCarre proposition,
            ObservableStringValue numeroSaisi) {
        this.service = Objects.requireNonNull(service, "service");
        this.position = Objects.requireNonNull(position, "position");
        this.proposition = Objects.requireNonNull(proposition, "proposition");
        Objects.requireNonNull(numeroSaisi, "numeroSaisi");
        offert = Bindings.createBooleanBinding(() -> enCreation.get() && lue().isPresent(), position, enCreation);
        avertissement = Bindings.createObjectBinding(
                () -> divergence(numeroSaisi.get()), position, numeroSaisi, demande, offert);
    }

    /// La case a-t-elle lieu d'être ? Vrai à la création, quand la position collée se lit.
    public BooleanBinding offert() {
        return offert;
    }

    /// La case elle-même, décochée par défaut.
    public BooleanProperty demande() {
        return demande;
    }

    /// Ce que la case a à dire **avant** de créer (#5860) : la position collée tombe dans un autre carré
    /// que le numéro saisi. Vide sinon, et sur une frontière, où aucun carré ne l'emporte.
    ///
    /// Le carré se lit sur le carroyage embarqué, celui de « Situer » : le verdict vaut hors connexion.
    /// Sa phrase est celle de la modale de point ([VerdictCarre.Diverge]), et il ne bloque pas.
    public ObjectBinding<RetourOperation> avertissement() {
        return avertissement;
    }

    private RetourOperation divergence(String numeroSaisi) {
        if (!demande.get() || !offert.get() || numeroSaisi == null || !numeroSaisi.matches("\\d{6}")) {
            return RetourOperation.AUCUN;
        }
        return proposition
                .pour(position.get())
                .numeroAProposer()
                .filter(officiel -> !officiel.equals(numeroSaisi))
                .map(officiel ->
                        RetourOperation.avertissement(new VerdictCarre.Diverge(officiel, numeroSaisi).message()))
                .orElse(RetourOperation.AUCUN);
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
