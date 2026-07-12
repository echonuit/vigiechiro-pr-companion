package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Audit **en ligne des points d'écoute** (#1178) : confronte chaque point local à sa localité serveur
/// (via le lien `vigiechiro_link` `ENTITE_SITE` du site) et signale les divergences (point local inconnu
/// du serveur, position différente). Lecture seule ; dégrade en `INFO` si le serveur est injoignable.
public final class AuditPointsServeur {

    /// Tolérance de comparaison des coordonnées (~11 m à l'équateur) : en deçà, les positions sont
    /// considérées identiques (arrondis de sérialisation).
    private static final double TOLERANCE_DEGRES = 1e-4;

    private final ClientVigieChiro client;
    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final LienVigieChiroDao liens;
    private final String idUtilisateur;

    public AuditPointsServeur(
            ClientVigieChiro client,
            SiteDao siteDao,
            PointDao pointDao,
            LienVigieChiroDao liens,
            String idUtilisateur) {
        this.client = Objects.requireNonNull(client, "client");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    List<ConstatAudit> auditer() {
        List<SiteVigieChiro> distants = client.mesSites();
        if (distants.isEmpty()) {
            return List.of(new ConstatAudit(
                    SeveriteConstat.INFO,
                    CategorieConstat.SERVEUR_INJOIGNABLE,
                    null,
                    "-",
                    "Points serveur indisponibles (hors connexion ou aucun site distant)."));
        }
        Map<String, SiteVigieChiro> parObjectid =
                distants.stream().collect(Collectors.toMap(SiteVigieChiro::id, Function.identity(), (a, b) -> a));
        List<ConstatAudit> constats = new ArrayList<>();
        for (Site local : siteDao.findByUtilisateur(idUtilisateur)) {
            Optional<String> objectid = liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(local.id()));
            if (objectid.isEmpty()) {
                continue; // site non lié au serveur : hors périmètre de l'audit en ligne
            }
            SiteVigieChiro distant = parObjectid.get(objectid.get());
            if (distant == null) {
                continue; // lien périmé : le site distant n'est plus dans mesSites
            }
            Map<String, PointVigieChiro> pointsServeur = distant.points().stream()
                    .collect(Collectors.toMap(PointVigieChiro::code, Function.identity(), (a, b) -> a));
            for (PointDEcoute point : pointDao.findBySite(local.id())) {
                confronter(constats, local, point, pointsServeur.get(point.code()));
            }
        }
        return constats;
    }

    private void confronter(List<ConstatAudit> constats, Site site, PointDEcoute local, PointVigieChiro distant) {
        String cible = site.numeroCarre() + " / " + local.code();
        if (distant == null) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.AVERTISSEMENT,
                    CategorieConstat.POINT_DIVERGENT,
                    null,
                    cible,
                    "Point local inconnu du serveur (créé localement, ou supprimé côté serveur)."));
        } else if (positionDiffere(local, distant)) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.AVERTISSEMENT,
                    CategorieConstat.POINT_DIVERGENT,
                    null,
                    cible,
                    "Position différente du serveur (local " + local.latitude() + "," + local.longitude()
                            + " vs serveur " + distant.latitude() + "," + distant.longitude() + ")."));
        }
    }

    private static boolean positionDiffere(PointDEcoute local, PointVigieChiro distant) {
        if (local.latitude() == null || local.longitude() == null) {
            return false; // pas de position locale à comparer
        }
        return Math.abs(local.latitude() - distant.latitude()) > TOLERANCE_DEGRES
                || Math.abs(local.longitude() - distant.longitude()) > TOLERANCE_DEGRES;
    }
}
