package fr.univ_amu.iut.sites.di;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.model.PointsDuCarre;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;

/// Implémentation du port socle [PointsDuCarre] par la feature `sites`, qui détient les carrés et leurs
/// points. Pendant de [InfosPointSites] : côté « fournisseur » de l'inversion qui évite le cycle
/// `passage ↔ sites`. Branchée par [SitesModule] via un `OptionalBinder`.
///
/// Le carré est résolu **parmi les sites de l'utilisateur courant** : c'est la portée de l'unicité R5,
/// donc la seule où « le carré 640380 » désigne un site et un seul.
final class PointsDuCarreSites implements PointsDuCarre {

    private final ServiceSites service;
    private final String idUtilisateur;

    @Inject
    PointsDuCarreSites(ServiceSites service, @Named("idUtilisateurCourant") String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public List<String> codes(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.isBlank()) {
            return List.of();
        }
        return service.listerSites(idUtilisateur).stream()
                .filter(site -> numeroCarre.equals(site.numeroCarre()))
                .findFirst()
                .map(Site::id)
                .map(service::listerPoints)
                .orElse(List.of())
                .stream()
                .map(PointDEcoute::code)
                .toList();
    }
}
