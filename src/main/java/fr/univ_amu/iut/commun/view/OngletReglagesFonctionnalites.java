package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;

/// Onglet « Fonctionnalités » de l'écran Réglages (#1057) : un **interrupteur par feature
/// désactivable** (`OPTIONNELLE` / `EXPERIMENTALE`), câblé au flag persisté `feature.<id>.active`.
/// Les features `COEUR` (socle) n'y figurent pas. La décision d'installation étant prise à la
/// composition, la bascule **prend effet au prochain démarrage** : un bandeau le rappelle, via
/// l'échappatoire [OngletReglagesPersonnalise].
///
/// Les descripteurs sont **calculés par le socle** (`CommunModule`) depuis le registre des
/// fonctionnalités ; cet onglet ne dépend donc pas du paquet `di`.
public final class OngletReglagesFonctionnalites implements OngletReglagesPersonnalise {

    private final List<DescripteurReglage> reglages;

    public OngletReglagesFonctionnalites(List<DescripteurReglage> reglages) {
        this.reglages = List.copyOf(Objects.requireNonNull(reglages, "reglages"));
    }

    @Override
    public String idFeature() {
        return "fonctionnalites";
    }

    @Override
    public int ordre() {
        return 90;
    }

    @Override
    public String titre() {
        return "Fonctionnalités";
    }

    @Override
    public String iconeLiteral() {
        return "fas-toggle-on";
    }

    @Override
    public List<DescripteurReglage> reglages() {
        return reglages;
    }

    @Override
    public Node formulairePersonnalise() {
        Label bandeau = new Label(
                "Activer ou désactiver une fonctionnalité prend effet au prochain démarrage de l'application.");
        bandeau.setWrapText(true);
        bandeau.setPadding(new Insets(8, 0, 0, 0));
        bandeau.getStyleClass().add("reglages-fonctionnalites-note");
        return bandeau;
    }
}
