package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.EventailCentre;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.PointGeo;
import fr.univ_amu.iut.importation.viewmodel.RattachementImportViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.ListChangeListener;
import javafx.scene.paint.Color;

/// **Carte de confirmation du rattachement** d'import (#154, lecture seule) : montre le **carré du site
/// sélectionné** et **ses points**, le **point choisi** en surbrillance (indigo) et les autres en gris,
/// pour vérifier d'un coup d'œil qu'on rattache la nuit au bon endroit. Aucune édition : elle ne fait que
/// **refléter** l'état du [RattachementImportViewModel].
///
/// Extrait du [ImportationController] (déjà dense) pour rester focalisé : il détient la [CarteSites] et la
/// tient à jour à chaque changement de site / point / liste de points.
final class CarteRattachement {

    /// Point **choisi** pour le rattachement : indigo de l'application (ressort sur les autres).
    private static final Color COULEUR_SELECTION = Color.web("#3f51b5");

    /// Autres points du site : gris neutre (présents pour le repère, mais pas le sujet).
    private static final Color COULEUR_AUTRE = Color.web("#9aa0a6");

    /// Remplissage translucide du carré du site.
    private static final Color COULEUR_CARRE = Color.web("#3f51b5", 0.12);

    private final CarteSites carte = new CarteSites();
    private RattachementImportViewModel rattachement;

    /// Le composant carte à insérer dans la vue (lecture seule : pas de mode édition).
    CarteSites vue() {
        return carte;
    }

    /// Lie la carte au sous-VM de rattachement : elle se redessine à chaque changement de site (recentrage
    /// sur le carré), de point choisi ou de liste de points (recoloration, sans recentrer).
    void lier(RattachementImportViewModel rattachement) {
        this.rattachement = rattachement;
        rattachement.siteSelectionneProperty().addListener((obs, ancien, nouveau) -> rafraichir(true));
        rattachement.pointSelectionneProperty().addListener((obs, ancien, nouveau) -> rafraichir(false));
        rattachement.points().addListener((ListChangeListener<PointDEcoute>) changement -> rafraichir(false));
        rafraichir(true);
    }

    /// (Re)dessine carré + points du site sélectionné ; `recentrer` recentre la vue sur le carré (à la
    /// sélection d'un site), sinon la laisse en place (simple recoloration du point choisi).
    private void rafraichir(boolean recentrer) {
        Site site = rattachement.siteSelectionneProperty().get();
        if (site == null) {
            carte.setDonnees(new DonneesCarte(List.of(), List.of()), false);
            return;
        }
        PointDEcoute choisi = rattachement.pointSelectionneProperty().get();
        Long idChoisi = choisi == null ? null : choisi.id();

        List<PointGeo> geolocalises = new ArrayList<>();
        List<PointDEcoute> sansGps = new ArrayList<>();
        for (PointDEcoute point : rattachement.points()) {
            if (point.latitude() != null && point.longitude() != null) {
                geolocalises.add(
                        new PointGeo(point.code(), point.latitude(), point.longitude(), couleur(point, idChoisi)));
            } else {
                sansGps.add(point);
            }
        }
        Optional<EmpriseCarre> emprise = FournisseurEmpriseCarre.parDefaut().emprise(site.numeroCarre(), geolocalises);
        List<PointGeo> marqueurs = new ArrayList<>(geolocalises);
        // Points sans GPS : placés (approximatifs) au centre du carré, **désempilés en éventail** (#153/#154)
        // pour qu'aucun ne se masque — sinon le point choisi pourrait disparaître sous un autre.
        emprise.ifPresent(e -> {
            List<double[]> positions = EventailCentre.positions(e, sansGps.size());
            for (int i = 0; i < sansGps.size(); i++) {
                PointDEcoute point = sansGps.get(i);
                marqueurs.add(new PointGeo(
                        point.code(), positions.get(i)[0], positions.get(i)[1], couleur(point, idChoisi), null, true));
            }
        });
        dessinerEnDernier(marqueurs, choisi);
        List<CarreGeo> carres = emprise.map(e -> List.of(new CarreGeo(site.numeroCarre(), e, COULEUR_CARRE)))
                .orElse(List.of());
        carte.setDonnees(new DonneesCarte(carres, marqueurs), false);
        if (recentrer) {
            emprise.ifPresent(carte::centrerSurCarre);
        }
    }

    /// Place le marqueur du point `choisi` **en dernier** dans la liste : la couche dessine dans l'ordre,
    /// donc le point choisi passe **au-dessus** et reste visible même si des marqueurs se chevauchent.
    private static void dessinerEnDernier(List<PointGeo> marqueurs, PointDEcoute choisi) {
        if (choisi == null) {
            return;
        }
        marqueurs.stream()
                .filter(marqueur -> marqueur.libelle().equals(choisi.code()))
                .findFirst()
                .ifPresent(selectionne -> {
                    marqueurs.remove(selectionne);
                    marqueurs.add(selectionne);
                });
    }

    private static Color couleur(PointDEcoute point, Long idChoisi) {
        return idChoisi != null && idChoisi.equals(point.id()) ? COULEUR_SELECTION : COULEUR_AUTRE;
    }
}
