package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.PointActivite;
import fr.univ_amu.iut.commun.model.Nuit;
import fr.univ_amu.iut.commun.view.AxeHoraire;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;

/// Comment une nuit d'activité **se dessine** : le repère nocturne, les séries, leurs infobulles.
///
/// ## Pourquoi ce n'est pas dans le contrôleur
///
/// Ces méthodes étaient statiques sur [ActiviteController] et **appelées depuis l'extérieur** :
/// `versSeries` par [ExportImageActivite], qui redessine les mêmes courbes hors écran, et
/// `texteInfobulle` par son test. Un chunk qu'on appelle du dehors n'appartient pas à l'écran qui le
/// contenait.
///
/// L'extraction a été **imposée par le portail qualité** : ajouter le contrat de fraîcheur (#3964)
/// portait `ActiviteController` à WMC=49, au-dessus du plafond God-class. Le réflexe `//NOPMD` est
/// exclu par convention ; la classe dit donc ce qu'elle fait, et l'écran s'allège d'autant.
///
/// ## Ce que ces méthodes garantissent ensemble
///
/// Que **l'image exportée montre exactement ce que l'écran montre** : mêmes séries, même repère. C'est
/// pour cela qu'elles sont partagées plutôt que recopiées - deux dessins d'une même nuit qui
/// divergeraient seraient un défaut invisible à la relecture.
final class CourbesActivite {

    /// Début de la fenêtre nocturne : les abscisses comptent les minutes écoulées depuis 18 h.
    private static final LocalTime DEBUT_FENETRE = LocalTime.of(18, 0);

    /// Largeur du cadre, de 18 h à 8 h le lendemain.
    private static final int MINUTES_FENETRE = 840;

    private static final DateTimeFormatter HEURE_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private CourbesActivite() {
        // Porte un dessin, pas un état.
    }

    /// Le repère nocturne : de 0 (18 h) à 840 minutes (8 h), une graduation par heure.
    ///
    /// **Fixe** et non calé sur les données : deux nuits se comparent alors à la même échelle, une nuit
    /// courte occupant le milieu du cadre au lieu d'être étirée (ADR 2352).
    static void configurerAxeNocturne(NumberAxis axeTemps) {
        AxeHoraire.graduerEnHeures(axeTemps, DEBUT_FENETRE, MINUTES_FENETRE, 60);
    }

    /// Traduit des courbes en séries de graphe. Partagée avec [ExportImageActivite], qui redessine les
    /// mêmes courbes hors écran : l'image montre ainsi exactement ce que l'écran montre.
    static List<XYChart.Series<Number, Number>> versSeries(List<CourbeEspece> courbes) {
        return courbes.stream().map(CourbesActivite::versSerie).toList();
    }

    /// Texte de l'infobulle d'un point : espèce, heure de la tranche (`HH:mm`) et nombre de contacts,
    /// avec l'accord singulier/pluriel. La valeur exacte que l'axe ne donne qu'approximativement.
    static String texteInfobulle(String espece, PointActivite point) {
        String heure = point.debutTranche().toLocalTime().format(HEURE_MINUTE);
        String contacts = point.nombre() > 1 ? " contacts" : " contact";
        return espece + " · " + heure + " · " + point.nombre() + contacts;
    }

    /// Les minutes écoulées depuis 18 h **du soir de la nuit**, abscisse d'un point sur le repère.
    ///
    /// Le soir vient de [Nuit#de] et non de la date de l'instant : après minuit, un contact appartient à
    /// la nuit de la veille, et le compter depuis le mauvais soir le placerait 24 h plus loin.
    private static long minutesDepuis18h(LocalDateTime instant) {
        LocalDate soir = Nuit.de(instant);
        return Duration.between(soir.atTime(DEBUT_FENETRE), instant).toMinutes();
    }

    /// Traduit une courbe en série. Redessinée plutôt qu'empruntée : une série n'appartient qu'à un
    /// graphe à la fois.
    private static XYChart.Series<Number, Number> versSerie(CourbeEspece courbe) {
        XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        serie.setName(nomAffiche(courbe));
        for (PointActivite point : courbe.points()) {
            XYChart.Data<Number, Number> donnee =
                    new XYChart.Data<>(minutesDepuis18h(point.debutTranche()), point.nombre());
            installerInfobulle(donnee, texteInfobulle(nomAffiche(courbe), point));
            serie.getData().add(donnee);
        }
        return serie;
    }

    /// Pose l'infobulle sur le symbole du point. Le nœud n'existe qu'une fois le graphe mis en page : on
    /// l'attend via `nodeProperty` (et on le prend s'il est déjà là).
    private static void installerInfobulle(XYChart.Data<Number, Number> donnee, String texte) {
        if (donnee.getNode() != null) {
            Tooltip.install(donnee.getNode(), new Tooltip(texte));
        }
        donnee.nodeProperty().addListener((observable, ancien, noeud) -> {
            if (noeud != null) {
                Tooltip.install(noeud, new Tooltip(texte));
            }
        });
    }

    /// Le nom vernaculaire, ou le code du taxon à défaut : la légende dit ce qu'on sait nommer.
    private static String nomAffiche(CourbeEspece courbe) {
        return courbe.nomEspece() != null ? courbe.nomEspece() : courbe.taxon();
    }
}
