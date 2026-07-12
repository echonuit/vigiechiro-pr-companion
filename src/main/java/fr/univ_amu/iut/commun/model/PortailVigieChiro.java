package fr.univ_amu.iut.commun.model;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import java.util.Objects;
import java.util.Optional;

/// **Pages du portail Vigie-Chiro** (#1124) : construit l’URL publique de l’entité **rattachée**
/// (référence locale → `objectid` via la table des liens), pour l’ouvrir dans le navigateur et
/// vérifier le rattachement d’un coup d’œil. Résolution purement **locale** : aucun appel réseau,
/// le portail gère lui-même l’authentification. Vide si l’entité n’est pas rattachée — l’action IHM
/// correspondante reste alors désactivée avec explication (patron [ConstructeurLienEspece]).
///
/// Routes vérifiées dans le bundle Angular du portail : `#/sites/{id}`, `#/participations/{id}`
/// et `#/participations/{id}/donnees`.
@Singleton
public final class PortailVigieChiro {

    /// URL publique du portail (page d’accueil) — aussi ouverte par la modale de connexion.
    public static final String URL_PORTAIL = "https://vigiechiro.herokuapp.com";

    private final LienVigieChiroDao liens;

    @Inject
    public PortailVigieChiro(LienVigieChiroDao liens) {
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    /// Page du **site** portail rattaché au site local `idSite`.
    public Optional<String> pageSite(Long idSite) {
        return objectid(LienVigieChiro.ENTITE_SITE, idSite).map(id -> URL_PORTAIL + "/#/sites/" + id);
    }

    /// Page de la **participation** liée au passage local `idPassage`.
    public Optional<String> pageParticipation(Long idPassage) {
        return objectid(LienVigieChiro.ENTITE_PASSAGE, idPassage).map(id -> URL_PORTAIL + "/#/participations/" + id);
    }

    /// Page des **données** (observations Tadarida) de la participation liée au passage local.
    public Optional<String> pageDonnees(Long idPassage) {
        return pageParticipation(idPassage).map(url -> url + "/donnees");
    }

    private Optional<String> objectid(String entite, Long refLocale) {
        return refLocale == null ? Optional.empty() : liens.objectidPour(entite, String.valueOf(refLocale));
    }
}
