package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.PublicationPoint;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Tout ce que la **fiche site** sait de la publication d'un point vers Vigie-Chiro (#3458) : ce qui
/// l'empêche, ce qu'elle retient, et comment la déclencher.
///
/// Tenu hors de [SiteDetailViewModel], qui porte déjà le bandeau d'identité, les cartes de points,
/// le tableau des passages et les suppressions. La publication est une préoccupation à part
/// entière, avec ses propres règles et ses propres raisons de refuser.
///
/// **Ce qui n'est pas ici, et n'y sera pas** : un garde sur le verrouillage du carré. La plateforme
/// refuse le propriétaire d'un carré verrouillé, mais accepte un participant validé sur le protocole,
/// verrouillé ou non ; et les liens de site venant de `GET /moi/participations` plutôt que de
/// `/moi/sites` (#718), rien ici ne dit dans quel cas on se trouve. Le refus est **rendu compte avec son
/// geste** par [PublicationPoint], jamais deviné.
public class PublicationDepuisLaFiche {

    private final PointPublieDao publies;
    private final LienVigieChiroDao liens;

    /// `Optional` **vide** hors de l'application complète (injecteurs de capture, tests) : la fiche
    /// n'offre alors simplement pas le geste, patron de `ControleCarreStoc`.
    private final Optional<PublicationPoint> publication;

    public PublicationDepuisLaFiche(
            PointPublieDao publies, LienVigieChiroDao liens, Optional<PublicationPoint> publication) {
        this.publies = Objects.requireNonNull(publies, "publies");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    /// La publication est-elle **installée** ? Faux hors de l'application complète : la fiche n'affiche
    /// alors aucune action « Publier », comme elle masque « Importer une nuit » quand la feature
    /// d'importation est absente (#1087).
    public boolean installee() {
        return publication.isPresent();
    }

    /// Les points de ce site que **nous** avons poussés en ligne. Une seule lecture pour tout le site :
    /// une requête par carte en ferait N pour afficher un écran qui n'en demande qu'une.
    public Set<Long> publiesDuSite(long idSite) {
        return Set.copyOf(publies.parSite(idSite));
    }

    /// Ce qui **empêche** de publier cette carte, ou vide si le geste est possible. La chaîne rendue est
    /// l'explication du gris : elle dit quoi faire, pas seulement ce qui manque.
    ///
    /// Les motifs sont énumérés dans l'ordre où ils bloquent le chemin de l'utilisateur : se connecter,
    /// puis déclarer le carré, puis placer le point. Annoncer « pas de coordonnées » à quelqu'un qui
    /// n'est pas connecté serait du bruit avant l'obstacle réel.
    public Optional<String> empechement(long idSite, CartePoint carte) {
        Objects.requireNonNull(carte, "carte");
        return empechement(idSite, carte.gpsPresent());
    }

    /// Variante sans carte : le point n'existe pas encore (#3458). C'est le cas de la **modale de
    /// création**, où l'on décide de publier avant même que le point soit enregistré ; seule sa position
    /// est connue, et elle change à mesure qu'on la saisit.
    public Optional<String> empechement(long idSite, boolean gpsPresent) {
        if (publication.isEmpty()) {
            return Optional.of("La publication vers Vigie-Chiro n'est pas disponible.");
        }
        if (!publication.get().connecte()) {
            return Optional.of("Connectez-vous à Vigie-Chiro pour publier ce point.");
        }
        if (objectidDuSite(idSite).isEmpty()) {
            return Optional.of("Ce carré n'est pas encore enregistré sur Vigie-Chiro."
                    + " Déclarez-le sur la plateforme avant d'y ajouter des points.");
        }
        if (!gpsPresent) {
            return Optional.of("Ce point n'a pas de coordonnées, et une localité Vigie-Chiro en exige."
                    + " Placez-le sur la carte avant de le publier.");
        }
        return Optional.empty();
    }

    /// Publie ce point sur la plateforme et retient qu'il y est.
    ///
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX. Ne touche aucune propriété observable et ne
    /// rafraîchit rien : l'appelant le fait au retour, sur le fil JavaFX.
    ///
    /// @throws IllegalStateException si le geste n'était pas possible ; la vue le gate par
    ///     [#empechement(long, CartePoint)], et l'appeler quand même est une faute de câblage
    public PublicationPoint.Resultat publier(long idSite, CartePoint carte) {
        Objects.requireNonNull(carte, "carte");
        return publier(idSite, carte.point());
    }

    /// Variante à partir du **point** : la modale de création n'a pas de carte à présenter, elle vient
    /// tout juste de l'enregistrer.
    public PublicationPoint.Resultat publier(long idSite, PointDEcoute point) {
        Objects.requireNonNull(point, "point");
        empechement(idSite, point.latitude() != null && point.longitude() != null)
                .ifPresent(motif -> {
                    throw new IllegalStateException("Publication impossible : " + motif);
                });
        return publication
                .orElseThrow()
                .publier(
                        objectidDuSite(idSite).orElseThrow(),
                        new PointVigieChiro(point.code(), point.latitude(), point.longitude()),
                        point.id());
    }

    /// `_id` VigieChiro de ce site, s'il est relié.
    private Optional<String> objectidDuSite(long idSite) {
        return liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(idSite));
    }
}
