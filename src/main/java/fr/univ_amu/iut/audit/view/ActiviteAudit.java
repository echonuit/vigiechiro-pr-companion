package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.util.Objects;

/// Carte d'accueil de la feature `audit` (prisme **« Collecte & passages »**) : ouvre l'écran « Audit de
/// cohérence » (audit disque / base global). Implémente le contrat socle [ActiviteAccueil] et délègue à
/// [NavigationAudit]. Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.audit.di.AuditModule]. Rang élevé : outil de contrôle, en fin de son prisme.
public final class ActiviteAudit implements ActiviteAccueil {

    private final NavigationAudit navigation;

    @Inject
    public ActiviteAudit(NavigationAudit navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public Prisme prisme() {
        return Prisme.COLLECTE_PASSAGES;
    }

    @Override
    public int ordre() {
        return 90;
    }

    @Override
    public String iconeLiteral() {
        return "fas-clipboard-check";
    }

    @Override
    public String couleur() {
        return "#8e44ad";
    }

    @Override
    public String titre() {
        return "Audit de cohérence";
    }

    @Override
    public String description() {
        return "Vérifie que fichiers, base et dépôts restent en correspondance : écarts disque / base repérés.";
    }

    @Override
    public void ouvrir() {
        navigation.ouvrir();
    }
}
